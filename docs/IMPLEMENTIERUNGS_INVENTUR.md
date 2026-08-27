# IMPLEMENTIERUNGS_INVENTUR

**Stand:** 2026-08-27 · Branch `arena/01a0412d-dinge88`

Diese Datei dokumentiert bewusste Implementierungs-Abweichungen der
SecureGuard-App von der ursprünglichen Bibliotheks-/Architekturplanung. Sie ist
an zwei Stellen im Build referenziert (`app/build.gradle.kts` → Paho,
BleService).

## 1. Abweichungen (bewusst so implementiert)

| Thema | Planung | Implementierung | Begründung |
|---|---|---|---|
| MQTT | `org.eclipse.paho.android.service` (Android-Service-Wrapper) | **`org.eclipse.paho.client.mqttv3`** (`MqttAsyncClient`) direkt, Wrapper in `MqttService` | Der Android-Service ist seit Jahren unmaintained und funktioniert ab Android 8 (Background-Service-Limits) nicht mehr zuverlässig. Der Java-Client läuft in eigener Coroutine/Singleton-Verwaltung inkl. `automaticReconnect`. |
| BLE-Scan | Nordic `no.nordicsemi.android:ble-ktx` | **Plattform-API** (`BluetoothLeScanner` + `ScanFilter`) in `BleService.kt` | Nordic-Bibliothek ist ein GATT-Client-Comfort-Layer; der reine MAC-Scan braucht sie nicht. `ble-ktx` wurde daher in der Abhängigkeitspflege **entfernt** (unkenutzte Dependency, siehe FEHLER_MANGEL_LISTE F-25). |
| Permissions | Accompanist `accompanist-permissions` | Eigenes, dünnes Util: `presentation/ui/common/Permissions.kt` | Weniger Abhängigkeiten; Versionierungsdruck von Accompanist (Compose-gekoppelt) vermeiden. Accompanist wurde **entfernt** (F-25). |
| DB | Room + Klartext | **Room + SQLCipher** (`net.zetetic:sqlcipher-android`) + automatische Migration plain→encrypted (`SqlCipherHelper`) | At-rest-Verschlüsselung; Key umgeht den AndroidKeyStore (`DatabaseKeyManager`, StrongBox-fähig mit Fallback). |
| QR-Scan | ZXing Core | `zxing-android-embedded` (`journeyapps`) | Embedded-View + Callback-Fertigkeit; zxing `core` wird transitiv geliefert, ein eigener Alias war überflüssig und wurde entfernt. |
| JSON | – | **Gson + Moshi parallel** (Gson für App-DTOs/Sync, Moshi für API-DTOs) | Historisch gewachsen; bekannter Konsolidierungsgrad (siehe FEHLER_MANGEL_LISTE F-27, offen). |
| Room-Compiler | KSP | **kapt** (KSP-Alias aus TOML entfernt, F-26) | KSP-Alias war deklariert, aber nie angewendet; kapt funktioniert stabil mit Kotlin 2.0.21 + AGP 8.7.3. Umstieg auf KSP später möglich. |

## 2. Nach dem Audit (2026-08-27) veränderte Schnittstellen

| Bereich | Änderung |
|---|---|
| `MqttService.publish/sendCommand` | liefern jetzt `Boolean` (echtes Publish-Ergebnis) statt `Unit` |
| `WebSocketService.sendMessage/sendCommand` | liefern jetzt `Boolean` (`WebSocket.send`-Ergebnis) |
| `AgentService.sendAction/flushOfflineQueue` | nutzen die Ergebnisse → Offline-Queue greift wirklich, wenn nichts zugestellt wurde |
| `AgentService.runCycle` | geschützt durch `cycleMutex` (kein Doppelzyklus von FGS-Loop + Worker) |
| `AgentService.runLoop` | aktualisiert die persistente Agent-Notification (`NotificationService.notifyAgentStatus`) |
| `AgentSettings` | werden über `AgentSettingsStore` (SharedPreferences+Gson) persistiert; Dashboard/Worker laden sie |
| `AuthManager` | persistenter Fehlversuchs-Zähler + exponentielle Zeitsperre (überlebt App-Neustart) |
| `AuthManager.AuthState` | neues Feld `lockoutSecondsRemaining` |
| `WiGleApi.searchBssid` | `auth`-Header ohne Default; `WiGleAuth.header()` baut Basic (`token:name`) oder Bearer |
| `MCPClient.createInboxHttp/waitForOtpHttp/extractMagicLinkHttp` | Aufrufe laufen über `withContext(Dispatchers.IO)` (Fix NetworkOnMainThread) |
| `CrowdService/LoraService/OpticalService.searchAsset` | gesamte Kanalabfrage auf `Dispatchers.IO` |
| `TelemetryService` | unverändert (GATT-UUIDs wie dokumentiert) |
| ESP32-Firmware | SPI-Pins korrigiert (SCK=18/MISO=19/MOSI=23/SS=5), keine Wildcard-Subscription mehr, `device_id` per CONFIG setztbar, Alarm non-blocking (+30 s Auto-Stop), Motor-Default AUS, WLAN-Defaults leer |
| **Nacharbeit 2026-08-27 (Runde 2 – offene Befunde):** | |
| Retrofit-JSON (F-27) | Retrofit-Schicht **Moshi-only**: OpenChargeMap- + Google-Geolocation-DTOs auf `@Json` konvertiert, `converter-gson` entfernt (Gson bleibt nur app-intern: Sync/MQTT/WS/MCP) |
| OCM-Retrofit (F-57) | eine geteilte Retrofit-Instanz (Moshi + RxJava3-Adapter) für suspend- und Rx-Interface |
| DHL (F-18) | wire-ready: `DHL_API_URL`/`DHL_API_TOKEN` (BuildConfig + Runtime-Settings via `EndpointConfig`), optionaler Bearer-Header |
| Netatmo (F-19) | OAuth2-Refresh-Flow (`NETATMO_CLIENT_ID/SECRET/REFRESH_TOKEN` → `POST /oauth2/token`, Token-Cache mit 60-s-Puffer); Legacy-`NETATMO_TOKEN` als Fallback |
| Helium (F-20) | `requiresAuth=false` (API braucht keinen Key), zentrale `HELIUM_BASE_URL`, Fehlerfall leer |
| Node-Loops (F-38) | `ApiNodeManager.startLoops()/shutdown()`; `AgentService.stop()` hält Loops an, `queryAllNodes()` startet bei Bedarf wieder |
| i18n (F-45) | Bottom-Nav über `@StringRes` + `stringResource`; `nav_*`-Strings in values/values-de/values-en |
| Kanal-Strings (F-46/F-50) | NotificationService nutzt die String-Ressourcen; Telemetrie-Kanal (throttled 1/Min/Asset) + System-Kanal (Sync/Queue) werden jetzt beschickt |
| Auto-Lock (F-49) | konfigurierbar (1–60 Min, persistent): `AuthManager.setAutoLockMinutes` + Security-Center-UI (5/10/30 Min) |
| Alarm-Sound (F-53) | Dauer-Alarm endet automatisch nach 30 s (`ALARM_AUTO_STOP_MS`) |
| CacheManager (F-55) | echtes O(1)-LRU (access-order LinkedHashMap, synchronisiert) statt Map-Kopie |
| Node-RED (F-37) | `settings.js` mit `NODE_RED_CREDENTIAL_SECRET` (compose-Env), `functionExternalModules:false` |
| Mosquitto-Log (F-58) | nur stdout + Docker-Log-Rotation (3×10 MB) für alle drei Services |
| **Nachaudit Runde 2 (F-61a–j, s. Mängelliste §11.5):** | |
| Detection-Auswahl (F-61a/b) | tier-basiert (exakt > Funkmessung > Schätzung) statt rohem `minByOrNull`; Satellite-Sentinel zählt nicht mehr als Fund; Learning nur auf Gewinner-Kanal |
| Backend-Bridge (F-61c) | MQTT→WS nutzt gecappte Haupt-Loop (`run_coroutine_threadsafe`) – WS-Clients empfangen jetzt MQTT-Events |
| Crowd-TZ (F-61d) | Sighting-Timestamps in UTC (SQLite-Format) |
| OfflineQueue (F-61e) | `false` zählt als Versuch, Dead-Letter nach 5 Fehlversuchen |
| Notification-Spam (F-61f) | LOW_PROBABILITY je Asset nur alle 10 Zyklen |
| SQLCipher (F-61g) | Klartext-Backup wird nach Migration gelöscht |
| MCP/DB (F-61h/i) | `requestId` atomar; `detectionCount()` via `COUNT(*)` |
| Offline-Karte (F-61j) | `preloadRegion()` (CacheManager) für planbares Vorab-Laden |
| Broadcast-Alarm (F-05-Rest) | Topic `secureguard/broadcast/command` beidseitig (App-Subscription+Handler, Node-RED-Inject strukturiert) |
| Backend | optionale `X-API-Key`-Auth (Env `SECUREGUARD_API_KEY`), CORS korrigiert (`CORS_ORIGINS`), `/api/health` → 503 bei degraded, DB-Endpunkte im Threadpool (`def`), portables Command-UPDATE, lifespan-Startup, paho `<2.0` gepinnt |

## 3. Bekannte, bewusst offene Punkte (Stand dieses Branches)

| Punkt | Status |
|---|---|
| DHL-Packstation-API | ✅ wire-ready: URL + Bearer-Token zur Laufzeit konfigurierbar; ** tatsächliche DHL-Credentials/Vertrag muss der Anwender liefern** – ohne diese liefert der Kanal leer. |
| Netatmo | ✅ OAuth2-Refresh implementiert; **Anwender-Registrierung (App/Secret/Refresh-Token)** nötig, Legacy-Token als Fallback. |
| Helium IoT API | ✅ korrekt konfiguriert (kein Auth nötig); inhaltliches Risiko post-Solana bleibt beobachtet, Fehlerfall = leerer Kanal. |
| RBAC | `RoleManager` vorhanden, App läuft implizit im ADMIN-Kontext; Enforcement an Aktionen/Serveranbindung geplant (Phase 2). |
| i18n | Bottom-Nav + Kanalnamen i18n-fähig; Deep-Screen-Texte größtenteils noch hartkodiert Deutsch (Phase 2). |
| Gson+Moshi | Retrofit-Schicht konsolidiert (Moshi-only); Gson verbleibt bewusst für App-interne Services. |
| Node-RED Credential-Secret | Pilot-Default „secureguard-change-me"; **Anwender setzt `NODE_RED_CREDENTIAL_SECRET` in `.env`**. |
