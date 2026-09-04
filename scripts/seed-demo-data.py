#!/usr/bin/env python3
"""Befüllt das SecureGuard-Backend mit einer realistischen Demo-Flotte.

Zweck: Das 3D Operations Center und die App lassen sich ohne Feldhardware
vollständig bewerten. Die Flotte entspricht 1:1 der Simulation in
``console3d/src/core/simulation.js``, damit der Wechsel zwischen den
Datenquellen ``backend`` und ``simulation`` keinen Bruch im Lagebild erzeugt.

Verwendung:

    python3 scripts/seed-demo-data.py                 # einmalig befüllen
    python3 scripts/seed-demo-data.py --live          # danach laufend Detektionen senden
    python3 scripts/seed-demo-data.py --url http://host:8000 --api-key GEHEIM

Ohne Zusatzpakete – nur Standardbibliothek.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

# Referenzpunkt: Duisburg Innenhafen (identisch zu ORIGIN in simulation.js)
ORIGIN_LAT = 51.4344
ORIGIN_LON = 6.7623

FLEET = [
    ("SCOOT-01", "E-Scooter Innenhafen", "ONLINE"),
    ("SCOOT-02", "E-Scooter Hauptbahnhof", "ONLINE"),
    ("SCOOT-03", "E-Scooter Rheinpark", "ONLINE"),
    ("CARGO-01", "Lastenrad Logistik Nord", "ONLINE"),
    ("CARGO-02", "Lastenrad Werkstatt", "MAINTENANCE"),
    ("BIKE-01", "Dienstrad Verwaltung", "ONLINE"),
    ("BIKE-02", "Dienstrad Außendienst", "ONLINE"),
    ("TAG-114", "Schlüsselfinder Depot", "OFFLINE"),
    ("TAG-207", "Schlüsselfinder Leitstand", "ONLINE"),
    ("PAD-CT45", "Honeywell CT45P XON", "ONLINE"),
    ("PAD-CT45B", "Honeywell CT45P Ersatz", "SEARCHING"),
    ("NODE-ESP", "ESP32-Gateway Hafentor", "ONLINE"),
]

# Kanäle wie in console3d/src/data/catalog.js
CHANNELS = [
    "BLE", "WIFI", "LORA", "SATELLITE", "OPTICAL",
    "NFC", "USB", "CROWD", "URBAN", "MQTT", "WEBSOCKET", "GATEWAY",
]

ALERTS = [
    ("GEOFENCE", "WARNING", "{name} hat die Zone Innenhafen verlassen"),
    ("BATTERY", "INFO", "{name}: Akku unter 20 %"),
    ("MOVEMENT", "CRITICAL", "{name}: Bewegung außerhalb der Betriebszeit"),
    ("SIGNAL", "WARNING", "{name}: seit 15 Minuten kein Signal"),
]


def mac_for(index: int) -> str:
    parts = (0xB0 + index, 40 + index * 7, 11 + index * 13, index)
    return "DE:AD:" + ":".join(f"{p & 0xFF:02X}" for p in parts)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class Client:
    def __init__(self, base_url: str, api_key: str | None) -> None:
        self.base = base_url.rstrip("/")
        self.api_key = api_key

    def post(self, path: str, payload: dict) -> dict | None:
        data = json.dumps(payload).encode()
        req = urllib.request.Request(
            f"{self.base}{path}", data=data, method="POST",
            headers={"Content-Type": "application/json"},
        )
        if self.api_key:
            req.add_header("X-API-Key", self.api_key)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                body = resp.read().decode()
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as exc:
            print(f"  ! {path} -> HTTP {exc.code}: {exc.read().decode()[:160]}", file=sys.stderr)
        except urllib.error.URLError as exc:
            print(f"  ! {path} -> nicht erreichbar: {exc.reason}", file=sys.stderr)
        return None

    def get(self, path: str) -> dict | list | None:
        try:
            with urllib.request.urlopen(f"{self.base}{path}", timeout=10) as resp:
                return json.loads(resp.read().decode())
        except Exception as exc:  # noqa: BLE001 – Diagnoseausgabe reicht hier
            print(f"  ! GET {path}: {exc}", file=sys.stderr)
            return None


def seed(client: Client) -> list[dict]:
    print("==> Assets anlegen")
    assets: list[dict] = []
    for i, (short, name, status) in enumerate(FLEET):
        asset = {
            "id": f"asset-{i + 1:03d}",
            "name": name,
            "mac": mac_for(i),
            "short_name": short,
            "status": status,
            "latitude": ORIGIN_LAT + random.uniform(-0.010, 0.010),
            "longitude": ORIGIN_LON + random.uniform(-0.016, 0.016),
            "rssi": 0 if status == "OFFLINE" else random.randint(-88, -42),
            "last_seen": now_iso(),
        }
        if client.post("/api/assets", asset) is not None:
            assets.append(asset)
            print(f"    {short:10} {status:12} {asset['mac']}")

    print("==> Detektionshistorie erzeugen")
    count = 0
    for asset in assets:
        if asset["status"] == "OFFLINE":
            continue
        for _ in range(random.randint(4, 9)):
            payload = {
                "asset_mac": asset["mac"],
                "source_type": random.choice(CHANNELS),
                "node_id": f"node-{random.randint(1, 4):02d}",
                "rssi": random.randint(-92, -40),
                "latitude": asset["latitude"] + random.uniform(-0.002, 0.002),
                "longitude": asset["longitude"] + random.uniform(-0.003, 0.003),
                "timestamp": now_iso(),
            }
            if client.post("/api/detections", payload) is not None:
                count += 1
    print(f"    {count} Detektionen")

    print("==> Alarme erzeugen")
    for asset in random.sample(assets, k=min(4, len(assets))):
        kind, severity, template = random.choice(ALERTS)
        client.post("/api/alerts", {
            "asset_id": asset["id"],
            "type": kind,
            "severity": severity,
            "message": template.format(name=asset["name"]),
            "timestamp": now_iso(),
        })
        print(f"    {severity:9} {asset['short_name']}")

    return assets


def live(client: Client, assets: list[dict], interval: float) -> None:
    print(f"==> Live-Modus: alle {interval:.1f}s eine Detektion (Strg+C beendet)")
    active = [a for a in assets if a["status"] != "OFFLINE"]
    if not active:
        print("    Keine aktiven Assets – Live-Modus beendet")
        return
    phase = 0.0
    try:
        while True:
            asset = random.choice(active)
            phase += 0.15
            payload = {
                "asset_mac": asset["mac"],
                "source_type": random.choice(CHANNELS),
                "node_id": f"node-{random.randint(1, 4):02d}",
                "rssi": random.randint(-92, -40),
                # leichte Kreisbewegung, damit das Lagebild sichtbar lebt
                "latitude": asset["latitude"] + math.sin(phase) * 0.0012,
                "longitude": asset["longitude"] + math.cos(phase) * 0.0018,
                "timestamp": now_iso(),
            }
            client.post("/api/detections", payload)
            time.sleep(interval)
    except KeyboardInterrupt:
        print("\n    Live-Modus beendet")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://127.0.0.1:8000", help="Basis-URL des Backends")
    parser.add_argument("--api-key", default=None, help="X-API-Key, falls das Backend einen verlangt")
    parser.add_argument("--live", action="store_true", help="nach dem Befüllen laufend Detektionen senden")
    parser.add_argument("--interval", type=float, default=2.0, help="Sekunden zwischen Live-Detektionen")
    parser.add_argument("--seed", type=int, default=None, help="Zufallsstartwert für reproduzierbare Daten")
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    client = Client(args.url, args.api_key)

    health = client.get("/api/health")
    if health is None:
        print(f"Backend unter {args.url} nicht erreichbar.", file=sys.stderr)
        print("Starten mit: uvicorn backend.main:app --host 0.0.0.0 --port 8000", file=sys.stderr)
        return 1
    print(f"==> Backend erreichbar: {health}")

    assets = seed(client)
    stats = client.get("/api/stats")
    print(f"==> Fertig. Stats: {stats}")

    if args.live:
        live(client, assets, args.interval)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
