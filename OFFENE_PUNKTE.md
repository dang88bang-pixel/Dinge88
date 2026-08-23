# 📌 Ergänzungsliste – noch zu ergänzende Parts & Module

**Zielvorgabe:** alle Teile **aktiv** (laufend bereitgestellt) · **angebunden** (vollständig verdrahtet) · **ausführbar** (ohne Hidden-Mocks nutzbar)

**Stand:** 2026-08-23 · Basis `3af19a7` (CI grün, APKs gebaut, Live-Dashboard aktiv)
**Legende:** P1 = kritisch für Zielvorgabe · P2 = wichtig · P3 = optional · Aufwand: S < 1 h · M einige Stunden · L > ½ Tag

---

## A · Live-Stack & Bereitstellung

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| A1 | **Stack-Wiederanlauf** | Sandbox-Resets zwischen Turns stoppen Broker/Backend/Node-RED (Worker lief z. T. weiter) | `tools/start-stack.sh` ausführen; Skript um Health-Auto-Wait ergänzen | P1 | S |
| A2 | **Datei-Upload-Kanal** (für UI-Design `code.html`) | Backend-Endpunkte (`POST/GET /api/upload`) bereits vorbereitet & kompiliert, **ungetestet, uncommitted**; UI-Karte fehlt | Endpunkte testen + committen, Upload-Karte ins Dashboard, Broadcast-Event verdrahten | **P1** | S |
| A3 | **Nutzer-Design `code.html` mergen** | Datei kam 3× nicht durch (Arena-Anhang wird von Sandbox-Resets verschluckt) | Nach A2: Upload über das Dashboard im Browser → Design analysieren & in `frontend/index.html` mergen | **P1** | M |
| A4 | **Watchdog** (Auto-Restart innerhalb laufender Sandbox) | Prozesse sterben bei Turns; Neustart manuell | Optional: Supervisor-Loop im Gateway-Worker-Prozess (prüft Ports, startet Kinder neu) | P3 | M |

## B · App ↔ Backend (größte echte Anbindungs-Lücke)

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| B1 | **BackendSyncService** (neues App-Modul) | App speichert Assets/Detections/Alerts **nur lokal** (Room); Backend-DB ist eine parallele Welt (Web-Dashboard) | REST-Sync (Retrofit vorhanden): Assets/CRUD-Abgleich, Detection-Push, Alert-Pull; Konfliktregel: letzter Timestamp gewinnt | **P1** | L |
| B2 | **QR→Backend-Kette** | QR-Scan legt Asset nur in Room an | Nach Scan: direkte Registrierung im Backend (`POST /api/assets`) | P2 | S |
| B3 | **App-WebSocket zum Backend** | `WebSocketService` existiert (URL via `WEBSOCKET_URL`), aber kein Reconnect-Backoff/Resubscribe der eigenen Assets | Backoff + Statusanzeige in Einstellungen | P2 | M |

## C · Ausführbarkeit bedingter Funktionen

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| C1 | **MCP-Server (TempMail)** | `MCPClient` fertig, aber es läuft **kein** MCP-Server → Kette ohne externe URL inaktiv | Mini-MCP-Server (Tools `create_inbox`, `wait_for_otp`, `extract_magic_link`) als Backend-Modul `mcp_server.py` + Anleitung `MCP_SERVER_URL` | **P1** | M |
| C2 | **Pilot-Endpunkte für Remote-Kanäle** | LoRa/Optik/Urban/Crowd erwarten externe Endpunkte; ohne sie = inaktiv (korrekt, aber nicht demonstrierbar) | Reference-Pilot-Implementierung im Backend (`/pilot/lora|optical|urban|crowd`) — klar als Pilot gekennzeichnet, mit realen Datenquellen-Anbindung (z. B. govdata für Urban) | P2 | M |
| C3 | **API-Keys** (WiGle, OpenChargeMap, Netatmo, Google, DHL) | Ohne Keys fail-soft inaktiv | Nutzerseitig zu konfigurieren (`local.properties`) — Anleitung reicht | P3 | S |
| C4 | **Helium-Mirror** | Standard `helium-api.stakejoy.com` aus Sandbox nicht prüfbar | Erreichbarkeit aus produktivem Netz verifizieren oder Eigen-Proxy | P3 | S |

## D · Backend-Vervollständigung

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| D1 | **Asset-REST vollständig** | Nur `GET/POST` (INSERT OR REPLACE) | `PUT /api/assets/{id}` + `DELETE /api/assets/{id}` (+ Broadcast) | P2 | S |
| D2 | **OpenAPI-Doku aktuell** | `docs/api-docs.yaml` kennt resolve/clear/upload nicht | Neue Endpunkte nachtragen | P2 | S |
| D3 | **Health-Details** | Health zeigt nur `mqtt: true` | `broker`, `db`, `uploads`, Worker-Seen-Age ergänzen | P3 | S |
| D4 | **API-Auth** | Endpunkte offen (Pilot ok) | Bearer-Token-Guard (optional aktivierbar via ENV) | P3 | M |

## E · Release & CI

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| E1 | **Release-Veröffentlichung** | Release-Job läuft nur bei `v*`-Tag (noch nie getriggert) | Tag `v1.1.0` setzen → signierte Release-APK als GitHub-Release | P2 | S |
| E2 | **Firmware-CI-Prüfung** | `.ino` wird nicht automatisch kompiliert | Arduino-CLI-Compile-Step im Workflow (optionaler Job) | P3 | M |

## F · Dokumentation

| # | Part/Modul | Ist-Zustand | Zu ergänzen | P | Aufwand |
|---|---|---|---|---|---|
| F1 | **Diese Liste pflegen** | neu | Status-Spalten abhaken bei Fertigstellung; in `INVENTURLISTE.md` verlinken | P2 | S |
| F2 | **Inventur nachziehen** | UI-Sektion steht; Upload/MCP/Pilot fehlen | Nach A2/C1/C2 Sektionen ergänzen | P2 | S |

---

## Reihenfolge-Empfehlung (Zielvorgabe maximal schnell erreichen)

1. **A1** Stack aktiv → 2. **A2** Upload fertig → 3. **A3** dein `code.html`-Design mergen
4. **C1** MCP-Server (TempMail ausführbar) → 5. **B1** BackendSync (App↔Backend angebinden)
6. **C2** Pilot-Endpunkte (Remote-Kanäle demonstrierbar) → 7. **D1+D2** REST/Doku → 8. **E1** Release-Tag
