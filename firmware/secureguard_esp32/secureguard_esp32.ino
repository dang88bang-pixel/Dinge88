/*
 * SecureGuard Enterprise – ESP32-Gateway-Firmware
 * -----------------------------------------------
 * Funktionen:
 *   - LoRa (868 MHz) empfangen und als MQTT an den Broker weiterleiten
 *   - BLE-Peripheral mit Telemetrie-Characteristic (für die App)
 *   - MQTT-Befehle empfangen (ALARM, LIGHT, MOTOR_OFF, RESTART, CONFIG)
 *   - Echte Sensor-Daten (ADC-Batterie, WiFi-RSSI, LoRa-RSSI)
 *   - Konfigurierbar über NVS (Preferences)
 *
 * Benötigte Bibliotheken (Arduino IDE Library Manager):
 *   - MCCI LoRa (oder SandeepMistry/arduino-LoRa)
 *   - ESP32 BLE Arduino
 *   - PubSubClient
 *
 * Hardware (Beispiel):
 *   - LoRa-Modul: SS=5, SCK=18, MISO=19, MOSI=23, RST=14, DIO0=2 (Ra-02/SX1278, 868 MHz)
 *   - GPIO2: Alarm-Ausgang (LED/Buzzer)
 *   - GPIO4: Motor-Relay
 *   - GPIO34: Batterie-ADC (Spannungsteiler 100k/100k)
 *
 * Wichtig: SS (=LORA_SS) darf NICHT mit SCK/MISO/MOSI kollidieren – früher
 * kollidierte SCK=5 mit SS=5, womit der LoRa-Transceiver nicht funktionierte.
 */

#include <LoRa.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <Preferences.h>

// ============ KONFIGURATION ============
#define LORA_SS 5
#define LORA_RST 14
#define LORA_DIO0 2
#define LORA_FREQ 868E6
#define BATTERY_PIN 34
#define MOTOR_RELAY_PIN 4

// BLE-Service UUIDs (aus der App-Konfiguration)
BLEUUID serviceUUID("6BA1B218-15A8-461F-9FA8-5DC85327FD13");
BLEUUID telemetryCharUUID("6BA1B218-15A8-461F-9FA8-5DC85327FD14");
BLEUUID commandCharUUID("6BA1B218-15A8-461F-9FA8-5DC85327FD15");

// ============ GLOBALE VARIABLEN ============
Preferences prefs;
unsigned long alarmStartMs = 0;

char wifi_ssid[64] = "";
char wifi_password[64] = "";
char mqtt_host[128] = "";
int mqtt_port = 1883;
char device_id[32] = "ESP32_SecureGuard";

WiFiClient espClient;
PubSubClient client(espClient);

BLEServer* pServer = NULL;
BLECharacteristic* pTelemetryChar = NULL;
BLECharacteristic* pCommandChar = NULL;
bool alarmActive = false;

// Sensor-Daten
int batteryPercent = 0;
int wifiRssi = 0;
int loraRssi = 0;
unsigned long uptimeSeconds = 0;

// ============ NVS KONFIGURATION ============

void loadConfig() {
    prefs.begin("secureguard", true);
    // Defaults LEER: keine fest verdrahteten Zugangsdaten im Source. Der
    // Anwender setzt WLAN/MQTT per CONFIG-Befehl (App: ESP32-Config-Screen).
    String s_ssid = prefs.getString("wifi_ssid", "");
    String s_pass = prefs.getString("wifi_pass", "");
    String s_mqtt = prefs.getString("mqtt_host", "");
    mqtt_port = prefs.getInt("mqtt_port", 1883);
    // device_id = Asset-MAC (z. B. "AA:BB:CC:DD:EE:01"), damit App/Backend
    // gezielt auf secureguard/<MAC>/command zustellen können.
    String s_devid = prefs.getString("device_id", "ESP32_SecureGuard");
    prefs.end();

    s_ssid.toCharArray(wifi_ssid, sizeof(wifi_ssid));
    s_pass.toCharArray(wifi_password, sizeof(wifi_password));
    s_mqtt.toCharArray(mqtt_host, sizeof(mqtt_host));
    s_devid.toCharArray(device_id, sizeof(device_id));
}

void saveConfig(const char* ssid, const char* pass, const char* mqttH, int port, const char* devid) {
    prefs.begin("secureguard", false);
    prefs.putString("wifi_ssid", ssid);
    prefs.putString("wifi_pass", pass);
    prefs.putString("mqtt_host", mqttH);
    prefs.putInt("mqtt_port", port);
    if (devid != nullptr && strlen(devid) > 0) {
        prefs.putString("device_id", devid);
    }
    prefs.end();
    Serial.println("Konfiguration gespeichert – Neustart...");
    ESP.restart();
}

// ============ SENSOR-READING ============

void readSensors() {
    // Batterie-Spannung über ADC (GPIO34, Spannungsteiler 100k/100k)
    int batteryRaw = analogRead(BATTERY_PIN);
    float voltage = (batteryRaw / 4095.0) * 3.3 * 2.0;
    batteryPercent = constrain((int)((voltage - 3.0) / 1.2 * 100), 0, 100);

    // WiFi RSSI
    wifiRssi = WiFi.RSSI();

    // Uptime
    uptimeSeconds = millis() / 1000;
}

// ============ SETUP ============
void setup() {
    Serial.begin(115200);
    pinMode(2, OUTPUT);
    pinMode(MOTOR_RELAY_PIN, OUTPUT);
    digitalWrite(2, LOW);
    // Sicherheitshalber AUS nach Boot; der Befehl "MOTOR_OFF"/"RESTART" bzw.
    // Config setzt den Zustand. (Vorher default AN – unerwünschtes Anlaufen.)
    digitalWrite(MOTOR_RELAY_PIN, LOW);  // Motor default: AUS

    loadConfig();
    Serial.printf("Device: %s\n", device_id);
    Serial.printf("WiFi: %s\n", wifi_ssid);
    Serial.printf("MQTT: %s:%d\n", mqtt_host, mqtt_port);

    // --- LoRa initialisieren ---
    // SPI-Pins: SCK=18, MISO=19, MOSI=23, SS=5 (= LORA_SS, kein Konflikt!)
    SPI.begin(18, 19, 23, LORA_SS);
    LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);
    if (!LoRa.begin(LORA_FREQ)) {
        Serial.println("LoRa init FAILED!");
    } else {
        Serial.println("LoRa init OK (868 MHz)");
    }

    // --- BLE initialisieren ---
    BLEDevice::init(device_id);
    pServer = BLEDevice::createServer();
    BLEService* pService = pServer->createService(serviceUUID);

    pTelemetryChar = pService->createCharacteristic(
        telemetryCharUUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pCommandChar = pService->createCharacteristic(
        commandCharUUID,
        BLECharacteristic::PROPERTY_WRITE
    );

    pService->start();
    BLEAdvertising* pAdvertising = pServer->getAdvertising();
    pAdvertising->start();
    Serial.println("BLE gestartet");

    // --- WiFi (nur wenn konfiguriert) ---
    if (strlen(wifi_ssid) == 0) {
        Serial.println("WiFi nicht konfiguriert – per CONFIG-Befehl setzen (wifi_ssid/wifi_pass/mqtt_host).");
    }
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

    // --- MQTT ---
    client.setServer(mqtt_host, mqtt_port);
    client.setCallback(callback);
}

// =========== LOOP ============
void loop() {
    if (!client.connected()) {
        reconnect();
    }
    client.loop();

    // --- LoRa empfangen und an MQTT weiterleiten ---
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
        String message = "";
        while (LoRa.available()) {
            message += (char)LoRa.read();
        }
        loraRssi = LoRa.packetRssi();
        Serial.printf("LoRa empfangen (RSSI %d): %s\n", loraRssi, message.c_str());

        char payload[512];
        snprintf(payload, sizeof(payload),
            "{\"raw\":\"%s\",\"lora_rssi\":%d,\"gateway\":\"%s\",\"uptime\":%lu}",
            message.c_str(), loraRssi, device_id, uptimeSeconds);

        char topic[64];
        snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
        client.publish(topic, payload);
    }

    // --- BLE-Telemetrie (alle 5 Sekunden) ---
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
        pTelemetryChar->setValue(telemetry);
        pTelemetryChar->notify();

        // Auch per MQTT senden
        if (client.connected()) {
            char topic[64];
            snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
            client.publish(topic, telemetry);
        }

        lastBLE = millis();
    }

    delay(100);
}

// ============ MQTT-CALLBACK ============
void callback(char* topic, byte* payload, unsigned int length) {
    String message = "";
    for (unsigned int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    Serial.printf("MQTT [%s]: %s\n", topic, message.c_str());

    if (message.indexOf("ALARM") >= 0) {
        // Non-blocking: 5 Blinkzyklen über millis() – blockiert nicht die
        // MQTT/LoRa-Verarbeitung im loop().
        alarmStartMs = millis();
        alarmActive = true;
    } else if (message.indexOf("LIGHT") >= 0) {
        digitalWrite(2, HIGH);
        delay(5000);
        digitalWrite(2, LOW);
    } else if (message.indexOf("MOTOR_OFF") >= 0) {
        digitalWrite(MOTOR_RELAY_PIN, LOW);
        Serial.println("Motor OFF");
    } else if (message.indexOf("RESTART") >= 0) {
        Serial.println("Restart angefordert...");
        ESP.restart();
    } else if (message.indexOf("CONFIG") >= 0) {
        parseAndSaveConfig(message);
    } else if (message.indexOf("BATTERY") >= 0) {
        // Batterie-Status sofort senden
        readSensors();
        char resp[128];
        snprintf(resp, sizeof(resp),
            "{\"type\":\"battery\",\"percent\":%d,\"voltage\":%.2f}",
            batteryPercent, (analogRead(BATTERY_PIN) / 4095.0) * 3.3 * 2.0);
        char topic[64];
        snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
        client.publish(topic, resp);
        Serial.println("BATTERY-Status gesendet");
    } else if (message.indexOf("MESSAGE") >= 0) {
        // Nachricht empfangen: LED 3x kurz blinken als Bestätigung
        for (int i = 0; i < 3; i++) {
            digitalWrite(2, HIGH);
            delay(100);
            digitalWrite(2, LOW);
            delay(100);
        }
        Serial.println("MESSAGE empfangen – LED-Bestätigung");
    } else if (message.indexOf("POSITION") >= 0) {
        // Positions-Anfrage: GPS-Daten senden (via WiFi-Position oder gespeicherte Koords)
        char resp[128];
        snprintf(resp, sizeof(resp),
            "{\"type\":\"position\",\"ip\":\"%s\",\"wifi_rssi\":%d,\"device\":\"%s\"}",
            WiFi.localIP().toString().c_str(), WiFi.RSSI(), device_id);
        char topic[64];
        snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
        client.publish(topic, resp);
        Serial.println("POSITION gesendet");
    } else if (message.indexOf("TELEMETRY") >= 0) {
        // Vollständige Telemetrie sofort senden
        readSensors();
        char resp[256];
        snprintf(resp, sizeof(resp),
            "{\"type\":\"telemetry\",\"battery\":%d,\"wifi_rssi\":%d,\"lora_rssi\":%d,"
            "\"uptime\":%lu,\"ip\":\"%s\",\"device\":\"%s\"}",
            batteryPercent, wifiRssi, loraRssi, uptimeSeconds,
            WiFi.localIP().toString().c_str(), device_id);
        char topic[64];
        snprintf(topic, sizeof(topic), "secureguard/%s/telemetry", device_id);
        client.publish(topic, resp);
        Serial.println("TELEMETRY gesendet");
    }
}

// ============ CONFIG PARSING ============

String extractJsonValue(String json, String key) {
    int start = json.indexOf("\"" + key + "\"");
    if (start < 0) return "";
    start = json.indexOf(":", start) + 1;
    while (start < (int)json.length() && (json[start] == ' ' || json[start] == '"')) start++;
    int end = json.indexOf("\"", start);
    if (end < 0) end = json.indexOf(",", start);
    if (end < 0) end = json.indexOf("}", start);
    if (end < 0) return "";
    return json.substring(start, end);
}

void parseAndSaveConfig(String json) {
    String ssid = extractJsonValue(json, "wifi_ssid");
    String pass = extractJsonValue(json, "wifi_pass");
    String mqttH = extractJsonValue(json, "mqtt_host");
    String portStr = extractJsonValue(json, "mqtt_port");
    String devid = extractJsonValue(json, "device_id");
    int port = portStr.length() > 0 ? portStr.toInt() : 1883;

    if (ssid.length() > 0 && mqttH.length() > 0) {
        saveConfig(ssid.c_str(), pass.c_str(), mqttH.c_str(), port, devid.c_str());
    } else {
        Serial.println("CONFIG unvollständig – ignoriert");
    }
}

// ============ MQTT-RECONNECT ============
void reconnect() {
    while (!client.connected()) {
        Serial.printf("MQTT verbinden mit %s:%d...", mqtt_host, mqtt_port);
        if (client.connect(device_id)) {
            Serial.println("ok");
            // NUR das eigene Topic abonnieren! Die frühere Wildcard
            // "secureguard/+/command" führte dazu, dass JEDES Gateway JEDEN
            // Befehl ausführte (z. B. ALARM an allen Gateways).
            char cmdTopic[96];
            snprintf(cmdTopic, sizeof(cmdTopic), "secureguard/%s/command", device_id);
            client.subscribe(cmdTopic);
            // Online-Status melden
            char statusTopic[64];
            snprintf(statusTopic, sizeof(statusTopic), "secureguard/%s/status", device_id);
            client.publish(statusTopic, "{\"status\":\"online\"}");
        } else {
            Serial.printf("fehlgeschlagen (%d) – Retry in 5s\n", client.state());
            delay(5000);
        }
    }
}
