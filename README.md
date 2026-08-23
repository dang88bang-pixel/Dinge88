# 🛡️ SecureGuard Enterprise

Schutz- und Wiederbeschaffungs-System für mobile Werte (E-Scooter, Fahrräder,
Schlüsselfinder, Tablets etc.) – **ohne Meshtastic-Abhängigkeit**. Die App
nutzt einen Mix aus **BLE, WiFi, generischem LoRa/LoRaWAN, optischer Erkennung,
urbaner Infrastruktur, Apple/Google-Crowdsourcing und Satellit**, orchestriert
von einem **selbstlernenden Agenten**.

> Projekt-Titel im Repository: *Dinge88 – Agent „Le Guck"*.

## ✨ Features

| Bereich | Beschreibung |
|--------|--------------|
| 📱 **UI** | Jetpack Compose, Material 3, 5 Haupt-Tabs (Dashboard, Assets, Karte, Aktionen, Einstellungen) |
| 🗺️ **Karte** | OpenStreetMap (OSMDroid) mit farbcodierten Markern, Legende und Offline-Kacheln |
| 📦 **Assets** | Whitelist, Suche/Filter, Detail mit Telemetrie, Historie & Aktionen |
| 📡 **Kanäle** | BLE, WiFi, LoRa/LoRaWAN (generisch), Optik, Urban, Crowd, Satellit, Telemetrie |
| 🔌 **Externe APIs** | WiGle.net (BSSID→GPS), MacLookup (OUI), Open Charge Map, DHL, CKAN, Google Geolocation, Netatmo, Helium – zentral über `ApiServiceManager` |
| 🔗 **Echtzeit** | MQTT (Paho) und WebSocket (OkHttp) für Telemetrie/Alerts/Befehle |
| 🧠 **Agent** | Selbstlernend (LearningEngine: Muster, Prädiktion, adaptives Intervall), priorisiert erfolgreiche Kanäle |
| 🔔 **Alarme** | Audit-Log, Push-Benachrichtigungen (4 Kanäle), Alarm-Töne pro Stufe, Command-Log |
| 🔒 **Sicherheit** | PIN-Sperre (PBKDF2), AES/GCM-Verschlüsselung (AndroidKeyStore), RBAC-Rollenmodell (ADMIN/MANAGER/OPERATOR/VIEWER, wirksam geprüft bei Aktionen & Löschen) |
| 💾 **Daten** | Room mit Migration v2, tägliche Retention-Bereinigung (MaintenanceWorker), Backup/Restore (zweiphasig), Offline-Queue mit Netzwerk-/MQTT-Nachlieferung, CSV/PDF-Export (auch verschlüsselt), DB-Paginierung (Paging 3) |
| 📷 **QR-Scan** | Integrierter Scanner (ZXing) zum Anlernen neuer Assets |
| 📟 **Hardware** | NFC-Tags, USB/Serial (usb-serial-for-android), ESP32-Firmware (`firmware/`) |

## 🏗️ Architektur

```
app/src/main/java/com/secureguard/enterprise/
├── data/
│   ├── model/           # Asset, Detection, Alert, AuditLog, PendingAction, Telemetry, Enums
│   ├── local/           # Room-Datenbank (v2 + Migration), DAOs, TypeConverter
│   └── repository/      # SecureGuardRepository (Single Source of Truth)
├── di/                  # Hilt-Module
├── security/            # RoleManager (RBAC: Rollen/Permissions)
├── services/            # LoraService, BleService, WifiService, TelemetryService,
│                        # OpticalService, UrbanService, CrowdService, SatelliteService,
│                        # AgentService, ApiServiceManager, MqttService, WebSocketService,
│                        # LearningEngine, AuthManager, EncryptionService, AuditLogService,
│                        # OfflineQueue, BackupManager, DatabaseCleanup, ExportService,
│                        # AlertSoundManager, OfflineMapService, NfcService, UsbSerialService
│   └── apis/            # WiGleApi, MacLookupApi, OpenChargeMapApi, DhlPackstationApi,
│                        # CkanOpenDataApi, GoogleGeolocationApi, NetatmoWeatherApi, HeliumNetworkApi
├── util/                # ErrorHandler, RetryManager, CacheManager, AssetPagingSource, AccessibilityHelper
├── worker/              # SecureAgentWorker (15-Min-Suchzyklus), MaintenanceWorker (tägliche Bereinigung)
├── receiver/            # BootCompletedReceiver (Worker/Agent nach Reboot wieder aufnehmen)
├── presentation/
│   ├── components/      # AssetCard, StatCard, ActionButton
│   ├── navigation/      # NavHost + Bottom-Navigation
│   ├── theme/           # Material 3 Theme (Light/Dark/Dynamic)
│   └── ui/              # Dashboard, Assets, Map, Actions, Agent, Settings, Alerts, Auth (LockScreen)
├── MainActivity.kt      # + PIN-Sperre (AuthManager) + NFC-Verarbeitung
└── SecureGuardApplication.kt  # Hilt, WorkManager-Scheduling
```

## 🔌 Externe APIs & Echtzeit-Kanäle

| API | Datei | Funktion |
|-----|-------|----------|
| WiGle.net | `services/apis/WiGleApi.kt` | BSSID → GPS |
| MacLookup.app | `services/apis/MacLookupApi.kt` | OUI-Auflösung (Hersteller) |
| Open Charge Map | `services/apis/OpenChargeMapApi.kt` | Ladesäulen (auch RxJava) |
| DHL Packstation | `services/apis/DhlPackstationApi.kt` | Paketstationen |
| CKAN Open Data | `services/apis/CkanOpenDataApi.kt` | Smart-City-Datensätze (eigene Instanz via `OPEN_DATA_URL`) |
| Google Geolocation | `services/apis/GoogleGeolocationApi.kt` | WLAN-Triangulation |
| Netatmo Weather | `services/apis/NetatmoWeatherApi.kt` | Wetterstationen |
| Helium Network | `services/apis/HeliumNetworkApi.kt` | LoRaWAN-Hotspots |
| MQTT (Paho) | `services/MqttService.kt` + `MqttConfig.kt` | Echtzeit-Pub/Sub |
| WebSocket (OkHttp) | `services/WebSocketService.kt` | Echtzeit-Updates |

API-Keys werden über `local.properties` (Vorlage: `local.properties.example`) bzw.
`gradle.properties` gesetzt und landen als `BuildConfig`-Felder
(`WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`,
`HELIUM_API_KEY`, `MQTT_BROKER_URL`, `WEBSOCKET_URL`, `MCP_SERVER_URL`,
`LORA_BACKEND_URL`, `YOLO_SERVER_URL`, `URBAN_SIGHTINGS_URL`,
`FIND_MY_PROXY_URL`, `OPEN_DATA_URL`). Leere Werte deaktivieren den jeweiligen
Kanal ehrlich (keine Treffer, keine Demo-Daten).

## 🖥️ Backend & Firmware

- `backend/` – FastAPI-Backend (Assets, Detektionen, Alerts, Befehle, WebSocket, MQTT-Publish)
- `docker-compose.yml` – Mosquitto (MQTT) + Backend + Node-RED
- `firmware/secureguard_esp32/` – ESP32-Gateway (LoRa + BLE + MQTT)
- `docs/api-docs.yaml` – OpenAPI-Spezifikation des Backends

Der `LoraService` fragt echte LoRaWAN-Uplinks ab: das mitgelieferte FastAPI-Backend
sammelt LoRa-Pakete der Gateways über MQTT, die App liest sie per
`GET /api/detections?mac=…&source_type=LORA` (`LORA_BACKEND_URL`). Befehle gehen
als echtes MQTT-Publish auf `secureguard/<MAC>/command` – exakt das Topic, das die
ESP32-Firmware abonniert. Ohne konfiguriertes Backend liefert der Kanal ehrlich
„kein Treffer“ (keine Simulation).

## 🔨 Build

Die APK wird über **GitHub Actions** gebaut (kein lokal eingerichteter
Wrapper nötig):

1. Code pushen oder Pull Request öffnen.
2. Der Workflow `.github/workflows/build-release.yml` lädt JDK 17, das
   Android-SDK (API 35) und Gradle 8.9 und führt `assembleDebug` plus
   `assembleRelease` aus.
3. Die Debug-APK steht als Artefakt **secureguard-pro-debug**, die
   Release-APK als **secureguard-pro** zur Verfügung.

Lokal bauen (sofern Android SDK + JDK 17 vorhanden):

```bash
# einmalig den Gradle Wrapper erzeugen (benötigt eine installierte Gradle-Distribution)
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

## ⚙️ Konfiguration

- **minSdk:** 26 (Android 8) · **targetSdk/compileSdk:** 35
- **Sprache:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12)
- **Build:** AGP 8.7.3 · Gradle 8.9 · JDK 17
- **DI:** Hilt 2.52 · **DB:** Room 2.6.1
- **Karte:** OSMDroid 6.1.20 · **Scanner:** ZXing 4.3.0
- **Netz:** Retrofit 2.9 / OkHttp 4.12 / Moshi 1.15 / MQTT Paho 1.2.5

## 📟 Honeywell CT45P XON (Android 11)

Das Projekt ist für das **Honeywell CT45P XON** ausgelegt, das werkseitig mit
**Android 11 (API 30)** läuft. Die App berücksichtigt dessen Besonderheiten:

| Bereich | Umsetzung |
|---------|-----------|
| **MQTT (tcp://)** | `android:usesCleartextTraffic="true"` im Manifest – Android 9+ blockiert Klartext sonst (auch auf Android 11) |
| **BLE-Scan** | Auf API ≤ 30 wird `ACCESS_FINE_LOCATION` statt `BLUETOOTH_SCAN` verlangt (`CT45PConfig.needsLocationForBle`, `BleService`) |
| **WiFi-Scan** | Benötigt Standortberechtigung auf Android 11 (in `WifiService` berücksichtigt) |
| **Geräte-Erkennung** | `config/CT45PConfig.kt` erkennt das CT45P (`Build.MODEL`/`MANUFACTURER`) und loggt Gerät + Android-Version beim Start |
| **Barcode-Scanner** | Integrierter 2D-Imager (HID-Keyboard) + ZXing-Kamera-Scan als Alternative |
| **USB-Host** | Serielle Anbindung (FTDI/CP210x) via `UsbSerialService` |
| **Benachrichtigungen** | `POST_NOTIFICATIONS` nur ab API 33 angefordert – auf Android 11 ohne Einschränkung |

Das Gerät läuft mit `targetSdk 35`, ohne dass Android-11-spezifische
Einschränkungen greifen (Klartext-Netzwerk, Scoped Storage, Hintergrund-
Dienste sind berücksichtigt).

## 📲 Installation auf dem CT45P

1. **Debug-APK** aus GitHub Actions laden: Workflow „🛡️ Build SecureGuard APK" →
   Artefakt **secureguard-pro-debug** (debug-signiert, direkt installierbar).
2. APK per USB/ADB oder Dateimanager auf das Gerät übertragen:
   `adb install secureguard-pro-debug.apk`
3. Beim ersten Start Berechtigungen erteilen: **Standort**, **Bluetooth**,
   **Kamera** (für QR), **Benachrichtigungen** (nur Android 13+).
4. Für den Pilotbetrieb: unter **Einstellungen → Backend-Endpunkte** die
   URLs für LoRa/Optik/Urban/Crowd setzen und ggf. `MQTT_BROKER_URL` bzw.
   `MCP_SERVER_URL` in `local.properties` konfigurieren.

> Die **Release-APK** wird nur signiert, wenn die Keystore-Secrets
> (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) im
> GitHub-Repo gesetzt sind – siehe `.github/workflows/build-release.yml`.

## 🛡️ Datenschutz

Alle Ortungsdaten und der Audit-Log verbleiben in der lokalen Room-Datenbank.
Externe Crowd- und Satelliten-Kanäle sind standardmäßig deaktiviert und werden
erst nach ausdrücklicher Einwilligung (in den Einstellungen) genutzt.

## 📄 Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE).
