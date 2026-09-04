---
title: 3D Operations Center auf Zielhardware profilen und Leistungsbudget durchsetzen
area: console3d
priority: P1
status: n
created: 2026-09-04
verification: "Messprotokoll mit FPS und Bundle-Größe in agent-os/Evals/"
refs:
  - agent-os/Knowledge/3d-konsole.md
---

# Leistungsbudget der 3D-Konsole

## Kontext

Ziel **G2 — Lagebild statt Listen**.

Die Konsole rendert Boden, Gitter, Ringe, Sternenfeld, Agent-Kern, zwölf
Kanal-Pylonen und pro Asset einen Knoten plus CSS2D-Label — dazu ein
`UnrealBloomPass`. Auf Desktop unkritisch, in einem Android-WebView auf
Mittelklasse-Hardware nicht automatisch.

Bisher ist nur der Build gemessen (633 kB / 165 kB gzip), nicht die Laufzeit.

## Umsetzung

- [ ] FPS-Zähler hinter Debug-Flag in `src/scene/world.js` (Mittel über 120 Frames)
- [ ] Messen: Desktop-Chrome, Android-WebView Mittelklasse, Android-WebView Oberklasse
- [ ] Adaptive Qualität: unter 45 fps über 3 s → `UnrealBloomPass` aus,
      Sternenfeld halbieren, Pixelverhältnis auf 1 begrenzen
- [ ] `renderer.setPixelRatio(Math.min(devicePixelRatio, 2))` prüfen/setzen
- [ ] Bei verdeckter Seite (`document.hidden`) Renderloop pausieren —
      spart Akku, wenn die WebView im Hintergrund liegt
- [ ] Labels ab > 40 Assets nur für sichtbare/ausgewählte Knoten zeichnen
- [ ] Ergebnis als Messprotokoll in `agent-os/Evals/` ablegen

## Nachweis

Tabelle Gerät × Szenario × FPS (Leerlauf / 12 Assets / 60 Assets), jeweils vor
und nach der Optimierung. Bundle-Größe gzip ≤ 200 kB.

## Verlauf

- 2026-09-04: Angelegt. Im Sandbox-Umfeld kein Browser verfügbar, deshalb
  bisher nur Build-Verifikation.
