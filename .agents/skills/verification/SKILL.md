---
name: verification
description: Erzwingt Nachweise, bevor Arbeit als fertig gemeldet wird. Vor jeder Fertigmeldung, jedem Commit und jedem Release anwenden.
---

# Verifikation vor der Fertigmeldung

## Das eiserne Gesetz

```
KEINE FERTIGMELDUNG OHNE FRISCHEN NACHWEIS
```

Wenn der Prüfbefehl nicht in dieser Antwort gelaufen ist, darf nicht behauptet
werden, dass er durchläuft.

## Die Schleuse

1. **Benennen** — welcher Befehl belegt genau diese Behauptung?
2. **Ausführen** — vollständig und frisch, nicht aus dem Gedächtnis.
3. **Lesen** — gesamte Ausgabe, Exit-Code, Anzahl Fehler.
4. **Abgleichen** — bestätigt die Ausgabe die Behauptung?
   - Nein → tatsächlichen Stand mit Beleg nennen.
   - Ja → Behauptung **mit** Beleg nennen.
5. **Erst dann** melden.

Einen Schritt überspringen ist keine Abkürzung, sondern eine Falschaussage.

## Nachweismatrix für dieses Projekt

| Behauptung | Nachweis | Nicht ausreichend |
|------------|----------|-------------------|
| App baut | `./gradlew :app:assembleDebug` → exit 0 | Lint war grün |
| Tests grün | `./gradlew :app:testDebugUnitTest` → 0 failures | Letzter Lauf von gestern |
| Lint sauber | `./gradlew :app:lint` → 0 errors | Teilprüfung einer Datei |
| Konsole baut | `cd console3d && npm run build` → exit 0 | `npm run dev` startet |
| Backend grün | `pytest backend/tests` | Server startet |
| UI stimmt | Screenshot oder Preview-Link | „sieht sicher gut aus" |
| Fehler behoben | Ursprüngliches Symptom erneut auslösen | Code wurde geändert |
| Aktion funktioniert | Rauchtest gegen Gerät oder Simulation | Kompiliert |

## Wenn nicht geprüft werden kann

Im Sandbox-Umfeld fehlen JDK, Android-SDK und Browser. Dann gilt:

- **Nicht** behaupten, es baue oder funktioniere.
- Stattdessen exakt schreiben, was geprüft wurde (z. B. „`npm run build` grün,
  Kotlin nur gelesen, nicht kompiliert") und was offen ist.
- Die ungeprüften Annahmen einzeln auflisten, damit sie jemand nachziehen kann.

Ein ehrliches „ungeprüft" ist wertvoll. Ein falsches „getestet" zerstört
Vertrauen in jede weitere Aussage.

## Warnsignale — anhalten

- „sollte", „vermutlich", „müsste jetzt"
- Erfolgsmeldung („Fertig!", „Perfekt!") vor dem Prüflauf
- Commit oder Push ohne vorherige Prüfung
- Teilprüfung als Gesamtnachweis ausgeben

## Ausreden und Wirklichkeit

| Ausrede | Wirklichkeit |
|---------|--------------|
| „Sollte jetzt gehen" | Dann führ es aus. |
| „Ich bin mir sicher" | Sicherheit ist kein Beleg. |
| „Nur dieses eine Mal" | Keine Ausnahmen. |
| „Der Teil reicht doch" | Teilprüfung beweist nichts. |

## Wann anwenden

Immer vor einer Fertigmeldung, einem Commit, einem Push oder einem Release.

## Wann nicht

Bei reinen Verständnisfragen ohne Codeänderung.
