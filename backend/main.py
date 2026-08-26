# SecureGuard Enterprise – Backend (FastAPI)
# ------------------------------------------
# REST + WebSocket-Backend für die SecureGuard-App:
#  - Asset-Verwaltung, Detektionen, Alerts, Befehls-Queue
#  - MQTT-Publish an den Broker (Befehle an Gateways/ESP32)
#  - WebSocket-Endpunkt für Echtzeit-Updates an die App
#  - MQTT → WebSocket Bridge (Telemetrie/Alerts forwarding)
#  - Crowd-Source Endpoints (anonyme Sichtungen)
#
# Start:
#   pip install -r requirements.txt
#   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# Oder: docker compose up --build

import asyncio
import json
import logging
import os
import sqlite3
from datetime import datetime
from typing import List, Optional, Set

import uvicorn
from fastapi import FastAPI, WebSocket, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("secureguard")

try:
    import paho.mqtt.client as mqtt
except ImportError:
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

# Active WebSocket connections for broadcasting
active_websockets: Set[WebSocket] = set()


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
        CREATE TABLE IF NOT EXISTS crowd_sightings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            mac TEXT,
            reporter_id TEXT,
            rssi INTEGER,
            latitude REAL,
            longitude REAL,
            timestamp TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_crowd_mac ON crowd_sightings(mac);
        """
    )
    conn.commit()
    conn.close()


init_db()


# ============ MQTT (optional) ============

_mqtt_client = None


def get_mqtt_client():
    """Returns a lazy MQTT client; None if paho is not available."""
    global _mqtt_client
    if mqtt is None:
        return None
    if _mqtt_client is None:
        try:
            host, _, port = MQTT_BROKER.partition(":")
            _mqtt_client = mqtt.Client(client_id="secureguard-backend")
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


# ============ MQTT → WEBSOCKET BRIDGE ============

def start_mqtt_subscriber():
    """Subscribes to all telemetry/alert/status topics and forwards to WebSockets."""
    client = get_mqtt_client()
    if client is None:
        logger.warning("MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert")
        return

    def on_message(mqtt_client, userdata, msg):
        """MQTT message received – format and broadcast to all WebSocket clients."""
        topic = msg.topic
        payload = msg.payload.decode("utf-8", errors="replace")

        if "/telemetry" in topic:
            try:
                data = json.loads(payload) if payload.startswith("{") else payload
            except json.JSONDecodeError:
                data = payload
            ws_msg = json.dumps({"type": "telemetry", "topic": topic, "data": data})
        elif "/alert" in topic:
            try:
                data = json.loads(payload) if payload.startswith("{") else payload
            except json.JSONDecodeError:
                data = payload
            ws_msg = json.dumps({"type": "alert", "topic": topic, "data": data})
        elif "/status" in topic:
            ws_msg = json.dumps({"type": "system_status", "topic": topic, "data": payload})
        else:
            ws_msg = json.dumps({"type": "unknown", "topic": topic, "data": payload})

        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.run_coroutine_threadsafe(
                    broadcast_websocket(ws_msg), loop
                )
        except RuntimeError:
            pass

    client.subscribe("secureguard/+/telemetry")
    client.subscribe("secureguard/+/alert")
    client.subscribe("secureguard/+/status")
    client.subscribe("secureguard/broadcast")
    client.on_message = on_message
    logger.info("MQTT-Subscriber gestartet – Topics abonniert")


async def broadcast_websocket(message: str):
    """Sends a message to all connected WebSocket clients."""
    disconnected = set()
    for ws in active_websockets:
        try:
            await ws.send_text(message)
        except Exception:
            disconnected.add(ws)
    active_websockets.difference_update(disconnected)


# ============ API-ENDPUNKTE ============

@app.get("/api/health")
async def health():
    """Ops-Health: Status + DB-Zähler für Monitoring/Dashboard."""
    conn = get_db()
    try:
        assets = conn.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"]
        detections = conn.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"]
        alerts = conn.execute("SELECT COUNT(*) AS c FROM alerts").fetchone()["c"]
    except Exception as e:
        return {
            "status": "degraded",
            "timestamp": datetime.now().isoformat(),
            "error": str(e),
        }
    finally:
        conn.close()
    return {
        "status": "ok",
        "timestamp": datetime.now().isoformat(),
        "assets": assets,
        "detections": detections,
        "alerts": alerts,
        "service": "secureguard-backend",
    }


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


@app.post("/api/actions/execute")
async def execute_action(action: Action, background_tasks: BackgroundTasks):
    """Queues an action: sent asynchronously via MQTT to the gateway/asset."""
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
    """Executes the action: sends MQTT command to the asset."""
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


# ============ CROWD SOURCE ============

@app.post("/api/crowd/report")
async def report_crowd_sighting(sighting: dict):
    """Reports an anonymous crowd sighting (MAC + position + RSSI)."""
    mac = sighting.get("mac", "").upper()
    if not mac:
        return {"status": "error", "message": "mac required"}

    conn = get_db()
    conn.execute(
        "INSERT INTO crowd_sightings (mac, reporter_id, rssi, latitude, longitude, timestamp) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (
            mac,
            sighting.get("reporter_id", "anonymous"),
            sighting.get("rssi", 0),
            sighting.get("latitude"),
            sighting.get("longitude"),
            datetime.now(),
        ),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}


@app.get("/api/crowd/search")
async def search_crowd_sightings(mac: str, hours: int = 24):
    """Returns recent crowd sightings for a MAC address."""
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM crowd_sightings WHERE mac = ? "
        "AND timestamp > datetime('now', ? || ' hours') "
        "ORDER BY timestamp DESC LIMIT 10",
        (mac.upper(), -hours),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


# ============ MCP / TEMP-MAIL (Test-Fallback) ============
# Einfacher In-Memory-MCP-ähnlicher Dienst für QA/Pilot ohne externen Server.
# Die App spricht standardmäßig WebSocket (MCPClient); zusätzlich REST.

_temp_inboxes: dict = {}
_temp_messages: dict = {}


class TempInboxCreate(BaseModel):
    prefix: Optional[str] = None


class TempMessageIn(BaseModel):
    token: str
    subject: str = "OTP"
    body: str
    from_addr: str = "noreply@example.com"


@app.post("/api/mcp/create_inbox")
async def mcp_create_inbox(body: Optional[TempInboxCreate] = None):
    import secrets
    import uuid
    body = body or TempInboxCreate()
    token = secrets.token_urlsafe(16)
    email = f"{body.prefix or 'sg'}-{token[:8]}@temp.secureguard.local"
    inbox_id = str(uuid.uuid4())
    _temp_inboxes[token] = {"email": email, "inbox_id": inbox_id, "created": datetime.now().isoformat()}
    _temp_messages[token] = []
    return {"email": email, "token": token, "inboxId": inbox_id}


@app.post("/api/mcp/inject_message")
async def mcp_inject_message(msg: TempMessageIn):
    """Test-Helfer: legt eine Nachricht (z. B. OTP) in die Inbox."""
    if msg.token not in _temp_inboxes:
        return {"status": "error", "message": "unknown token"}
    _temp_messages.setdefault(msg.token, []).append({
        "subject": msg.subject,
        "body": msg.body,
        "from": msg.from_addr,
        "received": datetime.now().isoformat(),
    })
    return {"status": "ok", "count": len(_temp_messages[msg.token])}


@app.get("/api/mcp/wait_for_otp")
async def mcp_wait_for_otp(token: str, timeout: int = 5):
    """Long-Poll: wartet kurz auf eine Nachricht und extrahiert eine 4–8-stellige OTP."""
    import re
    if token not in _temp_inboxes:
        return {"success": False, "error": "unknown token"}
    deadline = asyncio.get_event_loop().time() + max(1, min(timeout, 45))
    while asyncio.get_event_loop().time() < deadline:
        for m in _temp_messages.get(token, []):
            match = re.search(r"\b(\d{4,8})\b", m.get("body", ""))
            if match:
                inbox = _temp_inboxes[token]
                return {
                    "success": True,
                    "otp": match.group(1),
                    "email": inbox["email"],
                    "from": m.get("from", ""),
                    "subject": m.get("subject", ""),
                }
        await asyncio.sleep(0.5)
    return {"success": False, "error": "timeout"}


@app.get("/api/mcp/extract_magic_link")
async def mcp_extract_magic_link(token: str):
    import re
    if token not in _temp_inboxes:
        return {"success": False, "error": "unknown token"}
    for m in reversed(_temp_messages.get(token, [])):
        match = re.search(r"https?://\S+", m.get("body", ""))
        if match:
            return {
                "success": True,
                "magicLink": match.group(0).rstrip(".,)"),
                "email": _temp_inboxes[token]["email"],
            }
    return {"success": False, "error": "no magic link"}


# ============ WEBSOCKET ============

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


# ============ STARTUP ============

@app.on_event("startup")
async def startup_event():
    start_mqtt_subscriber()
    logger.info("SecureGuard Backend gestartet")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
