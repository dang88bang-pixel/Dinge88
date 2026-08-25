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
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional, Set

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


@asynccontextmanager
async def _lifespan(app: FastAPI):
    """Captures the serving event loop and starts the MQTT → WebSocket bridge."""
    global _main_loop
    _main_loop = asyncio.get_running_loop()
    start_mqtt_subscriber()
    logger.info("SecureGuard Backend gestartet")
    yield
    _main_loop = None


app = FastAPI(title="SecureGuard API", version="1.0.0", lifespan=_lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Active WebSocket connections for broadcasting
active_websockets: Set[WebSocket] = set()

# Event loop of the FastAPI/uvicorn main thread. The MQTT callbacks run on a
# paho network thread, which has *no* event loop of its own, so the loop has to
# be captured here to hand coroutines over via `run_coroutine_threadsafe`.
_main_loop: Optional[asyncio.AbstractEventLoop] = None


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
            client = _new_mqtt_client("secureguard-backend")
            client.connect(host, int(port or 1883), 60)
            client.loop_start()
            _mqtt_client = client
        except Exception as exc:
            logger.warning("MQTT-Verbindung zu %s fehlgeschlagen: %s", MQTT_BROKER, exc)
            _mqtt_client = None
    return _mqtt_client


def _new_mqtt_client(client_id: str):
    """
    Creates a paho client for both paho 1.x and 2.x.

    paho 2.x requires `callback_api_version`: omitting it emits a
    DeprecationWarning today and raises in paho 3. We prefer VERSION2
    (`on_message(client, message)`) and fall back to VERSION1 on old clients.
    `on_mqtt_message` accepts either signature.
    """
    api_version = getattr(mqtt, "CallbackAPIVersion", None)
    if api_version is not None and hasattr(api_version, "VERSION2"):
        return mqtt.Client(
            callback_api_version=api_version.VERSION2, client_id=client_id
        )
    if api_version is not None:
        return mqtt.Client(
            callback_api_version=api_version.VERSION1, client_id=client_id
        )
    return mqtt.Client(client_id=client_id)


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

def forward_mqtt_message(topic: str, payload) -> None:
    """
    Maps an MQTT topic/payload onto a WebSocket frame and hands it to the
    asyncio loop of the FastAPI thread.

    Called from the paho network thread, which has **no** event loop of its own:
    `asyncio.get_event_loop()` raises RuntimeError there, so the previous
    `try/except RuntimeError: pass` silently dropped every single message.
    """
    if isinstance(payload, (bytes, bytearray)):
        payload = payload.decode("utf-8", errors="replace")

    def _maybe_json(raw: str):
        try:
            return json.loads(raw) if raw.startswith("{") else raw
        except json.JSONDecodeError:
            return raw

    if "/telemetry" in topic:
        ws_msg = json.dumps({"type": "telemetry", "topic": topic, "data": _maybe_json(payload)})
    elif "/alert" in topic:
        ws_msg = json.dumps({"type": "alert", "topic": topic, "data": _maybe_json(payload)})
    elif "/status" in topic:
        ws_msg = json.dumps({"type": "system_status", "topic": topic, "data": payload})
    else:
        ws_msg = json.dumps({"type": "unknown", "topic": topic, "data": payload})

    loop = _main_loop
    if loop is None or loop.is_closed():
        logger.warning(
            "Kein Event-Loop erfasst – MQTT-Nachricht %s nicht weitergeleitet", topic
        )
        return
    asyncio.run_coroutine_threadsafe(broadcast_websocket(ws_msg), loop)


def on_mqtt_message(*args):
    """
    paho `on_message` callback, tolerant to both callback API versions:
      VERSION1 -> (client, userdata, message)
      VERSION2 -> (client, message)
    """
    msg = args[-1]
    forward_mqtt_message(
        getattr(msg, "topic", "") or "",
        getattr(msg, "payload", b"") or b"",
    )


def start_mqtt_subscriber():
    """Subscribes to all telemetry/alert/status topics and forwards to WebSockets."""
    client = get_mqtt_client()
    if client is None:
        logger.warning("MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert")
        return

    client.subscribe("secureguard/+/telemetry")
    client.subscribe("secureguard/+/alert")
    client.subscribe("secureguard/+/status")
    client.subscribe("secureguard/broadcast")
    client.on_message = on_mqtt_message
    logger.info("MQTT-Subscriber gestartet – Topics abonniert")


async def broadcast_websocket(message: str):
    """Sends a message to all connected WebSocket clients."""
    disconnected = set()
    # Iterate over a snapshot: clients connect/disconnect concurrently, and
    # mutating `active_websockets` while iterating it raises
    # "RuntimeError: Set changed size during iteration".
    for ws in list(active_websockets):
        try:
            await ws.send_text(message)
        except Exception:
            disconnected.add(ws)
    active_websockets.difference_update(disconnected)


# ============ API-ENDPUNKTE ============

@app.get("/api/health")
async def health():
    return {"status": "ok", "timestamp": datetime.now().isoformat()}


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
    # Sightings are written with Python's `datetime.now()`, i.e. *local* time,
    # while SQLite's `datetime('now')` is UTC. Comparing the two shifts the
    # window by the UTC offset (in Europe/Berlin a 25 h old sighting is still
    # reported for `hours=24`). `'localtime'` puts the cutoff on the same clock
    # as the stored values.
    rows = conn.execute(
        "SELECT * FROM crowd_sightings WHERE mac = ? "
        "AND timestamp > datetime('now', 'localtime', ? || ' hours') "
        "ORDER BY timestamp DESC LIMIT 10",
        (mac.upper(), -hours),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


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
#
# Der Application-Lifespan wird weiter oben (vor `app = FastAPI(...)`) über
# `_lifespan` definiert; er erfasst hier den Event-Loop der uvicorn-Threads,
# damit der MQTT-Thread Nachrichten an die WebSockets übergeben kann.


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
