# 🔴 Detaillierte ToDo-Liste – SecureGuard Enterprise Audit

> **Erstellt:** 2026-08-23  
> **Basis:** Vollständiger Projekt-Audit aller Dateien, Abhängigkeiten, Aktions- und Interaktionsketten  
> **Ziel:** Keine Platzhalter, keine Mock-Templates, keine Demo- oder simulierten Funktionen

---

## Legende

| Symbol | Bedeutung |
|--------|-----------|
| 🔴 P0 | **Blocker** – Kompilierungsfehler, App startet nicht |
| 🟠 P1 | **Kritisch** – Simulierte/Mock-Funktionen im Kern |
| 🟡 P2 | **Wichtig** – Gebrochene Aktionsketten, fehlende Anbindungen |
| 🔵 P3 | **Strukturell** – Nicht genutzte Komponenten einbinden |
| ⚪ P4 | **Qualität** – UI-Polish, Hardcodes entfernen |
| ⚫ P5 | **Infrastruktur** – Firmware, Backend, Konfiguration |

**Status:** ⬜ Offen · 🔄 In Arbeit · ✅ Erledigt · ❌ Gestrichen

---

## ═══════════════════════════════════════════════════════
## PHASE 1 – KOMPIlierungsfehler beheben (P0 · BLOCKER)
## ═══════════════════════════════════════════════════════

### 1.1 DashboardScreen.kt – Signatur-Mismatch mit Navigation

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** Funktion hat keinen `navController`-Parameter, aber `SecureGuardApp.kt` ruft `DashboardScreen(navController = navController)` auf.  
**Fix:**
- [ ] Parameter `navController: NavController` zur Funktionssignatur hinzufügen
- [ ] Import `androidx.navigation.NavController` ergänzen

**Betroffene Abhängigkeit:** `SecureGuardApp.kt` Zeile `composable(Routes.DASHBOARD)`

---

### 1.2 DashboardScreen.kt – Nicht-existierende Property `resolved`

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** `alerts.count { !it.resolved }` – das `Alert`-Modell hat `acknowledged`, nicht `resolved`.  
**Fix:**
- [ ] `!it.resolved` → `!it.acknowledged` ersetzen

**Betroffenes Modell:** `data/model/Alert.kt` (Feld: `acknowledged: Boolean`)

---

### 1.3 DashboardScreen.kt – StatCard Signatur falsch

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** Aufruf `StatCard(label = "Batterie", value = "...", icon = "🔋")` passt nicht zur Signatur:
```kotlin
StatCard(modifier: Modifier, value: String, label: String, icon: ImageVector, color: Color)
```
- `icon` erwartet `ImageVector`, nicht `String`
- `modifier` und `color` fehlen komplett

**Fix:**
- [ ] Alle 4 StatCard-Aufrufe mit korrekten Parametern versehen:
  - `modifier = Modifier.weight(1f)` o. ä.
  - `icon` durch passende `ImageVector` ersetzen (z. B. `Icons.Default.BatteryFull`, `Icons.Default.LocationOn`, `Icons.Default.Search`, `Icons.Default.Warning`)
  - `color` pro Karte setzen (z. B. `Color(0xFF2E7D32)` für Batterie)
- [ ] Imports für `ImageVector`-Icons ergänzen

**Betroffene Komponente:** `presentation/components/StatCard.kt`

---

### 1.4 AssetDetailScreen.kt – Fehlende Telemetrie-Felder

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`  
**Problem:** Verwendet Felder die im `Telemetry`-Modell nicht existieren:

| Screen nutzt | Modell hat tatsächlich |
|---|---|
| `data.battery` | `data.batteryPercent` |
| `data.fuel` | `data.fuelPercent` |
| `data.motor` (U/min) | `data.motorOk` (Boolean) |
| `data.distance` | `data.kilometers` |
| (fehlt) | `data.tiresOk` |
| (fehlt) | `data.operatingHours` |

**Fix:**
- [ ] `${data.battery}%` → `${data.batteryPercent}%`
- [ ] `${data.fuel}L` → `${data.fuelPercent}%`
- [ ] `${data.motor} U/min` → `${if (data.motorOk) "OK" else "FEHLER"}`
- [ ] `${data.distance}km` → `${"%.1f".format(data.kilometers ?: 0.0)} km`
- [ ] Betriebsstunden anzeigen: `${data.operatingHours}h`
- [ ] Reifen-Status anzeigen: `${if (data.tiresOk) "OK" else "Prüfung nötig"}`

**Betroffenes Modell:** `data/model/Telemetry.kt`

---

### 1.5 AssetDetailScreen.kt – Fehlende navController-Parameter

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`  
**Problem:** Signatur `AssetDetailScreen(assetId: String, viewModel)` aber Navigation übergibt `navController`.  
**Fix:**
- [ ] Parameter `navController: NavController` zur Funktionssignatur hinzufügen
- [ ] Navigation-Buttons (Zurück, Aktionen) einbauen

**Betroffene Navigation:** `SecureGuardApp.kt` → `composable(Routes.ASSET_DETAIL)`

---

### 1.6 AssetDetailScreen.kt – Fehlende Parameter beim Aufruf von `getAsset()` / `getLatestTelemetry()`

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`  
**Problem:** `viewModel.getAsset(assetId)` und `viewModel.getLatestTelemetry(assetId)` werden aufgerufen, aber das ViewModel hat `loadAsset(assetId)` und `telemetry: StateFlow<Telemetry?>` (kein Parameter).  
**Fix:**
- [ ] `LaunchedEffect(assetId) { viewModel.loadAsset(assetId) }` hinzufügen
- [ ] `viewModel.getAsset(assetId)` → `viewModel.assetState` verwenden
- [ ] `viewModel.getLatestTelemetry(assetId)` → `viewModel.telemetry` verwenden

---

### 1.7 DashboardScreen.kt – `collectAsState` ohne initial auf Alert-Liste

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** `alerts` wird als `List<Alert>` collected, aber die UI zeigt keine Alert-Detailfunktionalität.  
**Fix:**
- [ ] Alert-Anzeige mit `acknowledged`-Status und Typ-Farbgebung ausbauen
- [ ] Navigation zu `Routes.ALERTS` bei Tap hinzufügen

---

## ═══════════════════════════════════════════════════════
## PHASE 2 – SIMULIERTE SERVICES ERSETZEN (P1 · KRITISCH)
## ═══════════════════════════════════════════════════════

### 2.1 BleService.kt – Simulierten Fallback entfernen

**Datei:** `services/BleService.kt`  
**Problem:** Nach fehlgeschlagenem Hardware-Scan wird IMMER eine Fake-Detection generiert:
```kotlin
val rssi = -35 - Random.nextInt(0, 45)
return Detection(nodeId = "ble-sim", ...)
```

**Fix:**
- [ ] Simulierten Fallback-Block komplett entfernen
- [ ] `searchAsset()` gibt `null` zurück wenn `runHardwareScan()` nichts findet
- [ ] `import kotlin.random.Random` entfernen
- [ ] Kommentar „Fallback / demo result" entfernen
- [ ] Sicherstellen, dass der Agent `null`-Ergebnisse korrekt als „nicht gefunden" behandelt

**Auswirkung auf:** `AgentService.comprehensiveSearch()`, `AssetDetailViewModel.searchOnChannel()`

---

### 2.2 WifiService.kt – Echten WiFi-Scan implementieren

**Datei:** `services/WifiService.kt`  
**Problem:** Komplett simuliert – `scanResults` wird ignoriert, `Random`-Werte zurückgegeben.

**Fix:**
- [ ] `WifiManager.startScan()` initiieren (mit `BroadcastReceiver` für `SCAN_RESULTS_AVAILABLE_ACTION`)
- [ ] `getScanResults()` auswerten: ScanResult-Liste nach Asset-MAC filtern
- [ ] Bei MAC-Treffer: echte `Detection` mit realem RSSI und WiFi-Standort erstellen
- [ ] Bei keinem Treffer: `null` zurückgeben (statt Fake-Daten)
- [ ] WiFi-MAC-Adressen mit Asset-MACs abgleichen (Case-insensitive)
- [ ] Permission-Check beibehalten (`ACCESS_FINE_LOCATION`)
- [ ] `import kotlin.random.Random` entfernen

**Benötigte Android-APIs:**
- `WifiManager.startScan()`
- `WifiManager.getScanResults(): List<ScanResult>`
- `ScanResult.BSSID` (MAC-Abgleich)
- `ScanResult.level` (RSSI)

**Auswirkung auf:** `AgentService.buildChannelList()`, `AssetDetailViewModel`

---

### 2.3 LoraService.kt – DummyLoraClient durch echte Implementierung ersetzen

**Datei:** `services/LoraService.kt`  
**Problem:** `DummyLoraClient` mit 4 hartcodierten Berlin-Gateways + `Random` bei `sendCommand()`.

**Fix:**
- [ ] `DummyLoraClient`-Klasse entfernen
- [ ] `LoraClient`-Interface um HTTP-/MQTT-basierte Implementierung erweitern:
  - Option A: Helium-API-Integration (via `ApiServiceManager.searchViaHelium()`)
  - Option B: TTN (The Things Network) REST-API
  - Option C: Eigenes Gateway über MQTT-Topic `secureguard/lora/+`
- [ ] `getGateways()` → echte API-Abfrage mit Error-Handling
- [ ] `sendCommand()` → echte MQTT-Publikation an LoRa-Gateway-Topic
- [ ] `Random`-Import entfernen
- [ ] Konfigurierbaren Backend-Typ in `Settings` / `BuildConfig` vorsehen

**Abhängigkeiten:** `ApiServiceManager.searchViaHelium()`, `MqttService`

---

### 2.4 OpticalService.kt – Echte Kamera-/QR-Erkennung implementieren

**Datei:** `services/OpticalService.kt`  
**Problem:** Rein zufällige Treffer (`Random.nextFloat() > 0.55f`), keine Kamera-Nutzung.

**Fix:**
- [ ] Kamera-basierte Erkennung über ZXing (bereits als Dependency vorhanden):
  - QR-Code auf Asset scannen → MAC extrahieren
  - VIN-/Seriennummer-OCR (optional, ML Kit oder Tesseract)
- [ ] `searchAsset()`: In der lokalen Datenbank nach optischen Markern suchen
- [ ] Bei keinem Treffer: `null` zurückgeben
- [ ] `Random`-Import entfernen
- [ ] Integration mit `ScanQrScreen` (bereits vorhanden) für manuellen Scan

**Bereits vorhanden:** ZXing embedded (`libs.zxing.embedded`), `ScanQrScreen.kt`

---

### 2.5 UrbanService.kt – Echte Urban-Infrastruktur-Anbindung

**Datei:** `services/UrbanService.kt`  
**Problem:** Hartcodierte Berliner Knoten + `Random`-Treffer.

**Fix:**
- [ ] Knotenliste aus API/CKAN-Daten dynamisch laden:
  - `ApiServiceManager.searchViaCKAN("smart city sensor")` für öffentliche Sensoren
  - `ApiServiceManager.searchViaOpenChargeMap()` für Ladesäulen als Proxy
  - `ApiServiceManager.searchViaDHL()` für Packstationen als Proxy
- [ ] Konfigurierbare Endpunkt-URLs für Partner-Netzwerke
- [ ] Bei keinem Treffer: `null` zurückgeben
- [ ] `Random`-Import entfernen
- [ ] Knoten-Daten als konfigurierbare Liste in SharedPreferences / BuildConfig

---

### 2.6 CrowdService.kt – Echte Crowdsource-Anbindung

**Datei:** `services/CrowdService.kt`  
**Problem:** `Random.nextFloat() > 0.5f` entscheidet über Treffer.

**Fix:**
- [ ] Backend-Endpunkt für Crowdsource-Abfrage definieren:
  - Eigene Fleet-Instanz (über `WebSocketService` / Backend-API)
  - Apple Find My: Nur über proprietäres Protokoll – stattdessen eigenes Crowdsourcing-Netzwerk aufbauen
  - Google Find My Device: Google-Play-Services-API (FMDN)
- [ ] HTTP-Request an Backend: `GET /api/crowd/search?mac={mac}`
- [ ] Bei keinem Treffer: `null` zurückgeben
- [ ] Datenschutz-Check beibehalten (`asset.externalAllowed`)
- [ ] `Random`-Import entfernen

**Backend-Erweiterung nötig:** `backend/main.py` → `/api/crowd/search` Endpoint

---

### 2.7 TelemetryService.kt – Echte BLE-GATT-Anbindung

**Datei:** `services/TelemetryService.kt`  
**Problem:** `fetchTelemetry()` generiert immer Fake-Daten, `dispatchCommand()` ist simuliert.

**Fix:**
- [ ] `fetchTelemetry()`:
  - BLE-GATT-Verbindung zum Asset aufbauen (über `BluetoothGatt`)
  - Service-UUID / Characteristic-UUIDs konfigurierbar machen (Standard: ESP32-Firmware-UUIDs)
  - Characteristic lesen → Telemetrie-Payload parsen (JSON oder proprietäres Format)
  - Bei fehlender Verbindung: `null` zurückgeben (KEINE Fake-Daten)
- [ ] `dispatchCommand()`:
  - GATT-Write auf Command-Characteristic
  - Response/Notification abwarten
  - Bei Fehler: `false` zurückgeben
- [ ] `import kotlin.random.Random` entfernen
- [ ] Verbindungspool / Connection-Cache implementieren (nicht bei jedem Aufruf neu verbinden)

**Benötigte UUIDs (aus ESP32-Firmware):**
- Service: `6BA1B218-15A8-461F-9FA8-5DC85327FD13`
- Characteristic: `6BA1B218-15A8-461F-9FA8-5DC85327FD14`

---

### 2.8 SatelliteService.kt – Simulierten Fallback entfernen

**Datei:** `services/SatelliteService.kt`  
**Problem:** Bei keinem GPS-Fix werden `Random`-Koordinaten generiert.

**Fix:**
- [ ] Fallback-Block mit `Random.nextDouble()` entfernen
- [ ] Bei keinem GPS-Fix: `null` zurückgeben
- [ ] Optional: Letzte bekannte Position des Assets als Fallback nutzen (statt Random)
- [ ] `import kotlin.random.Random` entfernen

---

### 2.9 AgentService.performRegistration() – Echte HTTP-Implementierung

**Datei:** `services/AgentService.kt`  
**Problem:** TODO-Platzhalter, gibt immer `false` zurück.

**Fix:**
- [ ] HTTP-POST an `registrationUrl` mit `registrationData` + `email` als Payload
- [ ] OkHttp-Client verwenden (bereits vorhanden über `ApiServiceManager.httpClient`)
- [ ] Response-Status prüfen (2xx = Erfolg)
- [ ] Error-Handling: Bei HTTP-Fehler → `false` + Logging
- [ ] TODO-Kommentar entfernen

---

## ═══════════════════════════════════════════════════════
## PHASE 3 – AKTIONSKETTEN REPARATUREN (P2 · WICHTIG)
## ═══════════════════════════════════════════════════════

### 3.1 ActionsViewModel.executeAction() – AgentService.sendAction() nutzen

**Datei:** `presentation/ui/actions/ActionsViewModel.kt`  
**Problem:** Nutzt nur `telemetryService.sendCommand()` (simuliert). Umgeht MQTT/WebSocket/BLE/Offline-Queue.

**Fix:**
- [ ] `AgentService` als Dependency injizieren
- [ ] `telemetryService.sendCommand()` → `agentService.sendAction(asset, actionType.wireCommand)` ersetzen
- [ ] `AgentService` im Konstruktor hinzufügen
- [ ] `TelemetryService`-Import entfernen (falls nicht anderweitig benötigt)

**Betroffene Kette:**  
UI → `ActionsViewModel.executeAction()` → ~~`TelemetryService.sendCommand()`~~ → `AgentService.sendAction()` → MQTT + WebSocket + BLE/GATT + Offline-Queue

---

### 3.2 AssetDetailViewModel.executeAction() – AgentService.sendAction() nutzen

**Datei:** `presentation/ui/assets/AssetDetailViewModel.kt`  
**Problem:** Gleiches Problem wie 3.1 – nur `telemetryService.sendCommand()`.

**Fix:**
- [ ] `telemetryService.sendCommand()` → `agentService.sendAction(asset, actionType.wireCommand)` ersetzen
- [ ] `AgentService` ist bereits injiziert → nur den Aufruf ändern

**Betroffene Kette:**  
UI → `AssetDetailViewModel.executeAction()` → ~~`TelemetryService.sendCommand()`~~ → `AgentService.sendAction()` → MQTT + WebSocket + BLE/GATT + Offline-Queue

---

### 3.3 NFC-Tag-Ergebnis persistieren und Agenten benachrichtigen

**Datei:** `MainActivity.kt`  
**Problem:** `nfcService.processTag(intent)` gibt eine `Detection` zurück, die aber verworfen wird.

**Fix:**
- [ ] `SecureGuardDatabase` injizieren (oder über Repository)
- [ ] Nach `processTag()`: Detection in DB speichern
- [ ] Asset-Status aktualisieren wenn MAC bekannt ist
- [ ] Notification auslösen
- [ ] AuditLog-Eintrag erstellen

**Code-Skizze:**
```kotlin
val detection = nfcService.processTag(intent)
if (detection != null) {
    database.detectionDao().insert(detection)
    database.assetDao().getByMac(detection.assetMac)?.let { asset ->
        database.assetDao().updateStatus(
            mac = asset.mac, status = AssetStatus.ONLINE,
            rssi = 0, lat = null, lon = null, timestamp = Date()
        )
    }
}
```

---

### 3.4 Settings-Änderungen an laufenden AgentService kommunizieren

**Datei:** `presentation/ui/settings/SettingsViewModel.kt`  
**Problem:** Einstellungen landen nur in SharedPreferences, der Agent nutzt sie nicht.

**Fix:**
- [ ] `AgentService` als Dependency injizieren
- [ ] Bei Änderung von `offlineOnly`, `learningMode`, `externalCrowdAllowed`:
  - Aktuellen Agent stoppen
  - Mit neuen `AgentSettings` neu starten
- [ ] Oder: `AgentService` beobachtet SharedPreferences direkt

**Abhängigkeit:** `AgentService.start(settings)`, `AgentService.stop()`

---

### 3.5 DashboardViewModel – Echte Batterie-Abfrage

**Datei:** `presentation/ui/dashboard/DashboardViewModel.kt`  
**Problem:** `MutableStateFlow(87)` ist hartcodiert.

**Fix:**
- [ ] `@ApplicationContext context: Context` injizieren
- [ ] `BatteryManager` oder `Intent.ACTION_BATTERY_CHANGED` BroadcastReceiver nutzen:
```kotlin
val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
val batteryStatus = context.registerReceiver(null, ifilter)
val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
```
- [ ] `MutableStateFlow(87)` durch dynamischen Wert ersetzen

---

### 3.6 DashboardScreen.getBatteryLevel() – Korrekte API nutzen

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** `BATTERY_PROPERTY_CHARGE_COUNTER / ENERGY_COUNTER` ist nicht der Prozentwert.

**Fix:**
- [ ] Methode entfernen (Batterie wird jetzt im ViewModel berechnet, siehe 3.5)
- [ ] Oder: `BATTERY_PROPERTY_CAPACITY` nutzen (liefert Prozent)

---

## ═══════════════════════════════════════════════════════
## PHASE 4 – STRUKTURELLE ANBINDUNGEN (P3 · STRUKTURELL)
## ═══════════════════════════════════════════════════════

### 4.1 ApiNodeManager in AgentService einbinden

**Datei:** `services/AgentService.kt`  
**Problem:** `ApiNodeManager` (Circuit-Breaker, Ratenlimits, Health-Monitor, Learning) existiert, wird aber vom Agent nicht genutzt.

**Fix:**
- [ ] `ApiNodeManager` als Constructor-Dependency injizieren
- [ ] In `buildChannelList()`: API-Kanäle über `apiNodeManager.queryAllNodes()` abfragen
- [ ] `DetectionSource.API`-Ergebnisse aus dem NodeManager sammeln
- [ ] Einzelne API-Service-Aufrufe im Agent durch NodeManager-Aufrufe ersetzen

**Nutzen:** Automatische Prioritätsanpassung, Circuit-Breaker bei API-Ausfällen, Ratenlimit-Compliance.

---

### 4.2 RoleManager in Aktionen und Screens einbinden

**Datei:** `security/RoleManager.kt`  
**Problem:** Vollständiges RBAC-Modell existiert, wird nirgendwo geprüft.

**Fix:**
- [ ] `UserSession`-Singleton erstellen (aktueller User + Rolle)
- [ ] In `ActionsViewModel.executeAction()`: `RoleManager.hasPermission(user, EXECUTE_ACTIONS)` prüfen
- [ ] In `AddAssetViewModel.save()`: `RoleManager.hasPermission(user, EDIT_ASSETS)` prüfen
- [ ] In `SettingsScreen`: Konfigurations-Optionen nur bei `CONFIGURE_AGENT`-Permission anzeigen
- [ ] In `AgentViewModel`: Agent-Start nur bei `CONFIGURE_AGENT` erlauben
- [ ] Default: `Role.ADMIN` für Einzelgeräte-Betrieb (abwärtskompatibel)

---

### 4.3 AgentViewModel – Settings an AgentService weitergeben

**Datei:** `presentation/ui/agent/AgentViewModel.kt`  
**Problem:** `saveSettings()` erstellt neue `AgentSettings`, aber `offlineOnly` und `externalSources` sind hartcodiert:
```kotlin
offlineOnly = true,
externalSources = false
```

**Fix:**
- [ ] `SettingsViewModel`-Werte lesen (SharedPreferences) und in `AgentSettings` übernehmen
- [ ] `offlineOnly` und `externalSources` dynamisch aus Settings beziehen
- [ ] `learningMode` aus dem UI-State korrekt übergeben (bereits vorhanden)

---

### 4.4 NfcService – Detection-Ergebnis in den Agent-Flow integrieren

**Datei:** `services/NfcService.kt` + `MainActivity.kt`  
**Problem:** NFC-Detections werden emittiert (`_detections.tryEmit()`) aber niemand sammelt den Flow.

**Fix:**
- [ ] In `AgentService.startRealtimeChannels()`: `nfcService.detections` Flow sammeln
- [ ] NFC-Detection persistieren und Asset-Status aktualisieren (wie bei MQTT/WebSocket)
- [ ] `NfcService` als Constructor-Dependency in `AgentService` injizieren

---

### 4.5 DashboardScreen – uiState aus DashboardViewModel nutzen

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`  
**Problem:** Screen sammelt Rohdaten statt das reichhaltige `uiState` des ViewModels zu nutzen.

**Fix:**
- [ ] `val uiState by viewModel.uiState.collectAsState()` verwenden
- [ ] StatCards mit `uiState.totalAssets`, `uiState.onlineAssets`, `uiState.alertCount`, `uiState.agentRunning` füllen
- [ ] `uiState.lastSyncTime` anzeigen
- [ ] Agent-Toggle-Button mit `uiState.agentRunning` verbinden

---

### 4.6 OfflineQueue – Automatischen Flush im Agent-Cycle einbauen

**Datei:** `services/AgentService.kt`  
**Problem:** `flushOfflineQueue()` existiert, wird aber nie automatisch aufgerufen.

**Fix:**
- [ ] Am Ende jedes Agent-Cycles: `flushOfflineQueue()` aufrufen wenn MQTT verbunden
- [ ] Oder: Nach erfolgreichem MQTT-Reconnect automatisch flushen

---

## ═══════════════════════════════════════════════════════
## PHASE 5 – UI/UX-QUALITÄT (P4 · POLISH)
## ═══════════════════════════════════════════════════════

### 5.1 AgentViewModel – Fake-Progress entfernen

**Datei:** `presentation/ui/agent/AgentViewModel.kt`  
**Problem:** `progress = if (status.running) 85f else 0f` ist hartcodiert.

**Fix:**
- [ ] Progress aus echten Zyklusdaten berechnen:
  - `progress = (System.currentTimeMillis() - lastRunAt) / intervalMs` (Fortschritt bis nächster Zyklus)
  - Oder: `progress = (detectionsThisCycle / totalAssets).toFloat()` (Zyklus-Fortschritt)

---

### 5.2 SettingsScreen – Hardcodierte Profil-Daten entfernen

**Datei:** `presentation/ui/settings/SettingsScreen.kt`  
**Problem:** `"Benutzer: Wache Mitte"` und `"Organisation: SecureGuard Enterprise"` sind statisch.

**Fix:**
- [ ] `SettingsViewModel` um User-Info erweitern (aus SharedPreferences oder RoleManager)
- [ ] Profil-Daten editierbar machen (oder aus UserSession beziehen)

---

### 5.3 NodeStatusViewModel – Hardcoded Test-MAC entfernen

**Datei:** `presentation/ui/nodes/NodeStatusViewModel.kt`  
**Problem:** `mac = "AA:BB:CC:DD:EE:01"` ist hartcodiert.

**Fix:**
- [ ] Erstes Asset aus der Datenbank als Test-MAC verwenden
- [ ] Oder: User wählt Asset vor Test-Suche aus

---

### 5.4 Demo-Seed optional machen

**Datei:** `SecureGuardApplication.kt`  
**Problem:** `seedDemoDataIfEmpty()` sät 5 Fake-Assets beim ersten Start.

**Fix:**
- [ ] BuildConfig-Flag `DEMO_MODE` einführen (Default: `false` für Release)
- [ ] Seed nur bei `BuildConfig.DEMO_MODE == true` ausführen
- [ ] Oder: Ersten Start-Wizard statt Auto-Seed

---

### 5.5 AssetDetailScreen – Vollständige UI ausbauen

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`  
**Problem:** Sehr rudimentäre UI (nur 5 Text-Zeilen), kein Zurück-Button, keine Aktionen.

**Fix:**
- [ ] Scaffold mit TopAppBar (Zurück-Button)
- [ ] Asset-Info-Card (Name, MAC, Status, RSSI, Koordinaten)
- [ ] Telemetrie-Card (Batterie, Motor, Reifen, Betriebsstunden, km)
- [ ] Detektions-Historie (LazyColumn mit `detections` aus ViewModel)
- [ ] Aktions-Buttons (Alarm, Blinken, Motor, etc.) → über `agentService.sendAction()`
- [ ] Such-Buttons (Alle Kanäle, BLE, WiFi, LoRa, Crowd, Satellit)
- [ ] Karte-Ausschnitt (osmdroid Mini-Map mit Asset-Position)

---

### 5.6 MapScreen – Asset-Click navigiert korrekt

**Datei:** `presentation/ui/map/MapScreen.kt`  
**Problem:** Marker-Click nutzt `navController.navigate()` – prüfen ob die Navigation aus dem Composable-Kontext funktioniert (MapScreen bekommt `navController`).

**Fix:**
- [ ] Verifizieren, dass `Routes.assetDetail(asset.id)` korrekt aufgelöst wird
- [ ] Marker-Popup mit Asset-Details vor Navigation anzeigen

---

## ═══════════════════════════════════════════════════════
## PHASE 6 – FIRMWARE & BACKEND (P5 · INFRASTRUKTUR)
## ═══════════════════════════════════════════════════════

### 6.1 ESP32-Firmware – Credentials konfigurierbar machen

**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino`  
**Problem:** WiFi-SSID/Passwort und MQTT-Server sind hartcodiert.

**Fix:**
- [ ] WiFi-Credentials in `Preferences` (NVS) speichern
- [ ] Konfigurationsmodus über BLE oder Serial bei erstem Start
- [ ] MQTT-Server als konfigurierbare Variable
- [ ] OTA-Update-Support (ArduinoOTA) hinzufügen

---

### 6.2 ESP32-Firmware – Echte Telemetrie-Daten

**Datei:** `firmware/secureguard_esp32/secureguard_esp32.ino`  
**Problem:** BLE-Telemetrie ist statisch (`battery: 85, rssi: -45`).

**Fix:**
- [ ] Echte Sensor-Daten lesen (ADC für Batterie-Spannung)
- [ ] WiFi-RSSI messen (`WiFi.RSSI()`)
- [ ] LoRa-RSSI aus empfangenem Paket (`LoRa.packetRssi()`)
- [ ] Uptime statt `millis()` als Timestamp
- [ ] JSON-Payload dynamisch zusammenbauen

---

### 6.3 Backend – Crowdsourcing-Endpoint hinzufügen

**Datei:** `backend/main.py`  
**Problem:** Kein Endpoint für Crowd-Source-Abfragen.

**Fix:**
- [ ] `POST /api/crowd/report` – anonyme Sichtung melden
- [ ] `GET /api/crowd/search?mac={mac}` – letzte Sichtungen abfragen
- [ ] `crowd_sightings`-Tabelle in der DB erstellen
- [ ] Rate-Limiting pro IP
- [ ] Hashed-MACs für Datenschutz

---

### 6.4 Backend – Simulierte Verarbeitungszeit entfernen

**Datei:** `backend/main.py`  
**Problem:** `await asyncio.sleep(2)` als Demo-Artefakt.

**Fix:**
- [ ] `asyncio.sleep(2)` entfernen
- [ ] Echte MQTT-Antwort abwarten (mit Timeout)
- [ ] `print()` → strukturiertes Logging (`logging.info()`)

---

### 6.5 Backend – WebSocket-Echo durch echte Telemetrie-Weiterleitung ersetzen

**Datei:** `backend/main.py`  
**Problem:** WebSocket-Endpoint echo nur Nachrichten oder sendet ACKs.

**Fix:**
- [ ] MQTT-Subscriber im Backend: Telemetrie-Topic abonnieren
- [ ] Eingehende Telemetrie an alle verbundenen WebSocket-Clients broadcasten
- [ ] `type: "telemetry"` / `type: "alert"` korrekt formatieren

---

### 6.6 Docker-Compose – Node-RED hinzufügen

**Datei:** `docker-compose.yml`  
**Problem:** README erwähnt Node-RED, aber `docker-compose.yml` prüfbar ob vorhanden.

**Fix:**
- [ ] Verifizieren, dass Node-RED-Service in `docker-compose.yml` konfiguriert ist
- [ ] Node-RED mit MQTT-Broker verbinden
- [ ] Dashboard-Flows für Asset-Monitoring bereitstellen

---

## ═══════════════════════════════════════════════════════
## ZUSAMMENFASSUNG & AUFWANDSCHÄTZUNG
## ═══════════════════════════════════════════════════════

| Phase | Anzahl Tasks | Geschätzter Aufwand | Priorität |
|-------|:---:|---|---|
| **P0** Kompilierungsfehler | 7 | ~4h | 🔴 Sofort |
| **P1** Simulierte Services | 9 | ~24-40h | 🟠 Nächster Sprint |
| **P2** Aktionsketten | 6 | ~8-12h | 🟡 Parallel zu P1 |
| **P3** Strukturelle Anbindungen | 6 | ~12-16h | 🔵 Nach P1+P2 |
| **P4** UI/UX-Polish | 6 | ~8-12h | ⚪ Nach P3 |
| **P5** Firmware/Backend | 6 | ~12-16h | ⚫ Parallel |
| **Gesamt** | **40** | **~68-100h** | |

---

## ABHÄNGIGKEITEN (Reihenfolge)

```
Phase 1 (P0) → Phase 2 (P1) → Phase 3 (P2)
                                  ↓
                              Phase 4 (P3) → Phase 5 (P4)
                                                ↓
Phase 6 (P5) ← kann parallel zu allen Phasen laufen
```

**Kritischer Pfad:** P0 → P1 (BleService, TelemetryService) → P2 (3.1, 3.2) → P3 (4.1)

---

## BETROFFENE DATEIEN (Gesamtübersicht)

### Muss geändert werden:
| Datei | Phase | Tasks |
|-------|:---:|:---:|
| `DashboardScreen.kt` | P0, P4 | 1.1, 1.2, 1.3, 1.7, 3.6, 4.5 |
| `AssetDetailScreen.kt` | P0, P4 | 1.4, 1.5, 1.6, 5.5 |
| `BleService.kt` | P1 | 2.1 |
| `WifiService.kt` | P1 | 2.2 |
| `LoraService.kt` | P1 | 2.3 |
| `OpticalService.kt` | P1 | 2.4 |
| `UrbanService.kt` | P1 | 2.5 |
| `CrowdService.kt` | P1 | 2.6 |
| `TelemetryService.kt` | P1 | 2.7 |
| `SatelliteService.kt` | P1 | 2.8 |
| `AgentService.kt` | P1, P3 | 2.9, 4.1, 4.4, 4.6 |
| `ActionsViewModel.kt` | P2 | 3.1 |
| `AssetDetailViewModel.kt` | P2 | 3.2 |
| `MainActivity.kt` | P2 | 3.3 |
| `SettingsViewModel.kt` | P2 | 3.4 |
| `DashboardViewModel.kt` | P2 | 3.5 |
| `ApiNodeManager.kt` | P3 | 4.1 |
| `RoleManager.kt` | P3 | 4.2 |
| `AgentViewModel.kt` | P3, P4 | 4.3, 5.1 |
| `SecureGuardApplication.kt` | P4 | 5.4 |
| `SettingsScreen.kt` | P4 | 5.2 |
| `NodeStatusViewModel.kt` | P4 | 5.3 |
| `backend/main.py` | P5 | 6.3, 6.4, 6.5 |
| `firmware/secureguard_esp32.ino` | P5 | 6.1, 6.2 |

### Unverändert (bereits korrekt):
`MqttService.kt`, `WebSocketService.kt`, `MCPClient.kt`, `TempMailService.kt`, `AuthManager.kt`, `EncryptionService.kt`, `SecureGuardDatabase.kt`, alle DAOs, `SecureGuardRepository.kt`, `LearningEngine.kt`, `AuditLogService.kt`, `OfflineQueue.kt`, `BackupManager.kt`, `ExportService.kt`, `NotificationService.kt`, `AlertSoundManager.kt`, `OfflineMapService.kt`, `DatabaseCleanup.kt`, `UsbSerialService.kt`, `CacheManager.kt`, `RetryManager.kt`, `ErrorHandler.kt`, `Converters.kt`, `CT45PConfig.kt`, `NavItems.kt`, `SecureGuardApp.kt`, `SecureAgentWorker.kt`, `AgentForegroundService.kt`, `AppModule.kt`, alle API-Interfaces, `MqttConfig.kt`, Theme-Dateien
