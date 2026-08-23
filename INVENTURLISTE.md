# 📋 SecureGuard Enterprise – Inventurliste

**Stand:** 2026-08-23 · **Basis:** Branch `arena/01a02a7b-dinge88` (Commit `67ee3f5`) · CI-Status: ✅ grün (APKs gebaut)

**Legende:** ✅ real & live verifiziert · 🟡 real implementiert, benötigt Hardware/Keys/Netz (fail-soft) · 🔧 bewusste Demo/Blaupause (opt-in)

---

## 1. Aktions- & Interaktionsketten (live verifiziert: 19/19 ✓)

| # | Kette | Verlauf | Status |
|---|---|---|---|
| K1 | **Asset-Lebenszyklus** | REST: Asset anlegen → Liste → Detection erfassen → DB persistiert → Stats konsistent | ✅ |
| K2 | **Gateway-Telemetrie** | MQTT-Publish `secureguard/<mac>/telemetry` → Backend-Ingestion → DB-Eintrag → WS-Broadcast an alle Clients | ✅ |
| K3 | **Alarm-Kette** | WS-Command → ACK `delivered` → MQTT-Downlink auf `secureguard/<mac>/command` (Gateway empfängt) → Command-Status `delivered` in DB | ✅ |
| K4 | **Alert-Kette** | MQTT-Alert `secureguard/<mac>/alert` → Ingestion → DB (unresolved) → WS-Broadcast → REST-Auflösung | ✅ |
| K5 | **WS-Protokoll** | Echo für unbekannte Typen · `error` bei ungültigem JSON | ✅ |
| K6 | **Multi-Client** | Ein Ereignis → Broadcast an alle verbundenen WS-Clients gleichzeitig | ✅ |
| K7 | **Broker-Verteilung** | Dritt-Publisher → Broadcast-Topic `secureguard/broadcast` → alle Subscriber | ✅ |

## 2. App-Funktionen & -Ketten (Code-Verdrahtung verifiziert)

| Funktion | Kette | Status |
|---|---|---|
| **Agent-Suche** | `AgentService.comprehensiveSearch` → 8 Kanäle parallel (BLE/WiFi/LoRa/Optik/Urban/Crowd/Satellit/Telemetrie) + WiGle-API → bester Treffer (RSSI) → DB persistiert → Asset-Status ONLINE / Offline-Markierung | ✅ |
| **Aktionen senden** | `sendAction` → MQTT → WebSocket → BLE-GATT-Write → sonst Offline-Queue (Retries via `flushOfflineQueue`) | ✅ |
| **Hintergrund-Agent** | Application → WorkManager (15 min) → `SecureAgentWorker` → `runCycle` + Foreground-Service + Benachrichtigungen | ✅ |
| **Lern-Engine** | Jede Suche → `learningEngine.learn` → adaptives Intervall (`getOptimalInterval`) + Quellen-Priorisierung | ✅ |
| **Einstellungen** | SettingsScreen → ViewModel → `RuntimeSettings` (SharedPreferences) → Kanäle lesen live Demo-Modus/Endpunkte | ✅ |
| **Demo-Modus** | Toggle → `DemoDataManager.seed()/clear()` → alle Sim gekennzeichnet „(Demo)" | 🔧 opt-in |
| **QR-Scan** | ZXing Embedded (echte Kamera) → MAC-Erfassung → manueller Fallback | ✅ |
| **Asset-Verwaltung** | Add/List/Detail (Room, CRUD, Suche, Filter, Paging) | ✅ |
| **Auth/PIN** | `LockScreen.onUnlock` → `AuthManager` (PBKDF2-Salted-Hash, 5 Versuche, Auto-Lock) → MainActivity-Gate | ✅ |
| **Verschlüsselung** | AES/GCM + Android-KeyStore (`EncryptionService`) | ✅ |
| **Audit-Log** | Wer-was-wann (`AuditLogService`, DB) | ✅ |
| **RBAC** | `RoleManager` | ✅ |
| **Export** | CSV/PDF (`ExportService`, optional verschlüsselt) | ✅ |
| **Backup/Restore** | `BackupManager` | ✅ |
| **DB-Hygiene** | Retention/Cleanup (`DatabaseCleanup`) | ✅ |
| **Karte** | osmdroid + Offline-Kacheln/MBTiles (`OfflineMapService`) | ✅ |
| **NFC** | NdefRecord-Lesen (`NfcService`) | ✅ |
| **USB/Serial** | usb-serial-for-android (`UsbSerialService`) | 🟡 Hardware |
| **Alert-Sounds** | `ToneGenerator`-Töne pro Stufe + Vibration | ✅ |
| **TempMail/MCP** | JSON-RPC über WebSocket (`MCPClient`) → `TempMailService` → `autoRegisterExternalService` → echter HTTP-POST mit Audit | 🟡 `MCP_SERVER_URL` |
| **Abfrageknoten** | `ApiNodeManager` (11 Knoten, Health, Circuit-Breaker, Rate-Limit) → `ApiServiceManager` | ✅ |

## 3. Detektions-Kanäle (App)

| Kanal | Implementierung | Status |
|---|---|---|
| BLE | Echter `BluetoothLeScanner` (2 s, MAC-Filter); kein Treffer → `null` | ✅/🔧 |
| WiFi | `startScan` + BSSID-Abgleich, echte RSSI | ✅/🔧 |
| LoRa | `HttpLoraClient` auf konfigurierbaren Endpunkt (GET Gateways, POST Downlink) | 🟡/🔧 |
| Optik | POST an Inferenz-Endpunkt (YOLO-Server) | 🟡/🔧 |
| Urban | GET an Infrastruktur-/Open-Data-Endpunkt | 🟡/🔧 |
| Crowd | GET an Find-My-Proxy (nur `externalAllowed`) | 🟡/🔧 |
| Satellit | Echtes GPS via `FusedLocationProviderClient` | ✅/🔧 |
| Telemetrie/GATT | `BleCommandConnector` (Read + Write-with-Response, Firmware-UUIDs) | 🟡 Hardware/🔧 |

🔧 = im Demo-Modus simuliert (explizit, „(Demo)"-markiert); sonst `null` ohne Fake.

## 4. Externe API-Anbindungen

| API | Zweck | Status |
|---|---|---|
| WiGle.net | BSSID→GPS | 🟡 `WIGLE_API_KEY` |
| MacLookup.app | MAC→Hersteller | 🟡 kostenlos |
| Open Charge Map | Ladesäulen | 🟡 `OPEN_CHARGE_MAP_KEY` |
| DHL Location Finder | Paketstationen (echte API `api.dhl.com`) | 🟡 `DHL_API_KEY` |
| CKAN govdata.de | Open Data (echtes Portal) | 🟡 Netz |
| Google Geolocation | WLAN→Position | 🟡 `GOOGLE_API_KEY` |
| Netatmo | Wetterstationen | 🟡 `NETATMO_TOKEN` |
| Helium (Mirror) | LoRaWAN-Hotspots | 🟡 `HELIUM_API_BASE_URL` |

*Sandbox-Hinweis: externe Domains sind aus der Sandbox blockiert – Wiring ist fail-soft verifiziert (Key fehlt/Fehler → leer, kein Fake).*

## 5. Backend (FastAPI) – live bereitgestellt

| Funktion | Endpoint | Status |
|---|---|---|
| Health | `GET /api/health` (inkl. MQTT-Status) | ✅ |
| Assets | `GET/POST /api/assets` | ✅ |
| Detections | `GET/POST /api/detections` (+ WS-Broadcast) | ✅ |
| Alerts | `GET/POST /api/alerts` (+ WS-Broadcast) | ✅ |
| Aktionen | `POST /api/actions/execute` → MQTT → Status + Broadcast | ✅ |
| Commands | `GET /api/commands` (Tracking REST **und** WS) | ✅ |
| Stats | `GET /api/stats` | ✅ |
| WebSocket | `/ws` (Broadcast, Ack, Echo, Fehler) | ✅ |
| MQTT-Ingestion | `secureguard/+/telemetry`, `secureguard/+/alert` → DB + WS | ✅ |

## 6. Infrastruktur (live bereitgestellt)

| Part | Port | Status |
|---|---|---|
| MQTT-Broker (aedes) | 1883 (TCP) · 9001 (WS) | ✅ aktiv |
| FastAPI-Backend | 8000 | ✅ aktiv (MQTT verbunden) |
| Node-RED (Live-Flow) | 1880 | ✅ aktiv (Broker verbunden) |
| CI (GitHub Actions) | – | ✅ Release-APK 16,25 MB + Debug-APK 23,61 MB |
| PR #10 | – | ✅ mergebar |

## 7. Bewusste Demos/Blaupausen (keine versteckten Mocks)

1. **Demo-Modus** – opt-in, alle Ergebnisse „(Demo)"-markiert, Demo-Assets manuell ladbar
2. **Betriebsvereinbarung** – Blaupause, nicht angebunden
3. **Firmware-Template** – Beispiel-Zugangsdaten pro Gateway zu konfigurieren
4. **Release-Job** – läuft erst bei Version-Tags (`v*`)

---
*Verifikation: `backend/e2e_test.py` + `verify_chains.py` (K1–K7, 19/19 grün) · Code-Audit: keine ungeschützten Simulationen.*

---

## 8. Service-Worker & Bereitstellungs-Tools (Stand 2026-08-23, 2. Erweiterung)

| Komponente | Datei | Funktion | Status |
|---|---|---|---|
| **Gateway-Worker** | `tools/gateway_worker.py` | Echter Dienst-Akteur am Broker (Firmware-Vertrag): registriert Asset per REST, sendet zyklisch Telemetrie (RSSI/Akku/Position), führt ALARM/LIGHT/PING-Befehle aus, sendet LOW_BATTERY-Alerts | ✅ live aktiv |
| **Ein-Befehl-Stack** | `tools/start-stack.sh` | Stellt hintereinander bereit: Broker → Backend → Gateway-Worker → Node-RED (idempotent, mit Statusprüfung) | ✅ |
| **Broker (Sandbox)** | `tools/broker.js` | aedes-MQTT-Broker (1883 TCP / 9001 WS), Mosquitto-äquivalent | ✅ live aktiv |
| **Ketten-Verifikation** | `tools/verify_chains.py` | K1–K7 (19 Checks) | ✅ 19/19 |
| **Netzwerk-Matrix** | `tools/verify_network.py` | Alle Dienste + 6 Anbindungen (14 Checks) | ✅ 14/14 |
| **E2E-Test** | `tools/e2e_test.py` | MQTT→Ingestion→WS→Command→DB | ✅ |

### Live-Anbindungsmatrix (zuletzt 14/14 grün)
```
Gateway-Worker ──MQTT──▶ Broker ◀──MQTT── Backend (FastAPI, :8000)
     ▲                     │                    │  ▲
     └──── Commands ───────┘              REST  │  ├──▶ SQLite (DB)
  (ALARM/LIGHT/PING)                  (Assets,  │  │
                                       Detections,▼
Node-RED (:1880) ──MQTT──▶ Broker    Alerts, …)  WebSocket
                                                      │
                                            Broadcast an alle Clients
```

---

## 9. Benutzeroberfläche (Web-Dashboard) – vollständig integriert

| Element | Bindung | Status |
|---|---|---|
| **Live-Dashboard** `frontend/index.html` | Vom Backend unter `http://<host>:8000/` ausgeliefert (Static-Mount, API/`/docs` behalten Vorrang) | ✅ live |
| Status-Pills (Backend/MQTT/WS/Worker/Node-RED) | `GET /api/health` (5 s Polling) + WS-Zustand + Worker-Guard (35 s) | ✅ |
| Stats-Karten (Assets/Detektionen/Alerts/Befehle/Zugestellt/Uptime) | `GET /api/stats` + Live-Refresh bei jedem WS-Event | ✅ |
| Asset-Tabelle + Ping-Button | `GET/POST /api/assets` + `sendCmd(MAC,'PING')` | ✅ |
| Asset-Registrierungsformular | `POST /api/assets` (Validierung MAC-Format) | ✅ |
| Detektions-Historie | `GET /api/detections` + Live-Append via WS-Broadcast | ✅ |
| **Live-Ereignis-Feed** | WebSocket `/ws` (detection/alert/alert_resolved/command_status/ack), Auto-Reconnect | ✅ |
| Befehls-Formular (ALARM/LIGHT/PING) | WS-Command → Backend → MQTT → Gateway-Worker; ACK + Status im Feed | ✅ |
| Alert-Liste + „✔ Erledigt"-Button | `PATCH /api/alerts/{id}/resolve` (neu) + Broadcast an alle Clients | ✅ |
| „Erledigte entfernen" | `DELETE /api/alerts` (neu, bereinigt aufgelöste) | ✅ |
| Befehlshistorie | `GET /api/commands` (Status delivered/failed) | ✅ |

**Neue Backend-Endpunkte für die UI:** `PATCH /api/alerts/{id}/resolve` · `DELETE /api/alerts`
**UI-Interaktionsketten:** 10/10 verifiziert (Registrierung, Live-Feed, Befehl+ACK, Alert+Resolve+Broadcast, Historien, Stats)
