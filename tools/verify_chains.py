#!/usr/bin/env python3
"""
SecureGuard – vollständige Ketten-Verifikation
Testet alle Aktions- und Interaktionsketten des Live-Stacks:
  K1  Asset-Lebenszyklus (REST)
  K2  Gateway-Telemetrie: MQTT → Ingestion → DB → WS-Broadcast
  K3  Alarm-Kette: WS-Command → ACK → MQTT-Downlink → Status delivered
  K4  Alert-Kette: MQTT-Alert → Ingestion → WS-Broadcast → REST
  K5  WS-Protokoll: Echo / Fehlerbehandlung
  K6  Multi-Client-Broadcast
  K7  Broker-Verteilung (Dritt-Publisher → Subscriber)
"""
import asyncio
import json
import subprocess
import sys
import time

import paho.mqtt.client as mqtt
import websockets

WS = "ws://localhost:8000/ws"
API = "http://localhost:8000"
MAC = "AA:BB:CC:00:11:22"

PASS, FAIL = 0, 0


def ok(name, cond, detail=""):
    global PASS, FAIL
    mark = "✓" if cond else "✗ FEHLER"
    print(f"  [{mark}] {name}" + (f" – {detail}" if detail and not cond else ""))
    PASS += 1 if cond else 0
    FAIL += 0 if cond else 1


def curl(method, path, body=None):
    cmd = ["curl", "-s", "-X", method, f"{API}{path}"]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(body)]
    r = subprocess.run(cmd, capture_output=True, text=True).stdout
    try:
        return json.loads(r)
    except Exception:
        return r


async def main():
    print("=" * 70)
    print("K1 · ASSET-LEBENSZYKLUS (REST)")
    print("=" * 70)
    a = curl("POST", "/api/assets", {"id": "k1-asset", "name": "Ketten-Test-E-Scooter",
                                     "mac": MAC, "short_name": "K1", "status": "ONLINE",
                                     "latitude": 51.2277, "longitude": 6.7735})
    ok("Asset anlegen", a.get("id") == "k1-asset")
    lst = curl("GET", "/api/assets")
    ok("Asset-Liste enthält Asset", any(x["id"] == "k1-asset" for x in lst))
    det = curl("POST", "/api/detections", {"asset_mac": MAC, "source_type": "BLE",
                                           "node_id": "ble-real", "rssi": -47,
                                           "latitude": 51.2277, "longitude": 6.7735})
    ok("Detection per REST erfassen", det.get("status") == "ok")
    dets = curl("GET", "/api/detections")
    ok("Detection persistiert", any(d["asset_mac"] == MAC and d["source_type"] == "BLE" for d in dets))
    st = curl("GET", "/api/stats")
    ok("Stats konsistent", st.get("assets", 0) >= 1 and st.get("detections", 0) >= 1)

    # MQTT-Client (Gateway-Simulator + Command-Empfänger)
    mqtt_msgs = []
    m = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="chain-verifier")
    m.on_message = lambda c, u, msg: mqtt_msgs.append((msg.topic, msg.payload.decode()))
    m.connect("127.0.0.1", 1883)
    m.subscribe([("secureguard/+/command", 1), ("secureguard/+/status", 1), ("secureguard/broadcast", 1)])
    m.loop_start()

    print()
    print("=" * 70)
    print("K2 · GATEWAY-TELEMETRIE: MQTT → INGESTION → DB → WS-BROADCAST")
    print("=" * 70)
    async with websockets.connect(WS) as ws1:
        await asyncio.sleep(0.3)
        m.publish(f"secureguard/{MAC}/telemetry", json.dumps(
            {"type": "telemetry", "mac": MAC, "rssi": -63, "lat": 51.23, "lng": 6.78,
             "nodeId": "gw-esp32-k2"}), qos=1)
        try:
            msg = json.loads(await asyncio.wait_for(ws1.recv(), 5))
            ok("WS-Broadcast empfangen", msg.get("type") == "detection")
            ok("Inhalt echt (MQTT-Quelle, Node, RSSI)",
               msg.get("sourceType") == "MQTT" and msg.get("nodeId") == "gw-esp32-k2"
               and msg.get("rssi") == -63,
               json.dumps(msg))
        except asyncio.TimeoutError:
            ok("WS-Broadcast empfangen", False, "Timeout")
        dets = curl("GET", "/api/detections")
        ok("Telemetrie in DB ingestiert",
           any(d["node_id"] == "gw-esp32-k2" for d in dets))

        print()
        print("=" * 70)
        print("K3 · ALARM-KETTE: WS-COMMAND → ACK → MQTT-DOWNLINK → STATUS")
        print("=" * 70)
        await ws1.send(json.dumps({"type": "command", "assetId": MAC, "action": "ALARM"}))
        ack = json.loads(await asyncio.wait_for(ws1.recv(), 5))
        ok("WS-ACK erhalten", ack.get("type") == "ack" and ack.get("action") == "ALARM")
        await asyncio.sleep(1.0)
        downlinks = [t for t, _ in mqtt_msgs if t.endswith("/command")]
        ok("MQTT-Downlink am Gateway angekommen", bool(downlinks), str(mqtt_msgs))
        ok("Command-Status = delivered",
           any(c["status"] == "delivered" for c in curl("GET", "/api/commands")))

        # Gateway antwortet mit Status (Kette weiter)
        m.publish(f"secureguard/{MAC}/status", json.dumps({"status": "alarm-on"}), qos=1)

        print()
        print("=" * 70)
        print("K4 · ALERT-KETTE: MQTT-ALERT → INGESTION → WS → REST")
        print("=" * 70)
        m.publish(f"secureguard/{MAC}/alert", json.dumps(
            {"type": "GEOFENCE", "severity": "CRITICAL", "message": "Geofence verlassen (K4)"}),
            qos=1)
        got_alert = False
        try:
            for _ in range(3):
                msg = json.loads(await asyncio.wait_for(ws1.recv(), 5))
                if msg.get("type") == "alert":
                    got_alert = True
                    ok("WS-Alert-Broadcast", msg.get("message") == "Geofence verlassen (K4)")
                    break
        except asyncio.TimeoutError:
            pass
        ok("WS-Alert-Broadcast empfangen", got_alert)
        alerts = curl("GET", "/api/alerts?unresolved_only=true")
        ok("Alert in DB (unresolved)", any("Geofence verlassen (K4)" == x["message"] for x in alerts))

        print()
        print("=" * 70)
        print("K5 · WS-PROTOKOLL: ECHO & FEHLERBEHANDLUNG")
        print("=" * 70)
        await ws1.send(json.dumps({"type": "unknown", "data": 42}))
        echo = json.loads(await asyncio.wait_for(ws1.recv(), 5))
        ok("Echo für unbekannten Typ", echo.get("type") == "echo" and echo.get("data", {}).get("data") == 42)
        await ws1.send("{{{kein json")
        err = json.loads(await asyncio.wait_for(ws1.recv(), 5))
        ok("Fehlerbehandlung ungültiges JSON", err.get("type") == "error")

        print()
        print("=" * 70)
        print("K6 · MULTI-CLIENT-BROADCAST")
        print("=" * 70)
        async with websockets.connect(WS) as ws2:
            await asyncio.sleep(0.3)
            curl("POST", "/api/detections", {"asset_mac": MAC, "source_type": "URBAN",
                                             "node_id": "hub-verify", "rssi": -70})
            got1 = got2 = None
            try:
                got1 = json.loads(await asyncio.wait_for(ws1.recv(), 5))
                got2 = json.loads(await asyncio.wait_for(ws2.recv(), 5))
            except asyncio.TimeoutError:
                pass
            ok("Client 1 erhält Broadcast", got1 is not None and got1.get("type") == "detection")
            ok("Client 2 erhält Broadcast", got2 is not None and got2.get("type") == "detection")

    print()
    print("=" * 70)
    print("K7 · BROKER-VERTEILUNG (Dritt-Publisher → Subscriber)")
    print("=" * 70)
    third = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="third-publisher")
    third.connect("127.0.0.1", 1883)
    third.loop_start()
    third.publish("secureguard/broadcast", json.dumps({"event": "fleet-sync"}), qos=1)
    await asyncio.sleep(1.0)
    ok("Broadcast-Topic verteilt", any(t == "secureguard/broadcast" for t, _ in mqtt_msgs))
    third.loop_stop(); third.disconnect()

    m.loop_stop(); m.disconnect()

    print()
    print("=" * 70)
    print(f"ERGEBNIS: {PASS} bestanden · {FAIL} fehlgeschlagen")
    print("=" * 70)
    sys.exit(1 if FAIL else 0)


asyncio.run(main())
