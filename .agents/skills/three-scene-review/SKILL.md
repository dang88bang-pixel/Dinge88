---
name: three-scene-review
description: Prüft Änderungen an der Three.js-Szene des 3D Operations Center auf Korrektheit, Leistung und Speicherverhalten.
---

# Review der Three.js-Szene

Anwenden bei jeder Änderung in `console3d/src/scene/` oder an der Renderschleife.

## 1. Ressourcen freigeben

Three.js gibt GPU-Speicher nicht automatisch frei. Jedes entfernte Objekt
braucht Aufräumarbeit:

```js
scene.remove(mesh)
mesh.geometry.dispose()
mesh.material.dispose()          // bei Arrays: jedes Material einzeln
texture.dispose()
```

- [ ] Werden Knoten entfernt, wenn ein Asset verschwindet?
- [ ] Werden CSS2D-Labels aus dem DOM entfernt?
- [ ] Wird bei Größenänderung nichts neu erzeugt, was wiederverwendbar wäre?

Test: Datenquelle mehrfach mit `D` durchschalten und prüfen, dass die Zahl der
Kinder in `scene.children` nicht monoton wächst.

## 2. Renderschleife

- [ ] Keine Objekterzeugung pro Frame (`new Vector3()` in `animate()` ist ein
      Fehler — Instanzen außerhalb anlegen und wiederverwenden)
- [ ] `requestAnimationFrame` wird bei `document.hidden` pausiert
- [ ] `renderer.setPixelRatio(Math.min(devicePixelRatio, 2))`
- [ ] Keine DOM-Lesevorgänge (`getBoundingClientRect`) pro Frame

## 3. Skalierung

Die Szene muss mit 12 und mit 200 Assets funktionieren:

- [ ] Gleichartige Knoten über `InstancedMesh` oder geteilte Geometrie
- [ ] Materialien werden geteilt, nicht je Asset neu erzeugt
- [ ] CSS2D-Labels sind der Flaschenhals — ab etwa 40 Assets nur noch für
      sichtbare oder ausgewählte Knoten zeichnen

## 4. Postprocessing

`UnrealBloomPass` ist teuer. Bei schwacher Hardware zuerst abschalten,
nicht die Auflösung senken.

- [ ] Composer-Größe folgt der Fenstergröße
- [ ] Bloom-Schwellwert so gesetzt, dass Text lesbar bleibt

## 5. Koordinaten

- [ ] Umrechnung läuft ausschließlich über `createProjector(origin)` aus
      `src/core/geo.js` (`METERS_PER_UNIT = 42`)
- [ ] Keine Vermischung von Weltkoordinaten und Szenenkoordinaten
- [ ] Fehlende `lat`/`lon` führen nicht zu `NaN` in der Position — ein
      `NaN` bringt die gesamte Szene zum Verschwinden

## 6. Robustheit

- [ ] Kein WebGL-Kontext verfügbar → verständlicher Hinweis statt weißer Seite
- [ ] Kontextverlust (`webglcontextlost`) wird behandelt
- [ ] Die Szene funktioniert im Simulationsmodus ohne jedes Backend

## 7. Nachweis

```bash
cd console3d && npm run build     # muss grün sein
```

Danach manuell im Browser:

- [ ] Szene erscheint, Kamera lässt sich drehen und zoomen
- [ ] Asset auswählen hebt Knoten und Label hervor
- [ ] Aktion auslösen erzeugt sichtbare Rückmeldung
- [ ] Fenster verkleinern und vergrößern verzerrt nichts
- [ ] Konsole zeigt keine Fehler

Ohne Browser: ausdrücklich als ungeprüft kennzeichnen.

## Bekannte Stolpersteine

- Farbliterale sechsstellig schreiben (`0x1d3a5c`) — gekürzte Werte scheitern still.
- Zeilen, die mit `(` oder `[` beginnen, brauchen ein führendes Semikolon.
- Panels müssen `scrollTop` über das Neuzeichnen retten.
