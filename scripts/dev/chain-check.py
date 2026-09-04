"""End-to-End-Prüfung aller Aktions-/Interaktionsketten des SecureGuard-Stacks.

Läuft gegen die lokalen Prozesse:
  MQTT-Broker (amqtt)  :1883
  Slack-MCP-Stub       :13080
  Backend (uvicorn)    :8000

Jede Kette wird mit echten Protokollen geprüft (HTTP, WebSocket, MQTT, MCP) –
kein Mock der Business-Logik.
"""
import asyncio
import json
import os
import time

import httpx
import paho.mqtt.client as mqtt
from websockets.asyncio.client import connect

BASE = "http://127.0.0.1:8000"
MAC = "AA:BB:CC:DD:EE:01"
API_KEY = os.environ.get("SECUREGUARD_API_KEY", "")
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(f"{'✔' if ok else '✖'} {name}" + (f"  → {detail}" if detail else ""))


async def recv_type(ws, wanted, timeout=6.0):
    """Liest WS-Nachrichten bis zum gesuchten Typ (oder Timeout)."""
    end = time.time() + timeout
    while time.time() < end:
        try:
            raw = await asyncio.wait_for(ws.recv(), max(0.2, end - time.time()))
        except asyncio.TimeoutError:
            return None
        msg = json.loads(raw)
        if msg.get("type") == wanted:
            return msg
    return None


async def main():
    headers = {"X-API-Key": API_KEY} if API_KEY else {}
    http = httpx.Client(base_url=BASE, timeout=15, headers=headers)

    # ---- MQTT-Gegenseite: simuliert ESP32-Gateway (subscribe auf commands) --
    got_commands = []
    client = mqtt.Client(client_id="esp32-simulator", clean_session=True)
    client.connect("127.0.0.1", 1883, 60)
    client.subscribe("secureguard/+/command", qos=1)
    client.on_message = lambda c, u, m: got_commands.append(
        (m.topic, m.payload.decode("utf-8", "replace"))
    )
    client.loop_start()
    await asyncio.sleep(0.8)

    # ================= Kette 1: Aktion ausführen (UI → Asset) ================
    print("\n=== Kette 1 · Aktion ausführen (UI → Gateway) ===")
    r = http.post("/api/assets", json={"id": MAC, "name": "Roller #1", "mac": MAC,
                                       "short_name": "R1", "status": "ONLINE"})
    check("REST POST /api/assets (Asset anlegen)", r.status_code == 200,
          f"HTTP {r.status_code} {r.text[:60]}")

    r = http.post("/api/actions/execute",
                  json={"asset_id": MAC, "action_type": "ALARM"})
    check("REST POST /api/actions/execute akzeptiert", r.status_code == 200,
          f"HTTP {r.status_code} {r.text[:60]}")
    await asyncio.sleep(1.2)
    hit = [t for t, p in got_commands if t == f"secureguard/{MAC}/command"]
    payload = next((p for t, p in got_commands if t == f"secureguard/{MAC}/command"), "")
    check("MQTT-Publish secureguard/<MAC>/command", bool(hit), f"{len(hit)} Treffer")
    check("Command-Payload enthält Aktion", "ALARM" in payload, payload[:80])
    cmds = http.get("/api/commands").json()
    delivered = [c for c in cmds if c.get("command") == "ALARM"]
    check("Command-Historie Status=delivered",
          bool(delivered) and delivered[0].get("status") == "delivered",
          delivered[0].get("status") if delivered else "kein Eintrag")

    # ================= Kette 2: Echtzeit-Flow MQTT → WS =====================
    print("\n=== Kette 2 · Echtzeit-Flow (MQTT → WebSocket → UI) ===")
    async with connect("ws://127.0.0.1:8000/ws") as ws:
        # 2a Telemetrie
        client.publish(f"secureguard/{MAC}/telemetry",
                       json.dumps({"battery": 87, "rssi": -61}), qos=1)
        msg = await recv_type(ws, "telemetry")
        check("MQTT telemetry → WS 'telemetry'", msg is not None,
              json.dumps(msg)[:90] if msg else "keine Nachricht")
        check("Telemetrie-Payload übernommen",
              bool(msg) and msg.get("data", {}).get("battery") == 87)

        # 2b Status
        client.publish(f"secureguard/{MAC}/status", "ONLINE", qos=1)
        msg = await recv_type(ws, "system_status")
        check("MQTT status → WS 'system_status'", msg is not None,
              json.dumps(msg)[:90] if msg else "keine Nachricht")

        # 2c Broadcast-Topic
        client.publish("secureguard/broadcast", "Wartungsfenster 22:00", qos=1)
        msg = await recv_type(ws, "unknown", timeout=4)
        check("MQTT secureguard/broadcast → WS", msg is not None,
              json.dumps(msg)[:80] if msg else "keine Nachricht")

        # 2d WS-Command (UI → Backend → Gateway) inkl. ack
        got_commands.clear()
        await ws.send(json.dumps({"type": "command", "assetId": MAC,
                                  "action": "MOTOR_OFF"}))
        ack = await recv_type(ws, "ack")
        check("WS command → ack", ack is not None, json.dumps(ack)[:90] if ack else "-")
        await asyncio.sleep(0.8)
        check("WS command → MQTT an Gateway",
              any("MOTOR_OFF" in p for _, p in got_commands),
              str(got_commands)[:90])

        # 2e Fehlerpfade des WS-Kanals
        await ws.send("kein json")
        err = await recv_type(ws, "error", timeout=4)
        check("WS invalid json → error", err is not None)
        await ws.send(json.dumps({"type": "irgendwas"}))
        echo = await recv_type(ws, "echo", timeout=4)
        check("WS unbekannter Typ → echo", echo is not None)

        # 2f REST-Mutation → WS (in der App als 'asset_update' erwartet)
        http.post("/api/assets", json={"id": "AA:BB:CC:DD:EE:02", "name": "Roller #2",
                                       "mac": "AA:BB:CC:DD:EE:02", "short_name": "R2",
                                       "status": "ONLINE"})
        msg = await recv_type(ws, "asset_update", timeout=4)
        check("REST POST /api/assets → WS 'asset_update'", msg is not None,
              "App erwartet asset_update (WebSocketService.kt:77)"
              if msg is None else json.dumps(msg)[:80])

    # ================= Kette 3: Alert → Slack ===============================
    print("\n=== Kette 3 · Alert → Backend → Slack-MCP ===")
    r = http.post("/api/alerts", json={"asset_id": MAC, "type": "MOVEMENT",
                                       "severity": "CRITICAL", "message": "Bewegung"})
    check("REST POST /api/alerts", r.status_code == 200, r.text[:60])
    time.sleep(1.0)
    r = http.post("/api/slack/notify", json={"message": "Ketten-Check",
                                             "severity": "WARNING"})
    body = r.json()
    check("POST /api/slack/notify über MCP", body.get("ok") is True
          and body.get("transport") == "mcp", json.dumps(body)[:100])

    # MQTT-Alert → Slack
    client.publish(f"secureguard/{MAC}/alert",
                   json.dumps({"type": "GEOFENCE", "severity": "CRITICAL",
                               "message": "Zone verlassen"}), qos=2)
    await asyncio.sleep(1.5)

    h = http.get("/api/slack/health").json()
    check("Slack-MCP erreichbar + Tools", h.get("reachable") is True
          and len(h.get("tool_names", [])) > 0,
          f"{len(h.get('tool_names', []))} Tools, Session={h.get('session_id')}")
    t = http.get("/api/slack/tools").json()
    check("GET /api/slack/tools", len(t.get("tools", t if isinstance(t, list) else [])) > 0
          or isinstance(t, list), str(t)[:80])
    ch = http.get("/api/slack/channels").json()
    check("GET /api/slack/channels", isinstance(ch, (list, dict)), str(ch)[:80])
    call = http.post("/api/slack/call", json={"tool": "channels_list",
                                              "arguments": {}})
    check("POST /api/slack/call", call.status_code == 200, call.text[:80])

    # ================= Kette 4: Crowd ======================================
    print("\n=== Kette 4 · Crowd-Sichtung melden → suchen ===")
    r = http.post("/api/crowd/report", json={"mac": MAC, "lat": 51.43,
                                             "lon": 6.76, "source": "app"})
    check("POST /api/crowd/report", r.status_code == 200, r.text[:60])
    s = http.get("/api/crowd/search", params={"mac": MAC}).json()
    found = json.dumps(s)
    check("GET /api/crowd/search findet Sichtung", MAC.lower() in found.lower()
          or MAC in found, found[:90])

    # ================= Kette 5: Detektion → Asset-Status ===================
    print("\n=== Kette 5 · Detektion → Statistik/Health ===")
    r = http.post("/api/detections", json={"asset_mac": MAC, "source_type": "MQTT",
                                           "rssi": -58, "latitude": 51.43,
                                           "longitude": 6.76})
    check("POST /api/detections", r.status_code == 200, r.text[:60])
    st = http.get("/api/stats").json()
    check("Statistik zählt Detections", st.get("detections", 0) >= 1, str(st))
    hh = http.get("/api/health").json()
    check("Health enthält Slack-Block", "slack" in hh, str(hh.get("slack"))[:80])

    # ================= Kette 6: Abhängigkeits-Inventur =====================
    print("\n=== Kette 6 · Einstellungen → /api/system/dependencies ===")
    d = http.get("/api/system/dependencies").json()
    by_id = {x["id"]: x for x in d["dependencies"]}
    check("Inventur vollständig (5)", d.get("count") == 5, str(list(by_id)))
    check("DB reachable", by_id["database"]["reachable"] is True,
          by_id["database"]["detail"])
    check("MQTT reachable (Broker läuft)", by_id["mqtt"]["reachable"] is True,
          by_id["mqtt"]["detail"][:60])
    check("Slack-MCP reachable", by_id["slack-mcp"]["reachable"] is True,
          by_id["slack-mcp"]["detail"][:60])
    d2 = http.get("/api/system/dependencies", params={"probe": "false"}).json()
    check("probe=false überspringt Netzwerk",
          d2["probed"] is False and d2["count"] == 5)

    # ================= Kette 7: Auth-Gate =================================
    print("\n=== Kette 7 · API-Key-Schutz der Schreib-Endpunkte ===")
    anon = httpx.Client(base_url=BASE, timeout=10)
    r = anon.post("/api/slack/notify", json={"message": "x"})
    if API_KEY:
        check("POST /api/slack/notify ohne Key abgelehnt",
              r.status_code in (401, 403), f"HTTP {r.status_code}")
        r2 = http.post("/api/slack/notify", json={"message": "mit Key"})
        check("POST /api/slack/notify mit Key erlaubt", r2.status_code == 200,
              f"HTTP {r2.status_code}")
    else:
        check("Auth-Gate offen (SECUREGUARD_API_KEY nicht gesetzt)",
              r.status_code == 200,
              "by design: ohne konfigurierten Key sind POST-Endpunkte offen")
    anon.close()

    client.loop_stop()
    client.disconnect()
    http.close()

    print("\n=== Zusammenfassung ===")
    failed = [n for n, ok in results if not ok]
    print(f"{len(results) - len(failed)} ok · {len(failed)} fehlgeschlagen")
    for n in failed:
        print("  ✖", n)


asyncio.run(main())
