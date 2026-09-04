---
name: systematic-debugging
description: Findet die Ursache eines Fehlers, statt Symptome zu überschreiben. Anwenden, sobald die Ursache nicht offensichtlich ist.
---

# Systematisches Debugging

Anwenden, sobald der erste naheliegende Fix nicht gewirkt hat — spätestens
dann. Nicht weiterraten.

## Die Regel

```
EINE Hypothese. EINE Änderung. EINE Messung.
```

Zwei gleichzeitige Änderungen machen das Ergebnis unbrauchbar, egal wie es
ausgeht.

## Ablauf

### 1. Reproduzieren

Ohne verlässliche Reproduktion wird nichts geändert. Notieren:

- Genaue Schritte
- Erwartetes Verhalten
- Tatsächliches Verhalten
- Häufigkeit (immer / sporadisch / einmalig)

Für SecureGuard ohne Hardware: 3D-Konsole im Simulationsmodus
(`cd console3d && npm run dev`) erzeugt Assets, Detektionen und Alarme.

### 2. Eingrenzen

Die Kette lautet:

```
Tag → Gateway → MQTT/WS → Backend → Repository → ViewModel → UI
```

Halbierendes Suchen: in der Mitte messen, Hälfte ausschließen, wiederholen.
Vier Messungen reichen für sieben Stufen.

| Stufe | Messpunkt |
|-------|-----------|
| UI | Zeigt der State-Flow den Wert? |
| ViewModel | Emittiert der Flow überhaupt? |
| Repository | Liefert die DAO-Abfrage Zeilen? |
| Backend | `GET /api/detections?limit=20` |
| Broker | `docker compose logs mosquitto --tail 50` |
| Gateway | serielle Konsole |

### 3. Hypothese formulieren

Schriftlich, falsifizierbar:

> „Der Wert fehlt in der UI, weil der Flow nach dem Rollenwechsel nicht neu
> abonniert wird. Wenn das stimmt, erscheint der Wert nach einem
> Konfigurationswechsel."

Keine vagen Vermutungen („irgendwas mit Timing").

### 4. Prüfen, nicht reparieren

Erst messen, ob die Hypothese stimmt. Logausgabe, Breakpoint, Testfall.
Wenn sie falsch ist: nächste Hypothese, nicht trotzdem ändern.

### 5. Reparieren

Ursache beheben, nicht Symptom kaschieren. Warnzeichen für Symptomkosmetik:

- `try/catch` ohne Behandlung, nur damit es nicht mehr knallt
- `?: emptyList()` auf einem Wert, der nie leer sein dürfte
- Wartezeit, damit ein Rennen seltener auftritt
- `!!` gegen eine `NullPointerException`

### 6. Absichern

Test schreiben, der den Fehler ohne den Fix reproduziert. Ohne diesen Test
kommt der Fehler zurück.

### 7. Festhalten

War die Ursache nicht offensichtlich: nach `agent-os/Knowledge/` schreiben.
Fehlte eine Prüfung, die es hätte auffangen müssen: Aufgabe dafür anlegen.

## Wann nicht anwenden

Bei Tippfehlern und offensichtlichen Compilerfehlern — dort ist der direkte
Fix richtig.
