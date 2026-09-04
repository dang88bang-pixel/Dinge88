# Workflow: Backlog verarbeiten

Auslöser: „Backlog aufräumen", „Backlog verarbeiten", „mach Aufgaben draus".

---

## 1. Erfassen

`agent-os/BACKLOG.md` lesen und jeden Eintrag unter „Offen" herausziehen.
Einträge unter „Ideen" nur anfassen, wenn ausdrücklich gewünscht.

## 2. Kontext holen

Für jeden Eintrag:

- `agent-os/GOALS.md` — auf welches Ziel zahlt das ein? Kein Ziel → Rückfrage
  oder streichen.
- `agent-os/Knowledge/` — gibt es dazu bereits dokumentiertes Wissen?
- Betroffenen Code kurz ansehen. Ein Backlog-Eintrag beschreibt oft ein
  Symptom, nicht die Ursache.

## 3. Dubletten prüfen

`agent-os/Tasks/*.md` auflisten und Frontmatter vergleichen. Gleiches `area`
plus inhaltlich > 60 % Überschneidung → nicht neu anlegen, sondern die
bestehende Aufgabe ergänzen.

## 4. Unklares klären

Fehlt Kontext, Priorität oder ein prüfbarer Nachweis: **anhalten und fragen.**
Nicht raten. Konkret fragen:

- „Was genau muss passieren, damit *X* erledigt ist?"
- „Ist das P0 (diese Woche) oder P2 (planbar)?"
- „Womit weisen wir nach, dass es funktioniert?"

## 5. Aufgaben anlegen

Je bestätigtem Eintrag eine Datei `agent-os/Tasks/<sprechender-name>.md` nach
der Vorlage in `AGENTS.md` Abschnitt 7. Pflicht ist das Feld `verification`:
ein Befehl oder ein konkreter Nachweis. Aufgaben ohne prüfbaren Nachweis
werden nicht angelegt.

## 6. Backlog aufräumen

Verarbeitete Einträge aus „Offen" entfernen. Was erledigt wurde, wandert
in den Abschnitt „Erledigt (Archiv)".

## 7. Zusammenfassen

Kurzbericht ausgeben:

```
Verarbeitet: 8 Einträge
Neu angelegt: 5 Aufgaben (1×P0, 2×P1, 2×P2)
Zusammengeführt: 2 (Dubletten zu bestehenden Aufgaben)
Rückfragen offen: 1
```

## Prioritätsschranken

Nach dem Anlegen prüfen: mehr als 3 offene P0 oder mehr als 5 offene P1?
Dann hinweisen und Herabstufung vorschlagen. Wenn alles dringend ist, ist
nichts dringend.
