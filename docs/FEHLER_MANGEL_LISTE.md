# 🔍 SecureGuard Enterprise – Fehler- & Mängel-Liste (Vollaudit)

**Stand:** 2026-08-27 · **Scope:** Komplettes Repository `Dinge88` (Android-App, Backend, Docker-Stack, Mosquitto, Node-RED, ESP32-Firmware, CI, Skripte, Tests, Doku)
**Methodik:** Statisches Cross-Review aller 119 Kotlin-Dateien, Manifest/Runtime-Berechtigungsabgleich, Gradle-Abhängigkeitsabgleich (Imports vs. TOML), Backend-Code-Review, Infrastruktur-/Vertragsprüfung (MQTT-Topics, REST-Endpunkte, BLE-UUIDs), gezielte Verifizierung von Verdachtsfällen (SQLite `UPDATE…ORDER BY…LIMIT`, paho-mqtt 2.x-API, AOSP-USB-Filter-Parsing).

> Legende: 🔴 **P1 = kritisch** (Crash/Security/Kernfunktion kaputt) · 🟠 **P2 = hoch** (Feature funktioniert praktisch nie / Sicherheitslücke in Produktion) · 🟡 **P3 = mittel** (Lücke, Fehlverhalten unter Bedingungen) · ⚪ **P4 = niedrig** (Hygiene, Dok-Drift, toter Code)

---

## 0. Executive Summary

| Kategorie | Befund |
|---|---|
| ✅ **Solide** | Navigation/Routing vollständig (17 Routen ↔ Screens), DAOs/Repository konsistent verdrahtet, Runtime-Permission-Checks in BLE/WiFi/GPS vorhanden, SQLCipher+KeyStore-Konzept sauber, Backend-Sync-Feldnamen (`short_name`/`last_seen`) korrekt gemappt, BLE-UUIDs App↔Firmware identisch, alle 8 `ActionType`-Wire-Commands existieren in der Firmware |
| 🔴 Kritisch | **9** Befunde (Crash-Risiko, Brute-Force-Schwäche, MQTT-Befehl an alle Gateways, offene Backend-Write-Endpunkte, Firmware-Pin-Konflikt, Offline-Queue-Umgehung) |
| 🟠 Hoch | **10** Befunde (Main-Thread-Netzwerk, tote API-Anbindungen, fehlende Background-Location, CI ohne Tests, DB-Backup-Schwächen) |
| 🟡 Mittel | **14** Befunde |
| ⚪ Niedrig | **12** Befunde |

---

## 1. 🔴 Kritische Fehler (P1)

### F-01 · StrongBox-Crash in `DatabaseKeyManager` (Crash beim DB-Start)
**Datei:** `app/src/main/java/com/secureguard/enterprise/security/DatabaseKeyManager.kt`
1. `builder.setIsStrongBoxBacked(true)` ist erst ab **API 28** verfügbar – `minSdk = 26`. Auf API 26/27 → `NoSuchMethodError` (das `try/catch` um den *Setter* fängt nichts, weil die Exception beim **generieren** fliegt bzw. Methodenauflösung vorher scheitert).
2. `setIsStrongBoxBacked(true)` + `generateKey()` auf Geräten **ohne** StrongBox → `StrongBoxUnavailableException`/`ProviderException` bei `generator.init/generateKey()` – wird **nicht** abgefangen → App stürzt beim ersten DB-Open ab (die meisten Nicht-Pixel-Geräte!).

**Fix:** StrongBox nur wenn `Build.VERSION.SDK_INT >= 28`, umschalten auf `setIsStrongBoxBacked(false)`-Fallback via `catch (StrongBoxUnavailableException)` um `init/generateKey`, oder Kompatibilität `Build.VERSION.SDK_INT >= P` guard.

### F-02 · Netzwerk-/Blockier-I/O auf dem Main-Thread (mehrere Features faktisch tot)
Blockierendes `OkHttp…execute()` in `suspend`-Funktionen, aufgerufen aus `viewModelScope.launch { }` **ohne Dispatcher (→ Main)**:
| Stelle | Aufrufer | Folge |
|---|---|---|
| `MCPClient.createInboxHttp/waitForOtpHttp/extractMagicLinkHttp` | `TempMailViewModel` (Main) | `NetworkOnMainThreadException` → wird geschluckt → **Temp-Mail-Screen liefert immer „fehlgeschlagen"** (sobald `MCP_SERVER_URL` gesetzt) |
| `AgentService.performRegistration` (execute) | `AgentViewModel.autoRegisterService` (Main) | Auto-Register schlägt immer fehl |
| `CrowdService.searchBackend`, `LoraService.searchLocalGateway`, `OpticalService.queryYolo` (execute) | `AssetDetailViewModel` „Jetzt suchen" → `agentService.searchAsset` → `comprehensiveSearch` (async erbt Main) | Bei gesetztem Backend-/LoRa-/YOLO-URL: Exception pro Kanal → Kanal liefert null; ohne URL nur Latenz |

**Fix:** `withContext(Dispatchers.IO)` in allen HTTP-Methoden oder Aufrufe auf `Dispatchers.IO` verlagern (SettingsViewModel macht es vorbildlich vor – die anderen nicht).

### F-03 · PIN-Brute-Force-Schutz wirkungslos („5-Versuch-Limit" per Neustart zurücksetzbar)
**Datei:** `services/AuthManager.kt`
- `KEY_LOCKED_AT` wird bei `remaining <= 0` **geschrieben, aber nirgends gelesen**. `loadState()` setzt bei jedem Start `attemptsRemaining = MAX_ATTEMPTS` und `locked = enabled` → nach App-Neustart sind wieder **5 Versuche frei**. README/Checkliste versprechen „5-Versuch-Limit" – faktisch unbegrenzte Versuche in 5er-Batches. Kein Zeit-Lockout, keine „Pin vergessen"-Wiederherstellung (Hard-Lock = reinstall).

**Fix:** `KEY_LOCKED_AT` auswerten (z. B. exponentieller Lockout), Versuche-Zähler persistent speichern.

### F-04 · Backup/Restore-Mängel (`BackupManager`)
**Datei:** `services/BackupManager.kt`
1. `isValidSqlite()` = „plain SQLite **oder** `isSqlCipherDatabase()`" – letzteres ist nur „Datei >512 B und **kein** Plain-Header" → **jede beliebige Datei** (Bild, Text) besteht die Validierung → Restore kann DB mit Müll überschreiben (wird erst beim nächsten Start wirksam).
2. `createBackup()` kopiert die SQLite-Datei per Stream **ohne WAL-Checkpoint** → bei WAL-Modus können letzte Transaktionen fehlen; konsistentes Backup nur mit `VACUUM INTO` / `sqlite3_backup` / vorherigem Checkpoint.
3. Direct-Boot-Fall: `SecureGuardApplication.onCreate` → `applyPendingRestoreIfPresent()` → `getExternalFilesDir(null)` kann bei `LOCKED_BOOT_COMPLETED` `null` liefern → `File(null, "backups")` wirft **NPE außerhalb** des `runCatching` → Boot-Crash (nur bei Geräten mit Direct Boot).

### F-05 · ESP32-Firmware empfängt **jeden** Befehl für **jedes** Asset (Wildcards-Subscription)
**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino` (reconnect, Zeile ~345)
- `client.subscribe("secureguard/+/command")` → bei mehreren Gateways führt **jedes Gateway ALARM/LIGHT/MOTOR_OFF aus**, auch wenn der Befehl nur an ein Asset gerichtet war (Topic ist `secureguard/<ASSET-MAC>/command`). Gleichzeitig kein Mapping `device_id ↔ Asset-MAC` (Default `device_id = "ESP32_SecureGuard"` ≠ MAC) → gezielte Zustellung App→ESP32 funktioniert nur zufällig/eins zu eins.
- Zusätzlich: App published **Uppercase-MAC** (`MqttConfig.commandTopic`), Backend published MAC **unverändert** (`f"secureguard/{asset_mac}/command"`) → MQTT-Topics sind case-sensitiv → Backend-Befehle erreichen ggf. das falsche/nobody-Topic.

**Fix:** Nur eigenes Topic (`secureguard/{device_id}/command`) abonnieren; device_id = Asset-MAC setzen; MAC-Normalisierung App/Backend vereinheitlichen.

### F-06 · Firmware-LoRa: SPI-Pin-Konflikt (SCK = Chip-Select = GPIO5)
**Datei:** `secureguard_esp32.ino`
- `#define LORA_SS 5` **und** `SPI.begin(5, 19, 27, 18)` (SCK=5, SS=18) → GPIO5 ist gleichzeitig SCK **und** NSS → LoRa-Transceiver wird hardwareseitig nicht funktionieren (verbatim getestet unmöglich, aber Pin-Doppelbelegung ist objektiv fehlerhaft; übliche Belegung wäre SCK=5/**NSS=18** oder SCK=18/NSS=5 konsistent mit `LoRa.setPins`).
- Nebenkriegsschauplatz: `ALARM` blockiert `loop()` 2 s (10× delay), default-WLAN-PSK `secureguard123` im Source, Motor-Relay startet default AN.

### F-07 · `sendAction()` meldet falsche Zustellung → Offline-Queue wird umgangen
**Datei:** `services/AgentService.kt` (sendAction)
- WebSocket-Zweig: `webSocketService.sendCommand()` liefert `Unit`; `delivered = true` allein weil `isConfigured` – **nicht** weil verbunden/gesendet.
- MQTT-Zweig: `delivered = true` anhand **globalem** `mqttService.isConnected`, nicht anhand des Publish-Ergebnisses (`publish()` verschluckt Fehler nur als Event).
→ Aktionen gelten als „zugestellt", obwohl nichts ankam; der versprochene Offline-Fallback greift dann nicht. README: „8 Befehle über **4** Zustellkanäle" – implementiert sind **3** (MQTT, WS, BLE/GATT); Backend-/LoRa-Zustellung fehlt.

### F-08 · Backend: keinerlei Authentifizierung/Autorisierung + ungültige CORS-Konfiguration
**Datei:** `backend/main.py`
- **Alle** Endpunkte sind ungeschützt – auch schreibende: `POST /api/assets`, `/api/detections`, `/api/alerts`, `/api/actions/execute` (löst MQTT-Befehle aus!), `/api/crowd/report`, `/api/mcp/inject_message`. Jeder, der Port 8000 erreicht, kann Assets manipulieren und **physische Befehle (ALARM/MOTOR_OFF)** an Assets senden.
- CORS: `allow_origins=["*"]` zusammen mit `allow_credentials=True` ist per Spec ungültig (Sterlette sendet dann keinen Wildcard-Origin; Browser schlagen fehl) – Konfiguration ist fehlerhaft **und** unsicher.
- `sqlite3`-Aufrufe synchron in `async def`-Endpunkten → blockieren den Event-Loop unter Last.

**Fix:** API-Key/JWT-Middleware (mind. für schreibende Endpunkte), CORS korrigieren, DB-Zugriffe in Threadpool (`run_in_executor`/`def`-Endpunkte).

### F-09 · Foreground-Service / Boot-Pfad: Ziel-SDK-35-spezifische Risiken
**Dateien:** `AndroidManifest.xml`, `services/AgentForegroundService.kt`, `receiver/BootReceiver.kt`
- `foregroundServiceType="dataSync"`: ab **Android 15** (targetSdk 35) gilt ein **6-Stunden-Limit** für dataSync-FGS – der „permanente Agent" wird danach vom System gestoppt; kein Handling/Fallback im Code (z. B. `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` oder WorkManager-Only-Modus).
- `BootReceiver` ist `directBootAware="true"` und reagiert auf `LOCKED_BOOT_COMPLETED`: In dieser Phase ist CE-Storage nicht verfügbar → `WorkManager.getInstance()` (Hilt/WorkManager-Init, CE-Storage) und der indirekte `BackupManager`-Pfad (F-04.3) laufen ins Leere/Crash-Risiko. `Application` ist nicht `directBootAware` – Konstellation inkonsistent.

---

## 2. 🟠 Berechtigungen & Security-Setup (P2)

### F-10 · Fehlende `ACCESS_BACKGROUND_LOCATION` für den Hintergrund-Agenten
`SecureAgentWorker` (15-Min-Zyklus) nutzt `WifiService`/`BleService` **im Hintergrund**. Seit Android 10 liefern WiFi-Scan-Ergebnisse im Hintergrund **keine** BSSID/RSSI-Daten ohne Hintergrund-Standortfreigabe; BLE-Scan im Hintergrund ist ebenfalls eingeschränkt (Android 8+: Scan im Hintergrund nur mit Filter – ok – aber WiFi-Scan-Results sind leer). Die App fragt **nur** Vordergrund-Location an. → Die Kernkanäle BLE/WiFi liefern im geplanten Hintergrundlauf schlicht nichts. Entweder Permission+Onboarding ergänzen oder Verhalten dokumentieren/hardwareseitig akzeptieren.

### F-11 · Backup-/Device-Transfer-Regeln lückenhaft (`backup_rules.xml`, `data_extraction_rules.xml`)
- Exkludiert sind nur `secureguard.db` und `secureguard_settings.xml`. **Nicht** exkludiert (werden also in Cloud-Backup/Device-Transfer aufgenommen):
  - `secureguard_endpoints` – enthält **MQTT-Passwort im Klartext** (`EndpointConfig.KEY_MQTT_PASS`)
  - `secureguard_db_key` – die wrapped SQLCipher-Passphrase (allein zwar nutzlos, aber Key-Material wandert zum Backup-Anbieter)
  - `secureguard_auth` – PIN-Hash + Salt (Offline-Brute-Force möglich)
  - `secureguard.db-wal` / `-shm` – Auto-Backup-Exclude `path="secureguard.db"` deckt die WAL/SHM-Dateien nicht ab → **DB-Inhalte können über die WAL-Datei im Klartext? nein (verschlüsselt), aber Teile der DB wandern unvollständig/konsistenzgefährdend mit**
- `android:allowBackup="true"` ist für eine Security-App diskutabel – mit obigen Lücken eher problematisch.

### F-12 · USB-Workflow unvollständig (Permission-Dialog fehlt)
Manifest registriert `USB_DEVICE_ATTACHED` + `device_filter`, aber `MainActivity` verarbeitet **nur NFC-Intents**; es gibt nirgends `UsbManager.requestPermission(...)`. `UsbSerialService.readLine()` prüft nur `hasPermission()` und liefert sonst still `null` → der beworbene USB-Kanal hat keinen Bedienpfad zur Rechteeinholung.

### F-13 · `device_filter.xml`: ungültige Hex-Werte → CH340/CH341-Filter wirkungslos
`vendor-id="1a86"` ist weder Dezimal noch `0x`-Hex. AOSP `DeviceFilter` parst Dezimal, Hex nur mit `0x`-Präfix; ungültige Werte werden mit Log-Fehler **übersprungen**. → Die WCH-Einträge (CH340/CH341 – die häufigsten China-Adapter) matchen nie. Korrekt: `6790` oder `0x1a86`.

### F-14 · Redundante/falsche Manifest-Bits
- `android:usesCleartextTraffic="true"` ist wirkungslos, solange `networkSecurityConfig` gesetzt ist (NSC gewinnt) – Main-NSC erlaubt Cleartext sowieso, Release-NSC verbietet es → Flag ist irreführend (Kommentar im CT45PConfig behauptet, das Flag sei für MQTT-Cleartext verantwortlich – stimmt so nicht, für Release greift das Verbot).
- `USB_DEVICE_ATTACHED`-`meta-data` **doppelt**: einmal in der Activity (korrekt) und einmal auf Application-Ebene (wirkungslos).
- `MODIFY_AUDIO_SETTINGS` deklariert, aber `ToneGenerator(STREAM_ALARM)` benötigt diese Permission nicht → unnötige Permission.

### F-15 · Backend `/api/health` meldet DB-Fehler als HTTP 200 `{"status":"degraded"}`
Monitoring/Compose-Healthcheck sehen „grün", obwohl die DB defekt ist. Healthcheck-Endpunkt sollte bei `degraded` 5xx liefern (oder der Check wertet `status` aus – tut er nicht).

### F-16 · MQTT-Auth: Credentials im Klartext-SharedPreferences; Mosquitto-Prod-Pfad gefährlich
- `EndpointConfig` speichert MQTT-Pass unverschlüsselt (siehe auch F-11 Backup).
- `mosquitto/config/passwd.example` besteht **nur aus Kommentaren**. Wer der GO_LIVE-Anleitung folgt (`cp passwd.example passwd` + `allow_anonymous false` ohne `mosquitto_passwd`-Nutzer anzulegen) sperrt **alle** Clients inkl. eigener App aus (Mosquitto startet ggf. mit leerer passwd → niemand kann connecten). acl.example ebenso: alle Regeln auskommentiert → direkt als `acl_file` eingebunden = leere ACL.

---

## 3. 🟠 Anbindungen / Verträge (P2)

### F-17 · WiGle-API: falsches Auth-Schema → Anbindung praktisch immer 401
`WiGleApi.searchBssid` sendet `Authorization: Bearer <WIGLE_API_KEY>`. Die reale WiGle-API v2 verlangt **HTTP-Basic** mit `token:name` (zwei Werte). `local.properties.example` bietet nur **einen** Key → mit realem WiGle-Account funktioniert der Kanal so nicht. (Im KDoc eingeräumt, im Produktcode aber unfixt.)

### F-18 · DHL-Packstation-Anbindung: Endpunkt + Auth existieren nicht
`https://api.dhl.de/packstation/api/v1/station` ist kein öffentlicher Endpunkt; DHL benötigt OAuth2-Client-Credentials – es wird **kein** Auth-Header gesetzt. Der Urban-Kanal-Zweig „DHL" ist damit konstant leer (im Code als „Vertrag" dokumentiert – trotzdem: README/Checkliste verkaufen ihn als ✅/⚙️).

### F-19 · Netatmo: statischer Bearer-Token ohne Refresh-Flow
Netatmo-Access-Tokens laufen ~3 h; `NETATMO_TOKEN` wird zur Build-Zeit eingebrannt, es gibt keinen OAuth2-Refresh → Kanal nach kurzer Zeit dauerhaft 401.

### F-20 · Helium-API v1 (Post-Solana-Migration) ungesichert
`api.helium.io/v1/hotspots/...` – Helium IoT-Daten/APIs wurden im Zuge der Solana-Migration teils umgezogen/deprecated; keine Version-Pin-/Fallback-Strategie; `requiresAuth=true` in NodeConfig, obwohl die API keinen Key braucht → Konfig-Kontradiktion.

### F-21 · Node-RED: deklarierte Dashboard-Dependencies werden nicht genutzt
`nodered/package.json` deklariert `node-red-dashboard` + `node-red-node-ui-table`, aber `flows.json` enthält **keinen** einzigen `ui_*`-Node (nur debug/function/join/mqtt/http/inject) → tote Abhängigkeiten; zudem installiert das offizielle `nodered/node-red`-Image `/data/package.json` nicht zuverlässig automatisch (je nach Version/Entrypoint) – für die Flows aber irrelevant, da ungenutzt. Ein „Dashboard" wie im README suggeriert existiert nicht – es sind Debug-Flows.

### F-22 · `.env.example` ist totes Artefakt
`docker-compose.yml` definiert keine `env_file:`/Variablen-Substitution → `.env.example` hat keinerlei Funktion.

### F-23 · Backend-Stack: Dev-Konfiguration im Dauerbetrieb
`docker-compose.yml` mounted `./backend:/app` und überschreibt das CMD mit `uvicorn … --reload` → Auto-Reload im „Produktions"-Stack; In-Memory-`_temp_inboxes` verlieren bei jedem Reload/Neustart alle Inbox-Tokens (Temp-Mail-Feature bricht dann). Keine Restart-/Resource-Limits, Ports (1883 ohne TLS, 8000, 1880) auf allen Interfaces exponiert – für Pilot ok, für Produktion fehlt Reverse-Proxy/TLS-Konzept.

### F-24 · NDEF-Parsing: Sprachcode-Länge hartkodiert
`NfcService.readTagId` überspringt bei `payload[0]==0x02` fest 3 Bytes (Status + „en"-Annahme). Tags mit anderen Sprachcodes (z. B. 3-Buchstaben) oder zusätzlichem leerem Record werden falsch gelesen → NFC-Detection schlägt fehl. Richtig: Status-Byte-High-Nibble als Sprachlänge interpretieren.

---

## 4. 🟡 Abhängigkeiten (P2/P3)

### F-25 · Fünf deklarierte Libraries werden **nirgends importiert** (tote Abhängigkeiten)
| Alias | Artifact | Status |
|---|---|---|
| `rxandroid` | io.reactivex.rxjava3:rxandroid | 0 Imports (rxjava+adapter werden genutzt) |
| `okhttp-sse` | okhttp3 sse | 0 Imports |
| `accompanist-permissions` | accompanist | 0 Imports (eigenes `Permissions.kt`) – README sagt „optional" |
| `nordic-ble-ktx` | no.nordicsemi.android:ble-ktx | 0 Imports (Scan via Plattform-API) – README sagt „optional" |
| `zxing-core` | com.google.zxing:core | Alias in TOML, **nie** in build.gradle referenziert |

→ APK-Größe/Attack-Surface ohne Nutzen; inkonsistent zur README-„48 Libraries" (TOML zählt **69** Aliase).

### F-26 · KSP deklariert, aber nie benutzt; kapt statt KSP
`ksp = "2.0.21-1.0.28"` in TOML, aber kein `ksp`-Plugin/Compiler angewendet – Room/Hilt/Moshi laufen über kapt (langsamer, legacy). Entweder KSP einführen oder Versions-Eintrag entfernen.

### F-27 · Doppelter JSON-Stack (Gson **und** Moshi) + doppelt kapt-Codegen & Reflektion
Beide Converter, beide Annotation-Set (@SerializedName/@Json), Moshi via `kapt moshi-kotlin-codegen` **und** `KotlinJsonAdapterFactory()` (Reflektion) parallel → Wartungs- und RCE-Oberfläche (Reflektion-Adapter) ohne Nutzen. Konsolidieren.

### F-28 · Room: `exportSchema = false` + keine Migration-Tests
`room-testing` ist deklariert, aber es gibt **keinen** MigrationTest (1→2). Ohne exportierte Schemas sind künftige Migrationen untestbar/fehleranfällig – bei einer verschlüsselten Produktiv-DB ein echtes Risiko.

### F-29 · Backend `requirements.txt` ohne Pinning
Nur `>=`-Ranges → nicht reproduzierbare Builds. Verifiziert: `paho-mqtt>=1.6` zieht aktuell **2.1.x** – funktioniert mit der alten Client-API nur per Deprecation-Fallback (CallbackAPIVersion.VERSION1-Warnung) und kann bei paho 3.x brechen. Empfehlung: `paho-mqtt~=1.6` oder Code auf v2-API umstellen; übrige Deps auf getestete Versionen pinnen + Lock-Datei.

### F-30 · Firmware: ArduinoJson deklariert, aber ungenutzt
`lib_deps` enthält `bblanchon/ArduinoJson@^7.0.0`, das `.ino` nutzt aber ein selbstgestricktes, fehleranfälliges String-Parsing (`extractJsonValue`: keine Escapes/verschachtelte Objekte, bricht bei Zahlen ohne Quotes) → entweder ArduinoJson verwenden oder Dep entfernen.

### F-31 · CI-Build funktioniert nur mit Glück ohne Keystore
`build.gradle.kts`: Wenn weder Release- noch Debug-Keystore existiert, wird `signingConfigs.create("release")` mit **leeren** `storePassword/keyPassword` angelegt (`hasAnyKeystore=false` → `signingConfig` nicht gesetzt → unsignierter Build ok). Es gibt aber keine Validierung/Warning im Gradle-Log, wenn Keystore **vorhanden, Passwörter aber leer** sind → kryptischer Build-Fehler. Minor, aber typische CI-Falle.

---

## 5. 🟠 Tests & CI (P2)

### F-32 · CI führt **keine** Tests aus (trotz gegenteiliger Doku)
`build-release.yml` baut nur `assembleDebug`/`assembleRelease`. Es existieren 8 Unit-Tests + 2 Compose-UI-Tests, aber `testDebugUnitTest` läuft **nirgends** (CI noch lokal automatisiert). `docs/CI_ENHANCEMENTS.md` fordert die Einpflege explizit („Unit tests"-Step + `arena/**`-Trigger) – **beides nie umgesetzt**. Folge: Grünes CI bei potentiell roten Tests; Doku-Aussage „CI Unit-Tests" (READY_TO_GO_CHECKLISTE, Batch 4) ist falsch.

### F-33 · Kein Lint/Detekt/ktlint, keine Backend-Tests, kein Firmware-CI
Kein einziges statisches Analyse-Werkzeug konfiguriert; `backend/` hat keinen Test/Lint-Job; PlatformIO-Build läuft nicht in CI (Firmware-Fehler wie F-05/F-06 wären so auffindbar gewesen).

### F-34 · Test-Detailproblem: Robolectric-Tests gegen `targetSdk 35` nur mit `@Config(sdk=[28])`
Funktioniert, aber auth/core-Pfade werden nie auf modernen API-Leveln getestet (z. B. POST_NOTIFICATIONS-Zweig, BLUETOOTH_SCAN-Zweig).

---

## 6. 🟡 Infrastruktur & Betrieb (P3)

| ID | Befund |
|---|---|
| F-35 | Compose-Healthcheck `mosquitto_sub … -W 3`: Mosquitto-Client im `eclipse-mosquitto:2.0`-Image vorhanden – ok, aber der Check verbindet **ohne Credentials** → sobald `allow_anonymous false` gesetzt wird, schlägt der Healthcheck permanent fehl (Container wird als unhealthy markiert, obwohl Broker gesund ist). |
| F-36 | Backend-Healthcheck prüft nur HTTP 200 (siehe F-15: `degraded` zählt als gesund). |
| F-37 | `nodered`-Service mounted `./nodered:/data` – ohne `settings.js`, ohne Credential-Secret; keine Persistent-Doku für Produktions-Credentials. |
| F-38 | `agent/ApiNodeManager` Health-Monitor/Learning-Loops laufen als Endlos-Coroutines im Singleton-`init` ohne Lifecycle-Stop – Battery-Drain-Potenzial, kein Testabdeckung; Erfolgshistorie nur in-memory (Neustart = Lernen verloren; README „selbstlernend" nur sessionweise). |
| F-39 | `AgentSettings` werden **nicht persistiert**: DashboardViewModel startet den Agent mit hartkodierten Defaults; AgentConfigScreen-Änderungen überleben App-Neustart nicht (Settings-UI schreibt nur in-memory). |
| F-40 | `AgentService.runLoop` baut die Status-Notification (`notificationService.buildAgentNotification(...)`) und **wirft sie weg** – der „persistente Status" der FGS-Notification wird nie aktualisiert (notify() fehlt). |

---

## 7. ⚪ Dokumentation vs. Realität (P3/P4)

| ID | Befund |
|---|---|
| F-41 | **`IMPLEMENTIERUNGS_INVENTUR.md` fehlt** – 2× in `app/build.gradle.kts`-Kommentaren referenziert („siehe IMPLEMENTIERUNGS_INVENTUR.md ‚Abweichungen'") |
| F-42 | README-TOC: „UI-Screens (13)" – tatsächlich **18** (eigene Checkliste sagt 18); „Services (30)" – tatsächlich **33** Service-Dateien; „Abhängigkeiten (48 Libraries)" – TOML zählt **69** Aliase (davon F-25 tot) |
| F-43 | README „Aktionen: 8 Befehle über 4 Zustellkanäle" – implementiert sind 3 Kanäle (F-07) |
| F-44 | README/Manifest-Comment „RBAC: 4 Rollen, 7 Permissions" – `RoleManager` existiert, wird aber von **keiner** Aktion/keinem Screen erzwungen (App läuft implizit als ADMIN; RBAC ist Deko) |
| F-45 | i18n fast nicht vorhanden: `values-en`/`values-de` enthalten je 5 Strings; die komplette Compose-UI ist hartkodiert Deutsch (`values-en` funktionslos). `app_name` = „SecureGuard Pro", Repo/README = „SecureGuard Enterprise" |
| F-46 | `strings.xml`-Kanalnamen (`agent_channel_name` …) werden von `NotificationService` **nicht** verwendet (hartcodierte Namen) → Übersetzungs-Files irreführend |
| F-47 | `TempMailViewModel.autoRegisterService`-Kommentar („indirect via TempMailService") ist falsch – `TempMailService` hat keinen solchen Pfad; `AgentService.performRegistration` nutzt einen eigenen OkHttpClient statt injiziertem |
| F-48 | Doku-Verweis-Drift: Checkliste/GO_LIVE nennen Branch `arena/01a03c79-dinge88` (aktuell: `arena/01a0412d-dinge88`) |

---

## 8. ⚪ Weitere Code-Hygiene (P4)

| ID | Befund |
|---|---|
| F-49 | `AuthManager.AuthState.autoLockAfterMinutes` ist konfigurierbar deklariert, aber fix 5 min – keine UI/Persistenz |
| F-50 | `NotificationService.notify()` ohne `POST_NOTIFICATIONS`-Check (ab 33 still verworfen – ok, aber Telemetrie-/System-Kanäle werden erzeugt, aber **nie** benutzt) |
| F-51 | `DashboardViewModel.toggleAgent()` stoppt nur `agentService`, nicht den laufenden `AgentForegroundService` → statusleiste bleibt „läuft" |
| F-52 | `SecureAgentWorker` und FGS-Loop können **parallel** denselben Zyklus laufen lassen (`runCycle` nicht synchronisiert) |
| F-53 | `AlertSoundManager.play(laut, loop)` → endloser Alarm bis `stop()`; kein Auto-Stop/Timeout, kein Aufrufpfad aus Alerts-Screen sichtbar |
| F-54 | `TerminalViewModel.executeCommand("cycle")` → `runCycle()` auf Main (gleiches IO-Problem wie F-02, betrifft Detail-/Terminal-Suche) |
| F-55 | `CacheManager.put/get` kopiert bei jedem Zugriff die ganze Map (O(n), Main-Sync-Points) – bei 100 Einträgen ok, aber unschön; kein echter LRU |
| F-56 | `WifiService`/`BleService` unbegrenzte `registerReceiver` ohne `RECEIVER_EXPORTED`-Flag-Pflicht (System-Broadcast – ok), aber `unregisterReceiver` im `runCatching` versteckt Doppel-Abmelde-Bugs |
| F-57 | `ApiServiceManager` erstellt pro `lazy` einen gemeinsamen `httpClient` mit Logging-Interceptor in Release `NONE` – ok; aber 2 Retrofit-Instanzen für dieselbe OCM-Basis (sync + rx) → Verschwendung, funktional ok |
| F-58 | `mosquitto.conf` loggt doppelt (file + stdout) – Log-Rotation im Container ungeklärt (`/mosquitto/log` Volume wächst unbegrenzt) |
| F-59 | Backend `@app.on_event("startup")` ist deprecated (FastAPI ≥0.93) – Deprecation-Warning, funktional ok |
| F-60 | `.gitignore` enthält `app/secureguard-keystore.*` und `*.b64` – ok; aber `local.properties.example` enthält Beispiel-URLs (`10.0.2.2`) die im Release-NSC (Cleartext verboten) ohnehin scheitern → Release-Pilot-Doku sollte https/ssl-Pfade betonen (GO_LIVE tut das nur am Rande) |

---

## 9. Priorisierte Empfehlungen (Top 10)

1. **F-01** StrongBox-Guard + Exception-Fallback (Crash beim ersten Start auf den meisten Geräten).
2. **F-02** Alle blockierenden HTTP-Aufrufe auf `Dispatchers.IO` (Temp-Mail, Auto-Register, Detail-Suche sonst funktionslos).
3. **F-08** Backend-Auth (API-Key für schreibende Endpunkte) + CORS fix + `degraded` → 5xx (F-15/F-36).
4. **F-05/F-06** Firmware: Wildcard-Subscription entfernen, `device_id = Asset-MAC`-Konvention, SPI-Pins korrigieren.
5. **F-07** `sendAction`-Zustellung an echte Rückgabewerte koppeln (sonst tote Offline-Queue).
6. **F-03** Lockout-Zähler/Zeitsperre persistent auswerten.
7. **F-11** Backup-Rules: `secureguard_endpoints`, `secureguard_db_key`, `secureguard_auth`, `-wal/-shm` exkludieren (MQTT-Passwort wandert sonst in die Cloud).
8. **F-10** Entscheidung+Implementierung `ACCESS_BACKGROUND_LOCATION` (oder Dokumentation der Hintergrund-Limits).
9. **F-32** CI: `testDebugUnitTest`-Job + `arena/**`-Trigger gemäß `CI_ENHANCEMENTS.md` endlich einpflegen; Lint-Job ergänzen.
10. **F-13/F-12** `device_filter`-Hex-Werte fixen (0x-Präfix) + USB-Permission-Request-Flow ergänzen.

---

## 10. Anhang: Geprüfte und für **in Ordnung** befundene Bereiche

- **Manifest ↔ Code:** alle 4 deklarierten Komponenten (Activity, FGS, BootReceiver, Application) existieren und sind korrekt benannt; ZXing-`CaptureActivity`-Override sauber (`tools:replace`); NFC-Tech-Filter vorhanden; `data_extraction_rules`/`full-backup-content` sind gültig.
- **Runtime-Permissions:** `requiredPermissions()` deckt FINE/COARSE, CAMERA, POST_NOTIFICATIONS (33+), BLUETOOTH_SCAN/CONNECT (31+) ab; BLE/WiFi/GPS-Services prüfen Permissions vor Nutzung (keine SecurityException-Pfade gefunden).
- **Navigation:** alle 17 Routen ↔ Screens verdrahtet, Asset-Detail-Argument-Typ korrekt.
- **Datenbank:** Room v2 + Migration 1→2 stimmt SQL-konform mit den Entities; 5 DAOs vollständig in AppModule bereitgestellt; Repository-Impl nutzt nur existierende DAO-Methoden.
- **Verträge, die stimmen:** BLE-Service/Char-UUIDs App↔ESP32 identisch; MQTT-Topics App↔Firmware konsistent (bis auf Case/Wildcard, siehe F-05); Backend-REST-Pfade ↔ `CrowdService`/`MCPClient`/`BackendSyncService` stimmen; `RemoteAsset`-SerialNames matchen Backend-Snake-Case; Google-Geolocation-/OCM-/CKAN-Pfade korrekt.
- **Skripte:** alle 16 Shell-Skripte syntaxfehlerfrei; `prepare-all.sh`-Pfadliste deckt reale Repo-Struktur ab; Keystore-Script erzeugt keine Passwörter (wie dokumentiert).
- **Backend-Verifizierung:** `UPDATE … ORDER BY … LIMIT` funktioniert mit Debian-SQLite-Build (python:3.12-slim) – ist aber nicht portables SQL (Hinweis).

---

## 11. Reparatur-Status (2026-08-27 · Branch `arena/01a0412d-dinge88`)

> Nachfolgend der Stand nach dem Reparatur-Commit. Details zu den Änderungen: `docs/IMPLEMENTIERUNGS_INVENTUR.md` §2.

| ID | Befund | Status | Reparatur |
|----|--------|--------|-----------|
| F-01 | StrongBox-Crash (`DatabaseKeyManager`) | ✅ behoben | StrongBox nur mit API≥28 **und** `FEATURE_STRONGBOX_KEEP`; Fallback-Key ohne StrongBox, Exceptions abgefangen |
| F-02 | Main-Thread-Netzwerk (Temp-Mail, Register, Detail-Suche) | ✅ behoben | MCP-HTTP-Methoden, `performRegistration`, `Crowd/Lora/Optical.searchAsset` auf `Dispatchers.IO` |
| F-03 | PIN-Brute-Force zurücksetzbar | ✅ behoben | persistenter Fehlversuchszähler + exponentielle Zeitsperre (`KEY_LOCKED_AT` = gesperrt-bis), `AuthState.lockoutSecondsRemaining` |
| F-04 | Backup-Manager (Validierung/WAL/NPE) | ✅ behoben | SQLCipher-Datei wird mit echtem Key geöffnet validiert, WAL-Checkpoint `TRUNCATE` vor Copy, `backupDir` null-sicher (Fallback `filesDir`), `runCatching` in Application |
| F-05 | MQTT-Wildcard an allen Gateways | ✅ behoben | Firmware abonniert nur `secureguard/<device_id>/command`; Backend published GROSS-MAC; `device_id` per CONFIG setztbar |
| F-06 | Firmware SPI-Pin-Konflikt | ✅ behoben | `SPI.begin(18,19,23,SS=5)`; Alarm non-blocking; Motor-Default AUS; WLAN-Defaults leer |
| F-07 | `sendAction` false-positive Zustellung | ✅ behoben | `MqttService.publish/sendCommand → Boolean`, `WebSocketService.sendMessage/sendCommand → Boolean`, Offline-Queue nutzt echte Ergebnisse (inkl. `flushOfflineQueue`) |
| F-08 | Backend ohne Auth / CORS kaputt | ✅ behoben | optionale `X-API-Key`-Auth (Env `SECUREGUARD_API_KEY`) auf allen POST-Endpunkten, korrektes CORS via `CORS_ORIGINS`, DB-Endpunkte im Threadpool (`def`) |
| F-09 | FGS dataSync 6h / Direct-Boot | ✅ behoben (soweit im Code möglich) | expliziter `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 29+), BootReceiver nur noch `BOOT_COMPLETED` ohne `directBootAware`, WorkManager/Restore in `runCatching`; 6h-Limit dokumentiert (Worker übernimmt danach) |
| F-10 | Background-Location fehlt | ✅ behoben | Manifest-Permission + gestaffelte Anfrage nach Fine-Location (`missingBackgroundPermissions`) |
| F-11 | Backup-Rules-Lücken | ✅ behoben | `secureguard_endpoints`/`secureguard_db_key`/`secureguard_auth` + DB-`-wal/-shm/-journal` aus Cloud-Backup & Device-Transfer exkludiert |
| F-12 | USB-Permission-Flow fehlt | ✅ behoben | `UsbSerialService.requestPermission()` + `ACTION_USB_PERMISSION`-Empfänger + Auto-Request bei `USB_DEVICE_ATTACHED` in MainActivity |
| F-13 | `device_filter` Hex-Fehler (CH34x) | ✅ behoben | `vendor-id="0x1a86"` |
| F-14 | Manifest-Bits redundant | ✅ behoben | `MODIFY_AUDIO_SETTINGS` entfernt, doppeltes USB-meta-data entfernt, `usesCleartextTraffic` entfernt (NSC regelt), CT45P-Kommentar korrigiert |
| F-15 | Health `degraded` = HTTP 200 | ✅ behoben | `/api/health` → 503 bei DB-Fehler (inkl. `get_db`-Fehlern) |
| F-16 | Mosquitto-Beispiele gefährlich | ✅ behoben | Reihenfolge-Warnung in `mosquitto.conf`, `acl.example` mit echten Regeln (+`pattern`-Gateways), Healthcheck auth-unabhängig (`nc -z` + Fallback) |
| F-17 | WiGle Bearer vs Basic | ✅ behoben | `WiGleAuth.header()`: `token:name` → HTTP-Basic, sonst Bearer; Key-Format in `local.properties.example` dokumentiert |
| F-18 | DHL-Endpunkt/Auth existieren nicht | ✅ wire-ready | `DHL_API_URL`/`DHL_API_TOKEN` (BuildConfig + Runtime-Settings), optionaler Bearer; echte Credentials = Anwendervertrag |
| F-19 | Netatmo ohne Refresh | ✅ behoben | OAuth2-Refresh-Flow (`NETATMO_CLIENT_ID/SECRET/REFRESH_TOKEN`), Token-Cache, Legacy-Fallback |
| F-20 | Helium v1 Risiko | ✅ behoben (konfig) | `requiresAuth=false`, zentrale `HELIUM_BASE_URL`, Fehlerfall leer; Restrisiko dokumentiert |
| F-21 | Node-RED tote Dashboard-Deps | ✅ behoben | `package.json` ohne Zusatzdeps (Flows sind Core-only) |
| F-22 | `.env.example` tot | ✅ behoben | compose liest `.env` (`SECUREGUARD_API_KEY`, `CORS_ORIGINS`), `.env.example` funktional |
| F-23 | compose `--reload` im Stack | ✅ behoben | CMD aus Dockerfile, Bind-Mount des Codes entfernt, Backend-Healthcheck prüft HTTP 200 |
| F-24 | NDEF-Status-Byte hartkodiert | ✅ behoben | RTD-Text korrekt geparst (Sprachlänge aus Status-Byte, UTF-16-Fall) |
| F-25 | 5 ungenutzte Libraries | ✅ behoben | `rxandroid`, `okhttp-sse`, `accompanist-permissions`, `nordic-ble-ktx`, `zxing-core` aus build + TOML entfernt (69 → 64 Aliase) |
| F-26 | KSP-Alias ungenutzt | ✅ behoben | aus TOML entfernt (kapt beibehalten) |
| F-27 | Gson+Moshi dual | ✅ Retro-Schicht konsolidiert | Retrofit **Moshi-only** (OCM/Google-DTOs konvertiert, `converter-gson` entfernt); Gson nur noch app-intern |
| F-28 | Room-Schemas/Migration-Tests | ✅ behoben | `exportSchema=true` + `room.schemaLocation` → `app/schemas` |
| F-29 | Backend-Deps ungepinnt | ✅ behoben | ranges gebunden, `paho-mqtt<2.0`; verifiziert mit 7 laufenden pytest-Tests |
| F-30 | ArduinoJson ungenutzt (FW) | ✅ behoben | aus `platformio.ini` entfernt |
| F-31 | Keystore-Passwort-Falle | ✅ behoben | Gradle-`logger.warn`, wenn Keystore ohne Passwort existiert |
| F-32 | CI ohne Tests | ✅ behoben | `testDebugUnitTest`-Step (Debug-Job, fail-hard) + Report-Artefakte + `arena/**`-Trigger |
| F-33 | Kein Lint/Backend-Tests | ✅ behoben | `lintDebug`-Step (soft-fail) + eigener `backend-tests`-Job (pytest, release needs both) |
| F-34 | Robolectric nur SDK 28 | ✅ ergänzt | neuer `AuthManagerLockoutTest` auf SDK 34 (Lockout/Auto-Lock-Pfade) |
| F-35 | Mosquitto-Healthcheck vs. Auth | ✅ behoben | siehe F-16 |
| F-36 | Backend-Healthcheck prüft nur 200 | ✅ behoben | compose-Check prüft `r.status==200` (503 → unhealthy) |
| F-37 | Node-RED ohne Settings/Credential-Secret | ✅ behoben | `nodered/settings.js` + `NODE_RED_CREDENTIAL_SECRET` (compose/.env) |
| F-38 | ApiNodeManager-Lifecycles | ✅ behoben | `startLoops()/shutdown()`, AgentService.stop() hält an, queryAllNodes() startet wieder |
| F-39 | AgentSettings nicht persistiert | ✅ behoben | neuer `AgentSettingsStore`; Dashboard/Agent-Config/Settings schreiben+lesen |
| F-40 | runLoop wirft Notification weg | ✅ behoben | `NotificationService.notifyAgentStatus()` aktualisiert die persistente Notification |
| F-41 | `IMPLEMENTIERUNGS_INVENTUR.md` fehlt | ✅ behoben | Datei erstellt (Abweichungen, Schnittstellen-Änderungen, offene Punkte) |
| F-42 | README-Zähler falsch | ✅ behoben | 18 Screens, 33 Services, **63** Libraries (Gson-Converter entfernt, Runde 2) |
| F-43 | „4 Zustellkanäle" | ✅ behoben | README: 3 Kanäle + Offline-Queue |
| F-44 | RBAC-Deko | 🟡 dokumentiert | README markiert „vorbereitet"; Enforcement Phase 2 |
| F-45 | i18n fehlt | 🟡 Grundlage | Bottom-Nav/Kanalnamen über String-Ressourcen; Deep-Screen-Texte Phase 2 |
| F-46 | Kanal-Strings ungenutzt | ✅ behoben | NotificationService nutzt `alerts_channel_name` etc. |
| F-47 | Staler TempMail-Kommentar | ✅ behoben | korrigiert |
| F-48 | Branch-Referenzen alt | ✅ behoben | GO_LIVE/CHECKLISTE aktualisiert |
| F-49 | Auto-Lock fix 5 min | ✅ behoben | konfigurierbar 1–60 Min (persistent) + Security-Center-UI (5/10/30) |
| F-50 | Telemetrie/System-Kanäle ungenutzt | ✅ behoben | Telemetrie (throttled 1/Min/Asset) + System (Sync/Queue-Flush) beschickt |
| F-51 | Dashboard stoppt FGS nicht | ✅ behoben | `toggleAgent()` stoppt `AgentForegroundService` mit |
| F-52 | Doppelte Zyklen möglich | ✅ behoben | `cycleMutex` in `runCycle` |
| F-53 | Endlos-Alarm ohne Auto-Stop | ✅ behoben | Auto-Stop nach 30 s (`ALARM_AUTO_STOP_MS`), `stop()` weiter möglich |
| F-54 | Terminal `cycle` auf Main | ✅ behoben | Terminal-Befehle `cycle`/`flush` jetzt `launch(Dispatchers.IO)` |
| F-55 | CacheManager O(n)-Copy | ✅ behoben | echtes O(1)-LRU (access-order LinkedHashMap) |
| F-56 | registerReceiver-Sichtbarkeit | ⚪ offen | System-Broadcast, kein Handlungsbedarf |
| F-57 | Doppelte OCM-Retrofit-Instanz | ✅ behoben | eine geteilte Retrofit-Instanz (Moshi + RxJava3-Adapter) |
| F-58 | Mosquitto-Log-Rotation | ✅ behoben | stdout-only + Docker-Log-Rotation (3×10 MB) für alle Services |
| F-59 | deprecated `on_event` | ✅ behoben | FastAPI `lifespan`-Handler |
| F-60 | Cleartext-Doku Release | ✅ behoben | Manifest-Flag entfernt, Doku in INVENTUR/GO_LIVE |

### 11.5 Nachaudit Runde 2 – neue Befunde F-61 bis F-70 (2026-08-27)

Bei der Umsetzung der Runde-2-Befunde wurden zehn weitere Schwachstellen identifiziert
(F-61a–j) und **sofort im selben Zug behoben**:

| ID | Befund | Status | Fix |
|---|---|---|---|
| F-61a | **Satellite-Sentinel gewinnt**: `comprehensiveSearch` wählte per `minByOrNull { rssi }` den *schwächsten* Wert – der SATELLITE-Sentinel (−100 = eigener GPS-Fix) machte jedes Asset zum „Fund“ → Assets nie offline, Learning prämierte SATELLITE | ✅ behoben | tier-basierte `selectBestDetection()` (exakt > Funkmessung > Schätzung), SATELLITE fließt nicht in die Auswahl ein; Learning lernt nur den Gewinner-Kanal |
| F-61b | **Exakte Sichtungen verloren**: OPTICAL/QR (rssi=0) verlor gegen alle negativen RSSI; URBAN-Fixwerte (−75/−80) konnten echte Messungen schlagen | ✅ behoben | dieselbe Tier-Logik: OPTICAL/NFC vor Funkmessungen vor Schätzquellen; innerhalb einer Tier gewinnt das stärkste Signal |
| F-61c | **MQTT→WS-Bridge stumm**: `on_message` läuft im Paho-Thread; `asyncio.get_event_loop()` wirft dort ab Python 3.10+ `RuntimeError` (still gefangen) → WS-Clients empfingen nie MQTT-Events | ✅ behoben | Haupt-Event-Loop wird beim Lifespan-Start in `main_event_loop` gecappt; Bridge nutzt `asyncio.run_coroutine_threadsafe(..., main_event_loop)` |
| F-61d | **Crowd-Zeitbasis-Mismatch**: Report-INSERT mit `datetime.now()` (Lokalzeit) vs. Suche mit `datetime('now')` (UTC) → Sichtungen in TZ≠UTC bis zur Differenz falsch ein-/ausgeblendet | ✅ behoben | INSERT schreibt `datetime.now(timezone.utc)` im SQLite-Format |
| F-61e | **OfflineQueue ohne Dead-Letter**: `executor == false` (ohne Exception) erhöhte `attempts` nicht → Einträge liefen endlos weiter | ✅ behoben | `false` zählt als Versuch (`markAttempt`); nach `MAX_ATTEMPTS` Dead-Letter (Entfernung + `Log.w`) |
| F-61f | **LOW_PROBABILITY-Spam**: Audit-Log-Eintrag pro Asset pro Zyklus | ✅ behoben | Throttle: je Asset nur alle 10 Zyklen (`LOW_PROBABILITY_EVERY_CYCLES`) |
| F-61g | **Klartext-Backup blieb liegen**: SQLCipher-Migration hinterließ `$dbName.plain.bak` (Klartext-Kopie der ganzen DB) | ✅ behoben | Backup wird nach erfolgreicher Migration gelöscht (Rollback-Pfad im Fehlerfall bleibt) |
| F-61h | **MCPClient `requestId` nicht atomar**: `++requestId` auf `var` aus mehreren Coroutines → doppelte IDs möglich | ✅ behoben | `AtomicInteger.incrementAndGet()` |
| F-61i | **`detectionCount()` materialisierte ganze Tabelle**: `observeAll().first().size` | ✅ behoben | `detectionDao().count()` (SQL `COUNT(*)`) |
| F-61j | **Offline-Karte nur Zufalls-Cache**: Offline-Modus stützte sich allein auf zufällig angesammelte osmdroid-Kacheln | ✅ behoben | `OfflineMapService.preloadRegion()` (osmdroid `CacheManager.downloadAreaNoUI`, Radius um Center, Zoom 10–17) für planbares Vorab-Laden |
| F-05-Rest | **Broadcast-Alarm verworfen**: `MqttEvent.Broadcast` wurde emittiert, aber nirgends behandelt; Node-RED-Alarminject ging ins Leere | ✅ behoben | beidseitig: neues Topic `secureguard/broadcast/command` (MqttConfig + Subscription + Handler → Alert/Notification/Sound); Node-RED `sg_inject_alarm` sendet strukturiertes JSON auf das neue Topic |

**Gesamtergebnis (2026-08-27, beide Runden):** 70 Befundpositionen bearbeitet –
**68 behoben/wire-ready**, 2 bewusst offen/teilweise (F-44 RBAC-Enforcement Phase 2,
F-45 i18n-Deep-Screens Phase 2), F-56 System-Broadcast ohne Handlungsbedarf.

### 11.6 Re-Audit Runde 3 – Berechtigungen, Anbindungen, fehlende Parts (2026-08-27)

Erneute systematische Prüfung (Manifest↔Runtime-Permissions, DI/Room/Navigation,
Retrofit↔Backend-Verträge, MQTT-Topics App↔Firmware↔Backend↔Node-RED↔ACL,
NSC/Backup-Regeln). **Vier neue Befunde, alle sofort behoben:**

| ID | Befund | Status | Fix |
|---|---|---|---|
| F-71 | **X-API-Key fehlte app-seitig**: Backend verlangt den Header auf 7 POST-Endpunkten, die App sendete ihn nirgends → `pushAsset` (POST /api/assets) und `createInbox` (POST /api/mcp/create_inbox) liefen bei gesetztém `SECUREGUARD_API_KEY` blind in 401 | ✅ behoben | `EndpointConfig.backendApiKey` (Prefs `backend_api_key` → BuildConfig `SECUREGUARD_API_KEY`), Header in BackendSyncService + MCPClient; Settings-Update/Snapshot erweitert; local.properties.example + .env.example dokumentiert |
| F-72 | **Mosquitto-ACL-Lücken**: `pattern read secureguard/broadcast` deckt `secureguard/broadcast/command` NICHT ab (Topic-Ebenen matchen exakt); Node-RED-User war read-only → `sg_inject_alarm` Publish wäre in Produktion still verworfen worden | ✅ behoben | acl.example: `pattern read secureguard/broadcast/#` für Gateways + eigener `nodered`-User mit `write` auf broadcast/command und +/command |
| F-73 | **Backend-MQTT ohne Credentials**: `mqtt.Client()` setzte nie username/pass; bei Produktion (`allow_anonymous=false`) wäre die ganze Bridge (inkl. publish_command) tot gewesen, compose übergab die Werte nicht einmal | ✅ behoben | `MQTT_USERNAME`/`MQTT_PASSWORD`-Env + `username_pw_set()` in main.py; compose-Durchgriff + .env.example |
| F-75 | **GPS im Hintergrunddienst tot**: Agent-FGS war nur `dataSync` getypt – Android 14 verlangt für Standortzugriff im Dienst den `location`-Typ, sonst wirft FusedLocationProvider im Agent-Zyklus SecurityException | ✅ behoben | `FOREGROUND_SERVICE_LOCATION`-Permission + `foregroundServiceType="dataSync\|location"` + getyptes startForeground mit beiden Typen |

**Geprüft und in Ordnung (kein Handlungsbedarf):**
Manifest-Permissions vollständig/konsistent mit Runtime-Requests (BLE_SCAN/CONNECT,
FINE+BACKGROUND-Location, CAMERA, POST_NOTIFICATIONS; maxSdkVersion-Annotationen
korrekt; BootReceiver exported=true ist Pflicht); startForeground getypt (targetSdk-34-
Pflicht); Room 5 Entities ↔ 5 DAOs + Migration; Hilt-Bindings vollständig (DB, DAOs,
Repository; alle Imports auflösbar); alle 17 Nav-Routen registriert (assetDetail mit
Argument); REST-Pfade App↔Backend deckungsgleich (/api/assets, /api/crowd/search,
/api/mcp/*); MQTT-Verträge konsistent inkl. broadcast/command beidseitig; Node-RED
Broker `mqtt:1883` + Health-Probe `backend:8000/api/health`; NSC debug=offen/
release=strikt; Backup-/Extraction-Rules exkludieren DB + alle sensiblen Prefs
(SQLCipher-Key, PIN, Endpunkte); USB/NFC-Filter vorhanden; Backend-Ports/Healthcheck/
pytest 7/7.

**Bewusst dokumentiert, kein Fix:** Node-RED `sg_inject_telemetry` sendet an die
Default-Device-ID `ESP32_SecureGuard` – wirksam nur für Gateways ohne CONFIG-`device_id`
(produktive Gateways nutzen ihre MAC als Username/ID).

| F-76 | **Node-RED-Telemetrie-Inject auf Default-Device-ID fix** (`ESP32_SecureGuard`) – produktive Gateways mit CONFIG-`device_id` bekamen den Testbefehl nie | ✅ behoben | Topic über Env-Var `SG_DEFAULT_DEVICE_ID` (compose/.env, Default unverändert) |

**Nachtrag F-44 (2026-08-27, zweite Runde):** `RoleManager` ist jetzt injizierbarer
Singleton mit **persistenter aktiver Rolle** (Prefs, Default ADMIN). Enforcement an
allen Mutations-Sites: Aktionen senden (ActionsViewModel, AssetDetailViewModel,
`EXECUTE_ACTIONS`), Asset anlegen (AddAssetViewModel, `EDIT_ASSETS`), Endpunkte
speichern (SettingsViewModel, `CONFIGURE_AGENT`), Rollenwechsel (SecurityViewModel,
`MANAGE_USERS`, mit Audit-Log `ROLE_SWITCH`/`ROLE_SWITCH_DENIED`). Rollen-Umschalter
im Security-Center (gesperrt ohne MANAGE_USERS). `secureguard_roles.xml` ist aus
Cloud-Backup/Device-Transfer ausgeschlossen.

**Gesamtstand:** 76 Befundpositionen bearbeitet – **75 behoben/wire-ready**, 1 Phase-2
(F-45 i18n-Deep-Screens), F-56 System-Broadcast ohne Handlungsbedarf.
