# 🔗 Slack-Integration (MCP) – `provectus/slack-mcp-server`

SecureGuard meldet Alarme und Status in Slack. Die Anbindung läuft über den
**Slack-MCP-Server** ([github.com/provectus/slack-mcp-server](https://github.com/provectus/slack-mcp-server),
Go-Fork von `korotovsky/slack-mcp-server`), der als eigener Dienst im
Docker-Stack läuft. Das **FastAPI-Backend ist MCP-Client** und kapselt JSON-RPC
hinter REST – App und Node-RED brauchen dadurch weder MCP noch Slack-Tokens.

```
┌──────────────┐   REST /api/slack/*   ┌──────────────────┐   MCP (JSON-RPC)   ┌───────────────────┐   Slack API   ┌───────┐
│ Android-App  │ ────────────────────▶ │ FastAPI-Backend  │ ─────────────────▶ │ slack-mcp-server  │ ────────────▶ │ Slack │
│ Node-RED     │   X-API-Key           │ backend/slack_   │  Streamable HTTP   │ (provectus Fork)  │  xoxb/xoxp/   │       │
│ curl / CI    │                       │ mcp.py           │  oder SSE          │ Port 13080        │  xoxc+xoxd    │       │
└──────────────┘                       └──────────────────┘                    └───────────────────┘               └───────┘
        ▲                                       ▲
        │  POST /api/alerts                     │  MQTT secureguard/+/alert
        └────────────── Alert-Quellen ──────────┘
```

Inhalt:

1. [Schnellstart](#1-schnellstart)
2. [Dienste & Ports](#2-dienste--ports)
3. [Slack-Tokens](#3-slack-tokens)
4. [Umgebungsvariablen](#4-umgebungsvariablen)
5. [REST-Endpunkte](#5-rest-endpunkte)
6. [Automatische Alarme](#6-automatische-alarme)
7. [Verfügbare MCP-Tools](#7-verfügbare-mcp-tools)
8. [Betrieb ohne Docker](#8-betrieb-ohne-docker)
9. [Sicherheit](#9-sicherheit)
10. [Fehlersuche](#10-fehlersuche)

---

## 1. Schnellstart

```bash
cp .env.example .env            # Slack-Abschnitt ausfüllen (Tokens, Channel)
./scripts/start-stack.sh        # baut + startet mqtt, backend, nodered, slack-mcp
./scripts/smoke-check.sh        # prüft /api/slack/health + /api/system/dependencies
```

Ohne Slack-Token startet der Server im **Demo-Modus**
(`SLACK_MCP_XOXP_TOKEN=demo`, Default in `docker-compose.yml`): Der Dienst ist
erreichbar, `tools/list` funktioniert, echte Slack-Aufrufe scheitern. So lässt
sich die Verkabelung prüfen, bevor Tokens vorhanden sind.

Erste Meldung senden:

```bash
curl -X POST http://127.0.0.1:8000/api/slack/notify \
  -H 'Content-Type: application/json' \
  -d '{"message":"SecureGuard-Stack ist online","channel":"#secureguard-alerts"}'
```

Mit gesetztem `SECUREGUARD_API_KEY` zusätzlich `-H "X-API-Key: <key>"`.

---

## 2. Dienste & Ports

| Dienst | Build/Quelle | Port | Zweck |
|--------|--------------|------|-------|
| `slack-mcp` | `slack-mcp/Dockerfile` (Release-Binary `pv-v1.0.1`) | `127.0.0.1:13080` | MCP-Server für Slack |
| `backend` | `backend/Dockerfile` | `8000` | MCP-Client + REST für App/Node-RED |

* Der MCP-Port ist bewusst **nur auf loopback** gebunden. Für Zugriff von
  anderen Rechnern `ports` in `docker-compose.yml` ändern **und**
  `SLACK_MCP_API_KEY` setzen.
* Backend → MCP-Server läuft über das Compose-Netz:
  `SLACK_MCP_URL=http://slack-mcp:13080/mcp`.
* Der User-/Channel-Cache liegt in `slack-mcp/cache/` (Volume) und überlebt
  Neustarts – ohne Cache funktionieren `#channel`/`@user`-Auflösungen nicht.

---

## 3. Slack-Tokens

Genau **eine** Variante setzen (sonst startet der Server nicht – der
Cache-Refresh schlägt ohne Credentials fehl):

| Token | Prefix | Eigenschaften |
|-------|--------|---------------|
| Bot-Token | `xoxb-` | sicherster Weg; sieht nur Channels, in die der Bot eingeladen wurde; **keine Suche** (`conversations_search_messages` nicht nutzbar) |
| User-OAuth-Token | `xoxp-` | volle Suche, liest alle Channels des Users; Aktionen erscheinen als dieser User |
| Session-Token | `xoxc-` + `xoxd-` | „Stealth" ohne App-Installation (Browser-Cookie); mächtig, entsprechend schützen |

Bot-Variante (empfohlen):

1. <https://api.slack.com/apps> → *Create New App* → *From scratch*.
2. *OAuth & Permissions* → Scopes: `channels:history`, `channels:read`,
   `chat:write`, `reactions:write`, `groups:history`, `groups:read`,
   `im:history`, `im:read`, `users:read`.
3. *Install to Workspace* → **Bot User OAuth Token** (`xoxb-…`) nach
   `SLACK_MCP_XOXB_TOKEN` in `.env`.
4. Bot in den Ziel-Channel einladen: `/invite @<bot-name>`.

Wichtig: `conversations_add_message` ist serverseitig **deaktiviert**, bis
`SLACK_MCP_ADD_MESSAGE_TOOL` gesetzt ist (Compose-Default: `true`).

---

## 4. Umgebungsvariablen

Alle Werte stehen in `.env` (siehe `.env.example`).

### Slack-MCP-Server (Container `slack-mcp`)

| Variable | Default | Bedeutung |
|----------|---------|-----------|
| `SLACK_MCP_VERSION` | `pv-v1.0.1` | Gepinnte Release-Version (Build-Arg) |
| `SLACK_MCP_TRANSPORT` | `http` | `http` (Streamable HTTP, empfohlen) oder `sse` |
| `SLACK_MCP_XOXB_TOKEN` | – | Bot-Token |
| `SLACK_MCP_XOXP_TOKEN` | `demo` | User-Token; `demo` = Smoke-Betrieb ohne Slack |
| `SLACK_MCP_XOXC_TOKEN` / `SLACK_MCP_XOXD_TOKEN` | – | Session-Token-Paar |
| `SLACK_MCP_API_KEY` | – | Bearer-Token für HTTP/SSE-Transport |
| `SLACK_MCP_ADD_MESSAGE_TOOL` | `true` | Posting freigeben: `true`, Channel-Liste oder `!C123` |
| `SLACK_MCP_ENABLED_TOOLS` | – | Nur diese Tools registrieren (leer = Default-Satz) |
| `SLACK_MCP_CACHE_TTL` | `24h` | Frische des User-/Channel-Caches |
| `SLACK_MCP_LOG_LEVEL` | `info` | `debug` für Fehlersuche |

### Backend (Container `backend`)

| Variable | Default | Bedeutung |
|----------|---------|-----------|
| `SLACK_MCP_URL` | `http://slack-mcp:13080/mcp` | MCP-Endpunkt aus Backend-Sicht |
| `SLACK_MCP_TRANSPORT` | `http` | muss zum Server-Transport passen |
| `SLACK_MCP_API_KEY` | – | identisch zum Server-Key (Bearer) |
| `SLACK_MCP_TIMEOUT` | `15` | Sekunden pro MCP-Aufruf |
| `SLACK_NOTIFY_ENABLED` | `true` | Alarm-Weiterleitung ein/aus |
| `SLACK_NOTIFY_CHANNEL` | `#secureguard-alerts` | Ziel-Channel (Name oder `C…`) |
| `SLACK_NOTIFY_MIN_SEVERITY` | `WARNING` | `INFO`, `WARNING` oder `CRITICAL` |
| `SLACK_WEBHOOK_URL` | – | Fallback: Slack Incoming Webhook |

---

## 5. REST-Endpunkte

Alle Pfade liegen am Backend (Port 8000). Schreibende Endpunkte verlangen
`X-API-Key`, sobald `SECUREGUARD_API_KEY` gesetzt ist.

| Methode | Pfad | Funktion |
|---------|------|----------|
| `GET` | `/api/slack/health?probe=true` | Konfiguration + Erreichbarkeit + Tool-Anzahl |
| `GET` | `/api/slack/tools?refresh=false` | Registrierte MCP-Tools (Cache: `SLACK_MCP_TOOLS_TTL`, 300 s) |
| `GET` | `/api/slack/channels?limit=200` | Channel-Verzeichnis (`channels_list`, CSV → JSON) |
| `POST` | `/api/slack/call` | Beliebiger Tool-Aufruf: `{"tool":"…","arguments":{…}}` |
| `POST` | `/api/slack/notify` | Meldung senden: `{"message":"…","channel":"#…","severity":"INFO","asset_id":"…","alert_type":"…"}` |
| `GET` | `/api/system/dependencies` | Abhängigkeits-Inventur (DB, MQTT, Slack-MCP, Webhook, Node-RED) |

### App-Einstellungen

Unter **Einstellungen → 🧩 Anbindungen & Abhängigkeiten** sind alle Verbindungen
hinterlegt – lokale Endpunkte (Backend, WebSocket, MQTT, MCP/Temp-Mail, LoRa,
YOLO, CKAN, Find-My, DHL, externe API-Keys als „gesetzt/nicht gesetzt") **und**
die serverseitigen Abhängigkeiten aus `GET /api/system/dependencies`. Dazu:

* Schalter **„Slack-Alarme"** (`EndpointConfig.slackEnabled`) – steuert den
  `SlackAlertForwarder`; wirkt sofort, kein App-Neustart.
* Feld **„Slack-Channel"** (`EndpointConfig.slackChannel`) – Ziel-Channel der
  App; leer = `SLACK_NOTIFY_CHANNEL` des Backends.
* **„Status prüfen"** holt die Live-Inventur vom Backend (MCP-Handshake,
  Node-RED-HTTP, MQTT-Verbindung).

MCP-Server-URL, Bot-Tokens und Tool-Freigaben bleiben bewusst serverseitig
(`.env`) – die App zeigt sie nur an und speichert keine Slack-Credentials.

Beispiele:

```bash
# Status
curl -s http://127.0.0.1:8000/api/slack/health | python3 -m json.tool

# Channel-Liste
curl -s "http://127.0.0.1:8000/api/slack/channels?limit=50" | python3 -m json.tool

# Tool-Aufruf (History eines Channels)
curl -s -X POST http://127.0.0.1:8000/api/slack/call \
  -H 'Content-Type: application/json' \
  -d '{"tool":"conversations_history","arguments":{"channel_id":"#general","limit":"10"}}'

# Alert-Meldung (formatiert mit Icon, Asset, Quelle)
curl -s -X POST http://127.0.0.1:8000/api/slack/notify \
  -H 'Content-Type: application/json' \
  -d '{"message":"Bewegung erkannt","asset_id":"AA:BB:CC:DD:EE:01","severity":"CRITICAL","alert_type":"MOVEMENT"}'
```

Antwort von `/api/slack/call`:

```json
{ "ok": true, "tool": "channels_list", "text": "id,name,…", "is_error": false, "error": null, "raw": { } }
```

---

## 6. Automatische Alarme

Drei Wege führen ohne weiteres Zutun nach Slack:

| Quelle | Pfad | Gating |
|--------|------|--------|
| `POST /api/alerts` | Background-Task → `notify_slack_alert()` | `SLACK_NOTIFY_MIN_SEVERITY` |
| MQTT `secureguard/+/alert` | MQTT-Bridge → `notify_slack_alert()` | `SLACK_NOTIFY_MIN_SEVERITY` |
| Android-App (Room-Tabelle `alerts`) | `SlackAlertForwarder` → `POST /api/slack/notify` | ab `WARNING`, nur neue Alerts |

Node-RED: Der Flow `format slack alert` → `slack notify` hängt am MQTT-Alert-Topic
(`sg_in_alert`) und postet auf `/api/slack/notify`; der Inject-Node
**„Slack Testmeldung"** prüft die Kette manuell. `X-API-Key` und Ziel-Channel
kommen aus `SG_API_KEY` bzw. `SG_SLACK_CHANNEL` (Compose-Env des Node-RED-Containers).

Meldungsformat (Markdown → Slack `rich_text`):

```
:rotating_light: *SecureGuard CRITICAL* – `MOVEMENT`
*Asset:* `AA:BB:CC:DD:EE:01`
*Meldung:* Bewegung erkannt
*Quelle:* backend/api
*Zeit:* 2026-09-04T12:00:00
```

---

## 7. Verfügbare MCP-Tools

Über `/api/slack/tools` sichtbar; Aufruf per `/api/slack/call`. Die wichtigsten:

| Tool | Zweck | Freigabe |
|------|-------|----------|
| `channels_list` | Channel-Verzeichnis (CSV) | immer |
| `channels_members` | Mitglieder eines Channels | immer |
| `conversations_history` | Nachrichten eines Channels/DM | immer |
| `conversations_replies` | Thread-Antworten | immer |
| `conversations_search_messages` | Suche (nicht mit `xoxb-`) | immer |
| `conversations_unreads` | Ungelesene Nachrichten | immer |
| `users_search` | Nutzer suchen | immer |
| `conversations_add_message` | Nachricht posten | `SLACK_MCP_ADD_MESSAGE_TOOL` |
| `reactions_add` / `reactions_remove` | Reaktionen | `SLACK_MCP_REACTION_TOOL` |
| `conversations_mark` | Als gelesen markieren | `SLACK_MCP_MARK_TOOL` |
| `conversations_draft_message` | Nativer Entwurf (nur `xoxc`/`xoxd`) | `SLACK_MCP_DRAFT_MESSAGE_TOOL` |

Lesen von Cache-abhängigen Dingen (`#channel`, `@user`) funktioniert erst, wenn
der Server seinen Cache gefüllt hat (`slack-mcp/cache/`).

---

## 8. Betrieb ohne Docker

```bash
./slack-mcp/run-local.sh                 # Demo-Modus auf 127.0.0.1:13080
SLACK_MCP_XOXB_TOKEN=xoxb-… ./slack-mcp/run-local.sh
SLACK_MCP_TRANSPORT=sse ./slack-mcp/run-local.sh
```

Das Skript löst das Binary in dieser Reihenfolge auf: `$SLACK_MCP_BIN` →
`slack-mcp/bin/slack-mcp-server-linux-<arch>` → Download (mit
`checksums.txt`-Prüfung) nach `~/.cache/secureguard/slack-mcp`.

**Air-gapped:** Binaries auf einem Rechner mit Netz spiegeln und mitnehmen:

```bash
./scripts/offline/download-slack-mcp.sh          # → slack-mcp/bin/ + offline_repo/slack-mcp/
```

`docker build` verwendet `slack-mcp/bin/` automatisch und lädt dann nichts herunter.

**Entwicklung ohne Go-Binary/Slack-Zugang:** `scripts/dev/slack-mcp-stub.py`
implementiert dasselbe MCP-Protokoll (Streamable HTTP **und** SSE) aus dem
Speicher – nützlich für Preview/CI/air-gapped. Gepostete Meldungen sind unter
`GET /stub/messages` einsehbar. Kein Ersatz für den echten Server.

```bash
python3 scripts/dev/slack-mcp-stub.py     # 0.0.0.0:13080
```

Backend lokal gegen den MCP-Server:

```bash
pip install -r backend/requirements.txt
SLACK_MCP_URL=http://127.0.0.1:13080/mcp SLACK_NOTIFY_ENABLED=true \
  uvicorn --app-dir backend main:app --host 0.0.0.0 --port 8000
```

---

## 9. Sicherheit

* **Tokens gehören nur in `.env`** (nicht versioniert, siehe `.gitignore`) bzw.
  in die Container-Umgebung – niemals in App-Code, Flows oder Commits.
* MCP-Port `13080` bleibt auf `127.0.0.1`. Wird er geöffnet, ist
  `SLACK_MCP_API_KEY` Pflicht; das Backend sendet ihn als `Authorization: Bearer`.
* Schreibende REST-Endpunkte (`/api/slack/call`, `/api/slack/notify`,
  `/api/alerts`) sind über `SECUREGUARD_API_KEY` (`X-API-Key`) geschützt.
* Posting ist serverseitig nur mit `SLACK_MCP_ADD_MESSAGE_TOOL` möglich und kann
  per Channel-Whitelist begrenzt werden (`C123,C456` bzw. `!C123`).
* Der Container läuft als Non-Root-User (`slackmcp`, UID 10001).
* Für Produktion: Reverse-Proxy mit TLS vor dem Backend (Compose-TLS siehe
  `docs/GO_LIVE.md`).

---

## 10. Fehlersuche

```bash
docker compose logs -f slack-mcp          # Server-Logs (SLACK_MCP_LOG_LEVEL=debug)
curl -s localhost:8000/api/slack/health   # reachable? tools? Fehlermeldung?
curl -s localhost:8000/api/health         # slack-Konfiguration im Überblick
docker compose exec slack-mcp slack-mcp-server --version
```

| Symptom | Ursache | Lösung |
|---------|---------|--------|
| `reachable: false`, `Slack-MCP nicht erreichbar` | Server läuft nicht / URL falsch | `docker compose ps`, `SLACK_MCP_URL` prüfen |
| Container startet und beendet sich sofort | kein Token gesetzt | `SLACK_MCP_XOXB_TOKEN`/`XOXP` setzen (oder `demo`) |
| `conversations_add_message is disabled` | Posting nicht freigegeben | `SLACK_MCP_ADD_MESSAGE_TOOL=true` |
| `not_in_channel` / `channel_not_found` | Bot nicht im Channel | `/invite @bot` bzw. Channel-ID statt Name |
| `channels_list` leer | Cache fehlt/leer | Cache-Volume prüfen, `SLACK_MCP_CACHE_TTL`, Token-Rechte |
| Suche liefert nichts | Bot-Token genutzt | `xoxp-`-Token verwenden (`search.messages` braucht User-Kontext) |
| `401 Ungültiger oder fehlender X-API-Key` | Backend-API-Key | `X-API-Key`-Header mitsenden |
| Meldung kommt nicht an, `skipped: severity` | unterhalb `SLACK_NOTIFY_MIN_SEVERITY` | Schwere anheben oder Grenze senken |

Tests: `pytest backend/tests/test_slack_mcp.py -q` (31 Tests, inkl. Fake-MCP-Server
für HTTP- und SSE-Transport).
