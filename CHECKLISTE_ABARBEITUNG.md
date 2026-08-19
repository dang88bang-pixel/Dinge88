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
