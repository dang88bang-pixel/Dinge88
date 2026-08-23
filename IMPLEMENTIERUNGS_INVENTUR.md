# 🔍 Implementierungs-Inventur – SecureGuard Enterprise

**Stand:** Vollprüfung 2026-08-23 – komplette Aktions-/Interaktionskette, alle
Abhängigkeiten und Anbindungen geprüft. Alle Platzhalter, Mocks, Demo- und
Simulationsanteile wurden **entfernt und durch echte Implementierungen bzw.
ehrliches „nicht gefunden“ (null) ersetzt**.

Legende: ✅ real & ausführbar · 🟡 real, aber von Hardware-/Key-/Backend-Verfügbarkeit abhängig · 🔧 Mock/Platzhalter (**Stand der Prüfung: keiner mehr in Produktionscode**)

---

## 1. Ortungskanäle (Detection Services)

| Kanal | Datei | Status | Echte Kette |
|---|---|---|---|
| BLE | `services/BleService.kt` | ✅/🟡 | `BluetoothLeScanner` mit Hardware-MAC-Filter (2 s, LOW_LATENCY); **kein Simulations-Fallback mehr** – kein Treffer → `null` |
| WiFi | `services/WifiService.kt` | ✅/🟡 | `WifiManager.startScan()` + BSSID-Abgleich gegen `scanResults`, echte RSSI (`level`); `NEARBY_WIFI_DEVICES` (API 33+) geprüft; liefert zusätzlich echte AP-Liste für Google-Geolocation |
| LoRa/LoRaWAN | `services/LoraService.kt` | 🟡 | HTTP-Abfrage `LORA_BACKEND_URL` → `GET /api/detections?mac=&source_type=LORA` (FastAPI-Backend sammelt LoRa-Uplinks via MQTT); ohne URL → `null`. **`DummyLoraClient` entfernt** |
| Optik (YOLO) | `services/OpticalService.kt` | 🟡 | HTTP-POST an `YOLO_SERVER_URL/detect`, Antwort `{found,lat,lng,confidence,camera}`; ohne URL → `null`. **Keine Random-Sichtungen mehr** |
| Urban | `services/UrbanService.kt` | 🟡 | HTTP-POST an `URBAN_SIGHTINGS_URL/sightings`; ohne URL → `null`. **Keine erfundenen Berlin-Knoten mehr** |
| Crowd (Find My) | `services/CrowdService.kt` | 🟡 | HTTP-POST an `FIND_MY_PROXY_URL/locate` mit **SHA-256-Hash** der MAC (kein Klartext verlässt das Gerät); nur mit `externalAllowed` + URL |
| Satellit/GPS | `services/SatelliteService.kt` | ✅/🟡 | `FusedLocationProviderClient.lastLocation` (3 s Timeout); ohne Fix/Permission → `null`. **Random-Schätzwert entfernt** |
| Telemetrie (GATT) | `services/TelemetryService.kt` | ✅/🟡 | **Echte GATT-Implementierung**: connect → discoverServices → Read/Write auf Charakteristik `6BA1B218-…-FD14` (Service `…-FD13`, identisch zur ESP32-Firmware), `readRemoteRssi`, JSON-Parsing, CCC-Deskriptor, API-33+-Pfade, Verbindung wird immer geschlossen. **Keine simulierten Werte (12.456 h / 234.567 km) mehr** |

## 2. Fernsteuerung / Aktionskette

| Aktion | Kette | Status |
|---|---|---|
| Aktion senden (Aktionen-Screen & Asset-Detail) | `ActionsViewModel`/`AssetDetailViewModel` → `AgentService.sendAction()` → 1) MQTT-Publish `secureguard/<MAC>/command` (nur gezählt, wenn `isConnected`) → 2) WebSocket (nur gezählt, wenn Verbindung offen) → 3) BLE-GATT-Write mit Gerätebestätigung → sonst **Offline-Queue (Room, max. 5 Versuche)** + Audit-Log | ✅ |
| LoRa-Befehl | `LoraService.sendCommand()` → `MqttService.sendCommand()` auf `secureguard/+/command` – **genau das Topic, das die ESP32-Firmware abonniert** (ALARM → GPIO2) | ✅/🟡 |
| Offline-Nachlieferung | `AgentService.flushOfflineQueue()` → Erfolg nur bei bestätigter MQTT-Verbindung (vorher wurde immer `true` gemeldet – behoben) | ✅ |
| Registrierung (TempMail) | `AgentService.autoRegisterExternalService()` → MCP-Inbox → **echter HTTP-POST** (`performRegistration`, OkHttp, JSON-Body inkl. E-Mail) → OTP-Polling. Vorher TODO-Stub → behoben | ✅/🟡 |

## 3. Anzeige-Werte (alle echt)

| Stelle | Quelle |
|---|---|
| Dashboard „Batterie“ | `BatteryManager.BATTERY_PROPERTY_CAPACITY` + `ACTION_BATTERY_CHANGED`-Fallback (vorher hartcodiert 87 / falsche Charge/Energy-Counter-Mathe – behoben) |
| Dashboard Statistiken | live aus Room (Assets/Online/Offline/Wartung/Detektionen/Alarme) |
| Agent-Fortschritt | echter Anteil `uptime / konfigurierte Dauer` (1h/6h/24h/1w/Custom); unbegrenzt → „unbegrenzt“ statt Fake-85 % |
| Telemetrie-Raster (Detail) | echte GATT-Werte; ohne Gerät ehrlich „–“ |
| Karte | Startzentrum = Schwerpunkt positionierter Assets, sonst Weltansicht (hartcodiertes Berlin entfernt) |

## 4. UI-/Interaktionsketten (vollständig verdrahtet)

- **Navigation:** alle 12 Routen (`SecureGuardApp`) ↔ Screens; `DashboardScreen(navController)`-Signatur korrigiert (kompilierte zuvor nicht).
- **Einstellungen:** Profil zeigt echte Daten (PIN-Status, App-Version, Gerät); Verbindungsstatus (MQTT/WebSocket/MCP/Agent) live; **Schalter wirken live auf den laufenden Agenten** (`AgentService.updateSettings`, offlineOnly/external/learning ohne Neustart); Dark-Mode steuert das Theme über `MainActivity` (Kette war zuvor unterbrochen).
- **Berechtigungen:** neuer Abschnitt „Berechtigungen“ mit `RequestMultiplePermissions`-Launcher (Standort, Bluetooth, WiFi API 33+, Kamera, Benachrichtigungen) – vorher existierte `missingPermissions()`, war aber nirgends angebunden.
- **Benachrichtigungen:** `NotificationService` prüft jetzt Einstellung „Push-Benachrichtigungen“ **und** `POST_NOTIFICATIONS` (API 33+) vor jedem Senden.
- **Abfrageknoten:** `NodeStatusViewModel.runFullQuery()` nutzt das **erste echte Whitelist-Asset** (echte MAC/Position); ohne Asset klare Meldung (vorher hartcodierte Test-MAC `AA:BB:CC:…:01` + Berlin).
- **Kanalauswahl im Agenten:** GPS/Satellit ist gerätelokal und läuft immer; Crowd/API nur mit Einwilligung (zuvor wurde GPS fälschlich als extern behandelt).
- **Demo-Datenbank-Befüllung entfernt:** `seedDemoDataIfEmpty()` (5 Demo-Assets) komplett gestrichen – die App startet leer und ehrlich.

## 5. Externe APIs (Retrofit/OkHttp, zentral `ApiServiceManager`)

| API | Status | Anmerkung |
|---|---|---|
| WiGle.net | 🟡 | `WIGLE_API_KEY`, ohne Key → null |
| MacLookup.app | ✅ | OUI-Lookup, keyless |
| Open Charge Map (+RxJava-Variante) | 🟡 | `OPEN_CHARGE_MAP_KEY` |
| DHL Packstation | 🟡 | Vertrag sendpunkt hinterlegt (`api.dhl.de`), scheitert ehrlich ohne Zugang |
| CKAN | 🟡 | **Kein `demo.ckan.org` mehr** – eigene Instanz über `OPEN_DATA_URL`; ohne URL inaktiv |
| Google Geolocation | 🟡 | Key nötig; BSSID-Suche ohne erfundene Signalstärke (`signalStrength=null`) |
| Netatmo | 🟡 | `NETATMO_TOKEN` |
| Helium | 🟡 | Hotspots/Beacons um echte Position |
| ApiNodeManager | ✅ | 9 echte Such-Knoten (WiGle/MacLookup/OCM/DHL/GoogleGeo/Netatmo/Helium/MQTT/WebSocket) mit Rate-Limit, Timeout, Circuit-Breaker, Lern-Priorisierung; **TempMail & CKAN aus der Suche entfernt** (keine Asset-Sichtungen); positionsgestützte Knoten laufen nur mit echter Position (Berlin-Fallbacks entfernt) |

## 6. Echtzeit / Backend / Firmware

- **MQTT (Paho `MqttAsyncClient`):** Auto-Reconnect, Subscriptions `secureguard/+/telemetry|alert|status` + Broadcast; Telemetrie/Alert-Events → Detection/Alert in DB + Benachrichtigung.
- **WebSocket (OkHttp):** URL `WEBSOCKET_URL`; neuer echter `isConnected`-Zustand (Open/Closed/Failure).
- **FastAPI-Backend (`backend/main.py`):** SQLite (assets/detections/alerts/commands), MQTT-Publish, WebSocket-Endpoint; `GET /api/detections` jetzt mit `mac`/`source_type`-Filter (LoRa-Kette der App); **Demo-`sleep(2)` entfernt**, UPDATE…ORDER BY…LIMIT durch rowid-Subselect ersetzt (portabler SQLite-Syntax).
- **Mosquitto/Docker-Compose/Node-RED:** realer Stack (`docker compose up --build`).
- **ESP32-Firmware:** BLE-GATT-Server (UUIDs = App-Vertrag), LoRa-Empfang → MQTT, Subscribe `secureguard/+/command`; **statische Demo-Telemetrie (`battery:85`) ersetzt** durch echte Messwerte (WiFi-RSSI, freier Heap, Uptime, Gateway-MAC).

## 7. Konfiguration (BuildConfig, alle aus gradle/local.properties)

`WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`,
`HELIUM_API_KEY`, `MQTT_BROKER_URL` (Default `tcp://10.0.2.2:1883` = Docker-Broker
im Emulator), `WEBSOCKET_URL`, `MCP_SERVER_URL`, **neu: `LORA_BACKEND_URL`,
`YOLO_SERVER_URL`, `URBAN_SIGHTINGS_URL`, `FIND_MY_PROXY_URL`, `OPEN_DATA_URL`**
(siehe `local.properties.example`). **Leere Werte = Kanal ehrlich inaktiv (null), niemals Simulation.**

## 8. Daten & Infrastruktur (unverändert geprüft: real)

Room v2 + Migration, DAOs (Asset/Detection/Alert/AuditLog/PendingAction),
Repository (Single Source of Truth), Retention-Cleanup, Backup/Restore,
AES/GCM-Verschlüsselung (AndroidKeyStore), PIN-Auth (PBKDF2, Auto-Lock),
RBAC (`RoleManager`), Audit-Log, CSV/PDF-Export, Paging, Retry/Cache/ErrorHandler,
Offline-Karte (osmdroid), NFC, USB-Serial, ZXing-QR-Scan (echter Scanner mit
Fallback-Eingabe), WorkManager-Zyklus (15 min), Foreground-Service, CI
(Signing aus Secrets, Debug+Release-Matrix).

## 9. Verbleibende ehrliche Abhängigkeiten (keine Mocks)

Diese Punkte sind **bewusst** ohne Fake-Antworten – sie benötigen externe
Ressourcen: LoRa/Optik/Urban/Crowd-Endpunkte, API-Keys, erreichbares BLE-Gerät,
GPS-Fix, laufender Docker-Stack (Broker/Backend), DHL-Vertragszugang.
**Vor Ort ohne alles** laufen: BLE/WiFi-Suche (echt), GPS, GATT-Telemetrie &
Befehle (mit Gerät), lokale DB/Karte/Alarme/Export/Backup.

---

# 🔁 Erweiterungsrunde: Aufräumung & Vollständigkeits-Ausbau (2026-08-23)

## Repo-Bereinigung
- `stitch_native_android_operative (1).zip` (4,6 MB, nie referenziert) gelöscht
- `.gitignore`: `*.zip`/`*.db`/`data/`/`nodered/`/Python-Artefakte
- `BETRIEBSVEREINBARUNG.md`: war **1 Byte (leerer Platzhalter)** → vollständige Blaupause §1–8
- `CHECKLISTE_ABARBEITUNG.md`/`PERMISSIONS_VALIDATION.md` auf Echtstand zurückgesetzt

## Fehlende Berechtigungen ergänzt (Manifest, alle an echten Codepfaden)
`ACCESS_BACKGROUND_LOCATION` (Worker-Suche im Hintergrund), `FOREGROUND_SERVICE_LOCATION`
+ `FOREGROUND_SERVICE_CONNECTED_DEVICE` (API 34+, FGS-Typen), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
(Anfrage in Einstellungen), `NEARBY_WIFI_DEVICES`; FGS-Typ → `dataSync|location|connectedDevice`.

## Neue Service-Worker/Empfänger (alle aktiv)
| Komponente | Kette |
|---|---|
| `MaintenanceWorker` (täglich) | Retention-Cleanup (DatabaseCleanup) + Offline-Queue-Flush + Systembenachrichtigung |
| `BootCompletedReceiver` | BOOT_COMPLETED/MY_PACKAGE_REPLACED → Worker planen, Agent-FGS wiederaufnehmen (Flag `agent_autostart`), FGS-Restriktions-Fallback |
| ConnectivityWatcher (Application) | NetworkCallback onAvailable → Offline-Queue flushen + MQTT/WS neu verbinden |
| MQTT `Connected`-Event | → automatischer Offline-Queue-Flush im AgentService |

## Tote Komponenten → angeschlossen (vorher 0 Nutzungsstellen)
RoleManager → AuthManager (persistierte Rolle, `hasPermission` bei Aktionen/Löschen,
Rollen-Chips in Einstellungen) · AgentForegroundService → Dashboard-Toggle + Boot ·
ApiNodeManager.detections → Agent-Persistenz · ExportService/BackupManager →
Einstellungen (CSV/verschlüsselt/Backup/Restore + Restore-Anwendung beim Start) ·
DatabaseCleanup → MaintenanceWorker · AlertSoundManager → Agent-Alarme + Alerts-Screen
(Ton/Stopp) · CacheManager → WiGle-Cache · ErrorHandler → Agent-Zyklus · RetryManager →
LoRa-HTTP · AccessibilityHelper → AssetCard-TalkBack · AssetPagingSource → echte
DB-Paginierung der Asset-Liste (DAO-Status-Filter ergänzt, flatMapLatest) ·
UsbSerialService → USB-Diagnose in Einstellungen.

## Erneuter Compile-Bruch behoben
`AssetDetailScreen` (37-Zeilen-Altversion) referenzierte nicht existierende
ViewModel-Funktionen (`getAsset`, `getLatestTelemetry`, Felder `battery/fuel/motor/distance`)
und nahm `navController` nicht an → komplett neu: Kopf, Mehrkanal-Suche mit Ergebnis,
GATT-Telemetrie, Fernaktionen, Detektions-Historie, Verwaltungsmenü
(Wartung/Extern/Suchstatus/Löschen mit RBAC + Bestätigungsdialog).
