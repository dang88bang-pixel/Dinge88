---
name: tdd
description: Test zuerst für neue Geschäftslogik in services/, data/repository/ und backend/. Nicht für UI-Layout.
---

# Test zuerst

Gilt für Logik, die eine Entscheidung trifft: `services/`, `data/repository/`,
`agent/`, `backend/`. **Nicht** für Compose-Layout — dort sind Screenshots
das bessere Werkzeug.

## Zyklus

```
ROT  → Test schreiben, der aus dem richtigen Grund fehlschlägt
GRÜN → kleinste Änderung, die ihn bestehen lässt
BLAU → aufräumen, Tests bleiben grün
```

Den roten Lauf **tatsächlich sehen**. Ein Test, der nie fehlgeschlagen ist,
beweist nichts — er könnte an einer Konstanten hängen.

## Wo Tests liegen

| Art | Ort |
|-----|-----|
| Kotlin-Unit | `app/src/test/java/com/secureguard/enterprise/` |
| Android-Instrumentierung | `app/src/androidTest/java/...` |
| Backend | `backend/tests/` |

Bestehende Beispiele als Vorlage: `AgentCycleLogicTest`, `AssetCrudTest`,
`MacValidationTest`, `PrivacyExportContractTest`.

## Was getestet wird

Nach Risiko, nicht nach Abdeckungsquote:

- [ ] **Kommandopfad** — Kanalabfolge, Einreihen bei Ausfall, Ablehnung ohne Berechtigung
- [ ] **Rollen und Rechte** — jede `Permission` mindestens ein positiver und ein negativer Fall
- [ ] **Agentenzyklus** — Zählerstand, Intervall, Start/Stopp
- [ ] **Datenschutzverträge** — Export und Löschung
- [ ] **Validierung** — MAC-Adressen, Endpunkte, Eingabegrenzen
- [ ] **Grenzfälle** — leere Liste, `null`-Werte, Zeitstempel in der Zukunft

Nicht getestet: Getter, Datenklassen ohne Logik, Compose-Layout.

## Gute Testnamen

```kotlin
@Test fun `sendAction reiht ein wenn kein Kanal verfuegbar ist`()
@Test fun `sendAction lehnt ab ohne EXECUTE_ACTIONS`()
@Test fun `Agentzyklus erhoeht Zaehler genau einmal pro Lauf`()
```

Der Name beschreibt das Verhalten, nicht die Methode.

## Regeln

- Ein Verhalten pro Test. Drei Zusicherungen zu drei Themen = drei Tests.
- Keine echten Netzwerk-, Zeit- oder Datenbankzugriffe. Zeit injizieren, nicht
  `System.currentTimeMillis()` direkt lesen.
- Test schlägt fehl → **erst** verstehen, warum. Nie die Erwartung an das
  tatsächliche Ergebnis anpassen, um grün zu werden.

## Nachweis

```bash
./gradlew :app:testDebugUnitTest
pytest backend/tests
```

Vollständige Ausgabe lesen, Fehlerzahl nennen. Siehe Skill `verification`.
