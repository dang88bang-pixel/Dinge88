# Knowledge: 3D Operations Center

Kostenfreies Lagebild auf Basis von **Three.js (MIT)**. Läuft aus einer
Codebasis sowohl im Browser als auch in der Android-App.

---

## Warum Three.js

| Kriterium | Bewertung |
|-----------|-----------|
| Lizenz | MIT, keine Tiers, keine Laufzeitgebühren |
| Größe | Baumschüttelbar, aktuelles Bundle 165 kB gzip |
| WebView | Läuft in Android-WebView (WebGL2) ohne Plugin |
| Ökosystem | `OrbitControls`, `EffectComposer`, `UnrealBloomPass`, `CSS2DRenderer` in `three/addons/*` enthalten |
| Ausstieg | Reines WebGL, keine proprietäre Laufzeit |

Verworfen: Unity/Unreal (Lizenz- und Größenproblem), Babylon.js (größer, kein
Vorteil hier), bezahlte Karten-SDKs (Kostenpflicht widerspricht der Vorgabe).

## Aufbau

```
console3d/
├── index.html          HUD-Markup; IDs sind der Vertrag zum JS
├── vite.config.js      base './', Host 0.0.0.0:5173, Proxy /api -> Backend
└── src/
    ├── main.js         Verdrahtung, Quellenwahl, Tastaturkürzel
    ├── style.css       Tokens spiegeln die App-Farben
    ├── core/
    │   ├── store.js        Zustand + Ereignisbus (on/emit)
    │   ├── simulation.js   12 Assets, Bewegung, Detektionen, Alarme
    │   ├── api.js          FastAPI-Anbindung inkl. WebSocket
    │   ├── native.js       Brücke zur Android-App
    │   └── geo.js          lat/lon -> Szenenkoordinaten
    ├── data/catalog.js Kanäle, Kategorien, Aktionen (Spiegel des Kotlin-Katalogs)
    ├── scene/world.js  Szene, Kamera, Postprocessing, Knoten, Labels
    └── ui/              dom.js · panels.js · dock.js · overlays.js
```

## Datenquellen (Reihenfolge)

```
native  →  backend  →  simulation
```

- **native**: `window.SecureGuardNative` vorhanden → App liefert echte Daten
- **backend**: `/api/health` antwortet → FastAPI liefert Daten, WS liefert Live
- **simulation**: immer verfügbar

Die Konsole ist damit **nie leer**. Das ist Absicht: ein leeres Lagebild ist
im Zweifel schlimmer als ein erkennbar simuliertes. Die aktive Quelle steht
sichtbar im HUD und lässt sich mit `D` durchschalten.

## Szenenkonstanten

| Konstante | Wert |
|-----------|------|
| `METERS_PER_UNIT` | 42 |
| `CHANNEL_RADIUS` | 34 |
| `GROUND_RADIUS` | 120 |
| Ringe | 6 / 12 / 24 |
| Kamera | Position (0, 42, 62), FOV 52 |
| Ursprung | lat 51.4344, lon 6.7623 (Duisburg) |

## Tastaturkürzel

| Taste | Wirkung |
|-------|---------|
| `⌘/Strg + K` | Befehlspalette |
| `Esc` | Schließen / Auswahl aufheben |
| `Leertaste` | Agent starten/stoppen |
| `L` / `A` | Log- / Alarm-Schublade |
| `V` | Ansicht wechseln |
| `D` | Datenquelle wechseln |
| `H` | Hilfe |
| `1`–`8` | Aktion ausführen |
| `r f g m` | Szenen-Aktionen (Sweep, Fokus, Geofence, Heatmap) |

Unterdrückt in Eingabefeldern und bei offenem Dialog/Palette.

## Einbettung in die App

ES-Module lassen sich in einer WebView nicht über `file://` laden (CORS).
Deshalb:

1. `vite.config.js` setzt `base: './'` — das Bundle ist ortsunabhängig.
2. `scripts/sync-console3d.sh` kopiert `dist/` nach
   `app/src/main/assets/console3d/`.
3. `OpsCenter3DScreen.kt` fängt in `shouldInterceptRequest` alle Anfragen an
   `https://ops.secureguard.local/*` ab und liefert die Assets aus.
4. `OpsCenterViewModel` hält einen `@Volatile snapshot: String`, den die
   Brücke synchron zurückgeben kann.

Bewusst **nicht** verwendet: `androidx.webkit` / `WebViewAssetLoader` — die
zusätzliche Abhängigkeit bringt hier keinen Vorteil gegenüber den zwölf Zeilen
Interception.

## Entwicklung

```bash
cd console3d
npm ci
npm run dev      # http://0.0.0.0:5173 – Simulationsmodus ohne Backend
npm run build    # -> console3d/dist
```

Mit Backend: `SECUREGUARD_BACKEND=http://127.0.0.1:8000 npm run dev`.

## Fallstricke (bereits einmal aufgetreten)

- Panels müssen `scrollTop` vor dem Neuzeichnen sichern und danach
  wiederherstellen, sonst springt die Liste bei jedem Tick nach oben.
- Automatisches Semikolon-Einfügen: Zeilen, die mit `(` oder `[` beginnen,
  brauchen ein führendes Semikolon.
- Farbliterale in Materialien immer sechsstellig schreiben (`0x1d3a5c`),
  gekürzte Werte führen zu stummen Fehlern.
- `CSS2DRenderer`-Labels sind DOM-Knoten — bei vielen Assets sind sie und
  nicht WebGL der Flaschenhals.
