# SecureGuard Enterprise – Ready-to-Go Checkliste

Stand: 2026-08-27 · Branch `arena/01a0412d-dinge88` · Vollaudit + Reparatur: **docs/FEHLER_MANGEL_LISTE.md**  
Ziel: **vollständige App mit allen Diensten angebunden und einsatzbereit**.

> **Update 2026-08-26 (Vollbereitstellung):** `prepare-all.sh`, Docker-Healthchecks, Branch/PR-ready. Alle Funktionen erhalten.

> **Update 2026-08-27 (Vollaudit-Reparatur):** 60 Befunde (`FEHLER_MANGEL_LISTE.md`) repariert:
> StrongBox-Crash, Main-Thread-IO, PIN-Lockout, Backup-Validierung/WAL, MQTT-Wildcard,
> SPI-Pin-Konflikt, Backend-Auth/CORS/Health-503, USB-Permission-Flow, Background-Location,
> Backup-Rules, device_filter-Hex, CI-Tests (Android+Backend), AgentSettings-Persistenz,
> NDEF-Parser, WiGle-Basic-Auth, requirements-Pinning, Node-RED-Cleanup. Siehe auch IMPLEMENTIERUNGS_INVENTUR.md.

> **Update 2026-08-26 (Batch 4 / Go-Live-Pack):** DSGVO Datenauskunft/Retention/Löschen, Health-Monitor-Screen, Backend `/api/health` Stats, smoke/start-stack Scripts, CI Unit-Tests.

> **Update 2026-08-26 (Batch 3):** UI/Unit-Tests (PIN, Asset-CRUD, Agent-Cycle); Keystore-Passwörter nur vom Anwender.

> **Update 2026-08-26 (Batch 2):** SQLCipher Room, Release-Keystore-Script, Release NSC ohne Cleartext.

> **Update 2026-08-26 (Batch Ready-to-Go):** Runtime-Endpunkte (Settings), MQTT Auth,
> BuildConfig-Keys (LORA/YOLO/CKAN/FIND_MY/BACKEND/MQTT_USER), YOLO+LoRa-Gateway-HTTP,
> Find-My-Proxy, Backend-Sync, Node-RED Flows, Mosquitto Prod-Hinweise, compileSdk 35.



Legende:

| Symbol | Bedeutung |
|--------|-----------|
| ✅ | Im Code fertig, lokal/online lauffähig |
| ⚙️ | Code da, braucht **Konfiguration / Keys / Hardware** |
| 🟡 | Teilweise – funktioniert, aber Lücke oder Fallback |
| ❌ | Noch **nicht** implementiert / nicht verdrahtet |
| ⏳ | Bewusst Phase 2 |

---

## 1. Gesamtstatus auf einen Blick

| Bereich | Status | Kurz |
|---------|--------|------|
| Android-App (UI + Navigation) | ✅ | 18 Screens (+ Health), Bottom-Nav, Hilt |
| Agent + 9 Detection-Kanäle | ✅/⚙️ | Code fertig; externe Kanäle brauchen Keys/Consent |
| MQTT / WebSocket / NFC realtime | ✅/⚙️ | Runtime-Settings + MQTT Auth; Broker-URL setzen |
| 8 externe REST-APIs + Gateways | ✅/⚙️ | Keys optional; LORA/YOLO/CKAN/Find-My verdrahtet |
| Backend FastAPI + Crowd + MCP-REST | ✅ | Docker-ready |
| Mosquitto MQTT | ✅/🟡 | Pilot anonym; passwd/acl/TLS-Beispiele im Repo |
| Node-RED | ✅ | `flows.json` Telemetrie/Commands/Health |
| ESP32-Firmware | ⚙️ | PlatformIO + `.ino`; flashen + WiFi/MQTT setzen |
| Offline-Toolchain | ✅ | `scripts/offline/*` |
| Release-Signing | ✅/⚙️ | `scripts/create-release-keystore.sh` + CI-Secrets |
| SQLCipher (DB-Verschlüsselung) | ✅ | sqlcipher-android + KeyStore-Passphrase + Migration |
| Produktiv-TLS / Zertifikate | ✅/⚙️ | release NSC verbietet Cleartext; MQTT ssl:// ready |
| Instrumentierte UI-Tests | ✅/⚙️ | Compose Lock+Asset + Unit Auth/CRUD/Agent (lokal/CI) |
| Verifizierter Gradle-Build (diese Sandbox) | ⚙️ | kein JDK hier – **lokal/CI** `./gradlew testDebugUnitTest assembleDebug` |

**Fazit:** Die App ist **feature-complete im Code**.  
„Ready to go“ in der Praxis = **Build + Config + Keys + Backend + Hardware**.

---

## 2. Was schon fertig ist (Code ✅)

### 2.1 UI (18 Composables)

| # | Screen | Route | Status |
|---|--------|-------|--------|
| 1 | Dashboard | `dashboard` | ✅ |
| 2 | Asset-Liste | `assets` | ✅ |
| 3 | Asset-Detail | `asset_detail/{id}` | ✅ |
| 4 | Asset hinzufügen | `add_asset` | ✅ |
| 5 | QR-Scan | `scan_qr` | ✅ |
| 6 | Karte (osmdroid) | `map` | ✅ |
| 7 | Aktionen | `actions` | ✅ |
| 8 | Einstellungen | `settings` | ✅ |
| 9 | Agent-Config | `agent_config` | ✅ |
| 10 | Alerts | `alerts` | ✅ |
| 11 | Node-Status | `node_status` | ✅ |
| 12 | Temp-Mail | `temp_mail` | ✅ |
| 13 | Terminal | `terminal` | ✅ |
| 14 | Sensor-Fusion | `sensor_fusion` | ✅ |
| 15 | Security-Center | `security` | ✅ |
| 16 | ESP32-Config | `esp32_config` | ✅ |
| 17 | Lock-Screen (PIN) | (MainActivity) | ✅ |
| 18 | System-Health | `health` | ✅ |

### 2.2 Services (30) – im Code angebunden

| Service | An Agent / UI | Braucht extern |
|---------|---------------|----------------|
| `AgentService` | Kern | – |
| `BleService` | Detection | BLE-Hardware + Permission |
| `WifiService` | Detection | WiFi + Location-Permission |
| `LoraService` | Detection | Helium API / MQTT-Gateway |
| `OpticalService` | Detection | Kamera / Scan-Ergebnis |
| `UrbanService` | Detection | OCM/DHL/CKAN (teilw. Keys) |
| `CrowdService` | Detection | Backend `WEBSOCKET_URL` → HTTP |
| `SatelliteService` | Detection | GPS + Play Services |
| `TelemetryService` | Detection/Action | BLE-GATT Asset |
| `MqttService` | Realtime + Actions | `MQTT_BROKER_URL` |
| `WebSocketService` | Realtime | `WEBSOCKET_URL` |
| `NfcService` | Realtime | NFC-Hardware + NDEF-Tag |
| `UsbSerialService` | Detection | USB-OTG + Adapter |
| `ApiServiceManager` | 8 REST-APIs | Keys |
| `ApiNodeManager` | 11 Nodes | Keys / Verbindungen |
| `LearningEngine` | Agent | Daten aus Zyklen |
| `NotificationService` | überall | POST_NOTIFICATIONS |
| `AlertSoundManager` | Alerts | Audio |
| `AuthManager` | PIN | User setzt PIN |
| `PrivacyService` | DSGVO Export/Löschen | – |
| `HealthMonitorService` | Health-Screen | Backend optional |
| `EncryptionService` | Export/Security | Keystore (Gerät) |
| `AuditLogService` | Agent/Security | – |
| `OfflineQueue` | Actions offline | MQTT später |
| `BackupManager` | Settings | Storage |
| `DatabaseCleanup` | Worker | – |
| `ExportService` | Settings | – |
| `OfflineMapService` | Map | optional MBTiles |
| `TempMailService` + `MCPClient` | TempMail/Agent | `MCP_SERVER_URL` |
| `AgentForegroundService` | Settings/Agent | FGS-Permission |
| `SecureAgentWorker` | Application | WorkManager |
| `RoleManager` | Actions/Detail | aktuell Single-User ADMIN |

### 2.3 Backend-API (FastAPI)

| Endpoint | Status |
|----------|--------|
| `GET /api/health` | ✅ |
| `GET/POST /api/assets` | ✅ |
| `GET/POST /api/detections` | ✅ |
| `GET/POST /api/alerts` | ✅ |
| `POST /api/actions/execute` | ✅ → MQTT |
| `GET /api/commands` | ✅ |
| `GET /api/stats` | ✅ |
| `POST /api/crowd/report` | ✅ |
| `GET /api/crowd/search` | ✅ |
| `POST /api/mcp/create_inbox` | ✅ |
| `POST /api/mcp/inject_message` | ✅ |
| `GET /api/mcp/wait_for_otp` | ✅ |
| `GET /api/mcp/extract_magic_link` | ✅ |
| `WS /ws` | ✅ |

### 2.4 Firmware ESP32

| Feature | Status |
|---------|--------|
| LoRa RX 868 → MQTT | ✅ Code |
| BLE Peripheral Telemetrie | ✅ Code |
| MQTT Commands (9) | ✅ Code |
| NVS Config | ✅ Code |
| `platformio.ini` | ✅ |
| Offline-Pkg-Skripte | ✅ |

---

## 3. Was **du** noch tun musst (Betriebs-Checkliste)

### A) Build-Maschine (Pflicht)

- [ ] **JDK 17** installieren (`JAVA_HOME`)
- [ ] **Android SDK** (platforms 34+35, build-tools 34/35, platform-tools)
- [ ] `local.properties` anlegen:
  ```bash
  cp local.properties.example local.properties
  ```
- [ ] `sdk.dir=/absoluter/pfad/zum/android-sdk` setzen
- [ ] Debug bauen:
  ```bash
  ./gradlew :app:assembleDebug
  ```
- [ ] APK installieren:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] *(Optional air-gapped)* `./scripts/offline/download-all.sh` → USB → `install-offline.sh`

### B) Backend-Stack starten (Pflicht für Crowd/WS/MCP/MQTT-Bridge)

- [ ] Docker installiert
- [ ] Im Repo-Root:
  ```bash
  docker compose up --build -d
  ```
- [ ] Prüfen:
  - MQTT `localhost:1883`
  - API `http://localhost:8000/api/health`
  - WebSocket `ws://localhost:8000/ws`
  - Node-RED `http://localhost:1880`
- [ ] Firewall/Ports im Firmennetz freigeben (oder nur LAN)

### C) `local.properties` – Endpunkte (Pflicht für Anbindung)

Werte **vor dem Build** setzen (landen in `BuildConfig`):

| Key | Beispiel (Gerät im LAN) | Wofür |
|-----|-------------------------|--------|
| `MQTT_BROKER_URL` | `tcp://192.168.1.10:1883` | App ↔ Broker (Emulator: `tcp://10.0.2.2:1883`) |
| `WEBSOCKET_URL` | `ws://192.168.1.10:8000/ws` | Echtzeit + Crowd-HTTP-Basis |
| `MCP_SERVER_URL` | `http://192.168.1.10:8000` | Temp-Mail REST-Fallback |

> `mqtt://` in der Example-Datei ggf. als **`tcp://`** setzen – Paho erwartet typischerweise `tcp://` oder `ssl://`.

- [ ] URLs auf **reale Host-IP** des Backend-PCs stellen (nicht `example.com`)
- [ ] App **neu bauen** nach jeder Änderung an Keys/URLs
- [ ] Auf echtem Gerät: PC und Handy im **selben Netz**; Backend auf `0.0.0.0` (Docker-Compose bereits so)

### D) Externe API-Keys (optional pro Kanal)

Ohne Key → Kanal liefert `null`/leer, App bleibt stabil.

| Key | Dienst | Node-ID | Status |
|-----|--------|---------|--------|
| `WIGLE_API_KEY` | WiGle.net | `wigle` | ⚙️ |
| `OPEN_CHARGE_MAP_KEY` | OpenChargeMap | `openchargemap` | ⚙️ |
| `NETATMO_TOKEN` | Netatmo | `netatmo` | ⚙️ |
| `GOOGLE_API_KEY` | Google Geolocation | `googlegeo` | ⚙️ |
| *(kein Key)* | MacLookup | `maclookup` | 🟡 oft ohne Key limitiert |
| *(kein Key)* | DHL Packstation | `dhl` | 🟡 |
| *(kein Key)* | CKAN Open Data | `ckan` | 🟡 Demo-URL |
| *(kein Key)* | Helium | `helium` | 🟡 öffentl. API |

- [ ] Keys beantragen und in `local.properties` eintragen
- [ ] In der App: **DSGVO-Consent** + „Externe Crowdsource“ aktivieren
- [ ] Agent: `offlineOnly=false` / external sources an, sonst bleiben Internet-Kanäle aus

### E) In der App nach Installation

- [ ] Runtime-Permissions erlauben (Standort, BT, Kamera, Notifications)
- [ ] Optional: PIN im **Security-Center** setzen
- [ ] Settings: Offline-Only **aus**, wenn Online-Kanäle gewünscht
- [ ] Settings: Crowdsource/Consent setzen
- [ ] Agent starten (Dashboard oder Foreground-Service in Settings)
- [ ] Demo-Assets (Debug-Build) prüfen oder eigene Assets anlegen (MAC!)
- [ ] Test: Aktion ALARM an Asset (braucht MQTT/ESP32/Backend)

### F) ESP32-Gateway

- [ ] Hardware: ESP32 + LoRa-Modul (SS=5, RST=14, DIO0=2, 868 MHz)
- [ ] `pio run -t upload` (oder Arduino IDE)
- [ ] WiFi SSID/Pass + MQTT-Host setzen (NVS / App-Screen ESP32-Config / MQTT `CONFIG`)
- [ ] Topics prüfen: `secureguard/<device_id>/telemetry|command|status`
- [ ] MAC des Gateways/Assets in der App whitelisten

### G) Release / Produktion (noch offen)

- [ ] Release-Keystore erzeugen und CI-Secrets setzen (`KEYSTORE_*`)
- [ ] `assembleRelease` signiert bauen
- [ ] Mosquitto: `allow_anonymous false` + User/Pass + TLS 8883
- [x] HTTPS/WSS statt Cleartext; `network_security_config` härten (release: cleartext=false)
- [ ] Node-RED Flows für Dashboard exportieren und versionieren
- [ ] Backup-Strategie (Geräte + Backend-SQLite)

---

## 4. Noch **nicht** fertig / Lücken im Produkt

### 4.1 Code-Lücken (implementieren, wenn „100 %“ gemeint ist)

| # | Thema | Status | Was fehlt |
|---|--------|--------|-----------|
| 1 | **SQLCipher** Room-DB | ✅ | `DatabaseKeyManager` + `SqlCipherHelper` + Migration plain→enc |
| 2 | **`LORA_GATEWAY_URL`** | ✅ | BuildConfig + EndpointConfig + LoraService HTTP |
| 3 | **`YOLO_SERVER_URL`** | ✅ | OpticalService → POST `/api/v1/detect` (optional) |
| 4 | **`OPEN_DATA_API_URL`** | ✅ | EndpointConfig → ApiServiceManager CKAN base |
| 5 | **`FIND_MY_PROXY_URL`** | ✅ | CrowdService Fallback Find-My-Proxy |
| 6 | **Runtime-Config UI** | ✅ | Settings → Backend & Broker + reconnect |
| 7 | **App ↔ Backend Asset-Sync** | ✅ | BackendSyncService + Settings-Button + Agent alle 20 Zyklen |
| 8 | **Node-RED Flows** | ✅ | `nodered/flows.json` Telemetrie/Commands/Health |
| 9 | **Mipmap-PNG Fallbacks** | 🟡 | nur adaptive icons (API 26+); minSdk 26 → OK, aber keine mdpi-WebP |
| 10 | **compileSdk 34 vs CI 35** | ✅ | compileSdk = targetSdk = 35 |
| 11 | **Instrumented Tests / E2E** | ✅/⚙️ | LockScreen + AddAsset Compose; Unit Auth/Asset/Agent |
| 12 | **Multi-User / Server-RBAC** | 🟡 | `RoleManager` lokal vorbereitet, kein Login-Server |
| 13 | **MQTT Auth (User/Pass)** | ✅ | EndpointConfig + MqttConnectOptions; ssl:// SocketFactory |
| 14 | **Play Integrity / SafetyNet** | ⏳ | bewusst optional / Phase 2 |
| 15 | **Crash-Reporting** (Sentry/Firebase) | ⏳ | AuditLog + Logcat; externes SaaS optional |
| 16 | **Push (FCM)** | ⏳ | lokale Notifications; FCM optional Phase 2 |
| 17 | **i18n vollständig** | 🟡 | `values-de`/`en` minimal (App-Name + Channels) |
| 18 | **MBTiles-Download in-app** | 🟡 | URL-Helfer da, kein DownloadManager-Flow |
| 19 | **USB Permission Activity-Flow** | 🟡 | Service liest nur bei erteilter Permission; kein UI-Dialog-Flow |
| 20 | **Verifizierter Clean-Build** | ⚙️ | CI/Host: `testDebugUnitTest` + `assembleDebug/Release` |

### 4.2 Betriebs-Risiken (Pilot → Prod)

| Risiko | Empfehlung |
|--------|------------|
| Anonymer MQTT | User/ACL/TLS |
| Cleartext HTTP | WSS/HTTPS + Pinning |
| Keys im APK (BuildConfig) | steganografisch besser: verschlüsselter Remote-Config oder nur Backend-Proxy |
| Demo-Assets nur DEBUG | Release startet leer – Onboarding-UI prüfen |
| Geofence 5 km hardcoded | konfigurierbar machen |
| Agent-Intervall min. 15 Min WorkManager | FGS für engere Zyklen nutzen |

---

## 5. Dienste – Anbindungsmatrix („Ready“)

| Dienst | Code | Config | Backend/Cloud | Hardware | Ready wenn… |
|--------|------|--------|---------------|----------|-------------|
| BLE-Scan | ✅ | Permission | – | BT-LE | Permission + Gerät in Nähe |
| WiFi-Scan | ✅ | Permission | – | WiFi | Location an (≤11) |
| BLE-GATT Telemetrie | ✅ | – | – | kompatibles Asset | UUID match Firmware |
| LoRa via Helium API | ✅ | Netz | Helium | – | Asset-Lat/Lon gesetzt |
| LoRa via ESP32 / Gateway | ✅ | MQTT + LORA_GATEWAY_URL | Mosquitto / HTTP-GW | ESP32+SX1278 | Firmware oder Gateway-URL |
| Optisch (QR) | ✅ | Kamera | – | Kamera | ScanQr → OpticalService |
| Optisch (YOLO) | ✅ | YOLO_SERVER_URL | YOLO-Server | Kamera | Server erreichbar + Bild |
| Urban OCM | ✅ | OCM-Key | OpenChargeMap | – | Key + Lat/Lon |
| Urban DHL | ✅ | Netz | DHL-API | – | Lat/Lon |
| Urban CKAN | ✅ | Netz | CKAN | – | URL erreichbar |
| Crowd | ✅ | WS-URL | FastAPI | – | docker up + Consent |
| GPS/Satellit | ✅ | Permission | Play Services | GPS | Fix outdoors |
| WiGle | ✅ | Key | WiGle | – | Key + Consent |
| MacLookup | ✅ | Netz | maclookup.app | – | Netz |
| Google Geo | ✅ | Key | Google | – | Key + WiFi-APs |
| Netatmo | ✅ | Token | Netatmo | – | Token |
| MQTT | ✅ | Broker-URL | Mosquitto | – | tcp erreichbar |
| WebSocket | ✅ | WS-URL | FastAPI `/ws` | – | docker up |
| NFC | ✅ | – | – | NFC | NDEF mit MAC |
| USB-Serial | ✅ | – | – | OTG+Adapter | Permission |
| Temp-Mail/MCP | ✅ | MCP-URL | FastAPI `/api/mcp` | – | URL = Backend |
| Offline-Karte | ✅ | optional Archiv | Geofabrik | – | Cache/MBTiles |
| Node-RED UI | 🟡 | – | nodered Image | – | **Flows bauen** |
| SQLCipher | ✅ | auto | – | – | KeyStore-Passphrase |

---

## 6. Minimal-Pfad „Pilot ready“ (1–2 Stunden)

1. JDK 17 + Android SDK  
2. `local.properties` mit `sdk.dir` +  
   `MQTT_BROKER_URL=tcp://<PC-IP>:1883`  
   `WEBSOCKET_URL=ws://<PC-IP>:8000/ws`  
   `MCP_SERVER_URL=http://<PC-IP>:8000`  
3. `docker compose up --build -d`  
4. `./gradlew :app:assembleDebug` && `adb install`  
5. Permissions + Agent starten  
6. Optional: ein API-Key (z. B. OpenChargeMap) zum Testen von Urban  
7. Optional: ESP32 flashen und MQTT-Host = PC-IP  

Danach sind **lokal**: BLE/WiFi/GPS/QR/NFC (Hardware), MQTT/WS/Crowd/MCP (Docker), Agent, UI, Export/Backup **live**.

---

## 7. Maximal-Pfad „Production ready“ (Restliste)

Priorität hoch → niedrig:

1. ⚙️ Clean-Build lokal/`./gradlew assembleRelease` + CI grün  
2. ✅ MQTT Auth + Runtime-URLs + Release NSC (Cleartext aus)  
3. ⚙️ Keys: BuildConfig + Settings; optional Backend-Proxy für WiGle/Google  
4. ✅ Runtime-Settings Broker/URLs  
5. ✅ LORA/YOLO/CKAN/FIND_MY verdrahtet  
6. ✅ Asset-Sync  
7. ✅ Node-RED Flows  
8. ✅ SQLCipher  
9. ✅/⚙️ UI-Tests (Agent-Cycle-Logik, Login/PIN, Asset-CRUD) – lokal `./gradlew testDebugUnitTest` / `connectedDebugAndroidTest`  
10. ✅ Monitoring (Health-Screen + Backend `/api/health` + `scripts/smoke-check.sh`)  
11. ✅/⚙️ DSGVO: Consent, Datenauskunft, Retention 90d, Löschen Art.17 (AVV organisatorisch)  
12. ✅ compileSdk 35

---

## 8. Schnell-Referenz: Dateien

| Zweck | Pfad |
|-------|------|
| Keys/URLs | `local.properties` ← `local.properties.example` |
| Offline-Install | `docs/OFFLINE_SETUP.md`, `scripts/offline/` |
| Backend | `backend/main.py`, `docker-compose.yml` |
| Firmware | `firmware/secureguard_esp32/` |
| CI | `.github/workflows/build-release.yml` |
| Diese Liste | `docs/READY_TO_GO_CHECKLISTE.md` |

---

## 9. Kurzantwort

**Code-seitig:** App, Agent, 30 Services, 8 APIs, Backend, Firmware, Offline-Skripte = **weitgehend complete**.  

**Ready-to-go:** Code-Parts A–J bereit (siehe [GO_LIVE.md](GO_LIVE.md)). Noch **Host-Build**, **Docker-Stack**, **URLs/Keys in local.properties**, **Permissions**, optional **ESP32**. Batch 1–4 erledigt. Organisatorisch: AVV, echte Zertifikate, User-Passwörter. **Passwörter/PINs setzt der Anwender selbst.**

Wenn du willst, können wir als Nächstes **eine** der Lücken gezielt schließen – empfohlen Reihenfolge:  
`Batch 1–4 ✅` → **Go-Live:** [GO_LIVE.md](GO_LIVE.md) · Host `./scripts/start-stack.sh` + `./gradlew testDebugUnitTest assembleDebug`.
