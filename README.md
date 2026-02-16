# Helios Tracker

A minimal Android app that acts as a **location responder** for IoT systems. It receives `LOCATE` commands via [ntfy](https://ntfy.sh) push notifications and replies with the device's current GPS coordinates and battery level.

The app has no background service of its own — it uses the ntfy Android app as a trigger via Broadcast Intents.

## How It Works

```
Server                          ntfy.sh                        Phone
  |                               |                              |
  |-- POST "LOCATE" to topic ---->|                              |
  |                               |-- Push notification -------->|
  |                               |                              | ntfy app broadcasts intent
  |                               |                              | NtfyReceiver catches it
  |                               |                              | WorkManager starts LocationWorker
  |                               |                              | Gets GPS + battery level
  |                               |<-- POST location ------------|
  |<-- Subscribe / poll --------- |                              |
  |                               |                              |
```

1. The **ntfy app** (installed separately) subscribes to a topic and receives push messages.
2. When a message arrives, ntfy broadcasts an Android Intent (`io.heckel.ntfy.MESSAGE_RECEIVED`).
3. **Helios Tracker** listens for this broadcast, filters for the configured topic, and checks if the message is `LOCATE`.
4. A `WorkManager` job gets the current location (WiFi/cell-based, ~100m accuracy) and battery level.
5. The result is sent back via HTTP POST to a configurable ntfy reply topic.

## Server-Side Usage

### Send a LOCATE command

```bash
# Simple curl — send "LOCATE" to your listen topic
curl -d "LOCATE" https://ntfy.sh/YOUR_LISTEN_TOPIC
```

### Wait for the response

```bash
# Subscribe and wait for the next message on the reply topic (blocking)
curl -s "https://ntfy.sh/YOUR_REPLY_TOPIC/json?poll=1&since=30s"
```

### Full example: locate and get response

```bash
#!/bin/bash
LISTEN_TOPIC="my-device-locate"
REPLY_TOPIC="my-device-reply"

# Subscribe in background, wait for one message
curl -s -m 60 "https://ntfy.sh/$REPLY_TOPIC/json?since=now&poll=0" > /tmp/location.json &
LISTENER=$!

sleep 1

# Send LOCATE command
curl -s -d "LOCATE" "https://ntfy.sh/$LISTEN_TOPIC"
echo "LOCATE sent, waiting for response..."

# Wait for the listener
wait $LISTENER

# Parse the response
cat /tmp/location.json | jq -r '.message'
# Output: Lat: 52.5200, Lon: 13.4050, Battery: 72%
```

### Python example

```python
import requests
import json
import time
import threading

LISTEN_TOPIC = "my-device-locate"
REPLY_TOPIC = "my-device-reply"

result = {}

def listen():
    r = requests.get(
        f"https://ntfy.sh/{REPLY_TOPIC}/json",
        params={"since": "now", "poll": "0"},
        stream=True, timeout=60
    )
    for line in r.iter_lines():
        if line:
            msg = json.loads(line)
            if msg.get("event") == "message":
                result["location"] = msg["message"]
                return

# Start listener
t = threading.Thread(target=listen)
t.start()
time.sleep(1)

# Send LOCATE
requests.post(f"https://ntfy.sh/{LISTEN_TOPIC}", data="LOCATE")
print("LOCATE sent, waiting...")

t.join(timeout=60)
print(result.get("location", "No response"))
# Output: Lat: 52.5200, Lon: 13.4050, Battery: 72%
```

### Home Assistant example (REST command)

```yaml
# configuration.yaml
rest_command:
  locate_phone:
    url: "https://ntfy.sh/my-device-locate"
    method: POST
    payload: "LOCATE"
```

### Node-RED example

Send a `msg.payload = "LOCATE"` to an **HTTP Request** node configured as POST to `https://ntfy.sh/YOUR_LISTEN_TOPIC`. Subscribe to the reply topic with a second HTTP Request node or an MQTT input.

### Response format

The app replies with a plain-text message:

```
Lat: 52.5200, Lon: 13.4050, Battery: 72%
```

## Setup

### Prerequisites

1. Install the [ntfy Android app](https://play.google.com/store/apps/details?id=io.heckel.ntfy) on the target device.
2. Subscribe to your chosen listen topic in the ntfy app.

### App Configuration

1. Open **Helios Tracker** on the device.
2. Grant **location permissions** (including "Allow all the time" for background access).
3. Enter your **listen topic** (the topic ntfy subscribes to).
4. Enter your **reply topic** (where location responses are posted).

### Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS-based location |
| `ACCESS_COARSE_LOCATION` | WiFi/cell-based location |
| `ACCESS_BACKGROUND_LOCATION` | Location access when app is not in foreground |
| `INTERNET` | Send location via HTTP POST |

## Architecture

| Component | Role |
|---|---|
| `NtfyReceiver` | BroadcastReceiver — catches `io.heckel.ntfy.MESSAGE_RECEIVED`, filters by topic, enqueues worker |
| `LocationWorker` | CoroutineWorker — gets location via FusedLocationProviderClient, sends result via OkHttp |
| `Prefs` | SharedPreferences wrapper for topic configuration |
| `MainActivity` | Jetpack Compose UI — permission management and topic configuration |

## Build

```bash
./gradlew assembleDebug
# or install directly:
./gradlew installDebug
```

Requires Android Studio with AGP 9.0+ and JDK 17.

## License

MIT
