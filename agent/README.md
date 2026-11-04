# Monitoring Agent

Collects real hardware metrics (CPU, RAM, disk, latency) and sends them to the backend.

## Setup
```bash
pip install -r requirements.txt
```

## Run
```bash
python agent.py
```

On first run, the agent registers itself with the backend using this machine's hostname and caches the assigned device ID in `.device_id`. Subsequent runs reuse this cached ID.

## Tests
```bash
cd agent
python -m unittest agentTests -v
```

The tests need no running backend, no network and no AGENT_API_KEY. They load agent.py with test settings, redirect .device_id and .machine_id to a temporary folder and mock requests, psutil and time.sleep, so your real cached device ID is never touched.

## Configuration
Set `BACKEND_BASE_URL` as an environment variable if the backend is not running on `localhost:8080`, e.g.:

```bash
export BACKEND_BASE_URL=http://192.168.1.50:8080/api/devices
python agent.py
```