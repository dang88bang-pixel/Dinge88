# Knowledge: Architektur

Wo was liegt und warum. Vor jeder Änderung an einem Subsystem lesen.

---

## Monorepo

| Verzeichnis | Inhalt |
|-------------|--------|
| `app/` | Android-App — das Produkt |
| `console3d/` | 3D Operations Center (Three.js, Vite) |
| `backend/` | FastAPI-Ingest und Kommando-API |
| `firmware/` | ESP32-Gateway/Tag-Firmware |
| `mosquitto/`, `nodered/` | Stack-Konfiguration |
| `scripts/` | Setup, Rauchtest, Konsolen-Sync, Keystore |
| `docs/` | Betriebs- und Sicherheitsdokumentation |
| `agent-os/`, `.agents/` | Agentic OS: Ziele, Aufgaben, Workflows, Skills |

## Android-Paketstruktur

```
com.secureguard.enterprise
├── MainActivity.kt              Einstieg, Compose-Host
├── SecureGuardApplication.kt    @HiltAndroidApp
├── agent/                       Agentenlogik (Zyklus, Planung)
├── config/                      Endpunkte, Feature-Flags
├── data/
│   ├── local/                   Room + SQLCipher, DAOs
│   ├── model/                   Asset, Alert, Detection, …
│   └── repository/              SecureGuardRepository (einzige Datenfassade)
├── di/                          Hilt-Module
├── mcp/                         Model-Context-Protocol-Anbindung
├── presentation/
│   ├── designsystem/            Tokens.kt, SgComponents.kt  ← einzige UI-Quelle
│   ├── theme/                   Farben, Typografie
│   ├── navigation/              Routes, NavHost
│   ├── components/              projektspezifische Karten (AssetCard, …)
│   └── ui/<feature>/            je Feature: Screen + ViewModel
├── receiver/                    Broadcast-Empfänger
├── security/                    AuthManager, RoleManager, Permissions
├── services/                    Kanäle und Hintergrunddienste
├── util/                        Hilfsfunktionen
└── worker/                      WorkManager-Aufgaben
```

### Regel: Datenfluss ist einbahnig

```
Room / Netzwerk → Repository → ViewModel (StateFlow) → Composable
                                    ↑
                            Ereignisse (Funktionsaufrufe)
```

Composables halten keinen Geschäftszustand. ViewModels kennen keine
Compose-Typen. Repositories kennen keine ViewModels.

## Dienste (`services/`)

Grob drei Gruppen:

**Erfassungskanäle** — implementieren `DetectionCapable`:
`BleService`, `WifiService`, `NfcService`, `LoraService`, `SatelliteService`,
`OpticalService`, `UsbSerialService`, `CrowdService`, `UrbanService`.

**Transport** — `MqttService`, `WebSocketService`, `BackendSyncService`,
`ApiServiceManager`, `OfflineQueue`.

**Betrieb** — `AgentService` (Herzstück), `AgentForegroundService`,
`HealthMonitorService`, `TelemetryService`, `AuditLogService`,
`NotificationService`, `AlertSoundManager`, `PrivacyService`,
`ExportService`, `BackupManager`, `EncryptionService`, `LearningEngine`.

### AgentService

Führt zyklisch alle aktivierten Kanäle aus, schreibt Detektionen, erzeugt
Alarme und stellt `sendAction(asset, command)` bereit. Sein Zustand
(`AgentStatus`: läuft, Zyklus, Startzeit, Intervall, letzter Lauf) ist die
Grundlage der Agent-Anzeige im Dashboard und in der 3D-Konsole.

## Sicherheit

- `AuthManager` — Anmeldung, Sperre, Fehlversuchszähler
- `RoleManager` — Rollen und `Permission`-Prüfung; `require(Permission)` wirft
  bei fehlender Berechtigung
- `AuditLogService` — jede mutierende Aktion wird protokolliert
- SQLCipher verschlüsselt die lokale Datenbank
- Secrets ausschließlich über `local.properties` → `buildConfigField`

## Backend (`backend/main.py`)

| Route | Zweck |
|-------|-------|
| `GET /api/health` | Lebenszeichen |
| `GET /api/assets` | Whitelist |
| `GET /api/detections` | Detektionen |
| `GET /api/alerts` | Alarme |
| `GET /api/stats` | Kennzahlen |
| `GET /api/commands` | Kommandohistorie |
| `POST /api/actions/execute` | Aktion einreihen (`{status:"queued"}`) |
| `WS /ws` | Live-Ereignisse |
| `/api/mcp/*` | Model-Context-Protocol |

Die 3D-Konsole spricht dieselben Routen über den Vite-Proxy `/api` an.
