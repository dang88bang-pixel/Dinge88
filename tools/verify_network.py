#!/usr/bin/env python3
"""Netzwerk- & Anbindungsmatrix: alle Dienste, alle Verbindungen live geprüft."""
import asyncio
import json
import subprocess
import sys
import time

import paho.mqtt.client as mqtt
import websockets

API = "http://localhost:8000"
MAC = "AA:BB:CC:00:11:22"
P, F = 0, 0


def ok(name, cond, detail=""):
    global P, F
    print(f"  [{'✓' if cond else '✗ FEHLER'}] {name}" + (f" – {detail}" if detail and not cond else ""))
    P, F = P + (1 if cond else 0), F + (0 if cond else 1)


def curl(path):
    return subprocess.run(["curl", "-s", f"{API}{path}"], capture_output=True, text=True).stdout


async def main():
    print("=" * 66)
    print("NETZWERK- & ANBINDUNGSMATRIX (alle Dienste aktiv)")
    print("=" * 66)

    print("\n▶ TCP-Erreichbarkeit aller Dienste")
    for name, port in [("MQTT-Broker (TCP)", 1883), ("MQTT-Broker (WS)", 9001),
                       ("Backend API", 8000), ("Node-RED", 1880)]:
        r = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                            f"http://localhost:{port}/"], capture_output=True, text=True)
        # 1883 = MQTT-Protokoll (kein HTTP) – Port-Check via bash
        port_open = subprocess.run(["bash", "-c", f"(echo > /dev/tcp/127.0.0.1/{port})"]).returncode == 0
        ok(f"{name} :{port}", port_open)

    print("\n▶ Anbindung 1: Backend ↔ MQTT-Broker")
    h = json.loads(curl("/api/health"))
    ok("Backend-Health OK", h.get("status") == "ok")
    ok("Backend hat MQTT-Verbindung (Subscription aktiv)", h.get("mqtt") is True)

    print("\n▶ Anbindung 2: Gateway-Worker ↔ Broker ↔ Backend ↔ WebSocket")
    async with websockets.connect("ws://localhost:8000/ws") as ws:
        # Auf die nächste Live-Telemetrie des Workers warten (Intervall 10 s)
        got = None
        try:
            for _ in range(4):
                msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=12))
                if msg.get("type") == "detection" and msg.get("nodeId") == "gw-worker-01":
                    got = msg
                    break
        except asyncio.TimeoutError:
            pass
        ok("Worker-Telemetrie fließt live durch alle Ebenen (MQTT→DB→WS)",
           got is not None and got.get("sourceType") == "MQTT")

        print("\n▶ Anbindung 3: Command-Roundtrip zum Worker (WS→MQTT→Worker→Status)")
        m = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="matrix-check")
        msgs = []
        m.on_message = lambda c, u, x: msgs.append((x.topic, x.payload.decode()))
        m.connect("127.0.0.1", 1883)
        m.subscribe(f"secureguard/{MAC}/status", qos=1)
        m.loop_start()
        await ws.send(json.dumps({"type": "command", "assetId": MAC, "action": "PING"}))
        ack = json.loads(await asyncio.wait_for(ws.recv(), 12))
        ok("WS-ACK vom Backend", ack.get("type") == "ack" and ack.get("delivered") is True)
        await asyncio.sleep(1.2)
        ok("Worker hat Befehl ausgeführt (Status-Pong am Broker)",
           any(t.endswith("/status") and "pong" in p.lower() for t, p in msgs),
           str(msgs))
        m.loop_stop(); m.disconnect()

    print("\n▶ Anbindung 4: Worker-Telemetrie in der DB (Ingestion dauerhaft)")
    dets = json.loads(curl("/api/detections?limit=100")) if "limit" in curl("/api/detections") \
        else json.loads(curl("/api/detections"))
    worker_dets = [d for d in dets if d.get("node_id") == "gw-worker-01"]
    ok(f"Worker-Detektionen persistiert ({len(worker_dets)} Einträge)", len(worker_dets) >= 1)

    print("\n▶ Anbindung 5: REST-Oberfläche (Assets/Stats/Commands)")
    assets = json.loads(curl("/api/assets"))
    ok("Worker-Asset registriert", any(a["mac"] == MAC for a in assets))
    cmds = json.loads(curl("/api/commands"))
    ok("Command-Historie getrackt", len(cmds) >= 1)
    st = json.loads(curl("/api/stats"))
    ok(f"Stats live: {st.get('assets')} Assets · {st.get('detections')} Detektionen · {st.get('alerts')} Alerts",
       st.get("detections", 0) >= len(worker_dets))

    print("\n▶ Anbindung 6: Node-RED ↔ Broker (Flow aktiv)")
    flows = subprocess.run(["curl", "-s", "http://localhost:1880/flows"],
                           capture_output=True, text=True).stdout
    fl = json.loads(flows) if flows else []
    mqtt_nodes = [n for n in fl if isinstance(n, dict) and n.get("type") in ("mqtt in", "mqtt out")]
    ok(f"Node-RED MQTT-Nodes im Flow ({len(mqtt_nodes)})", len(mqtt_nodes) >= 2)

    print("\n" + "=" * 66)
    print(f"ERGEBNIS: {P} bestanden · {F} fehlgeschlagen")
    print("=" * 66)
    sys.exit(1 if F else 0)


asyncio.run(main())
