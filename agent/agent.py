import time
import psutil
import requests

BACKEND_URL = "http://localhost:8080/api/devices/ingest"
DEVICE_ID = 1
INTERVAL_SECONDS = 5


def get_latency(host="1.1.1.1"):
    try:
        start_time = time.time()
        requests.get(f"http://{host}", timeout=2)
        return round((time.time() - start_time) * 1000, 2)
    except Exception:
        return -1.0


def collect_metrics():
    cpu_usage = psutil.cpu_percent(interval=1)
    ram_usage = psutil.virtual_memory().percent
    disk_usage = psutil.disk_usage('/').percent
    latency = get_latency()

    return {
        "deviceId": DEVICE_ID,
        "cpu": cpu_usage,
        "ram": ram_usage,
        "disk": disk_usage,
        "latencyMs": latency
    }


def main():
    print(f"Hardware Agent started for Device ID: {DEVICE_ID}")
    print(f"Sending metrics to: {BACKEND_URL} every {INTERVAL_SECONDS}s\n")

    while True:
        try:
            payload = collect_metrics()
            response = requests.post(
                BACKEND_URL,
                json=payload,
                headers={"Content-type": "application/json"},
                timeout=5
            )

            if response.status_code in (200, 201):
                print(f"[OK] Metrics sent: CPU: {payload['cpu']}% | RAM: {payload['ram']}% | DISK: {payload['disk']}% | Latency: {payload['latencyMs']}ms")
            else:
                print(f"[Error {response.status_code}] Backend rejected metrics: {response.text}")

        except requests.exceptions.ConnectionError:
            print("[Connection Error] Spring Boot backend is offline or unreachable.")
        except Exception as e:
            print(f"[Unexpected Error]: {e}")

        time.sleep(INTERVAL_SECONDS)


if __name__ == "__main__":
    main()