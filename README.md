# SecureGuard Enterprise

Professionelles Sicherheits- und Ortungssystem für Firmen-Assets
(Fahrzeuge, Anlagen, Geräte). 100 % lokale Datenhaltung (Room/SQLite).

**Version 1.1.0** · **Android 11+** (API 30, minSdk 26) · **JDK 17** · **Android SDK 34**

## Download APK

[![Download APK](https://img.shields.io/github/v/release/dang88bang-pixel/Dinge88?label=Download%20APK&color=blue)](https://github.com/dang88bang-pixel/Dinge88/releases/latest)

- Release-APK: `SecureGuard-Enterprise-1.1.0-android11.apk`
- Debug-APK: `SecureGuard-Enterprise-1.1.0-debug.apk`

## Funktionen (alle aktiv)

- BLE-Scan und GATT-Fernsteuerung (Alarm, Licht, Motor, Batterie, Nachricht, Position, Restart, Telemetrie)
- WiFi-BSSID-Erkennung
- GPS / GLONASS / Galileo
- LoRa / LoRaWAN, Optik (YOLO), Urban, Crowd (Find My) über konfigurierbare Endpunkte
- Selbstlernender Agent (adaptive Intervalle, Erfahrungsspeicher, WorkManager)
- OpenStreetMap-Karte, QR-Scan, Dashboard, Alarme, Asset-Whitelist

## Installation auf Android 11

1. APK aus dem [GitHub Release](https://github.com/dang88bang-pixel/Dinge88/releases/latest) laden
2. Unbekannte Quellen erlauben und installieren
3. Beim Start Standort, Bluetooth und Kamera erteilen
4. Asset hinzufügen → Agent starten

## Toolchain

| Komponente | Version |
|---|---|
| JDK | 17 (Temurin) |
| Android SDK | compile/target 34, min 26 (Android 11 = API 30) |
| Gradle | 8.5 |
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| Compose BOM | 2024.02.00 |

```bash
# Lokal (JDK 17 + Android SDK)
./gradlew assembleDebug
./gradlew assembleRelease
```

In dieser Sandbox sind Maven/Google/Gradle gefirewallt – der vorgesehene
Build-Weg ist **GitHub Actions** (`.github/workflows/build-release.yml`).
Siehe [`TOOLCHAIN.md`](./TOOLCHAIN.md).

## Rechtlicher Rahmen

Pilot-Projekt. Die Betriebsvereinbarung ist als Blaupause hinterlegt,
aber nicht an die App angebunden. Siehe [`BETRIEBSVEREINBARUNG.md`](./BETRIEBSVEREINBARUNG.md).

## Lizenz

[Apache License 2.0](./LICENSE)
