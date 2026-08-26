# 📋 Implementierungsinventur – Abweichungen & begründete Entscheidungen

> Diese Datei ist die vom Code referenzierte Inventur der Implementierungs-
> abweichungen. Sie wird in `app/build.gradle.kts` an zwei Stellen zitiert:
>
> - `app/build.gradle.kts:194` → MQTT-Paho-Entscheidung („Abweichungen")
> - `app/build.gradle.kts:201` → BLE-Scan-Entscheidung
>
> Stand: 2026-08-26 · Commit `fea0da5` + Vervollständigungs-Branch

---

## 1. MQTT: Paho Java-Client statt Paho-Android-Service

| | Entscheidung |
|---|---|
| **Eingesetzt** | `org.eclipse.paho.client.mqttv3` (`MqttAsyncClient`) in `MqttService.kt` |
| **Nicht verwendet** | `org.eclipse.paho.android.service` (veraltet, unmaintained, kaputt ab Android 8/Oreo wegen Background-Service-Restriktionen) |
| **Begründung** | Der Android-Wrapper benötigt einen gebundenen Android-`Service` und ist seit Jahren ohne Releases. Der reine Java-Client läuft in einem eigenen Koroutinen-/Foreground-Service-Kontext (`AgentForegroundService`) stabil; Reconnects, QoS und Persistence steuert die App selbst. |

## 2. BLE-Scan: Plattform-API statt Nordic-Library

| | Entscheidung |
|---|---|
| **Eingesetzt** | Aktiver Scan über `BluetoothLeScanner` (Plattform-API) in `BleService.kt` (MAC-Filter, 2s-Scan-Fenster); GATT-Read/Write in `TelemetryService.kt` über `BluetoothGatt` |
| **Nicht verwendet (aktiv)** | Nordic `ble-ktx` (Dependency ist enthalten, wird aber nicht für den aktiven Scan genutzt) |
| **Begründung** | Der Use-Case (ein MAC-Scan pro Kanal-Zyklus) ist mit ~40 Zeilen Plattform-API vollständig abgedeckt; die Nordic-Bibliothek bleibt für künftige Verbindungs-Features (Auto-Reconnect, Bonding) eingebunden. |

## 3. Telemetrie-Schema: Firmware ↔ App (abgestimmt)

Die ESP32-Firmware sendet: `battery`, `motor` (Relay-Status), `wifi_rssi`,
`lora_rssi`, `uptime`, `ip`, `device`. Die App (`TelemetryService.parseTelemetryJson`)
liest zusätzlich `fuel`, `tires`, `hours`, `km`, `lat`, `lon` per `opt*()`-Defaults.

**Grundsatz: Es werden keine Sensorwerte simuliert.** Keys ohne vorhandene
Hardware (Tank-Sensor, Reifen-TPMS, GPS am Asset) bleiben `null` – die Firmware
sendet sie nicht und die App füllt nichts auf.

## 4. RBAC: vorbereitet, aber Einzelgerät-ADMIN-Kontext

`RoleManager` definiert 4 Rollen × 7 Permissions; die ViewModels erzeugen
aktuell einen hardcodierten `User(id="local", name="Admin", role=ADMIN)`.
Das ist eine bewusste Vereinfachung für den Einzelgerät-Betrieb; Login-/
User-Management bleibt für die Server-Anbindung vorbereitet (siehe Kommentar
in `RoleManager.kt`).

## 5. Paging: Utility vorhanden, Liste nutzt reaktive Flows

`AssetPagingSource` (Paging 3) ist implementiert und getestet nutzbar
(`Pager(PagingConfig(20)) { AssetPagingSource(repo, filter) }` +
`collectAsLazyPagingItems()`), wird aber von `AssetListScreen` aktuell **nicht**
genutzt: Die Asset-Whitelist ist klein genug, dass der reaktive
`observeWhitelisted()`-Flow (Live-Updates ohne manuelles Refresh) die bessere
Wahl ist. Paging bleibt für >500-Asset-Installationen als documented utility
erhalten.

## 6. CI: Tests vor Build, API-Keys als optionale Secrets

Der Workflow führt `testDebugUnitTest` + `lintDebug` **vor** dem APK-Build aus
(`build: needs: test`). API-Keys werden optional aus Repo-Secrets als
`ORG_GRADLE_PROJECT_*`-Umgebungsvariablen an Gradle durchgereicht (wird von
Gradle automatisch als Project-Property exponiert → `apiKey()` in
`app/build.gradle.kts` liest sie). Fehlende Secrets = leerer Key = Kanal liefert
leer/null (by design, kein Build-Fehler).

## 7. Docker-Stack: MCP-Server + Healthchecks

- Der MCP-Server (Temp-Mail/OTP, `backend-mcp/`) ergänzt den Stack auf Port
  8001 und ist Ziel von `MCP_SERVER_URL`. E-Mails werden per REST injiziert
  (`POST /api/mcp/mail`) statt per SMTP – ausreichend für Testumgebungen.
- Alle Services haben `healthcheck:`-Definitionen; `backend`/`nodered` starten
  erst nach gesundem MQTT (`depends_on: condition: service_healthy`).
- Node-RED startet mit `nodered/flows.json` (nur Core-Nodes, kein
  node-red-dashboard-Plugin nötig).
