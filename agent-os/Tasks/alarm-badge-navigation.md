---
title: Alarm-Badge in der Navigation und einheitliche Alarm-Quittierung
area: android
priority: P2
status: n
created: 2026-09-04
verification: "./gradlew :app:testDebugUnitTest grün + Screenshot mit Badge"
refs:
  - agent-os/Knowledge/architektur.md
---

# Alarm-Badge und einheitliche Quittierung

## Kontext

Ziel **G1** und **G3**.

`SecureGuardRepository.getUnacknowledgedAlertCount()` existiert bereits, wird
aber nirgends in der Navigation ausgewertet. Wer nicht zufällig auf dem
Dashboard steht, sieht offene Alarme nicht.

Gleichzeitig gibt es Quittierung inzwischen an drei Stellen (Dashboard,
Alarm-Screen, 3D-Konsole). Die Logik gehört an genau eine Stelle.

## Umsetzung

- [ ] `SecureGuardApp.kt`: Zähler als `StateFlow` beobachten und in den
      Bottom-Nav-Eintrag als `BadgedBox` einhängen (Anzeige „9+" ab 10)
- [ ] Quittierlogik in einen `AlertsInteractor` (oder Repository-Methode)
      ziehen; Dashboard, Alarm-Screen und `OpsCenterViewModel` rufen nur noch
      diesen Pfad auf
- [ ] Audit-Log-Eintrag pro Quittierung sicherstellen
- [ ] Unit-Test: Quittieren senkt den Zähler; „alle quittieren" setzt auf 0

## Nachweis

```bash
./gradlew :app:testDebugUnitTest --tests '*Alert*'
```

Plus Screenshot der Bottom-Navigation mit sichtbarem Badge.

## Verlauf

- 2026-09-04: Angelegt.
