# Agentic OS — SecureGuard

Dateibasierte Betriebsschicht für KI-Agenten in diesem Repository.
Adaptiert aus dem Agentic-Personal-OS-Ansatz auf den Projektkontext
„Schutz- und Wiederbeschaffungssystem für mobile Werte".

## Einstieg

| Wenn du … | dann lies |
|-----------|-----------|
| … neu im Repository bist | `../AGENTS.md` |
| … wissen willst, woran Arbeit gemessen wird | `GOALS.md` |
| … eine Aufgabe suchst | `Tasks/` (nach `priority` sortieren) |
| … etwas notieren willst, ohne es auszuarbeiten | `BACKLOG.md` |
| … einen Ablauf brauchst | `Workflows/` |
| … ein Subsystem verstehen musst | `Knowledge/` |
| … wissen willst, was zuletzt belegt wurde | `Evals/` |

## Verzeichnisse

```
agent-os/
├── GOALS.md      G1–G5. Jede Aufgabe zahlt auf genau ein Ziel ein.
├── BACKLOG.md    Schnellerfassung. Wird über Workflows/backlog-verarbeiten.md geleert.
├── Tasks/        Eine Datei je Aufgabe, YAML-Frontmatter, Pflichtfeld „verification".
├── Workflows/    backlog-verarbeiten · funktion-umsetzen · release · stoerung-im-feld
├── Knowledge/    architektur · aktionsprotokoll · 3d-konsole · design-system
└── Evals/        Session-Reviews mit Abschnitt „Nicht nachgewiesen".
```

Skills liegen bewusst außerhalb, unter `../.agents/skills/`, weil dort mehrere
Agenten-Runtimes sie erwarten.

## Die drei Regeln

1. **Kein Code ohne Zielbezug.** Findet sich kein Ziel in `GOALS.md`, ist
   entweder die Aufgabe falsch oder das Ziel fehlt. Beides klären, bevor
   gearbeitet wird.
2. **Kein „fertig" ohne Nachweis.** Jede Aufgabe trägt im Frontmatter das Feld
   `verification` mit einem ausführbaren Befehl. Siehe
   `../.agents/skills/verification/SKILL.md`.
3. **Wissen gehört ins Repository, nicht in den Chatverlauf.** Was beim
   nächsten Mal wieder gebraucht wird, wandert nach `Knowledge/`.

## Aufgabenzustände

`n` offen · `s` in Arbeit · `b` blockiert · `d` fertig

Prioritätsschranken: höchstens 3 offene P0, höchstens 5 offene P1.
Wird die Schranke überschritten, wird herabgestuft, nicht ignoriert.

## Aktueller Stand

Siehe `Evals/2026-09-04-3d-console-und-agentic-os.md` — insbesondere den
Abschnitt „Nicht nachgewiesen".
