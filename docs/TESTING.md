# Tests – SecureGuard Enterprise

## Prinzip: Passwörter vom Anwender

- **PIN** (App-Sperre): setzt der Anwender im Security-Center (min. 4 Zeichen).
- **Keystore-Passwörter** (`KEYSTORE_PASSWORD` / `KEY_PASSWORD`): setzt der Anwender
  selbst per Umgebung/`local.properties` – `scripts/create-release-keystore.sh`
  **generiert keine** Passwörter mehr.
- In Tests kommen nur **disposable Fixtures** vor (z. B. `"2468"`) – niemals
  Produktionsgeheimnisse committen.

## Unit-Tests (JVM / Robolectric)

```bash
./gradlew :app:testDebugUnitTest
```

| Klasse | Abdeckung |
|--------|-----------|
| `AuthManagerTest` | PIN setzen/entsperren/Fehlversuche/Disable |
| `AssetCrudTest` | Room CRUD + Status + Pagination |
| `AddAssetViewModelLogicTest` | Formular-Validierung MAC/Name |
| `AgentCycleLogicTest` | Cycle-Fusion, Settings, Learning-Memory |
| `EndpointConfigTest` | MQTT/HTTP-URL-Normalisierung |
| `PrivacyExportContractTest` | DSGVO-Export ohne Secrets |
| `MacValidationTest` / `ModelBasicsTest` | Modelle |
| `IntegrationInfoTest` | Zustands-Mapping der Anbindungsliste (Einstellungen) |

## Backend-Tests (pytest)

```bash
pip install -r backend/requirements-dev.txt
pytest backend/tests -q              # 45 Tests
pytest backend/tests/test_slack_mcp.py -q   # nur Slack-MCP (31 Tests)
```

| Datei | Abdeckung |
|-------|-----------|
| `test_api.py` | Health, Asset-CRUD, Befehle, MQTT-MAC-Normalisierung, Degraded-Health |
| `test_slack_mcp.py` | MCP-Client (HTTP + SSE), Tool-Cache, API-Key, Severity-Gate, Webhook-Fallback, `/api/slack/*`, Alert-Weiterleitung |
| `test_dependencies.py` | `/api/system/dependencies` – Inventur für das App-Einstellungsmenü (DB, MQTT, Slack-MCP, Webhook, Node-RED) |

Die Slack-Tests ersetzen den Go-Server durch einen `httpx.MockTransport`, der
sich exakt wie mcp-go v0.44 verhält (`POST /mcp`, `Mcp-Session-Id`, Antworten
als `application/json` **oder** `text/event-stream`, SSE-`endpoint`-Event).

**Manueller End-to-End-Test ohne Docker/Go-Binary** (QA-Stub):

```bash
python3 scripts/dev/slack-mcp-stub.py &                       # MCP-Stub :13080
SLACK_MCP_URL=http://127.0.0.1:13080/mcp SLACK_NOTIFY_ENABLED=true \
  uvicorn --app-dir backend main:app --port 8000 &            # Backend :8000
curl -s localhost:8000/api/slack/health
curl -X POST localhost:8000/api/slack/notify -H 'Content-Type: application/json' \
     -d '{"message":"Test","channel":"#general"}'
curl -s localhost:13080/stub/messages                         # „gepostete" Meldungen
```

## Instrumented Compose-UI (Emulator/Gerät)

```bash
./gradlew :app:connectedDebugAndroidTest
```

| Klasse | Abdeckung |
|--------|-----------|
| `LockScreenUiTest` | PIN-Feld, Entsperren, Fehlermeldung |
| `AddAssetFormUiTest` | Asset-Formular speichern |

`testTag`s in der UI:

- `lock_pin_field`, `lock_unlock_button`
- `security_pin_field`, `security_pin_set_button`, …
- `asset_name_field`, `asset_mac_field`, `asset_save_button`
- `integrations_card`, `integrations_refresh_button`, `settings_slack_enabled`,
  `settings_slack_channel`, `integration_row_<id>`
- `slack_status_card`, `slack_refresh_button`, `slack_tools_button`,
  `slack_channels_button`, `slack_test_button`, `slack_channel_field`,
  `slack_message_field`, `slack_send_button`, `settings_slack_button`

## Hinweis Sandbox

In der Arena-Sandbox gibt es oft kein JDK/SDK – Tests lokal oder in CI ausführen.
