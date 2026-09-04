# GOALS.md — SecureGuard Enterprise

Woran Arbeit gemessen wird. Jede Aufgabe in `agent-os/Tasks/` muss auf genau
ein Ziel einzahlen. Aufgaben ohne Zielbezug werden nicht angefangen.

Stand: 2026-09 · Review-Rhythmus: monatlich

---

## G1 — Ein Werkzeug, das im Ernstfall funktioniert

> Wenn ein Asset gestohlen wird, führt die App den Nutzer in unter 60 Sekunden
> von „App öffnen" zu „Position bekannt und Aktion ausgelöst".

**Messpunkte**
- Vom Kaltstart bis zur ersten sichtbaren Position: ≤ 10 s
- Jede Aktion aus max. 2 Taps erreichbar (Dashboard-Kachel oder Palette)
- Offline ausgelöste Aktionen gehen nicht verloren (Offline-Queue)
- Kritische Aktionen sind nie versehentlich auslösbar (Bestätigungsdialog)

**Status:** Aktionen-Center und Dashboard neu gebaut; Zeitmessung fehlt.

---

## G2 — Lagebild statt Listen

> Der Betreiber versteht den Zustand der Flotte in einem Blick, nicht nach
> drei Scrollbewegungen.

**Messpunkte**
- 3D Operations Center zeigt Assets, Kanäle, Detektionen und Alarme gleichzeitig
- Konsole läuft in der App (WebView) **und** im Browser aus derselben Codebasis
- Konsole ist nie leer: `native` → `backend` → `simulation`
- Bundle unter 1 MB gzip, 60 fps auf Mittelklasse-Hardware

**Status:** Umgesetzt. Performance-Budget noch nicht auf Gerät gemessen.

---

## G3 — Professionelle, konsistente Oberfläche

> Kein Screen sieht aus wie ein Prototyp. Alles kommt aus einem Design-System.

**Messpunkte**
- Null Hex-Farbliterale in `presentation/ui/**`
- Alle Karten/Kacheln/Statusindikatoren aus `presentation/designsystem/`
- Leerzustände, Ladezustände und Fehlerzustände sind überall gestaltet
- Konsole und App teilen dieselbe Farbsprache (`--navy-950`, `--cyan`)

**Status:** Dashboard, Aktionen, Assets migriert. Restliche Screens offen.

---

## G4 — Vertrauenswürdig, weil nachvollziehbar

> Jede ausgelöste Aktion ist zurückverfolgbar; keine Funktion überschreitet
> den Zweck „eigene Werte wiederbeschaffen".

**Messpunkte**
- Jede mutierende Aktion prüft `RoleManager` und schreibt ins Audit-Log
- Whitelist-Zwang: keine Erfassung nicht registrierter Geräte
- Datenexport/-löschung erfüllt den Privacy-Contract-Test
- Keine Secrets im Repository

**Status:** Erfüllt; bei jeder Änderung erneut zu prüfen.

---

## G5 — Reproduzierbar baubar

> Jeder kann das Projekt in unter 15 Minuten von null auf lauffähig bringen.

**Messpunkte**
- `docker compose up -d` startet den kompletten Backend-Stack
- `./gradlew :app:assembleDebug` läuft ohne manuelle Nacharbeit
- `scripts/sync-console3d.sh` erzeugt das App-Bundle deterministisch
- CI baut ein signiertes Release-APK

**Status:** Weitgehend erfüllt; Konsolen-Build noch nicht in CI verdrahtet.

---

## Explizite Nicht-Ziele

- Keine Cloud-Pflicht. Das System muss lokal vollständig laufen.
- Keine kostenpflichtigen SDKs, Engines oder Map-Tiers.
- Keine Funktionen zur Überwachung fremder Personen.
- Keine iOS-Portierung, solange G1–G3 nicht erfüllt sind.
