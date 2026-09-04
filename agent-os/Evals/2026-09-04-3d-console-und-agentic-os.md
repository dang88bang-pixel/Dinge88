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

## Nicht nachgewiesen

Ehrlich und vollständig:

1. **Kein Kotlin-Build.** Die Sandbox hat kein JDK und kein Android-SDK,
   `dl.google.com` ist blockiert. Der gesamte Kotlin-Anteil ist **gelesen,
   nicht kompiliert**. Erste Handlung auf einer Maschine mit Toolchain:
   `./gradlew :app:assembleDebug`.
2. **Kein Browser.** Die 3D-Szene wurde nie gerendert. Nachgewiesen ist nur,
   dass sie fehlerfrei bündelt. Die Laufzeit ist ungeprüft.
3. **Ungeprüfte API-Annahmen** im neuen Kotlin-Code — beim ersten Build als
   Erstes zu erwarten:
   - `SecureGuardRepository.acknowledgeAlert(Long)` / `acknowledgeAllAlerts()`
   - `AgentService.sendAction(asset, String)`
   - `RoleManager.require(Permission)` und `currentRole`
   - `AgentStatus.uptimeMillis` / `.cycle` / `.settings.interval`
   - `Asset.maintenanceDue` / `.shortName` / `.mac` / `.rssi`
   - `Alert.severity` / `.timestamp`
   - Routen `SCAN_QR`, `ADD_ASSET`, `assetDetail`, `SENSOR_FUSION`,
     `AGENT_CONFIG`, `ESP32_CONFIG`, `NODE_STATUS`, `TERMINAL`, `TEMP_MAIL`,
     `ALERTS`
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

- [ ] `./gradlew :app:assembleDebug` auf einer Maschine mit Toolchain,
      Annahmen aus Abschnitt „Nicht nachgewiesen" Punkt 3 nachziehen
- [ ] 3D-Konsole im Browser und in der App-WebView rauchtesten
- [ ] `agent-os/Tasks/aktionskatalog-drift-schutz.md` (P0)
- [ ] `agent-os/Tasks/design-system-restmigration.md` (P1)
- [ ] `agent-os/Tasks/3d-leistungsbudget.md` (P1)
- [ ] `agent-os/Tasks/alarm-badge-navigation.md` (P2)

## Bewertung

| Achse | Note | Begründung |
|-------|------|------------|
| Zielbezug | grün | Zahlt auf G1, G2, G3, G5 ein |
| Nachweis | **gelb** | Konsole belegt, Kotlin ungeprüft — Toolchain fehlt |
| Hinterlassenschaft | grün | Ziele, Aufgaben, Wissen und Skills sind dokumentiert |
