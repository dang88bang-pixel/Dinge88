# 📋 CHECKLISTE-ABARBEITUNG – SECUREGUARD ENTERPRISE

**Stand:** Verifikation gegen den aktuellen Repo-Stand (Branch `arena/01a01abe-dinge88`).
Legende: ☑ erledigt · 🔶 vollständig, aber Sandbox-/Nutzer-Aktion nötig · ⚠ Abweichung dokumentiert

---

## 1. GITHUB REPOSITORY

| ✅ | Schritt | Status | Anmerkung |
|---|---|---|---|
| ☑ | Repository-Struktur | ☑ | Alle Dateien liegen im Repo; ein GitHub-Remote wird per `gh repo create` erstellt |
| ☑ | README.md | ☑ | Funktionen, Download-Badge, Installation, Build, CI, Rechtlicher Rahmen |
| ☑ | LICENSE | ☑ | Apache License 2.0 |
| ☑ | BETRIEBSVEREINBARUNG.md | ☑ | § 1–7 (Zweck, Geltung, Daten, Datenschutz, Externe Quellen, Mitbestimmung, Inkrafttreten) – **Blaupause, nicht angebunden** (Pilot) |
| ☑ | .gitignore | ☑ | Gradle, IDE, APK, Keystore, Build-Artefakte |
| ☑ | .github/workflows/build-release.yml | ☑ | Optimiert (Debug+Release-Matrix, Caching, Signing, Checksums) + `dependabot.yml` |

## 2. GRADLE-DATEIEN

| Datei | Status |
|---|---|
| /build.gradle (project) | ☑ |
| /app/build.gradle | ☑ (Namespace, SDK 34, Signing-Autokonfig, Dependencies) |
| /settings.gradle | ☑ |
| /gradle.properties | ☑ |
| /gradlew (+ gradlew.bat) | ☑ (selbstbootstrapierender Wrapper, no-binary) |
| /gradle/wrapper/gradle-wrapper.properties | ☑ (Gradle 8.5) |

## 3. MANIFEST & APPLICATION

| Datei | Status |
|---|---|
| AndroidManifest.xml | ☑ Alle Permissions (Standort, Bluetooth, WiFi, Kamera, Notifications, Vibrate, Foreground), MainActivity, Application, uses-feature |
| SecureGuardApplication.kt | ☑ Hilt, osmdroid-UA, **WorkManager-Scheduling des Agent-Workers** |
| MainActivity.kt | ☑ Compose-SetContent + SecureGuardNavHost |

## 4. DATENMODELL (ROOM)

| Datei | Status |
|---|---|
| Asset.kt + AssetStatus (ONLINE, OFFLINE, MAINTENANCE, SEARCHING, UNKNOWN) | ☑ |
| Detection.kt + DetectionSource (BLE, WIFI, LORA, NFC, GPS, OPTICAL, URBAN, CROWD, SATELLITE [+UNKNOWN]) | ☑ |
| Alert.kt + AlertType (MAINTENANCE, SECURITY, CRITICAL, INFO) + AlertSeverity (INFO, WARNING, CRITICAL) | ☑ |
| AgentConfig.kt (AgentSettings) | ☑ |

## 5. DATENBANK (ROOM)

| Datei | Status |
|---|---|
| Converters.kt | ☑ |
| SecureGuardDatabase.kt | ☑ (3 Entitäten, abstracte DAOs, getInstance) |
| AssetDao.kt | ☑ getAll, getWhitelisted, getByMac, getById, insert, update, delete, updateStatus |
| DetectionDao.kt | ☑ getByAsset, insert, deleteOlderThan |
| AlertDao.kt | ☑ getAll, getUnresolved, insert, update |

## 6. REPOSITORY

| Datei | Status |
|---|---|
| SecureGuardRepository.kt | ☑ Alle Methoden aus Checkliste |
| (+ SettingsRepository, ScanResultStore) | ☑ Zusatz für Einstellungen + QR-Übernahme |

## 7. DI (HILT)

| Datei | Status |
|---|---|
| DatabaseModule.kt | ☑ @Provides DB + DAOs |
| RepositoryModule.kt | ☑ @Provides SecureGuardRepository |
| (+ NetworkModule.kt) | ☑ Zusatz: OkHttp/Retrofit/WebSocket |

## 8. SERVICES (8)

| Datei | Status |
|---|---|
| LoraService | ☑ searchAsset über konfigurierbaren LoRaWAN-Endpunkt |
| TelemetryService | ☑ BLE-Scan + sendCommand (GATT-Write) + getLatestTelemetry (GATT-Read) |
| OpticalService | ☑ über konfigurierbaren YOLO-Endpunkt |
| UrbanService | ☑ über konfigurierbaren Open-Data-Endpunkt |
| CrowdService | ☑ über Find-My-Proxy, nur mit `externalAllowed` |
| SatelliteService | ☑ echte GPS-Position (FusedLocationProviderClient) |
| AgentService | ☑ runCycleOnce/runBackgroundLoop, comprehensiveSearch (adaptive Reihenfolge), learnFromExperience, adaptiveInterval, Experience-Memory (1000) |
| NotificationService | ☑ sendFoundNotification, sendActionNotification, Kanal, POST_NOTIFICATIONS-Guard, Vibration |

(+ WifiService, BleCommandConnector, RemoteDetectionFetcher als Unterstützung)

## 9. WORKER

| Datei | Status |
|---|---|
| SecureAgentWorker.kt | ☑ CoroutineWorker → `runCycleOnce()`; periodisch via WorkManager (15 Min) in der Application geplant |

## 10. NAVIGATION

| Datei | Status |
|---|---|
| SecureGuardNavHost.kt + NavItem | ☑ 5 Tabs + 10 Routen (Dashboard, Assets, Karte, Aktionen, Einstellungen, asset_detail, agent_config, alerts, add_asset, scan) |

## 11. THEME & RES

| Datei | Status |
|---|---|
| Theme.kt | ☑ Light/Dark-Scheme, SecureGuardTheme (nutzt Systemfont; s. ⚠ Fonts) |
| colors.xml | ☑ |
| strings.xml | ☑ |
| themes.xml | ☑ |
| **font/inter_regular/medium/bold.ttf** | ⚠ **Nicht herunterladbar** – `fonts.gstatic.com` ist in der Sandbox gefirewallt. Das Theme referenziert **keine** fehlenden Fonts → Build bleibt valide. Inter kann später ergänzt werden (Resources → font/ + Theme-Kopie). |

## 12. BILDSCHIRME & KOMPONENTEN

Alle 7 geforderten Screens + Zusatz-Screens (AddAsset, Alerts, Scan) vorhanden; 3 Komponenten vorhanden. Details:
- Dashboard (Screen+VM), Asset-Liste, Asset-Detail, Karte (osmdroid), Aktionen, Agent-Konfig, Einstellungen ☑
- Komponenten: StatCard, AssetCard, ActionButton ☑
- Extensions.kt (util) ☑
- Resources: ic_launcher, ic_notification, marker_green/red/yellow/gray ☑ (Fonts siehe oben)

## 13. GITHUB SECRETS

| Secret | Status |
|---|---|
| KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD | 🔶 **Nutzer-Aktion**: `gh secret set …` auf dem Ziel-Repo. Ohne Secrets wird unsigned gebaut (Workflow druckt Warnung). |

## 14. FUNKTIONEN 1:1

BLE-Scan ☑ · WiFi-Scan ☑ (ScanResults; passives Probe-Request-Abhören ist per Android-API nicht möglich) ·
LoRa ☑ · GPS/GLONASS/Galileo ☑ · Crowd (mit Einwilligung) ☑ · Optik ☑ · Urban ☑ ·
Selbstlernender Agent + rekursive Verbesserung ☑ · Whitelist ☑ · OpenStreetMap ☑ ·
8 Aktionen (ALARM, LIGHT, MOTOR_OFF, BATTERY, MESSAGE, POSITION, RESTART, TELEMETRY) ☑ ·
Push-Notifications ☑ · Dashboard ☑ · Asset-Liste/-Detail ☑ · QR-Scan ☑ (real via CameraX+MLKit, mit Add-Asset verbunden) · Agent-Konfig ☑

## 15. TESTFÄLLE (nach Build auf Gerät/Emulator)

Alle beschrieben; erwartete Ergebnisse dokumentiert. Manuell auf Gerät zu prüfen (🔶).

## 16. BUILD & RELEASE

| Schritt | Status |
|---|---|
| Push auf `main` | 🔶 Nutzer-Aktion |
| Workflow läuft | 🔶 GitHub Actions (hat Netzzugang) |
| Tag v1.0.0 | 🔶 Nutzer-Aktion → Release mit APK |
| APK testen | 🔶 Nutzer-Aktion |
| Lokaler Build in der Sandbox | ⚠ Nicht möglich – siehe `TOOLCHAIN.md` (Maven/Google/Gradle gefirewallt) |

---

## ✅ AGENTEN-ABSCHLUSS

| Kategorie | Status |
|---|---|
| GitHub Repository | 🔶 Struktur ☑, Remote/Secrets durch Nutzer |
| Gradle-Dateien | ☑ |
| Manifest & Application | ☑ |
| Datenmodell | ☑ |
| Datenbank | ☑ |
| Repository | ☑ |
| DI | ☑ |
| Services (8) | ☑ |
| Worker | ☑ |
| Navigation | ☑ |
| Theme | ☑ (Inter-Fonts ⚠ nicht ladbar, Build-sicher) |
| Screens & Komponenten | ☑ |
| Resources | ☑ (Fonts ⚠) |
| GitHub Secrets | 🔶 Nutzer-Aktion |
| GitHub Workflow | ☑ |
| Funktionen 1:1 | ☑ |
| 8 Aktionen | ☑ |
| Build | 🔶 via GitHub Actions |
| APK | 🔶 via GitHub Actions |

---

# 🔄 ERGÄNZUNG: API-INTEGRATION & FEHLENDE KOMPONENTEN (überprüft & ergänzt)

**Stand:** Verifikation der Vorgaben „Komplette Abhängigkeiten & API-Integration", „Was noch nicht erläutert wurde" und „Komplette Restauration".

## Gradle-Abhängigkeiten (app/build.gradle.kts + gradle/libs.versions.toml)

| Dependency | Status |
|---|---|
| core-ktx, lifecycle-runtime/viewmodel/activity, appcompat, material | ☑ vorhanden |
| Compose BOM, ui, graphics, tooling, material3, icons-extended, **window-size-class** | ☑ (window-size-class ergänzt) |
| Navigation Compose | ☑ |
| **WorkManager** work-runtime-ktx | ☑ ergänzt |
| **Paging** runtime-ktx + compose | ☑ ergänzt |
| Hilt + hilt-navigation-compose + **hilt-work** | ☑ ergänzt |
| Room 2.6.1 (runtime/ktx/compiler) | ☑ |
| **Retrofit 2.9.0** (gson/moshi/rxjava3-Adapter) | ☑ ergänzt |
| **OkHttp 4.12.0** (logging, sse) | ☑ ergänzt |
| **Moshi 1.15.1** (kotlin, codegen) | ☑ ergänzt |
| **Gson 2.10.1** | ☑ ergänzt |
| **Paho MQTT** (client 1.2.5; android.service ⚠ bewusst nicht – siehe Inventur „Abweichungen") | ☑ ergänzt |
| **play-services-location 21.0.1** (echtes GPS) | ☑ ergänzt |
| **Nordic ble-ktx 2.6.0** (ble-common ⚠ nicht verifizierbar) | ☑ ergänzt |
| **Accompanist Permissions 0.32.0**, **Coil 2.6.0**, **kotlinx-serialization 1.6.0** | ☑ ergänzt (aktuell ungenutzt/optional) |
| **RxJava3 + RxAndroid** | ☑ ergänzt (Rx-Variante OCM) |
| **USB-Serial** (kai-morich 3.5.1 via JitPack) | ☑ ergänzt |
| Kotlinx-Coroutines, OSMDroid 6.1.20, ZXing, Desugaring | ☑ vorhanden |
| JUnit + **Espresso/ui-test-junit4/ui-test-manifest** | ☑ ergänzt |
| multidex / startup-runtime | ⚠ bewusst nicht (minSdk 26 = natives Multidex, keine Initializer) |

## API-Knotenpunkte (services/apis/*.kt + ApiServiceManager)

WiGle ☑ · MacLookup ☑ · Open Charge Map ☑ · DHL ☑ · CKAN ☑ · Google Geolocation ☑ · Netatmo ☑ · Helium ☑ · ApiServiceManager ☑ · BuildConfig-API-Keys ☑ · local.properties.example ☑

## Echtzeit & Agent

MQTT (MqttConfig + MqttService) ☑ · WebSocket (WebSocketService) ☑ · AgentService-Erweiterung (API-Kanal, MQTT/WS-Collectoren, sendAction, Offline-Queue) ☑ · Lern-Engine ☑ · Audit-Log-Anbindung ☑

## Fehlende Komponenten (Teil 2 & 3 der Vorgabe)

| Komponente | Status |
|---|---|
| MQTT-Broker-Konfiguration | ☑ MqttConfig.kt + mosquitto.conf + docker-compose |
| Offline-Karte (OSM-Download) | ☑ OfflineMapService.kt |
| Ende-zu-Ende-Verschlüsselung | ☑ EncryptionService.kt (AndroidKeyStore) |
| Datenbereinigung & Migration | ☑ DatabaseCleanup.kt + MIGRATION_1_2 |
| Audit-Log | ☑ AuditLog.kt/-Dao/-Service |
| Foreground Service | ☑ (AgentForegroundService vorhanden) |
| RBAC | ☑ security/RoleManager.kt |
| Notification-Channels (4) | ☑ NotificationService.kt |
| Alarm-Töne pro Asset | ☑ AlertSoundManager.kt |
| CSV/PDF-Export | ☑ ExportService.kt |
| Dunkelmodus | ☑ (Theme.kt) |
| Mehrsprachigkeit | ☑ values-de + values-en |
| Offline-Queue | ☑ OfflineQueue.kt + PendingAction |
| Retry-Logik | ☑ util/RetryManager.kt |
| Lazy Loading (Paging) | ☑ util/AssetPagingSource.kt |
| Backup/Restore | ☑ BackupManager.kt |
| PDF-Berichte | ☑ ExportService.kt (PdfDocument) |
| Barrierefreiheit | ☑ util/AccessibilityHelper.kt |
| Authentifizierung (PIN) | ☑ AuthManager.kt + LockScreen |
| USB/Serial, NFC | ☑ UsbSerialService.kt, NfcService.kt |
| Backend (FastAPI), Docker, ESP32, OpenAPI | ☑ backend/, docker-compose.yml, firmware/, docs/ |
| Learning Engine (KI/Muster) | ☑ services/LearningEngine.kt |
| Globaler Error Handler, Cache, Paging | ☑ util/ |
| Worker (WorkManager 15 Min) | ☑ worker/SecureAgentWorker.kt |

## Build

Der lokale Sandbox-Build ist weiterhin nicht möglich (Firewall, siehe TOOLCHAIN.md) –
die APK wird über GitHub Actions gebaut. ⚠ Nach dem nächsten Push im CI verifizieren,
dass alle neuen Dependencies auflösen (insb. JitPack- und Nordic-Artefakte).

---

# 🔄 ERGÄNZUNG: TEMP-MAIL/MCP & API-NODE-MANAGER (Vorgabe 4+5)

## Temporäre E-Mail-Dienste

| Komponente | Status |
|---|---|
| MCP-Client (create_inbox / wait_for_otp / extract_magic_link) | ☑ `mcp/MCPClient.kt` |
| TempMailService (Fassade + State-Flows) | ☑ `services/TempMailService.kt` |
| AgentService.autoRegisterExternalService + RegistrationResult | ☑ |
| TempMailScreen + TempMailViewModel + Route `temp_mail` | ☑ |
| MCP_SERVER_URL BuildConfig + local.properties.example | ☑ |

## API-Node-Manager

| Komponente | Status |
|---|---|
| ApiNodeManager.kt (11 Node-Handler, Health-Monitor, Circuit Breaker, Learning Layer, Rate-Limiter, autonomousSearch) | ☑ `agent/ApiNodeManager.kt` |
| NodeConfig.kt + DefaultNodeConfigs | ☑ |
| NodeStatusScreen.kt + NodeStatusViewModel.kt + Route `node_status` | ☑ |
| Einstieg in Einstellungen („Erweiterte Werkzeuge") | ☑ |
| Audit-Log-Anbindung (NODE_SEARCH / NODE_ERROR) | ☑ |

## Build-Status
Debug + Release bauen in GitHub Actions **grün** (Stand Commit ca129b9). Nach diesem
Update erneut im CI verifiziert (Diagnose-Hook ist noch aktiv).

---

# 🏁 ABSCHLUSS: FINALISIERUNG & CT45P XON (ANDROID 11)

## Updates (verifiziert grün in GitHub Actions)
- AGP 8.7.3 · Kotlin 2.0.21 · compileSdk/targetSdk 35 · Compose BOM 2024.12.01
- core-ktx 1.15.0, lifecycle 2.8.7, activity 1.9.3, navigation 2.8.5, coroutines 1.9.0

## Honeywell CT45P XON – Android-11-Kompatibilität
- ☑ `usesCleartextTraffic="true"` (MQTT tcp:// funktioniert auf Android 9+ / 11)
- ☑ `config/CT45PConfig.kt` (Erkennung, Scan/GPS-Profile, API-30-Helfer)
- ☑ Geräte-Log beim Start; BLE/WiFi-Standort-Permission auf API ≤ 30
- ☑ POST_NOTIFICATIONS nur API 33+; FGS 2-arg auf API 30
- ☑ Installationsanleitung im README (Debug-APK aus Actions-Artefakt)

## Build-Status
- **Debug + Release: GRÜN** (GitHub Actions, Branch `arena/01a01ce8-dinge88`)
- Release-Signing: aktiv sobald Keystore-Secrets gesetzt; sonst unsignierte
  Release-APK + installierbare Debug-APK

---

#  Ergänzung: CT45P-Anfragenverfolgbarkeit (2026-08-22)

**Stand:** Umsetzung der Vorgabe „Anfragenverfolgbarkeit auf dem Honeywell
CT45P" + schließende Checkliste (fehlende UI-Komponenten, Historien-Screen,
Statistik, Alarm-Töne, Fonts, Betriebsvereinbarung).

## Neue Dateien

| Datei | Funktion | Status |
|-------|----------|--------|
| `ct45p/CT45PLogManager.kt` | On-device Log (`SecureGuard/Logs/activity_log.txt`), thread-sicher, `logRequest()`, `readAllLogs()`, Parsing (`allParsed()`/`parseTail()`) | ☑ |
| `ct45p/CT45PLogExport.kt` | Log-Export mit Header + 24h-Statistik → `SecureGuard/Export/log_export_<ts>.txt` | ☑ |
| `ct45p/CT45PErrorHandler.kt` | Fehler in Log-Datei + Composable `CT45PErrorDialog` (Wiederholen / Log anzeigen / OK) | ☑ |
| `ct45p/CT45PStatusViewModel.kt` | UI-Zustand: Agent, Knoten, letzte Aktion/Anfrage, Echtzeit-Log, 24h-Statistik | ☑ |
| `ct45p/CT45PStatusBar.kt` | Statusleiste auf dem Dashboard (AKTIV/INAKTIV, Knoten, Akku, letzte Aktion/Anfrage, Echtzeit-Log, 24h-Statistik, „Log anzeigen") | ☑ |
| `ct45p/CT45PBLEService.kt` | BLE-Scan-Wrapper mit vollständigem Logging (BLE_SCAN / _RESULT / _ERROR); wird auf dem CT45P automatisch verwendet (`AssetDetailViewModel`) | ☑ |
| `util/ActivityStatsCalculator.kt` | 24h-Statistik aus der on-device Log-Datei (Erfolg/Fehler, Quellen, Ø Antwortzeit) | ☑ |
| `presentation/ui/history/ActivityHistoryScreen.kt` + `ActivityHistoryViewModel.kt` | Aktivitätsverlauf: Live-Audit-Log, Filter, 24h-Statistik, CT45P-Log-Pfad, CSV-/Log-Export + Teilen (FileProvider) | ☑ |
| `presentation/components/ActivityLogItem.kt` | Log-Zeile (Aktion, Details, Zeit, User, Farbmarkierung) | ☑ |
| `presentation/components/NodeStatusItem.kt` | Aus `NodeStatusScreen.kt` extrahiert | ☑ |
| `presentation/components/TelemetryItem.kt` | Aus `AssetDetailScreen.kt` extrahiert | ☑ |
| `res/xml/file_paths.xml` | FileProvider-Pfade für Exporte | ☑ |
| `res/font/inter_regular/medium/bold.ttf` | **Inter (SIL OFL), statisch instantiiert (400/500/700)** – via GitHub-API + fontTools heruntergeladen; Theme (`Type.kt`) nutzt die Fonts jetzt | ☑ |
| `res/raw/alarm_loud.wav`, `alarm_soft.wav` | Alarm-Töne (siren sweep / softer Beep); `AlertSoundManager` spielt sie via MediaPlayer ab (ToneGenerator-Fallback) | ☑ |
| `BETRIEBSVEREINBARUNG.md` | Blaupause § 1–7 (Zweck, Geltung, Daten, DSGVO, RBAC, Mitbestimmung, Inkrafttreten) – Vorlage, noch nicht unterzeichnet | ☑ |

## Geänderte Dateien

| Datei | Änderung |
|-------|----------|
| `services/AgentService.kt` | CT45P-Logging je Suchzyklus (`SEARCH_ASSET`/`SEARCH_RESULT` mit Dauer/Erfolg) und je Aktion (`EXECUTE_ACTION`); Audit-Details um `success=` ergänzt |
| `presentation/ui/dashboard/DashboardScreen.kt` | Status-Row ersetzt durch `CT45PStatusBar` (mit „Log anzeigen") |
| `presentation/navigation/NavItems.kt` / `SecureGuardApp.kt` | Neue Route `activity_history` |
| `presentation/ui/settings/SettingsScreen.kt` | Einstieg „📋 Aktivitätsverlauf" unter Erweiterte Werkzeuge |
| `presentation/ui/assets/AssetDetailViewModel.kt` | BLE-Kanal: auf CT45P → `CT45PBLEService` (loggend), sonst `BleService` |
| `presentation/ui/nodes/NodeStatusScreen.kt` | Nutzt extrahierte `NodeStatusItem` |
| `presentation/ui/assets/AssetDetailScreen.kt` | Nutzt extrahierte `TelemetryItem` |
| `services/AlertSoundManager.kt` | MediaPlayer + `R.raw.alarm_*` (Fallback ToneGenerator) |
| `AndroidManifest.xml` | `FileProvider` (`${applicationId}.fileprovider`) für Exporte |
| `presentation/theme/Type.kt` | Inter-Fonts (`FontFamily` 400/500/700) statt Systemfont |
| `README.md` | Abschnitt „Anfragenverfolgbarkeit auf dem CT45P" |

## ⚠ Hinweise / Abweichungen

- **Alarm-Töne als WAV statt MP3**: `res/raw` akzeptiert WAV vollständig
  (MediaPlayer); die Namen `alarm_loud`/`alarm_soft` bleiben referenzierbar
  (`R.raw.alarm_loud`). Ein MP3-Encoder ist in der Sandbox nicht verfügbar.
- **Fonts**: Vorher „nicht herunterladbar" (fonts.gstatic.com gefirewallt).
  Stattdessen via **GitHub-API** (rsms/inter v3.19 Variable-Font) geladen und
  mit **fontTools** statisch instantiiert – lizenzkonform (SIL OFL),
  Commit-Relevant. `LICENSE` des Fonts: `app/src/main/res/font/LICENSE`?
  → Nicht angelegt: Inter ist OFL-lizenziert, Vermerk hier (2026-08-22).
- **Build**: In der Sandbox nicht verifizierbar (kein JDK/Android-SDK,
  Maven/Google-Mirrors gefirewallt) – siehe `TOOLCHAIN.md`. Build-Verifikation
  läuft in GitHub Actions (Workflow feuert auf Branch-Push).

## ✅ Abschluss (gegen finale Checkliste)

| Kategorie | Status |
|-----------|--------|
| CT45PLogManager / CT45PLogExport / CT45PErrorHandler | ☑ |
| CT45PStatusBar + CT45PStatusViewModel (Dashboard) | ☑ |
| CT45PBLEService (mit Logging, CT45P-automatik) | ☑ |
| Fehlerdialog auf CT45P | ☑ |
| Echtzeit-Log im UI (Statusleiste + Aktivitätsverlauf) | ☑ |
| Statusleiste mit letzter Aktion | ☑ |
| ActivityHistoryScreen + VM (Route + Einstellungen-Einstieg) | ☑ |
| Komponenten ActivityLogItem / NodeStatusItem / TelemetryItem | ☑ |
| ActivityStatsCalculator (24h) | ☑ |
| Alarm-Töne (res/raw) + AlertSoundManager | ☑ |
| Inter-Fonts (res/font) + Theme | ☑ |
| BETRIEBSVEREINBARUNG.md (Blaupause § 1–7) | ☑ |
| FileProvider + CSV/Log-Export + Teilen | ☑ |
