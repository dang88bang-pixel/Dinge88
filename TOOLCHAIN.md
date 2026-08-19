# Build-Toolchain

## Ziel: Android 11 APK

| Komponente | Vorgabe |
|---|---|
| JDK | 17 (Temurin) – Pflicht für AGP 8 |
| Android SDK | platforms;android-34 + build-tools;34.0.0 |
| minSdk | 26 (läuft auf Android 11 / API 30) |
| targetSdk / compileSdk | 34 |
| Gradle | 8.5 |
| Kotlin | 1.9.22 |

## Sandbox

In dieser Entwicklungsumgebung sind Maven Central, Google Maven und
`services.gradle.org` nicht erreichbar. Deshalb wird die APK über
**GitHub Actions** gebaut (voller Netzzugang, JDK 17, Android SDK).

## GitHub Actions

Workflow: `.github/workflows/build-release.yml`

- Läuft bei jedem Push
- Baut Debug- und Release-APK
- Lädt Artefakte hoch
- Erstellt/aktualisiert das GitHub-Release `v1.1.0`

## Lokaler Build (normaler Rechner)

```bash
# JDK 17 + ANDROID_HOME setzen
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```
