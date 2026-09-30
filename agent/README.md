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

## Configuration
Set `BACKEND_BASE_URL` as an environment variable if the backend is not running on `localhost:8080`, e.g.:

```bash
export BACKEND_BASE_URL=http://192.168.1.50:8080/api/devices
python agent.py
```