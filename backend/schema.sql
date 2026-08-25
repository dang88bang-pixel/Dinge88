-- SecureGuard Enterprise – Backend-SQLite
-- Wird von main.init_db() geladen (DATABASE_PATH, Default: data/secureguard.db).

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    mac TEXT NOT NULL UNIQUE,
    short_name TEXT,
    status TEXT NOT NULL DEFAULT 'UNKNOWN',
    latitude REAL,
    longitude REAL,
    rssi INTEGER DEFAULT 0,
    last_seen TIMESTAMP
);

CREATE TABLE IF NOT EXISTS detections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_mac TEXT NOT NULL,
    source_type TEXT NOT NULL,
    node_id TEXT,
    rssi INTEGER DEFAULT 0,
    latitude REAL,
    longitude REAL,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'SECURITY',
    severity TEXT NOT NULL DEFAULT 'WARNING',
    message TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    resolved INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS commands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id TEXT NOT NULL,
    command TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS crowd_sightings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mac TEXT NOT NULL,
    reporter_id TEXT,
    rssi INTEGER DEFAULT 0,
    latitude REAL,
    longitude REAL,
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_assets_mac ON assets(mac);
CREATE INDEX IF NOT EXISTS idx_assets_status ON assets(status);
CREATE INDEX IF NOT EXISTS idx_detections_mac ON detections(asset_mac);
CREATE INDEX IF NOT EXISTS idx_detections_ts ON detections(timestamp);
CREATE INDEX IF NOT EXISTS idx_alerts_asset ON alerts(asset_id);
CREATE INDEX IF NOT EXISTS idx_alerts_unresolved ON alerts(resolved);
CREATE INDEX IF NOT EXISTS idx_commands_asset ON commands(asset_id);
CREATE INDEX IF NOT EXISTS idx_crowd_mac ON crowd_sightings(mac);
CREATE INDEX IF NOT EXISTS idx_crowd_ts ON crowd_sightings(timestamp);
