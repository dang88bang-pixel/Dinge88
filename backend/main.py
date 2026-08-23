# SecureGuard Enterprise – Backend (FastAPI)
# ------------------------------------------
# REST + WebSocket-Backend für die SecureGuard-App:
#  - Asset-Verwaltung, Detektionen, Alerts, Befehls-Queue
#  - MQTT-Publish an den Broker (Befehle an Gateways/ESP32)
#  - WebSocket-Endpunkt für Echtzeit-Updates an die App
#
# Start:
#   pip install -r requirements.txt
#   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# Oder: docker compose up --build

import asyncio
import json
import os
import sqlite3
from datetime import datetime
from typing import List, Optional

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover
    mqtt = None

DB_PATH = os.environ.get("DATABASE_PATH", "secureguard.db")
MQTT_BROKER = os.environ.get("MQTT_BROKER", "mqtt:1883")

app = FastAPI(title="SecureGuard API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============ MODELLE ============

class Asset(BaseModel):
    id: str
    name: str
    mac: str
    short_name: str
    status: str = "UNKNOWN"
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    rssi: int = 0
    last_seen: Optional[datetime] = None


class Detection(BaseModel):
    asset_mac: str
    source_type: str
    node_id: Optional[str] = None
    rssi: int = 0
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    timestamp: Optional[datetime] = None


class Alert(BaseModel):
    asset_id: str
    type: str = "SECURITY"
    severity: str = "WARNING"
    message: str
    timestamp: Optional[datetime] = None


class Action(BaseModel):
    asset_id: str
    action_type: str
    parameters: Optional[dict] = None


# ============ DATENBANK ============

def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_db()
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS assets (
            id TEXT PRIMARY KEY,
            name TEXT,
            mac TEXT UNIQUE,
            short_name TEXT,
            status TEXT,
            latitude REAL,
            longitude REAL,
            rssi INTEGER,
            last_seen TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS detections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_mac TEXT,
            source_type TEXT,
            node_id TEXT,
            rssi INTEGER,
            latitude REAL,
            longitude REAL,
            timestamp TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS alerts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_id TEXT,
            type TEXT,
            severity TEXT,
            message TEXT,
            timestamp TIMESTAMP,
            resolved INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS commands (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asset_id TEXT,
            command TEXT,
            status TEXT,
            timestamp TIMESTAMP
        );
        """
    )
    conn.commit()
    conn.close()


init_db()

# ============ MQTT (optional) ============

_mqtt_client = None


def on_mqtt_message(client, userdata, msg):
    """Echte Ingestion: MQTT-Telemetrie/Alerts → DB + WebSocket-Broadcast."""
    try:
        payload = json.loads(msg.payload.decode("utf-8", errors="replace"))
    except Exception:
        payload = {"raw": msg.payload.decode("utf-8", errors="replace")}

    topic_parts = msg.topic.split("/")
    asset_mac = topic_parts[1] if len(topic_parts) > 1 else "unknown"
    kind = topic_parts[2] if len(topic_parts) > 2 else "telemetry"

    conn = get_db()
    try:
        if kind == "alert":
            conn.execute(
                "INSERT INTO alerts (asset_id, type, severity, message, timestamp, resolved) "
                "VALUES (?, ?, ?, ?, ?, 0)",
                (
                    payload.get("assetId", asset_mac),
                    payload.get("type", "SECURITY"),
                    payload.get("severity", "WARNING"),
                    payload.get("message", str(payload)),
                    datetime.now(),
                ),
            )
            conn.commit()
            manager.broadcast({"type": "alert", "assetId": payload.get("assetId", asset_mac),
                       "message": payload.get("message", str(payload))})
        else:
            conn.execute(
                "INSERT INTO detections "
                "(asset_mac, source_type, node_id, rssi, latitude, longitude, timestamp) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    payload.get("mac", asset_mac),
                    "MQTT",
                    payload.get("nodeId", msg.topic),
                    int(payload.get("rssi", 0)),
                    payload.get("lat") or payload.get("latitude"),
                    payload.get("lng") or payload.get("longitude"),
                    datetime.now(),
                ),
            )
            conn.commit()
            manager.broadcast({
                "type": "detection",
                "assetMac": payload.get("mac", asset_mac),
                "sourceType": "MQTT",
                "nodeId": payload.get("nodeId", msg.topic),
                "rssi": int(payload.get("rssi", 0)),
                "latitude": payload.get("lat") or payload.get("latitude"),
                "longitude": payload.get("lng") or payload.get("longitude"),
            })
    except Exception as exc:
        print(f"MQTT-Ingestion-Fehler: {exc}")
    finally:
        conn.close()


def get_mqtt_client():
    """Liefert einen (lazy) MQTT-Client; None, wenn paho fehlt."""
    global _mqtt_client
    if mqtt is None:
        return None
    if _mqtt_client is None:
        try:
            host, _, port = MQTT_BROKER.partition(":")
            _mqtt_client = mqtt.Client(client_id="secureguard-backend")
            _mqtt_client.on_message = on_mqtt_message
            _mqtt_client.on_connect = lambda c, u, f, rc: (
                c.subscribe([("secureguard/+/telemetry", 1), ("secureguard/+/alert", 2)])
            )
            _mqtt_client.connect(host, int(port or 1883), 60)
            _mqtt_client.loop_start()
        except Exception:
            _mqtt_client = None
    return _mqtt_client


def publish_command(asset_mac: str, command: str) -> bool:
    client = get_mqtt_client()
    if client is None:
        return False
    try:
        client.publish(f"secureguard/{asset_mac}/command", command, qos=1)
        return True
    except Exception:
        return False


# ============ WEBSOCKET-CONNECTION-MANAGER ============

class ConnectionManager:
    """Hält alle verbundenen WebSocket-Clients und broadcastet echte Events."""

    def __init__(self):
        self.active: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active:
            self.active.remove(websocket)

    async def send(self, websocket: WebSocket, message: dict):
        try:
            await websocket.send_text(json.dumps(message))
        except Exception:
            self.disconnect(websocket)

    async def broadcast_async(self, message: dict):
        for ws in list(self.active):
            await self.send(ws, message)

    def broadcast(self, message: dict):
        """Thread-sicherer Fire-and-forget-Broadcast (auch aus dem paho-
        Callback-Thread heraus). Der Loop des Servers wurde beim Startup
        in `_main_loop` eingefangen."""
        loop = _main_loop
        if loop is None or loop.is_closed() or not self.active:
            return
        try:
            loop.call_soon_threadsafe(
                lambda: loop.create_task(self.broadcast_async(message))
            )
        except RuntimeError:
            pass  # Loop läuft nicht mehr – nichts zu senden


manager = ConnectionManager()

# Event-Loop des Servers; wird beim Startup eingefangen, damit Threads
# (z. B. der paho-MQTT-Callback-Thread) Broadcasts thread-safe schedulen können.
_main_loop: "asyncio.AbstractEventLoop | None" = None


@app.on_event("startup")
async def capture_loop():
    global _main_loop
    _main_loop = asyncio.get_running_loop()
    get_mqtt_client()  # MQTT-Verbindung (inkl. Subscription) direkt beim Start


@app.get("/api/health")
async def health():
    get_mqtt_client()  # lazy Absicherung, falls der Start-Connect scheiterte
    return {"status": "ok", "timestamp": datetime.now().isoformat(), "mqtt": _mqtt_client is not None}


# ============ API-ENDPUNKTE ============

@app.get("/api/assets")
async def get_assets():
    conn = get_db()
    assets = conn.execute("SELECT * FROM assets").fetchall()
    conn.close()
    return [dict(a) for a in assets]


@app.post("/api/assets")
async def create_asset(asset: Asset):
    conn = get_db()
    conn.execute(
        "INSERT OR REPLACE INTO assets "
        "(id, name, mac, short_name, status, latitude, longitude, rssi, last_seen) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            asset.id, asset.name, asset.mac, asset.short_name, asset.status,
            asset.latitude, asset.longitude, asset.rssi, asset.last_seen,
        ),
    )
    conn.commit()
    conn.close()
    return asset


@app.post("/api/detections")
async def add_detection(detection: Detection):
    conn = get_db()
    conn.execute(
        "INSERT INTO detections "
        "(asset_mac, source_type, node_id, rssi, latitude, longitude, timestamp) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        (
            detection.asset_mac, detection.source_type, detection.node_id,
            detection.rssi, detection.latitude, detection.longitude,
            detection.timestamp or datetime.now(),
        ),
    )
    conn.commit()
    conn.close()
    await manager.broadcast_async({
        "type": "detection",
        "assetMac": detection.asset_mac,
        "sourceType": detection.source_type,
        "nodeId": detection.node_id,
        "rssi": detection.rssi,
        "latitude": detection.latitude,
        "longitude": detection.longitude,
    })
    return {"status": "ok"}


@app.get("/api/detections")
async def list_detections(limit: int = 100):
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM detections ORDER BY timestamp DESC LIMIT ?", (limit,)
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.post("/api/alerts")
async def add_alert(alert: Alert):
    conn = get_db()
    conn.execute(
        "INSERT INTO alerts (asset_id, type, severity, message, timestamp, resolved) "
        "VALUES (?, ?, ?, ?, ?, 0)",
        (alert.asset_id, alert.type, alert.severity, alert.message,
         alert.timestamp or datetime.now()),
    )
    conn.commit()
    conn.close()
    await manager.broadcast_async({
        "type": "alert",
        "assetId": alert.asset_id,
        "severity": alert.severity,
        "message": alert.message,
    })
    return {"status": "ok"}


@app.get("/api/alerts")
async def list_alerts(unresolved_only: bool = False):
    conn = get_db()
    query = "SELECT * FROM alerts"
    if unresolved_only:
        query += " WHERE resolved = 0"
    query += " ORDER BY timestamp DESC"
    rows = conn.execute(query).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.patch("/api/alerts/{alert_id}/resolve")
async def resolve_alert(alert_id: int):
    """Markiert einen Alert als erledigt (UI-Aktion) und broadcastet den Wechsel."""
    conn = get_db()
    cur = conn.execute("UPDATE alerts SET resolved = 1 WHERE id = ?", (alert_id,))
    updated = cur.rowcount
    conn.commit()
    conn.close()
    if updated == 0:
        return {"status": "not_found"}
    await manager.broadcast_async({"type": "alert_resolved", "alertId": alert_id})
    return {"status": "ok"}


@app.delete("/api/alerts")
async def clear_resolved_alerts():
    """Entfernt alle bereits erledigten Alerts."""
    conn = get_db()
    cur = conn.execute("DELETE FROM alerts WHERE resolved = 1")
    removed = cur.rowcount
    conn.commit()
    conn.close()
    return {"status": "ok", "removed": removed}


@app.post("/api/actions/execute")
async def execute_action(action: Action, background_tasks: BackgroundTasks):
    """Queued Aktion: wird asynchron über MQTT an das Gateway/Asset geschickt."""
    conn = get_db()
    conn.execute(
        "INSERT INTO commands (asset_id, command, status, timestamp) VALUES (?, ?, ?, ?)",
        (action.asset_id, action.action_type, "queued", datetime.now()),
    )
    conn.commit()
    conn.close()
    background_tasks.add_task(process_action, action)
    return {"status": "queued", "action_id": action.asset_id}


@app.get("/api/commands")
async def list_commands(limit: int = 50):
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM commands ORDER BY timestamp DESC LIMIT ?", (limit,)
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/stats")
async def stats():
    conn = get_db()
    assets = conn.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"]
    detections = conn.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"]
    alerts = conn.execute("SELECT COUNT(*) AS c FROM alerts").fetchone()["c"]
    conn.close()
    return {"assets": assets, "detections": detections, "alerts": alerts}


async def process_action(action: Action) -> None:
    """Führt die Aktion aus: MQTT-Befehl an das Asset (MAC-Lookup) senden."""
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

    await manager.broadcast_async({
        "type": "command_status",
        "assetId": action.asset_id,
        "command": action.action_type,
        "status": "delivered" if ok else "failed",
    })
    print(f"Aktion {action.action_type} für {action.asset_id} ausgeführt (mqtt={ok})")


# ============ WEBSOCKET ============

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """Echtzeit-Kanal: Clients erhalten echte Broadcasts (Detektionen, Alerts,
    Command-Status). Eingehende `command`-Nachrichten werden quittiert (ack),
    per MQTT veröffentlicht und – konsistent zu /api/actions/execute – in der
    commands-Tabelle persistiert."""
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
                if msg.get("type") == "command":
                    asset_id = msg.get("assetId", "")
                    action = msg.get("action", "")
                    ok = publish_command(asset_id, action)
                    # Command-Tracking wie beim REST-Pfad (/api/actions/execute)
                    conn = get_db()
                    conn.execute(
                        "INSERT INTO commands (asset_id, command, status, timestamp) "
                        "VALUES (?, ?, ?, ?)",
                        (asset_id, action, "delivered" if ok else "failed", datetime.now()),
                    )
                    conn.commit()
                    conn.close()
                    await websocket.send_text(
                        json.dumps({
                            "type": "ack",
                            "assetId": asset_id,
                            "action": action,
                            "delivered": ok,
                        })
                    )
                else:
                    await websocket.send_text(json.dumps({"type": "echo", "data": msg}))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"type": "error", "message": "invalid json"}))
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as exc:
        print(f"WebSocket-Fehler: {exc}")
        manager.disconnect(websocket)


# ============ BENUTZEROBERFLÄCHE (Web-Dashboard) ============

# Liefert das Live-Dashboard (frontend/index.html) unter http://<host>:8000/
# aus – komplett an dieselbe API/WebSocket/MQTT-Infrastruktur gebunden.
# Der Mount wird als letzte Route registriert, sodass /api/*, /ws und /docs
# Vorrang behalten.
import os as _os
from pathlib import Path as _Path

_FRONTEND_DIR = _Path(__file__).resolve().parent.parent / "frontend"
if _FRONTEND_DIR.is_dir():
    from fastapi.staticfiles import StaticFiles as _StaticFiles

    app.mount("/", _StaticFiles(directory=str(_FRONTEND_DIR), html=True), name="ui")


# ============ START ============

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
