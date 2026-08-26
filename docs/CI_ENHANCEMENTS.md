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
