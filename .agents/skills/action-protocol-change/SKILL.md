---
name: action-protocol-change
description: Pflichtablauf für neue oder geänderte Gerätebefehle. Hält Android-Katalog, 3D-Konsole und ESP32-Firmware synchron.
---

# Gerätebefehl ändern

Der teuerste Fehler dieses Produkts: Die Oberfläche bietet einen Befehl an,
den das Gerät nicht kennt. Der Nutzer merkt es im Ernstfall.

Grundlage: `agent-os/Knowledge/aktionsprotokoll.md`.

## Reihenfolge (nicht abkürzen)

### 1. Firmware zuerst

`firmware/secureguard_esp32/secureguard_esp32.ino` → `handleCommand()`.

Die Firmware definiert, was wirklich möglich ist. Ein Befehl, den kein Gerät
versteht, darf gar nicht erst in einen Katalog.

- Genau ein Literal, GROSSBUCHSTABEN, keine Leerzeichen
- Nutzlast nur nach Doppelpunkt (`MESSAGE:<text>`)
- Unbekannte Befehle protokollieren, nicht still verwerfen

### 2. `ActionType` erweitern

`app/.../presentation/ui/common/ActionType.kt`

```kotlin
NEUER_BEFEHL(wireCommand = "NEUER_BEFEHL", label = "Sprechender deutscher Name")
```

### 3. `ActionCatalog` erweitern

`app/.../presentation/ui/common/ActionCatalog.kt`

```kotlin
ActionSpec(
    type = ActionType.NEUER_BEFEHL,
    category = ActionCategory.…,
    risk = ActionRisk.…,          // LOW | MEDIUM | CRITICAL
    icon = Icons.Default.…,
    description = "Was passiert am Gerät, in einem Satz.",
    queueable = true              // CRITICAL immer false
)
```

### 4. Konsolenkatalog identisch erweitern

`console3d/src/data/catalog.js` — dieselbe Kennung, dasselbe Risiko, dieselbe
Kategorie, deutsche Beschriftung.

### 5. Bestätigung bei Risiko `CRITICAL`

- App: `SgConfirmDialog`
- Konsole: `#modal` über `overlays.confirm(...)`
- Nicht einreihbar: bei fehlender Verbindung ablehnen, nicht in die Queue

### 6. Berechtigung und Audit

- Prüfung `RoleManager.require(Permission.EXECUTE_ACTIONS)` vor dem Senden
- Eintrag im `AuditLogService` nach dem Senden
- Bei Ablehnung: Grund für den Nutzer sichtbar machen

### 7. Bundle spiegeln

```bash
bash scripts/sync-console3d.sh
```

Sonst zeigt die App weiterhin den alten Katalog.

## Prüfliste vor der Fertigmeldung

- [ ] Firmware kennt den Befehl
- [ ] `ActionType` ergänzt
- [ ] `ActionCatalog` ergänzt
- [ ] `catalog.js` ergänzt, Kennungen identisch
- [ ] Risiko in beiden Katalogen gleich
- [ ] Bestätigungsdialog bei `CRITICAL` in App **und** Konsole
- [ ] Offline-Verhalten festgelegt und umgesetzt
- [ ] Berechtigungsprüfung vorhanden
- [ ] Audit-Log-Eintrag vorhanden
- [ ] `scripts/sync-console3d.sh` gelaufen
- [ ] `agent-os/Knowledge/aktionsprotokoll.md` aktualisiert

## Nachweis

```bash
grep -n "wireCommand" app/src/main/java/com/secureguard/enterprise/presentation/ui/common/ActionType.kt
grep -n "wire:"      console3d/src/data/catalog.js
grep -n "NEUER_BEFEHL" firmware/secureguard_esp32/secureguard_esp32.ino
cd console3d && npm run build
./gradlew :app:assembleDebug
```

Alle drei Listen müssen dieselben Kennungen enthalten — abzüglich der reinen
Szenen-Aktionen `SWEEP`, `FOCUS`, `GEOFENCE`, `HEATMAP`.
