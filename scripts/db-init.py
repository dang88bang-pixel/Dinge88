#!/usr/bin/env python3
"""
SecureGuard Enterprise – Datenbank-Schema initialisieren
----------------------------------------------------------
Legt NUR das Schema an (Tabellen + Indizes) – ohne Demo-/Beispieldaten.
Assets, Detektionen, Alerts und Crowd-Sichtungen entstehen ausschließlich
über die API und die App (echte Daten).

Verwendung:
  DATABASE_PATH=data/secureguard.db python3 scripts/db-init.py
"""
import os
import sqlite3

DB_PATH = os.environ.get("DATABASE_PATH", "data/secureguard.db")


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
    c.commit()


def main() -> None:
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    init_schema(conn)
    conn.close()
    print(f"[db-init] Schema bereit – Datenbank: {DB_PATH} (keine Beispieldaten)")


if __name__ == "__main__":
    main()
