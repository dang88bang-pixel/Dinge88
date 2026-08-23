# 📋 CHECKLISTE-ABARBEITUNG – SECUREGUARD ENTERPRISE

**Stand:** Gesamt-Aufräumung 2026-08-23 (Branch `arena/01a02dcb-dinge88`).
Zielvorgabe: keine Platzhalter/Mock-Templates/Demo-Parts, ausschließlich aktiv
ausführbare Komponenten, vollständige Berechtigungen, Anbindungen, Service-Worker
und Abhängigkeiten.

Legende: ☑ erledigt · 🔶 vollständig, aber externe Ressource (Key/Endpunkt/Hardware) nötig

---

## 1. Repository-Aufräumung

| Schritt | Status |
|---|---|
| `stitch_native_android_operative (1).zip` (4,6 MB Fremd-Artefakt) entfernt | ☑ |
| `.gitignore` erweitert (`*.zip`, `*.db`, `data/`, `nodered/`, Python-Artefakte) | ☑ |
| `BETRIEBSVEREINBARUNG.md` war 1 Byte (leerer Platzhalter) → vollständige Blaupause §1–8 | ☑ |
| `PERMISSIONS_VALIDATION.md` auf aktuellen Manifest-Stand (27 Permissions) | ☑ |
| `IMPLEMENTIERUNGS_INVENTUR.md` = geführter Echtstand (keine falschen Behauptungen mehr) | ☑ |
| CI-Workflow: `android-34` → `android-35` (compileSdk-Mismatch behoben) | ☑ |
| `dependabot.yml` real angelegt (gradle/actions/pip/docker) | ☑ |

## 2. Berechtigungen (Manifest)

| Ergänzung | Zweck |
|---|---|
| `ACCESS_BACKGROUND_LOCATION` | Agent-Worker-Suche bei ausgeschaltetem Screen |
| `FOREGROUND_SERVICE_LOCATION` | API 34+: GPS im Vordergrunddienst |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34+: BLE-GATT im Vordergrunddienst |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | zuverlässiger 15-Min-Takt (Anfrage in Einstellungen) |
| `NEARBY_WIFI_DEVICES` | WiFi-Scan-Ergebnisse ab API 33 |
| FGS-Typ `dataSync\|location\|connectedDevice` | AgentForegroundService |
| Receiver `BootCompletedReceiver` registriert | BOOT_COMPLETED + MY_PACKAGE_REPLACED |

## 3. Service-Worker & Empfänger

| Komponente | Funktion | Status |
|---|---|---|
| `SecureAgentWorker` | 15-Min-Suchzyklus (WorkManager, Hilt) | ☑ |
| `MaintenanceWorker` **(neu)** | täglich: Retention-Cleanup + Offline-Queue-Nachlieferung + Ergebnis-Benachrichtigung | ☑ |
| `BootCompletedReceiver` **(neu)** | Worker neu planen; Agent-FGS wieder aufnehmen (Flag `agent_autostart`), FGS-Restriktions-Fallback | ☑ |
| ConnectivityWatcher **(neu, Application)** | `registerNetworkCallback`: bei Internet → Offline-Queue flushen + MQTT/WS verbinden | ☑ |

## 4. Bisher tote Komponenten → jetzt angebunden

| Komponente | Anbindung |
|---|---|
| `AgentForegroundService` | Start/Stop aus Dashboard-Toggle (+ Autostart-Flag für Reboot) |
| `ApiNodeManager.detections` | Agent-Service sammelt Flow → DB-Persistenz + Asset-Update |
| `RoleManager` (RBAC) | `AuthManager.currentUser` (persistierte Rolle) + Prüfung bei Aktionen & Löschen + Rollen-Umschalter in Einstellungen |
| `ExportService` | Einstellungen → Assets-CSV, Detektionen-CSV, verschlüsselter CSV-Export |
| `BackupManager` | Einstellungen → Backup erstellen/neuestes wiederherstellen; `applyPendingRestoreIfPresent()` beim App-Start |
| `DatabaseCleanup` | `MaintenanceWorker` (täglich geplant) |
| `AlertSoundManager` | Agent-Alarmereignisse (MQTT/WS) + Alerts-Screen (Ton je Stufe, Stop-Button) |
| `CacheManager` | WiGle-Lookups (TTL 5 min, inkl. Negativ-Cache, Fehler nicht gecacht) |
| `ErrorHandler` | Agent-Zyklus-Fehler → Logcat + Audit-Log |
| `RetryManager` | LoRa-Backend-HTTP mit Backoff (2 Versuche) |
| `AccessibilityHelper` | TalkBack-`contentDescription` auf AssetCard (vollständiger Zustand) |
| `AssetPagingSource` | echte DB-Paginierung der Asset-Liste (Paging 3, `flatMapLatest` bei Filterwechsel, DAO-Status-Filter neu) |
| `UsbSerialService` | USB-Diagnose in Einstellungen (echte Adapter-Erkennung FTDI/CP210x/CH34x) |

## 5. Compile-Brüche behoben

- `AssetDetailScreen` referenzierte nicht existierende ViewModel-Funktionen und
  nahm `navController` nicht an → komplett neu gebaut (Telemetrie/Suche/Historie/
  Aktionen/Verwaltungsmenü mit RBAC-Löschschutz).
- Dashboard (Vorrunderunde) und NavHost-Signaturen synchron.

## 6. Externe Ressourcen (🔶 bewusst Key-/Endpunkt-gebunden, keine Simulation)

WiGle/OCM/Netatmo/Google/HELIUM-Keys, MQTT/WS/MCP/LORA/YOLO/URBAN/CROWD/CKAN-URLs,
DHL-Vertragszugang, physische BLE-Geräte, GPS-Fix — ohne diese liefern die
Kanäle ehrlich „nicht gefunden".

## 7. Offen / Nächstschritte

- 🔶 CI-Build über GitHub Actions verifizieren (kein Android-SDK in der Sandbox)
- 🔶 Play-Store-Review wäre für `ACCESS_BACKGROUND_LOCATION` eine Deklaration nötig (Pilot: Sideload/MDM)
