# 🤖 6-AGENTEN PARALLEL-PLAN – SecureGuard Enterprise
## Übersicht, Zuordnung und Abhängigkeiten

> **Erstellt:** 2026-08-23  
> **6 Agenten arbeiten parallel** an getrennten Fehlerquellen  
> **Jeder Agent hat:** eigene Dateien, klare Abnahmekriterien, Testbefehle

---

## ZUORDNUNG

| Agent | Datei | Scope | Tasks | Aufwand |
|:---:|---|---|:---:|:---:|
| **1** | `AGENT_1_KOMPILIERUNG.md` | Presentation-Layer (Screens, ViewModels, Components) | 8 | ~4h |
| **2** | `AGENT_2_DETECTION_SIMPLE.md` | BLE, WiFi, Optical, Satellite Services | 4 | ~6-8h |
| **3** | `AGENT_3_DETECTION_COMPLEX.md` | LoRa, Urban, Crowd Services + Backend-Crowd | 4 | ~8-10h |
| **4** | `AGENT_4_TELEMETRY_ACTIONS.md` | TelemetryService + Aktionsketten + performRegistration | 4 | ~8-10h |
| **5** | `AGENT_5_INTEGRATION_UI.md` | ApiNodeManager, RoleManager, NFC, Settings, UI-Polish | 10 | ~10-14h |
| **6** | `AGENT_6_FIRMWARE_BACKEND.md` | ESP32-Firmware, Backend, Docker-Compose, API-Docs | 8 | ~10-14h |

---

## DATEI-ZUORDNUNG (wer bearbeitet was)

| Datei | Agent |
|---|:---:|
| `presentation/ui/dashboard/DashboardScreen.kt` | **1** |
| `presentation/ui/dashboard/DashboardViewModel.kt` | **1** |
| `presentation/ui/assets/AssetDetailScreen.kt` | **1** |
| `presentation/navigation/SecureGuardApp.kt` | **1** |
| `services/BleService.kt` | **2** |
| `services/WifiService.kt` | **2** |
| `services/OpticalService.kt` | **2** |
| `services/SatelliteService.kt` | **2** |
| `services/LoraService.kt` | **3** |
| `services/UrbanService.kt` | **3** |
| `services/CrowdService.kt` | **3** |
| `backend/main.py` (Crowd-Endpoint) | **3** |
| `services/TelemetryService.kt` | **4** |
| `presentation/ui/actions/ActionsViewModel.kt` | **4** |
| `presentation/ui/assets/AssetDetailViewModel.kt` | **4** |
| `services/AgentService.kt` (performRegistration) | **4** |
| `services/AgentService.kt` (ApiNodeManager, NFC, Flush) | **5** |
| `MainActivity.kt` (NFC) | **5** |
| `presentation/ui/settings/SettingsViewModel.kt` | **5** |
| `presentation/ui/agent/AgentViewModel.kt` | **5** |
| `presentation/ui/nodes/NodeStatusViewModel.kt` | **5** |
| `presentation/ui/settings/SettingsScreen.kt` | **5** |
| `SecureGuardApplication.kt` | **5** |
| `firmware/secureguard_esp32/secureguard_esp32.ino` | **6** |
| `backend/main.py` (WebSocket, Logging, MQTT-Sub) | **6** |
| `docker-compose.yml` | **6** |
| `docs/api-docs.yaml` | **6** |
| `mosquitto/config/mosquitto.conf` | **6** |
| `backend/requirements.txt` | **6** |

---

## ÜBERSCHNEIDUNGEN (Koordination nötig)

| Datei | Agent A | Agent B | Konfliktlösung |
|---|:---:|:---:|---|
| `AgentService.kt` | **4** (performRegistration) | **5** (ApiNodeManager, NFC, Flush) | Agent 4 bearbeitet NUR `performRegistration()` (Zeilen ~290-310). Agent 5 bearbeitet Constructor, `buildChannelList()`, `startRealtimeChannels()`, `runCycle()`. Keine Überlappung. |
| `backend/main.py` | **3** (Crowd-Endpoints) | **6** (WebSocket, Logging) | Agent 3 fügt NUR `/api/crowd/*` Endpoints + `crowd_sightings`-Tabelle hinzu. Agent 6 bearbeitet WebSocket-Endpoint, `process_action()`, MQTT-Subscriber. Keine Überlappung. |
| `ActionsViewModel.kt` | **4** (executeAction) | **5** (RoleManager-Check) | Agent 4 ändert Constructor + `executeAction()`. Agent 5 fügt Permission-Check IN `executeAction()` hinzu. **Reihenfolge: erst Agent 4, dann Agent 5.** |
| `AssetDetailViewModel.kt` | **4** (executeAction) | **1** (Screen-Signatur) | Kein Konflikt – Agent 1 bearbeitet nur `AssetDetailScreen.kt`, Agent 4 nur das ViewModel. |

---

## ABHÄNGIGKEITS-MATRIX

```
Agent 1 (Kompilierung)     ← UNABHÄNGIG, kann sofort starten
Agent 2 (Detection Simple) ← UNABHÄNGIG, kann sofort starten
Agent 3 (Detection Complex)← UNABHÄNGIG, kann sofort starten
Agent 4 (Telemetrie+Action)← UNABHÄNGIG, kann sofort starten
Agent 5 (Integration+UI)   ← WARTET auf Agent 4 (ActionsViewModel-Constructor)
Agent 6 (Firmware+Backend) ← UNABHÄNGIG, kann sofort starten
```

### Empfohlene Start-Reihenfolge:

```
T=0h  ──┬── Agent 1 (Kompilierung, ~4h)
        ├── Agent 2 (Detection Simple, ~6h)
        ├── Agent 3 (Detection Complex, ~8h)
        ├── Agent 4 (Telemetrie+Action, ~8h)
        └── Agent 6 (Firmware+Backend, ~10h)

T=4h  ──── Agent 1 FERTIG → Merge + Build-Test

T=8h  ──┬── Agent 2 FERTIG
        ├── Agent 4 FERTIG → Agent 5 kann starten

T=8h  ──── Agent 5 (Integration+UI, ~10h) ← startet nach Agent 4

T=16h ──── Agent 3 FERTIG
T=18h ──── Agent 6 FERTIG
T=18h ──── Agent 5 FERTIG

T=20h ──── INTEGRATIONSTEST (alle Agenten merged)
```

---

## INTEGRATIONSTEST (nach allen Agenten)

```bash
# 1. Vollständiger Build
cd /home/user/Dinge88 && ./gradlew :app:assembleDebug 2>&1 | tail -50

# 2. Keine Random-Simulationen mehr
grep -rn "import kotlin.random.Random" app/src/main/java/com/secureguard/enterprise/services/
# Erwartet: 0 Treffer

# 3. Keine Fake-Detection-Generatoren
grep -rn "ble-sim\|wifi-ap\|DummyLoraClient\|52\.5200.*Random\|13\.4050.*Random" \
  app/src/main/java/com/secureguard/enterprise/services/
# Erwartet: 0 Treffer

# 4. Aktionskette korrekt
grep -rn "agentService.sendAction" \
  app/src/main/java/com/secureguard/enterprise/presentation/
# Erwartet: Treffer in ActionsViewModel + AssetDetailViewModel

# 5. Keine TODO-Platzhalter
grep -rn "TODO" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: 0 Treffer

# 6. Backend-Test
cd backend && python -c "from main import app; print('Backend OK')"

# 7. ApiNodeManager eingebunden
grep -n "apiNodeManager" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 2 Treffer

# 8. NFC-Integration
grep -n "nfcService\|nfcCollectorJob" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 3 Treffer

# 9. Settings → Agent
grep -n "agentService" app/src/main/java/com/secureguard/enterprise/presentation/ui/settings/SettingsViewModel.kt
# Erwartet: mindestens 2 Treffer

# 10. Firmware konfigurierbar
grep -n "Preferences\|loadConfig\|saveConfig" firmware/secureguard_esp32/secureguard_esp32.ino
# Erwartet: mindestens 5 Treffer
```

---

## ANWEISUNGEN FÜR JEDEN AGENTEN

### Für alle Agenten gilt:

1. **Datei lesen** – Vor jeder Änderung die komplette Zieldatei lesen
2. **Änderung vornehmen** – Exakt die beschriebenen Änderungen durchführen
3. **Imports prüfen** – Alle nötigen Imports hinzufügen, überflüssige entfernen
4. **Syntax prüfen** – Mit `grep` und `wc -l` die Datei-Struktur validieren
5. **Testbefehle ausführen** – Alle `PRÜFUNG & TEST`-Befehle am Ende der Liste ausführen
6. **Ergebnis dokumentieren** – Abnahmekriterien abhaken

### Bei Konflikten:

- **AgentService.kt**: Agent 4 NUR `performRegistration()`, Agent 5 alles andere
- **backend/main.py**: Agent 3 NUR Crowd-Endpoints, Agent 6 alles andere
- **ActionsViewModel.kt**: Agent 4 zuerst (Constructor), Agent 5 danach (Permission-Check)

### Bereitstellung:

Jeder Agent committet seine Änderungen auf dem Branch `arena/01a02dba-dinge88`:
```bash
git add -A
git commit -m "fix(agent-N): <kurze Beschreibung>"
```

Nach allen 6 Agenten:
```bash
git push origin arena/01a02dba-dinge88
```
