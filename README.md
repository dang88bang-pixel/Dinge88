# 🛡️ SecureGuard Enterprise

Schutz- und Wiederbeschaffungssystem für mobile Werte (E-Scooter, Fahrräder,
Schlüsselfinder, Tablets etc.) – ohne Meshtastic-Abhängigkeit. Die App nutzt
einen Mix aus **BLE, WiFi, LoRa/LoRaWAN, optischer Erkennung, urbaner
Infrastruktur, Crowdsourcing und Satellit**, orchestriert von einem
**selbstlernenden Agenten** mit **9 parallelen Detection-Kanälen** und
**3 Echtzeit-Flows**.

> Repository-Titel: *Dinge88 – Agent „Le Guck"*

---

## Inhaltsverzeichnis

- [Funktionsübersicht](#-funktionsübersicht)
- [Architektur](#️-architektur)
- [Aktionsketten (End-to-End)](#-aktionsketten-end-to-end)
- [Detection-Kanäle](#-detection-kanäle-9)
- [Echtzeit-Kanäle](#-echtzeit-kanäle-3)
- [Externe APIs (8)](#-externe-apis-8)
- [Services (30)](#-services-30)
- [UI-Screens (13)](#-ui-screens-13)
- [Datenmodelle (8)](#-datenmodelle-8)
- [Datenbank (Room v2)](#-datenbank-room-v2)
- [Sicherheit & Berechtigungen](#-sicherheit--berechtigungen)
- [Backend (FastAPI)](#-backend-fastapi)
- [Firmware (ESP32)](#-firmware-esp32)
- [Docker-Stack](#-docker-stack)
- [Abhängigkeiten (48 Libraries)](#-abhängigkeiten-48-libraries)
- [Build & CI](#-build--ci)
- [Konfiguration](#️-konfiguration)
- [Honeywell CT45P XON](#-honeywell-ct45p-xon)
- [Installation](#-installation)
- [Datenschutz](#️-datenschutz)

---

## ✨ Funktionsübersicht

### Kernfunktionen

| Funktion | Beschreibung | Implementierung |
|----------|-------------|-----------------|
| **Multi-Channel-Suche** | 9 parallele Detection-Kanäle pro Asset | `AgentService.comprehensiveSearch()` |
| **Selbstlernender Agent** | Adaptive Intervalle, Kanal-Priorisierung | `LearningEngine` + `AgentService` |
| **Echtzeit-Monitoring** | MQTT + WebSocket + NFC parallel | `startRealtimeChannels()` |
| **Aktionen** | 8 Befehle über 4 Zustellkanäle | `AgentService.sendAction()` |
| **Offline-Queue** | Persistierte Aktionen bei Netzverlust | `OfflineQueue` (Room) |
| **Alarm-Sound** | Töne pro Schweregrad | `AlertSoundManager` |
| **PIN-Sperre** | PBKDF2 + Auto-Lock + 5-Versuch-Limit | `AuthManager` |
| **Verschlüsselung** | AES/GCM via AndroidKeyStore | `EncryptionService` |
| **RBAC** | 4 Rollen, 7 Permissions | `RoleManager` |
| **Backup/Restore** | SQLite-Datei-Kopie + Validierung | `BackupManager` |
| **CSV/PDF-Export** | Assets, Detektionen, Alarme | `ExportService` |
| **QR-Scan** | ZXing-Kamera + HID-Barcode-Scanner | `ScanQrScreen` → `OpticalService` |
| **NFC-Tags** | NDEF-Lesung, Asset-Identifikation | `NfcService` → Agent-Flow |
| **USB-Serial** | FTDI/CP210x/CH34x/PL2303 Adapter | `UsbSerialService` |
| **Karte** | OpenStreetMap + Offline-Kacheln | `MapScreen` + `OfflineMapService` |
| **API-Node-Manager** | Circuit-Breaker, Rate-Limits, Health | `ApiNodeManager` (11 Nodes) |
| **Hintergrund-Agent** | WorkManager 15-Min + ForegroundService | `SecureAgentWorker` + `AgentForegroundService` |
| **Boot-Restart** | Agent startet nach Geräteneustart | `BootReceiver` |
| **Datenbereinigung** | Retention: 30d/90d/365d | `DatabaseCleanup` |
| **Audit-Log** | Wer hat was wann gemacht | `AuditLogService` |
| **Temporäre E-Mail** | MCP-Client für OTP-Empfang | `TempMailService` + `MCPClient` |
| **Barrierefreiheit** | TalkBack-Support für Status/Quellen | `AccessibilityHelper` |

---

## 🏗️ Architektur

```
app/src/main/java/com/secureguard/enterprise/
├── MainActivity.kt                    # PIN-Sperre + NFC-Intent-Verarbeitung
├── SecureGuardApplication.kt          # Hilt-Init, WorkManager-Scheduling
│
├── agent/
│   ├── ApiNodeManager.kt              # 11 API-Nodes: Circuit-Breaker, Rate-Limits, Learning
│   └── NodeConfig.kt                  # DefaultNodeConfigs (Prioritäten, Timeouts, Limits)
│
├── config/
│   └── CT45PConfig.kt                 # Honeywell CT45P Erkennung + Kompatibilität
│
├── data/
│   ├── model/                         # 8 Datenmodelle (Entity + Enum + DTO)
│   │   ├── Asset.kt                   # Room @Entity (Whitelist)
│   │   ├── Detection.kt               # Room @Entity (Sichtung)
│   │   ├── Alert.kt                   # Room @Entity (Alarm)
│   │   ├── AuditLog.kt               # Room @Entity (Audit)
│   │   ├── PendingAction.kt           # Room @Entity (Offline-Queue)
│   │   ├── Telemetry.kt              # In-Memory (GATT-Payload)
│   │   ├── Enums.kt                   # AssetStatus, DetectionSource, AlertType, AlertSeverity
│   │   └── Models.kt                  # SearchResult
│   ├── local/
│   │   ├── SecureGuardDatabase.kt     # Room v2 + Migration 1→2
│   │   ├── Converters.kt             # TypeConverter (Enum↔String, Date↔Long)
│   │   └── dao/                       # 5 DAOs (Asset, Detection, Alert, AuditLog, PendingAction)
│   └── repository/
│       └── SecureGuardRepository.kt   # Interface + Impl (22 Funktionen)
│
├── di/
│   └── AppModule.kt                   # Hilt @Module: DB, DAOs, Repository
│
├── mcp/
│   └── MCPClient.kt                   # JSON-RPC über WebSocket (create_inbox, wait_for_otp)
│
├── receiver/
│   └── BootReceiver.kt                # BOOT_COMPLETED → WorkManager reschedule
│
├── security/
│   └── RoleManager.kt                 # RBAC: ADMIN, MANAGER, OPERATOR, VIEWER
│
├── services/                          # 30 Services (alle @Singleton @Inject)
│   ├── AgentService.kt                # Kern-Orchestrierung (18 Dependencies)
│   ├── AgentModels.kt                 # AgentSettings, AgentStatus, AgentCycleResult
│   ├── AgentForegroundService.kt      # Android Foreground Service Wrapper
│   ├── BleService.kt                  # BLE-Scan (BluetoothLeScanner, MAC-Filter)
│   ├── WifiService.kt                 # WiFi-Scan (WifiManager.startScan, BSSID-Match)
│   ├── LoraService.kt                 # Helium API + MQTT-Commands
│   ├── OpticalService.kt             # QR-Code-Match gegen Asset MAC/ID/VIN
│   ├── UrbanService.kt               # OpenChargeMap + DHL + CKAN
│   ├── CrowdService.kt               # HTTP GET /api/crowd/search
│   ├── SatelliteService.kt           # FusedLocationProviderClient (GPS)
│   ├── TelemetryService.kt           # BLE-GATT Read/Write (JSON-Parse)
│   ├── MqttService.kt                # Paho MqttAsyncClient (4 Topics)
│   ├── MqttConfig.kt                 # Topics, QoS, Broker-URL
│   ├── WebSocketService.kt           # OkHttp WebSocket (JSON-Messages)
│   ├── ApiServiceManager.kt          # 8 Retrofit-Clients + CacheManager + RetryManager
│   ├── LearningEngine.kt             # Muster, Prädiktion, adaptives Intervall
│   ├── NotificationService.kt        # 4 Kanäle (Alerts, Agent, Telemetrie, System)
│   ├── AlertSoundManager.kt          # ToneGenerator pro Schweregrad
│   ├── AuthManager.kt                # PBKDF2 PIN + Auto-Lock
│   ├── EncryptionService.kt          # AES/GCM AndroidKeyStore
│   ├── AuditLogService.kt            # Persistiert ins AuditLog-DAO
│   ├── OfflineQueue.kt               # Room-persistierte Aktions-Queue + Retry
│   ├── BackupManager.kt              # SQLite-Datei-Kopie + Restore
│   ├── DatabaseCleanup.kt            # Retention-basierte Bereinigung
│   ├── ExportService.kt              # CSV + PDF + AES-verschlüsselt
│   ├── OfflineMapService.kt          # osmdroid Cache + MBTiles
│   ├── NfcService.kt                 # NDEF-Tag-Lesung + Detection-Emission
│   ├── UsbSerialService.kt           # usb-serial-for-android (FTDI/CP210x)
│   ├── TempMailService.kt            # MCPClient-Wrapper (Inbox, OTP, Magic Link)
│   ├── DetectionCapable.kt           # Abstract Base: SharedFlow<Detection>
│   └── apis/                          # 8 Retrofit-Interfaces
│       ├── WiGleApi.kt               # BSSID → GPS (Bearer Auth)
│       ├── MacLookupApi.kt           # OUI-Auflösung (kostenlos)
│       ├── OpenChargeMapApi.kt       # Ladesäulen (Coroutine + RxJava)
│       ├── DhlPackstationApi.kt      # Paketstationen
│       ├── CkanOpenDataApi.kt        # Smart-City-Datensätze
│       ├── GoogleGeolocationApi.kt   # WLAN-Triangulation (API-Key)
│       ├── NetatmoWeatherApi.kt      # Wetterstationen (Bearer Auth)
│       └── HeliumNetworkApi.kt       # LoRaWAN-Hotspots + Beacons
│
├── util/
│   ├── ErrorHandler.kt               # Globaler Error-Handler → AuditLog
│   ├── RetryManager.kt               # Exponentieller Backoff
│   ├── CacheManager.kt               # In-Memory LRU + TTL (5 Min)
│   ├── AssetPagingSource.kt          # Paging 3 Source für große Listen
│   └── AccessibilityHelper.kt        # TalkBack-ContentDescriptions
│
├── worker/
│   └── SecureAgentWorker.kt          # WorkManager: runCycle() + cleanup()
│
└── presentation/
    ├── components/                    # 3 wiederverwendbare Composables
    │   ├── AssetCard.kt              # Asset-Karte + AccessibilityHelper
    │   ├── StatCard.kt               # Dashboard-Statistik-Karte
    │   └── ActionButton.kt           # Aktions-Button
    ├── navigation/
    │   ├── NavItems.kt               # 12 Routes + BottomNav-Items
    │   └── SecureGuardApp.kt         # NavHost + Scaffold + BottomBar
    ├── theme/                         # Material 3 (Light/Dark/Dynamic)
    └── ui/                            # 13 Screens + 12 ViewModels
        ├── dashboard/                 # DashboardScreen + DashboardViewModel
        ├── assets/                    # AssetList, AssetDetail, AddAsset, ScanQr
        ├── map/                       # MapScreen + MapViewModel (osmdroid)
        ├── actions/                   # ActionsScreen + ActionsViewModel
        ├── agent/                     # AgentConfigScreen + AgentViewModel
        ├── alerts/                    # AlertsScreen + AlertsViewModel
        ├── settings/                  # SettingsScreen + SettingsViewModel
        ├── nodes/                     # NodeStatusScreen + NodeStatusViewModel
        ├── tempmail/                  # TempMailScreen + TempMailViewModel
        ├── auth/                      # LockScreen (PIN-Eingabe)
        └── common/                    # ActionType (8 Commands), Permissions
```

---

## 🔗 Aktionsketten (End-to-End)

### Aktion ausführen (UI → Asset)

```
┌─────────────────────────┐
│  ActionsScreen          │  User drückt Button (z.B. ALARM)
│  AssetDetailScreen      │
└───────────┬─────────────┘
            │ viewModel.executeAction(ActionType.ALARM)
            ▼
┌─────────────────────────┐
│  ActionsViewModel       │  RBAC-Check: RoleManager.hasPermission(EXECUTE_ACTIONS)
│  AssetDetailViewModel   │
└───────────┬─────────────┘
            │ agentService.sendAction(asset, "ALARM")
            ▼
┌─────────────────────────┐
│  AgentService           │  AuditLog: "ACTION → ALARM → Roller #1 (AA:BB:CC:DD:EE:01)"
│  .sendAction()          │
└──┬──────┬──────┬─────┬──┘
   │      │      │     │
   ▼      ▼      ▼     ▼
 MQTT   WebSocket  BLE/GATT  Offline-Queue
 (Paho)  (OkHttp)  (GATT)    (Room)
   │      │      │     │
   ▼      ▼      ▼     ▼
┌─────────────────────────┐
│  ESP32 Gateway          │  MQTT subscribe: secureguard/+/command
│  (Firmware)             │  Verarbeitet: ALARM, LIGHT, MOTOR_OFF,
│                         │  BATTERY, MESSAGE, POSITION, RESTART,
│                         │  TELEMETRY, CONFIG
└─────────────────────────┘
```

### Detection-Zyklus (Agent → Asset-Findung)

```
┌─────────────────────────┐
│  SecureAgentWorker      │  WorkManager: alle 15 Minuten
│  ODER AgentService      │  Foreground: konfigurierbares Intervall
│  .runCycle()            │
└───────────┬─────────────┘
            │ für jedes whitelisted Asset:
            ▼
┌─────────────────────────┐
│  comprehensiveSearch()  │  buildChannelList() → 9 Kanäle
│                         │  (lernend: erfolgreicher Kanal zuerst)
└──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐
   │  │  │  │  │  │  │  │  │  │
   ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  │
  TEL BLE WiFi LoRa Opt Urb Crd Sat API
   │  │  │  │  │  │  │  │  │  │
   └──┴──┴──┴──┴──┴──┴──┴──┴──┘
            │ parallel (coroutineScope + async)
            ▼
   results.filterNotNull().minByOrNull { it.rssi }
            │
            ▼
   persist(Detection) → Room-DB
   applyDetectionToAsset() → Asset-Status = ONLINE
   LearningEngine.learn(Experience)
            │
            ▼
   flushOfflineQueue() (wenn MQTT verbunden)
```

### Echtzeit-Flow (MQTT/WS/NFC → UI)

```
┌──────────┐   ┌──────────┐   ┌──────────┐
│ MQTT     │   │WebSocket │   │  NFC     │
│ Broker   │   │ Server   │   │  Tag     │
└────┬─────┘   └────┬─────┘   └────┬─────┘
     │              │              │
     ▼              ▼              ▼
MqttService   WebSocketService  NfcService
.events        .events          .detections
(SharedFlow)  (SharedFlow)     (SharedFlow)
     │              │              │
     └──────┬───────┘──────┬──────┘
            ▼              ▼
     AgentService.startRealtimeChannels()
     ┌─────────────────────────────────┐
     │ handleMqttEvent()               │→ Detection persistieren
     │ handleWebSocketEvent()          │→ Asset-Status aktualisieren
     │ handleNfcDetection()            │→ Notification + AuditLog
     └─────────────────────────────────┘    + AlertSoundManager
```

---

## 📡 Detection-Kanäle (9)

| # | Kanal | Service | Quelle | null wenn |
|---|-------|---------|--------|-----------|
| 1 | **BLE** | `BleService` | `BluetoothLeScanner` + MAC-Filter (2s Scan) | Kein BLE-Hit / keine Permission |
| 2 | **WiFi** | `WifiService` | `WifiManager.startScan()` + BSSID-Match | Asset-MAC nicht in Scan-Ergebnissen |
| 3 | **LoRa** | `LoraService` | Helium Network API (Hotspot-Discovery) | Kein Hotspot in der Nähe / API-Fehler |
| 4 | **Optical** | `OpticalService` | QR-Code-Match vs MAC/ID/VIN | Kein Code gescannt / kein Match |
| 5 | **Urban** | `UrbanService` | OpenChargeMap → DHL → CKAN | Keine Infrastruktur in der Nähe |
| 6 | **Crowd** | `CrowdService` | HTTP GET `/api/crowd/search` | `externalAllowed=false` / Backend leer |
| 7 | **Satellite** | `SatelliteService` | `FusedLocationProviderClient` (GPS) | Kein GPS-Fix + keine letzte Position |
| 8 | **Telemetry** | `TelemetryService` | BLE-GATT Read → JSON-Parse | GATT-Verbindung fehlgeschlagen |
| 9 | **API** | `ApiNodeManager` | 11 Nodes (WiGle, MacLookup, Google, ...) | Alle Nodes offline / kein Treffer |

**Kanal-Priorisierung (Learning Mode):** Der Kanal, der zuletzt einen Treffer für ein Asset geliefert hat, wird im nächsten Zyklus zuerst abgefragt.

---

## 🔗 Echtzeit-Kanäle (3)

| Kanal | Service | Protocol | Flow | Events |
|-------|---------|----------|------|--------|
| **MQTT** | `MqttService` | Paho `MqttAsyncClient` | `events: SharedFlow<MqttEvent>` | Telemetry, Alert, Status, Broadcast, Connected, Disconnected, Error |
| **WebSocket** | `WebSocketService` | OkHttp WebSocket | `events: SharedFlow<WebSocketEvent>` | Telemetry, Alert, AssetUpdate, SystemStatus, Connected, Disconnected, Error |
| **NFC** | `NfcService` | Android `NfcAdapter` | `detections: SharedFlow<Detection>` | NDEF-Tag gelesen → Detection(NFC) |

**MQTT-Topics:**
```
secureguard/+/telemetry    (subscribe, QoS 1)
secureguard/+/alert        (subscribe, QoS 2)
secureguard/+/command      (publish, QoS 1)
secureguard/+/status       (subscribe, QoS 1)
secureguard/broadcast      (subscribe)
secureguard/{MAC}/command  (publish für spezifisches Asset)
```

---

## 🔌 Externe APIs (8)

| API | Datei | Auth | Cache | Retry | Funktion |
|-----|-------|------|-------|-------|----------|
| **WiGle.net** | `WiGleApi.kt` | Bearer (WIGLE_API_KEY) | ✅ `wigle_{bssid}` | ✅ 2x | BSSID → GPS-Koordinaten |
| **MacLookup** | `MacLookupApi.kt` | Keine | ✅ `mac_{mac}` | ✅ 2x | MAC → Hersteller (OUI) |
| **Open Charge Map** | `OpenChargeMapApi.kt` | API-Key (OPEN_CHARGE_MAP_KEY) | – | – | Ladesäulen um Position |
| **DHL** | `DhlPackstationApi.kt` | Keine | – | – | Paketstationen um Position |
| **CKAN** | `CkanOpenDataApi.kt` | Keine | – | – | Smart-City-Datensätze |
| **Google Geolocation** | `GoogleGeolocationApi.kt` | API-Key (GOOGLE_API_KEY) | – | – | WLAN-Triangulation |
| **Netatmo** | `NetatmoWeatherApi.kt` | Bearer (NETATMO_TOKEN) | – | – | Wetterstationen |
| **Helium** | `HeliumNetworkApi.kt` | Keine | – | – | LoRaWAN-Hotspots + Beacons |

**ApiServiceManager:** Zentraler Manager mit `CacheManager` (5 Min TTL, 100 Einträge LRU) und `RetryManager` (exponentieller Backoff).

---

## ⚙️ Services (30)

| Service | Dependencies | Funktion |
|---------|-------------|----------|
| `AgentService` | 18 (alle Kanäle + MQTT + WS + NFC + USB + Sound + Error) | Kern-Orchestrierung |
| `BleService` | Context | BLE-Scan (BluetoothLeScanner) |
| `WifiService` | Context | WiFi-Scan (WifiManager) |
| `LoraService` | Context, MqttService | Helium API + MQTT-Commands |
| `OpticalService` | Context | QR-Code-Match |
| `UrbanService` | Context, ApiServiceManager | OpenChargeMap + DHL + CKAN |
| `CrowdService` | Context | Backend HTTP GET |
| `SatelliteService` | Context | GPS (FusedLocationProvider) |
| `TelemetryService` | Context | BLE-GATT Read/Write + JSON |
| `MqttService` | – | Paho MqttAsyncClient |
| `WebSocketService` | – | OkHttp WebSocket |
| `ApiServiceManager` | CacheManager | 8 Retrofit-Clients |
| `LearningEngine` | – | Muster + Prädiktion + Intervall |
| `NotificationService` | Context | 4 Notification-Channels |
| `AlertSoundManager` | – | ToneGenerator pro Schweregrad |
| `AuthManager` | Context | PBKDF2 PIN + Auto-Lock |
| `EncryptionService` | – | AES/GCM AndroidKeyStore |
| `AuditLogService` | Database | Audit-Log persistieren |
| `OfflineQueue` | Database | Room-persistierte Aktions-Queue |
| `BackupManager` | Context, Database | SQLite-Backup + Restore |
| `DatabaseCleanup` | Database | Retention: 30d/90d/365d |
| `ExportService` | Context, Repository, EncryptionService | CSV + PDF Export |
| `OfflineMapService` | – | osmdroid Cache + MBTiles |
| `NfcService` | Context | NDEF-Tag-Lesung |
| `UsbSerialService` | Context | FTDI/CP210x/CH34x Serial |
| `TempMailService` | MCPClient | Inbox + OTP + Magic Link |
| `MCPClient` | – | JSON-RPC WebSocket |
| `ApiNodeManager` | 5 Services | 11 Nodes: Circuit-Breaker + Rate-Limits |
| `AgentForegroundService` | AgentService, NotificationService | Android Foreground Service |
| `DetectionCapable` | – | Abstract Base (SharedFlow) |

---

## 📱 UI-Screens (13)

| Screen | ViewModel | Route | Funktionen |
|--------|-----------|-------|-----------|
| **DashboardScreen** | DashboardViewModel | `dashboard` | 4 StatCards, Agent-Toggle, Refresh, Alarm-Navigation |
| **AssetListScreen** | AssetListViewModel | `assets` | Suche, Status-Filter, AssetCards |
| **AssetDetailScreen** | AssetDetailViewModel | `asset_detail/{id}` | Info, Telemetrie, Suche (3 Kanäle), 4 Aktionen, Historie |
| **AddAssetScreen** | AddAssetViewModel | `add_asset` | Name, Kurzname, MAC, VIN, QR-Scan |
| **ScanQrScreen** | ScanQrViewModel | `scan_qr` | ZXing-Kamera + manuelle Eingabe → OpticalService |
| **MapScreen** | MapViewModel | `map` | osmdroid, farbcodierte Marker, Legende, Zoom |
| **ActionsScreen** | ActionsViewModel | `actions` | Asset-Dropdown, 8 Aktions-Buttons, Command-Log |
| **AgentConfigScreen** | AgentViewModel | `agent_config` | Intervall, Dauer, Priorität, Learning-Toggle |
| **AlertsScreen** | AlertsViewModel | `alerts` | Alert-Liste, Bestätigen, Alle bestätigen |
| **SettingsScreen** | SettingsViewModel | `settings` | Profil (editierbar), Notifications, Verbindungen, DSGVO, Backup/CSV/PDF, ForegroundService |
| **NodeStatusScreen** | NodeStatusViewModel | `node_status` | 11 Nodes: Status, Toggle, Test-Suche |
| **TempMailScreen** | TempMailViewModel | `temp_mail` | Inbox erstellen, OTP abrufen, Log |
| **LockScreen** | – | (Modal) | PIN-Eingabe, Versuchsanzeige |

---

## 📊 Datenmodelle (8)

| Modell | Typ | Tabelle | Felder |
|--------|-----|---------|--------|
| `Asset` | @Entity | `assets` | id, name, shortName, mac, vin, status, rssi, batteryLevel, lat, lon, lastSeen, whitelisted, externalAllowed, maintenanceDue, notes |
| `Detection` | @Entity | `detections` | id, assetMac, sourceType, nodeId, rssi, lat, lon, accuracyMeters, message, timestamp |
| `Alert` | @Entity | `alerts` | id, assetId, type, severity, message, acknowledged, timestamp |
| `AuditLog` | @Entity | `audit_log` | id, userId, action, details, timestamp, deviceId, ipAddress |
| `PendingAction` | @Entity | `pending_actions` | id, actionType, assetMac, payload, createdAt, attempts, lastError |
| `Telemetry` | Data Class | (In-Memory) | mac, batteryPercent, fuelPercent, motorOk, tiresOk, operatingHours, kilometers, lat, lon |
| `SearchResult` | Data Class | – | found, detection, accuracy |
| **Enums** | | | AssetStatus(5), DetectionSource(13), AlertType(7), AlertSeverity(3) |

---

## 🗄️ Datenbank (Room v2)

### Schema

```sql
-- Migration 1→2: audit_log + pending_actions hinzugefügt
assets         (id PK, name, shortName, mac UNIQUE, vin, status, rssi, batteryLevel, lat, lon, lastSeen, whitelisted, externalAllowed, maintenanceDue, notes, createdAt, updatedAt)
detections     (id PK AUTO, assetMac, sourceType, nodeId, rssi, lat, lon, accuracyMeters, message, timestamp)
alerts         (id PK AUTO, assetId, type, severity, message, acknowledged, timestamp)
audit_log      (id PK AUTO, userId, action, details, timestamp, deviceId, ipAddress)
pending_actions(id PK AUTO, actionType, assetMac, payload, createdAt, attempts, lastError)
```

### DAOs (5)

| DAO | Funktionen |
|-----|-----------|
| `AssetDao` | observeWhitelisted, observeAll, getById, getByMac, getByIdOrMac, upsert, update, updateStatus, setStatus, deleteById, count, getPage |
| `DetectionDao` | observeForAsset, observeAll, latestForAsset, insert, deleteForAsset, deleteOlderThan |
| `AlertDao` | observeAll, observeUnacknowledged, observeUnacknowledgedCount, insert, acknowledge, acknowledgeAll, clear, deleteOlderThan |
| `AuditLogDao` | observeAll, latest, insert, deleteOlderThan, clear |
| `PendingActionDao` | observeAll, getAll, count, insert, deleteById, markAttempt |

### Repository (22 Funktionen)

`SecureGuardRepository` (Interface) → `SecureGuardRepositoryImpl`:
getWhitelistedAssets, getAllAssets, getAssetByMac, getAssetById, resolveAsset, upsertAsset, updateAssetStatus, deleteAsset, getDetections, getAllDetections, getLatestDetection, insertDetection, getAssetsPaginated, deleteDetectionsOlderThan, deleteAlertsOlderThan, getAlerts, getUnacknowledgedAlerts, getUnacknowledgedAlertCount, insertAlert, acknowledgeAlert, acknowledgeAllAlerts, raiseAlert

---

## 🔒 Sicherheit & Berechtigungen

### Android-Permissions (21)

| Permission | Genutzt von | Zweck |
|-----------|-------------|-------|
| `INTERNET` | MqttService, WebSocketService, ApiServiceManager, CrowdService | Netzwerk |
| `ACCESS_NETWORK_STATE` | ApiServiceManager | Netz-Status |
| `BLUETOOTH` (≤API 30) | BleService, TelemetryService | BLE-Scan |
| `BLUETOOTH_ADMIN` (≤API 30) | BleService | BLE-Scan starten |
| `BLUETOOTH_SCAN` (≥API 31) | BleService | BLE-Scan |
| `BLUETOOTH_CONNECT` (≥API 31) | TelemetryService | GATT-Verbindung |
| `ACCESS_WIFI_STATE` | WifiService | WiFi-Scan-Ergebnisse |
| `CHANGE_WIFI_STATE` | WifiService | WiFi-Scan starten |
| `ACCESS_FINE_LOCATION` | BleService, WifiService, SatelliteService | BLE/WiFi-Scan + GPS |
| `ACCESS_COARSE_LOCATION` | BleService, WifiService | Fallback-Standort |
| `CAMERA` | ScanQrScreen | QR-Code-Scanner |
| `NFC` | NfcService, MainActivity | NFC-Tag-Lesung |
| `POST_NOTIFICATIONS` (≥API 33) | NotificationService | Push-Benachrichtigungen |
| `VIBRATE` | NotificationService | Alarm-Vibration |
| `MODIFY_AUDIO_SETTINGS` | AlertSoundManager | Alarm-Töne |
| `FOREGROUND_SERVICE` | AgentForegroundService | Hintergrund-Agent |
| `FOREGROUND_SERVICE_DATA_SYNC` | AgentForegroundService | Foreground-Typ |
| `WAKE_LOCK` | WorkManager | Worker-Ausführung |
| `RECEIVE_BOOT_COMPLETED` | BootReceiver | Agent nach Neustart |
| `READ_EXTERNAL_STORAGE` (≤API 28) | BackupManager | Backup-Dateien lesen |
| `WRITE_EXTERNAL_STORAGE` (≤API 28) | BackupManager, ExportService | Backup/Export schreiben |

### Hardware-Features (optional)

`bluetooth_le`, `camera`, `location.gps`, `nfc`, `usb.host` – alle `required="false"`

### Sicherheitsmechanismen

| Mechanismus | Implementierung |
|-------------|-----------------|
| **PIN-Sperre** | PBKDF2WithHmacSHA256, 100.000 Iterationen, 256-Bit-Key, 16-Byte-Salt |
| **Auto-Lock** | 5 Minuten Inaktivität |
| **Max. Versuche** | 5 Fehlversuche → App gesperrt |
| **Verschlüsselung** | AES/GCM/NoPadding, 256-Bit, AndroidKeyStore (Schlüssel verlässt Gerät nie) |
| **RBAC** | 4 Rollen (ADMIN, MANAGER, OPERATOR, VIEWER) × 7 Permissions |
| **DSGVO** | Opt-in für externe Kanäle, lokale Datenhaltung, Audit-Log |

---

## 🖥️ Backend (FastAPI)

### Endpoints (13)

| Methode | Pfad | Funktion |
|---------|------|----------|
| GET | `/api/health` | Gesundheitsstatus |
| GET | `/api/assets` | Alle Assets |
| POST | `/api/assets` | Asset anlegen/aktualisieren |
| GET | `/api/detections` | Detektionen (limit) |
| POST | `/api/detections` | Detektion hinzufügen |
| GET | `/api/alerts` | Alerts (unresolved_only) |
| POST | `/api/alerts` | Alert hinzufügen |
| POST | `/api/actions/execute` | Aktion ausführen (async via MQTT) |
| GET | `/api/commands` | Befehls-Historie |
| GET | `/api/stats` | Statistiken |
| POST | `/api/crowd/report` | Crowd-Sichtung melden |
| GET | `/api/crowd/search` | Crowd-Sichtungen abfragen |
| WS | `/ws` | Echtzeit-Updates (Command + MQTT-Bridge) |

### MQTT → WebSocket Bridge

Das Backend abonniert `secureguard/+/telemetry`, `+/alert`, `+/status` und forwarded alle Nachrichten als JSON an alle verbundenen WebSocket-Clients.

### Datenbank (SQLite)

5 Tabellen: `assets`, `detections`, `alerts`, `commands`, `crowd_sightings` (+ Index auf `crowd_sightings.mac`)

---

## 📟 Firmware (ESP32)

### Hardware

| Komponente | Pin/Anschluss | Bibliothek |
|-----------|---------------|-----------|
| LoRa SX1278 | SS=5, RST=14, DIO0=2 (868 MHz) | MCCI LoRa |
| BLE | Integrated | ESP32 BLE Arduino |
| WiFi | Integrated | WiFi.h |
| MQTT | WiFi → Broker | PubSubClient |
| LED/Buzzer | GPIO2 | – |
| Motor-Relay | GPIO4 | – |
| Batterie-ADC | GPIO34 (Spannungsteiler 100k/100k) | analogRead |
| NVS-Speicher | Flash | Preferences.h |

### Befehle (9)

| Befehl | Aktion |
|--------|--------|
| `ALARM` | LED 5x blinken (200ms) |
| `LIGHT` | LED 5s an |
| `MOTOR_OFF` | GPIO4 Relay aus |
| `BATTERY` | Sofort: Batterie-Status senden (Prozent + Spannung) |
| `MESSAGE` | LED 3x kurz blinken (Bestätigung) |
| `POSITION` | Sofort: Geräte-IP + WiFi-RSSI senden |
| `RESTART` | `ESP.restart()` |
| `TELEMETRY` | Sofort: Vollständige Telemetrie senden |
| `CONFIG` | JSON parsen → NVS speichern → Neustart |

### BLE GATT UUIDs

```
Service:        6BA1B218-15A8-461F-9FA8-5DC85327FD13
Telemetry Char: 6BA1B218-15A8-461F-9FA8-5DC85327FD14 (Read + Notify)
Command Char:   6BA1B218-15A8-461F-9FA8-5DC85327FD15 (Write)
```

### Telemetrie-Payload (JSON)

```json
{
  "type": "telemetry",
  "battery": 85,
  "wifi_rssi": -42,
  "lora_rssi": -67,
  "uptime": 3600,
  "ip": "192.168.1.50",
  "device": "ESP32_SecureGuard"
}
```

### Konfiguration (NVS)

| Key | Default | Beschreibung |
|-----|---------|-------------|
| `wifi_ssid` | `SECUREGUARD` | WiFi-SSID |
| `wifi_pass` | `secureguard123` | WiFi-Passwort |
| `mqtt_host` | `192.168.1.100` | MQTT-Broker-Host |
| `mqtt_port` | `1883` | MQTT-Broker-Port |
| `device_id` | `ESP32_SecureGuard` | Geräte-ID |

Konfiguration änderbar via MQTT-Befehl `CONFIG` mit JSON-Payload.

---

## 🐳 Docker-Stack

```yaml
services:
  mqtt:        # Eclipse Mosquitto 2.0
    ports: 1883 (MQTT), 9001 (WebSocket)
  backend:     # FastAPI + Uvicorn
    ports: 8000
    depends_on: mqtt
  nodered:     # Node-RED Dashboard
    ports: 1880
```

**Start:** `docker compose up --build`

---

## 📦 Abhängigkeiten (48 Libraries)

### Build-System

| Tool | Version |
|------|---------|
| AGP (Android Gradle Plugin) | 8.7.3 |
| Kotlin | 2.0.21 |
| Gradle | 8.9 |
| JDK | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 (Android 8) |

### AndroidX Core

| Library | Version | Zweck |
|---------|---------|-------|
| core-ktx | 1.15.0 | Kotlin-Erweiterungen |
| lifecycle-runtime-ktx | 2.8.7 | Lifecycle-aware Components |
| lifecycle-runtime-compose | 2.8.7 | Compose Lifecycle |
| lifecycle-viewmodel-compose | 2.8.7 | ViewModel in Compose |
| lifecycle-viewmodel-ktx | 2.8.7 | ViewModel Coroutines |
| activity-compose | 1.9.3 | Compose Activity |
| appcompat | 1.7.0 | Abwärtskompatibilität |
| material | 1.12.0 | Material Design (ZXing benötigt AppCompat) |

### Jetpack Compose

| Library | Version | Zweck |
|---------|---------|-------|
| compose-bom | 2024.12.01 | BOM für konsistente Versionen |
| ui | (BOM) | Compose UI |
| ui-graphics | (BOM) | Grafik-APIs |
| ui-tooling-preview | (BOM) | Preview |
| material3 | (BOM) | Material 3 Components |
| material3-window-size | (BOM) | Adaptive Layouts |
| material-icons-extended | (BOM) | Alle Material Icons |
| navigation-compose | 2.8.5 | Navigation |

### Dependency Injection

| Library | Version | Zweck |
|---------|---------|-------|
| hilt-android | 2.52 | DI-Framework |
| hilt-compiler | 2.52 | Annotation Processor |
| hilt-navigation-compose | 1.2.0 | Hilt + Navigation |
| hilt-work | 1.2.0 | Hilt + WorkManager |
| hilt-work-compiler | 1.2.0 | Hilt Worker kapt |

### Datenbank & Paging

| Library | Version | Zweck |
|---------|---------|-------|
| room-runtime | 2.6.1 | Room ORM |
| room-ktx | 2.6.1 | Room Coroutines |
| room-compiler | 2.6.1 | Room Annotation Processor |
| paging-runtime-ktx | 3.2.1 | Paging 3 |
| paging-compose | 3.2.1 | Paging in Compose |

### Netzwerk

| Library | Version | Zweck |
|---------|---------|-------|
| retrofit | 2.9.0 | HTTP-Client |
| retrofit-converter-gson | 2.9.0 | Gson-Converter |
| retrofit-converter-moshi | 2.9.0 | Moshi-Converter |
| retrofit-adapter-rxjava3 | 2.9.0 | RxJava-Adapter |
| okhttp | 4.12.0 | HTTP-Client (Basis) |
| okhttp-logging-interceptor | 4.12.0 | Request-Logging |
| okhttp-sse | 4.12.0 | Server-Sent Events |

### JSON & Serialisierung

| Library | Version | Zweck |
|---------|---------|-------|
| moshi | 1.15.1 | JSON-Parsing |
| moshi-kotlin | 1.15.1 | Kotlin-Reflection |
| moshi-kotlin-codegen | 1.15.1 | Codegen (kapt) |
| gson | 2.10.1 | JSON-Parsing (MQTT/WS) |
| kotlinx-serialization-json | 1.6.0 | Kotlin Serialization |

### MQTT & Echtzeit

| Library | Version | Zweck |
|---------|---------|-------|
| paho-mqtt-client | 1.2.5 | MQTT-Client |

### Standort & BLE

| Library | Version | Zweck |
|---------|---------|-------|
| play-services-location | 21.0.1 | FusedLocationProvider |
| nordic-ble-ktx | 2.6.0 | BLE Kotlin Extensions |

### Hardware

| Library | Version | Zweck |
|---------|---------|-------|
| usb-serial | 3.5.1 | FTDI/CP210x/CH34x Serial |
| zxing-core | 3.5.3 | QR/Barcode-Core |
| zxing-embedded | 4.3.0 | ZXing Android-Integration |

### Karte & UI

| Library | Version | Zweck |
|---------|---------|-------|
| osmdroid-android | 6.1.20 | OpenStreetMap |
| coil-compose | 2.6.0 | Bildladen |
| accompanist-permissions | 0.32.0 | Permission-Handling |

### Reaktiv

| Library | Version | Zweck |
|---------|---------|-------|
| rxjava | 3.1.8 | RxJava 3 |
| rxandroid | 3.0.2 | RxJava Android Scheduler |
| kotlinx-coroutines-android | 1.9.0 | Coroutines |

### Sonstige

| Library | Version | Zweck |
|---------|---------|-------|
| work-runtime-ktx | 2.9.0 | Hintergrund-Tasks |
| desugar-jdk-libs | 2.1.2 | Java 8+ API-Desugaring |

### Testing

| Library | Version |
|---------|---------|
| junit | 4.13.2 |
| androidx-test-ext-junit | 1.1.5 |
| espresso-core | 3.5.1 |
| compose-ui-test-junit4 | (BOM) |
| compose-ui-test-manifest | (BOM) |

---

## 🔨 Build & CI

### GitHub Actions

Workflow `.github/workflows/build-release.yml`:
- **Trigger:** **jedes neue GitHub Release** (`release: created`), Tags `v*`, Push auf `main`, Pull Requests, manuell
- **JDK:** 17 · **SDK:** android-35 · **Build-Tools:** 35.0.0 · **Gradle:** 8.9
- **Release-Verhalten:** Bei jedem neuen Release wird die signierte `release.apk` (+ `SHA256SUMS.txt`, `BUILD_INFO.txt`) automatisch an das Release gehängt. Wird per Tag-Push noch kein Release existiert, legt der Workflow es an.
- **Version:** `versionName`/`versionCode` werden im CI aus dem Release-Tag abgeleitet (z. B. `v1.0.8` → `1.0.8`/`10008`)
- **Artefakte:** `secureguard-pro` (Release-APK), `secureguard-pro-debug` (Debug-APK)
- **Release-Signing:** Via Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (ohne Secrets: unsignierte APK)
- **API-Keys:** Optionale Secrets `WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`, `MQTT_BROKER_URL`, `WEBSOCKET_URL`, `MCP_SERVER_URL` landen (falls gesetzt) in `BuildConfig`

> ⚠️ `release`-Ereignisse nutzen den Workflow aus dem Head des Default-Branches – dieser Workflow muss daher vor dem ersten Release in `main` gemerged sein.

### Lokal bauen

```bash
# 1. API-Keys konfigurieren
cp local.properties.example local.properties
# local.properties editieren

# 2. Debug-APK bauen
./gradlew :app:assembleDebug

# 3. Release-APK bauen (unsiginiert ohne Keystore)
./gradlew :app:assembleRelease
```

### BuildConfig-Felder

| Feld | Quelle | Genutzt von |
|------|--------|-------------|
| `WIGLE_API_KEY` | local.properties | ApiServiceManager |
| `OPEN_CHARGE_MAP_KEY` | local.properties | ApiServiceManager |
| `NETATMO_TOKEN` | local.properties | ApiServiceManager |
| `GOOGLE_API_KEY` | local.properties | ApiServiceManager |
| `MQTT_BROKER_URL` | local.properties | MqttConfig |
| `WEBSOCKET_URL` | local.properties | WebSocketService |
| `MCP_SERVER_URL` | local.properties | MCPClient |

---

## ⚙️ Konfiguration

### local.properties.example

```properties
WIGLE_API_KEY=your_wigle_key_here
OPEN_CHARGE_MAP_KEY=your_ocm_key_here
NETATMO_TOKEN=your_netatmo_token_here
GOOGLE_API_KEY=your_google_key_here
MQTT_BROKER_URL=mqtt://broker.example.com:1883
WEBSOCKET_URL=ws://api.example.com:8000/ws
MCP_SERVER_URL=http://api.example.com:8000
```

---

## 📟 Honeywell CT45P XON

| Bereich | Umsetzung |
|---------|-----------|
| **Android 11 (API 30)** | `CT45PConfig.kt` erkennt Gerät + loggt beim Start |
| **MQTT (tcp://)** | `usesCleartextTraffic="true"` im Manifest |
| **BLE-Scan** | API ≤ 30: `ACCESS_FINE_LOCATION` statt `BLUETOOTH_SCAN` |
| **WiFi-Scan** | Standortberechtigung erforderlich |
| **Barcode-Scanner** | HID-Keyboard (2D-Imager) + ZXing-Kamera |
| **USB-Host** | FTDI/CP210x/CH34x via `UsbSerialService` + `device_filter.xml` |
| **NFC** | NDEF + Tech-Filter (`nfc_tech_filter.xml`) |
| **Benachrichtigungen** | `POST_NOTIFICATIONS` nur ab API 33 |
| **Boot-Restart** | `BootReceiver` → WorkManager reschedule |

---

## 📲 Installation

1. **Debug-APK** aus GitHub Actions laden → `adb install secureguard-pro-debug.apk`
2. Berechtigungen erteilen: Standort, Bluetooth, Kamera, Benachrichtigungen
3. Einstellungen → Backend-Endpunkte konfigurieren
4. Optional: Foreground-Dienst starten für dauerhaften Betrieb

---

## 🛡️ Datenschutz

- Alle Ortungsdaten verbleiben in der lokalen Room-Datenbank
- Externe Kanäle (Crowd/Satellit/APIs) standardmäßig deaktiviert
- DSGVO-Einwilligung erforderlich für externe Datenverarbeitung
- Audit-Log dokumentiert alle sicherheitsrelevanten Aktionen
- Export/Backup optional AES/GCM-verschlüsselt

---

## 📄 Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE).
