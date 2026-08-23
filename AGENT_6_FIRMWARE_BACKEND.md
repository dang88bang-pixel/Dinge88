# 🤖 AGENT 6 – Firmware + Backend + Infrastruktur
## Scope: ESP32-Firmware, FastAPI-Backend, Docker-Compose, API-Docs

> **Ziel:** ESP32 konfigurierbar machen, Backend um WebSocket-Forwarding + Cleanup erweitern, Docker-Compose vervollständigen, API-Docs aktualisieren.  
> **Keine Änderungen an:** Kotlin/Android-Code (keine .kt-Dateien)  
> **Betroffene Dateien:** `firmware/secureguard_esp32/secureguard_esp32.ino`, `backend/main.py`, `backend/requirements.txt`, `docker-compose.yml`, `mosquitto/config/mosquitto.conf`, `docs/api-docs.yaml`

---

## TASK 6.1 – ESP32: Credentials konfigurierbar (NVS/Preferences)

**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino`

### Aktuelles Problem:
```c
const char* ssid = "SECUREGUARD";
const char* password = "secureguard123";
const char* mqtt_server = "192.168.1.100";
```

### Zielcode – NVS-basierte Konfiguration:
```cpp
#include <Preferences.h>

Preferences prefs;

// Globale Variablen (aus NVS geladen)
char wifi_ssid[64] = "";
char wifi_password[64] = "";
char mqtt_host[128] = "";
int mqtt_port = 1883;
char device_id[32] = "ESP32_SecureGuard";

void loadConfig() {
    prefs.begin("secureguard", true);  // Read-only
    String s_ssid = prefs.getString("wifi_ssid", "SECUREGUARD");
    String s_pass = prefs.getString("wifi_pass", "secureguard123");
    String s_mqtt = prefs.getString("mqtt_host", "192.168.1.100");
    mqtt_port = prefs.getInt("mqtt_port", 1883);
    String s_devid = prefs.getString("device_id", "ESP32_SecureGuard");
    prefs.end();

    s_ssid.toCharArray(wifi_ssid, sizeof(wifi_ssid));
    s_pass.toCharArray(wifi_password, sizeof(wifi_password));
    s_mqtt.toCharArray(mqtt_host, sizeof(mqtt_host));
    s_devid.toCharArray(device_id, sizeof(device_id));
}

void saveConfig(const char* ssid, const char* pass, const char* mqtt, int port) {
    prefs.begin("secureguard", false);  // Read-write
    prefs.putString("wifi_ssid", ssid);
    prefs.putString("wifi_pass", pass);
    prefs.putString("mqtt_host", mqtt);
    prefs.putInt("mqtt_port", port);
    prefs.end();
    Serial.println("Konfiguration gespeichert – Neustart nötig");
    ESP.restart();
}
```

### setup() anpassen:
```cpp
void setup() {
    Serial.begin(115200);
    pinMode(2, OUTPUT);
    digitalWrite(2, LOW);

    loadConfig();

    // LoRa
    SPI.begin(5, 19, 27, 18);
    LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);
    if (!LoRa.begin(LORA_FREQ)) {
        Serial.println("LoRa init FAILED");
    } else {
        Serial.println("LoRa init OK (868 MHz)");
    }

    // BLE
    BLEDevice::init(device_id);
    pServer = BLEDevice::createServer();
    BLEService* pService = pServer->createService(serviceUUID);
    pCharacteristic = pService->createCharacteristic(
        characteristicUUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY);
    pService->start();
    BLEAdvertising* pAdvertising = pServer->getAdvertising();
    pAdvertising->start();
    Serial.println("BLE gestartet");

    // WiFi
    WiFi.begin(wifi_ssid, wifi_password);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }
    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\nWiFi connected: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println("\nWiFi connection FAILED");
    }

    // MQTT
    client.setServer(mqtt_host, mqtt_port);
    client.setCallback(callback);
}
```

### Änderungen:
- [ ] `#include <Preferences.h>` hinzufügen
- [ ] `Preferences prefs` als globale Variable
- [ ] `loadConfig()` Funktion (NVS lesen)
- [ ] `saveConfig()` Funktion (NVS schreiben + Neustart)
- [ ] `setup()`: `loadConfig()` am Anfang aufrufen
- [ ] WiFi-SSID/Passwort aus NVS-Variablen statt Hardcodes
- [ ] MQTT-Server/Port aus NVS-Variablen
- [ ] WiFi-Connection mit Timeout (30 Versuche, dann weiter)
- [ ] `#include <Preferences.h>` hinzufügen

---

## TASK 6.2 – ESP32: Echte Telemetrie-Daten

**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino`

### Aktuelles Problem:
```cpp
String telemetry = "{\"type\":\"telemetry\",\"battery\":85,\"rssi\":-45,\"timestamp\":\"" +
                   String(millis()) + "\"}";
```

### Zielcode – Dynamische Telemetrie:
```cpp
// Globale Variablen für Sensor-Daten
int batteryRaw = 0;
int batteryPercent = 0;
int wifiRssi = 0;
int loraRssi = 0;
unsigned long uptimeSeconds = 0;

void readSensors() {
    // Batterie-Spannung über ADC (GPIO34, Spannungsteiler 100k/100k)
    batteryRaw = analogRead(34);
    // 3.3V Referenz, 12-bit ADC, Spannungsteiler 2:1
    float voltage = (batteryRaw / 4095.0) * 3.3 * 2.0;
    // LiPo: 3.0V (0%) bis 4.2V (100%)
    batteryPercent = constrain((int)((voltage - 3.0) / 1.2 * 100), 0, 100);

    // WiFi RSSI
    wifiRssi = WiFi.RSSI();

    // Uptime
    uptimeSeconds = millis() / 1000;
}

// Im loop() – BLE-Telemetrie (alle 5 Sekunden):
static unsigned long lastBLE = 0;
if (millis() - lastBLE > 5000) {
    readSensors();
    char telemetry[256];
    snprintf(telemetry, sizeof(telemetry),
        "{\"type\":\"telemetry\","
        "\"battery\":%d,"
        "\"wifi_rssi\":%d,"
        "\"lora_rssi\":%d,"
        "\"uptime\":%lu,"
        "\"ip\":\"%s\","
        "\"device\":\"%s\"}",
        batteryPercent,
        wifiRssi,
        loraRssi,
        uptimeSeconds,
        WiFi.localIP().toString().c_str(),
        device_id
    );
    pCharacteristic->setValue(telemetry);
    pCharacteristic->notify();

    // Auch per MQTT senden
    if (client.connected()) {
        char topic[64];
        snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
        client.publish(topic, telemetry);
    }

    lastBLE = millis();
}
```

### LoRa-Empfang mit RSSI:
```cpp
int packetSize = LoRa.parsePacket();
if (packetSize) {
    String message = "";
    while (LoRa.available()) {
        message += (char)LoRa.read();
    }
    loraRssi = LoRa.packetRssi();  // Echten LoRa-RSSI speichern
    Serial.printf("LoRa empfangen (RSSI %d): %s\n", loraRssi, message.c_str());

    // An MQTT weiterleiten mit RSSI-Metadaten
    char payload[512];
    snprintf(payload, sizeof(payload),
        "{\"raw\":\"%s\",\"lora_rssi\":%d,\"gateway\":\"%s\",\"ts\":%lu}",
        message.c_str(), loraRssi, device_id, uptimeSeconds);
    client.publish(mqtt_topic, payload);
}
```

### Änderungen:
- [ ] `readSensors()` Funktion mit ADC-Batterie, WiFi-RSSI, Uptime
- [ ] Batterie-Berechnung über ADC (GPIO34, Spannungsteiler)
- [ ] WiFi-RSSI: `WiFi.RSSI()`
- [ ] LoRa-RSSI: `LoRa.packetRssi()` bei Paket-Empfang speichern
- [ ] JSON-Payload mit `snprintf()` dynamisch zusammenbauen
- [ ] Telemetrie per BLE + MQTT senden
- [ ] LoRa-Payload mit RSSI + Gateway-ID + Timestamp anreichern
- [ ] IP-Adresse und Device-ID im Payload

---

## TASK 6.3 – ESP32: MQTT-Callback erweitern (Konfiguration empfangen)

**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino`

### Zielcode – Erweiterter Callback:
```cpp
void callback(char* topic, byte* payload, unsigned int length) {
    String message = "";
    for (unsigned int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    Serial.printf("MQTT [%s]: %s\n", topic, message.c_str());

    if (message.indexOf("ALARM") >= 0) {
        alarmActive = true;
        for (int i = 0; i < 5; i++) {
            digitalWrite(2, HIGH);
            delay(200);
            digitalWrite(2, LOW);
            delay(200);
        }
        alarmActive = false;
    } else if (message.indexOf("LIGHT") >= 0) {
        digitalWrite(2, HIGH);
        delay(5000);
        digitalWrite(2, LOW);
    } else if (message.indexOf("MOTOR_OFF") >= 0) {
        // Motor-Relay ausschalten (GPIO4)
        pinMode(4, OUTPUT);
        digitalWrite(4, LOW);
        Serial.println("Motor OFF");
    } else if (message.indexOf("RESTART") >= 0) {
        Serial.println("Restart angefordert...");
        ESP.restart();
    } else if (message.indexOf("CONFIG") >= 0) {
        // JSON-Konfiguration empfangen und speichern
        // Format: {"wifi_ssid":"...","wifi_pass":"...","mqtt_host":"...","mqtt_port":1883}
        // Parsing mit einfachem String-Suchen (kein JSON-Library nötig)
        parseAndSaveConfig(message);
    }
}

void parseAndSaveConfig(String json) {
    // Einfaches JSON-Parsing (ohne externe Library)
    String ssid = extractJsonValue(json, "wifi_ssid");
    String pass = extractJsonValue(json, "wifi_pass");
    String mqtt = extractJsonValue(json, "mqtt_host");
    String portStr = extractJsonValue(json, "mqtt_port");
    int port = portStr.length() > 0 ? portStr.toInt() : 1883;

    if (ssid.length() > 0 && mqtt.length() > 0) {
        saveConfig(ssid.c_str(), pass.c_str(), mqtt.c_str(), port);
    } else {
        Serial.println("CONFIG unvollständig – ignoriert");
    }
}

String extractJsonValue(String json, String key) {
    int start = json.indexOf("\"" + key + "\"");
    if (start < 0) return "";
    start = json.indexOf(":", start) + 1;
    // Skip whitespace and quote
    while (start < (int)json.length() && (json[start] == ' ' || json[start] == '"')) start++;
    int end = json.indexOf("\"", start);
    if (end < 0) end = json.indexOf(",", start);
    if (end < 0) end = json.indexOf("}", start);
    if (end < 0) return "";
    return json.substring(start, end);
}
```

### reconnect() anpassen:
```cpp
void reconnect() {
    while (!client.connected()) {
        Serial.printf("MQTT verbinden mit %s:%d...", mqtt_host, mqtt_port);
        if (client.connect(device_id)) {
            Serial.println("ok");
            char cmdTopic[64];
            snprintf(cmdTopic, sizeof(cmdTopic), "secureguard/%s/command", device_id);
            client.subscribe(cmdTopic);
            client.subscribe("secureguard/+/command");
            // Status online melden
            char statusTopic[64];
            snprintf(statusTopic, sizeof(statusTopic), "secureguard/%s/status", device_id);
            client.publish(statusTopic, "{\"status\":\"online\"}");
        } else {
            Serial.printf("fehlgeschlagen (%d) – Retry in 5s\n", client.state());
            delay(5000);
        }
    }
}
```

### Änderungen:
- [ ] `MOTOR_OFF`-Befehl implementieren (GPIO4 Relay)
- [ ] `RESTART`-Befehl implementieren (`ESP.restart()`)
- [ ] `CONFIG`-Befehl: JSON-Konfiguration empfangen + NVS speichern
- [ ] `parseAndSaveConfig()` + `extractJsonValue()` Hilfsfunktionen
- [ ] `reconnect()`: Device-spezifisches Topic abonnieren
- [ ] Online-Status bei Verbindung melden

---

## TASK 6.4 – Backend: Simulierte Verarbeitungszeit entfernen + Logging

**Datei:** `backend/main.py`

### Aktuelles Problem:
```python
async def process_action(action: Action) -> None:
    # ...
    await asyncio.sleep(2)  # Simulierte Verarbeitungszeit für Demo-Setups
    print(f"Aktion {action.action_type} für {action.asset_id} ausgeführt (mqtt={ok})")
```

### Zielcode:
```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("secureguard")

# process_action ohne sleep:
async def process_action(action: Action) -> None:
    """Führt die Aktion aus: MQTT-Befehl an das Asset senden."""
    conn = get_db()
    row = conn.execute("SELECT mac FROM assets WHERE id = ?", (action.asset_id,)).fetchone()
    conn.close()

    mac = row["mac"] if row else action.asset_id
    ok = publish_command(mac, action.action_type)

    conn = get_db()
    conn.execute(
        "UPDATE commands SET status = ? WHERE asset_id = ? AND command = ? "
        "ORDER BY timestamp DESC LIMIT 1",
        ("delivered" if ok else "failed", action.asset_id, action.action_type),
    )
    conn.commit()
    conn.close()

    logger.info(
        "Aktion %s für %s (mac=%s) → %s",
        action.action_type, action.asset_id, mac,
        "delivered" if ok else "failed"
    )
```

### Änderungen:
- [ ] `import logging` hinzufügen
- [ ] `logging.basicConfig()` konfigurieren
- [ ] `logger = logging.getLogger("secureguard")`
- [ ] `await asyncio.sleep(2)` **entfernen**
- [ ] `print()` durch `logger.info()` ersetzen

---

## TASK 6.5 – Backend: WebSocket-Forwarding (MQTT → WebSocket)

**Datei:** `backend/main.py`

### Aktuelles Problem:
WebSocket-Endpoint echo nur Nachrichten, leitet keine MQTT-Telemetrie weiter.

### Zielcode – MQTT → WebSocket Bridge:
```python
import asyncio
from typing import Set

# Aktive WebSocket-Verbindungen
active_websockets: Set[WebSocket] = set()

# MQTT-Subscriber (läuft im Hintergrund)
_mqtt_subscriber = None

def start_mqtt_subscriber():
    """Abonniert alle Telemetrie-/Alert-Topics und forwarded an WebSockets."""
    global _mqtt_subscriber
    client = get_mqtt_client()
    if client is None:
        logger.warning("MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert")
        return

    def on_message(mqtt_client, userdata, msg):
        """MQTT-Nachricht empfangen und an alle WebSocket-Clients senden."""
        topic = msg.topic
        payload = msg.payload.decode("utf-8", errors="replace")

        # Typ aus Topic ableiten
        if "/telemetry" in topic:
            ws_msg = json.dumps({"type": "telemetry", "topic": topic, "data": json.loads(payload) if payload.startswith("{") else payload})
        elif "/alert" in topic:
            ws_msg = json.dumps({"type": "alert", "topic": topic, "data": json.loads(payload) if payload.startswith("{") else payload})
        elif "/status" in topic:
            ws_msg = json.dumps({"type": "system_status", "topic": topic, "data": payload})
        else:
            ws_msg = json.dumps({"type": "unknown", "topic": topic, "data": payload})

        # An alle verbundenen WebSocket-Clients senden (async → sync Bridge)
        asyncio.run_coroutine_threadsafe(
            broadcast_websocket(ws_msg),
            asyncio.get_event_loop()
        )

    client.subscribe("secureguard/+/telemetry")
    client.subscribe("secureguard/+/alert")
    client.subscribe("secureguard/+/status")
    client.subscribe("secureguard/broadcast")
    client.on_message = on_message
    _mqtt_subscriber = client
    logger.info("MQTT-Subscriber gestartet – Topics abonniert")


async def broadcast_websocket(message: str):
    """Sendet eine Nachricht an alle verbundenen WebSocket-Clients."""
    disconnected = set()
    for ws in active_websockets:
        try:
            await ws.send_text(message)
        except Exception:
            disconnected.add(ws)
    active_websockets.difference_update(disconnected)


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    active_websockets.add(websocket)
    logger.info("WebSocket verbunden (%d aktiv)", len(active_websockets))
    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
                if msg.get("type") == "command":
                    asset_id = msg.get("assetId", "")
                    action = msg.get("action", "")
                    # Befehl über MQTT weiterleiten
                    ok = publish_command(asset_id, action)
                    await websocket.send_text(
                        json.dumps({
                            "type": "ack",
                            "assetId": asset_id,
                            "action": action,
                            "delivered": ok
                        })
                    )
                else:
                    await websocket.send_text(json.dumps({"type": "echo", "data": msg}))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"type": "error", "message": "invalid json"}))
    except Exception as exc:
        logger.debug("WebSocket getrennt: %s", exc)
    finally:
        active_websockets.discard(websocket)
        logger.info("WebSocket entfernt (%d aktiv)", len(active_websockets))


# Beim App-Start MQTT-Subscriber starten
@app.on_event("startup")
async def startup_event():
    start_mqtt_subscriber()
```

### Änderungen:
- [ ] `active_websockets: Set[WebSocket]` für verbundene Clients
- [ ] `start_mqtt_subscriber()`: MQTT-Topics abonnieren + on_message-Handler
- [ ] `on_message()`: MQTT-Payload → JSON-formatieren → an WebSockets broadcasten
- [ ] `broadcast_websocket()`: An alle aktiven WS-Clients senden
- [ ] `websocket_endpoint()`: Client in `active_websockets` registrieren/entfernen
- [ ] Commands über WebSocket → MQTT weiterleiten
- [ ] `@app.on_event("startup")`: MQTT-Subscriber beim Start initialisieren

---

## TASK 6.6 – Docker-Compose: Node-RED hinzufügen + prüfen

**Datei:** `docker-compose.yml`

### Aktuellen Stand prüfen:
```bash
cat docker-compose.yml
```

### Zielcode (falls Node-RED fehlt):
```yaml
  nodered:
    image: nodered/node-red:latest
    container_name: secureguard-nodered
    ports:
      - "1880:1880"
    volumes:
      - nodered-data:/data
    environment:
      - TZ=Europe/Berlin
    depends_on:
      - mqtt
      - backend
    restart: unless-stopped

volumes:
  nodered-data:
```

### Sicherstellen, dass alle Services vorhanden sind:
- [ ] `mqtt` (Mosquitto) – prüfen
- [ ] `backend` (FastAPI) – prüfen
- [ ] `nodered` (Node-RED) – hinzufügen falls fehlt
- [ ] Volumes korrekt definiert
- [ ] `depends_on`-Reihenfolge korrekt

### Mosquitto-Konfiguration prüfen:
**Datei:** `mosquitto/config/mosquitto.conf`
- [ ] `listener 1883` vorhanden
- [ ] `allow_anonymous true` (für Development)
- [ ] `persistence true` + `persistence_location`

---

## TASK 6.7 – API-Docs aktualisieren

**Datei:** `docs/api-docs.yaml`

### Neue Endpoints dokumentieren:
```yaml
  /api/crowd/report:
    post:
      summary: Crowd-Sichtung melden
      tags: [Crowd]
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                mac:
                  type: string
                  example: "AA:BB:CC:DD:EE:01"
                reporter_id:
                  type: string
                  example: "device-123"
                rssi:
                  type: integer
                  example: -65
                latitude:
                  type: number
                  example: 52.52
                longitude:
                  type: number
                  example: 13.40
      responses:
        "200":
          description: Sichtung gespeichert

  /api/crowd/search:
    get:
      summary: Crowd-Sichtungen abrufen
      tags: [Crowd]
      parameters:
        - name: mac
          in: query
          required: true
          schema:
            type: string
        - name: hours
          in: query
          schema:
            type: integer
            default: 24
      responses:
        "200":
          description: Liste der Sichtungen
```

### Änderungen:
- [ ] `/api/crowd/report` dokumentieren
- [ ] `/api/crowd/search` dokumentieren
- [ ] Bestehende Endpoints auf Aktualität prüfen
- [ ] WebSocket-Endpoint `/ws` dokumentieren

---

## TASK 6.8 – Backend: requirements.txt prüfen

**Datei:** `backend/requirements.txt`

### Sicherstellen, dass alle Dependencies vorhanden sind:
```
fastapi>=0.104.0
uvicorn>=0.24.0
pydantic>=2.0.0
paho-mqtt>=1.6.0
```

### Änderungen:
- [ ] `paho-mqtt` prüfen (für MQTT-Subscriber)
- [ ] Versionen auf Kompatibilität prüfen

---

## PRÜFUNG & TEST

```bash
# 1. ESP32: Preferences-Include vorhanden
grep -n "Preferences\|prefs\." firmware/secureguard_esp32/secureguard_esp32.ino
# Erwartet: mindestens 10 Treffer

# 2. ESP32: Keine hardcoded Credentials mehr als const char*
grep -n "const char\* ssid\|const char\* password\|const char\* mqtt_server" firmware/secureguard_esp32/secureguard_esp32.ino
# Erwartet: 0 Treffer

# 3. ESP32: Echte Sensor-Daten
grep -n "analogRead\|WiFi.RSSI\|LoRa.packetRssi\|readSensors" firmware/secureguard_esp32/secureguard_esp32.ino
# Erwartet: mindestens 4 Treffer

# 4. ESP32: snprintf für JSON
grep -n "snprintf" firmware/secureguard_esp32/secureguard_esp32.ino
# Erwartet: mindestens 3 Treffer

# 5. Backend: Kein asyncio.sleep in process_action
grep -A20 "async def process_action" backend/main.py | grep "sleep"
# Erwartet: 0 Treffer

# 6. Backend: Logging konfiguriert
grep -n "logging\|logger" backend/main.py
# Erwartet: mindestens 5 Treffer

# 7. Backend: WebSocket-Broadcasting
grep -n "active_websockets\|broadcast_websocket" backend/main.py
# Erwartet: mindestens 5 Treffer

# 8. Backend: MQTT-Subscriber
grep -n "start_mqtt_subscriber\|on_message" backend/main.py
# Erwartet: mindestens 3 Treffer

# 9. Docker-Compose: Node-RED vorhanden
grep -n "nodered\|node-red" docker-compose.yml
# Erwartet: mindestens 2 Treffer

# 10. Backend-Import-Test
cd /home/user/Dinge88/backend && python -c "from main import app; print('OK')"
```

---

## ABNAHMEKRITERIEN

- [ ] ESP32: WiFi/MQTT-Credentials aus NVS (Preferences) geladen
- [ ] ESP32: `saveConfig()` speichert Credentials + Neustart
- [ ] ESP32: Batterie über ADC (GPIO34) gemessen
- [ ] ESP32: WiFi-RSSI via `WiFi.RSSI()`
- [ ] ESP32: LoRa-RSSI via `LoRa.packetRssi()`
- [ ] ESP32: JSON-Payload mit `snprintf()` dynamisch
- [ ] ESP32: Telemetrie per BLE + MQTT gesendet
- [ ] ESP32: MOTOR_OFF + RESTART + CONFIG Befehle implementiert
- [ ] ESP32: Online-Status bei MQTT-Verbindung gemeldet
- [ ] Backend: `asyncio.sleep(2)` entfernt
- [ ] Backend: `logging` statt `print()`
- [ ] Backend: MQTT-Subscriber abonniert Telemetrie/Alert/Status-Topics
- [ ] Backend: MQTT-Nachrichten an alle WebSocket-Clients forwarded
- [ ] Backend: WebSocket-Commands über MQTT weitergeleitet
- [ ] Docker-Compose: Node-RED Service vorhanden
- [ ] API-Docs: Crowd-Endpoints dokumentiert
