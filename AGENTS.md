# AGENTS.md — SecureGuard Enterprise (Dinge88 / Agent „Le Guck")

Operating instructions for any coding agent working in this repository
(Claude Code, Codex, Arena Agent Mode, Pi, OpenClaw, …).

> Dieses Dokument ist die **einzige Quelle der Wahrheit** für Arbeitsweise,
> Qualitätsschranken und Sicherheitsregeln. Runtime-Wrapper
> (`CLAUDE.md`, `CODEX.md`) verweisen nur hierher.

---

## 1. Was dieses Produkt ist

SecureGuard Enterprise ist ein **Schutz- und Wiederbeschaffungssystem für
mobile Werte** (E-Scooter, Lastenräder, Schlüsselfinder, Tablets, ESP32-Nodes).

Es besteht aus vier Artefakten in einem Monorepo:

| Pfad | Artefakt | Sprache / Stack |
|------|----------|-----------------|
| `app/` | Android-App (Hauptprodukt) | Kotlin, Jetpack Compose, Hilt, Room + SQLCipher |
| `console3d/` | 3D Operations Center | JavaScript, Three.js (MIT), Vite |
| `backend/` | Ingest- & Kommando-API | Python, FastAPI, SQLite, MQTT |
| `firmware/` | Gateway-/Tag-Firmware | C++ (Arduino/ESP32) |

Betriebsumgebung: `docker-compose.yml` (Mosquitto, Node-RED, Backend).

**Nicht verhandelbar:** Es werden ausschließlich **gewhitelistete** Assets
gesucht. Kein Tracking Dritter. Jede Änderung, die diese Grenze aufweicht,
ist abzulehnen.

---

## 2. Workspace-Struktur (Agentic OS)

```
Dinge88/
├── AGENTS.md              # diese Datei – gemeinsames Verhalten
├── CLAUDE.md / CODEX.md   # Runtime-Wrapper (verweisen hierher)
├── .agents/skills/        # Skill-Packs nach Agent-Skills-Standard
├── agent-os/
│   ├── GOALS.md           # Produktziele, an denen Arbeit gemessen wird
│   ├── BACKLOG.md         # Schnellerfassung, unstrukturiert
│   ├── Tasks/             # Aufgaben mit YAML-Frontmatter
│   ├── Workflows/         # wiederverwendbare Abläufe
│   ├── Knowledge/         # Architektur- und Protokollwissen
│   └── Evals/             # Session-Reviews, Qualitätsmessung
├── app/ backend/ console3d/ firmware/ docs/ scripts/
```

---

## 3. Arbeitsablauf (verbindlich)

### 3.1 Vor der ersten Zeile Code

1. `agent-os/GOALS.md` lesen — trägt die Aufgabe auf ein Ziel ein?
2. `agent-os/Knowledge/` nach dem betroffenen Subsystem durchsuchen.
3. Betroffene Dateien **lesen**, bevor sie geändert werden.
4. Bei Unklarheit: **fragen**, nicht raten. Sicherheitsprodukt.

### 3.2 Während der Arbeit

- Bestehende Konventionen schlagen persönliche Vorlieben.
- Deutschsprachige Nutzertexte, englische Bezeichner im Code — wie im Bestand.
- Jede neue öffentliche Funktion bekommt einen KDoc-/JSDoc-Kommentar, der
  das **Warum** erklärt, nicht das Was.
- Keine neuen Abhängigkeiten ohne Notwendigkeit. Neue Libraries müssen
  kostenfrei und permissiv lizenziert sein (MIT/Apache-2.0/BSD).

### 3.3 Vor „fertig"

Verifikationspflicht — siehe `.agents/skills/verification/SKILL.md`.
Eine Aufgabe gilt erst als erledigt, wenn Nachweis erbracht ist:

| Änderung betrifft | Nachweis |
|-------------------|----------|
| `app/` | `./gradlew :app:assembleDebug` **und** `:app:testDebugUnitTest` grün |
| `console3d/` | `npm run build` grün + manueller Smoke-Test im Browser |
| `backend/` | `pytest backend/tests` grün |
| `firmware/` | Kompilat oder begründeter Hinweis, warum nicht prüfbar |
| UI-Änderung | Beschreibung *und* Screenshot/Preview-Link |

Kein Nachweis = nicht fertig. „Sollte funktionieren" ist kein Nachweis.

---

## 4. Build- und Prüfbefehle

```bash
# Android
./gradlew :app:assembleDebug            # Debug-Build
./gradlew :app:testDebugUnitTest        # Unit-Tests
./gradlew :app:lint                     # Android Lint
./gradlew :app:assembleRelease          # Release-APK (CI-Pfad)

# 3D Operations Center
cd console3d && npm ci && npm run dev   # Live-Konsole auf :5173
cd console3d && npm run build           # Bundle nach console3d/dist
bash scripts/sync-console3d.sh          # Bundle in die App-Assets spiegeln

# Backend
pip install -r backend/requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --app-dir backend
python3 scripts/seed-demo-data.py --seed 88    # Demo-Flotte (12 Assets)
python3 scripts/seed-demo-data.py --live       # laufender Detektionsstrom
pytest backend/tests

# Gesamtstack
docker compose up -d
```

---

## 5. Architektur-Leitplanken

### 5.1 Android

- **Schichten:** `presentation/` (Compose + ViewModel) → `data/repository` →
  `data/local` (Room/SQLCipher) und `services/` (Kanäle, Agent).
- **State:** ViewModel exponiert `StateFlow`, Screens sind zustandslos.
- **DI:** ausschließlich Hilt. Kein manuelles Service-Locator-Muster.
- **Design-System:** alle UI-Bausteine kommen aus
  `presentation/designsystem/` (`SgCard`, `SgPill`, `SgMetricTile`, …).
  Keine nackten `Card`/`Button`-Aufrufe mit Ad-hoc-Farben mehr.
- **Farben/Abstände:** nur Tokens aus `designsystem/Tokens.kt` und
  `presentation/theme/Color.kt`. Keine Hex-Literale in Screens.
- **RBAC:** jede mutierende Aktion prüft `RoleManager.require(Permission.…)`.

### 5.2 Aktionen (Kommandopfad)

Ein Befehl geht immer denselben Weg:

```
UI → ViewModel → AgentService.sendAction(asset, command)
      → 1. MQTT   2. WebSocket   3. BLE/GATT
      → sonst: OfflineQueue (Room, überlebt Neustart)
```

Neue Befehle **immer** in beiden Katalogen ergänzen und identisch benennen:
`app/.../presentation/ui/common/ActionCatalog.kt` **und**
`console3d/src/data/catalog.js`. Wire-Befehle müssen zur Firmware in
`firmware/secureguard_esp32/secureguard_esp32.ino` passen.

### 5.3 3D Operations Center

- Engine: **Three.js**, MIT-Lizenz, keine Tier-/Lizenzkosten.
- Datenquellen in dieser Reihenfolge: `native` (WebView-Bridge der App) →
  `backend` (FastAPI) → `simulation` (immer verfügbar, damit die Konsole nie
  leer ist).
- Der Browser spricht **nur relative URLs** an. Kein `localhost` im
  Client-Code.
- Das gebaute Bundle liegt in `app/src/main/assets/console3d/` und wird über
  einen virtuellen https-Origin ausgeliefert (ES-Module funktionieren nicht
  über `file://`).

---

## 6. Sicherheitsregeln (hart)

1. **Keine Geheimnisse im Repo.** API-Keys kommen aus `local.properties` /
   Umgebungsvariablen und landen über `buildConfigField` im Build.
2. **Keine Aufweichung der Whitelist.** Suche nur über `assets`-Tabelle.
3. **Kritische Aktionen** (`MOTOR_OFF`, `RESTART`) brauchen in jeder
   Oberfläche einen Bestätigungsdialog und dürfen nicht offline eingereiht
   werden.
4. **Audit-Log** (`AuditLogService`) darf nie umgangen werden.
5. **Keine neuen Netzwerkziele** ohne Eintrag in `EndpointConfig` und
   Dokumentation in `docs/`.
6. Der Zweck des Systems ist Wiederbeschaffung eigener Werte. Funktionen zur
   Überwachung fremder Personen werden nicht gebaut.

---

## 7. Task-Format

```yaml
---
title: [Handlungsorientierter Titel]
area: [android|console3d|backend|firmware|infra|docs]
priority: [P0|P1|P2|P3]
status: n            # n=offen, s=in Arbeit, b=blockiert, d=fertig
created: YYYY-MM-DD
verification: [Befehl oder Nachweis, der die Erledigung belegt]
refs:
  - agent-os/Knowledge/....md
---

# Titel

## Kontext
Welches Ziel aus GOALS.md wird bedient? Was ist der Ist-Zustand?

## Umsetzung
- [ ] Schritt
- [ ] Schritt

## Nachweis
Konkrete Ausgabe/Screenshot/Testlauf.

## Verlauf
- YYYY-MM-DD: Entscheidung, Blocker, Ergebnis.
```

Prioritäten: **P0** diese Woche kritisch (max. 3) · **P1** wichtig mit Frist
(max. 5) · **P2** normal · **P3** nice-to-have.

---

## 8. Skills

Skills liegen unter `.agents/skills/<name>/SKILL.md` und folgen dem
[Agent-Skills-Standard](https://agentskills.io/home). Der Agent lädt zuerst
nur Name + Beschreibung und öffnet die Datei erst, wenn der Skill gebraucht
wird (progressive disclosure).

| Skill | Wann verwenden |
|-------|----------------|
| `secureguard-ui-review` | Jede sichtbare Änderung an App oder Konsole |
| `compose-design-system` | Neue oder geänderte Compose-Oberflächen |
| `three-scene-review` | Änderungen an der 3D-Szene |
| `action-protocol-change` | Neuer/geänderter Gerätebefehl |
| `release-apk` | Release, Signierung, CI-Artefakte |
| `systematic-debugging` | Fehler, dessen Ursache unklar ist |
| `verification` | Immer vor „fertig" |
| `tdd` | Neue Geschäftslogik in `services/` oder `backend/` |

Bootstrap für Claude/OpenClaw (einmalig, optional):

```bash
mkdir -p .claude && ln -sfn ../.agents/skills .claude/skills
ln -sfn .agents/skills skills
```

---

## 9. Git & Auslieferung

- Branch-Namen: `feature/…`, `fix/…`, `chore/…`; Agent-Sessions arbeiten auf
  dem ihnen zugewiesenen Branch und wechseln ihn nicht.
- Commits: Imperativ, deutsche oder englische Zusammenfassung, Kontext im
  Body. Kein „wip".
- Keine generierten Artefakte committen — **Ausnahme**:
  `app/src/main/assets/console3d/` (das gebaute 3D-Bundle wird bewusst
  mitgeliefert, damit die App offline funktioniert).
- Release-APK entsteht über `.github/workflows/build-release.yml`.

---

## 10. Umgang mit Unsicherheit

Wenn zwei Wege plausibel sind und die Entscheidung teuer rückgängig zu machen
ist: **Optionen benennen, Empfehlung geben, Rückfrage stellen.**
Wenn die Entscheidung billig reversibel ist: **entscheiden, dokumentieren,
weiterarbeiten.** Siehe `.agents/skills/verification/SKILL.md`.
