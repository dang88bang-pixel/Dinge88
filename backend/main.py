# SecureGuard Enterprise – Backend (FastAPI)
# ------------------------------------------
# REST + WebSocket-Backend für die SecureGuard-App:
#  - Asset-Verwaltung, Detektionen, Alerts, Befehls-Queue
#  - MQTT-Publish an den Broker (Befehle an Gateways/ESP32)
#  - WebSocket-Endpunkt für Echtzeit-Updates an die App
#  - MQTT → WebSocket Bridge (Telemetrie/Alerts forwarding)
#  - Crowd-Source Endpoints (anonyme Sichtungen)
#  - Schema-Migrationen, Backup/Restore, optionale API-Authentifizierung
#
# Start:
#   pip install -r requirements.txt
#   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# Oder: docker compose up --build

import asyncio
import json
import logging
import os
import shutil
import sqlite3
from datetime import datetime, timezone
from typing import List, Optional, Set

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, BackgroundTasks, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
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

# ============ KONFIGURATION (aus Umgebung / .env) ============
DB_PATH = os.environ.get("DATABASE_PATH", os.path.join(os.path.dirname(__file__), "secureguard.db"))
MQTT_BROKER = os.environ.get("MQTT_BROKER", "mqtt:1883")
MQTT_USERNAME = os.environ.get("MQTT_USERNAME", "").strip()
MQTT_PASSWORD = os.environ.get("MQTT_PASSWORD", "").strip()
MQTT_USE_TLS = os.environ.get("MQTT_USE_TLS", "false").lower() in {"1", "true", "yes"}
MQTT_TLS_CA = os.environ.get("MQTT_TLS_CA", "").strip()
MQTT_CLIENT_ID = os.environ.get("MQTT_CLIENT_ID", "secureguard-backend")

# API-Token: leeres Token deaktiviert die Absicherung (nur für lokale Entwicklung).
API_TOKEN = os.environ.get("API_TOKEN", "").strip()

# CORS_ORIGINS: kommagetrennte Liste. In Produktion nie "*" mit Credentials.
CORS_ORIGINS = [
    origin.strip()
    for origin in os.environ.get(
        "CORS_ORIGINS",
        "http://localhost:3000,http://localhost:8000,http://127.0.0.1:3000,http://127.0.0.1:8000"
    ).split(",")
    if origin.strip()
]

BACKUP_DIR = os.environ.get("BACKUP_DIR", os.path.join(os.path.dirname(__file__), "backups"))
os.makedirs(BACKUP_DIR, exist_ok=True)

app = FastAPI(title="SecureGuard API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=("*" not in CORS_ORIGINS),
    allow_methods=["*"],
    allow_headers=["*"],
)

# Active WebSocket connections for broadcasting
active_websockets: Set[WebSocket] = set()


# ============ AUTH ============

def is_authorized(request: Request) -> bool:
    if not API_TOKEN:
        return True
    auth = request.headers.get("authorization", "")
    return auth == f"Bearer {API_TOKEN}"


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    path = request.url.path
    if path.startswith("/api") and path != "/api/health" and not is_authorized(request):
        return JSONResponse(
            {"status": "error", "message": "Nicht autorisiert"},
            status_code=401,
        )
    return await call_next(request)


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
    accuracy_meters: Optional[float] = None
    is_historical: bool = False


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
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def _column_exists(conn: sqlite3.Connection, table: str, column: str) -> bool:
    rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
    return any(r["name"] == column for r in rows)


def init_db() -> None:
    """Erstellt das aktuelle Schema (idempotent)."""
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
            timestamp TIMESTAMP,
            accuracy_meters REAL,
            is_historical INTEGER DEFAULT 0
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
            timestamp TIMESTAMP,
            accuracy_meters REAL,
            is_historical INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            applied_at TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_crowd_mac ON crowd_sightings(mac);
        CREATE INDEX IF NOT EXISTS idx_detections_mac ON detections(asset_mac);
        """
    )
    conn.commit()
    conn.close()


def run_migrations() -> None:
    """Führt fehlende, idempotente Schema-Migrationen aus."""
    conn = get_db()
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            applied_at TIMESTAMP
        );
        """
    )
    applied = {r["version"] for r in conn.execute("SELECT version FROM schema_migrations").fetchall()}

    if 1 not in applied:
        if not _column_exists(conn, "detections", "accuracy_meters"):
            conn.execute("ALTER TABLE detections ADD COLUMN accuracy_meters REAL")
        if not _column_exists(conn, "detections", "is_historical"):
            conn.execute("ALTER TABLE detections ADD COLUMN is_historical INTEGER DEFAULT 0")
        if not _column_exists(conn, "crowd_sightings", "accuracy_meters"):
            conn.execute("ALTER TABLE crowd_sightings ADD COLUMN accuracy_meters REAL")
        if not _column_exists(conn, "crowd_sightings", "is_historical"):
            conn.execute("ALTER TABLE crowd_sightings ADD COLUMN is_historical INTEGER DEFAULT 0")
        conn.execute(
            "INSERT INTO schema_migrations(version, applied_at) VALUES (1, ?)",
            (datetime.now(timezone.utc).isoformat(),),
        )

    if 2 not in applied:
        # Migration 2: optionale Spalte für Historien-/Positions-Status bereits
        # über init_db abgedeckt; hier als Marker für Schema-Reviews.
        conn.execute(
            "INSERT INTO schema_migrations(version, applied_at) VALUES (2, ?)",
            (datetime.now(timezone.utc).isoformat(),),
        )

    conn.commit()
    conn.close()


init_db()
run_migrations()


# ============ MQTT (optional) ============

_mqtt_client = None


def get_mqtt_client():
    """Returns a lazy MQTT client; None if paho is not available."""
    global _mqtt_client
    if mqtt is None:
        return None
    if _mqtt_client is None:
        try:
            host = MQTT_BROKER
            port = 1883
            use_tls = MQTT_USE_TLS
            username = MQTT_USERNAME
            password = MQTT_PASSWORD
            if "://" in host:
                scheme, host = host.split("://", 1)
                if scheme in {"mqtts", "ssl", "tls"}:
                    use_tls = True
            if "@" in host:
                userinfo, host = host.rsplit("@", 1)
                if ":" in userinfo:
                    u, p = userinfo.split(":", 1)
                    if not username:
                        username = u
                    if not password:
                        password = p
            if ":" in host:
                host, port_str = host.rsplit(":", 1)
                port = int(port_str or 1883)

            _mqtt_client = mqtt.Client(client_id=MQTT_CLIENT_ID)
            if username:
                _mqtt_client.username_pw_set(username, password or None)
            if use_tls:
                _mqtt_client.tls_set(ca_certs=MQTT_TLS_CA or None)
            _mqtt_client.connect(host, port, 60)
            _mqtt_client.loop_start()
        except Exception as exc:
            logger.warning("MQTT konnte nicht verbunden werden: %s", exc)
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
    """Subscribes to all telemetry/alert/status/search topics and forwards to WebSockets."""
    client = get_mqtt_client()
    if client is None:
        logger.warning("MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert")
        return

    def on_message(mqtt_client, userdata, msg):
        """MQTT message received – format and broadcast to all WebSocket clients."""
        topic = msg.topic
        payload = msg.payload.decode("utf-8", errors="replace")

        if "/telemetry" in topic:
            data = _parse_json(payload)
            ws_msg = json.dumps({"type": "telemetry", "topic": topic, "data": data})
        elif "/alert" in topic:
            data = _parse_json(payload)
            ws_msg = json.dumps({"type": "alert", "topic": topic, "data": data})
        elif "/status" in topic:
            ws_msg = json.dumps({"type": "system_status", "topic": topic, "data": payload})
        elif "/search/response" in topic or "/search_response" in topic:
            data = _parse_json(payload)
            ws_msg = json.dumps({"type": "search_result", "topic": topic, "data": data})
        elif topic == "secureguard/broadcast":
            ws_msg = json.dumps({"type": "broadcast", "topic": topic, "data": payload})
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
    client.subscribe("secureguard/+/search/response")
    client.subscribe("secureguard/+/search_response")
    client.subscribe("secureguard/broadcast")
    client.on_message = on_message
    logger.info("MQTT-Subscriber gestartet – Topics abonniert")


def _parse_json(payload: str):
    try:
        return json.loads(payload) if payload.startswith("{") else payload
    except json.JSONDecodeError:
        return payload


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
    """Health-Check mit DB-Erreichbarkeit."""
    try:
        conn = get_db()
        db_ok = conn.execute("SELECT 1").fetchone() is not None
        conn.close()
    except Exception as exc:
        return {"status": "error", "database": str(exc), "timestamp": datetime.now(timezone.utc).isoformat()}
    return {
        "status": "ok",
        "database": "ok" if db_ok else "error",
        "version": "1.0.0",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.get("/api/assets")
async def get_assets():
    conn = get_db()
    assets = conn.execute("SELECT * FROM assets ORDER BY name").fetchall()
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
        "(asset_mac, source_type, node_id, rssi, latitude, longitude, timestamp, "
        "accuracy_meters, is_historical) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            detection.asset_mac, detection.source_type, detection.node_id,
            detection.rssi, detection.latitude, detection.longitude,
            detection.timestamp or datetime.now(),
            detection.accuracy_meters,
            1 if detection.is_historical else 0,
        ),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}


@app.get("/api/detections")
async def list_detections(limit: int = 100, mac: Optional[str] = None):
    conn = get_db()
    if mac:
        rows = conn.execute(
            "SELECT * FROM detections WHERE asset_mac = ? ORDER BY timestamp DESC LIMIT ?",
            (mac.upper(), limit),
        ).fetchall()
    else:
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
    mac = str(sighting.get("mac", "")).upper()
    if not mac:
        return {"status": "error", "message": "mac required"}

    conn = get_db()
    conn.execute(
        "INSERT INTO crowd_sightings (mac, reporter_id, rssi, latitude, longitude, "
        "timestamp, accuracy_meters, is_historical) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (
            mac,
            sighting.get("reporter_id", "anonymous"),
            sighting.get("rssi", 0),
            sighting.get("latitude"),
            sighting.get("longitude"),
            datetime.now(),
            sighting.get("accuracy_meters"),
            1 if sighting.get("is_historical", False) else 0,
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


@app.post("/api/search")
async def search_asset(request_body: dict):
    """Search endpoint: verknüpft crowd sightings + detections für eine MAC."""
    mac = str(request_body.get("mac", "")).upper()
    if not mac:
        return JSONResponse({"status": "error", "message": "mac required"}, status_code=422)
    hours = int(request_body.get("hours", 24))
    conn = get_db()
    crowd = conn.execute(
        "SELECT * FROM crowd_sightings WHERE mac = ? "
        "AND timestamp > datetime('now', ? || ' hours') "
        "ORDER BY timestamp DESC LIMIT 20",
        (mac, -hours),
    ).fetchall()
    detections = conn.execute(
        "SELECT * FROM detections WHERE asset_mac = ? ORDER BY timestamp DESC LIMIT 20",
        (mac,),
    ).fetchall()
    conn.close()
    return {
        "status": "ok",
        "mac": mac,
        "sightings": [dict(r) for r in crowd],
        "detections": [dict(r) for r in detections],
    }


# ============ BACKUP / RESTORE ============

@app.get("/api/backup")
async def list_backups():
    """Listet lokale Backups."""
    try:
        files = sorted(
            (f for f in os.listdir(BACKUP_DIR) if f.endswith(".db")),
            reverse=True,
        )
    except FileNotFoundError:
        files = []
    return {"backups": files, "dir": BACKUP_DIR}


@app.post("/api/backup")
async def create_backup(name: str = "secureguard"):
    """Erstellt eine SQLite-Kopie (Backup) und liefert Metadaten."""
    if not os.path.exists(DB_PATH):
        return JSONResponse({"status": "error", "message": "database missing"}, status_code=404)
    filename = f"{name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.db"
    target = os.path.join(BACKUP_DIR, filename)
    shutil.copy2(DB_PATH, target)
    conn = get_db()
    assets = conn.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"]
    detections = conn.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"]
    conn.close()
    return {
        "status": "ok",
        "filename": filename,
        "size_bytes": os.path.getsize(target),
        "assets": assets,
        "detections": detections,
    }


@app.post("/api/restore")
async def restore_backup(request: Request):
    """Stellt eine hochgeladene SQLite-Datei wieder her (validiert Header)."""
    data = await request.body()
    if not data.startswith(b"SQLite format 3"):
        return JSONResponse({"status": "error", "message": "invalid sqlite file"}, status_code=400)

    current = f"{DB_PATH}.before_restore"
    if os.path.exists(DB_PATH):
        shutil.copy2(DB_PATH, current)

    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    with open(DB_PATH, "wb") as fh:
        fh.write(data)

    init_db()
    run_migrations()
    return {"status": "ok", "restored_bytes": len(data), "backup_before": current}


@app.get("/api/backup/{filename}")
async def download_backup(filename: str):
    """Liefert ein Backup als Datei-Download."""
    safe = os.path.basename(filename)
    path = os.path.join(BACKUP_DIR, safe)
    if not os.path.exists(path):
        return JSONResponse({"status": "error", "message": "not found"}, status_code=404)
    from fastapi.responses import FileResponse
    return FileResponse(path, filename=safe, media_type="application/octet-stream")


# ============ WEBSOCKET ============

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    if API_TOKEN:
        auth = websocket.headers.get("authorization", "")
        token = websocket.query_params.get("token", "")
        if auth != f"Bearer {API_TOKEN}" and token != API_TOKEN:
            await websocket.close(code=1008, reason="Unauthorized")
            return
    await websocket.accept()
    active_websockets.add(websocket)
    logger.info("WebSocket verbunden (%d aktiv)", len(active_websockets))
    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
                msg_type = msg.get("type", "")
                if msg_type == "command":
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
                elif msg_type in {"search", "search_request"}:
                    mac = str(msg.get("mac", "")).upper()
                    response = await _search_mac(mac)
                    await websocket.send_text(json.dumps({
                        "type": "search_result",
                        "mac": mac,
                        "data": response,
                    }))
                else:
                    await websocket.send_text(json.dumps({"type": "echo", "data": msg}))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"type": "error", "message": "invalid json"}))
    except (WebSocketDisconnect, Exception) as exc:
        logger.debug("WebSocket getrennt: %s", exc)
    finally:
        active_websockets.discard(websocket)
        logger.info("WebSocket entfernt (%d aktiv)", len(active_websockets))


async def _search_mac(mac: str) -> dict:
    conn = get_db()
    crowd = conn.execute(
        "SELECT * FROM crowd_sightings WHERE mac = ? ORDER BY timestamp DESC LIMIT 20",
        (mac,),
    ).fetchall()
    detections = conn.execute(
        "SELECT * FROM detections WHERE asset_mac = ? ORDER BY timestamp DESC LIMIT 20",
        (mac,),
    ).fetchall()
    conn.close()
    return {
        "sightings": [dict(r) for r in crowd],
        "detections": [dict(r) for r in detections],
    }


# ============ STARTUP ============

@app.on_event("startup")
async def startup_event():
    run_migrations()
    start_mqtt_subscriber()
    logger.info("SecureGuard Backend gestartet (DB: %s, CORS: %s)", DB_PATH, CORS_ORIGINS)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
