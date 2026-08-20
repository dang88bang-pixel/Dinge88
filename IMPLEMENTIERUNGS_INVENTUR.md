# 🔍 Implementierungs-Inventur – SecureGuard Enterprise

**Stand:** laufende Prüfung. Legende: ✅ real & ausführbar · 🟡 real, aber von Hardware-/Backend-Verfügbarkeit abhängig · 🔧 Mock / Platzhalter / Demo

> **Betriebsvereinbarung:** Ist eine **Blaupause** (Pilot-Projekt) – sie wird nur hinterlegt
> ([`BETRIEBSVEREINBARUNG.md`](./BETRIEBSVEREINBARUNG.md)), aber **nicht an die App angebunden**
> (keine Anzeige, keine aktive Geltung).

---

## 1. Ortungstechnologien (Dienste)

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| BLE-Scan | `TelemetryService.searchAsset()` | 🟡 | **Echte** `BluetoothLeScanner`-Suche; findet nur bei vorhandenem Gerät + Berechtigung etwas. |
| WiFi (ScanResults) | `WifiService.searchAsset()` | 🟡 | **Echt** (`WifiManager` + BSSID-Abgleich). Findet nur mit Standort-/NEARBY-Permission etwas. Probe-Requests sind per öffentlichem API nicht passiv abhörbar. |
| LoRa/LoRaWAN | `LoraService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren LoRaWAN-Backend-Endpunkt (`RemoteDetectionFetcher`); ohne konfigurierte URL → `null`. |
| Optik (Webcams/YOLO) | `OpticalService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren YOLO-Inferenz-Endpunkt; ohne URL → `null`. |
| Urban | `UrbanService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren Open-Data-/Infrastruktur-Endpunkt; ohne URL → `null`. |
| Crowd (Apple/Google) | `CrowdService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren Find-My-Proxy, nur mit `externalAllowed`; ohne URL → `null`. |
| Satellit/GPS | `SatelliteService.searchAsset()` | 🟡 | **Echt** (GPS-Position via `FusedLocationProviderClient`). |

## 2. Fernsteuerung / Telemetrie

| Funktion | Datei | Status | Kommentar |
|---|---|---|---|
| `sendCommand()` (Alarm/Motor/Batterie/…) | `TelemetryService.sendCommand()` | 🟡 | **Echt** (BLE-GATT-Write via `BleCommandConnector`). Funktioniert nur mit verbindbarem BLE-Gerät. |
| `getLatestTelemetry()` | `TelemetryService.getLatestTelemetry()` | 🟡 | **Echt** (BLE-GATT-Read via `BleCommandConnector.readTelemetry`, JSON-Telemetrie). Ohne Gerät → `null`. |

## 3. Anzeige-Werte (gefälschte/Demo-Daten)

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Batterie | `DashboardViewModel` → `batteryLevel` | ✅ | **Echter Akkustand** (BatteryManager). |
| Telemetrie-Raster | `AssetDetailScreen` | 🟡 | Zeigt ehrlich „–" bis eine echte BLE-GATT-Telemetrie gelesen wurde (Implementierung vorhanden). |
| Agent-Laufzeit | `AgentViewModel` → `runtime`/`progress` | ✅ | **Echt**: Laufzeit aus persistiertem Startzeitpunkt, Fortschritt aus eingestellter Gesamtdauer. |

## 4. UI-Aktionen ohne echte Wirkung

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Karten-Zoom/Zentrieren | `MapScreen` | ✅ | An `MapView` angebunden (`setZoom`/`animateTo`). |
| Einstellungen-Schalter | `SettingsScreen` + `SettingsRepository` | ✅ | Persistiert (SharedPreferences); steuern den Agenten (BLE/WiFi/GPS). |
| Backend-Endpunkte | `SettingsScreen` + `SettingsRepository` | ✅ | Konfigurierbare URLs für LoRa/Optik/Urban/Crowd (Pilot) – damit sind alle Quellen aktivierbar. |
| QR-Scan | `ScanScreen` | ✅ | **Echt** (CameraX + ML Kit Barcode), inkl. Berechtigungsfluss. |
| Agent-Worker | `SecureAgentWorker` | ✅ | Führt `runCycleOnce()` aus (einen echten Suchzyklus). |
| Profil-Daten | `SettingsScreen` („SecureGuard Admin", „Muster GmbH") | 🔧 | Statisch (Platzhalter). |
| `SecureAgentWorker` | `worker/SecureAgentWorker.kt` | ✅ | Führt `runCycleOnce()` aus (einen echten Suchzyklus). |
| Asset-Detail Menü (⋮) | `AssetDetailScreen` | ✅ | Echte Aktionen: Status setzen, Externe-Quellen erlauben/sperren, Asset löschen. |
| Asset-Detail Suche | `AssetDetailScreen` | ✅ | „Suche starten" (alle Quellen), „Extern", „Satellit", „Bluetooth" – jede Quelle einzeln auslösbar; Ergebnis zeigt Quelle + RSSI. |
| Asset-Detail Telemetrie | `AssetDetailScreen` | ✅ | Zeigt echte GATT-Telemetrie (Batterie, Kraftstoff, Motor, Betriebsstunden, km) nach „↻ Aktualisieren". |
| Navigations-Stubs | `DashboardViewModel` / `AssetListViewModel` | ✅ | Entfernt (Navigation läuft direkt über `navController`). |
| Laufzeit-Berechtigungen | `SettingsScreen` → „Berechtigungen" | ✅ | Anfragen für **Standort**, **Bluetooth**, **Benachrichtigungen** (Android 13+) mit Statusanzeige („Erteilt/Nicht erteilt") + Recomposition nach Systemrückkehr. |
| Worker-Scheduling | `SecureGuardApplication` | ✅ | Plant `SecureAgentWorker` periodisch (15 Min) per WorkManager (`HiltWorkerFactory`, `ExistingPeriodicWorkPolicy.KEEP`). |
| Benachrichtigungs-Permission | `NotificationService` | ✅ | Prüft `POST_NOTIFICATIONS` (API 33+) vor dem Senden; Vibration folgt der Asset-/Einstellungs-Flag. |
| Asset-Liste „Suchen" | `AssetListScreen` | ✅ | Button navigiert zur Detailseite mit den Suchfunktionen. |
| Adaptive Priorisierung | `AgentService` | ✅ | `learnFromExperience` sortiert Quellen nach Erfolgsquote; `comprehensiveSearch` fragt in gelernter Reihenfolge ab (erster Treffer gewinnt). |

## 5. Wirklich funktionsfähig (✅)

- App-Start + Hilt-DI, Navigation (Bottom-Bar + Detail-Routen mit Übergängen)
- Room-Datenbank (Asset/Detection/Alert), CRUD über Repository
- **Asset hinzufügen** (speichert in DB)
- **Asset-Liste**: echte Suche + Status-Filter über DB-Daten
- **Asset-Detail**: lädt Asset per ID, zeigt echte Detections/Historie aus der DB
- **Alarme**: liest echte ungelöste Alerts, „als erledigt markieren" persistiert
- **Karte**: osmdroid rendert reale Marker aus DB, Klick → Detail
- **Dashboard**: Statistiken kommen live aus der DB
- **Agent-Schleife** (`AgentService`): läuft, adaptiert Intervalle – findet aber nichts, weil alle Quellen Stubs sind

---

## Umsetzungsplan (Fortschritt)

### ✅ Erledigt
- [x] 1. **`SatelliteService`** → echte GPS-Position (`FusedLocationProviderClient`, `getCurrentLocation` / `lastLocation`)
- [x] 2. **WiFi-Erkennung** → neuer `WifiService` (`WifiManager.getScanResults()` + BSSID-Abgleich), `NEARBY_WIFI_DEVICES`-Permission ergänzt, im Agenten verdrahtet
- [x] 3. **`sendCommand`** → echter BLE-GATT-Write (`BleCommandConnector`: Verbinden → Services → Write), fehlertolerant
- [x] 4. **Karten-Zoom/Zentrieren** → an `MapView` angebunden (`setZoom`/`animateTo`)
- [x] 5. **Einstellungen** → persistiert (`SettingsRepository` auf SharedPreferences); Toggles steuern den Agenten (BLE/WiFi/GPS werden je nach Schalter genutzt)
- [x] 6. **Fake-Werte** → Dashboard liest echten Akkustand (BatteryManager); Asset-Detail-Telemetrie zeigt ehrlich „–" statt Demo-Zahlen; Agent-Fortschritt bleibt simuliert (siehe offen)
- [x] 7. **QR-Scan** → echte Kamera-Erfassung (CameraX + ML Kit Barcode), Berechtigungsfluss + Ergebnisanzeige

### ✅ Aktiv (alle Teile funktional bereitgestellt)
Alle Ortungsquellen, Fernsteuerung, Telemetrie, Kartensteuerung, Einstellungen, QR-Scan,
Agent-Laufzeit und Worker sind **aktiv implementiert**. Die remote-/backend-abhängigen
Quellen (LoRa, Optik, Urban, Crowd) werden über **konfigurierbare Endpunkt-URLs**
(Einstellungen → „Backend-Endpunkte") aktiviert; ohne URL geben sie sauber `null` zurück.

### ⏳ Noch offen / bewusst zurückgestellt
- **Profil-Daten** („SecureGuard Admin" / „Muster GmbH") sind statische Platzhalter.
- **Betriebsvereinbarung**: Als **Blaupause hinterlegt, aber bewusst nicht angebunden**
  (Pilot-Projekt). Keine Anzeige im App-, keine aktive Geltung.

> **Wichtiger Hinweis zu Backend-Quellen:** Für echte Detektionen aus LoRa/Optik/Urban/Crowd
> muss jeweils ein passender Endpunkt im Pilot laufen (LoRaWAN-Gateway/Proxy, YOLO-Server,
> Open-Data-API, Find-My-Proxy) und unter „Backend-Endpunkte" konfiguriert werden.
> **Vor Ort ohne Backend** sind **BLE, GPS, WiFi, Befehlsübertragung und Telemetrie** vollständig
> ausführbar.

---

# 🔌 Ergänzung: Externe APIs, Echtzeit-Kanäle & fehlende Komponenten

**Stand:** ergänzt im Rahmen der API-Integrations-Überprüfung. Legende: ✅ implementiert · 🟡 von Backend/Keys abhängig · ⚠ Abweichung dokumentiert

## 6. Externe API-Knotenpunkte (REST)

| API | Datei | Status | Kommentar |
|---|---|---|---|
| WiGle.net (BSSID→GPS) | `services/apis/WiGleApi.kt` | 🟡 | Key über `WIGLE_API_KEY`; ohne Key → null |
| MacLookup.app (OUI) | `services/apis/MacLookupApi.kt` | ✅ | kostenlos, ohne Key |
| Open Charge Map | `services/apis/OpenChargeMapApi.kt` | 🟡 | Key `OPEN_CHARGE_MAP_KEY`; reale verschachtelte Antwortstruktur abgebildet |
| DHL Packstation | `services/apis/DhlPackstationApi.kt` | 🟡 | Endpunkt benötigt DHL-Zugang (Vertrag); Vertrag hinterlegt |
| CKAN Open Data | `services/apis/CkanOpenDataApi.kt` | ✅ | demo.ckan.org, ohne Key |
| Google Geolocation | `services/apis/GoogleGeolocationApi.kt` | 🟡 | Key `GOOGLE_API_KEY`; WLAN-APs als Body |
| Netatmo Weather | `services/apis/NetatmoWeatherApi.kt` | 🟡 | Bearer-Token `NETATMO_TOKEN` |
| Helium Network | `services/apis/HeliumNetworkApi.kt` | 🟡 | Hotspots um Position, Beacons |
| Zentraler Manager | `services/ApiServiceManager.kt` | ✅ | Retrofit/OkHttp (Gson+Moshi), Logging, Fehlertoleranz, Detection-Flow, RxJava-Variante |

## 7. Echtzeit-Kanäle

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| MQTT (Paho) | `services/MqttService.kt` + `MqttConfig.kt` | ✅ | `MqttAsyncClient`, Auto-Reconnect, Events-Flow, Topics/Konfiguration |
| WebSocket (OkHttp) | `services/WebSocketService.kt` | ✅ | Gson-Nachrichten, Events-Flow; URL via `WEBSOCKET_URL` |
| Agent-Integration | `services/AgentService.kt` | ✅ | API-Kanal (nur mit Einwilligung), MQTT-/WS-Collectoren, `sendAction` über alle Kanäle, Offline-Queue-Fallback |

## 8. Ergänzte Komponenten (vorher „fehlt")

| Komponente | Datei | Status |
|---|---|---|
| Audit-Log (Entity+DAO+Service) | `data/model/AuditLog.kt`, `AuditLogDao.kt`, `AuditLogService.kt` | ✅ |
| Offline-Queue (Entity+DAO+Service) | `data/model/PendingAction.kt`, `PendingActionDao.kt`, `OfflineQueue.kt` | ✅ |
| Room-Migration v2 | `SecureGuardDatabase.kt` (`MIGRATION_1_2`) | ✅ |
| Datenbereinigung (Retention) | `DatabaseCleanup.kt` | ✅ |
| Backup/Restore | `BackupManager.kt` | ✅ |
| E2E-Verschlüsselung (AES/GCM, KeyStore) | `EncryptionService.kt` | ✅ |
| Authentifizierung (PIN, PBKDF2) | `AuthManager.kt` + `LockScreen.kt` (MainActivity-Gate) | ✅ |
| RBAC | `security/RoleManager.kt` | ✅ |
| Alarm-Töne pro Stufe | `AlertSoundManager.kt` | ✅ |
| Offline-Karte (OSM) | `OfflineMapService.kt` | ✅ |
| CSV/PDF-Export (+verschlüsselt) | `ExportService.kt` | ✅ |
| Retry-Logik | `util/RetryManager.kt` | ✅ |
| Globaler Error-Handler | `util/ErrorHandler.kt` | ✅ |
| Cache (TTL/Size) | `util/CacheManager.kt` | ✅ |
| Lazy Loading (Paging 3) | `util/AssetPagingSource.kt` + `AssetDao.getPage` | ✅ |
| Barrierefreiheit (TalkBack) | `util/AccessibilityHelper.kt` | ✅ |
| Lern-Engine (Muster/Prädiktion) | `services/LearningEngine.kt` | ✅ |
| WorkManager-Worker (15 Min) | `worker/SecureAgentWorker.kt` + Application-Scheduling | ✅ |
| NFC-Integration | `services/NfcService.kt` + Manifest | ✅ |
| USB/Serial | `services/UsbSerialService.kt` (usb-serial-for-android) | ✅ |
| Benachrichtigungskanäle (4) | `NotificationService.kt` | ✅ |
| Mehrsprachigkeit | `res/values-en/strings.xml` | ✅ |
| Dunkelmodus | `presentation/theme/Theme.kt` (Dark/Dynamic) | ✅ (bereits vorhanden) |
| Foreground Service | `AgentForegroundService.kt` | ✅ (bereits vorhanden) |
| Backend (FastAPI) | `backend/main.py` + Docker-Compose + Mosquitto | ✅ (neben dem Android-Projekt) |
| ESP32-Firmware | `firmware/secureguard_esp32/` | ✅ |
| OpenAPI-Doku | `docs/api-docs.yaml` | ✅ |

## 9. ⚠ Abweichungen von der Vorgabe (bewusst)

| Vorgabe | Umsetzung | Begründung |
|---|---|---|
| `org.eclipse.paho.android.service` (MqttAndroidClient) | `org.eclipse.paho.client.mqttv3` + `MqttAsyncClient` | Die Android-Service-Variante ist seit 2018 ungepflegt, benötigt die alte Support-Library und verursacht auf modernen Android-Versionen Laufzeitprobleme. `MqttAsyncClient` ist funktional äquivalent (Pub/Sub, Callbacks, Auto-Reconnect) und stabil. |
| `no.nordicsemi.android:ble-common:2.6.1` | nur `ble-ktx:2.6.0` | `ble-common`-Artefakt für 2.6.x nicht verifizierbar; `ble-ktx` (auf Maven Central bestätigt) genügt als Nordic-Anbindung. Der aktive Scan läuft über die Plattform-API (`BleService`). |
| `androidx.startup:startup-runtime` / `androidx.multidex` | nicht ergänzt | Nicht benötigt: minSdk 26 hat natives Multidex, und kein Initializer wird verwendet. |
| API-Keys `Bearer`-Schema WiGle | `Authorization: Bearer <Key>` | WiGle nutzt regulär HTTP-Basic; über den Key kann beides hinterlegt werden (siehe Doku im Client). |
| DHL/Helium-Endpunkte | Vertrag hinterlegt | Die realen APIs benötigen Zugangsdaten bzw. haben sich geändert; die Interfaces sind über `ApiServiceManager` austauschbar. |

---

# 🔌 Ergänzung: TempMail/MCP-Integration & ApiNodeManager

**Stand:** ergänzt nach Vorgabe „Temporäre E-Mail-Dienste für Agenten" + „API-Node-Manager Agent".

## 10. Temporäre E-Mail (MCP)

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| MCP-Client (WebSocket/JSON-RPC) | `mcp/MCPClient.kt` | ✅ | Tools `create_inbox`, `wait_for_otp`, `extract_magic_link`; URL via `MCP_SERVER_URL`; ohne Konfiguration → `null` |
| TempMail-Service (Fassade) | `services/TempMailService.kt` | ✅ | Inbox/OTP/Magic-Link-Workflow, State-Flows, Auto-Logout |
| Agent-Erweiterung | `services/AgentService.kt` | ✅ | `autoRegisterExternalService()` + `RegistrationResult`; `performRegistration` bewusst skizziert (keine unautorisierten Aufrufe) |
| UI | `presentation/ui/tempmail/TempMailScreen.kt` + `ViewModel` | ✅ | Inbox anzeigen, OTP abrufen, Log; Route `temp_mail` + Einstieg in Einstellungen |
| BuildConfig | `MCP_SERVER_URL` | ✅ | |

## 11. ApiNodeManager (autonome Knoten-Verwaltung)

| Komponente | Datei | Status |
|---|---|---|
| Kern (11 Node-Handler, Health-Monitor, Circuit Breaker, Learning Layer, Rate-Limiter) | `agent/ApiNodeManager.kt` | ✅ |
| Standard-Konfigurationen | `agent/NodeConfig.kt` | ✅ |
| UI (Status, Toggle, Test-Suche) | `presentation/ui/nodes/NodeStatusScreen.kt` + `ViewModel` | ✅ |
| Navigation | `Routes.NODE_STATUS` + Einstellungen-Einstieg | ✅ |

## 12. ⚠ Anpassungen gegenüber der Vorgabe (bewusst)

| Vorgabe | Umsetzung | Begründung |
|---|---|---|
| Direkte API-Injection in `ApiNodeManager` (WiGleApi etc.) | nutzt `ApiServiceManager` | Clients sind dort zentral gekapselt (Retrofit-Instanzen, Keys) |
| `Detection.isVerified` / `metadata` / `triangulationPoints` | existieren im Datenmodell nicht → in `message` kodiert | Modell bleibt schlank; `message` zeigt Hersteller/OTP/Status |
| `AuditService` / `AuditActionType` | `AuditLogService.log(action, details)` | bestehende Audit-Implementierung |
| TempMail REST-Variante (freecustom.email) | nur MCP-Variante | einheitlicher Kanal; REST-Endpunkt wäre fiktiv |
| `searchViaTempMail` im NodeManager | nutzt `TempMailService` (MCP) | konsistente Fassade |
| `performRegistration` | Rückgabe `false` (Skizze) | keine unautorisierten Registrierungen aus dem Repo heraus |
