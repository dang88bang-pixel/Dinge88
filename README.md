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
| 🔒 **Sicherheit** | PIN-Sperre (PBKDF2), AES/GCM-Verschlüsselung (AndroidKeyStore), RBAC-Rollenmodell |
| 💾 **Daten** | Room mit Migration v2, Retention-Bereinigung, Backup/Restore, Offline-Queue, CSV/PDF-Export |
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
├── worker/              # SecureAgentWorker (WorkManager, 15-Min-Takt)
├── presentation/
│   ├── components/      # AssetCard, StatCard, ActionButton
│   ├── navigation/      # NavHost + Bottom-Navigation
│   ├── theme/           # Material 3 Theme (Light/Dark/Dynamic)
│   └── ui/              # Dashboard, Assets, Map, Actions, Agent, Settings, Alerts, Auth (LockScreen)
├── MainActivity.kt      # + PIN-Sperre (AuthManager) + NFC-Verarbeitung
└── SecureGuardApplication.kt  # Hilt, WorkManager-Scheduling, Demo-Seed
```

## 🔌 Externe APIs & Echtzeit-Kanäle

| API | Datei | Funktion |
|-----|-------|----------|
| WiGle.net | `services/apis/WiGleApi.kt` | BSSID → GPS |
| MacLookup.app | `services/apis/MacLookupApi.kt` | OUI-Auflösung (Hersteller) |
| Open Charge Map | `services/apis/OpenChargeMapApi.kt` | Ladesäulen (auch RxJava) |
| DHL Packstation | `services/apis/DhlPackstationApi.kt` | Paketstationen |
| CKAN Open Data | `services/apis/CkanOpenDataApi.kt` | Smart-City-Datensätze |
| Google Geolocation | `services/apis/GoogleGeolocationApi.kt` | WLAN-Triangulation |
| Netatmo Weather | `services/apis/NetatmoWeatherApi.kt` | Wetterstationen |
| Helium Network | `services/apis/HeliumNetworkApi.kt` | LoRaWAN-Hotspots |
| MQTT (Paho) | `services/MqttService.kt` + `MqttConfig.kt` | Echtzeit-Pub/Sub |
| WebSocket (OkHttp) | `services/WebSocketService.kt` | Echtzeit-Updates |

API-Keys werden über `local.properties` (Vorlage: `local.properties.example`) bzw.
`gradle.properties` gesetzt und landen als `BuildConfig`-Felder
(`WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`,
`HELIUM_API_KEY`, `MQTT_BROKER_URL`, `WEBSOCKET_URL`).

## 🖥️ Backend & Firmware

- `backend/` – FastAPI-Backend (Assets, Detektionen, Alerts, Befehle, WebSocket, MQTT-Publish)
- `docker-compose.yml` – Mosquitto (MQTT) + Backend + Node-RED
- `firmware/secureguard_esp32/` – ESP32-Gateway (LoRa + BLE + MQTT)
- `docs/api-docs.yaml` – OpenAPI-Spezifikation des Backends

Der `LoraService` hält sich bewusst an einen generischen `LoraClient`-Vertrag.
Die eingebaute `DummyLoraClient`-Implementierung liefert Demo-Daten; für den
Produktivbetrieb lässt sich z. B. Helium, The Things Network oder eine eigene
Gateway-Flotte andocken, ohne andere Schichten zu ändern.

## 🔨 Build

Die APK wird über **GitHub Actions** gebaut (kein lokal eingerichteter
Wrapper nötig):

1. Code pushen oder Pull Request öffnen.
2. Der Workflow `.github/workflows/build.yml` lädt JDK 17, das Android-SDK und
   Gradle 8.9 und führt `gradle :app:assembleDebug` aus.
3. Die Debug-APK `app-debug.apk` steht als Artefakt
   **SecureGuardEnterprise-debug** zur Verfügung.

Lokal bauen (sofern Android SDK + JDK 17 vorhanden):

```bash
# einmalig den Gradle Wrapper erzeugen (benötigt eine installierte Gradle-Distribution)
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

## ⚙️ Konfiguration

- **minSdk:** 26 · **targetSdk/compileSdk:** 34
- **Sprache:** Kotlin 2.0, Jetpack Compose (BOM 2024.09)
- **DI:** Hilt 2.52 · **DB:** Room 2.6.1
- **Karte:** OSMDroid 6.1.20 · **Scanner:** ZXing 4.3.0

## 🛡️ Datenschutz

Alle Ortungsdaten und der Audit-Log verbleiben in der lokalen Room-Datenbank.
Externe Crowd- und Satelliten-Kanäle sind standardmäßig deaktiviert und werden
erst nach ausdrücklicher Einwilligung (in den Einstellungen) genutzt.

## 📄 Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE).
