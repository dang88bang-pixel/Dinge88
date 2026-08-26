#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Dienste starten (ohne Docker)
# ==============================================================
# Startet: MQTT-Broker (1883/9001), FastAPI-Backend (8000),
#          Node-RED (1880), Demo-Telemetrie-Simulator
#
# Alle Prozesse laufen im Vordergrund-Panel (Strg+C beendet alle).
# ==============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RUNTIME="${SG_RUNTIME_DIR:-$HOME/.secureguard-runtime}"
VENV="$RUNTIME/venv"
NODERED="$RUNTIME/nodered"
NODE_PATH="$RUNTIME/node_modules"

[ -x "$VENV/bin/python" ] || { echo "❌ Setup fehlt – bitte zuerst: scripts/setup-all.sh"; exit 1; }
[ -d "$RUNTIME/node_modules/aedes" ] || { echo "❌ Broker-Abhängigkeiten fehlen – bitte zuerst: scripts/setup-all.sh"; exit 1; }

cleanup() {
  echo
  echo "⏹  Beende alle SecureGuard-Dienste …"
  kill 0 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "🔌 MQTT-Broker auf 0.0.0.0:1883 (+ WebSocket 9001)"
NODE_PATH="$NODE_PATH" node scripts/mqtt-broker.js &

sleep 1

echo "🖥  FastAPI-Backend auf 0.0.0.0:8000"
DATABASE_PATH="data/secureguard.db" \
MQTT_BROKER="127.0.0.1:1883" \
"$VENV/bin/uvicorn" main:app --app-dir backend --host 0.0.0.0 --port 8000 &

sleep 2

if [ -x "$NODERED/node_modules/.bin/node-red" ]; then
  echo "📊 Node-RED Dashboard auf 0.0.0.0:1880"
  FLOWS="$ROOT/nodered/flows.json" node "$NODERED/node_modules/.bin/node-red" \
    -u "$RUNTIME/nodered-data" -s "$ROOT/nodered/settings.js" &
else
  echo "⚠  Node-RED nicht installiert (node-red Paket fehlt) – übersprungen."
fi

echo "📡 Demo-Telemetrie-Simulator"
NODE_PATH="$NODE_PATH" node scripts/demo-publisher.js 15 &

echo
echo "=============================================================="
echo " Alle Dienste laufen:"
echo "   MQTT    : mqtt://127.0.0.1:1883"
echo "   Backend : http://127.0.0.1:8000  (Doku: /docs, Health: /api/health)"
echo "   NodeRED : http://127.0.0.1:1880/ui"
echo " Strg+C beendet alles."
echo "=============================================================="
wait
