# 🔍 Implementierungs-Inventur – SecureGuard Enterprise

**Stand:** Mock-/Demo-Audit abgearbeitet (alle Simulationen ersetzt bzw. hinter expliziten Demo-Modus gestellt). Legende: ✅ real & ausführbar · 🟡 real, aber von Hardware-/Backend-Verfügbarkeit abhängig · 🔧 Mock / Platzhalter / Demo

> **Betriebsvereinbarung:** Ist eine **Blaupause** (Pilot-Projekt) – sie wird nur hinterlegt
> ([`BETRIEBSVEREINBARUNG.md`](./BETRIEBSVEREINBARUNG.md)), aber **nicht an die App angebunden**
> (keine Anzeige, keine aktive Geltung).

> **Demo-Modus (neu):** Alle Detektions-Kanäle arbeiten standardmäßig mit echten Quellen
> (Hardware bzw. konfigurierbare Endpunkte). **Simulation nur noch, wenn der Demo-Modus in
> den Einstellungen explizit aktiviert wird** (`RuntimeSettings.demoMode`); simulierte
> Ergebnisse sind mit „(Demo)" gekennzeichnet. Ohne Demo-Modus melden nicht erreichbare
> Quellen ehrlich „nicht gefunden" (`null`).

---

## 1. Ortungstechnologien (Dienste)

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| BLE-Scan | `BleService.searchAsset()` | ✅/🔧 | **Echte** `BluetoothLeScanner`-Suche (2 s, MAC-Filter). Ohne Treffer → `null`; Simulation nur im Demo-Modus (`ble-sim (Demo)`). |
| WiFi (ScanResults) | `WifiService.searchAsset()` | ✅/🔧 | **Echt**: `startScan()` + `scanResults`, BSSID-Abgleich mit Asset-MAC, echte RSSI. Ohne Treffer → `null`; Simulation nur im Demo-Modus. |
| LoRa/LoRaWAN | `LoraService.searchAsset()` | 🟡/🔧 | **Echt** über konfigurierbaren LoRaWAN-Endpunkt (`HttpLoraClient`, Einstellungen → Backend-Endpunkte); Downlink via `POST <url>/downlink`. Ohne URL → `null`; `DummyLoraClient` nur im Demo-Modus. |
| Optik (Webcams/YOLO) | `OpticalService.searchAsset()` | 🟡/🔧 | **Echt** über konfigurierbaren Inferenz-Endpunkt (`POST`, JSON-Vertrag im KDoc); ohne URL → `null`; Simulation nur im Demo-Modus. |
| Urban | `UrbanService.searchAsset()` | 🟡/🔧 | **Echt** über konfigurierbaren Infrastruktur-/Open-Data-Endpunkt (`GET ?mac=`); ohne URL → `null`; Simulation nur im Demo-Modus. |
| Crowd (Apple/Google) | `CrowdService.searchAsset()` | 🟡/🔧 | **Echt** über konfigurierbaren Find-My-Proxy (`GET ?mac=`), nur mit `externalAllowed`; ohne URL → `null`; Simulation nur im Demo-Modus. |
| Satellit/GPS | `SatelliteService.searchAsset()` | ✅/🔧 | **Echt** (GPS-Position via `FusedLocationProviderClient` als Geräte-Referenzpunkt); ohne Fix → `null`; Schätzung nur im Demo-Modus. |

## 2. Fernsteuerung / Telemetrie

| Funktion | Datei | Status | Kommentar |
|---|---|---|---|
| `sendCommand()` (Alarm/Motor/Batterie/…) | `TelemetryService.sendCommand()` | 🟡/🔧 | **Echt**: BLE-GATT-Write-with-Response via `BleCommandConnector` (UUIDs wie ESP32-Firmware). Erfolg nur bei Gerätebestätigung; Simulation nur im Demo-Modus. |
| `getLatestTelemetry()` / `fetchTelemetry()` | `TelemetryService` | 🟡/🔧 | **Echt**: BLE-GATT-Read, JSON-Vertrag der Firmware (`battery`, `rssi`, optional `fuel`, `hours`, `km`, `lat`, `lng`); ohne Gerät → `null`; Simulation nur im Demo-Modus. |

## 3. Anzeige-Werte (keine gefälschten Daten mehr)

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Batterie | `DashboardViewModel` → `batteryLevel` | ✅ | **Echter Akkustand** (`BatteryManager.BATTERY_PROPERTY_CAPACITY`, Fallback `ACTION_BATTERY_CHANGED`); nicht auslesbar → „–" statt Fake. |
| Telemetrie-Raster | `AssetDetailScreen` | ✅ | Zeigt ehrlich „–" bis eine echte BLE-GATT-Telemetrie gelesen wurde. |
| Agent-Laufzeit/-Fortschritt | `AgentViewModel` → `runtime`/`progress` | ✅ | **Echt**: Laufzeit aus `AgentStatus.uptimeMillis`; Fortschritt = verstrichene/geplante Laufzeit (`durationHours`) bzw. Zyklusfortschritt; Agent stoppt automatisch nach Ablauf. |
| Dashboard-Statistiken | `DashboardViewModel` | ✅ | Live aus der Room-DB. **Hinweis:** Ohne Demo-Modus startet die DB leer (kein Auto-Seed mehr); Demo-Assets nur noch explizit über Einstellungen. |

## 4. UI-Aktionen ohne echte Wirkung

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Karten-Zoom/Zentrieren | `MapScreen` | ✅ | An `MapView` angebunden (`setZoom`/`animateTo`). |
| Einstellungen-Schalter | `SettingsScreen` + `SettingsRepository` | ✅ | Persistiert (SharedPreferences); steuern den Agenten (BLE/WiFi/GPS). |
| Backend-Endpunkte | `SettingsScreen` + `SettingsRepository` | ✅ | Konfigurierbare URLs für LoRa/Optik/Urban/Crowd (Pilot) – damit sind alle Quellen aktivierbar. |
| QR-Scan | `ScanScreen` | ✅ | **Echt** (CameraX + ML Kit Barcode), inkl. Berechtigungsfluss. |
| Agent-Worker | `SecureAgentWorker` | ✅ | Führt `runCycleOnce()` aus (einen echten Suchzyklus). |
| Profil-Daten | `SettingsScreen` | ✅ | Editierbar und lokal persistiert (`profile_name`/`profile_org`, Standard „Wache Mitte"/„SecureGuard Enterprise"). |
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
- **Agent-Schleife** (`AgentService`): läuft, adaptiert Intervalle; findet Assets über die
  echten lokalen Kanäle (BLE, WiFi, GPS) bzw. über konfigurierte Backend-Endpunkte.
  Ohne Demo-Modus und ohne erreichbare Quellen wird ehrlich „nicht gefunden" gemeldet.

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
- **Betriebsvereinbarung**: Als **Blaupause hinterlegt, aber bewusst nicht angebunden**
  (Pilot-Projekt). Keine Anzeige im App-, keine aktive Geltung.
- **Firmware-Zugangsdaten**: `secureguard_esp32.ino` enthält Beispiel-WiFi/MQTT-Werte
  (Template) – müssen pro Gateway konfiguriert werden.

> **Wichtiger Hinweis zu Backend-Quellen:** Für echte Detektionen aus LoRa/Optik/Urban/Crowd
> muss jeweils ein passender Endpunkt im Pilot laufen (LoRaWAN-Gateway/Proxy, YOLO-Server,
> Open-Data-API, Find-My-Proxy) und unter „Backend-Endpunkte" konfiguriert werden.
> **Vor Ort ohne Backend** sind **BLE, GPS, WiFi, Befehlsübertragung und Telemetrie** vollständig
> ausführbar. Der **Demo-Modus** (Einstellungen) schaltet alle Kanäle auf Simulation und lädt
> 5 Beispiel-Assets – immer gekennzeichnet mit „(Demo)".

---

# 🔌 Ergänzung: Externe APIs, Echtzeit-Kanäle & fehlende Komponenten

**Stand:** ergänzt im Rahmen der API-Integrations-Überprüfung. Legende: ✅ implementiert · 🟡 von Backend/Keys abhängig · ⚠ Abweichung dokumentiert

## 6. Externe API-Knotenpunkte (REST)

| API | Datei | Status | Kommentar |
|---|---|---|---|
| WiGle.net (BSSID→GPS) | `services/apis/WiGleApi.kt` | 🟡 | Key über `WIGLE_API_KEY`; ohne Key → null |
| MacLookup.app (OUI) | `services/apis/MacLookupApi.kt` | ✅ | kostenlos, ohne Key |
| Open Charge Map | `services/apis/OpenChargeMapApi.kt` | 🟡 | Key `OPEN_CHARGE_MAP_KEY`; reale verschachtelte Antwortstruktur abgebildet |
| DHL Packstation | `services/apis/DhlPackstationApi.kt` | 🟡 | **Echte Location-Finder-API** (`api.dhl.com/location-finder/v1/findLocations`, Header `DHL-API-Key`, Key über `DHL_API_KEY`); ohne Key → leere Liste |
| CKAN Open Data | `services/apis/CkanOpenDataApi.kt` | ✅ | **echtes Portal** `www.govdata.de/ckan` (Standard, über `CKAN_BASE_URL` überschreibbar) – kein Demo-Server mehr |
| Google Geolocation | `services/apis/GoogleGeolocationApi.kt` | 🟡 | Key `GOOGLE_API_KEY`; WLAN-APs als Body |
| Netatmo Weather | `services/apis/NetatmoWeatherApi.kt` | 🟡 | Bearer-Token `NETATMO_TOKEN` |
| Helium Network | `services/apis/HeliumNetworkApi.kt` | 🟡 | Basis konfigurierbar (`HELIUM_API_BASE_URL`, Standard Community-Mirror `helium-api.stakejoy.com`, da alte `api.helium.io` v1 abgeschaltet) |
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
| `performRegistration` | **Echt implementiert**: HTTP-POST mit JSON-Payload (`email` + Registrierungsfelder), Erfolg = HTTP 2xx, Ergebnis im Audit-Log (`REGISTER_HTTP`/`REGISTER_HTTP_ERROR`) | keine unautorisierten Registrierungen aus dem Repo heraus – Aufrufer übergibt Ziel-URL + Daten bewusst |

---

# 🛠️ Ergänzung: Finalisierung & Honeywell CT45P XON (Android 11)

**Stand:** Projekt fertiggestellt; Build grün (Debug + Release).

## 13. Abhängigkeiten aktualisiert (2024-12-Stand)

| Komponente | vorher | nachher |
|---|---|---|
| AGP | 8.5.2 | 8.7.3 |
| Kotlin | 2.0.20 | 2.0.21 |
| compileSdk / targetSdk | 34 | 35 |
| core-ktx | 1.13.1 | 1.15.0 |
| lifecycle | 2.8.6 | 2.8.7 |
| activity-compose | 1.9.2 | 1.9.3 |
| Compose BOM | 2024.09.02 | 2024.12.01 |
| navigation | 2.8.1 | 2.8.5 |
| coroutines | 1.8.1 | 1.9.0 |

## 14. Honeywell CT45P XON (Android 11, API 30)

| Maßnahme | Datei | Status |
|---|---|---|
| Klartext-Netzwerk für MQTT tcp:// (Android 9+ blockiert sonst) | `AndroidManifest.xml` (`usesCleartextTraffic`) | ✅ |
| Geräte-Erkennung + Android-11-Kompatibilitäts-Helfer | `config/CT45PConfig.kt` | ✅ |
| Geräte-Log beim App-Start (Hersteller/Modell/API) | `SecureGuardApplication.kt` | ✅ |
| BLE: Standort-Permission auf API ≤ 30 | `BleService` (bereits vorhanden) | ✅ |
| WiFi: Standort-Permission auf Android 11 | `WifiService` (bereits vorhanden) | ✅ |
| POST_NOTIFICATIONS nur ab API 33 | `NotificationService` + `CT45PConfig.needsNotificationPermission` | ✅ |
| Foreground-Service auf API 30 (2-arg startForeground) | `AgentForegroundService` | ✅ |
| USB-Host (FTDI/CP210x) | `UsbSerialService` | ✅ |

**Hinweis:** Mit `targetSdk 35` läuft die App auf Android 11 uneingeschränkt;
Android-11-spezifische Regeln (Klartext, Scoped Storage, Hintergrund) sind
berücksichtigt. Der 2D-Imager des CT45P arbeitet als HID-Keyboard; der
ZXing-Kamera-Scan bleibt zusätzlich nutzbar.

---

# 🧹 Audit-Runde: Mock-/Demo-Teile ersetzt (2026-08)

Alle in der Prüfung gefundenen Mock-/Demo-/Simulations-Teile wurden ergänzt bzw.
hinter einen **expliziten Demo-Modus** gestellt. Überblick:

## Neue Komponenten
| Komponente | Datei | Zweck |
|---|---|---|
| Laufzeit-Einstellungen | `services/RuntimeSettings.kt` | `demoMode` (Standard AUS) + Endpunkt-URLs für LoRa/Optik/Urban/Crowd |
| HTTP-Client für Remote-Kanäle | `services/RemoteEndpointClient.kt` | GET/POST-JSON, fehlertolerant (null statt Fake) |
| Echte GATT-Verbindung | `services/BleCommandConnector.kt` | BLE-Read/Write mit den UUIDs der ESP32-Firmware (Write-with-Response) |
| Demo-Daten-Verwaltung | `services/DemoDataManager.kt` | Explizites Laden/Entfernen der 5 Beispiel-Assets (kein Auto-Seed mehr) |

## Ersetzte Simulationen
| Vorher | Nachher |
|---|---|
| `DummyLoraClient` immer aktiv | `HttpLoraClient` (konfigurierbarer Endpunkt, JSON-Vertrag); Dummy nur im Demo-Modus |
| `LoraService.sendCommand`: `Random > 0.5` | Echter Downlink `POST <url>/downlink` (Erfolg = HTTP 2xx) |
| `OpticalService`: Zufallstreffer | `POST` an Inferenz-Endpunkt (`found:false` = nichts); Zufall nur im Demo-Modus |
| `CrowdService`: Zufallstreffer | `GET <url>?mac=` am Find-My-Proxy; Zufall nur im Demo-Modus |
| `UrbanService`: feste Fake-Knoten | `GET <url>?mac=` an Infrastruktur-API; Beispiel-Knoten nur im Demo-Modus |
| `WifiService`: Scan-Ergebnis verworfen, immer simuliert | Echter BSSID-Abgleich mit gemessener RSSI; Simulation nur im Demo-Modus |
| `BleService`: immer Ersatztreffer (`ble-sim`) | `null` ohne Treffer; Simulation nur im Demo-Modus |
| `SatelliteService`: Zufallsposition ohne Fix | `null` ohne Fix; Schätzung nur im Demo-Modus |
| `TelemetryService`: Telemetrie/Zustellung komplett simuliert | Echtes GATT-Read/-Write (`BleCommandConnector`); Simulation nur im Demo-Modus |
| `SecureGuardApplication`: Auto-Demo-Seed | Entfernt – Demo-Daten nur explizit (Einstellungen) |
| `DashboardViewModel`: Akku fest 87 % | Echter Akkustand (`BatteryManager`), „–" falls nicht auslesbar |
| `AgentViewModel`: Fortschritt fest 85 % | Echter Fortschritt (Laufzeit/Gesamtdauer bzw. Zyklus), Agent stoppt nach Ablauf (`durationHours`) |
| `NodeStatusViewModel`: Demo-MAC + Fixkoordinaten | Erstes echtes Asset aus der DB; ohne Asset Hinweistext |
| `AgentService.performRegistration`: immer `false` | Echter HTTP-POST (JSON-Payload, Erfolg = 2xx, Audit-Log) |
| CKAN `demo.ckan.org` | Echtes Portal `www.govdata.de/ckan` (via `CKAN_BASE_URL` überschreibbar) |
| DHL fiktiver Vertrag | Echte Location-Finder-API (`api.dhl.com/location-finder/v1`), Key `DHL_API_KEY` |
| Helium tote `api.helium.io` | Basis konfigurierbar (`HELIUM_API_BASE_URL`, Standard Community-Mirror) |
| Backend: `asyncio.sleep(2)` „Demo-Verarbeitung" | Entfernt; Aktionen senden echten Status-Broadcast |
| Backend-WS: nur Echo/Ack | Echter Broadcast von Detektionen/Alerts/Command-Status + MQTT-Ingestion (`secureguard/+/telemetry`, `secureguard/+/alert`) an alle Clients |
| Profil statisch („Wache Mitte") | Editierbar + lokal persistiert |

**Verbleibende, dokumentierte Demos/Blaupausen (bewusst):**
- Demo-Modus der App (explizit zuschaltbar, alle Ergebnisse „(Demo)"-markiert)
- Betriebsvereinbarung als Blaupause (nicht angebunden)
- Firmware-Template mit Beispiel-Zugangsdaten (pro Gateway zu konfigurieren)
