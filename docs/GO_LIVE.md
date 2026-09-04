# SecureGuard Enterprise – Go-Live (alle Parts)

**Stand:** 2026-08-27 · Branch `arena/01a0412d-dinge88` · Audit-Fixes siehe `docs/FEHLER_MANGEL_LISTE.md`

> **Passwörter / PINs / Keystore:** legt der **Anwender selbst** fest.  
> Kein Script und keine App-Logik erzeugen Produktionsgeheimnisse.

---

## 1. Teile im Überblick

| Part | Inhalt | Status |
|------|--------|--------|
| **A · Android-App** | 18 Screens, Agent, 9+ Kanäle, SQLCipher, PIN | ✅ Code |
| **B · Backend** | FastAPI Assets/Detections/Crowd/MCP/WS/Health | ✅ |
| **C · MQTT** | Mosquitto + Auth/TLS-Beispiele | ✅/⚙️ |
| **D · Node-RED** | flows.json Telemetrie/Commands | ✅ |
| **E · ESP32** | PlatformIO Firmware | ⚙️ flashen |
| **F · Signing** | Keystore-Script (User-Passwörter) | ✅/⚙️ |
| **G · Tests** | Unit + Compose UI | ✅/⚙️ lokal/CI |
| **H · DSGVO** | Consent, Datenauskunft, Retention, Löschen | ✅ |
| **I · Monitoring** | Health-Screen + `/api/health` + smoke-check | ✅ |
| **J · Offline** | `scripts/offline/*` | ✅ |
| **K · Slack (MCP)** | `slack-mcp/` + `/api/slack/*` + App-Screen + Node-RED-Flow | ✅ |

---

## 2. Schnellstart (Host mit JDK/SDK/Docker)

```bash
# 0) Integritäts-Check (ohne Secrets)
./scripts/prepare-all.sh

# 1) Backend-Stack (optional .env anlegen: SECUREGUARD_API_KEY / CORS_ORIGINS)
cp .env.example .env   # Werte setzen – schützt POST-Endpunkte per X-API-Key
./scripts/start-stack.sh
./scripts/smoke-check.sh

# 2) App-Keys (Anwender) – Kopie von Beispiel
cp local.properties.example local.properties
# sdk.dir=… und optionale API-Keys / MQTT_* / BACKEND_BASE_URL setzen

# 3) Unit-Tests + Debug-APK
./gradlew :app:testDebugUnitTest :app:assembleDebug

# 4) Optional Release (Passwörter selbst wählen)
export KEYSTORE_PASSWORD='…'
export KEY_PASSWORD='…'
./scripts/create-release-keystore.sh
./gradlew :app:assembleRelease

# 5) Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Emulator-URLs (Android Emulator → Host)

| Dienst | URL in App-Settings |
|--------|---------------------|
| MQTT | `tcp://10.0.2.2:1883` |
| WebSocket | `ws://10.0.2.2:8000/ws` |
| Backend | `http://10.0.2.2:8000` |

Physisches Gerät: IP des PCs im LAN verwenden.

---

## 3. App-Konfiguration (Runtime)

**Einstellungen → Backend & Broker**

- MQTT URL / User / Pass (**Pass vom Anwender**)
- WebSocket, MCP, Backend, LoRa-Gateway, YOLO, CKAN, Find-My-Proxy
- „Endpunkte speichern“ → „Backend-Sync“

**Security-Center**

- PIN setzen (**Anwender wählt PIN**, min. 4 Zeichen)
- SQLCipher-Status / Key-Fingerprint

**Datenschutz (DSGVO)**

- Einwilligung Checkbox
- **Datenauskunft** → JSON unter App-External-Files/`privacy/`
- **Retention 90d**
- **Alle lokalen Daten löschen (Art. 17)**

**System-Health**

- Einstellungen → Erweiterte Werkzeuge → System-Health / Monitoring

---

## 4. MQTT Produktion

```bash
# Beispiele anpassen – User/Pass selbst wählen
cp mosquitto/config/passwd.example mosquitto/config/passwd
# mosquitto_passwd -c mosquitto/config/passwd IHR_USER
# acl.example → acl, TLS-Zertifikate ops
```

Release-Build: `network_security_config` verbietet Cleartext → `ssl://broker:8883`.

---

## 4b. Slack (MCP) – Alarme in Slack

```bash
# .env: mindestens ein Slack-Token + Ziel-Channel
SLACK_MCP_XOXB_TOKEN=xoxb-…          # Bot in den Channel einladen: /invite @bot
SLACK_NOTIFY_CHANNEL=#secureguard-alerts
SLACK_MCP_ADD_MESSAGE_TOOL=true      # Posting serverseitig freigeben
SLACK_MCP_API_KEY=bitte-selbst-setzen  # Pflicht, sobald Port 13080 geöffnet wird

docker compose up -d --build slack-mcp backend
curl -s localhost:8000/api/slack/health     # reachable: true + Tool-Anzahl
```

* Dienst `slack-mcp` (provectus-Fork, Release `pv-v1.0.1`) läuft auf
  `127.0.0.1:13080`; das Backend ist MCP-Client und stellt `/api/slack/*`.
* Automatisch gemeldet wird ab `SLACK_NOTIFY_MIN_SEVERITY` (Default `WARNING`)
  aus `POST /api/alerts`, MQTT `secureguard/+/alert` und der App
  (`SlackAlertForwarder`).
* Ohne Token: Demo-Modus (`SLACK_MCP_XOXP_TOKEN=demo`) – Verkabelung prüfbar.
* Details, Tokens, Fehlersuche: [SLACK_MCP.md](SLACK_MCP.md).

---

## 5. CI

Workflow: `.github/workflows/build-release.yml` (Basis wie auf `main`).

Optionale Erweiterungen (Unit-Tests, `arena/**`-Trigger):
siehe [CI_ENHANCEMENTS.md](CI_ENHANCEMENTS.md) – manuell mergen, falls der
Push von Workflow-Dateien durch GitHub-App-Rechte blockiert wird.

- `assembleDebug` / `assembleRelease` (+ Keystore-Secrets vom Anwender)
- Lokal: `./gradlew testDebugUnitTest assembleDebug`

Secrets (Anwender setzt Werte):

- `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

---

## 6. Checkliste vor Pilot

- [ ] `./scripts/start-stack.sh` + smoke grün  
- [ ] `local.properties` mit `sdk.dir` + benötigten URLs/Keys  
- [ ] `./gradlew testDebugUnitTest assembleDebug` grün  
- [ ] APK installiert, Permissions erteilt  
- [ ] PIN vom Anwender gesetzt  
- [ ] Endpunkte gespeichert, Backend-Sync ok  
- [ ] Optional: Slack-MCP konfiguriert (`/api/slack/health` → `reachable: true`)  
- [ ] Optional: ESP32 geflasht, MQTT Auth aktiv  
- [ ] Optional: Release-Keystore + `assembleRelease`  

---

## 7. Docs-Index

| Doc | Thema |
|-----|--------|
| [READY_TO_GO_CHECKLISTE.md](READY_TO_GO_CHECKLISTE.md) | Detailstatus aller Dienste |
| [TESTING.md](TESTING.md) | Unit / Compose Tests |
| [SQLCIPHER_AND_SIGNING.md](SQLCIPHER_AND_SIGNING.md) | DB-Verschlüsselung + Signing |
| [OFFLINE_SETUP.md](OFFLINE_SETUP.md) | Air-gapped Build |
| [api-docs.yaml](api-docs.yaml) | OpenAPI Backend |
