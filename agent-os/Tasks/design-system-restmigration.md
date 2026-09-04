---
title: Verwaiste UI-Bausteine entfernen und Restscreens auf das Design-System heben
area: android
priority: P1
status: n
created: 2026-09-04
verification: "grep liefert keine Treffer für StatCard/ActionButton; ./gradlew :app:assembleDebug grün"
refs:
  - agent-os/Knowledge/design-system.md
  - agent-os/GOALS.md
---

# Verwaiste UI-Bausteine entfernen, Restscreens migrieren

## Kontext

Ziel **G3 — Professionelle, konsistente Oberfläche**.

Dashboard, Aktionen-Center, Asset-Liste und Asset-Karte laufen bereits über
`presentation/designsystem/`. Dadurch sind `presentation/components/StatCard.kt`
und `presentation/components/ActionButton.kt` ohne Aufrufer, und mehrere
Screens benutzen weiterhin nackte `Card`-Aufrufe mit eigenen Farben.

Zwei Codepfade für dieselbe Sache sind die Hauptquelle für optische Drift.

## Umsetzung

- [ ] Aufrufer prüfen: `grep -rn "StatCard\|ActionButton" app/src`
- [ ] Bei null Treffern beide Dateien löschen; sonst auf `SgMetricTile` bzw.
      `SgQuickTile` umstellen und dann löschen
- [ ] Screens in dieser Reihenfolge migrieren (Nutzungshäufigkeit):
      Karte → Sensor-Fusion → Node-Status → Health → Terminal → Temp-Mail →
      Einstellungen
- [ ] Je Screen: `SgCard`, `SgSectionHeader`, `SgEmptyState`,
      Statusfarben aus `Tokens.kt`
- [ ] Prüfen, dass in `presentation/ui/**` keine `Color(0x…)`-Literale bleiben
- [ ] `Theme.kt`: toten `dynamicColor`-Zweig entfernen

## Nachweis

```bash
grep -rn "Color(0x" app/src/main/java/com/secureguard/enterprise/presentation/ui | wc -l   # erwartet: 0
grep -rn "StatCard\|ActionButton" app/src | wc -l                                          # erwartet: 0
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Zusätzlich: Screenshot je migriertem Screen im PR.

## Verlauf

- 2026-09-04: Angelegt. Dashboard/Aktionen/Assets sind bereits migriert.
