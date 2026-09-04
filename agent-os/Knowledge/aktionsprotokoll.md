# Knowledge: Aktions- und Kommandoprotokoll

Der teuerste Fehlerfall dieses Produkts ist ein Befehl, den die Oberfläche
anbietet und das Gerät nicht kennt. Dieses Dokument hält die drei Kataloge
zusammen.

---

## Wire-Befehle (Vertrag mit der Firmware)

| Wire | Wirkung am Gerät | Risiko | Offline einreihbar |
|------|------------------|--------|--------------------|
| `ALARM` | Akustischer Alarm | niedrig | ja |
| `LIGHT` | Beleuchtung/Blinken | niedrig | ja |
| `MOTOR_OFF` | Antrieb sperren | **kritisch** | nein |
| `RESTART` | Gerät neu starten | **kritisch** | nein |
| `CONFIG` | Konfiguration anfordern/setzen | mittel | ja |
| `MESSAGE:<text>` | Text auf Gerät/Display | niedrig | ja |

`MESSAGE` ist der einzige Befehl mit Nutzlast. Die App kürzt den Text auf
120 Zeichen (`wire + ":" + note.take(120)`).

Gegenstelle: `firmware/secureguard_esp32/secureguard_esp32.ino`.
Unbekannte Befehle werden vom Gerät **stillschweigend verworfen** — das sieht
wie ein Netzwerkfehler aus, ist aber Katalog-Drift.

## Die drei Kataloge

| Ebene | Datei | Enthält |
|-------|-------|---------|
| Android | `presentation/ui/common/ActionCatalog.kt` | `ActionSpec` mit Kategorie, Risiko, Icon, Berechtigung |
| Android | `presentation/ui/common/ActionType.kt` | `ActionType` mit `wireCommand`, `label` |
| Konsole | `console3d/src/data/catalog.js` | `ACTIONS`, `ACTION_MAP`, `RISK_LABEL` |
| Firmware | `firmware/secureguard_esp32/*.ino` | `handleCommand()` |

**Regel:** Ein neuer Befehl wird in allen drei Ebenen gleichzeitig ergänzt.
Ein Pull-Request, der nur eine Ebene ändert, wird abgelehnt.

## Reine Szenen-Aktionen (nur Konsole)

Diese vier existieren ausschließlich in der 3D-Konsole und erreichen nie ein
Gerät. Sie brauchen kein Firmware-Gegenstück:

| Aktion | Taste | Wirkung |
|--------|-------|---------|
| `SWEEP` | `r` | Radar-Sweep über die Szene |
| `FOCUS` | `f` | Kamera auf Auswahl |
| `GEOFENCE` | `g` | Geofence-Ringe einblenden |
| `HEATMAP` | `m` | Detektionsdichte als Heatmap |

## Zustellweg

```
UI → ViewModel → AgentService.sendAction(asset, command)
   1. MQTT        (MqttService)
   2. WebSocket   (WebSocketService)
   3. BLE/GATT    (BleService)
   sonst → OfflineQueue (Room, überlebt Neustart)
```

Vor dem Senden: `RoleManager.require(Permission.EXECUTE_ACTIONS)`.
Fehlt die Berechtigung, wird **abgelehnt**, nicht eingereiht.
Nach dem Senden: Eintrag im `AuditLogService`.

## Brückenvertrag App ⇄ 3D-Konsole

Die Konsole läuft in der App in einer WebView. Kotlin registriert das Objekt
`SecureGuardNative`.

**Von JS nach Kotlin:**

```js
window.SecureGuardNative.snapshot()                 // -> JSON-String
window.SecureGuardNative.toggleAgent()
window.SecureGuardNative.acknowledgeAlert(id)       // id als String
window.SecureGuardNative.execute(requestId, assetId, wireCommand, note)
window.SecureGuardNative.flushQueue(requestId)
```

**Von Kotlin nach JS:**

```js
window.SecureGuardBridgeResolve(requestId, state, detail)
// state: 'delivered' | 'queued' | 'blocked' | 'denied'
// bei flushQueue ist detail die Anzahl gesendeter Befehle als String
```

Snapshot-Form:

```json
{
  "source": "native", "queue": 0, "role": "OPERATOR", "canExecute": true,
  "agent": { "running": true, "cycle": 42, "startedAt": 0, "intervalSec": 30, "lastRunAt": 0 },
  "assets":     [{ "id":"", "name":"", "shortName":"", "kind":"asset", "mac":"", "status":"", "rssi":0, "battery":0, "lat":0, "lon":0, "lastSeen":0, "maintenanceDue":false }],
  "alerts":     [{ "id":"", "assetId":"", "assetName":"", "type":"", "severity":"", "message":"", "acknowledged":false, "ts":0 }],
  "detections": [{ "id":"", "assetId":"", "assetName":"", "source":"", "rssi":0, "lat":0, "lon":0, "ts":0 }]
}
```

Zeitstempel sind Epoch-Millisekunden, fehlende Werte `null`.
Antwortet die App nicht innerhalb von 15 s (12 s bei `flushQueue`), nimmt die
Konsole `{state:'blocked'}` an und meldet das sichtbar.

## Änderungscheckliste

- [ ] Firmware `handleCommand()` erweitert
- [ ] `ActionType.wireCommand` ergänzt
- [ ] `ActionCatalog` um `ActionSpec` mit Kategorie/Risiko/Icon erweitert
- [ ] `console3d/src/data/catalog.js` identisch ergänzt
- [ ] Bei Risiko „kritisch": Bestätigungsdialog in App **und** Konsole
- [ ] Offline-Verhalten festgelegt (einreihbar ja/nein)
- [ ] Audit-Log-Eintrag vorhanden
- [ ] Berechtigung gesetzt
- [ ] `bash scripts/sync-console3d.sh` ausgeführt
