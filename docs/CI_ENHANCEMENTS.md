> **2026-08-27: Die Komplett-Erweiterung ist fertig entwickelt** – als kopierfertige
> Vorlage unter **`docs/ci/build-release.yml`** (enthält: `arena/**`-Trigger, `Unit tests`-Step,
> Android-Lint, Test-Report-Artefakte, Backend-pytest-Job).
>
> **Einmalig manuell aktivieren** (GitHub-App ohne `workflows`-Permission darf
> `.github/workflows/` nicht ändern):
>
> ```bash
> cp docs/ci/build-release.yml .github/workflows/build-release.yml
> git add .github/workflows/build-release.yml && git commit -m "ci: activate extended workflow"
> git push
> ```
>
> Der folgende Text ist historisch.

# CI-Erweiterungen (manuell mergen)

Der Arena-Agent darf Workflow-Dateien unter `.github/workflows/` nicht
pushen (GitHub-App ohne `workflows`-Permission).

Bitte **manuell** in `.github/workflows/build-release.yml` einpflegen:

## 1) Branches

```yaml
on:
  push:
    branches: [ main, develop, 'arena/**' ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main, develop, 'arena/**' ]
```

## 2) Unit-Tests vor Debug-Build

Nach „Make Gradle wrapper executable“:

```yaml
      - name: Unit tests
        if: matrix.task == 'assembleDebug'
        run: ./gradlew testDebugUnitTest --no-daemon --stacktrace
```

Lokal ohne CI:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
