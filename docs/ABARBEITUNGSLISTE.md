# 🛡️ SecureGuard Enterprise – Vollständige Abarbeitungsliste

> **Hinweis (Umsetzungsstand):** In der aktuellen Sandbox konnte das Backend
> vollständig aufgesetzt werden (venv + Abhängigkeiten + SQLite + Migrationen +
> 12/12 pytest grün). PlatformIO-CLI wurde installiert; ESP32-Platform und
>   Bibliotheks-Downloads wurden vom Netzwerk blockiert. JDK-17- und
>   Android-SDK-Download (adoptium/google) wurden ebenfalls vom Netzwerk
>   blockiert – die Skripte sind dafür vorbereitet und laufen auf einem
>   Rechner mit Internetzugang.

Status-Legende:

| Status | Bedeutung |
|--------|-----------|
| ✅ | In diesem Repository umgesetzt und dokumentiert |
| 🔧 | Implementiert, aber nur mit echter Hardware/echten Keys verifizierbar |
| ⏳ | Offen – erfordert externe Zugangsdaten, Geräte oder Produktivinfrastruktur |
| ❌ | Nicht möglich, ohne Vertrag/Netz/Ausbildungs-Credentials |

Legende „Ohne Hardware möglich": Ein Punkt ist **lokal ohne Hardware** machbar, wenn er
ohne Android-Gerät, ESP32, LoRa-Modul, BLE, NFC, USB-Serial, echtes GNSS oder
echte externe API-Keys ausführbar/prüfbar ist.

---

## 1. Lokale Build-Umgebung

### 1.1 JDK 17
| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| ENV-001 | JDK 17 (Temurin) installieren | ✅ | ✅ | `scripts/install-jdk17.sh` |
| ENV-002 | `JAVA_HOME` setzen (User + Shell) | ✅ | ✅ | Skript schreibt `~/.secureguard-env.sh` |
| ENV-003 | `java -version`-Prüfung im Bootstrap | ✅ | ✅ | `scripts/validate-local-env.sh` |

### 1.2 Android SDK
| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| ENV-010 | Android SDK Command-Line Tools installieren | ✅ | ✅ | `scripts/install-android-sdk.sh` |
| ENV-011 | `ANDROID_HOME`/`ANDROID_SDK_ROOT` setzen | ✅ | ✅ | Skript |
| ENV-012 | Platform `android-35` installieren | ✅ | ✅ | `sdkmanager "platforms;android-35"` |
| ENV-013 | Build-Tools `35.0.0` installieren | ✅ | ✅ | `sdkmanager "build-tools;35.0.0"` |
| ENV-014 | Platform-Tools installieren | ✅ | ✅ | `sdkmanager "platform-tools"` |
| ENV-015 | SDK-Lizenzen akzeptieren | ✅ | ✅ | `yes | sdkmanager --licenses` |
| ENV-016 | `local.properties` aus Beispiel erzeugen | ✅ | ✅ | `scripts/create-local-properties.sh` |
| ENV-017 | `compileSdk`/`targetSdk`-Konsistenz (35/35) | ✅ | ✅ | `app/build.gradle.kts` |
| ENV-018 | Erster Gradle-Build gegen Google Maven/Maven Central | 🔧 | ✅ | Internet + Abhängigkeiten erforderlich (`./gradlew :app:assembleDebug`) |

### 1.3 Gradle-Abhängigkeiten
| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| ENV-020 | Google Maven + Maven Central im Settings erreichbar | ✅ | ✅ | `settings.gradle.kts` |
| ENV-021 | Kotlin-/Android-Gradle-Plugin-Abhängigkeiten downloaden | 🔧 | ✅ | `./gradlew` beim ersten Build |
| ENV-022 | Gradle-Wrapper-Cache/Daemon-Optionen für Windows/Linux | ✅ | ✅ | `gradle.properties` |
| ENV-023 | Dependency-Cache lokal prüfen | ⏳ | ✅ | offline erst nach erfolgreichem Erstbuild |

### 1.4 PlatformIO / ESP32-Firmware
| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| ENV-030 | Python 3 (>=3.9) installieren | ✅ | ✅ | Vorhanden: `python3 --version` |
| ENV-031 | `platformio` installieren (`pipx` bevorzugt) | ✅ | ✅ | `scripts/setup-platformio.sh` |
| ENV-032 | `esp32`-Platform installieren | 🔧 | ✅ | `pio pkg install -p esp32` |
| ENV-033 | LoRa-Bibliothek (MCCI LoRa oder SandeepMistry LoRa) installieren | 🔧 | ✅ | `pio pkg install -l ...` / Library-Manager |
| ENV-034 | PubSubClient-Bibliothek installieren | 🔧 | ✅ | `pio pkg install -l knolleary/PubSubClient` |
| ENV-035 | ESP32-Board flashen | ⏳ | ❌ | ESP32-Board + USB-Seriell nötig |

> Für PlatformIO ist in `firmware/secureguard_esp32/` eine reine Arduino-Umgebung.
> Ein `platformio.ini` wurde im Zuge der Abarbeitung ergänzt, damit die
> Bibliotheken reproduce-bar auflösbar sind.

---

## 2. App-Konfiguration

| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| APP-001 | `local.properties` aus Beispieldatei erzeugen | ✅ | ✅ | Skript + Doku |
| APP-002 | WiGLE-API-Key | ⏳ | ❌ | echte Registrierung/API-Key |
| APP-003 | OpenChargeMap-API-Key | ⏳ | ❌ | echte Registrierung/API-Key |
| APP-004 | Google-Geolocation-API-Key | ⏳ | ❌ | Google Cloud Account |
| APP-005 | Netatmo-Token | ⏳ | ❌ | Netatmo-Account/Dev-APP |
| APP-006 | Produktive Backend-URL | ⏳ | ❌ | produktives Deployment |
| APP-007 | Produktive WebSocket-URL | ⏳ | ❌ | produktives Deployment |
| APP-008 | Produktive MQTT-Broker-URL | ⏳ | ❌ | MQTT-Broker/Gateway |
| APP-009 | LoRa-Gateway-Endpunkt | ⏳ | ❌ | LoRa-Gateway |
| APP-010 | YOLO-Inference-Endpunkt | ⏳ | ❌ | YOLO-Server |
| APP-011 | Crowd-/Fleet-API-Endpunkt | ⏳ | ❌ | Backend-Instanz |
| APP-012 | Open-Data-URL | ⏳ | ❌ | `OPEN_DATA_API_URL` |
| APP-013 | Find-My-Proxy-URL | ⏳ | ❌ | `FIND_MY_PROXY_URL` |
| APP-014 | TLS-Zertifikate/Auth | ⏳ | ❌ | Certificates/Secrets |
| APP-015 | `CORS_ORIGINS` für Backend | ✅ | ✅ | `.env.example` + `backend/main.py` |

---

## 3. Backend (FastAPI)

| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| BE-001 | `requirements.txt` installierbar | ✅ | ✅ | `scripts/setup-backend.sh` |
| BE-002 | SQLite-Datenbank initialisieren | ✅ | ✅ | `init_db()` + `run_migrations()` |
| BE-003 | Datenbank-Migrationen | ✅ | ✅ | Schema-Versionstabelle (`schema_migrations`) |
| BE-004 | Backup-/Restore-Endpunkt | ✅ | ✅ | `/api/backup`, `/api/restore` |
| BE-005 | Backup-/Restore-Tests | ✅ | ✅ | `backend/tests/test_api.py` |
| BE-006 | API-Authentifizierung | ✅ | ✅ | optionales Bearer-Token (`API_TOKEN`) |
| BE-007 | Produktiver MQTT-Broker | ⏳ | ❌ | Broker + DNS/TLS |
| BE-008 | MQTT-Benutzer/Passwort/TLS | ✅ | ✅ | Konfiguration vorbereitet (`.env`, `mosquitto`), echte Zertifikate fehlen |
| BE-009 | WebSocket-Ende-zu-Ende-Test | ✅ | ✅ | Test-Client in Tests; echter Geräte-End-to-End-Test ⏳ |
| BE-010 | CORS aus Konfiguration | ✅ | ✅ | `CORS_ORIGINS` statt `*` |
| BE-011 | Crowd-/Search-API | ✅ | ✅ | Bestehende Endpunkte + Tests |
| BE-012 | `/api/health` mit DB-Check | ✅ | ✅ | Tests |

---

## 4. Hardware

| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| HW-001 | Android-Testgerät | ⏳ | ❌ | - |
| HW-002 | ESP32-Board | ⏳ | ❌ | - |
| HW-003 | LoRa-Modul (SX1276/Ra-02) | ⏳ | ❌ | - |
| HW-004 | MQTT-Broker/Gateway | ⏳ | ❌ | - |
| HW-005 | BLE-Geräte | ⏳ | ❌ | - |
| HW-006 | NFC-Tags | ⏳ | ❌ | - |
| HW-007 | USB-Seriell-Geräte | ⏳ | ❌ | - |
| HW-008 | Reale Sensoren | ⏳ | ❌ | - |
| HW-009 | WLAN-/GPS-Testumgebung | ⏳ | ❌ | - |

---

## 5. Noch offene Programmfunktionen

| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| FUNC-001 | MQTT-Suchantworten implementieren | ✅ | ✅ | `MqttConfig.TOPIC_SEARCH_RESPONSE`, `MqttService`, `AgentService` |
| FUNC-002 | WebSocket-Suchantworten implementieren | ✅ | ✅ | `WebSocketService`, `AgentService` |
| FUNC-003 | Provider-Fehler von „keine Ergebnisse" unterscheiden | ✅ | ✅ | `SearchResult.providerErrors`, `AgentService.comprehensiveSearch` |
| FUNC-004 | Feste Demo-/Fallbackwerte entfernen | ✅ | ✅ | Debug-Seed + Default-Broker-Fallback + Berlin-Fallbacks |
| FUNC-005 | Künstliche RSSI-/Genauigkeitswerte entfernen | ✅ | ✅ | LoRa/Urban/Satellite/Telemetry/API-Nodes |
| FUNC-006 | Historische GPS-Daten korrekt kennzeichnen | ✅ | ✅ | `Detection.isHistorical` + Migration 2→3 |
| FUNC-007 | API-Contract-Tests ergänzen | ✅ | ✅ | `backend/tests/test_api.py` (OpenAPI/Contract) |
| FUNC-008 | Hardware-in-the-loop-Tests ergänzen | ⏳ | ❌ | Testgeräte nötig |
| FUNC-009 | Room-Datenbankmigrationen ergänzen | ✅ | ✅ | Migration 2→3 |
| FUNC-010 | Verschlüsselung der lokalen Datenbanken | ⏳ | ✅ | AES/GCM für Datenfelder vorhanden (`EncryptionService`); Room-DB selbst noch nicht auf SQLCipher umgestellt (Android-Build derzeit nicht verifizierbar) |

---

## 6. Infrastruktur / CI / Dokumentation

| ID | Aufgabe | Status | Ohne Hardware? | Hinweise |
|----|---------|--------|----------------|----------|
| OPS-001 | `.env.example` | ✅ | ✅ | Backend/Docker-Env |
| OPS-002 | Docker-Compose mit TLS-fähigem Mosquitto | ✅ | ✅ | `mosquitto/config/mosquitto.conf` (TLS vorbereitet) |
| OPS-003 | Setup-Skripte idempotent | ✅ | ✅ | `scripts/*.sh` |
| OPS-004 | README-Abschnitt „Lokale Build-Umgebung" | ✅ | ✅ | README.md |
| OPS-005 | Node-RED-Dashboard | ⏳ | ✅ | Standard-Bild, Flow importierbar; eigenes Flow fehlt |
| OPS-006 | GitHub-Actions-Build | ✅ | ✅ | bestehender Workflow |

---

## 7. Nächste Prioritäten / Empfohlene Reihenfolge

1. `./scripts/setup-local-env.sh --all` ausführen (JDK + SDK + PlatformIO + Backend).
2. `local.properties` / `.env` mit Sitzungsdaten füllen.
3. `./scripts/test-all.sh` ausführen (Gradle-Test + Backend-Pytest + PlatformIO-Build ab Firmware).
4. Backend starten: `./scripts/start-dev-stack.sh` bzw. `uvicorn main:app --host 0.0.0.0 --port 8000`.
5. Android-App mit `./gradlew installDebug` auf Gerät installieren.
6. ESP32-Firmware mit `pio run --target upload` auf das Board bringen.
7. Anschließend Hardware-in-the-loop-Szenarien abarbeiten (HIL-Tests).
