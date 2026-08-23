/*
 * SecureGuard Enterprise – ESP32-Gateway-Firmware
 * -----------------------------------------------
 * Funktionen:
 *   - LoRa (868 MHz) empfangen und als MQTT an den Broker weiterleiten
 *   - BLE-Peripheral mit Telemetrie-Characteristic (für die App)
 *   - MQTT-Befehle empfangen (z. B. ALARM) und verarbeiten
 *
 * Benötigte Bibliotheken (Arduino IDE Library Manager):
 *   - MCCI LoRa (oder SandeepMistry/arduino-LoRa)
 *   - ESP32 BLE Arduino
 *   - PubSubClient
 *
 * Hardware (Beispiel):
 *   - LoRa-Modul: SS=5, RST=14, DIO0=2 (Ra-02/SX1278, 868 MHz)
 *   - GPIO2: Alarm-Ausgang (LED/Buzzer)
 */

#include <LoRa.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <WiFi.h>
#include <PubSubClient.h>

// ============ KONFIGURATION ============
// HINWEIS: ssid/password/mqtt_server sind TEMPLATE-Werte und müssen pro
// Gateway angepasst werden (kein Demo-Betrieb). Alternativ per NVS/Env
// überschreiben, wenn die Firmware in eine Build-Pipeline eingebunden ist.
#define LORA_SS 5
#define LORA_RST 14
#define LORA_DIO0 2
#define LORA_FREQ 868E6

const char* ssid = "SECUREGUARD";
const char* password = "secureguard123";
const char* mqtt_server = "192.168.1.100";
const int mqtt_port = 1883;
const char* mqtt_topic = "secureguard/gw1/telemetry";

// BLE-Service (UUIDs aus der App-Konfiguration)
BLEUUID serviceUUID("6BA1B218-15A8-461F-9FA8-5DC85327FD13");
BLEUUID characteristicUUID("6BA1B218-15A8-461F-9FA8-5DC85327FD14");

WiFiClient espClient;
PubSubClient client(espClient);

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool alarmActive = false;

// ============ SETUP ============
void setup() {
  Serial.begin(115200);
  pinMode(2, OUTPUT);
  digitalWrite(2, LOW);

  // --- LoRa initialisieren ---
  SPI.begin(5, 19, 27, 18);
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(LORA_FREQ)) {
    Serial.println("LoRa init failed!");
  } else {
    Serial.println("LoRa init OK (868 MHz)");
  }

  // --- BLE initialisieren ---
  BLEDevice::init("SecureGuard_ESP32");
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

  // --- WiFi + MQTT ---
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
  client.setServer(mqtt_server, mqtt_port);
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
    Serial.println("LoRa empfangen: " + message);
    client.publish(mqtt_topic, message.c_str());
  }

  // --- BLE-Telemetrie (alle 5 Sekunden) ---
  static unsigned long lastBLE = 0;
  if (millis() - lastBLE > 5000) {
    String telemetry = "{\"type\":\"telemetry\",\"battery\":85,\"rssi\":-45,\"timestamp\":\"" +
                       String(millis()) + "\"}";
    pCharacteristic->setValue(telemetry.c_str());
    pCharacteristic->notify();
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
  Serial.print("MQTT erhalten [");
  Serial.print(topic);
  Serial.print("]: ");
  Serial.println(message);

  if (message.indexOf("ALARM") >= 0) {
    alarmActive = true;
    digitalWrite(2, HIGH);
    delay(1000);
    digitalWrite(2, LOW);
    alarmActive = false;
  } else if (message.indexOf("LIGHT") >= 0) {
    digitalWrite(2, HIGH);
    delay(5000);
    digitalWrite(2, LOW);
  }
}

// ============ MQTT-RECONNECT ============
void reconnect() {
  while (!client.connected()) {
    Serial.print("MQTT verbinden...");
    if (client.connect("ESP32Client")) {
      Serial.println("ok");
      client.subscribe("secureguard/+/command");
    } else {
      Serial.print("fehlgeschlagen (");
      Serial.print(client.state());
      Serial.println(") – Retry in 5s");
      delay(5000);
    }
  }
}
