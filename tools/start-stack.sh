#!/usr/bin/env bash
# ============================================================
# SecureGuard – kompletten Stack aktiv bereitstellen (Ein Befehl)
# ============================================================
# Startet hintereinander:
#   1. MQTT-Broker (aedes, Ports 1883 TCP + 9001 WS)
#   2. FastAPI-Backend (Port 8000, an Broker angebunden)
#   3. Gateway-Worker (Dienst-Akteur: Telemetrie + Command-Handling)
#   4. Node-RED (Port 1880, optional – braucht node-red)
#
# Voraussetzungen:
#   pip install fastapi uvicorn pydantic paho-mqtt websockets
#   npm install aedes ws   (im Verzeichnis $BROKER_DIR)
#   npm install -g node-red   (optional)
#
# Nutzung:   bash tools/start-stack.sh [--dir /home/user]
set -euo pipefail

BASE_DIR="${2:-/home/user}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BROKER_DIR="$BASE_DIR/mqtt-broker"
DB_PATH="${DB_PATH:-/tmp/secureguard-live.db}"

echo "▶ [1/4] MQTT-Broker vorbereiten …"
mkdir -p "$BROKER_DIR"
cd "$BROKER_DIR"
[ -f broker.js ] || cp "$REPO_DIR/tools/broker.js" broker.js 2>/dev/null || true
[ -d node_modules/aedes ] || npm install --silent --no-audit --no-fund aedes ws
if (echo > /dev/tcp/127.0.0.1/1883) 2>/dev/null; then
  echo "   Broker läuft bereits."
else
  echo "   Broker starten (nohup) …"
  nohup node broker.js > "$BROKER_DIR/broker.log" 2>&1 &
  sleep 2
fi

echo "▶ [2/4] Backend starten …"
if (echo > /dev/tcp/127.0.0.1/8000) 2>/dev/null; then
  echo "   Backend läuft bereits."
else
  cd "$REPO_DIR/backend"
  DATABASE_PATH="$DB_PATH" MQTT_BROKER=127.0.0.1:1883 \
    nohup python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 \
    > "$BASE_DIR/backend.log" 2>&1 &
  sleep 3
fi

echo "▶ [3/4] Gateway-Worker starten (Telemetrie/Commands) …"
if pgrep -f "gateway_worker.py" > /dev/null 2>&1; then
  echo "   Worker läuft bereits."
else
  cd "$REPO_DIR"
  nohup python3 tools/gateway_worker.py \
    --broker 127.0.0.1:1883 --api http://localhost:8000 \
    --mac AA:BB:CC:00:11:22 --interval 10 --heartbeat 30 \
    > "$BASE_DIR/gateway-worker.log" 2>&1 &
  sleep 2
fi

echo "▶ [4/4] Node-RED (optional) …"
if (echo > /dev/tcp/127.0.0.1/1880) 2>/dev/null; then
  echo "   Node-RED läuft bereits."
elif command -v node-red > /dev/null 2>&1; then
  mkdir -p "$BASE_DIR/nodered-data"
  nohup node-red -s "$BASE_DIR/nodered-data/settings.js" \
    > "$BASE_DIR/nodered.log" 2>&1 || echo "   (Node-RED optional – übersprungen)"
  sleep 2
else
  echo "   node-red nicht installiert – übersprungen (optional)."
fi

echo
echo "✅ Stack-Status:"
for p in 1883 9001 8000 1880; do
  if (echo > /dev/tcp/127.0.0.1/$p) 2>/dev/null; then echo "   :$p aktiv"; fi
done
command -v curl > /dev/null && curl -s http://localhost:8000/api/health && echo
echo "   Verifikation: python3 tools/verify_network.py"
