---
title: Aktionskatalog zwischen Kotlin, Konsole und Firmware gegen Drift absichern
area: infra
priority: P0
status: d
created: 2026-09-04
verification: "python3 scripts/check-action-drift.py → Exit 0; Gegenprobe mit verfälschtem wire → Exit 1; CI-Job 'contract'"
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

- [x] `scripts/check-action-drift.py` schreiben:
      - Wire-Befehle aus `ActionCatalog.kt` per Regex auf `wireCommand = "…"` ziehen
      - Wire-Befehle aus `catalog.js` per Regex auf `wire: '…'` ziehen
      - Von der Firmware die in `handleCommand` behandelten Literale ziehen
      - Nur reine Szenen-Aktionen der Konsole (`SWEEP`, `FOCUS`, `GEOFENCE`,
        `HEATMAP`) sind erlaubt, ohne Firmware-Gegenstück zu existieren
      - Exit-Code 1 mit lesbarem Diff bei Abweichung
- [x] Skript als eigener CI-Job `contract` in `ci/workflows/ci.yml`, der vor
      allen Build-Jobs läuft
- [x] Ergebnisformat so wählen, dass der Fehlertext direkt sagt, welche Datei
      nachgezogen werden muss
- [x] Über den reinen Wire-Vergleich hinaus auch Titel, Risikostufe,
      `requiresOnline`, `queueable`, `acceptsNote` und die Existenz des
      Bestätigungsdialogs vergleichen

## Nachweis

```bash
python3 scripts/check-action-drift.py          # Exit 0 auf sauberem Stand
# Gegenprobe: eine Aktion in catalog.js umbenennen -> Exit 1 mit Diff
```

## Verlauf

- 2026-09-04: Angelegt. Aktuell stimmen alle drei Kataloge überein
  (ALARM, LIGHT, MOTOR_OFF, RESTART, CONFIG, MESSAGE).
- 2026-09-04: **Erledigt.** Skript geschrieben und scharf gestellt. Der erste
  Lauf hat sofort echten Drift gefunden: Die Aktion `RESTART` hieß in der App
  „Gerät neu starten", in der Konsole „Neustart" — dieselbe Aktion mit zwei
  Namen. In `console3d/src/data/catalog.js` auf den App-Wortlaut angeglichen.
  Gegenprobe bestanden: `wire: 'LIGHT'` → `'BLINK'` verfälscht ⇒ Exit 1 mit
  `[LIGHT] wire: Android 'LIGHT' ≠ Web 'BLINK'`, nach Rücknahme wieder Exit 0.
  Prüfumfang jetzt: 8 Fernbefehle × (wire, Titel, Risiko, requiresOnline,
  queueable, acceptsNote, Bestätigungsdialog) + 4 lokale Lagebild-Aktionen +
  9 Firmware-Befehle. Zusätzliche Regel: eine als `critical` eingestufte
  Aktion ohne Bestätigungsdialog ist ein Fehler.
