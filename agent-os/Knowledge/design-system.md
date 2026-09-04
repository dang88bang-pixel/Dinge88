# Knowledge: Design-System

Alle sichtbaren Bausteine kommen aus `presentation/designsystem/`.
Ad-hoc-Karten mit eigenen Farben sind die Ursache dafür, dass Software wie ein
Prototyp aussieht — deshalb hier verboten.

---

## Tokens (`designsystem/Tokens.kt`)

```kotlin
object Sg {
    object Space  { xs, sm, md, lg, xl, xxl }
    object Radius { sm, md, lg, xl, pill }
    object Size   { icon, tile, dot, bar, … }
}
```

Dazu Zuordnungsfunktionen, die überall dieselbe Semantik erzwingen:

| Funktion | Zweck |
|----------|-------|
| `statusColor(AssetStatus)` / `statusLabel(…)` | Farbe und Text je Asset-Status |
| `batteryColor(Int?)` | Grün → Bernstein → Rot |
| `severityColor(String)` / `severityLabel(…)` | Alarmstufen |
| `sourceColor/sourceIcon/sourceLabel(String)` | Erfassungskanäle (BLE, WiFi, LoRa, …) |
| `relativeTime(Long?)` | „vor 3 Min." statt Rohzeitstempel |
| `compactDuration(Long)` | Laufzeit des Agenten |

**Regel:** Kein `Color(0x…)` in `presentation/ui/**`. Farben kommen aus
`theme/Color.kt` oder aus einer dieser Funktionen.

## Bausteine (`designsystem/SgComponents.kt`)

| Baustein | Verwendung |
|----------|------------|
| `SgCard` | Basiscontainer; optionaler Akzentrand, `selected`-Zustand |
| `SgSectionHeader` | Abschnittstitel mit Icon, Untertitel, Aktion rechts |
| `SgStatusDot` | Statuspunkt, optional pulsierend (`live`) |
| `SgPill` | Kompaktes Etikett; gefüllt oder umrandet |
| `SgSignalBars` | RSSI als Balken (statt nackter dBm-Zahl) |
| `SgMeter` | Horizontaler Balken (Batterie, Auslastung) |
| `SgProgressRing` | Ringfortschritt |
| `SgSparkline` | Verlauf ohne Achsen, für Kacheln |
| `SgMetricTile` | Kennzahl + Label + Trend + optionale Sparkline |
| `SgQuickTile` | Navigationskachel mit Icon, Titel, Untertitel, Badge |
| `SgEmptyState` | Leerzustand mit Icon, Text und optionaler Aktion |
| `SgSkeleton` | Ladeplatzhalter |
| `SgConfirmDialog` | Bestätigung für kritische Aktionen |

## Muster

**Jeder Listenbildschirm** hat drei gestaltete Zustände: laden (`SgSkeleton`),
leer (`SgEmptyState`), gefüllt. Ein leerer Bildschirm ohne Erklärung gilt als
Fehler.

**Jede kritische Aktion** (Risiko `CRITICAL`) läuft über `SgConfirmDialog`.
Kein direkter Auslöser.

**Jede Kennzahl** bekommt Kontext: entweder Trend, Sparkline oder Vergleich.
Eine nackte Zahl beantwortet nicht die Frage „ist das gut?".

**Zeitstempel** werden nie roh angezeigt — immer `relativeTime()`.

## Compose-Fallstricke in diesem Projekt

- `PullToRefreshBox` und `menuAnchor()` werden **nicht** verwendet
  (API-Instabilität zwischen Material3-Versionen). Refresh läuft über einen
  Icon-Button in der TopAppBar, Menüs über `DropdownMenu` in einer `Box`.
- Zustand, der in einem `LazyListScope`-Lambda gelesen wird, löst nicht
  zuverlässig eine Neuzusammensetzung aus. Abgeleitete Listen deshalb **im
  Composable-Scope** mit `remember(key)` berechnen und fertig in `LazyColumn`
  hineingeben.
- `FilterChip` braucht je nach Version `@OptIn(ExperimentalMaterial3Api::class)`
  — beim Einsatz mit annotieren.

## Sprache

Nutzertexte deutsch, Bezeichner und Ressourcen-Keys englisch
(`nav_dashboard`, `action_alarm`). Keine gemischten Sätze.
