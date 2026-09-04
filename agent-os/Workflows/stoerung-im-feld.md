# Workflow: Störung im Feld

Ein Asset reagiert nicht, Alarme kommen nicht an, oder eine Aktion wird nicht
ausgeführt. Ziel: Ursache eingrenzen, ohne blind zu ändern.

---

## 0. Schaden begrenzen

Handelt es sich um einen laufenden Diebstahl? Dann zuerst:

1. Letzte bekannte Position aus dem Dashboard oder dem 3D-Lagebild sichern
   (Screenshot, Zeitstempel).
2. `ALARM` und `LIGHT` auslösen — beides ist unkritisch und reversibel.
3. `MOTOR_OFF` **nur** mit ausdrücklicher Freigabe des Halters und nur, wenn
   sich das Fahrzeug nicht in Bewegung befindet.
4. Erst danach Fehlersuche.

## 1. Schicht bestimmen

Die Kette lautet:

```
Tag/Gerät → Gateway (ESP32) → MQTT/WS → Backend → App → UI
```

Von hinten nach vorn prüfen, weil die Nachweise dort am billigsten sind:

| Prüfung | Befehl / Ort |
|---------|--------------|
| Sieht die App das Asset? | Asset-Liste, `lastSeen`-Zeitstempel |
| Kommt etwas im Backend an? | `GET /api/detections?limit=20` |
| Läuft der Broker? | `docker compose logs mosquitto --tail 50` |
| Sendet das Gateway? | Serielle Konsole der ESP32-Firmware |
| Ist das Tag am Leben? | Batteriewert, RSSI-Historie |

## 2. Symptome sauber trennen

- **Kein `lastSeen`-Update** → Erfassungskette (Tag/Gateway/Ingest)
- **`lastSeen` aktuell, Aktion ohne Wirkung** → Kommandopfad
- **Aktion landet in der Offline-Queue** → alle drei Kanäle nicht verfügbar
- **UI zeigt nichts, API liefert Daten** → ViewModel/Flow-Problem

## 3. Kommandopfad im Detail

`AgentService.sendAction()` probiert der Reihe nach MQTT → WebSocket →
BLE/GATT und reiht sonst in die Offline-Queue ein.

- Audit-Log der App öffnen: Wurde der Befehl überhaupt erzeugt?
- Rolle prüfen: Ohne `Permission.EXECUTE_ACTIONS` wird abgelehnt, nicht
  eingereiht.
- Wire-Befehl gegen die Firmware prüfen. Unbekannte Befehle werden vom Gerät
  stillschweigend verworfen — das sieht wie ein Netzwerkproblem aus, ist aber
  Katalog-Drift.

## 4. Reproduzieren, bevor repariert wird

Skill `systematic-debugging`. Kernregel: erst eine zuverlässige Reproduktion,
dann eine Hypothese, dann **eine** Änderung, dann erneut messen.
Nie zwei Dinge gleichzeitig ändern.

Reproduktion ohne Hardware: 3D-Konsole im Simulationsmodus starten
(`cd console3d && npm run dev`) — sie erzeugt Assets, Detektionen und Alarme
ohne Backend.

## 5. Nachbereitung

- Ursache und Fix in `agent-os/Knowledge/` festhalten, wenn sie nicht
  offensichtlich war.
- Falls das Problem unentdeckt blieb, obwohl es hätte auffallen müssen:
  Aufgabe für die fehlende Prüfung anlegen (Test, Monitoring, Warnung).
- Bei sicherheitsrelevanten Befunden zusätzlich `docs/` aktualisieren.
