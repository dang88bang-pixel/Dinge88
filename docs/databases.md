# Datenbanken

## App (Room) – `secureguard.db`

Datei: `app/src/main/java/com/secureguard/enterprise/data/local/SecureGuardDatabase.kt`  
Version **2**, Migration `1 → 2` legt `audit_log` und `pending_actions` an.

| Tabelle | Entity | Zweck |
|---------|--------|--------|
| `assets` | `Asset` | Whitelist der geschützten Geräte |
| `detections` | `Detection` | Sichtungen (BLE, WiFi, LoRa, …) |
| `alerts` | `Alert` | Alarme / Ereignisse |
| `audit_log` | `AuditLog` | Wer hat was wann gemacht |
| `pending_actions` | `PendingAction` | Offline-Aktionsqueue |

DAOs: `AssetDao`, `DetectionDao`, `AlertDao`, `AuditLogDao`, `PendingActionDao`.  
Hilt: `AppModule` stellt die DB (inkl. Migration) und die Repository-DAOs bereit.

## Backend (SQLite) – `data/secureguard.db`

Schema: [`backend/schema.sql`](../backend/schema.sql)  
Pfad: Umgebungsvariable `DATABASE_PATH` (Compose: `/data/secureguard.db`).

| Tabelle | Zweck |
|---------|--------|
| `assets` | Gerätebestand (REST) |
| `detections` | Gemeldete Sichtungen |
| `alerts` | Alerts inkl. `resolved` |
| `commands` | Aktions-/MQTT-Historie |
| `crowd_sightings` | Anonyme Crowd-Reports |

## MQTT / Node-RED

- Mosquitto persistiert unter `mosquitto/data/` (nicht im Git).
- Node-RED-Flows unter `nodered/` (nicht im Git).
