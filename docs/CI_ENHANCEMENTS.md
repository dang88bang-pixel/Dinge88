> **2026-08-27: Die Komplett-Erweiterung ist fertig entwickelt** – als kopierfertige
> Vorlage unter **`docs/ci/build-release.yml`** (enthält: `arena/**`-Trigger, `Unit tests`-Step,
> Android-Lint, Test-Report-Artefakte, Backend-pytest-Job, Slack-MCP-Image-Job).
>
> `slack-mcp-image` baut das Slack-MCP-Image aus `slack-mcp/Dockerfile` und prüft
> den MCP-Handshake (`initialize`), `tools/list` sowie die Backend-Anbindung
> (`/api/slack/health` → `reachable: true`) – Details: `docs/SLACK_MCP.md`.
>
> **Einmalig manuell aktivieren** (GitHub-App ohne `workflows`-Permission darf
> `.github/workflows/` nicht ändern – Push-Versuche scheitern mit
> `refusing to allow a GitHub App to create or update workflow …`):
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

## 3) Backend-Tests + Slack-MCP-Image (Jobs)

Zusätzliche Jobs aus `docs/ci/build-release.yml` (komplett kopierbar):

```yaml
  backend-tests:
    name: Backend-Tests (FastAPI + Slack-MCP)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: pip
          cache-dependency-path: backend/requirements-dev.txt
      - run: python -m pip install -r backend/requirements-dev.txt
      - run: python -m pytest backend/tests -q

  slack-mcp-image:
    name: Slack-MCP image + MCP handshake
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose config --quiet
      - run: docker compose build slack-mcp
      - run: docker compose up -d slack-mcp
      - name: MCP-Handshake (initialize)
        run: |
          req='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"ci","version":"1.0"}}}'
          for i in $(seq 1 30); do
            curl -fsS -o /tmp/init.json -X POST -H 'Content-Type: application/json' \
              -H 'Accept: application/json, text/event-stream' -d "$req" \
              http://127.0.0.1:13080/mcp && { cat /tmp/init.json; exit 0; }
            sleep 2
          done
          docker compose logs --tail=60 slack-mcp; exit 1
      - name: Backend gegen slack-mcp (End-to-End)
        run: |
          docker compose up -d --build backend
          for i in $(seq 1 30); do curl -fsS http://127.0.0.1:8000/api/health >/dev/null && break; sleep 2; done
          curl -fsS http://127.0.0.1:8000/api/slack/health | tee /tmp/slack-health.json
          grep -q '"reachable": *true' /tmp/slack-health.json
      - if: always()
        run: docker compose down -v
```

Lokal ohne CI:

```bash
pytest backend/tests -q                                   # 38 Tests
docker compose build slack-mcp && docker compose up -d slack-mcp
curl -s localhost:8000/api/slack/health                   # nach backend-Start
```
