# Tests der 3D-Konsole

168 Tests, Laufzeit ca. 20 Sekunden:

```bash
cd console3d
npm test            # einmalig
npm run test:watch  # beim Entwickeln
```

## Was geprüft wird

| Datei | Umfang | Gegenstand |
|-------|--------|-----------|
| `catalog.test.js` | 12 | Stammdaten: Aktionen, Kategorien, Status, Kanäle, Tastenkürzel |
| `store.test.js` | 23 | Zustand, Ereignisse, Auswahl-Logik, Kappungsgrenzen, Favoriten |
| `simulation.test.js` | 22 | Flottenaufbau, Zustellung, Warteschlange, Simulationsschleife |
| `geo.test.js` | 9 | Projektion und Entfernungsberechnung |
| `native.test.js` | 20 | Brücke zur Android-App: Protokoll, Zeitüberschreitungen, Fehler |
| `api.test.js` | 21 | Backend-Vertrag: Übersetzung, Zeitstempel, API-Key, WebSocket |
| `app.test.js` | 46 | **Das komplette HUD** – zwölf Interaktionsketten |
| `native-boot.test.js` | 15 | Betrieb in der WebView der App, Ende zu Ende |

Die zwölf Ketten in `app.test.js` bilden ab, was eine Bedienerin tatsächlich
tut: Start ohne Backend, Ziele auswählen und filtern, unkritische Aktion
ausführen, kritische Aktion bestätigen **und** abbrechen, Freitext senden,
Lagebild-Aktionen, Tastatur, Befehlspalette, Alarme quittieren, Offline-Queue
leeren, Kopfzeile, Robustheit.

## Wie getestet wird

Kein vereinfachtes Test-DOM: `test/setup/boot.js` lädt den `<body>` des echten
`index.html` in jsdom und importiert danach `src/main.js`. Fehlt eine
Element-ID, schlägt der Test fehl – im Browser wäre das ein weißer Bildschirm.

Drei Dinge werden ersetzt, alles andere läuft echt:

* **`src/scene/world.js`** → `test/setup/world.mock.js`. jsdom hat keinen
  WebGL-Kontext. Die Attrappe zeichnet jeden Szenenaufruf auf, sodass Tests
  belegen können, dass ein Klick tatsächlich im Lagebild ankommt.
* **`requestAnimationFrame`** → manuelle Pumpe. Die Renderschleife von
  `main.js` würde sonst nie enden.
* **`fetch`** → schlägt fehl bzw. liefert feste Antworten, damit der
  Quellen-Fallback (App → Backend → Simulation) gezielt angesteuert werden kann.

## Was diese Tests nicht beweisen

* **Die WebGL-Darstellung.** Ob Kamera, Bloom und Instanzierung korrekt
  aussehen, zeigt nur ein echter Browser (`npm run dev`).
* **Die Android-Oberfläche.** Die Compose-Screens werden im CI-Job `android`
  kompiliert und mit Unit-Tests geprüft, nicht hier.

Der Befehlssatz zwischen App, Konsole und Firmware wird separat abgesichert:
`python3 scripts/check-action-drift.py`.
