# ci/workflows — vorbereitete GitHub-Actions-Workflows

Diese Dateien gehören inhaltlich nach `.github/workflows/`. Sie liegen hier,
weil das Token des Arena-Agenten die GitHub-Berechtigung **`workflows`** nicht
besitzt: Jeder Push, der Dateien unter `.github/workflows/` anlegt oder ändert,
wird von GitHub abgelehnt mit

```
refusing to allow a GitHub App to create or update workflow
`.github/workflows/...` without `workflows` permission
```

## Aktivieren

```bash
bash scripts/install-ci-workflows.sh
git add .github/workflows
git commit -m "ci: JDK/Android-SDK über offizielle Actions, CI auf jedem Branch"
git push
```

Der Push muss von einem Konto oder Token kommen, das `workflows` darf –
Ihr persönlicher Account genügt, ebenso ein PAT mit dem Scope `workflow`.

Alternativ: In Arena die GitHub-Verbindung mit der Berechtigung `workflows`
neu verbinden, dann kann der Agent die Dateien direkt schreiben.

---

## Inhalt

### `ci.yml` (neu)

Schnelle Rückmeldung auf **jedem** Push und Pull Request – auch auf Feature-
und Agenten-Branches. Genau das fehlte bisher: `build-release.yml` läuft nur
auf `main`, `develop` und Tags, deshalb blieb der Kotlin-Anteil von
Arbeitsbranches unkompiliert.

| Job | Inhalt |
|-----|--------|
| `android` | Temurin JDK 17 · Android SDK 35 · `assembleDebug` · `testDebugUnitTest` · Lint (noch nicht blockierend) · APK und Berichte als Artefakt |
| `console3d` | `npm ci` · `npm run build` · **Drift-Check** gegen `app/src/main/assets/console3d` · Bundle-Größe in der Job-Zusammenfassung |
| `backend` | `pytest backend/tests` · Rauchtest der Bereitstellung (Backend hochfahren, Demo-Flotte einspielen, Kennzahlen prüfen) |

Der Drift-Check schließt eine stille Fehlerklasse: Läuft das committete
3D-Bundle der Quelle davon, liefert die App eine veraltete Konsole aus, **ohne
dass irgendetwas fehlschlägt**. Der Job bricht dann mit der Anweisung ab,
`scripts/sync-console3d.sh` auszuführen.

### `build-release.yml` (korrigiert)

Drei konkrete Fehler behoben:

1. **Fragiles SDK-Handskript.** Es ermittelte die cmdline-tools-Version per
   `grep -oP` aus `dl.google.com/android/repository/repository2-1.xml`. Das
   bricht, sobald Google das XML umbaut, und scheitert hinter Proxies still.
   Ersetzt durch `android-actions/setup-android@v3` – die Action setzt
   `ANDROID_HOME`/`ANDROID_SDK_ROOT` und akzeptiert die SDK-Lizenzen selbst.
2. **Wirkungsloser Cache.** Der Schritt `Cache Android SDK` stand **hinter**
   der Installation. `actions/cache` stellt aber an genau seiner Position
   wieder her – der Cache konnte also nie etwas beschleunigen. Entfällt, weil
   die Action das übernimmt.
3. **Ungenutzte `env`-Variablen** (`GRADLE_CACHE_FOLDER`,
   `GRADLE_WRAPPER_FOLDER`) entfernt; `COMPILE_SDK` von `android-35` auf `35`
   normalisiert, weil die Action den Präfix selbst setzt.

---

## Hinweise für Self-Hosted Runner

Microsoft-gehostete Runner (`ubuntu-latest`) bringen JDK und Android-SDK bereits
mit; die Actions oben sorgen nur für die richtigen Versionen. Auf einem
Self-Hosted Runner sind erfahrungsgemäß drei Dinge zu prüfen:

**Netzwerk.** Ausgehend erreichbar sein müssen `github.com` samt der
Artefakt-Buckets für Actions, `dl.google.com`, `dl.google.com/android`,
`services.gradle.org` bzw. der in `gradle-wrapper.properties` hinterlegte
Spiegel, und `repo.maven.apache.org`. Hinter einem Proxy müssen `HTTP_PROXY`
und `HTTPS_PROXY` in der Umgebung des **Agenten-Dienstes** stehen, nicht nur in
der Shell des Benutzers.

**Schreibrechte.** Der Runner-Benutzer braucht volles Schreibrecht auf sein
Arbeitsverzeichnis, auf `$HOME` (für `~/.gradle` und `~/.android`) und auf das
Temp-Verzeichnis des Systems. `Permission denied` beim SDK-Entpacken kommt fast
immer daher.

**Lizenzen.** `android-actions/setup-android@v3` akzeptiert sie automatisch.
Bei einer Handinstallation ist `yes | sdkmanager --licenses` vorzuschalten,
sonst bricht Gradle mit „You have not accepted the license agreements" ab.

## Dieses Projekt braucht

| Werkzeug | Version | Woher |
|----------|---------|-------|
| JDK | 17 (Temurin) | `kotlinOptions.jvmTarget = "17"` in `app/build.gradle.kts` |
| Android compileSdk | 35 | `app/build.gradle.kts` |
| Build-Tools | 35.0.0 | passend zu compileSdk |
| Gradle | 8.9 | `gradle/wrapper/gradle-wrapper.properties` |
| Node | 20 | `console3d/` |
| Python | 3.11 | `backend/` |
