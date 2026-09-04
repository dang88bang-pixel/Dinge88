---
name: secureguard-ui-review
description: Prüfliste für jede sichtbare Änderung an der Android-App oder der 3D-Konsole. Deckt Zustände, Hierarchie, Aktionen, Barrierefreiheit und Sprache ab.
---

# UI-Review SecureGuard

Anwenden, sobald sich etwas Sichtbares ändert — vor der Fertigmeldung.

## 1. Zustände (häufigste Lücke)

Jede Ansicht muss vier Zustände gestaltet haben:

- [ ] **Laden** — `SgSkeleton`, kein blanker Spinner auf leerem Grund
- [ ] **Leer** — `SgEmptyState` mit Erklärung *und* nächster Handlung
- [ ] **Fehler** — verständlicher Text plus Wiederholen-Möglichkeit
- [ ] **Gefüllt** — auch mit 1 Element und mit 200 Elementen brauchbar

Ein Bildschirm, der leer bleibt und nichts erklärt, gilt als Fehler.

## 2. Informationshierarchie

- [ ] Die wichtigste Information ist ohne Scrollen sichtbar
- [ ] Nicht mehr als eine primäre Aktion pro Ansicht
- [ ] Kennzahlen haben Kontext (Trend, Sparkline, Vergleich) — nackte Zahlen
      beantworten nicht die Frage „ist das gut?"
- [ ] Zeitstempel über `relativeTime()`, nie roh

## 3. Aktionen (Kernanforderung des Produkts)

- [ ] Jede wichtige Aktion ist in höchstens zwei Tippern erreichbar
- [ ] Kritische Aktionen (`MOTOR_OFF`, `RESTART`) laufen über `SgConfirmDialog`
- [ ] Fehlende Berechtigung wird **erklärt**, nicht durch ein stummes
      Deaktivieren angedeutet
- [ ] Offline: Einreihen wird sichtbar bestätigt („in Warteschlange"), nicht
      als Erfolg getarnt
- [ ] Jede ausgelöste Aktion liefert eine Rückmeldung (Snackbar/Toast)

## 4. Konsistenz

- [ ] Nur Bausteine aus `presentation/designsystem/`
- [ ] Kein `Color(0x…)` in `presentation/ui/**`
- [ ] Abstände aus `Sg.Space`, Radien aus `Sg.Radius`
- [ ] Statusfarben über `statusColor()`, nicht handverdrahtet
- [ ] App und 3D-Konsole benutzen dieselbe Farbsprache

## 5. Sprache

- [ ] Nutzertexte deutsch, Bezeichner englisch
- [ ] Keine Entwicklerbegriffe in der Oberfläche („Flow", „Repository", „null")
- [ ] Fehlermeldungen sagen, was zu tun ist, nicht nur was schiefging

## 6. Barrierefreiheit

- [ ] Jedes `Icon` mit Funktion hat `contentDescription`; rein dekorative `null`
- [ ] Tippziele mindestens 48 dp
- [ ] Farbe ist nie der einzige Träger einer Information
      (Statuspunkt immer mit Text daneben)
- [ ] Text skaliert bei großer Systemschrift ohne Abschneiden

## 7. Nachweis

- [ ] `./gradlew :app:assembleDebug` grün
- [ ] Screenshot oder Preview-Link im Ergebnis
- [ ] Bei Konsolenänderung: `npm run build` grün und Szene im Browser gesehen

Wenn kein Screenshot möglich ist: ausdrücklich schreiben, dass die Optik
ungeprüft ist. Siehe Skill `verification`.

## Wann nicht anwenden

Bei reinen Backend-, Firmware- oder Dokumentationsänderungen.
