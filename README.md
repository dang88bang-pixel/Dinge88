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
| 🗺️ **Karte** | OpenStreetMap (OSMDroid) mit farbcodierten Markern und Legende |
| 📦 **Assets** | Whitelist, Suche/Filter, Detail mit Telemetrie, Historie & Aktionen |
| 📡 **Kanäle** | BLE, WiFi, LoRa/LoRaWAN (generisch), Optik, Urban, Crowd, Satellit, Telemetrie |
| 🧠 **Agent** | Selbstlernend, priorisiert erfolgreiche Kanäle ("rekursive Verbesserung") |
| 🔔 **Alarme** | Lokaler Audit-Log, Push-Benachrichtigungen, Command-Log |
| 🔒 **DSGVO** | Alles lokal (Room), externe Kanäle nur mit ausdrücklicher Einwilligung |
| 📷 **QR-Scan** | Integrierter Scanner (ZXing) zum Anlernen neuer Assets |

## 🏗️ Architektur

```
app/src/main/java/com/secureguard/enterprise/
├── data/
│   ├── model/           # Asset, Detection, Alert, Telemetry, Enums
│   ├── local/           # Room-Datenbank, DAOs, TypeConverter
│   └── repository/      # SecureGuardRepository (Single Source of Truth)
├── di/                  # Hilt-Module
├── services/            # LoraService, BleService, WifiService, TelemetryService,
│                        # OpticalService, UrbanService, CrowdService,
│                        # SatelliteService, AgentService, NotificationService
├── presentation/
│   ├── components/      # AssetCard, StatCard, ActionButton
│   ├── navigation/      # NavHost + Bottom-Navigation
│   ├── theme/           # Material 3 Theme
│   └── ui/              # Dashboard, Assets, Map, Actions, Agent, Settings, Alerts
├── MainActivity.kt
└── SecureGuardApplication.kt
```

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
