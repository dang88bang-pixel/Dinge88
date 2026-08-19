# 🛡️ SecureGuard Enterprise

**Professionelles Sicherheits- & Ortungssystem für Unternehmen**

Selbstlernender Ortungs-Agent für Firmen-Assets (Fahrzeuge, Anlagen, Geräte).
100 % lokale Datenhaltung (Room/SQLite), DSGVO-konform, mit Betriebsvereinbarung.

## 📱 Funktionen

- 📡 **LoRa / LoRaWAN** – Langstreckenkommunikation (generisch, ohne Meshtastic)
- 🧠 **Selbstlernender Agent** – rekursive Verbesserung, adaptive Intervalle,
  Mustererkennung (zeitlich/räumlich/signalbasiert), Erfahrungsspeicher (letzte 1000 Ereignisse)
- 🗺️ **OpenStreetMap-Karte** mit Echtzeitpositionen
- 🎮 **Fernsteuerung** (Alarm, Motor, Batterie, Nachricht, Position)
- 👁️ **Optische Erkennung** (Webcams, YOLO)
- 🌍 **Apple/Google Crowdsourcing** – nur mit expliziter Einwilligung
- 🏙️ **Urbane Infrastruktur** (ÖPNV, Laternen, Paketstationen, Wetterstationen)
- 📡 **GPS / GLONASS / Galileo** (Satellitenortung)
- 🔒 **DSGVO-konform** (Betriebsvereinbarung nach § 87 BetrVG, BDSG)

## 🛠️ Technologie-Stack

- **Sprache:** Kotlin 1.9.20
- **UI:** Jetpack Compose (Material 3, BOM 2024.02.00)
- **Architektur:** MVVM + Repository + Hilt DI
- **Datenbank:** Room (SQLite) – lokal, keine Cloud
- **Ortung:** BLE (Nordic), WiFi-Probe-Requests, LoRa, GPS, Crowd, Optik, Urban
- **Background:** WorkManager + Foreground Service
- **minSdk 26 / targetSdk 34**

## 📥 Download

[![Download APK](https://img.shields.io/github/v/release/YOUR_USERNAME/secureguard-enterprise?label=Download%20APK&color=blue)](https://github.com/YOUR_USERNAME/secureguard-enterprise/releases/latest)

> Ersetze `YOUR_USERNAME` durch deinen GitHub-Org-/Benutzernamen.

## 🛠️ Installation

1. APK aus dem GitHub Release herunterladen (oder mit `./gradlew assembleRelease` selbst bauen)
2. Auf Android-Gerät installieren (min. Android 8.0 / API 26)
3. Assets in die Whitelist hinzufügen
4. Laufzeit-Berechtigungen erteilen (Bluetooth, Standort, Benachrichtigungen)
5. Agent starten

## 🔧 Lokaler Build

```bash
# JDK 17 vorausgesetzt
./gradlew assembleDebug     # Debug-APK
./gradlew assembleRelease   # Release-APK (Signing konfigurieren)
```

> Hinweis zum Gradle Wrapper: Dieser Wrapper ist **selbstbootstrapierend** und
> benötigt keine eingecheckte `gradle-wrapper.jar`. Er lädt die Gradle-Distribution
> (siehe `gradle/wrapper/gradle-wrapper.properties`) bei der ersten Ausführung herunter.
> Alternativ kann der offizielle Wrapper mit `gradle wrapper` erzeugt werden.

## ⚙️ GitHub Actions (CI/CD)

Der Workflow `.github/workflows/build-release.yml` baut automatisch bei jedem Push
auf `main`/`develop` und erstellt bei Tags (`v*`) ein GitHub Release mit APK.

**Voraussetzungen – Repository Secrets setzen:**

| Secret | Zweck |
|---|---|
| `KEYSTORE_BASE64` | Release-Keystore als base64-kodierter String |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEY_ALIAS` | Alias des Signaturschlüssels |
| `KEY_PASSWORD` | Schlüssel-Passwort |

> **Hinweis:** Ohne gesetzte Secrets wird das APK **unsigned** gebaut und hochgeladen.
> Für die Verteilung signierst du das APK bitte mit deinem eigenen Keystore.

## 🧭 Rechtlicher Rahmen

- Betriebsvereinbarung nach **§ 87 BetrVG** – siehe [`BETRIEBSVEREINBARUNG.md`](./BETRIEBSVEREINBARUNG.md)
- **DSGVO-konform**: keine Personenüberwachung, ausschließlich Firmen-Assets
- **Einwilligungspflicht** für externe Quellen (Apple/Google Find My Crowdsourcing)
- Daten werden lokal gespeichert und standardmäßig nach 30 Tagen automatisch gelöscht

## 📄 Lizenz

[Apache License 2.0](./LICENSE)
