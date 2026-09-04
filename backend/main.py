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
import threading
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import List, Optional, Set

import uvicorn
from fastapi import FastAPI, Header, HTTPException, WebSocket, BackgroundTasks, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel

import slack_mcp
from slack_mcp import SlackMCPError, format_alert_message, parse_channels_csv

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
# F-73: Broker-Credentials (Produktion mit allow_anonymous=false).
# Leer = anonym (Pilot).
MQTT_USERNAME = os.environ.get("MQTT_USERNAME", "")
MQTT_PASSWORD = os.environ.get("MQTT_PASSWORD", "")

# Optionale API-Key-Absicherung: Ist SECUREGUARD_API_KEY gesetzt, verlangen alle
# schreibenden Endpunkte den Header `X-API-Key: <key>`. Lesen bleibt offen
# (Pilot-Betrieb im LAN) – für Produktion Reverse-Proxy/TLS vorschalten.
API_KEY = os.environ.get("SECUREGUARD_API_KEY", "").strip()
# CORS: Komma-getrennte Origins; "*" = offen (dann ohne Credentials, siehe unten).
CORS_ORIGINS = [o.strip() for o in os.environ.get("CORS_ORIGINS", "*").split(",") if o.strip()]
# Node-RED (für die Abhängigkeits-Inventur /api/system/dependencies).
NODERED_URL = os.environ.get("NODERED_URL", "http://nodered:1880").strip().rstrip("/")


async def require_api_key(x_api_key: str = Header(default="", alias="X-API-Key")) -> None:
    """Prüft den X-API-Key gegen SECUREGUARD_API_KEY (wenn konfiguriert)."""
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Ungültiger oder fehlender X-API-Key")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    global main_event_loop
    main_event_loop = asyncio.get_running_loop()
    start_mqtt_subscriber()
    logger.info("SecureGuard Backend gestartet")
    yield
    # Slack-MCP-Client sauber schließen (offene HTTP-/SSE-Verbindungen).
    notifier = slack_mcp.peek_notifier()
    if notifier is not None:
        try:
            await notifier.aclose()
        except Exception as exc:  # pragma: no cover - Best effort beim Shutdown
            logger.debug("Slack-MCP-Client nicht sauber geschlossen: %s", exc)


app = FastAPI(title="SecureGuard API", version="1.0.1", lifespan=lifespan)

# F-61c: Referenz auf die Haupt-Event-Loop. Der Paho-Callback laeuft in einem
# eigenen Thread – dort gibt es KEINEN aktuellen Loop mehr (Python >= 3.10/3.12:
# asyncio.get_event_loop() -> RuntimeError). Ohne diese Referenz blieb die
# MQTT->WS-Bridge stumm (RuntimeError wurde still verschluckt).
main_event_loop: asyncio.AbstractEventLoop | None = None

_allow_all = "*" in CORS_ORIGINS
app.add_middleware(
    CORSMiddleware,
    # "*" + allow_credentials=True ist per CORS-Spec ungültig (Browser lehnen ab):
    # nur bei konkreten Origins werden Credentials erlaubt.
    allow_origins=["*"] if _allow_all else CORS_ORIGINS,
    allow_credentials=not _allow_all,
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
            # F-73: Credentials setzen, wenn konfiguriert – sonst schlägt bei
            # allow_anonymous=false JEDE Verbindung fehl (Bridge + publish_command tot).
            if MQTT_USERNAME:
                _mqtt_client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD or None)
            _mqtt_client.connect(host, int(port or 1883), 60)
            _mqtt_client.loop_start()
        except Exception:
            _mqtt_client = None
    return _mqtt_client


def publish_command(asset_mac: str, command: str) -> bool:
    """Publishet einen Befehl. Topic-MAC wird wie in der App (MqttConfig)
    GROSSGESCHRIEBEN – MQTT-Topics sind case-sensitiv."""
    client = get_mqtt_client()
    if client is None:
        return False
    try:
        topic = f"secureguard/{asset_mac.strip().upper()}/command"
        client.publish(topic, command, qos=1)
        return True
    except Exception:
        return False


# ============ MQTT → WEBSOCKET BRIDGE ============

# (Topic, QoS) – wird bei JEDEM Verbindungsaufbau erneut abonniert.
MQTT_SUBSCRIPTIONS = [
    ("secureguard/+/telemetry", 1),
    ("secureguard/+/alert", 2),
    ("secureguard/+/status", 1),
    ("secureguard/broadcast", 0),
]

# True, solange die Subscriptions auf dem Broker liegen (für die Inventur).
_mqtt_subscribed = False

# Selbstheilung: Manche Broker (z. B. Session-Overtake/Expiry) verwerfen die
# Subscriptions, obwohl die TCP-Verbindung lebt – `is_connected()` bleibt True
# und die Echtzeitkette wäre still tot. Deshalb in festen Abständen erneut
# abonnieren (idempotent). 0 schaltet den Watchdog aus.
MQTT_RESUBSCRIBE_INTERVAL = int(os.environ.get("MQTT_RESUBSCRIBE_INTERVAL", "60"))
_mqtt_watchdog_stop = None


def mqtt_subscribe_all(mqtt_client) -> None:
    """Alle Topics abonnieren – idempotent, beliebig oft aufrufbar."""
    for topic, qos in MQTT_SUBSCRIPTIONS:
        mqtt_client.subscribe(topic, qos=qos)


def mqtt_resubscribe_once(mqtt_client) -> bool:
    """Ein Watchdog-Durchlauf. True, wenn (erneut) abonniert wurde."""
    global _mqtt_subscribed
    try:
        if not mqtt_client.is_connected():
            _mqtt_subscribed = False
            return False
        mqtt_subscribe_all(mqtt_client)
        _mqtt_subscribed = True
        return True
    except Exception:
        _mqtt_subscribed = False
        return False


def _mqtt_watchdog_loop(mqtt_client, stop_event, interval: int) -> None:
    while not stop_event.wait(interval):
        if mqtt_resubscribe_once(mqtt_client):
            logger.debug("MQTT-Subscriptions aufgefrischt (%d Topics)",
                         len(MQTT_SUBSCRIPTIONS))


def start_mqtt_subscriber():
    """Subscribes to all telemetry/alert/status topics and forwards to WebSockets."""
    global _mqtt_subscribed, _mqtt_watchdog_stop
    client = get_mqtt_client()
    if client is None:
        logger.warning("MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert")
        return

    subscribe_all = mqtt_subscribe_all

    def on_connect(mqtt_client, userdata, flags, rc):
        """Wichtig: paho verbindet sich nach einem Abbruch selbst wieder, die
        Subscriptions gehen dabei aber verloren. Ohne Re-Subscribe wäre die
        komplette Echtzeitkette (MQTT → WebSocket/Slack) still tot, während
        `is_connected()` weiter True meldet."""
        global _mqtt_subscribed
        if rc != 0:
            logger.warning("MQTT-Connect abgelehnt (rc=%s)", rc)
            return
        subscribe_all(mqtt_client)
        _mqtt_subscribed = True
        logger.info("MQTT verbunden – %d Topics (erneut) abonniert",
                    len(MQTT_SUBSCRIPTIONS))

    def on_disconnect(mqtt_client, userdata, rc):
        global _mqtt_subscribed
        _mqtt_subscribed = False
        logger.warning("MQTT getrennt (rc=%s) – Subscriptions weg, Reconnect läuft", rc)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

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
            # Alert zusätzlich an Slack melden (Gateway/ESP32 → Backend → Slack).
            loop = main_event_loop
            if loop is not None and loop.is_running():
                asyncio.run_coroutine_threadsafe(_notify_mqtt_alert(topic, data), loop)
        elif "/status" in topic:
            ws_msg = json.dumps({"type": "system_status", "topic": topic, "data": payload})
        else:
            ws_msg = json.dumps({"type": "unknown", "topic": topic, "data": payload})

        loop = main_event_loop
        if loop is not None and loop.is_running():
            # Thread-sichere Uebergabe an die Haupt-Loop (Paho-Thread -> uvicorn)
            asyncio.run_coroutine_threadsafe(broadcast_websocket(ws_msg), loop)

    client.on_message = on_message
    # Falls die Verbindung schon steht (CONNACK vor Callback-Zuordnung),
    # einmal direkt abonnieren – ein Doppel-Abo ist unkritisch.
    if client.is_connected():
        subscribe_all(client)
        _mqtt_subscribed = True
    logger.info("MQTT-Subscriber gestartet – %d Topics konfiguriert",
                len(MQTT_SUBSCRIPTIONS))

    # Selbstheilungs-Watchdog (einmal pro Prozess; früheren Thread beenden).
    if MQTT_RESUBSCRIBE_INTERVAL > 0:
        if _mqtt_watchdog_stop is not None:
            _mqtt_watchdog_stop.set()
        _mqtt_watchdog_stop = threading.Event()
        threading.Thread(
            target=_mqtt_watchdog_loop,
            args=(client, _mqtt_watchdog_stop, MQTT_RESUBSCRIBE_INTERVAL),
            name="mqtt-resubscribe-watchdog",
            daemon=True,
        ).start()
        logger.info("MQTT-Watchdog aktiv (Re-Subscribe alle %ss)",
                    MQTT_RESUBSCRIBE_INTERVAL)


async def _notify_mqtt_alert(topic: str, data) -> dict:
    """MQTT-Alert (`secureguard/<MAC>/alert`) → Slack-Benachrichtigung."""
    parts = [p for p in topic.split("/") if p]
    asset = parts[1].upper() if len(parts) > 1 else topic
    if isinstance(data, dict):
        return await notify_slack_alert(
            asset_id=str(data.get("asset_id") or asset),
            alert_type=str(data.get("type", "SECURITY")),
            severity=str(data.get("severity", "WARNING")),
            message=str(data.get("message", "")),
            source=f"mqtt:{topic}",
            timestamp=str(data.get("timestamp", "")) or None,
        )
    return await notify_slack_alert(
        asset, "SECURITY", "WARNING", str(data), f"mqtt:{topic}"
    )


async def broadcast_websocket(message: str):
    """Sends a message to all connected WebSocket clients."""
    disconnected = set()
    for ws in active_websockets:
        try:
            await ws.send_text(message)
        except Exception:
            disconnected.add(ws)
    active_websockets.difference_update(disconnected)


def broadcast_event(event_type: str, data: dict) -> None:
    """REST-Mutation → alle WebSocket-Clients (Echtzeit-Flow, siehe README).

    Die Endpunkte laufen als sync-`def` im Threadpool, daher die thread-sichere
    Übergabe an die uvicorn-Event-Loop – identisch zum MQTT→WS-Bridge-Pfad.
    Die App erwartet `{"type": …, "data": {…}}` (WebSocketService.kt).
    """
    loop = main_event_loop
    if loop is None or not loop.is_running():
        return
    try:
        asyncio.run_coroutine_threadsafe(
            broadcast_websocket(json.dumps({"type": event_type, "data": data})),
            loop,
        )
    except RuntimeError:  # Loop wird gerade geschlossen (Shutdown)
        logger.debug("WebSocket-Broadcast übersprungen: %s", event_type)


# ============ API-ENDPUNKTE ============

@app.get("/api/health")
def health():
    """Ops-Health: Status + DB-Zähler für Monitoring/Dashboard."""
    try:
        conn = get_db()
    except Exception as e:
        return JSONResponse(
            status_code=503,
            content={
                "status": "degraded",
                "timestamp": datetime.now().isoformat(),
                "error": f"DB nicht öffnbar: {e}",
            },
        )
    try:
        assets = conn.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"]
        detections = conn.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"]
        alerts = conn.execute("SELECT COUNT(*) AS c FROM alerts").fetchone()["c"]
    except Exception as e:
        # Degraded als 503, damit Monitoring/Compose-Healthcheck echte
        # DB-Probleme erkennen (200 + "degraded" war für Checkes unsichtbar).
        return JSONResponse(
            status_code=503,
            content={
                "status": "degraded",
                "timestamp": datetime.now().isoformat(),
                "error": str(e),
            },
        )
    finally:
        conn.close()
    return {
        "status": "ok",
        "timestamp": datetime.now().isoformat(),
        "assets": assets,
        "detections": detections,
        "alerts": alerts,
        "service": "secureguard-backend",
        # Konfiguration (nicht Erreichbarkeit!) der Slack-MCP-Integration –
        # siehe GET /api/slack/health für den Live-Check.
        "slack": _slack_summary(),
    }


@app.get("/api/assets")
def get_assets():
    conn = get_db()
    assets = conn.execute("SELECT * FROM assets").fetchall()
    conn.close()
    return [dict(a) for a in assets]


@app.post("/api/assets", dependencies=[Depends(require_api_key)])
def create_asset(asset: Asset):
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
    # Echtzeit-Flow: andere Clients (App/Dashboard) sehen die Änderung sofort.
    broadcast_event("asset_update", {
        "id": asset.id,
        "name": asset.name,
        "mac": asset.mac,
        "short_name": asset.short_name,
        "status": asset.status,
        "latitude": asset.latitude,
        "longitude": asset.longitude,
        "rssi": asset.rssi,
        "last_seen": asset.last_seen.isoformat() if asset.last_seen else None,
    })
    return asset


@app.post("/api/detections", dependencies=[Depends(require_api_key)])
def add_detection(detection: Detection):
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
    broadcast_event("telemetry", {
        "asset_mac": detection.asset_mac,
        "source_type": detection.source_type,
        "node_id": detection.node_id,
        "rssi": detection.rssi,
        "latitude": detection.latitude,
        "longitude": detection.longitude,
    })
    return {"status": "ok"}


@app.get("/api/detections")
def list_detections(limit: int = 100):
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM detections ORDER BY timestamp DESC LIMIT ?", (limit,)
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.post("/api/alerts", dependencies=[Depends(require_api_key)])
def add_alert(alert: Alert, background_tasks: BackgroundTasks):
    conn = get_db()
    conn.execute(
        "INSERT INTO alerts (asset_id, type, severity, message, timestamp, resolved) "
        "VALUES (?, ?, ?, ?, ?, 0)",
        (alert.asset_id, alert.type, alert.severity, alert.message,
         alert.timestamp or datetime.now()),
    )
    conn.commit()
    conn.close()
    # Slack-Benachrichtigung asynchron (siehe docs/SLACK_MCP.md) – blockiert
    # weder den REST-Client noch scheitert der Alert selbst bei Slack-Problemen.
    background_tasks.add_task(
        notify_slack_alert,
        alert.asset_id,
        alert.type,
        alert.severity,
        alert.message,
        "backend/api",
        (alert.timestamp or datetime.now()).isoformat(),
    )
    # Zusätzlich Live-Update an alle WS-Clients (Alert-Karte/Dashboard).
    broadcast_event("alert", {
        "asset_id": alert.asset_id,
        "type": alert.type,
        "severity": alert.severity,
        "message": alert.message,
        "timestamp": (alert.timestamp or datetime.now()).isoformat(),
    })
    return {"status": "ok"}


@app.get("/api/alerts")
def list_alerts(unresolved_only: bool = False):
    conn = get_db()
    query = "SELECT * FROM alerts"
    if unresolved_only:
        query += " WHERE resolved = 0"
    query += " ORDER BY timestamp DESC"
    rows = conn.execute(query).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.post("/api/actions/execute", dependencies=[Depends(require_api_key)])
def execute_action(action: Action, background_tasks: BackgroundTasks):
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
def list_commands(limit: int = 50):
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM commands ORDER BY timestamp DESC LIMIT ?", (limit,)
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/stats")
def stats():
    conn = get_db()
    assets = conn.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"]
    detections = conn.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"]
    alerts = conn.execute("SELECT COUNT(*) AS c FROM alerts").fetchone()["c"]
    conn.close()
    return {"assets": assets, "detections": detections, "alerts": alerts}


def process_action(action: Action) -> None:
    """Executes the action: sends MQTT command to the asset."""
    conn = get_db()
    row = conn.execute("SELECT mac FROM assets WHERE id = ?", (action.asset_id,)).fetchone()
    conn.close()

    mac = row["mac"] if row else action.asset_id
    ok = publish_command(mac, action.action_type)

    conn = get_db()
    # Portables SQL: UPDATE ... ORDER BY ... LIMIT benötigt
    # SQLITE_ENABLE_UPDATE_DELETE_LIMIT (nicht in jedem Build vorhanden) –
    # daher die neueste Command-ID vorher per SELECT ermitteln.
    last = conn.execute(
        "SELECT id FROM commands WHERE asset_id = ? AND command = ? "
        "ORDER BY timestamp DESC LIMIT 1",
        (action.asset_id, action.action_type),
    ).fetchone()
    if last is not None:
        conn.execute(
            "UPDATE commands SET status = ? WHERE id = ?",
            ("delivered" if ok else "failed", last["id"]),
        )
        conn.commit()
    conn.close()

    logger.info(
        "Aktion %s für %s (mac=%s) → %s",
        action.action_type, action.asset_id, mac,
        "delivered" if ok else "failed"
    )


# ============ CROWD SOURCE ============

@app.post("/api/crowd/report", dependencies=[Depends(require_api_key)])
def report_crowd_sighting(sighting: dict):
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
            # UTC im SQLite-Format – die Suche vergleicht mit datetime('now')
            # (ebenfalls UTC). datetime.now() (Lokalzeit) lief sonst um die
            # TZ-Differenz daneben (F-61d).
            datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
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


@app.post("/api/mcp/create_inbox", dependencies=[Depends(require_api_key)])
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


@app.post("/api/mcp/inject_message", dependencies=[Depends(require_api_key)])
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
    loop = asyncio.get_running_loop()
    deadline = loop.time() + max(1, min(timeout, 45))
    while loop.time() < deadline:
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


# ============ SLACK (MCP – provectus/slack-mcp-server) ============
# Das Backend ist MCP-Client gegenüber dem Slack-MCP-Server (docker-compose-
# Service `slack-mcp`) und stellt der App REST-Endpunkte zur Verfügung.
# Details: docs/SLACK_MCP.md

class SlackCallIn(BaseModel):
    tool: str
    arguments: Optional[dict] = None


class SlackNotifyIn(BaseModel):
    message: str
    channel: Optional[str] = None
    # Optional: Meldung wie einen Alert formatieren (Icon, Asset, Quelle).
    asset_id: Optional[str] = None
    alert_type: str = "STATUS"
    severity: str = "INFO"


def _slack_summary() -> dict:
    """Konfigurations-Snapshot für /api/health (ohne Netzwerkaufruf)."""
    cfg = slack_mcp.load_settings()
    return {
        "configured": cfg.configured or bool(cfg.webhook_url),
        "mcp_url": cfg.http_endpoint if cfg.transport == "http" else cfg.sse_endpoint,
        "transport": cfg.transport,
        "notify_enabled": cfg.notify_enabled,
        "notify_channel": cfg.notify_channel,
        "min_severity": cfg.notify_min_severity,
        "webhook_configured": bool(cfg.webhook_url),
    }


async def notify_slack_alert(
    asset_id: str,
    alert_type: str,
    severity: str,
    message: str,
    source: str = "backend",
    timestamp: Optional[str] = None,
) -> dict:
    """Meldet einen Alert an Slack – scheitert nie hart (Alerting bleibt stabil).

    Läuft als Background-Task: Der REST-/MQTT-Pfad wird nicht blockiert, wenn
    der Slack-MCP-Server langsam oder offline ist.
    """
    notifier = slack_mcp.get_notifier()
    if not notifier.config.notify_enabled:
        return {"ok": False, "skipped": "disabled"}
    try:
        result = await notifier.notify_alert(
            asset_id=asset_id,
            alert_type=alert_type,
            severity=severity,
            message=message,
            source=source,
            timestamp=timestamp,
        )
    except Exception as exc:  # noqa: BLE001 – Alerting darf nicht crashen
        logger.warning("Slack-Benachrichtigung fehlgeschlagen: %s", exc)
        return {"ok": False, "error": str(exc)}
    if result.get("ok"):
        logger.info(
            "Slack: Alert %s (%s) → %s", asset_id, severity, result.get("channel")
        )
    else:
        logger.info("Slack: Alert %s nicht gesendet (%s)", asset_id, result)
    return result


@app.get("/api/slack/health")
async def slack_health(probe: bool = True):
    """Erreichbarkeit des Slack-MCP-Servers + registrierte Tools."""
    notifier = slack_mcp.get_notifier()
    try:
        info = await notifier.client.health(probe=probe)
    except SlackMCPError as exc:
        info = {"configured": notifier.config.configured, "reachable": False, "error": str(exc)}
    info["notify"] = {
        "enabled": notifier.config.notify_enabled,
        "channel": notifier.config.notify_channel,
        "min_severity": notifier.config.notify_min_severity,
        "webhook_configured": bool(notifier.config.webhook_url),
    }
    return info


@app.get("/api/slack/tools")
async def slack_tools(refresh: bool = False):
    """Registrierte MCP-Tools des Slack-MCP-Servers (gecacht)."""
    try:
        tools = await slack_mcp.get_notifier().client.list_tools(refresh=refresh)
    except SlackMCPError as exc:
        raise HTTPException(status_code=502, detail=f"Slack-MCP: {exc}") from exc
    return {
        "count": len(tools),
        "tools": [
            {"name": t.get("name", ""), "description": t.get("description", "")}
            for t in tools
        ],
    }


@app.get("/api/slack/channels")
async def slack_channels(
    channel_types: str = "public_channel,private_channel",
    limit: int = 200,
    refresh: bool = False,
):
    """Channel-Verzeichnis (MCP-Tool `channels_list`, CSV → JSON)."""
    result = await slack_mcp.get_notifier().client.call_tool(
        "channels_list",
        {"channel_types": channel_types, "limit": min(max(limit, 1), 999)},
    )
    if not result.ok:
        raise HTTPException(status_code=502, detail=result.error or "channels_list fehlgeschlagen")
    channels = parse_channels_csv(result.text)
    return {"count": len(channels), "channels": channels, "refresh": refresh}


@app.post("/api/slack/call", dependencies=[Depends(require_api_key)])
async def slack_call(body: SlackCallIn):
    """Direkter MCP-Tool-Aufruf (z. B. conversations_history, users_search)."""
    tool = body.tool.strip()
    if not tool:
        raise HTTPException(status_code=400, detail="tool erforderlich")
    result = await slack_mcp.get_notifier().client.call_tool(tool, body.arguments or {})
    if not result.ok and result.error and result.text == "":
        raise HTTPException(status_code=502, detail=result.error)
    return result.as_dict()


@app.post("/api/slack/notify", dependencies=[Depends(require_api_key)])
async def slack_notify(body: SlackNotifyIn):
    """Meldung in einen Slack-Channel senden (manuell → ohne Severity-Gate)."""
    text = body.message.strip()
    if not text:
        raise HTTPException(status_code=400, detail="message erforderlich")
    if body.asset_id:
        text = format_alert_message(
            asset_id=body.asset_id,
            alert_type=body.alert_type,
            severity=body.severity,
            message=text,
            source="api",
        )
    result = await slack_mcp.get_notifier().send_text(text, body.channel)
    if not result.get("ok"):
        return JSONResponse(status_code=502, content=result)
    return result


# ============ ABHÄNGIGKEITEN (Inventur für das App-Einstellungsmenü) ============
# Eine Quelle für "welche Anbindung gibt es, wohin, ist sie erreichbar" – die
# App zeigt das unter Einstellungen → Anbindungen & Abhängigkeiten.

@app.get("/api/system/dependencies")
async def system_dependencies(probe: bool = True):
    """Serverseitige Abhängigkeiten inkl. Status (für Settings/Health-Screen)."""
    deps: List[dict] = []

    # --- SQLite ---
    db_detail = "nicht erreichbar"
    db_ok = False
    try:
        conn = get_db()
        counts = {
            table: conn.execute(f"SELECT COUNT(*) AS c FROM {table}").fetchone()["c"]
            for table in ("assets", "detections", "alerts")
        }
        conn.close()
        db_ok = True
        db_detail = ", ".join(f"{k}={v}" for k, v in counts.items())
    except Exception as exc:  # noqa: BLE001 – Status statt Absturz
        db_detail = str(exc)[:120]
    deps.append({
        "id": "database",
        "name": "SQLite (Backend-DB)",
        "kind": "storage",
        "configured": True,
        "reachable": db_ok,
        "target": DB_PATH,
        "detail": db_detail,
    })

    # --- MQTT-Broker ---
    client = _mqtt_client
    mqtt_ok = False
    try:
        mqtt_ok = bool(client and client.is_connected())
    except Exception:  # noqa: BLE001
        mqtt_ok = False
    deps.append({
        "id": "mqtt",
        "name": "MQTT-Broker (Mosquitto)",
        "kind": "broker",
        "configured": bool(MQTT_BROKER),
        "reachable": mqtt_ok,
        "target": MQTT_BROKER,
        "detail": f"Auth: {'ja' if MQTT_USERNAME else 'anonym'} · "
                  f"Topics: secureguard/+/(telemetry|alert|status) · "
                  f"{'abonniert' if _mqtt_subscribed else 'nicht abonniert'}",
    })

    # --- Slack-MCP-Server ---
    try:
        slack_info = await slack_mcp.get_notifier().client.health(probe=probe)
        notify = slack_mcp.get_notifier().config
        deps.append({
            "id": "slack-mcp",
            "name": "Slack-MCP-Server (provectus)",
            "kind": "mcp",
            "configured": slack_info.get("configured", False),
            "reachable": slack_info.get("reachable"),
            "target": slack_info.get("url", ""),
            "detail": f"Transport: {slack_info.get('transport', '')} · "
                      f"{slack_info.get('tools', 0)} Tools · "
                      f"Channel: {notify.notify_channel} · "
                      f"ab {notify.notify_min_severity}"
                      + (f" · Fehler: {slack_info.get('error')}" if slack_info.get("error") else ""),
        })
    except Exception as exc:  # noqa: BLE001
        deps.append({
            "id": "slack-mcp",
            "name": "Slack-MCP-Server (provectus)",
            "kind": "mcp",
            "configured": False,
            "reachable": False,
            "target": "",
            "detail": str(exc)[:120],
        })

    # --- Slack Incoming Webhook (Fallback) ---
    webhook = slack_mcp.get_notifier().config.webhook_url
    deps.append({
        "id": "slack-webhook",
        "name": "Slack Incoming Webhook (Fallback)",
        "kind": "webhook",
        "configured": bool(webhook),
        # Webhooks werden nie "geprobt" – ein Test würde wirklich posten.
        "reachable": None,
        "target": "gesetzt" if webhook else "nicht gesetzt",
        "detail": "Nur aktiv, wenn der MCP-Post fehlschlägt",
    })

    # --- Node-RED ---
    nodered_ok = None
    nodered_detail = "nicht konfiguriert"
    if NODERED_URL:
        if probe:
            try:
                import httpx
                async with httpx.AsyncClient(timeout=4) as http_client:
                    response = await http_client.get(NODERED_URL + "/")
                nodered_ok = response.status_code < 500
                nodered_detail = f"HTTP {response.status_code}"
            except Exception as exc:  # noqa: BLE001
                nodered_ok = False
                nodered_detail = f"{type(exc).__name__}"[:120]
        else:
            nodered_detail = "Probe übersprungen"
    deps.append({
        "id": "nodered",
        "name": "Node-RED (Flows)",
        "kind": "flow",
        "configured": bool(NODERED_URL),
        "reachable": nodered_ok,
        "target": NODERED_URL,
        "detail": nodered_detail,
    })

    return {
        "generated": datetime.now().isoformat(),
        "probed": probe,
        "count": len(deps),
        "dependencies": deps,
    }


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
# (lifespan-Handler oben: startet den MQTT-Subscriber beim App-Start;
#  das deprecated @app.on_event("startup") wurde ersetzt)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
