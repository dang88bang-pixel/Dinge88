# Workflow: Release

Erzeugt ein signiertes Release-APK und die zugehörigen Artefakte.

---

## Vorbedingungen

- [ ] Alle P0-Aufgaben in `agent-os/Tasks/` sind `status: d`
- [ ] `git status` sauber, auf dem Release-Branch
- [ ] `local.properties` bzw. CI-Secrets für die Signierung vorhanden

## 1. Konsole bauen und spiegeln

Das 3D Operations Center wird offline aus den App-Assets geladen. Wenn Quelle
und Bundle auseinanderlaufen, zeigt die App eine alte Konsole.

```bash
bash scripts/sync-console3d.sh
git diff --stat app/src/main/assets/console3d
```

Ergibt der Diff Änderungen: committen. Ergibt er nichts: Bundle war aktuell.

## 2. Protokollkonsistenz prüfen

```bash
python3 scripts/check-action-drift.py     # sobald Task „Drift-Schutz" erledigt ist
```

Bis dahin manuell: Wire-Befehle in `ActionCatalog.kt`,
`console3d/src/data/catalog.js` und `firmware/secureguard_esp32/*.ino`
gegenlesen.

## 3. Vollständige Prüfung

```bash
./gradlew clean
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
pytest backend/tests
./gradlew :app:assembleDebug
```

Alles grün, sonst Abbruch.

## 4. Version hochziehen

In `app/build.gradle.kts`:

- `versionCode` +1 (monoton, nie rückwärts)
- `versionName` nach SemVer

## 5. Release bauen

```bash
./gradlew :app:assembleRelease
```

Signierung läuft über die Keystore-Konfiguration; siehe
`scripts/create-release-keystore.sh`. **Keystore und Passwörter gehören
niemals ins Repository.**

## 6. Rauchtest auf Gerät

```bash
bash scripts/smoke-check.sh
```

Manuell zusätzlich:

- [ ] App startet kalt ohne Absturz
- [ ] Anmeldung/Sperrbildschirm funktioniert
- [ ] Dashboard zeigt Kennzahlen, Agent lässt sich starten und stoppen
- [ ] 3D-Lagebild lädt in der WebView, Szene reagiert auf Eingaben
- [ ] Eine unkritische Aktion (`LIGHT`) auslösen — Rückmeldung erscheint
- [ ] Flugmodus an: Aktion wird eingereiht; Flugmodus aus: Queue leert sich

## 7. Veröffentlichen

```bash
gh release create v<version> app/build/outputs/apk/release/app-release.apk \
  --title "SecureGuard v<version>" --notes-file RELEASE_NOTES.md
```

Release-Notes gliedern nach: Neu · Verbessert · Behoben · Bekannte Probleme.

## 8. Nacharbeiten

- `agent-os/GOALS.md` Statuszeilen aktualisieren
- Session-Review in `agent-os/Evals/` anlegen
- Erledigte Aufgaben archivieren
