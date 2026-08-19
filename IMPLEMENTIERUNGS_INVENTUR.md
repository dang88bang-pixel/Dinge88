# 🔍 Implementierungs-Inventur – SecureGuard Enterprise

**Stand:** laufende Prüfung. Legende: ✅ real & ausführbar · 🟡 real, aber von Hardware-/Backend-Verfügbarkeit abhängig · 🔧 Mock / Platzhalter / Demo

> **Betriebsvereinbarung:** Ist eine **Blaupause** (Pilot-Projekt) – sie wird nur hinterlegt
> ([`BETRIEBSVEREINBARUNG.md`](./BETRIEBSVEREINBARUNG.md)), aber **nicht an die App angebunden**
> (keine Anzeige, keine aktive Geltung).

---

## 1. Ortungstechnologien (Dienste)

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| BLE-Scan | `TelemetryService.searchAsset()` | 🟡 | **Echte** `BluetoothLeScanner`-Suche; findet nur bei vorhandenem Gerät + Berechtigung etwas. |
| WiFi (ScanResults) | `WifiService.searchAsset()` | 🟡 | **Echt** (`WifiManager` + BSSID-Abgleich). Findet nur mit Standort-/NEARBY-Permission etwas. Probe-Requests sind per öffentlichem API nicht passiv abhörbar. |
| LoRa/LoRaWAN | `LoraService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren LoRaWAN-Backend-Endpunkt (`RemoteDetectionFetcher`); ohne konfigurierte URL → `null`. |
| Optik (Webcams/YOLO) | `OpticalService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren YOLO-Inferenz-Endpunkt; ohne URL → `null`. |
| Urban | `UrbanService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren Open-Data-/Infrastruktur-Endpunkt; ohne URL → `null`. |
| Crowd (Apple/Google) | `CrowdService.searchAsset()` | 🟡 | **Aktiv** über konfigurierbaren Find-My-Proxy, nur mit `externalAllowed`; ohne URL → `null`. |
| Satellit/GPS | `SatelliteService.searchAsset()` | 🟡 | **Echt** (GPS-Position via `FusedLocationProviderClient`). |

## 2. Fernsteuerung / Telemetrie

| Funktion | Datei | Status | Kommentar |
|---|---|---|---|
| `sendCommand()` (Alarm/Motor/Batterie/…) | `TelemetryService.sendCommand()` | 🟡 | **Echt** (BLE-GATT-Write via `BleCommandConnector`). Funktioniert nur mit verbindbarem BLE-Gerät. |
| `getLatestTelemetry()` | `TelemetryService.getLatestTelemetry()` | 🟡 | **Echt** (BLE-GATT-Read via `BleCommandConnector.readTelemetry`, JSON-Telemetrie). Ohne Gerät → `null`. |

## 3. Anzeige-Werte (gefälschte/Demo-Daten)

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Batterie | `DashboardViewModel` → `batteryLevel` | ✅ | **Echter Akkustand** (BatteryManager). |
| Telemetrie-Raster | `AssetDetailScreen` | 🟡 | Zeigt ehrlich „–" bis eine echte BLE-GATT-Telemetrie gelesen wurde (Implementierung vorhanden). |
| Agent-Laufzeit | `AgentViewModel` → `runtime`/`progress` | ✅ | **Echt**: Laufzeit aus persistiertem Startzeitpunkt, Fortschritt aus eingestellter Gesamtdauer. |

## 4. UI-Aktionen ohne echte Wirkung

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Karten-Zoom/Zentrieren | `MapScreen` | ✅ | An `MapView` angebunden (`setZoom`/`animateTo`). |
| Einstellungen-Schalter | `SettingsScreen` + `SettingsRepository` | ✅ | Persistiert (SharedPreferences); steuern den Agenten (BLE/WiFi/GPS). |
| Backend-Endpunkte | `SettingsScreen` + `SettingsRepository` | ✅ | Konfigurierbare URLs für LoRa/Optik/Urban/Crowd (Pilot) – damit sind alle Quellen aktivierbar. |
| QR-Scan | `ScanScreen` | ✅ | **Echt** (CameraX + ML Kit Barcode), inkl. Berechtigungsfluss. |
| Agent-Worker | `SecureAgentWorker` | ✅ | Führt `runCycleOnce()` aus (einen echten Suchzyklus). |
| Profil-Daten | `SettingsScreen` („SecureGuard Admin", „Muster GmbH") | 🔧 | Statisch (Platzhalter). |
| `SecureAgentWorker` | `worker/SecureAgentWorker.kt` | ✅ | Führt `runCycleOnce()` aus (einen echten Suchzyklus). |
| Asset-Detail Menü (⋮) | `AssetDetailScreen` | ✅ | Echte Aktionen: Status setzen, Externe-Quellen erlauben/sperren, Asset löschen. |
| Asset-Detail Suche | `AssetDetailScreen` | ✅ | „Suche starten" (alle Quellen), „Extern", „Satellit", „Bluetooth" – jede Quelle einzeln auslösbar; Ergebnis zeigt Quelle + RSSI. |
| Asset-Detail Telemetrie | `AssetDetailScreen` | ✅ | Zeigt echte GATT-Telemetrie (Batterie, Kraftstoff, Motor, Betriebsstunden, km) nach „↻ Aktualisieren". |
| Navigations-Stubs | `DashboardViewModel` / `AssetListViewModel` | ✅ | Entfernt (Navigation läuft direkt über `navController`). |
| Laufzeit-Berechtigungen | `SettingsScreen` → „Berechtigungen" | ✅ | Anfragen für **Standort**, **Bluetooth**, **Benachrichtigungen** (Android 13+) mit Statusanzeige („Erteilt/Nicht erteilt") + Recomposition nach Systemrückkehr. |
| Worker-Scheduling | `SecureGuardApplication` | ✅ | Plant `SecureAgentWorker` periodisch (15 Min) per WorkManager (`HiltWorkerFactory`, `ExistingPeriodicWorkPolicy.KEEP`). |
| Benachrichtigungs-Permission | `NotificationService` | ✅ | Prüft `POST_NOTIFICATIONS` (API 33+) vor dem Senden; Vibration folgt der Asset-/Einstellungs-Flag. |
| Asset-Liste „Suchen" | `AssetListScreen` | ✅ | Button navigiert zur Detailseite mit den Suchfunktionen. |
| Adaptive Priorisierung | `AgentService` | ✅ | `learnFromExperience` sortiert Quellen nach Erfolgsquote; `comprehensiveSearch` fragt in gelernter Reihenfolge ab (erster Treffer gewinnt). |

## 5. Wirklich funktionsfähig (✅)

- App-Start + Hilt-DI, Navigation (Bottom-Bar + Detail-Routen mit Übergängen)
- Room-Datenbank (Asset/Detection/Alert), CRUD über Repository
- **Asset hinzufügen** (speichert in DB)
- **Asset-Liste**: echte Suche + Status-Filter über DB-Daten
- **Asset-Detail**: lädt Asset per ID, zeigt echte Detections/Historie aus der DB
- **Alarme**: liest echte ungelöste Alerts, „als erledigt markieren" persistiert
- **Karte**: osmdroid rendert reale Marker aus DB, Klick → Detail
- **Dashboard**: Statistiken kommen live aus der DB
- **Agent-Schleife** (`AgentService`): läuft, adaptiert Intervalle – findet aber nichts, weil alle Quellen Stubs sind

---

## Umsetzungsplan (Fortschritt)

### ✅ Erledigt
- [x] 1. **`SatelliteService`** → echte GPS-Position (`FusedLocationProviderClient`, `getCurrentLocation` / `lastLocation`)
- [x] 2. **WiFi-Erkennung** → neuer `WifiService` (`WifiManager.getScanResults()` + BSSID-Abgleich), `NEARBY_WIFI_DEVICES`-Permission ergänzt, im Agenten verdrahtet
- [x] 3. **`sendCommand`** → echter BLE-GATT-Write (`BleCommandConnector`: Verbinden → Services → Write), fehlertolerant
- [x] 4. **Karten-Zoom/Zentrieren** → an `MapView` angebunden (`setZoom`/`animateTo`)
- [x] 5. **Einstellungen** → persistiert (`SettingsRepository` auf SharedPreferences); Toggles steuern den Agenten (BLE/WiFi/GPS werden je nach Schalter genutzt)
- [x] 6. **Fake-Werte** → Dashboard liest echten Akkustand (BatteryManager); Asset-Detail-Telemetrie zeigt ehrlich „–" statt Demo-Zahlen; Agent-Fortschritt bleibt simuliert (siehe offen)
- [x] 7. **QR-Scan** → echte Kamera-Erfassung (CameraX + ML Kit Barcode), Berechtigungsfluss + Ergebnisanzeige

### ✅ Aktiv (alle Teile funktional bereitgestellt)
Alle Ortungsquellen, Fernsteuerung, Telemetrie, Kartensteuerung, Einstellungen, QR-Scan,
Agent-Laufzeit und Worker sind **aktiv implementiert**. Die remote-/backend-abhängigen
Quellen (LoRa, Optik, Urban, Crowd) werden über **konfigurierbare Endpunkt-URLs**
(Einstellungen → „Backend-Endpunkte") aktiviert; ohne URL geben sie sauber `null` zurück.

### ⏳ Noch offen / bewusst zurückgestellt
- **Profil-Daten** („SecureGuard Admin" / „Muster GmbH") sind statische Platzhalter.
- **Betriebsvereinbarung**: Als **Blaupause hinterlegt, aber bewusst nicht angebunden**
  (Pilot-Projekt). Keine Anzeige im App-, keine aktive Geltung.

> **Wichtiger Hinweis zu Backend-Quellen:** Für echte Detektionen aus LoRa/Optik/Urban/Crowd
> muss jeweils ein passender Endpunkt im Pilot laufen (LoRaWAN-Gateway/Proxy, YOLO-Server,
> Open-Data-API, Find-My-Proxy) und unter „Backend-Endpunkte" konfiguriert werden.
> **Vor Ort ohne Backend** sind **BLE, GPS, WiFi, Befehlsübertragung und Telemetrie** vollständig
> ausführbar.
