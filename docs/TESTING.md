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
| `MacValidationTest` / `ModelBasicsTest` | Modelle |

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

## Hinweis Sandbox

In der Arena-Sandbox gibt es oft kein JDK/SDK – Tests lokal oder in CI ausführen.
