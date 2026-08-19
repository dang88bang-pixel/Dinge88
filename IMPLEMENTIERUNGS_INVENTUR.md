# 🔍 Implementierungs-Inventur – SecureGuard Enterprise

**Stand:** laufende Prüfung. Legende: ✅ real & ausführbar · 🟡 teilweise / abhängig von Hardware-Berechtigung · 🔧 Mock / Platzhalter / Demo

---

## 1. Ortungstechnologien (Dienste)

| Komponente | Datei | Status | Kommentar |
|---|---|---|---|
| BLE-Scan | `TelemetryService.searchAsset()` | 🟡 | **Echte** `BluetoothLeScanner`-Suche; findet nur bei vorhandenem Gerät + Berechtigung etwas. |
| WiFi (ScanResults) | `WifiService.searchAsset()` | 🟡 | **Neu implementiert** (`WifiManager` + BSSID-Abgleich). Findet nur mit Standort-/NEARBY-Permission etwas. Probe-Requests sind per öffentlichem API nicht passiv abhörbar. |
| LoRa/LoRaWAN | `LoraService.searchAsset()` | 🔧 | Gibt immer `null` zurück (TODO). Kein Backend. |
| Optik (Webcams/YOLO) | `OpticalService.searchAsset()` | 🔧 | Gibt immer `null` zurück (TODO). |
| Urban | `UrbanService.searchAsset()` | 🔧 | Gibt immer `null` zurück (TODO). |
| Crowd (Apple/Google) | `CrowdService.searchAsset()` | 🔧 | Gibt immer `null` zurück (TODO) – korrekt nur mit Einwilligung. |
| Satellit/GPS | `SatelliteService.searchAsset()` | 🟡 | **Neu implementiert** (echte GPS-Position via `FusedLocationProviderClient`). Liefert die Geräteposition als Detection-Referenz. |

## 2. Fernsteuerung / Telemetrie

| Funktion | Datei | Status | Kommentar |
|---|---|---|---|
| `sendCommand()` (Alarm/Motor/Batterie/…) | `TelemetryService.sendCommand()` | 🟡 | **Neu implementiert** (echter BLE-GATT-Write via `BleCommandConnector`). Funktioniert nur mit verbindbarem BLE-Gerät. |
| `getLatestTelemetry()` | `TelemetryService.getLatestTelemetry()` | 🔧 | Gibt weiter `null` zurück (GATT-Read noch offen). |

## 3. Anzeige-Werte (gefälschte/Demo-Daten)

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Batterie | `DashboardViewModel` → `batteryLevel` | ✅ | **Echter Akkustand** (BatteryManager) statt 87 %. |
| Telemetrie-Raster | `AssetDetailScreen` | ✅ | Demo-Zahlen ersetzt durch ehrliches „–" (echte Telemetrie erfordert BLE-GATT-Read, noch offen). |
| Agent-Fortschritt | `AgentViewModel` → `progress = 85f`, `runtime = "12d 4h 32m"` | 🔧 | Simuliert / hartkodiert (offen). |

## 4. UI-Aktionen ohne echte Wirkung

| Stelle | Datei | Status | Kommentar |
|---|---|---|---|
| Karten-Zoom/Zentrieren | `MapScreen` | ✅ | An `MapView` angebunden (`setZoom`/`animateTo`). |
| Einstellungen-Schalter | `SettingsScreen` + `SettingsRepository` | ✅ | Persistiert (SharedPreferences); steuern den Agenten (BLE/WiFi/GPS). |
| QR-Scan | `ScanScreen` | ✅ | **Neu implementiert** (CameraX + ML Kit Barcode), inkl. Berechtigungsfluss. |
| Profil-Daten | `SettingsScreen` („SecureGuard Admin", „Muster GmbH") | 🔧 | Statisch. |
| Navigations-Stubs | `DashboardViewModel.navigateToDetail/…` | 🟡 | Unkritisch – Navigation wird direkt im UI über `navController` erledigt. |
| `SecureAgentWorker` | `worker/SecureAgentWorker.kt` | 🟡 | Startet den Agenten nur an; führt die Schleife nicht im Worker aus. |

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

### ⏳ Noch offen
- [ ] `AgentViewModel.progress` (85f) + `runtime` („12d 4h 32m") sind noch simuliert → echtes Laufzeit-Tracking in Persistenz
- [ ] `TelemetryService.getLatestTelemetry()` gibt weiter `null` zurück (echte BLE-GATT-Read-Telemetrie)
- [ ] `SecureAgentWorker` startet den Agenten nur an (kein Foreground-Service)
- [ ] **Infrastruktur-abhängig (ohne externes Backend nicht ausführbar):**
  - `LoraService` (LoRaWAN-Backend/Gateway)
  - `OpticalService` (YOLO + Webcam-Stream / Kamera-Server)
  - `CrowdService` (Apple/Google Find My API)
  - `UrbanService` (Smart-City-/Open-Data-APIs)
  - Profil-Daten („SecureGuard Admin" / „Muster GmbH") statisch

> **Wichtiger Hinweis:** Die Infrastruktur-quellen können ohne echtes Backend nicht
> "echt" gemacht werden. Mit echter Hardware-Vorort-Logik sind **BLE, GPS, WiFi und
> Befehlsübertragung** vollständig ausführbar.
