# 📦 APK-Auslieferung (im Repository)

Dieses Verzeichnis enthält die **installierbaren APKs** der SecureGuard-
Enterprise-App, wie sie die CI (`.github/workflows/build-release.yml`) gebaut
und verifiziert hat (`apksigner verify`, `aapt2 dump badging`).

| Datei | Inhalt |
|-------|--------|
| `SecureGuard-<version>-release.apk` | Release-Build (Cleartext-Traffic verboten → Backend/MQTT nur per HTTPS/WSS/TLS) |
| `SecureGuard-<version>-debug.apk` | Debug-Build (Demo-Daten, Cleartext für LAN/Emulator erlaubt → passt zum Docker-Stack `http://…:8000`, `tcp://…:1883`) |
| `SHA256SUMS.txt` | Prüfsummen aller APKs in diesem Ordner |
| `BUILD-INFO-<type>.txt` | Commit, CI-Run, SDK-Level, Signatur-Modus |

**Aktueller Stand: Version 1.2.0 (versionCode 3)** – gebaut von CI-Run
[35493001629](https://github.com/dang88bang-pixel/Dinge88/actions/runs/35493001629)
aus Commit `db283eb`, beide APKs mit `apksigner verify` (v2-Signatur) und
`aapt2 dump badging` (minSdk 26, targetSdk 35) verifiziert; Unit-Tests 52/52 grün.

## Unterstützte Geräte

| | |
|-|-|
| **minSdk** | 26 (Android 8.0) |
| **Zielplattformen (verifiziert)** | **Android 11 (API 30) – Android 14 (API 34)**, u. a. Honeywell CT45P XON |
| **targetSdk / compileSdk** | 35 |
| ABI | universell (keine nativen Splits; SQLCipher-Bibliotheken für arm64-v8a, armeabi-v7a, x86, x86_64 enthalten) |

## Installation

```bash
# Prüfsumme kontrollieren
sha256sum -c SHA256SUMS.txt

# per ADB (USB-Debugging aktiv)
adb install -r SecureGuard-<version>-release.apk

# oder: Datei aufs Gerät kopieren → Dateimanager → "Unbekannte Quellen" für den
# Dateimanager erlauben → installieren (Sideload/MDM, z. B. Honeywell Enterprise Provisioner)
```

Beim ersten Start fragt die App die Laufzeitberechtigungen (Standort, Kamera,
Bluetooth, Benachrichtigungen) und – nach erteiltem Standort – separat den
Hintergrund-Standort an (Android-11+-Vorgabe).

## Signatur

Die Datei `BUILD-INFO-<type>.txt` nennt den Signatur-Modus:

- `production` – mit dem Produktions-Keystore des Betreibers signiert
  (Secrets `ANDROID_KEYSTORE_*` bzw. `KEYSTORE_*` in GitHub).
- `ci-debug-fallback` – mit dem eingecheckten CI-Debug-Keystore
  (`app/keystore/secureguard-ci-debug.p12`, Zertifikat SHA-256
  `02:8C:8F:5A:1C:7A:D1:22:AB:6B:20:10:6F:52:44:8C:47:40:91:F9:FE:32:01:3C:7F:5F:72:1C:54:23:ED:B0`)
  signiert. Installierbar und über frühere Fallback-Builds updatefähig, aber
  **ohne Herkunftsnachweis** – für den produktiven Rollout Produktions-Keystore
  hinterlegen (siehe `docs/SQLCIPHER_AND_SIGNING.md`). Beim Wechsel auf
  `production` einmalig deinstallieren (Signaturwechsel).

## Aktualisieren

```bash
scripts/fetch-apk.sh release   # holt apk-delivery-release → releases/
scripts/fetch-apk.sh debug     # holt apk-delivery-debug   → releases/
git add releases && git commit -m "APK <version> aktualisiert"
```

`*.apk` ist global in `.gitignore` ausgeschlossen; nur `releases/*.apk` ist
per Ausnahme erlaubt. Bitte pro Version nur eine Release- und eine Debug-APK
behalten, damit das Repository klein bleibt.
