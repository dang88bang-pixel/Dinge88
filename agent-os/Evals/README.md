# Evals — Session-Reviews

Verifikation ist im Agentic OS keine Nettigkeit, sondern die Bedingung dafür,
dass „fertig" etwas bedeutet. Hier wird festgehalten, was tatsächlich
nachgewiesen wurde — und was nicht.

---

## Wann ein Review entsteht

- Nach jeder Session, die Code geändert hat
- Nach jedem Release
- Nach jeder Störung im Feld

Dateiname: `YYYY-MM-DD-<kurzer-titel>.md`.

## Vorlage

```markdown
# YYYY-MM-DD — Titel

## Auftrag
Was war gefordert, in den Worten des Auftraggebers.

## Geändert
- Pfad — was und warum

## Nachgewiesen
| Prüfung | Befehl | Ergebnis |
|---------|--------|----------|
| ... | ... | grün/rot |

## Nicht nachgewiesen
Was aus welchem Grund ungeprüft blieb. Ehrlich sein — dieser Abschnitt ist
der wertvollste.

## Erkenntnisse
Was beim nächsten Mal anders laufen sollte. Wenn allgemeingültig:
nach agent-os/Knowledge/ übertragen.

## Folgeaufgaben
- [ ] ...
```

## Bewertungsraster

Jede Session wird in drei Achsen bewertet:

| Achse | Frage |
|-------|-------|
| **Zielbezug** | Zahlte die Arbeit auf ein Ziel aus `GOALS.md` ein? |
| **Nachweis** | Gibt es für jede Behauptung einen ausführbaren Beleg? |
| **Hinterlassenschaft** | Ist der Workspace danach aufgeräumter als vorher? |

Rot in „Nachweis" bedeutet: Die Arbeit ist nicht fertig, egal wie viel Code
entstanden ist.
