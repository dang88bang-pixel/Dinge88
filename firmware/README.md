# ESP32-Firmware

Sketch: `secureguard_esp32/secureguard_esp32.ino`

## Abhängigkeiten (Arduino Library Manager)

| Bibliothek | Zweck |
|------------|--------|
| MCCI LoRa / Sandeep Mistry LoRa | SX1278 868 MHz |
| ESP32 BLE Arduino | GATT-Telemetrie |
| PubSubClient | MQTT |

## Hardware

- LoRa: SS=5, RST=14, DIO0=2
- Alarm-LED/Buzzer: GPIO2
- Motor-Relay: GPIO4
- Batterie-ADC: GPIO34

Konfiguration liegt in NVS (`wifi_ssid`, `wifi_pass`, `mqtt_host`, `mqtt_port`, `device_id`).
