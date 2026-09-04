# Workflow: Funktion umsetzen

Vom Auftrag bis zum belegten Ergebnis. Gilt für jede Änderung, die mehr als
eine Datei berührt.

---

## Phase 1 — Verstehen (kein Code)

1. Aufgabe in `agent-os/Tasks/` lesen oder anlegen.
2. Zielbezug prüfen (`agent-os/GOALS.md`). Ohne Ziel kein Code.
3. Bestehenden Code lesen: **alle** betroffenen Dateien, nicht nur die
   offensichtliche.
4. Blast Radius benennen: Welche Schichten sind betroffen?
   `presentation` → `data` → `services` → Firmware-Protokoll?
5. Bei Protokoll- oder Datenmodelländerungen: Skill
   `action-protocol-change` anwenden.

**Abbruchkriterium:** Wenn nach diesem Schritt nicht in zwei Sätzen sagbar
ist, was sich ändern soll, ist die Aufgabe nicht reif. Rückfrage stellen.

## Phase 2 — Planen

Kurzplan schreiben (in die Aufgabe, unter „Umsetzung"):

- Betroffene Dateien mit einem Satz je Datei
- Reihenfolge (Datenmodell → Repository → ViewModel → UI)
- Wie wird verifiziert?
- Was wird **nicht** angefasst?

Bei teuren, schwer reversiblen Entscheidungen (Datenbankschema, Wire-Protokoll,
Abhängigkeiten): Optionen mit Empfehlung vorlegen und bestätigen lassen.

## Phase 3 — Bauen

Reihenfolge von innen nach außen:

1. **Modell/Datenbank** — bei Room-Schemaänderung Migration schreiben, nie
   `fallbackToDestructiveMigration`.
2. **Repository/Service** — Geschäftslogik, testbar, ohne Compose-Bezug.
3. **ViewModel** — `StateFlow`, keine UI-Typen im Zustand.
4. **UI** — zustandslose Composables, Bausteine aus
   `presentation/designsystem/`.

Regeln währenddessen:

- Kleine, kompilierbare Schritte. Nicht sieben Dateien gleichzeitig halb fertig.
- Keine Debug-Ausgaben (`println`, `Log.d`) im Endstand.
- Nutzertexte deutsch, Bezeichner englisch.
- Jede mutierende Aktion prüft `RoleManager.require(Permission.…)`.

## Phase 4 — Verifizieren

Skill `verification`. Minimum:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
```

Bei UI-Änderung zusätzlich Skill `secureguard-ui-review`.
Bei Konsolenänderung zusätzlich `cd console3d && npm run build` und Skill
`three-scene-review`.

## Phase 5 — Abschließen

- Aufgabe auf `status: d` setzen, Nachweis eintragen, Verlauf ergänzen.
- Neue Erkenntnisse nach `agent-os/Knowledge/` schreiben, nicht in den
  Commit-Text vergraben.
- Commit im Imperativ, Body erklärt das Warum.
- Wenn während der Arbeit Nebenfunde auftauchen: nicht mitfixen, sondern in
  `agent-os/BACKLOG.md` notieren.
