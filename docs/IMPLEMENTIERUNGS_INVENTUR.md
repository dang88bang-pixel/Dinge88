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
| ESP32-Firmware | SPI-Pins korrigiert (SCK=18/MISO=19/MOSI=23/SS=5), keine Wildcard-Subscription mehr, `device_id` per CONFIG setztbar, Alarm non-blocking, Motor-Default AUS, WLAN-Defaults leer |
| Backend | optionale `X-API-Key`-Auth (Env `SECUREGUARD_API_KEY`), CORS korrigiert (`CORS_ORIGINS`), `/api/health` → 503 bei degraded, DB-Endpunkte im Threadpool (`def`), portables Command-UPDATE, lifespan-Startup, paho `<2.0` gepinnt |

## 3. Bekannte, bewusst offene Punkte (Stand dieses Branches)

| Punkt | Status |
|---|---|
| DHL-Packstation-API | Nur Vertrag (`DhlPackstationApi`): öffentlicher Endpunkt + OAuth2-Client-Credentials existieren nicht frei → Kanal liefert ohne DHL-Vertrag konsequent leer. |
| Netatmo | Bearer-Token build-time (`NETATMO_TOKEN`); OAuth2-Refresh-Flow nicht implementiert → Token muss manuell rotiert werden. |
| Helium IoT API | `api.helium.io/v1` nach Solana-Migration – ohne hartes Versions-Pinning, im Fehlerfall leer. |
| RBAC | `RoleManager` (4 Rollen/7 Permissions) vorhanden, aber App läuft implizit im ADMIN-Kontext; Enforcement an Aktionen/Serveranbindung geplant (Phase 2). |
| i18n | UI-Texte größtenteils hartkodiert Deutsch; `values-en` deckt nur 5 Strings ab. |
| Gson+Moshi | Dual-Stack aktiv (F-27); Konsolidierung offen. |
| Auto-Lock-Dauer | fix 5 min (`AUTO_LOCK_MINUTES`), nicht konfigurierbar (F-49). |
