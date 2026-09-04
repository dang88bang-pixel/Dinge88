# Workflow: Lokal vollständig bereitstellen

Bringt den kompletten Stack von null auf lauffähig — ohne Feldhardware.
Ziel **G5 — Reproduzierbar baubar**.

Ergebnis: Backend mit realistischer Demo-Flotte, laufender Datenstrom und das
3D Operations Center, das genau diese Daten anzeigt.

---

## 1. Backend

```bash
pip3 install -r backend/requirements.txt
DATABASE_PATH=data/secureguard.db \
  python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 --app-dir backend
```

Ohne MQTT-Broker meldet das Backend beim Start
`MQTT nicht verfügbar – WebSocket-Forwarding deaktiviert` und läuft weiter.
Das ist gewollt: die REST-API funktioniert eigenständig.

Prüfen:

```bash
curl -s http://127.0.0.1:8000/api/health
# {"status":"ok","assets":0,"detections":0,"alerts":0,...}
```

## 2. Demo-Flotte einspielen

```bash
python3 scripts/seed-demo-data.py --seed 88
```

Legt 12 Assets an — identisch zur Simulation in
`console3d/src/core/simulation.js`, damit der Wechsel zwischen den
Datenquellen keinen Bruch im Lagebild erzeugt. Dazu Detektionshistorie und
vier Alarme.

Für ein lebendiges Lagebild zusätzlich in einem eigenen Terminal:

```bash
python3 scripts/seed-demo-data.py --live --interval 2
```

## 3. 3D Operations Center

```bash
cd console3d
npm ci
npm run dev        # http://0.0.0.0:5173
```

Der Dev-Server proxyt `/api` **und** `/ws` an `http://127.0.0.1:8000`.
Abweichendes Backend: `SECUREGUARD_BACKEND=http://host:port npm run dev`.

Die Konsole wählt die Quelle selbst:

| Anzeige im HUD | Bedeutung |
|----------------|-----------|
| `backend` | FastAPI antwortet — echte Daten |
| `simulation` | Backend nicht erreichbar — Rückfallebene |
| `native` | läuft in der Android-App |

Mit `D` lässt sich die Quelle manuell durchschalten — nützlich, um den
Rückfall bewusst zu prüfen.

## 4. Android-App

```bash
bash scripts/sync-console3d.sh      # Konsolen-Bundle in die App-Assets
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Die App braucht das Backend nicht: Room ist die Quelle der Wahrheit, das
eingebettete Lagebild bekommt seine Daten über die Brücke `SecureGuardNative`.

## 5. Gesamtstack mit Broker

Wenn MQTT und Node-RED gebraucht werden:

```bash
docker compose up -d
docker compose ps
docker compose logs -f backend
```

## 6. Aufräumen

```bash
docker compose down
rm -f data/secureguard.db          # Demo-Daten verwerfen
```

## Prüfliste „läuft vollständig"

- [ ] `curl /api/health` liefert `status: ok`
- [ ] `assets` ≥ 12, `detections` steigt bei laufendem `--live`
- [ ] Konsole im Browser zeigt im HUD die Quelle `backend`
- [ ] Assets erscheinen im Lagebild, Auswahl hebt Knoten und Label hervor
- [ ] Aktion aus dem Dock erzeugt eine Rückmeldung
- [ ] `scripts/sync-console3d.sh` erzeugt keinen Diff mehr (Bundle ist aktuell)
