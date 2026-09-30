import os
import time
import socket
import platform
import psutil
import requests

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080/api/devices")
INTERVAL_SECONDS = int(os.environ.get("INTERVAL_SECONDS", "5"))

DEVICE_ID_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".device_id")

def get_hostname():
   
    return socket.gethostname()


def register_device():
    hostname = get_hostname()
    payload = {
        "hostname": hostname,
        "type": platform.system(),   
        "location": "Unknown"
    }

    response = requests.post(
        f"{BACKEND_BASE_URL}/register",
        json=payload,
        headers={"Content-Type": "application/json"},
        timeout=10
    )

    
    response.raise_for_status()

    device = response.json()
    device_id = device["id"]

    with open(DEVICE_ID_FILE, "w") as f:
        f.write(str(device_id))

    print(f"[Registered] Hostname '{hostname}' -> Device ID {device_id}")
    return device_id


def load_or_register_device_id():
    """
    Loads a cached device ID if one exists. Otherwise attempts to register
    with the backend, retrying every 5 seconds if the backend is unreachable
    (e.g. still starting up) instead of crashing immediately.
    """
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


def collect_metrics(device_id):
    return {
        "deviceId": device_id,
        "cpu": psutil.cpu_percent(interval=1),
        "ram": psutil.virtual_memory().percent,
        "disk": psutil.disk_usage('/').percent,
        "latencyMs": get_latency()
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
                headers={"Content-Type": "application/json"},
                timeout=5
            )

            if response.status_code in (200, 201):
                print(f"[OK] CPU: {payload['cpu']}% | RAM: {payload['ram']}% | "
                      f"DISK: {payload['disk']}% | Latency: {payload['latencyMs']}ms")
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