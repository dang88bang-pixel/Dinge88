# 📟 SecureGuard Pro – Installation & Inbetriebnahme auf dem Honeywell CT45P (Android 11)

Diese Anleitung beschreibt Schritt für Schritt, wie du **SecureGuard Pro** auf dem
**Honeywell CT45P XON mit Android 11 (API 30)** installierst und startest.

> **Wichtigster Hinweis:** Eine APK muss auf Android **signiert** sein, sonst
> kommt beim Installieren die Meldung **„App wurde nicht installiert“**. Ab
> Version **v1.2.0 (versionCode 3)** wird deshalb auch der Release-Build in der
> CI **garantiert signiert** (Produktiv-Keystore, falls gesetzt, sonst ein
> Debug-Fallback-Keystore). Ältere unsignierte `release.apk` lassen sich **nicht**
> installieren – bitte v1.2.0 oder neuer verwenden.

---

## 1. Welche APK nehmen?

| APK | Signiert | Klartext-MQTT (LAN, `tcp://`) | TLS (`ssl://`/`https`) | Empfehlung CT45P |
|-----|----------|------------------------------|------------------------|------------------|
| **Debug** (`app-debug.apk`) | ✅ (Debug-Key) | ✅ erlaubt | ✅ | **Pilot/Test im WLAN mit lokalem Mosquitto** |
| **Release** (`release.apk` ≥ v1.2.0) | ✅ (Produktiv- oder Debug-Fallback-Key) | ❌ gesperrt | ✅ | **Produktivbetrieb mit TLS-Broker/HTTPS-Backend** |

- Für den **Pilot mit einem lokalen Broker** (z. B. `tcp://192.168.1.100:1883`)
  nimm die **Debug-APK** – sie erlaubt unverschlüsselte Verbindungen im LAN.
- Für **Produktion** nimm die **Release-APK** und betreibe MQTT/Backend mit TLS.
  Wenn du dort trotzdem einen lokalen Klartext-Broker brauchst, gib die IP/Domain
  in `app/src/release/res/xml/network_security_config.xml` frei (siehe unten).

---

## 2. APK beziehen

Die APKs werden von der CI automatisch als Git-Branch `apk-delivery-*` bereitgestellt
(zuverlässig, da kein Download-Portal nötig):

```bash
# APK nach dem erfolgreichen CI-Build von PR/main holen:
git fetch origin apk-delivery-debug apk-delivery-release
git show origin/apk-delivery-debug:apk-dist/app-debug.apk  > SecureGuard-debug.apk
git show origin/apk-delivery-release:apk-dist/release.apk > SecureGuard-release.apk
sha256sum -c <(git show origin/apk-delivery-debug:apk-dist/SHA256SUMS.txt)
```

Alternativ liegen die Artefakte im GitHub-Actions-Lauf (`secureguard-pro-debug`,
`secureguard-pro`) und – bei getaggten Releases – auf der GitHub-Releases-Seite.

**Vor der Installation prüfen (optional), ob die APK signiert ist:**
```bash
apksigner verify --print-certs SecureGuard-release.apk   # muss ohne Fehler beendet werden
```

---

## 3. Installation auf dem CT45P

### 3.1 Voraussetzungen am Gerät
1. Auf dem CT45P **„Unbekannte Quellen“ erlauben**:
   *Einstellungen → Apps → Spezielle Zugriffsberechtigungen → Unbekannte Apps
   installieren* → die nutzende App (Dateimanager/Browser) zulassen.
2. Ausreichend Speicher frei (die APK ist ~40–48 MB).

### 3.2 Übertragen & installieren
- **Per USB (ADB), empfohlen:**
  ```bash
  adb devices                      # CT45P muss erscheinen (USB-Debugging aktivieren)
  adb install -r SecureGuard-debug.apk
  ```
- **Per Dateiübertragung:** APK auf das Gerät kopieren (USB-Laufwerk/MTP, SD-Karte
  oder E-Mail) und mit dem Dateimanager antippen → *Installieren*.

> Falls eine **alte Version** mit einer anderen Signatur installiert ist, meldet
> Android „App wurde nicht installiert“ (Signaturkonflikt). Dann erst die alte
> App deinstallieren (`adb uninstall com.secureguard.enterprise`) und neu
> installieren.

---

## 4. Erster Start & Berechtigungen

Beim ersten Start fragt die App die nötigen Berechtigungen an. Auf dem
**CT45P mit Android 11** sind das insbesondere:

| Berechtigung | Warum nötig (CT45P / Android 11) |
|--------------|----------------------------------|
| **Standort (genau)** `ACCESS_FINE_LOCATION` | **BLE-Scan UND WLAN-Scan brauchen auf Android 11 zwingend Standort** (kein `BLUETOOTH_SCAN` vor Android 12) + GPS-Kanal |
| **Standort immer zulassen** `ACCESS_BACKGROUND_LOCATION` | WLAN/BLE-Scans des 15-Min-Hintergrund-Agenten liefern sonst keine Daten |
| **Kamera** | QR-/Barcode-Scan (ZXing) |
| **Bluetooth** | Wird auf Android 11 über die alten `BLUETOOTH`/`BLUETOOTH_ADMIN`-Rechte abgedeckt (im Manifest, bis API 30) |
| **NFC** | Asset-Tags lesen (keine Laufwerkabfrage nötig, bei installiertem NFC) |
| **Benachrichtigungen** | Auf Android 11 **keine** Laufzeitfreigabe nötig (erst ab Android 13) |

Vorgehen:
1. App öffnen → Berechtigungsdialog(e) **alle zulassen**.
2. Für Hintergrund-Erkennung: bei Standort **„Immer zulassen“** wählen
   *(Einstellungen → Apps → SecureGuard → Berechtigungen → Standort → Immer zulassen)*.
3. Standort am Gerät **einschalten** (sonst liefert der 2D-Imager-Unabhängige
   BLE/WLAN-Scan keine Treffer).

---

## 5. Honeywell-spezifische Einstellungen

### 5.1 Barcode-Scanner (2D-Imager)
Der integrierte Imager des CT45P arbeitet als **HID-Tastatur** („Keyboard Wedge“):
- Scannt man einen Code, tippt das Gerät den Code wie eine Tastatur ein und hängt
  standardmäßig einen Zeilenumbruch (Enter) an.
- In der App kann der gescannte Code damit direkt in Textefelder (z. B. MAC/ID im
  Asset-Formular) übernommen werden.
- Der **Kamera-QR-Scan** (ZXing) in der App bleibt zusätzlich verfügbar
  (Menü *QR scannen*).

> Scan-Profil im Honeywell-Settings (Enterprise-Profile): Klartext-Ausgabe +
> „Auto Enter (CR/LF)“ entspricht `CT45PConfig.SCAN_MODE = AUTO_ENTER`.

### 5.2 USB-Host (serielle Adapter)
FTDI-, CP210x-, **CH340/CH341**- und PL2303-Adapter werden unterstützt
(`device_filter.xml`, USB-Serial). Beim Einstecken fragt Android nach
USB-Berechtigung → **zulassen**.

### 5.3 Hintergrund-Agent & Neustart
- Der Agent läuft als **Foreground-Service** (Status-Notification) und als
  **WorkManager**-Zyklus (alle 15 Min).
- Nach einem Geräte-Neustart plant der `BootReceiver` den Worker automatisch neu.
- Auf Android 11 den **Akku-Optimierungen** für SecureGuard deaktivieren, damit
  der Hintergrund-Agent nicht eingeschläfert wird:
  *Einstellungen → Apps → SecureGuard → Akku → Nicht eingeschränkt*.

---

## 6. Backend/MQTT konfigurieren

In der App unter **Einstellungen → Verbindungen** (oder zur Build-Zeit über
`local.properties`):

| Einstellung | Beispiel (LAN-Pilot) |
|-------------|----------------------|
| MQTT-Broker | `tcp://192.168.1.100:1883` (Debug-APK) bzw. `ssl://...` (Release) |
| WebSocket | `ws://192.168.1.100:8000/ws` (Debug) / `wss://...` (Release) |
| Backend-URL | `http://192.168.1.100:8000` (Debug) / `https://...` (Release) |
| API-Key | falls Backend mit `SECUREGUARD_API_KEY` abgesichert ist |

Der gesamte Stack (Mosquitto + FastAPI-Backend + Node-RED) lässt sich via
`docker compose up -d` auf einem Server im gleichen WLAN starten.

### Klartext im Release-Build freigeben (nur falls nötig)
Soll die **Release-APK** mit einem lokalen Klartext-Broker reden, in
`app/src/release/res/xml/network_security_config.xml` die konkrete Adresse
whitelisten (statt komplett freizugeben):
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.1.100</domain>
    </domain-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
</network-security-config>
```

---

## 7. Fehlerbehebung

| Symptom | Ursache / Lösung |
|---------|------------------|
| **„App wurde nicht installiert“** | APK unsigniert (alte `release.apk`) → **v1.2.0+** verwenden; oder Signaturkonflikt mit alter Installation → alte App deinstallieren |
| App startet nicht / stürzt sofort | Log mit `adb logcat` erfassen (`adb logcat \| grep -i -E "secureguard\|AndroidRuntime"`); prüfe, ob die aktuelle signierte APK installiert ist |
| BLE/WLAN-Scan findet nichts | Auf Android 11 **Standortfreigabe (genau + immer)** erteilen und Standort einschalten |
| Hintergrund-Agent stoppt | Akku-Optimierung für die App deaktivieren (s. 5.3) |
| MQTT verbindet nicht (Release) | Release erzwingt TLS → Debug-APK für LAN-Klartext oder Broker-Domain in der Release-NSC freigeben |
| QR-Scan schwarz | Kamera-Berechtigung erteilen; alternativ 2D-Imager als Tastatur nutzen |

Logcat für die Fehlersuche:
```bash
adb logcat -c            # Puffer leeren
adb logcat | grep -i -E "SecureGuard|AndroidRuntime|SqlCipher|Hilt"
```

---

## 8. Technische Eckdaten (CT45P)

- **Android 11 = API 30**, `minSdk 26`, `targetSdk 35` – die App ist abwärts-
  kompatibel und läuft auf dem CT45P.
- Architektur: `arm64-v8a` (sowie `armeabi-v7a`) Native-Libs (SQLCipher) enthalten.
- BLE/WLAN-Berechtigungslogik erkennt API ≤ 30 und nutzt Standort statt
  `BLUETOOTH_SCAN` (`CT45PConfig.needsLocationForBle`).
- Benachrichtigungsberechtigung erst ab Android 13 relevant – auf Android 11
  erscheint kein Dialog dafür.
