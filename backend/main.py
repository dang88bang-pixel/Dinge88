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

import json
import os
import sqlite3
from datetime import datetime
from typing import List, Optional

import uvicorn
from fastapi import FastAPI, WebSocket, BackgroundTasks
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


def get_mqtt_client():
    """Liefert einen (lazy) MQTT-Client; None, wenn paho fehlt."""
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


# ============ CHANNEL-ENDPUNKTE (Suchkanäle der App) ============
# LIEFERN NUR ECHTE DATEN: neueste Sichtungen pro Asset/Channel aus der
# detections-Tabelle (gefüllt u. a. über POST /api/detections und den
# MQTT-Import). Keine simulierten Werte.


def _sightings(asset_mac: str, source_type: str, limit: int = 20) -> list:
    conn = get_db()
    rows = conn.execute(
        "SELECT node_id, rssi, latitude, longitude, timestamp "
        "FROM detections WHERE asset_mac = ? AND source_type = ? "
        "ORDER BY timestamp DESC LIMIT ?",
        (asset_mac.upper(), source_type, limit),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


class LoraCommand(BaseModel):
    mac: str
    command: str


@app.get("/api/lora/sightings")
async def lora_sightings(mac: str):
    """Echte LoRa/LoRaWAN-Sichtungen des Assets (via Gateways)."""
    return {"sightings": _sightings(mac, "LORA")}


@app.post("/api/lora/command")
async def lora_command(cmd: LoraCommand):
    """Echtes MQTT-Publish an das Gateway/Asset-Command-Topic."""
    ok = publish_command(cmd.mac.upper(), cmd.command)
    return {"delivered": ok}


@app.get("/api/optical/detections")
async def optical_detections(mac: str):
    """Echte optische Erkennungen des Assets (YOLO-Server)."""
    return {"detections": _sightings(mac, "OPTICAL")}


@app.get("/api/crowd/locate")
async def crowd_locate(mac: str):
    """Letzte echte Crowdsource-Position des Assets (Find-My-Proxy)."""
    rows = _sightings(mac, "CROWD", limit=1)
    latest = rows[0] if rows else None
    if latest is None or latest["latitude"] is None or latest["longitude"] is None:
        return {"found": False}
    return {
        "found": True,
        "latitude": latest["latitude"],
        "longitude": latest["longitude"],
        "accuracy": 100,  # Kanal-Konstante für Crowd-Netzwerke (Meter)
        "network": latest["node_id"] or "crowd-proxy",
    }


@app.get("/api/urban/detections")
async def urban_detections(mac: str):
    """Echte urbanen Infrastruktur-Sichtungen (WiGle/OCM/DHL/CKAN via Backend)."""
    return {"detections": _sightings(mac, "URBAN")}


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

    print(f"Aktion {action.action_type} für {action.asset_id} ausgeführt (mqtt={ok})")


# ============ WEBSOCKET ============

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
                if msg.get("type") == "command":
                    asset_id = msg.get("assetId", "")
                    action = msg.get("action", "")
                    await websocket.send_text(
                        json.dumps({"type": "ack", "assetId": asset_id, "action": action})
                    )
                else:
                    await websocket.send_text(json.dumps({"type": "echo", "data": msg}))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"type": "error", "message": "invalid json"}))
    except Exception as exc:
        print(f"WebSocket-Fehler: {exc}")


# ============ START ============

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
