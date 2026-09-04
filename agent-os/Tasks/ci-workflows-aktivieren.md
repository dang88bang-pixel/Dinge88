---
title: CI-Workflows aktivieren und den Kotlin-Build endlich nachweisen
area: infra
priority: P0
status: n
created: 2026-09-04
verification: "GitHub-Actions-Lauf 'CI' auf diesem Branch ist grün (Jobs android, console3d, backend)"
refs:
  - ci/workflows/README.md
  - agent-os/Evals/2026-09-04-3d-console-und-agentic-os.md
---

# CI-Workflows aktivieren

## Kontext

Ziel **G5 — Reproduzierbar baubar**, und Voraussetzung für jeden weiteren
Nachweis.

Die Arbeitsumgebung des Agenten hat kein JDK und kein Android-SDK,
`dl.google.com` ist dort blockiert. Der gesamte Kotlin-Anteil der letzten
Änderungen ist deshalb **gelesen und gegen die Modelle geprüft, aber nie
kompiliert**. Solange das so bleibt, ist jede Aussage über den Android-Teil
nur eine begründete Vermutung.

CI löst das: GitHub-gehostete Runner bringen die Toolchain mit.

Zwei Dinge standen bisher im Weg:

1. `build-release.yml` läuft nur auf `main`, `develop` und Tags — Arbeits-
   branches bekamen nie eine Rückmeldung.
2. Das Token des Arena-Agenten hat die Berechtigung `workflows` nicht.
   GitHub lehnt jeden Push ab, der `.github/workflows/` anfasst. Die fertigen
   Workflows liegen deshalb unter `ci/workflows/`.

## Umsetzung

- [ ] Workflows aktivieren:
      ```bash
      bash scripts/install-ci-workflows.sh
      git add .github/workflows
      git commit -m "ci: JDK/Android-SDK über offizielle Actions, CI auf jedem Branch"
      git push
      ```
      Der Push braucht ein Konto oder PAT mit `workflow`-Scope.
      Alternativ die GitHub-Verbindung in Arena mit der Berechtigung
      `workflows` neu verbinden — dann kann der Agent es selbst.
- [ ] Ersten Lauf beobachten: `gh run watch`
- [ ] Kompilierfehler beheben, bis `android` grün ist
- [ ] Ergebnis in `agent-os/Evals/2026-09-04-*` nachtragen und den Abschnitt
      „Nicht nachgewiesen" entsprechend kürzen
- [ ] Danach: Lint grün machen und `continue-on-error` aus `ci.yml` entfernen

## Nachweis

```bash
gh run list --branch arena/01a06c35-dinge88 --limit 3
gh run view --log-failed        # falls rot
```

Grün heißt: `assembleDebug` und `testDebugUnitTest` sind durchgelaufen. Erst
dann ist der Android-Teil dieser Änderungsreihe belegt.

## Verlauf

- 2026-09-04: Workflows geschrieben und lokal validiert (YAML gültig,
  `pytest backend/tests` 7 passed, Rauchtest-Logik durchgespielt,
  Drift-Check identisch). Aktivierung blockiert durch fehlende
  `workflows`-Berechtigung des Agenten-Tokens.
