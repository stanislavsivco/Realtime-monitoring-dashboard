import contextlib
import importlib.util
import io
import os
import shutil
import tempfile
import types
import unittest
from unittest import mock

import requests

AGENT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "agent.py")
KEY = "test-agent-key-0123456789abcdef0123456789"
BASE = "http://backend.test/api/devices"
REGISTER, INGEST = f"{BASE}/register", f"{BASE}/ingest"
METRICS = {"cpu": 12.5, "ram": 40.0, "disk": 55.0, "latencyMs": 20.0, "networkInMbps": 1.0, "networkOutMbps": 2.0}


class StopLoop(BaseException):
    pass


class Resp:
    def __init__(self, status=200, body=None):
        self.status_code, self.text, self._body = status, "body", body

    def json(self):
        return self._body

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.exceptions.HTTPError(str(self.status_code), response=self)


class Backend:
    def __init__(self, ingest=(), register=()):
        self.queues = {INGEST: list(ingest), REGISTER: list(register)}
        self.calls = []

    def post(self, url, **kwargs):
        self.calls.append((url, kwargs))
        result = self.queues[url].pop(0)
        if isinstance(result, BaseException):
            raise result
        return result

    def sent(self, url):
        return [kwargs for u, kwargs in self.calls if u == url]

    def ids(self):
        return [kwargs["json"]["deviceId"] for kwargs in self.sent(INGEST)]


def load_agent(env=None, remove=()):
    values = {"AGENT_API_KEY": KEY, "BACKEND_BASE_URL": BASE, "INTERVAL_SECONDS": "5", **(env or {})}
    with mock.patch.dict(os.environ, values):
        for name in remove:
            os.environ.pop(name, None)
        spec = importlib.util.spec_from_file_location("agent_under_test", AGENT_PATH)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    return module


class AgentTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.agent = load_agent()
        self.agent.DEVICE_ID_FILE = os.path.join(self.tmp, ".device_id")
        self.agent.MACHINE_ID_FILE = os.path.join(self.tmp, ".machine_id")

    def write_id(self, content):
        with open(self.agent.DEVICE_ID_FILE, "wb" if isinstance(content, bytes) else "w") as f:
            f.write(content)

    def read_id(self):
        with open(self.agent.DEVICE_ID_FILE) as f:
            return f.read()

    def run_loop(self, backend, sleeps, collect=None):
        collect = collect or (lambda device_id: {"deviceId": device_id, **METRICS})
        out = io.StringIO()
        with contextlib.redirect_stdout(out), \
                mock.patch.object(self.agent.requests, "post", side_effect=backend.post), \
                mock.patch.object(self.agent.time, "sleep", side_effect=[None] * (sleeps - 1) + [StopLoop()]) as sleep, \
                mock.patch.object(self.agent, "collect_metrics", side_effect=collect):
            with self.assertRaises(StopLoop):
                self.agent.main()
        return out.getvalue(), sleep


class ConfigTest(unittest.TestCase):
    def test_missing_or_blank_api_key_exits(self):
        with self.assertRaises(SystemExit):
            load_agent(remove=("AGENT_API_KEY",))
        for value in ("", "   "):
            with self.assertRaises(SystemExit):
                load_agent(env={"AGENT_API_KEY": value})

    def test_defaults_and_trimmed_key(self):
        module = load_agent(env={"AGENT_API_KEY": f" {KEY}\n"}, remove=("BACKEND_BASE_URL", "INTERVAL_SECONDS"))
        self.assertEqual(module.AGENT_API_KEY, KEY)
        self.assertEqual(module.BACKEND_BASE_URL, "http://localhost:8080/api/devices")
        self.assertEqual(module.INTERVAL_SECONDS, 5)


class RegistrationTest(AgentTestCase):
    def register(self, backend):
        with contextlib.redirect_stdout(io.StringIO()), \
                mock.patch.object(self.agent.requests, "post", side_effect=backend.post), \
                mock.patch.object(self.agent, "get_hostname", return_value="lab-pc"):
            return self.agent.register_device()

    def test_sends_payload_and_key_then_saves_id(self):
        with open(self.agent.MACHINE_ID_FILE, "w") as f:
            f.write("abcdef12-3456")
        backend = Backend(register=[Resp(200, {"id": 17})])

        self.assertEqual(self.register(backend), 17)

        call = backend.sent(REGISTER)[0]
        self.assertEqual(call["json"]["hostname"], "lab-pc-abcdef12")
        self.assertEqual(call["headers"]["X-Agent-Key"], KEY)
        self.assertEqual(call["timeout"], 10)
        self.assertEqual(self.read_id(), "17")

    def test_http_error_saves_nothing(self):
        with self.assertRaises(requests.exceptions.HTTPError):
            self.register(Backend(register=[Resp(401)]))
        self.assertFalse(os.path.exists(self.agent.DEVICE_ID_FILE))

    def test_machine_id_is_created_once_and_reused(self):
        first = self.agent.get_machine_id()
        self.assertEqual(first, self.agent.get_machine_id())


class DeviceIdCacheTest(AgentTestCase):
    def load(self, backend):
        with contextlib.redirect_stdout(io.StringIO()), \
                mock.patch.object(self.agent.requests, "post", side_effect=backend.post), \
                mock.patch.object(self.agent.time, "sleep") as sleep:
            return self.agent.load_or_register_device_id(), sleep

    def test_valid_cached_id_is_used_without_backend(self):
        for content in ("5", "5\n", "  5  "):
            self.write_id(content)
            backend = Backend()
            self.assertEqual(self.load(backend)[0], 5)
            self.assertEqual(backend.calls, [])

    def test_corrupted_file_registers_again_instead_of_crashing(self):
        for content in ("abc", "", "  \n", "12abc", "1.5", "NaN", "0", "-1", b"\xff\xfe\x80"):
            with self.subTest(content=content):
                self.write_id(content)
                backend = Backend(register=[Resp(200, {"id": 42})])
                self.assertEqual(self.load(backend)[0], 42)
                self.assertEqual(self.read_id(), "42")

    def test_registration_is_retried_every_5s_while_backend_is_down(self):
        backend = Backend(register=[requests.exceptions.ConnectionError(), Resp(503), Resp(200, {"id": 8})])
        device_id, sleep = self.load(backend)
        self.assertEqual(device_id, 8)
        self.assertEqual(sleep.call_args_list, [mock.call(5), mock.call(5)])


class MetricsTest(AgentTestCase):
    def test_latency_is_milliseconds_of_the_tcp_connect(self):
        clock = types.SimpleNamespace(perf_counter=mock.Mock(side_effect=[100.0, 100.01234]))
        network = types.SimpleNamespace(create_connection=mock.MagicMock())
        with mock.patch.object(self.agent, "time", clock), mock.patch.object(self.agent, "socket", network):
            self.assertEqual(self.agent.get_latency(), 12.34)
        network.create_connection.assert_called_once_with(("1.1.1.1", 443), timeout=2)

    def test_latency_is_none_when_the_connection_fails(self):
        for error in (TimeoutError(), ConnectionRefusedError(), OSError("unreachable")):
            with self.subTest(error=type(error).__name__):
                network = types.SimpleNamespace(create_connection=mock.Mock(side_effect=error))
                with mock.patch.object(self.agent, "socket", network):
                    self.assertIsNone(self.agent.get_latency())

    def test_network_rate(self):
        def counters(received, sent):
            return types.SimpleNamespace(bytes_recv=received, bytes_sent=sent)

        def run(samples, times):
            self.agent._last_net_io = None
            clock = types.SimpleNamespace(time=mock.Mock(side_effect=times))
            with mock.patch.object(self.agent.psutil, "net_io_counters", side_effect=samples), \
                    mock.patch.object(self.agent, "time", clock):
                return [self.agent.get_network_mbps() for _ in samples]

        self.assertEqual(run([counters(0, 0), counters(1_250_000, 625_000)], [1.0, 2.0]), [(None, None), (10.0, 5.0)])
        self.assertEqual(run([counters(0, 0), counters(9, 9)], [1.0, 1.0])[1], (None, None))
        self.assertEqual(run([counters(5_000_000, 5_000_000), counters(1, 1)], [1.0, 2.0])[1], (0.0, 0.0))

    def test_payload_matches_ingest_contract(self):
        with mock.patch.object(self.agent.psutil, "cpu_percent", return_value=33.3), \
                mock.patch.object(self.agent.psutil, "virtual_memory", return_value=types.SimpleNamespace(percent=44.4)), \
                mock.patch.object(self.agent.psutil, "disk_usage", return_value=types.SimpleNamespace(percent=55.5)), \
                mock.patch.object(self.agent, "get_latency", return_value=None), \
                mock.patch.object(self.agent, "get_network_mbps", return_value=(1.5, 2.5)):
            payload = self.agent.collect_metrics(9)
        self.assertEqual(payload, {"deviceId": 9, "cpu": 33.3, "ram": 44.4, "disk": 55.5,
                                   "latencyMs": None, "networkInMbps": 1.5, "networkOutMbps": 2.5})


class MainLoopTest(AgentTestCase):
    def test_sends_correct_data_with_key_and_timeout(self):
        self.write_id("5")
        backend = Backend(ingest=[Resp(200)])

        out, _ = self.run_loop(backend, sleeps=1)

        call = backend.sent(INGEST)[0]
        self.assertEqual(call["json"], {"deviceId": 5, **METRICS})
        self.assertEqual(call["headers"]["X-Agent-Key"], KEY)
        self.assertEqual(call["timeout"], 5)
        self.assertEqual(backend.sent(REGISTER), [])
        self.assertIn("[OK]", out)

    def test_first_run_registers_and_uses_new_id(self):
        backend = Backend(ingest=[Resp(201)], register=[Resp(200, {"id": 42})])
        self.run_loop(backend, sleeps=1)
        self.assertEqual(backend.ids(), [42])

    def test_corrupted_device_id_file_does_not_crash_on_start(self):
        self.write_id("not-a-number")
        backend = Backend(ingest=[Resp(200)], register=[Resp(200, {"id": 42})])
        self.run_loop(backend, sleeps=1)
        self.assertEqual(backend.ids(), [42])
        self.assertEqual(self.read_id(), "42")

    def test_survives_outage_timeouts_and_unexpected_errors(self):
        self.write_id("5")
        backend = Backend(ingest=[requests.exceptions.ConnectionError(), requests.exceptions.Timeout(), Resp(200)])
        out, _ = self.run_loop(backend, sleeps=4, collect=[RuntimeError("boom"), *[{"deviceId": 5, **METRICS}] * 3])
        self.assertEqual(backend.ids(), [5, 5, 5])
        self.assertIn("[Connection Error]", out)
        self.assertIn("[Unexpected Error]", out)
        self.assertIn("[OK]", out)
        self.assertEqual(backend.sent(REGISTER), [])

    def test_rejected_metrics_do_not_cause_re_registration(self):
        for status in (400, 401, 403, 500):
            with self.subTest(status=status):
                self.write_id("5")
                backend = Backend(ingest=[Resp(status), Resp(200)])
                self.run_loop(backend, sleeps=2)
                self.assertEqual(backend.sent(REGISTER), [])
                self.assertEqual(self.read_id(), "5")

    def test_re_registers_when_device_was_deleted(self):
        self.write_id("5")
        backend = Backend(ingest=[Resp(404), Resp(200)], register=[Resp(200, {"id": 9})])
        self.run_loop(backend, sleeps=2)
        self.assertEqual(backend.ids(), [5, 9])
        self.assertEqual(self.read_id(), "9")

    def test_re_registration_survives_backend_outage(self):
        self.write_id("5")
        backend = Backend(ingest=[Resp(404), Resp(200)],
                          register=[requests.exceptions.ConnectionError(), Resp(200, {"id": 9})])
        self.run_loop(backend, sleeps=3)
        self.assertEqual(backend.ids(), [5, 9])


if __name__ == "__main__":
    unittest.main()