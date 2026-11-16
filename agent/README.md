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

The backend recognizes a machine by its machine ID, not by its name. The agent reads the operating system's machine ID (MachineGuid on Windows, /etc/machine-id on Linux, IOPlatformUUID on macOS) with py-machineid and sends only an HMAC-SHA256 hash of it, never the raw ID. If the OS machine ID cannot be read, the agent hashes the random ID stored in `.machine_id` instead. Because of this, an admin can rename the device in the dashboard and the agent still finds it after a restart.

When an admin deletes an agent device, its machine ID is blocked. The agent gets 404 for its metrics, tries to register again, gets 403 and stops with a `[Blocked]` message. It stays blocked until an admin removes the block (`DELETE /api/devices/blocked/{id}`), after that start the agent again. The agent also stops on other permanent registration errors (400, 401, 409), because retrying would never help. It keeps retrying every 5 seconds when the backend is unreachable, returns 5xx, 408 or 429.

## Tests
```bash
cd agent
python -m unittest agentTests -v
```

The tests need no running backend, no network and no AGENT_API_KEY. They load agent.py with test settings, redirect .device_id and .machine_id to a temporary folder and mock requests, psutil, py-machineid and time.sleep, so your real cached device ID is never touched.

## Configuration
Set `BACKEND_BASE_URL` as an environment variable if the backend is not running on `localhost:8080`, e.g.:

```bash
export BACKEND_BASE_URL=http://192.168.1.50:8080/api/devices
python agent.py
```