---
title: Aktionskatalog zwischen Kotlin, Konsole und Firmware gegen Drift absichern
area: infra
priority: P0
status: n
created: 2026-09-04
verification: "scripts/check-action-drift.py läuft in CI und schlägt bei Abweichung fehl"
refs:
  - agent-os/Knowledge/aktionsprotokoll.md
---

# Drift-Schutz für den Aktionskatalog

## Kontext

Ziel **G1 — Ein Werkzeug, das im Ernstfall funktioniert** und
**G4 — Vertrauenswürdig, weil nachvollziehbar**.

Derselbe Befehlssatz existiert an drei Stellen:

| Ort | Datei |
|-----|-------|
| Android | `app/src/main/java/com/secureguard/enterprise/presentation/ui/common/ActionCatalog.kt` |
| 3D-Konsole | `console3d/src/data/catalog.js` |
| Firmware | `firmware/secureguard_esp32/secureguard_esp32.ino` |

Läuft einer davon weg, sendet die Oberfläche Befehle, die das Gerät nicht
kennt — und der Nutzer merkt es erst im Ernstfall. Das ist der teuerste
denkbare Fehlerfall dieses Produkts.

## Umsetzung

- [ ] `scripts/check-action-drift.py` schreiben:
      - Wire-Befehle aus `ActionCatalog.kt` per Regex auf `wireCommand = "…"` ziehen
      - Wire-Befehle aus `catalog.js` per Regex auf `wire: '…'` ziehen
      - Von der Firmware die in `handleCommand` behandelten Literale ziehen
      - Nur reine Szenen-Aktionen der Konsole (`SWEEP`, `FOCUS`, `GEOFENCE`,
        `HEATMAP`) sind erlaubt, ohne Firmware-Gegenstück zu existieren
      - Exit-Code 1 mit lesbarem Diff bei Abweichung
- [ ] Skript in `.github/workflows/build-release.yml` als eigenen Schritt
      vor dem Gradle-Build einhängen
- [ ] Ergebnisformat so wählen, dass der Fehlertext direkt sagt, welche Datei
      nachgezogen werden muss

## Nachweis

```bash
python3 scripts/check-action-drift.py          # Exit 0 auf sauberem Stand
# Gegenprobe: eine Aktion in catalog.js umbenennen -> Exit 1 mit Diff
```

## Verlauf

- 2026-09-04: Angelegt. Aktuell stimmen alle drei Kataloge überein
  (ALARM, LIGHT, MOTOR_OFF, RESTART, CONFIG, MESSAGE).
