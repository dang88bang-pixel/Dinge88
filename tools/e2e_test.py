#!/usr/bin/env python3
"""E2E-Test: MQTT → Backend-Ingestion → WebSocket-Broadcast → Command-Ack.

Ausführen (Broker + Backend müssen laufen):
  pip install paho-mqtt websockets
  MQTT_BROKER=127.0.0.1:1883 uvicorn main:app --port 8000
  python3 e2e_test.py
"""
import asyncio
import json
import subprocess
import sys
import time

import paho.mqtt.client as mqtt
import websockets

WS_URL = "ws://localhost:8000/ws"
MAC = "AA:BB:CC:00:11:22"

received = []


async def main():
    # 1) MQTT-Client (Subscriber für Command-Topic + Publisher für Telemetrie)
    mqttc = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="e2e-test")
    mqtt_messages = []
    mqttc.on_message = lambda c, u, m: mqtt_messages.append((m.topic, m.payload.decode()))
    mqttc.connect("127.0.0.1", 1883)
    mqttc.subscribe("secureguard/+/command", qos=1)
    mqttc.loop_start()

    # 2) Backend-Health aufrufen -> MQTT-Verbindung + Subscription des Backends
    health = subprocess.run(
        ["curl", "-s", "http://localhost:8000/api/health"], capture_output=True, text=True
    ).stdout
    print("[1] Health:", health)

    # 3) WebSocket verbinden und auf Broadcasts lauschen
    async with websockets.connect(WS_URL) as ws:
        await asyncio.sleep(0.5)

        # 4) Echte MQTT-Telemetrie publishen (wie ein ESP32-Gateway)
        payload = json.dumps({
            "type": "telemetry", "mac": MAC, "rssi": -58,
            "lat": 51.2277, "lng": 6.7735, "nodeId": "gw-esp32-01",
        })
        mqttc.publish(f"secureguard/{MAC}/telemetry", payload, qos=1)
        print("[2] MQTT-Telemetrie veröffentlicht:", payload)

        # 5) Auf WebSocket-Broadcast warten (Backend ingested + broadcastet)
        try:
            msg = await asyncio.wait_for(ws.recv(), timeout=5.0)
            data = json.loads(msg)
            received.append(data)
            print("[3] WS-Broadcast empfangen:", json.dumps(data, ensure_ascii=False))
            assert data.get("type") == "detection" and data.get("sourceType") == "MQTT", \
                "Falscher Broadcast-Typ!"
        except asyncio.TimeoutError:
            print("[3] FEHLER: kein WS-Broadcast innerhalb 5s")
            sys.exit(1)

        # 6) Command über WebSocket senden -> muss per MQTT published werden
        await ws.send(json.dumps({"type": "command", "assetId": MAC, "action": "ALARM"}))
        ack = json.loads(await asyncio.wait_for(ws.recv(), timeout=5.0))
        print("[4] WS-Command-ACK:", json.dumps(ack))
        assert ack.get("type") == "ack", "Kein ACK!"

        await asyncio.sleep(1.0)

    mqttc.loop_stop()
    mqttc.disconnect()

    # 7) MQTT-Command-Publish prüfen (vom Backend an den Broker)
    cmd_topics = [t for t, _ in mqtt_messages if t.endswith("/command")]
    print("[5] MQTT-Commands vom Backend empfangen:", mqtt_messages)
    assert cmd_topics, "Backend hat keinen Befehl per MQTT veröffentlicht!"

    # 8) Detektion muss jetzt in der DB stehen
    detections = subprocess.run(
        ["curl", "-s", "http://localhost:8000/api/detections"],
        capture_output=True, text=True,
    ).stdout
    assert MAC in detections and "MQTT" in detections, "MQTT-Detektion fehlt in der DB!"
    n = len(json.loads(detections))
    print(f"[6] DB enthält {n} Detektionen inkl. MQTT-Ingestion ✓")

    print("\n=== E2E ALLE PARTS AKTIV & VERIFIZIERT ✓ ===")


asyncio.run(main())
