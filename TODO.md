# ✅ To-Do: SecureGuard Enterprise 100 % fertig stellen

Stand: 26.08.2026 · Basis: `main` (fea0da5) · Priorität: 🔴 hoch · 🟡 mittel · 🟢 niedrig

> Regel: Jede abgeschlossene Aufgabe mit `x` abhaken und im CHANGELOG eintragen.
> Erst wenn **alle 🔴 und 🟡** erledigt sind, ist das Projekt „100 % fertig".

---

## 1. 🚀 CI / Release-Pipeline (Kernanforderung)

- [x] 🔴 **GitHub Action: Bei jedem neuen Release APK bauen + anhängen** – Workflow
  `.github/workflows/build-release.yml` reagiert auf `release: created`, Tag-Push `v*`,
  PRs und `main`; hängt `release.apk` + `SHA256SUMS.txt` + `BUILD_INFO.txt` an das Release
  (neu in diesem PR)
- [ ] 🔴 **PR in `main` mergen** – `release`-Ereignisse nutzen immer den Workflow aus dem
  Head des Default-Branches; ohne Merge bleibt der alte Workflow aktiv und neue Releases
  bekommen **keine** APK
- [ ] 🔴 **v1.0.7 nachträglich veröffentlichen**: Der Tag `v1.0.7` existiert, aber das
  Release fehlt (CI-Lauf ist fehlgeschlagen). Nach dem Merge: Release anlegen
  (`gh release create v1.0.7 --notes "..."`) → CI hängt die APK automatisch an.
  *Alternativ: Tag löschen und neu als `v1.0.8` setzen (empfohlen, da `main` weitergezogen ist).*
- [ ] 🔴 **Orphan-Kommitte von v1.0.7 entscheiden**: Tag `v1.0.7` zeigt auf `288645a`
  (nicht in `main`!), enthält u. a. `backend/schema.sql`, `docs/databases.md`,
  `firmware/README.md`, `HELIUM_API_KEY` + „APK-Delivery-per-Orphan-Branch"-Hack in
  `app/build.gradle.kts`. → Nützlich content per `git cherry-pick`/Merge übernehmen,
  den Branch-Hack **nicht** übernehmen (Release-Anhang ersetzt ihn), Tag neu setzen.
- [ ] 🟡 **CI-Lauf grün prüfen** (`gh run list`) und sicherstellen, dass Gradle- +
  Android-SDK-Cache greifen (zweiter Lauf sollte deutlich schneller sein)
- [ ] 🟢 **`develop`-Branch-Trigger wieder aktivieren**, falls ein Feature-Branch-Flow
  gewünscht ist (aktuell: nur `main`)
- [ ] 🟢 **AAB-Task ergänzen** (`bundleRelease`), falls später Play Store vorgesehen ist
  (aktuell nur APK)

## 2. 🔐 Signing & Secrets (Repo-Einstellungen, nicht im Code)

- [ ] 🔴 **Release-Keystore einmalig erstellen und sichern** (offline, Passwort notieren):
  ```bash
  keytool -genkey -v -keystore secureguard-keystore.jks -keyalg RSA -keysize 2048 \
    -validity 10000 -alias secureguard -storepass <PASSWORT> -keypass <PASSWORT>
  ```
  ⚠️ Unsignierte APKs können **nicht** über bestehende Installationen aktualisiert werden –
  für ein produktionsreifes Projekt Pflicht.
- [ ] 🔴 **Secrets im Repo setzen** (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64` (base64 der .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= secureguard),
  `KEY_PASSWORD`
- [ ] 🟡 **Optionale API-Key-Secrets** setzen, damit die Release-APK voll funktionsfähig ist:
  `WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`,
  `MQTT_BROKER_URL`, `WEBSOCKET_URL`, `MCP_SERVER_URL` (und ggf. `HELIUM_API_KEY` nach Punkt 1)
- [ ] 🟡 **Installationstest**: Gebaute `release.apk` auf echtem Android-Gerät (8.0+)
  installieren, PIN-Setup, BLE-Scan, Karte & Agent durchklicken

## 3. 📱 App (Android)

- [ ] 🔴 **i18n / Übersetzungen**: ~240 UI-Strings (17 Compose-Dateien unter
  `presentation/ui/`) sind hartkodiert; `values-de/` und `values-en/` haben je nur 5 Strings.
  → Alle UI-Texte nach `res/values*/strings.xml` auslagern, `stringResource()` nutzen
- [ ] 🔴 **Netzwerk-Sicherheit**: `usesCleartextTraffic="true"` ist global →
  `res/xml/network_security_config.xml` anlegen und gezielt nur benötigte
  HTTP-Endpoints (z. B. lokaler Mosquitto/Backend) erlauben
- [ ] 🟡 **R8/ProGuard für Release aktivieren** (`isMinifyEnabled = true`) + Regeln pflegen
  (Moshi/Kotlin-Reflection/Retain-Listen prüfen)
- [ ] 🟡 **Crash-Reporting** (z. B. Sentry, DSGVO-konfiguriert) – aktuell keine Fehlerdiagnose
- [ ] 🟡 **Instrumented Tests** (`app/src/androidTest` fehlt komplett) – min. Smoke-Test:
  App-Start, PIN-Screen, Asset-Liste (Compose-Test + Espresso vorhanden als Dependency)
- [ ] 🟢 **App-Icon**: Nur vektor/adaptive Icon vorhanden (OK ab API 26, minSdk=26 → akzeptabel);
  falls Fallback gewünscht: PNG-Mipmaps ergänzen
- [ ] 🟢 **App-Speicher/Cleanup**: `DatabaseCleanup`-Einstellungen in Settings anzeigbar/machbar
  prüfen; Backup-Datei-Format dokumentieren
- [ ] 🟢 **Play-Store-Ready** (falls geplant): Data-Safety-Form, Datenschutzseite online,
  AAB-Build, Screenshot-Paket

## 4. 🖥️ Backend (FastAPI)

- [ ] 🔴 **`backend/schema.sql` nach `main` holen** (liegt nur im Orphan-Commit `288645a`);
  `main.py` aktuell erzeugt Schema zur Laufzeit – beides vereinheitlichen
- [ ] 🔴 **API-Schutz**: Alle Endpoints sind offen (keine Auth) → API-Key-/Token-Middleware
  (zumindest für POST-Endpoints) + `CORS allow_origins=["*"]` auf konkrete Origins schränken
- [ ] 🟡 **`docs/api-docs.yaml` synchron halten**: Dokumentiert 9 Pfade, Backend hat 13
  Endpoints (+ `/ws` WebSocket fehlen in der Doku)
- [ ] 🟡 **Produktions-Hardening Mosquitto** (`mosquitto/config/mosquitto.conf`):
  `allow_anonymous false` + `mosquitto_passwd` + TLS-Listener 8883 (Cert via Let's Encrypt)
- [ ] 🟡 **Deploy**: Docker-Stack auf VPS/Heimserver betreiben (URL dann in App-Secret
  `WEBSOCKET_URL`/`MQTT_BROKER_URL`), Health-Check `/api/health` + Monitoring (Uptime)
- [ ] 🟢 **SQLite-Backup** (cron: `.backup` nach `data/`) + Retention für Detektionen
- [ ] 🟢 **Logging/Rate-Limiting** für Crowdsourcing-Endpoints (`/api/crowd/*`)

## 5. 📟 Firmware (ESP32)

- [ ] 🔴 **`firmware/README.md` nach `main` holen** (liegt nur im Orphan-Commit)
- [ ] 🟡 **Build-/Test-Setup für die Firmware**: PlatformIO-Project (`platformio.ini`) +
  CI-Task `pio ci`, damit der `.ino` automatisiert kompiliert (inkl. `LoRa.h`-Abhängigkeit)
- [ ] 🟡 **OTA-Update-Mechanismus** (ESP32 `ArduinoOTA` oder HTTPS-OTA) – aktuell nur
  USB-Flash möglich
- [ ] 🟡 **Hardware-Test auf echtem Board** (SX1278 868 MHz, Pins SS=5/RST=14/DIO0=2):
  LoRa→MQTT-Relay, BLE-GATT (Telemetrie + Command), Alarm-Ausgang GPIO2
- [ ] 🟢 **Schutz/Keying für BLE** (z. Zt. offenes GATT) + Watchdog-Status-LED-Verhalten dokumentieren

## 6. 🎛️ Node-RED / Dashboard

- [ ] 🔴 **Node-RED-Flow anlegen**: `nodered/` ist leer (nur `.gitkeep`), aber
  `docker-compose.yml` mountet `./nodered:/data` und das README bewirbt das Dashboard.
  → `flows.json` mit MQTT-Telemetrie → Dashboard-Seite (Assets, Alerts, Live-Karte) committen
- [ ] 🟡 **Node-RED im Release-Dokument erwähnen** (Login/URL) + Persistenz-Backup

## 7. 🧪 Tests (Qualität)

- [ ] 🟡 **Unit-Test-Suite aufbauen** (aktuell **nur 1 Test**: `MacValidationTest.kt`):
  - `LearningEngine` (Adaption/Priorisierung)
  - `SecureGuardRepository` (DAO-Verhalten, Room-In-Memory)
  - `EncryptionService` (AES/GCM Roundtrip)
  - `AuthManager` (PBKDF2, 5-Versuch-Limit, Auto-Lock)
  - `OfflineQueue` (Persistenz, Retry)
  - `ApiNodeManager` (Circuit-Breaker, Rate-Limit)
- [ ] 🟡 **Backend-Tests** (pytest): 13 Endpoints + MQTT→WS-Bridge mit Test-MQTT-Broker
- [ ] 🟢 **CI-Test-Step** ergänzen: `./gradlew :app:testDebugUnitTest` + `pytest` vor dem
  Release-Build (Fail-fast)

## 8. 📚 Dokumentation

- [ ] 🔴 **`IMPLEMENTIERUNGS_INVENTUR.md` schreiben/erstellen** – wird in
  `app/build.gradle.kts` und `MqttService.kt` zitiert („Abweichungen"), existiert aber nicht
- [ ] 🟡 **`CHANGELOG.md`** anlegen (v1.0.0 → aktuell) und pro Release pflegen
  (`keep-a-changelog`-Format)
- [ ] 🟡 **README-Aufräumarbeiten**: Verweis auf `develop`-Branch entfernen (falls Punkt 1
  nicht umgesetzt wird), „BETRIEBSVEREINBARUNG.md" (in Commit-Historie erwähnt) neu schreiben
  oder Referenzen streichen
- [ ] 🟢 **Datenschutz-Seite online** (DSGVO, Crowdsourcing-Consent-Flow beschreiben) +
  Impressum, falls öffentlich verteilt

## 9. 📦 Ops / Betrieb

- [ ] 🟡 **Monitoring/Alerting**: Backend-Health + MQTT-Broker-Status (z. B. Uptime-Kuma)
- [ ] 🟡 **Update-Strategie App dokumentieren**: Neue Version = neuer Tag (`vX.Y.Z`) →
  CI baut + hängt APK an Release → User laden „latest release". **Keine** APKs manuell verteilen
- [ ] 🟢 **Retention-Strategie** (Detektionen 30/90/365 d) im Backend + App abgleichen
- [ ] 🟢 **Katastrophenfall**: Keystore-Backup + Repo-Backup (offline) dokumentiert vorliegen

---

## ✅ Definition of „100 % fertig"

1. 🔴 Alle Punkte der Sektionen 1–4 erledigt
2. Release-Pipeline nachweislich grün: Neues Release → signierte `release.apk` + Prüfsummen
   automatisch angehängt (Screenshot/Link im Changelog)
3. App auf echtem Gerät installiert und durchgetestet (Sektion 2, letzter Punkt)
4. `CHANGELOG.md` + `IMPLEMENTIERUNGS_INVENTUR.md` gepflegt
5. Alle 🟡 abgeschlossen (🟢 = nice-to-have, kann folgen)
