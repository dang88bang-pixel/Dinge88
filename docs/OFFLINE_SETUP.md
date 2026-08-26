# Offline-Setup – SecureGuard Enterprise

Anleitung, um **JDK 17**, **Android SDK**, **Gradle-Dependencies** und
**PlatformIO (ESP32)** auf einem Rechner **ohne Internetzugang** bereitzustellen.

Typisches Szenario: Firmennetz blockiert `dl.google.com`, `repo.maven.apache.org`,
`github.com`, `registry.platformio.org` und Adoptium.

```
┌─────────────────────┐         USB / SMB          ┌──────────────────────┐
│  Online-PC          │  ───────────────────────►  │  Offline-Ziel-PC     │
│  download-all.sh    │      offline_repo/         │  install-offline.sh  │
└─────────────────────┘                            └──────────────────────┘
```

---

## Schnellstart

### 1) Online-Rechner – Repository spiegeln

```bash
cd /pfad/zu/Dinge88
chmod +x scripts/offline/*.sh

# Alles (JDK + Android SDK + PlatformIO + Gradle-Cache)
./scripts/offline/download-all.sh

# Oder nur Teilmengen:
./scripts/offline/download-all.sh --skip-gradle
./scripts/offline/download-jdk.sh
./scripts/offline/download-android-sdk.sh
./scripts/offline/download-platformio.sh
./scripts/offline/download-gradle-deps.sh
```

Ergebnis:

| Pfad | Inhalt |
|------|--------|
| `offline_repo/jdk/` | OpenJDK 17 (Temurin) als `.tar.gz` / `.zip` |
| `offline_repo/android-sdk/` | platforms 34+35, build-tools, platform-tools, cmdline-tools |
| `offline_repo/platformio/` | ESP32-Platform, Libs, optional `cache/` |
| `offline_repo/gradle/` | Wrapper-Distribution + Dependency-Cache |
| `secureguard-offline-repo-YYYYMMDD.tar` | optionales Gesamtarchiv |

> `OFFLINE_REPO=/media/usb/sg-offline ./scripts/offline/download-all.sh`  
> schreibt direkt auf den Stick.

### 2) Transfer

Kopiere `offline_repo/` (oder das `.tar`) per USB-Stick / Netzlaufwerk auf den Zielrechner.
Entpacken falls nötig:

```bash
tar -xf secureguard-offline-repo-YYYYMMDD.tar -C /pfad/zum/projekt/
```

### 3) Offline-Rechner – installieren

```bash
cd /pfad/zu/Dinge88
# Falls der Repo-Ordner woanders liegt:
export OFFLINE_REPO=/mnt/usb/offline_repo

./scripts/offline/install-offline.sh

# Umgebung in der aktuellen Shell:
source ~/.secureguard/env.sh
```

Das Install-Skript:

1. Entpackt JDK → `~/.secureguard/jdk`, setzt `JAVA_HOME`
2. Kopiert Android SDK → `~/.secureguard/android-sdk`, setzt `ANDROID_HOME`
3. Schreibt `sdk.dir=…` in `local.properties`
4. Spielt Gradle-Cache nach `~/.gradle`
5. Spielt PlatformIO-Cache / Archive ein und aktualisiert `platformio.ini`

### 4) API-Keys (lokal, nicht in Git)

```bash
# local.properties existiert nach install bereits mit sdk.dir
# Keys aus dem Example ergänzen:
nano local.properties
```

Felder siehe `local.properties.example`:

- `WIGLE_API_KEY`, `OPEN_CHARGE_MAP_KEY`, `NETATMO_TOKEN`, `GOOGLE_API_KEY`
- `MQTT_BROKER_URL`, `WEBSOCKET_URL`, `MCP_SERVER_URL`
- LoRa/Optik/Urban-Endpunkte

> Diese Datei steht in `.gitignore` und darf **nie** committed werden.

### 5) Bauen

```bash
source ~/.secureguard/env.sh

# Android (erzwingt Offline-Modus – kein Netzwerkabruf)
./gradlew :app:assembleDebug --offline
./gradlew :app:assembleRelease --offline

# ESP32-Firmware
cd firmware/secureguard_esp32
pio run
pio run -t upload   # wenn Port verbunden
```

---

## Skript-Referenz

| Skript | Ort | Zweck |
|--------|-----|-------|
| `download-all.sh` | Online | Orchestriert alle Downloads |
| `download-jdk.sh` | Online | Adoptium JDK 17 |
| `download-android-sdk.sh` | Online | cmdline-tools + sdkmanager-Pakete |
| `download-platformio.sh` | Online | `pio pkg download` + Cache-Spiegel |
| `download-gradle-deps.sh` | Online | einmaliger Build → Cache kopieren |
| `install-offline.sh` | Offline | Orchestriert alle Installer |
| `install-jdk.sh` | Offline | Entpacken + `JAVA_HOME` |
| `install-android-sdk.sh` | Offline | SDK kopieren + `local.properties` |
| `install-gradle-cache.sh` | Offline | `~/.gradle` befüllen |
| `install-platformio.sh` | Offline | Cache/Archive + `platformio.ini` |
| `common.sh` | beide | Versionen, Logging, Hilfsfunktionen |

Flags (Download & Install):

- `--skip-jdk` / `--skip-sdk` / `--skip-pio` / `--skip-gradle`

Umgebungsvariablen:

| Variable | Default | Bedeutung |
|----------|---------|-----------|
| `OFFLINE_REPO` | `<repo>/offline_repo` | Transfer-Ordner |
| `JAVA_INSTALL_ROOT` | `~/.secureguard/jdk` | JDK-Ziel |
| `ANDROID_INSTALL_ROOT` | `~/.secureguard/android-sdk` | SDK-Ziel |
| `SG_AUTO_BASHRC=1` | aus | schreibt `source env.sh` in `~/.bashrc` |
| `COMPILE_SDK` | `android-35` | sdkmanager-Platform |
| `BUILD_TOOLS` | `35.0.0` | Build-Tools-Version |

---

## PlatformIO – manuelle Variante

Falls die Skripte die CLI-Flags deiner PIO-Version nicht treffen:

**Online:**

```bash
pio pkg download --platform "platformio/espressif32" --output-dir ./offline_repo/platformio/platforms
pio pkg download --library "knolleary/PubSubClient" --output-dir ./offline_repo/platformio/libs
pio pkg download --library "sandeepmistry/LoRa" --output-dir ./offline_repo/platformio/libs
pio pkg download --library "bblanchon/ArduinoJson" --output-dir ./offline_repo/platformio/libs

# Zusätzlich robuster: gesamten Cache kopieren
cp -a ~/.platformio/platforms offline_repo/platformio/cache/
cp -a ~/.platformio/packages  offline_repo/platformio/cache/
```

**Offline** – `firmware/secureguard_esp32/platformio.ini`:

```ini
[env:esp32dev]
platform = file:///pfad/zu/offline_repo/platformio/platforms/espressif32-6.x.x.tar.gz
board = esp32dev
framework = arduino
lib_deps =
    file:///pfad/zu/offline_repo/platformio/libs/PubSubClient-2.8.0.tar.gz
    file:///pfad/zu/offline_repo/platformio/libs/LoRa-0.8.0.tar.gz
    file:///pfad/zu/offline_repo/platformio/libs/ArduinoJson-7.x.x.tar.gz
```

Oder Cache 1:1 nach `~/.platformio/platforms` und `~/.platformio/packages` kopieren und die **Online-**`platformio.ini` unverändert lassen (PIO löst dann lokal auf).

---

## Android SDK – manuelle Variante

```bash
# Online
sdkmanager --sdk_root=./offline_repo/android-sdk \
  "platform-tools" \
  "platforms;android-34" "platforms;android-35" \
  "build-tools;34.0.0" "build-tools;35.0.0"

# Offline: Ordner kopieren, dann
export ANDROID_HOME=/pfad/zu/android-sdk
# in local.properties:
sdk.dir=/pfad/zu/android-sdk
```

---

## JDK 17 – manuelle Variante

1. Von [Adoptium](https://adoptium.net/) das **portable** JDK 17 (`.tar.gz` / `.zip`) laden  
2. Entpacken z. B. nach `~/.secureguard/jdk/…`  
3. `export JAVA_HOME=…` und `PATH="$JAVA_HOME/bin:$PATH"`

---

## Phase 2 – bewusst später

| Thema | Status | Begründung |
|-------|--------|------------|
| **SQLCipher** (Room-Verschlüsselung) | ⏳ aufgeschoben | Braucht vollständiges NDK/SDK-Build; native `.so`-Linkerfehler sind offline schwer debuggbar. Erst wenn `./gradlew assembleDebug --offline` grün ist. |
| **Produktive API-Keys** | ⏳ lokal | Nur in `local.properties` / Secrets-Store auf dem Zielrechner – nicht im Offline-Repo und nicht in Git. |
| **Release-Keystore** | optional | `secureguard-keystore.jks` + `KEYSTORE_*` Env; sonst debug-signiert / unsigned. |

### SQLCipher – Checkliste wenn es soweit ist

1. Offline-Build der App verifiziert  
2. `sqlcipher-android` / `android-database-sqlcipher` Dependency + Maven-Artefakte im Gradle-Cache  
3. `SupportFactory` in `SecureGuardDatabase` / Hilt-Module  
4. Passphrase aus AndroidKeyStore (`EncryptionService`)  
5. Migration unverschlüsselt → verschlüsselt testen (Backup vorher!)

---

## Troubleshooting

| Symptom | Ursache / Fix |
|---------|----------------|
| `sdk.dir is missing` | `install-android-sdk.sh` erneut, oder `sdk.dir=` in `local.properties` |
| Gradle will trotzdem netzwerken | `--offline` vergessen; oder Cache unvollständig → online `download-gradle-deps.sh` nochmal |
| `Could not find com.android.tools.build:gradle` | Gradle-Cache nicht kopiert / falsches `GRADLE_USER_HOME` |
| `pio: command not found` | PlatformIO-CLI muss einmalig (online) installiert und mitübertragen werden (`pip install --user platformio`, `~/.local` kopieren) |
| `Platform not found espressif32` | Cache nach `~/.platformio` kopieren oder `file://`-Pfade in `platformio.ini` |
| sdkmanager braucht Java | Zuerst `download-jdk.sh` / `install-jdk.sh` |
| Falsche SDK-Version | `app/build.gradle.kts`: `compileSdk = 34`, CI nutzt 35 – Skripte spiegeln **beide** |

---

## Verzeichnisstruktur (Zielzustand)

```
Dinge88/
├── offline_repo/                 # gitignored – Transfer-Artefakte
│   ├── jdk/
│   ├── android-sdk/
│   ├── platformio/
│   │   ├── platforms/
│   │   ├── libs/
│   │   └── cache/
│   ├── gradle/
│   ├── MANIFEST.txt
│   └── README.txt
├── scripts/offline/
│   ├── common.sh
│   ├── download-*.sh
│   └── install-*.sh
├── firmware/secureguard_esp32/
│   ├── platformio.ini
│   └── secureguard_esp32.ino
├── local.properties              # gitignored – sdk.dir + Keys
└── docs/OFFLINE_SETUP.md         # diese Datei
```
