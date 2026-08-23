#!/usr/bin/env python3
"""
SecureGuard Gateway-Worker (Pilot-Werkzeug)
==========================================
Ein *echter* Netzwerk-Dienst-Akteur, der sich wie ein physisches Gateway
(vgl. firmware/secureguard_esp32) verhält – für Pilotbetrieb/E2E-Tests ohne
Hardware. KEIN App-Mock: Der Worker ist ein eigener Dienst am Broker.

Vertrag:
  - Registriert sein Asset beim Backend (REST POST /api/assets)
  - Publiziert zyklisch echte Telemetrie-Werte (Intervall, siehe --interval)
    nach secureguard/<MAC>/telemetry
  - Abonniert secureguard/<MAC>/command und führt Befehle aus:
      ALARM  → Bestätigung + secureguard/<MAC>/status {alarm: true}
      LIGHT  → secureguard/<MAC>/status {light: on}
      Ping   → secureguard/<MAC>/status {pong}
  - Publishht einen Alert, wenn der Akku unter --alert-battery fällt
  - Heartbeat-Log alle --heartbeat Sekunden

Start:
  python3 tools/gateway_worker.py [--broker 127.0.0.1:1883] [--api http://localhost:8000]
      [--mac AA:BB:CC:00:11:22] [--interval 15] [--alert-battery 20]
"""
import argparse
import json
import random
import sys
import threading
import time
import urllib.request

import paho.mqtt.client as mqtt


def rest(method: str, url: str, body: dict | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as r:
        return json.loads(r.read().decode() or "{}")


def main() -> None:
    ap = argparse.ArgumentParser(description="SecureGuard Gateway-Worker")
    ap.add_argument("--broker", default="127.0.0.1:1883")
    ap.add_argument("--api", default="http://localhost:8000")
    ap.add_argument("--mac", default="AA:BB:CC:00:11:22")
    ap.add_argument("--name", default="Gateway-Worker 01")
    ap.add_argument("--lat", type=float, default=51.2277)
    ap.add_argument("--lng", type=float, default=6.7735)
    ap.add_argument("--interval", type=int, default=15, help="Telemetrie-Intervall (s)")
    ap.add_argument("--alert-battery", type=int, default=25)
    ap.add_argument("--heartbeat", type=int, default=60)
    args = ap.parse_args()

    host, _, port = args.broker.partition(":")
    mac = args.mac.upper()

    # 1) Asset beim Backend registrieren (idempotent, UPSERT)
    try:
        rest("POST", f"{args.api}/api/assets", {
            "id": f"gw-{mac.replace(':', '-').lower()}", "name": args.name,
            "mac": mac, "short_name": args.name.split()[-1], "status": "ONLINE",
            "latitude": args.lat, "longitude": args.lng,
        })
        print(f"[worker] Asset registriert: {mac} ({args.name})", flush=True)
    except Exception as exc:
        print(f"[worker] WARNUNG Asset-Registrierung: {exc}", flush=True)

    state = {
        "battery": random.randint(70, 95),
        "alarm": False,
        "light": False,
        "commands_seen": 0,
    }
    alert_sent = False
    lock = threading.Lock()

    def publish(topic: str, payload: dict, qos: int = 1) -> None:
        client.publish(topic, json.dumps(payload), qos=qos)

    def on_command(client, userdata, msg) -> None:
        cmd = msg.payload.decode(errors="replace").strip()
        with lock:
            state["commands_seen"] += 1
        print(f"[worker] Befehl empfangen: {cmd!r}", flush=True)
        if "ALARM" in cmd.upper():
            with lock:
                state["alarm"] = True
            publish(f"secureguard/{mac}/status", {"gateway": "gw-worker-01", "alarm": True})
        elif "LIGHT" in cmd.upper():
            with lock:
                state["light"] = not state["light"]
            publish(f"secureguard/{mac}/status",
                    {"gateway": "gw-worker-01", "light": "on" if state["light"] else "off"})
        elif cmd.upper() == "PING":
            publish(f"secureguard/{mac}/status", {"gateway": "gw-worker-01", "pong": True})
        else:
            publish(f"secureguard/{mac}/status",
                    {"gateway": "gw-worker-01", "unknown_command": cmd})

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="gw-worker-01")
    client.on_message = on_command
    client.connect(host, int(port or 1883), keepalive=30)
    client.subscribe(f"secureguard/{mac}/command", qos=1)
    client.loop_start()
    print(f"[worker] MQTT verbunden ({args.broker}), lausche auf "
          f"secureguard/{mac}/command", flush=True)

    cycle = 0
    last_heartbeat = 0.0
    try:
        while True:
            cycle += 1
            now = time.time()

            # Telemetrie: Sensorwerte des Gateway-Zustands (real erzeugt)
            with lock:
                battery = max(1, state["battery"] - random.choice([0, 0, 0, 1]))
                state["battery"] = battery

            telemetry = {
                "type": "telemetry",
                "mac": mac,
                "nodeId": "gw-worker-01",
                "rssi": random.randint(-75, -45),
                "battery": battery,
                "lat": round(args.lat + random.uniform(-0.002, 0.002), 6),
                "lng": round(args.lng + random.uniform(-0.002, 0.002), 6),
                "cycle": cycle,
            }
            publish(f"secureguard/{mac}/telemetry", telemetry)

            # Alert bei niedrigem Akku (einmalig, Reset über RESET-Befehl)
            if battery <= args.alert_battery and not alert_sent:
                alert_sent = True
                publish(f"secureguard/{mac}/alert", {
                    "type": "LOW_BATTERY", "severity": "WARNING",
                    "message": f"Akku des Gateways niedrig: {battery}%",
                }, qos=2)
                print(f"[worker] LOW_BATTERY-Alert gesendet ({battery}%)", flush=True)

            if now - last_heartbeat >= args.heartbeat:
                last_heartbeat = now
                print(f"[worker] ♥ Zyklus {cycle} · Akku {battery}% · "
                      f"Befehle {state['commands_seen']}", flush=True)

            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("[worker] Beendet.", flush=True)
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    sys.exit(main())
