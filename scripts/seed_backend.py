#!/usr/bin/env python3
"""
SecureGuard Enterprise – Demo-/Basis-Datenbank befüllen
----------------------------------------------------------
Idempotent: Legt die Tabellen an (falls nicht vorhanden) und fügt die
Beispieldaten NUR ein, wenn die jeweilige Tabelle leer ist.

Die Assets entsprechen den Demo-Daten, die die Android-App beim ersten
Start in ihrer lokalen Room-Datenbank anlegt (gleiche MAC-Adressen), damit
App und Backend dieselben Assets sehen.

Verwendung:
  DATABASE_PATH=data/secureguard.db python3 scripts/seed_backend.py
"""
import os
import sqlite3
from datetime import datetime, timedelta

DB_PATH = os.environ.get("DATABASE_PATH", "data/secureguard.db")

DEMO_ASSETS = [
    # (id, name, mac, short_name, status, lat, lon, rssi, last_seen)
    ("asset-001", "E-Scooter Roller #1", "AA:BB:CC:DD:EE:01", "Roller #1",
     "ONLINE", 52.5200, 13.4050, -45, 0),
    ("asset-002", "E-Bike Fahrrad #2", "AA:BB:CC:DD:EE:02", "Fahrrad #2",
     "MAINTENANCE", 52.4980, 13.4040, -60, 0),
    ("asset-003", "Schlüsselfinder #3", "AA:BB:CC:DD:EE:03", "Schlüssel #3",
     "OFFLINE", 52.5100, 13.4100, -90, -2 * 3600),
    ("asset-004", "Tablet Wache #4", "AA:BB:CC:DD:EE:04", "Tablet #4",
     "ONLINE", 52.5219, 13.4132, -55, 0),
    ("asset-005", "Smartphone #5", "AA:BB:CC:DD:EE:05", "Smartphone #5",
     "ONLINE", 52.5380, 13.4200, -50, 0),
]

DEMO_DETECTIONS = [
    ("AA:BB:CC:DD:EE:01", "BLE", "node-ble-01", -45, 52.5200, 13.4050, 0),
    ("AA:BB:CC:DD:EE:01", "WiFi", "node-wifi-02", -52, 52.5201, 13.4051, 0),
    ("AA:BB:CC:DD:EE:04", "LoRa", "node-lora-01", -67, 52.5219, 13.4132, -600),
    ("AA:BB:CC:DD:EE:02", "BLE", "node-ble-01", -60, 52.4980, 13.4040, -3600),
]

DEMO_ALERTS = [
    ("asset-001", "SECURITY", "WARNING", "Ungewöhnliche Bewegung am Roller #1 (BLE-RSSI-Anstieg)", 0),
    ("asset-003", "BATTERY", "CRITICAL", "Schlüsselfinder #3: Batterie kritisch (12 %)", 0),
]

DEMO_CROWD = [
    ("AA:BB:CC:DD:EE:01", "crowd-berlin-01", -70, 52.5200, 13.4050),
    ("AA:BB:CC:DD:EE:04", "crowd-berlin-02", -80, 52.5219, 13.4132),
]


def conn() -> sqlite3.Connection:
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    return c


def init_schema(c: sqlite3.Connection) -> None:
    c.executescript(
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


def ts(offset_seconds: int = 0) -> str:
    return (datetime.now() + timedelta(seconds=offset_seconds)).isoformat(sep=" ", timespec="seconds")


def seed(c: sqlite3.Connection) -> None:
    if c.execute("SELECT COUNT(*) AS c FROM assets").fetchone()["c"] == 0:
        for row in DEMO_ASSETS:
            a_id, name, mac, short, status, lat, lon, rssi, off = row
            c.execute(
                "INSERT INTO assets (id, name, mac, short_name, status, latitude, longitude, rssi, last_seen) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (a_id, name, mac, short, status, lat, lon, rssi, ts(off)),
            )
        print(f"[seed] {len(DEMO_ASSETS)} Assets eingefügt")
    else:
        print("[seed] Assets vorhanden – übersprungen")

    if c.execute("SELECT COUNT(*) AS c FROM detections").fetchone()["c"] == 0:
        for mac, src, node, rssi, lat, lon, off in DEMO_DETECTIONS:
            c.execute(
                "INSERT INTO detections (asset_mac, source_type, node_id, rssi, latitude, longitude, timestamp) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                (mac, src, node, rssi, lat, lon, ts(off)),
            )
        print(f"[seed] {len(DEMO_DETECTIONS)} Detektionen eingefügt")
    else:
        print("[seed] Detektionen vorhanden – übersprungen")

    if c.execute("SELECT COUNT(*) AS c FROM alerts").fetchone()["c"] == 0:
        for asset_id, atype, severity, msg, _ in DEMO_ALERTS:
            c.execute(
                "INSERT INTO alerts (asset_id, type, severity, message, timestamp, resolved) "
                "VALUES (?, ?, ?, ?, ?, 0)",
                (asset_id, atype, severity, msg, ts(-900)),
            )
        print(f"[seed] {len(DEMO_ALERTS)} Alerts eingefügt")
    else:
        print("[seed] Alerts vorhanden – übersprungen")

    if c.execute("SELECT COUNT(*) AS c FROM crowd_sightings").fetchone()["c"] == 0:
        for mac, reporter, rssi, lat, lon in DEMO_CROWD:
            c.execute(
                "INSERT INTO crowd_sightings (mac, reporter_id, rssi, latitude, longitude, timestamp) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (mac, reporter, rssi, lat, lon, ts(-300)),
            )
        print(f"[seed] {len(DEMO_CROWD)} Crowd-Sichtungen eingefügt")
    else:
        print("[seed] Crowd-Sichtungen vorhanden – übersprungen")

    c.commit()


def main() -> None:
    c = conn()
    init_schema(c)
    seed(c)
    c.close()
    print(f"[seed] Fertig – Datenbank: {DB_PATH}")


if __name__ == "__main__":
    main()
