# BACKLOG.md — Schnellerfassung

Unsortierter Eingang. Hier landet alles, was auffällt, ohne dass sofort eine
saubere Aufgabe daraus wird.

**Verarbeitung:** Workflow `agent-os/Workflows/backlog-verarbeiten.md`.
Jeder Eintrag wird zu einer Datei in `agent-os/Tasks/`, mit Zielbezug,
Priorität und Nachweis — oder wird gestrichen.

---

## Offen

- [ ] `StatCard.kt` und `ActionButton.kt` sind seit der Design-System-Migration
      verwaist — löschen oder auf `SgMetricTile`/`SgQuickTile` umbiegen.
- [ ] Alarm-Badge in der Bottom-Navigation
      (`repository.getUnacknowledgedAlertCount()` existiert bereits).
- [ ] `Theme.kt`: der `dynamicColor`-Zweig ist tot — entfernen oder als
      Einstellung sichtbar machen.
- [ ] Restliche Screens auf das Design-System heben: Karte, Sensor-Fusion,
      Node-Status, Terminal, Health, Temp-Mail, Einstellungen.
- [ ] Konsolen-Build (`npm run build`) in `.github/workflows/build-release.yml`
      aufnehmen, damit App-Assets und Quelle nicht auseinanderlaufen.
- [ ] Drift-Test: Kotlin-`ActionCatalog` gegen `console3d/src/data/catalog.js`
      automatisch vergleichen.
- [ ] Kaltstart bis erste Position messen (Ziel G1) — Makrobenchmark.
- [ ] 3D-Konsole auf echtem Mittelklasse-Gerät profilen; ggf.
      Bloom-Pass unter einem FPS-Schwellwert abschalten.
- [ ] Screenshot-Tests für Dashboard/Aktionen/Assets (Paparazzi oder Roborazzi).
- [ ] Backend: `/api/actions/execute` liefert nur `queued` — echten
      Zustellstatus zurückmelden.
- [ ] Barrierefreiheit: `contentDescription` in den neuen Composables prüfen,
      TalkBack-Durchlauf.
- [ ] Konsole: Tastaturkürzel-Übersicht auch als sichtbares Panel, nicht nur `H`.

## Ideen (nicht eingeplant)

- Zeitraffer-Wiedergabe der Detektionen in der 3D-Szene
- Geofence-Editor direkt in der Konsole (aktuell nur Anzeige)
- Export des Lagebilds als PDF-Einsatzprotokoll
- WebXR-Ansicht der Szene (Three.js kann das kostenfrei)

## Erledigt (Archiv)

- [x] Kostenfreie 3D-Engine auswählen → Three.js (MIT)
- [x] 3D Operations Center bauen (Szene, HUD, Aktionen, Palette)
- [x] WebView-Bridge App ⇄ Konsole (`SecureGuardNative`)
- [x] Design-System `Sg*` einführen
- [x] Aktionen-Center mit Katalog, Favoriten, Verlauf, Offline-Queue
- [x] Dashboard, Asset-Liste und Asset-Karte neu bauen
- [x] Agentic-OS-Framework auf das Projekt adaptieren
