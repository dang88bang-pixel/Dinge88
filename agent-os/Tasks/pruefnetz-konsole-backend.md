---
title: Automatisierte Prüfung von Konsole und Backend aufbauen
area: infra
priority: P0
status: d
created: 2026-09-04
verification: "bash scripts/verify-all.sh → alle Stufen grün (168 Konsolentests, 42 Backend-Tests, Drift-Gate, Bundle-Abgleich)"
refs:
  - console3d/test/README.md
  - scripts/verify-all.sh
  - agent-os/Tasks/ci-workflows-aktivieren.md
---

# Prüfnetz für Konsole und Backend

## Kontext

Ziel **G4 — Vertrauenswürdig, weil nachvollziehbar**.

Die 3D-Konsole war 537 Zeilen `main.js` plus Store, Simulation, Brücke und
vier UI-Module — und keine einzige automatisierte Prüfung. Jede Änderung war
ein Blindflug: Ob ein Klick noch beim richtigen Ziel ankommt, ob der
Bestätigungsdialog wirklich blockiert, ob eine Aktion nach Abbruch trotzdem
gesendet wird, ließ sich nur durch Hinsehen feststellen. Bei einer Anwendung,
die Motoren abschaltet, ist das die falsche Grundlage.

## Umsetzung

- [x] Vitest + jsdom in `console3d/` (`npm test`, `npm run test:watch`)
- [x] `test/setup/boot.js`: lädt den `<body>` des **echten** `index.html` in
      jsdom, kontrolliert die Renderschleife über eine manuelle Frame-Pumpe
- [x] `test/setup/world.mock.js`: WebGL-Szene als aufzeichnende Attrappe —
      Tests belegen damit, dass Bedienschritte im Lagebild ankommen
- [x] Fachlogik: `catalog` (12), `store` (23), `simulation` (22), `geo` (9),
      `native` (20), `api` (21)
- [x] `app.test.js` (46): zwölf vollständige Interaktionsketten gegen das
      gebootete HUD
- [x] `native-boot.test.js` (15): Betrieb in der WebView der App inklusive
      Befehlszustellung und Quittierung über die Brücke
- [x] `backend/tests/test_contract.py` (35): jeder Endpunkt, API-Key-Schutz,
      WebSocket, Fehlerpfade
- [x] `scripts/verify-all.sh` als eine Eingabe für alles Prüfbare
- [x] CI: Job `contract` vorgeschaltet, `npm test` im Job `console3d`

## Nachweis

```bash
bash scripts/verify-all.sh
# ▶ Befehlssatz – Drift … ✔
# ▶ 3D-Konsole – Tests            168 passed (8 Dateien)
# ▶ 3D-Konsole – Bundle bauen     652K
# ▶ 3D-Konsole – Bundle gegen App-Assets   aktuell
# ▶ Backend – Tests               42 passed
```

## Verlauf

- 2026-09-04: Erledigt. Zwei echte Fehler sind dabei aufgefallen und behoben:
  1. `sparkline()` in `src/ui/dom.js` nahm an, `canvas.getContext('2d')`
     liefere immer einen Kontext. Bei `null` — im Browser möglich, wenn das
     Kontingent an 2D-Kontexten erschöpft ist — riss die Ausnahme die gesamte
     Renderschleife des HUD mit. Eine Dekoration hätte die ganze Konsole
     eingefroren. Jetzt steigt die Funktion still aus.
  2. Die Aktion `RESTART` hieß in App und Konsole unterschiedlich
     (siehe `aktionskatalog-drift-schutz.md`).
- Nicht abgedeckt und bewusst nicht vorgetäuscht: die WebGL-Darstellung
  (kein Browser in der Arbeitsumgebung — `npx playwright install chromium`
  scheitert am blockierten Download) und die Compose-Oberfläche
  (kein JDK/Android-SDK; siehe `ci-workflows-aktivieren.md`).
