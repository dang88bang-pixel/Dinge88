---
name: release-apk
description: Baut ein signiertes SecureGuard-Release-APK inklusive Konsolen-Sync, Protokollprüfung, Rauchtest und Veröffentlichung.
---

# Release bauen

Vollständiger Ablauf in `agent-os/Workflows/release.md`. Dieser Skill ist die
Kurzfassung mit den Stellen, an denen erfahrungsgemäß etwas schiefgeht.

## Reihenfolge

```bash
# 1. Konsole bauen und ins App-Bundle spiegeln
bash scripts/sync-console3d.sh
git diff --stat app/src/main/assets/console3d

# 2. Protokollkonsistenz
python3 scripts/check-action-drift.py   # sobald vorhanden

# 3. Vollprüfung
./gradlew clean
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
pytest backend/tests

# 4. Version in app/build.gradle.kts hochziehen (versionCode +1)

# 5. Release
./gradlew :app:assembleRelease

# 6. Rauchtest
bash scripts/smoke-check.sh
```

## Die drei häufigsten Fehler

1. **Konsolen-Bundle vergessen.** Die App lädt die 3D-Konsole aus
   `app/src/main/assets/console3d/`. Ohne `sync-console3d.sh` wird eine alte
   Version ausgeliefert und niemand merkt es, weil nichts fehlschlägt.
2. **`versionCode` nicht erhöht.** Der Store lehnt das Artefakt ab, oder
   schlimmer: Installationen aktualisieren nicht.
3. **Signierung lokal statt über CI.** Keystore und Passwörter gehören nie ins
   Repository. `scripts/create-release-keystore.sh` erzeugt den Keystore,
   die Geheimnisse liegen in den CI-Secrets.

## Rauchtest auf Gerät (manuell, nicht überspringen)

- [ ] Kaltstart ohne Absturz
- [ ] Anmeldung/Sperrbildschirm
- [ ] Dashboard zeigt Kennzahlen, Agent startet und stoppt
- [ ] 3D-Lagebild lädt in der WebView und reagiert auf Eingaben
- [ ] Unkritische Aktion (`LIGHT`) auslösen, Rückmeldung erscheint
- [ ] Flugmodus an: Aktion wird eingereiht; aus: Queue leert sich
- [ ] Kritische Aktion fragt nach Bestätigung

## Veröffentlichen

```bash
gh release create v<version> \
  app/build/outputs/apk/release/app-release.apk \
  --title "SecureGuard v<version>" --notes-file RELEASE_NOTES.md
```

Release-Notes gliedern: Neu · Verbessert · Behoben · Bekannte Probleme.

## Nacharbeiten

- Statuszeilen in `agent-os/GOALS.md` aktualisieren
- Session-Review in `agent-os/Evals/` anlegen
- Erledigte Aufgaben auf `status: d` setzen
