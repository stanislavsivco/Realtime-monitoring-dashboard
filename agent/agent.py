import os
import time
import socket
import platform
import uuid
import psutil
import requests

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080/api/devices")
INTERVAL_SECONDS = int(os.environ.get("INTERVAL_SECONDS", "5"))
AGENT_API_KEY = os.environ.get("AGENT_API_KEY", "").strip()

if not AGENT_API_KEY:
    raise SystemExit("AGENT_API_KEY environment variable is not set. The agent cannot authenticate with the backend.")

AGENT_DIR = os.path.dirname(os.path.abspath(__file__))
DEVICE_ID_FILE = os.path.join(AGENT_DIR, ".device_id")
MACHINE_ID_FILE = os.path.join(AGENT_DIR, ".machine_id")

_last_net_io = None
_last_net_time = None


def get_hostname():
    return socket.gethostname()


def get_machine_id():
    if os.path.exists(MACHINE_ID_FILE):
        with open(MACHINE_ID_FILE, "r") as f:
            return f.read().strip()

    machine_id = str(uuid.uuid4())
    with open(MACHINE_ID_FILE, "w") as f:
        f.write(machine_id)
    return machine_id


def register_device():
    device_name = f"{get_hostname()}-{get_machine_id()[:8]}"

    payload = {
        "hostname": device_name,
        "type": platform.system(),
        "location": "Unknown"
    }

    response = requests.post(
        f"{BACKEND_BASE_URL}/register",
        json=payload,
        headers={"Content-Type": "application/json", "X-Agent-Key": AGENT_API_KEY},
        timeout=10
    )

    response.raise_for_status()

    device = response.json()
    device_id = device["id"]

    with open(DEVICE_ID_FILE, "w") as f:
        f.write(str(device_id))

    print(f"[Registered] Device name '{device_name}' -> Device ID {device_id}")
    return device_id


def load_or_register_device_id():
    if os.path.exists(DEVICE_ID_FILE):
        with open(DEVICE_ID_FILE, "r") as f:
            device_id = int(f.read().strip())
            print(f"[Cached] Using existing Device ID {device_id}")
            return device_id

    while True:
        try:
            return register_device()
        except requests.exceptions.RequestException as e:
            print(f"[Registration failed] {e}. Retrying in 5s...")
            time.sleep(5)


def get_latency(host="1.1.1.1"):
    try:
        start_time = time.time()
        requests.get(f"http://{host}", timeout=2)
        return round((time.time() - start_time) * 1000, 2)
    except Exception:
        return None


def get_network_mbps():
    global _last_net_io, _last_net_time

    current = psutil.net_io_counters()
    current_time = time.time()

    if _last_net_io is None:
        _last_net_io = current
        _last_net_time = current_time
        return None, None

    elapsed = current_time - _last_net_time
    if elapsed <= 0:
        return None, None

    bytes_in_per_sec = (current.bytes_recv - _last_net_io.bytes_recv) / elapsed
    bytes_out_per_sec = (current.bytes_sent - _last_net_io.bytes_sent) / elapsed

    _last_net_io = current
    _last_net_time = current_time

    mbps_in = round(max(bytes_in_per_sec, 0) * 8 / 1_000_000, 3)
    mbps_out = round(max(bytes_out_per_sec, 0) * 8 / 1_000_000, 3)
    return mbps_in, mbps_out


def collect_metrics(device_id):
    network_in, network_out = get_network_mbps()
    return {
        "deviceId": device_id,
        "cpu": psutil.cpu_percent(interval=1),
        "ram": psutil.virtual_memory().percent,
        "disk": psutil.disk_usage('/').percent,
        "latencyMs": get_latency(),
        "networkInMbps": network_in,
        "networkOutMbps": network_out
    }


def main():
    print("Hardware Agent starting...")
    device_id = load_or_register_device_id()

    ingest_url = f"{BACKEND_BASE_URL}/ingest"
    print(f"Sending metrics for Device ID {device_id} to: {ingest_url} every {INTERVAL_SECONDS}s\n")

    while True:
        try:
            payload = collect_metrics(device_id)
            response = requests.post(
                ingest_url,
                json=payload,
                headers={"Content-Type": "application/json", "X-Agent-Key": AGENT_API_KEY},
                timeout=5
            )

            if response.status_code in (200, 201):
                net_in = payload['networkInMbps']
                net_out = payload['networkOutMbps']
                net_str = f"NetIn: {net_in} Mbps | NetOut: {net_out} Mbps" if net_in is not None else "NetIn: -- | NetOut: --"
                print(f"[OK] CPU: {payload['cpu']}% | RAM: {payload['ram']}% | "
                      f"DISK: {payload['disk']}% | Latency: {payload['latencyMs']}ms | {net_str}")
            elif response.status_code == 404:
                print(f"[Not Found] Device ID {device_id} no longer exists on the backend. Registering again...")
                if os.path.exists(DEVICE_ID_FILE):
                    os.remove(DEVICE_ID_FILE)
                device_id = load_or_register_device_id()
            else:
                print(f"[Error {response.status_code}] Backend rejected metrics: {response.text}")

        except requests.exceptions.ConnectionError:
            print("[Connection Error] Spring Boot backend is offline or unreachable.")
        except Exception as e:
            print(f"[Unexpected Error]: {e}")

        time.sleep(INTERVAL_SECONDS)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nAgent stopped.")
