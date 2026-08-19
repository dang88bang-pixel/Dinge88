# 🧰 Build-Toolchain & Netzwerk-Status (Sandbox)

## Stand: verifizierte Messungen

| Komponente | Status im Sandbox | Quelle / Blockierung |
|---|---|---|
| **JDK 21 (JRE)** | ✅ installiert unter `~/tools/java21` | heruntergeladen als PyPI-Wheel `jdk4py-21.0.8.2` (Host `files.pythonhosted.org` erlaubt) |
| **javac** (Compiler) | ❌ fehlt (jdk4py bündelt nur ein JRE) | kein erreichbarer Host stellt einen vollständigen JDK-Kompilierer bereit |
| **Gradle** | ❌ nicht installierbar | `services.gradle.org` / `downloads.gradle.org` → **blockiert** (HTTP 000) |
| **Android-SDK** (cmdline-tools, platform-34, build-tools) | ❌ nicht installierbar | `dl.google.com`, `maven.google.com` → **blockiert** |
| **Maven-Abhängigkeiten** (androidx, Kotlin, Compose, Hilt, Room, osmdroid, CameraX, ML Kit, …) | ❌ nicht auflösbar | `repo.maven.apache.org`, `maven.google.com`, `repo1.maven.org` → **blockiert** |

## Erreichbare Hosts (Egress-Allowlist, gemessen)

✅ `github.com` · `api.github.com` · `codeload.github.com` · `pypi.org` · `files.pythonhosted.org` · `registry.npmjs.org`

❌ alle anderen (Maven Central, Google, Gradle, Debian/Ubuntu, Oracle/Adoptium-CDNs, raw.githubusercontent, …)

## Was das heißt

- Ein **lokaler Android-Build in diesem Sandbox ist nicht möglich**: Auch mit installierter
  Toolchain würden die Maven-Abhängigkeiten nicht heruntergeladen werden können (Firewall).
- Der **einzige funktionierende Build-Pfad ist GitHub Actions** – der Workflow
  (`.github/workflows/build-release.yml`) installiert JDK 17 + Android-SDK 34 selbst,
  hat vollen Netzzugang und baut die APK in der Cloud.

## Was bereits bereitgestellt wurde

- **JDK 21 Runtime** (Temurin 21.0.8) unter `/home/user/tools/java21` – zum *Ausführen* von
  JVM-Programmen nutzbar, aber **ohne `javac`** (kein Kompilieren möglich).
- `setup_env.sh` setzt `JAVA_HOME` und `PATH` (siehe unten).

## Nutzung

```bash
# JAVA_HOME & PATH im aktuellen Shell-Prozess setzen
source ./tools/setup_env.sh
java -version        # → OpenJDK 21.0.8 (Temurin)

# Build möglich machen (dafür GitHub Actions nutzen):
git push origin main
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

## Empfehlung für „echte" lokale Builds

Auf einem normalen Entwicklerrechner (mit Internet) genügt:

```bash
# JDK 17 installieren (z. B. Temurin) und JAVA_HOME setzen
# Gradle wird automatisch vom selbstbootstrapierenden Wrapper geladen
./gradlew assembleDebug     # lädt Gradle 8.5 + Android-SDK + Dependencies
```
