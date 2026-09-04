# 2026-09-04 — 3D Operations Center, UI-Neubau, Agentic OS

## Auftrag

> „Benutzeroberfläche und Funktionen qualitativ massiv aufwerten in Nutzung und
> Aktionen." Anschließend: „Nutze eine kostenfreie 3D-Engine für die
> Oberfläche, ihren Inhalt und für zusätzliche Aktionen in der Anwendung.
> Adaptiere außerdem agentic-os vollständig auf den Projektkontext.
> Professionell, qualitativ hochwertig, stabil."

## Geändert

**Neu: 3D Operations Center (`console3d/`)**
- Three.js (MIT) — kostenfrei, keine Tiers. Szene mit Boden, Gitter, Ringen,
  Sternenfeld, Agent-Kern, 12 Kanal-Pylonen, Asset-Knoten mit CSS2D-Labels,
  `UnrealBloomPass`.
- HUD: Kennzahlen, Kanalliste, Ereignis-Feed, Asset-Liste, Aktions-Dock,
  Log- und Alarm-Schublade, Befehlspalette, Bestätigungsdialog, Toasts.
- Drei Datenquellen mit Rückfall: `native` → `backend` → `simulation`.
- Tastaturbedienung inkl. `⌘/Strg+K`-Palette.

**Neu: Einbettung in die App**
- `presentation/ui/ops/OpsCenterViewModel.kt` — Snapshot-Aggregation,
  `@Volatile` für synchronen Brückenzugriff.
- `presentation/ui/ops/OpsCenter3DScreen.kt` — WebView mit
  `shouldInterceptRequest` auf `https://ops.secureguard.local/*`,
  JS-Interface `SecureGuardNative`.
- Route `Routes.OPS_3D` in Navigation und NavHost.
- Gebautes Bundle in `app/src/main/assets/console3d/` (652 kB).

**Neu: Design-System**
- `presentation/designsystem/Tokens.kt` — Abstände, Radien, Statusfarben,
  Zeitformatierung.
- `presentation/designsystem/SgComponents.kt` — 13 Bausteine.

**Neu: Aktionen-Center**
- `presentation/ui/common/ActionCatalog.kt` — Kategorien, Risikostufen.
- `presentation/ui/actions/{ActionsViewModel,ActionsScreen}.kt` — Katalog,
  Favoriten, Mehrfachauswahl, Verlauf, Offline-Queue, Bestätigung bei
  kritischem Risiko.

**Neu gebaut auf dem Design-System**
- `presentation/ui/dashboard/{DashboardViewModel,DashboardScreen}.kt`
- `presentation/ui/assets/{AssetListViewModel,AssetListScreen}.kt`
- `presentation/components/AssetCard.kt`

**Neu: Agentic OS**
- `AGENTS.md`, `CLAUDE.md`, `CODEX.md`
- `agent-os/{GOALS,BACKLOG}.md`, `Tasks/` (4), `Workflows/` (4),
  `Knowledge/` (4), `Evals/`
- `.agents/skills/` — 8 projektspezifische Skills mit `openai.yaml`
- `scripts/sync-console3d.sh`

## Nachgewiesen

| Prüfung | Befehl | Ergebnis |
|---------|--------|----------|
| Konsolen-Build | `cd console3d && npx vite build` | grün — 633,6 kB JS (165 kB gzip), 19,0 kB CSS |
| Konsolen-Dev-Server | `npm run dev` auf 0.0.0.0:5173 | läuft, einbettbar (kein `X-Frame-Options`) |
| Shell-Skript | `bash -n scripts/sync-console3d.sh` | grün |
| Skill-Frontmatter | Parserlauf über alle `SKILL.md` | 8/8 gültig |
| Keine Testkollision | `grep` über `app/src/test` | keine der geänderten ViewModels wird von Tests konstruiert |
| Backend startet | `uvicorn main:app --host 0.0.0.0 --port 8000` | grün — MQTT fehlt, wird sauber degradiert |
| Backend-API | `GET /api/health` über den Vite-Proxy | `{"status":"ok","assets":12,...}` |
| Demo-Daten | `python3 scripts/seed-demo-data.py --seed 88` | 12 Assets, 75 Detektionen, 4 Alarme |
| Live-Datenstrom | Zähler zweimal im Abstand von 6 s gelesen | 160 → 163 Detektionen, Backendpfad lebt |
| Konsole liefert aus | `GET /` auf dem Dev-Server | HTTP 200, HUD-Markup vollständig |
| WebSocket-Proxy | `ws://127.0.0.1:5173/ws` verbunden | grün — Echtzeitpfad erreicht das Backend |
| Bundle deterministisch | `scripts/sync-console3d.sh` erneut | keine Änderung an den App-Assets |
| Kotlin-API-Annahmen | Abgleich gegen Modelle, Repository, Services | 14/14 bestätigt (siehe unten) |

## Nicht nachgewiesen

Ehrlich und vollständig:

1. **Kein Kotlin-Build.** Die Sandbox hat kein JDK und kein Android-SDK,
   `dl.google.com` ist blockiert. Der gesamte Kotlin-Anteil ist **gelesen,
   nicht kompiliert**.

   Lösungsweg gelegt, aber noch nicht abgeschlossen: `ci/workflows/ci.yml`
   baut `assembleDebug` und `testDebugUnitTest` auf jedem Push. Aktivierung
   scheiterte daran, dass das Agenten-Token die GitHub-Berechtigung
   `workflows` nicht besitzt — `bash scripts/install-ci-workflows.sh` plus ein
   Push mit `workflow`-Scope schließt die Lücke. Siehe
   `agent-os/Tasks/ci-workflows-aktivieren.md` (P0).
2. **Kein Browser.** Die 3D-Szene wurde nie gerendert. Nachgewiesen ist nur,
   dass sie fehlerfrei bündelt. Die Laufzeit ist ungeprüft.
3. **API-Annahmen inzwischen geprüft** (per Quelltextabgleich, nicht per
   Compiler) — alle 14 haben sich bestätigt:
   - `SecureGuardRepository.acknowledgeAlert(Long)` / `acknowledgeAllAlerts()` ✓
   - `getUnacknowledgedAlertCount(): Flow<Int>` ✓
   - `getAssetById(String)` / `resolveAsset(String)` ✓
   - `AgentService.sendAction(Asset, String): Boolean` ✓
   - `AgentService.start(AgentSettings)` / `stop()` / `flushOfflineQueue(): Int` ✓
   - `AgentService.agentStatus: StateFlow<AgentStatus>` ✓
   - `AgentStatus.uptimeMillis` / `.cycle` / `.settings.interval` ✓
   - `RoleManager.require(Permission): Boolean` (gibt zurück, wirft nicht) ✓
   - `RoleManager.currentRole` / `.role: StateFlow<Role>` ✓
   - `OfflineQueue.pending: Flow<List<PendingAction>>` ✓
   - `ActionType.wireCommand` / `.label` ✓
   - `Asset.batteryLevel: Int?`, `lastSeen: Date?` — im Code korrekt als
     `batteryLevel` bzw. `lastSeen?.time` verwendet ✓
   - `Alert.severity: AlertSeverity`, `timestamp: Date` — als Enum bzw.
     `.timestamp.time` verwendet ✓
   - Alle Routen (`SCAN_QR`, `ADD_ASSET`, `assetDetail`, `SENSOR_FUSION`,
     `AGENT_CONFIG`, `ESP32_CONFIG`, `NODE_STATUS`, `TERMINAL`, `TEMP_MAIL`,
     `ALERTS`, `HEALTH`, `SECURITY`) existieren in `Routes` ✓
   - `when`-Ausdrücke über `DetectionSource` (13 Werte), `AssetStatus` (5) und
     `AlertSeverity` (3) sind vollständig ✓

   Das ersetzt keinen Compilerlauf, schließt aber die Klasse von Fehlern aus,
   die hier am wahrscheinlichsten war.
4. **Keine Leistungsmessung** der Szene auf Zielhardware.
5. **Kein Rauchtest** der Brücke App ⇄ Konsole auf einem Gerät.

## Erkenntnisse

- Ein Snapshot-Modell (ein JSON-String, synchron abrufbar) ist für eine
  WebView-Brücke deutlich robuster als ereignisbasiertes Nachreichen: kein
  Zustand doppelt gehalten, kein Reihenfolgeproblem beim Neuladen.
- Der Simulationsmodus als unterste Rückfallebene war die wichtigste
  Entscheidung an der Konsole — sie ist dadurch ohne Backend, ohne App und
  ohne Hardware entwickel- und vorführbar.
- Zustand, der in einem `LazyListScope`-Lambda gelesen wird, löst keine
  zuverlässige Neuzusammensetzung aus. Nach `Knowledge/design-system.md`
  übernommen.
- Drei Kataloge für denselben Befehlssatz sind eine strukturelle Fehlerquelle.
  Deshalb Aufgabe `aktionskatalog-drift-schutz` als P0.

## Folgeaufgaben

- [ ] `agent-os/Tasks/ci-workflows-aktivieren.md` (P0) — CI liefert den
      fehlenden Kotlin-Build
- [ ] 3D-Konsole im Browser und in der App-WebView rauchtesten
- [ ] `agent-os/Tasks/aktionskatalog-drift-schutz.md` (P0)
- [ ] `agent-os/Tasks/design-system-restmigration.md` (P1)
- [ ] `agent-os/Tasks/3d-leistungsbudget.md` (P1)
- [x] Alarm-Badge in der Navigation (`AppShellViewModel`) — Rest der Aufgabe
      (gemeinsamer Quittierpfad) bleibt offen
- [x] Verwaiste `StatCard.kt` / `ActionButton.kt` entfernt
- [x] Toter `dynamicColor`-Zweig aus `Theme.kt` entfernt

## Bewertung

| Achse | Note | Begründung |
|-------|------|------------|
| Zielbezug | grün | Zahlt auf G1, G2, G3, G5 ein |
| Nachweis | **gelb** | Konsole, Backend und Datenpfad belegt; Kotlin per Quelltextabgleich geprüft, aber nicht kompiliert — Toolchain fehlt |
| Hinterlassenschaft | grün | Ziele, Aufgaben, Wissen und Skills sind dokumentiert |
