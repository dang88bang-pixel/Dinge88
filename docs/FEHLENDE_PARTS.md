# 🔍 Übersicht: Fehlende & unvollständige Teile

> **Stand:** 26.08.2026 · **Code-Stand:** Commit `fea0da5` + Vervollständigungs-Commits (Branch `arena/01a03e66-dinge88`)
> **Methode:** Statische Analyse (README-Ansprüche ↔ Code, Querverweise, Verdrahtung, Firmware/Backend-Gegenprüfung).
> Ein Compile-Check war in dieser Umgebung nicht möglich (kein Android-SDK/Netzwerkzugriff) — Syntaxfehler können daher nicht ausgeschlossen werden.

---

## 🎉 Umsetzung (26.08.2026, gleicher Branch)

Die folgenden Punkte aus der ursprünglichen Liste sind inzwischen **umgesetzt**:

| # | Fix | Commit-Bereich |
|---|-----|----------------|
| 1 | ESP32: `onWrite`-Handler für Command-Characteristic + gemeinsame `handleCommand()`-Befehlskette (MQTT + BLE) + `respond()` (Antworten per MQTT **und** BLE-Notify) | Firmware |
| 2 | Telemetrie-Schema abgestimmt: Firmware sendet jetzt zusätzlich `motor` (echter Relay-Status); `Telemetry`-Modell um `wifiRssi`, `loraRssi`, `uptimeSeconds`, `ipAddress`, `device` erweitert; Parser dokumentiert | Firmware + App |
| 3 | CI: neuer `test`-Job (`testDebugUnitTest` + `lintDebug`) **vor** dem APK-Build (`build: needs: test`), Reports als Artefakt — ✅ **aktiv in `.github/workflows/build-release.yml`** | CI |
| 4 | CI: API-Key-Secrets via `ORG_GRADLE_PROJECT_*` an Build & Test durchgereicht — im selben Vorschlag enthalten | CI |
| 5 | `IMPLEMENTIERUNGS_INVENTUR.md` erstellt (7 dokumentierte Abweichungen) | Doku |
| 6 | `nodered/flows.json` erstellt (Core-Nodes: secureguard/# → Kanal-Switch, Debug-Sidebar, Zähler, Test-Injects) | Docker |
| 7 | MCP-Server implementiert (`backend-mcp/`, FastAPI WebSocket-JSON-RPC, kompatibel mit `MCPClient`) + als Compose-Service auf Port 8001 | Backend |
| 8 | `AssetPagingSource`: als documented utility eingestuft (reactive-Flow-UI bleibt; Begründung in IMPLEMENTIERUNGS_INVENTUR.md §5) | Doku |
| 10 | `local.properties.example`: 4 ungenutzte Keys explizit als „derzeit nicht verdrahtet" markiert | App-Config |
| 13 | `LearningEngineTest` ergänzt (6 Tests: Muster, Prädiktion, Intervall, Per-Asset-Wahrscheinlichkeit) | Tests |
| 14 | docker-compose: Healthchecks für alle Services + `depends_on: service_healthy` | Docker |
| 17 | `MCPClient`: `http(s)://` → `ws(s)://`-Normalisierung | App |
| 19/20 | README-Zahlen korrigiert (16 Screens / 15 ViewModels / 16 Routes; SDK android-35) + Firmware-/Docker-Kapitel aktualisiert | Doku |
| 21 | ESP32: BLE2902/CCCD-Descriptor an der Notify-Characteristic | Firmware |

**Offen (bewusst):** #9 RBAC-User-Management (Feature), #11/#12 Mosquitto- & Backend-Härtung
(Pilot-Defaults dokumentiert), #15/#16 Cleartext-Restriction & R8 (braucht Build-Verifikation),
#18 i18n-Ausbau, #22 GPS am Asset (Hardware), #23 Keystore-Secrets (muss im GitHub-Repo gesetzt werden).

---

## ⚡ executive Summary

Das Repository ist erstaunlich vollständig (App, Backend, Firmware, CI, Doku), hat aber **23 konkrete Lücken**.
Die kritischsten: **Firmware-Funktionslücken** (BLE-Befehle ohne Handler, Telemetrie-Schema passt nicht zur App),
**fehlende Komponenten** (Node-RED-Flows, MCP-Server, Implementierungsinventur) und
**CI baut APKs, führt aber nie Tests aus**.

| # | Problem | Bereich | Priorität |
|---|---------|---------|-----------|
| 1 | ESP32: BLE-Command-Characteristic ohne `onWrite`-Handler → App-Befehle über GATT kommen nie an | Firmware | 🔴 P0 |
| 2 | Telemetrie-Schema Firmware ↔ App inkompatibel (fuel/motor/tires/hours/km/lat/lon werden nie geliefert) | Firmware ↔ App | 🔴 P0 |
| 3 | CI-Workflow baut nur APKs — `test`, `lint` laufen nie | CI | 🔴 P0 |
| 4 | CI übergibt keine API-Keys → CI-APKs haben leerene `WIGLE_API_KEY` etc. | CI | 🟡 P1 |
| 5 | `IMPLEMENTIERUNGS_INVENTUR.md` wird referenziert, existiert nicht | Doku | 🟡 P1 |
| 6 | Node-RED: kein `flows.json` → Dashboard leer | Docker-Stack | 🟡 P1 |
| 7 | MCP-Server (Temp-Mail) fehlt komplett im Stack | Backend | 🟡 P1 |
| 8 | `AssetPagingSource` wird nirgends benutzt (Paging 3 totes Feature) | App | 🟡 P1 |
| 9 | RBAC nur vorbereitet: hardcodierter ADMIN-User, kein User-Management | App | 🟡 P1 |
| 10 | 4 dokumentierte Config-Keys ohne Verdrahtung (LoRa-Gateway, YOLO, …) | App-Config | 🟡 P1 |
| 11 | Mosquitto: `allow_anonymous true`, kein TLS | Infra | 🟡 P1 |
| 12 | Backend: keine Authentifizierung, CORS `*` | Backend | 🟡 P1 |
| 13 | Nur 1 Unit-Test, kein `androidTest`-Verzeichnis | Tests | 🟡 P1 |
| 14 | Kein Healthcheck in `docker-compose.yml`, `depends_on` ohne Bereitschafts-Check | Docker | 🟢 P2 |
| 15 | `usesCleartextTraffic="true"` ohne Network-Security-Config | App-Security | 🟢 P2 |
| 16 | R8/Minify deaktiviert (`isMinifyEnabled = false`) | App-Security | 🟢 P2 |
| 17 | MCPClient erwartet `ws://`, Beispiel-Config liefert `http://` | App | 🟢 P2 |
| 18 | i18n: nur 5 Strings übersetzt, restliche UI-Texte hardcodiert | App | 🟢 P2 |
| 19 | README-Drift: „13 Screens / 12 VMs / 12 Routes" — real 16 / 15 / 16 | Doku | 🟢 P2 |
| 20 | README-Build: „SDK 34 / Build-Tools 34" — CI nutzt android-35 / 35.0.0 | Doku | 🟢 P2 |
| 21 | ESP32: kein CCCD-Descriptor (BLE2902) → Notify clientabhängig wackelig | Firmware | 🟡 P1 |
| 22 | ESP32 `POSITION` liefert nur IP+RSSI (kein GPS am Gateway) — echte Positions­kette unvollständig | Firmware | 🟢 P2 |
| 23 | Release-Keystore/Secrets (KEYSTORE_BASE64 …) noch nicht gesetzt → unsignierte APKs | CI/Release | 🟡 P1 |

---

## A. Fehlende Dateien & Komponenten

| Was | Wo erwartet | Status |
|-----|-------------|--------|
| **`IMPLEMENTIERUNGS_INVENTUR.md`** | `app/build.gradle.kts:194` und `:201` (Kommentare: „siehe … Abweichungen") | ❌ Datei existiert nicht im Repo |
| **Node-RED-Flows** | `docker-compose.yml` mountet `./nodered:/data`; README: „Node-RED Dashboard (Port 1880)" | ❌ Nur `.gitkeep` — kein `flows.json`, Dashboard nach `docker compose up` leer |
| **MCP-Server (Temp-Mail-Backend)** | `MCPClient.kt` (JSON-RPC: `create_inbox`, `wait_for_otp`), `MCP_SERVER_URL` in `local.properties.example` zeigt auf `api.example.com` | ❌ Weder im Repo noch als Service in `docker-compose.yml` — Feature „Temporäre E-Mail/OTP" ohne Server nicht lauffähig |
| **Backend-URL für Crowd-Kanal im Compose-Stack** | `CrowdService.kt:39-43` leitet die Backend-URL aus `WEBSOCKET_URL` ab | ⚠️ Funktional, aber der compose-interne Host (`http://backend:8000`) ist aus dem Android-Gerät nicht erreichbar — für echte Pilotbetriebs-Topologie fehlt ein dokumentierter Exposing-Mechanismus |
| **Release-Keystore** | `app/build.gradle.kts` (Fallback Debug-Key), CI-Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | ❌ Noch nicht gesetzt (by design) → GitHub-Releases derzeit mit unsignierter/Debug-signierter APK |
| **Legacy-Test-Report/Codecov, CONTRIBUTING, CHANGELOG** | gängige Projektparts | ❌ nicht vorhanden (optional) |

---

## B. Firmware (ESP32) ↔ App — funktionale Lücken

### B1. BLE-Command-Characteristic ohne Handler 🔴
`secureguard_esp32.ino:141-144` erzeugt `pCommandChar` mit `PROPERTY_WRITE`, **aber es gibt keinen
`onWrite`-Callback** (kein `setWriteCallback`/`BLECharacteristicCallbacks` in der ganzen Datei).
Die App schickt Aktionen u. a. über BLE/GATT (`AgentService.sendAction()` → TelemetryService-Write) —
diese Befehle kommen auf der Firmware **nie** an. Nur der MQTT-Weg (`callback()` ab Zeile 241) funktioniert.

### B2. Telemetrie-Schema-Mismatch 🔴
- **Firmware sendet** (Zeile 202-216): `{"type","battery","wifi_rssi","lora_rssi","uptime","ip","device"}`
- **App erwartet** (`TelemetryService.kt:150-165`): `{"battery","fuel","motor","tires","hours","km","lat","lon"}`

Folge: `fuelPercent`, `motorOk`, `tiresOk`, `operatingHours`, `kilometers`, `lat/lon` bleiben **immer leer**.
Es fehlt entweder die Firmware-Erweiterung (Sensoren: Tank/Kraftstoff, Motorstatus, Reifen, Betriebsstunden,
Kilometerstand, GPS) oder eine App-seitige Anpassung des Schemas.

### B3. Weitere Firmware-Lücken
- **Kein BLE2902/CCCD-Descriptor** an der Notify-Characteristic → Notifications funktionieren je nach Client-Stack nicht zuverlässig.
- **`POSITION`** antwortet nur mit Geräte-IP + WiFi-RSSI (ESP32 hat kein GPS) — die Kette „Asset-Position via Telemetrie" ist damit strukturell unvollständig.
- `device_filter.xml`/USB-Weg: App-seitig vorhanden, Firmware-seitig gibt es **keinen Serial-Ausgabe-Modus** für den `UsbSerialService` (liest Zeilen, aber es ist kein Protokoll definiert).

---

## C. Backend & Docker-Stack

| Punkt | Details |
|-------|---------|
| **Node-RED** | s. A. — ohne Flows |
| **MCP-Server** | s. A. — fehlt als Service |
| **Keine Healthchecks** | `docker-compose.yml` hat `depends_on`, aber keine `healthcheck:`-Definitionen → Backend kann vor Mosquitto „ready" sein (MQTT-Bridge verbindet dann nicht/retry) |
| **Backend ohne Auth** | `main.py`: alle Endpunkte offen, `CORS allow_origins=["*"]` — für Produktion fehlt API-Key/JWT |
| **Mosquitto** | `allow_anonymous true` (Zeilen 9 & 14), kein TLS 8883 — die Konfigdatei selbst vermerkt die Produktions-Härtung als To-do |
| **Backend-Skala** | SQLite + `--reload` im Compose-Command → Pilot-Konfig, kein Multi-Worker-Setup |

---

## D. App: nicht verdrahtete Konfiguration & tote Bausteine

### D1. Config-Keys ohne Empfänger 🟡
`local.properties.example` dokumentiert 4 Keys, die **nirgends gelesen werden**
(kein `buildConfigField`, keine Code-Referenz — 0 Treffer):
- `LORA_GATEWAY_URL` → der LoRa-Kanal nutzt stattdessen Helium-API + MQTT
- `YOLO_SERVER_URL` → optische Kamera-Erkennung/Objekterkennung per YOLO-Server fehlt komplett
- `OPEN_DATA_API_URL` → CKAN-Demo-URL ist hardcodiert
- `FIND_MY_PROXY_URL` → nicht vorhanden

➜ Entweder BuildConfig-Felder + Nutzung ergänzen oder aus der Beispiel-Datei entfernen.

### D2. Toter Code / unvollständige Features
| Baustein | Befund |
|----------|--------|
| `AssetPagingSource.kt` | **0 Referenzen** — AssetList nutzt direktes `observeAll()`; „Paging für große Listen" (README) inaktiv |
| RBAC (`RoleManager`) | Rollen/Permissions definiert, aber ViewModels erzeugen hardcodiert `User(id="local", name="Admin", role=ADMIN)` (`ActionsViewModel.kt:64`, `AssetDetailViewModel.kt:94`); kein Login, kein User-Management-UI. Im Code selbst als „vorbereitet" dokumentiert |
| `MCPClient.kt:79` | Baut einen OkHttp-**WebSocket** auf die rohe `MCP_SERVER_URL` — Beispielwert ist aber `http://…` → Verbindungsfehler; fehlende Scheme-Normalisierung (`http→ws`) |
| i18n | `values-de`/`values-en` enthalten je **nur 5 Strings** (App-Name + 4 Notification-Channels); sämtliche Screen-Texte sind hardcodiert — englische UI existiert faktisch nicht |

### D3.ByKey-Abhängigkeiten (leer = Feature still)
Ohne `local.properties` sind per Default leer: `WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`,
`GOOGLE_API_KEY`, `MQTT_BROKER_URL`, `WEBSOCKET_URL`, `MCP_SERVER_URL` → Kanäle WiGle/OCM/Netatmo/Google,
MQTT, WebSocket, Crowd (abgeleitet) und Temp-Mail liefern dann leer/null. **Gewolltes Verhalten**, aber für einen
Pilotbetrieb ist die komplette `local.properties` ein „fehlender Part".

---

## E. CI & Qualitätssicherung

| Punkt | Befund |
|-------|--------|
| **Tests laufen nie** 🔴 | `.github/workflows/build-release.yml` führt nur `assembleDebug`/`assembleRelease` aus — kein `testDebugUnitTest`, kein `lint`. Der vorhandene Test (`MacValidationTest.kt`) wird im CI nie ausgeführt |
| **API-Keys im CI** 🟡 | Der Build liest Keys aus `gradle.properties`/`local.properties`/`-P` — der Workflow übergibt keine davon (nur Keystore-Secrets) → CI-APKs haben garantiert leere Keys. Entweder Secrets→`-P` verdrahten oder bewusst dokumentieren |
| **Testabdeckung** 🟡 | Genau **1** Unit-Test. Keine Tests für `AgentService` (Kern!), `LearningEngine`, `AuthManager` (PBKDF2), DAOs, Repository. Kein `app/src/androidTest/`-Verzeichnis, obwohl Espresso/Compose-Test-Dependencies eingebunden sind |
| **Lint** | kein `lintDebug`-Step, Lint-Ergebnisse werden nicht publiziert |

---

## F. Dokumentations-Drift (README ↔ Code)

| README sagt | Real | Datei |
|-------------|------|-------|
| „UI-Screens (13)" | **16 Screens** (zusätzlich: Terminal, SensorFusion, Security, Esp32Config) | `presentation/ui/*` |
| „13 Screens + 12 ViewModels" | **15 ViewModel-Dateien** | `presentation/ui/*/` |
| „12 Routes" | **16 Konstanten** in `Routes` | `NavItems.kt:26-43` |
| Build & CI: „SDK: android-34, Build-Tools 34.0.0" | CI installiert **android-35 / 35.0.0**; `targetSdk 35` | Workflow `env:` vs. README Abschnitt Build |
| „Detection-Kanal LoRa über Helium" | stimmt, aber `LORA_GATEWAY_URL` (eigenes Gateway) dokumentiert und ungenutzt | s. D1 |

*(Die 4 Extra-Screens sind ein Mehr, kein Mangel — aber die Übersicht im README ist unvollständig.)*

---

## G. Sicherheit / Produktionsreife (bewusst offene Punkte)

- `AndroidManifest.xml:69` → `android:usesCleartextTraffic="true"` **global**; keine `networkSecurityConfig` (Pilot-OK, Produktion: nur Broker/Backend-Hosts erlauben).
- `app/build.gradle.kts:91` → `isMinifyEnabled = false` im Release: kein R8/Obfuscation, obwohl `proguard-rules.pro` existiert.
- Mosquitto/Backend ohne Auth & TLS (s. C).
- `AuthManager` PIN: solide (PBKDF2, 100k Iterationen), aber **keine Möglichkeit zum PIN-Wechsel** ohne vorheriges `disablePin` → `configurePin` (Minor).

---

## ✅ To-do-Checkliste (priorisiert)

### P0 — Funktionsketten schließen
- [x] ESP32: `BLECharacteristicCallbacks::onWrite` für `pCommandChar` implementieren (Befehle an selbe `handleCommand()`-Logik wie MQTT) ✅ erledigt
- [x] Telemetrie-Schema abstimmen: Firmware sendet jetzt `motor` (echter Relay-Status); App-Modell um Firmware-Felder erweitert; simulierte Werte (fuel/tires/hours/km) bewusst NICHT erfunden ✅ erledigt
- [x] CI: `./gradlew testDebugUnitTest lintDebug` als eigener `test`-Job vor dem APK-Build ✅ aktiv in `.github/workflows/build-release.yml`

### P1 — Fehlende Bausteine ergänzen
- [x] `IMPLEMENTIERUNGS_INVENTUR.md` schreiben ✅ erledigt
- [x] `nodered/flows.json` (Dashboard: MQTT-Topics → Kanal-Switch/Debug/Zähler + Test-Injects) ✅ erledigt
- [x] MCP-Server bereitstellen (`backend-mcp/`, FastAPI-WebSocket-JSON-RPC mit `create_inbox`/`wait_for_otp`/`extract_magic_link`) ✅ erledigt
- [x] CI: API-Key-Secrets → `ORG_GRADLE_PROJECT_*` an den Gradle-Build durchreichen ✅ aktiv in `.github/workflows/build-release.yml`
- [ ] Mosquitto-Härtung umsetzen (Users, TLS 8883) + Backend-Auth (API-Key) für den Pilotbetrieb — **offen** (bricht Pilot-Defaults; vor Produktion nötig)
- [x] `AssetPagingSource`: Entscheidung dokumentiert (reactive Flow bleibt für kleine Whitelist, Paging als Utility — IMPLEMENTIERUNGS_INVENTUR.md §5) ✅ erledigt (dokumentiert)
- [x] docker-compose: `healthcheck` für alle Services, `depends_on: condition: service_healthy` ✅ erledigt
- [ ] Release-Secrets (`KEYSTORE_BASE64`, …) im GitHub-Repo setzen, damit Releases signiert sind — **offen** (nur repo-admin seitig möglich)
- [x] BLE2902-Descriptor in Firmware ergänzen ✅ erledigt
- [x] Tests ausbauen (Schritt 1): `LearningEngineTest` mit 6 Tests ✅ Teilerledigt — weitere Tests (AuthManager, AgentService) folgen

### P2 — Konsistenz & Politur
- [x] `local.properties.example`: 4 ungenutzte Keys explizit als „derzeit nicht verdrahtet" markiert ✅ erledigt
- [x] `MCPClient`: `http(s)://` → `ws(s)://`-Normalisierung ✅ erledigt
- [x] README-Zahlen korrigieren (16 Screens / 15 ViewModels / 16 Routes; SDK android-35 im CI-Abschnitt) ✅ erledigt
- [ ] `networkSecurityConfig` statt globalem Cleartext; R8 für Release aktivieren — **offen** (braucht Build-Verifikation)
- [ ] Strings in `values-de`/`values-en` auslagern (i18n wird aktuell nur behauptet) — **offen**
- [ ] PIN-Wechsel-UI (alt → neu) in `SecurityScreen` ergänzen — **offen**
- [ ] CONTRIBUTING.md / CHANGELOG.md ergänzen (optional) — **offen**
