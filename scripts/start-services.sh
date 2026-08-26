#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Dienste starten (ohne Docker)
# ==============================================================
# Startet: MQTT-Broker (1883/9001, Authentifizierung aktiv),
#          FastAPI-Backend (8000), Node-RED (1880, Login geschützt)
#
# Vorher: scripts/setup-all.sh (installiert Abhängigkeiten + Credentials)
# Alle Prozesse laufen im Vordergrund-Panel (Strg+C beendet alle).
# ==============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RUNTIME="${SG_RUNTIME_DIR:-$HOME/.secureguard-runtime}"
VENV="$RUNTIME/venv"
NODERED="$RUNTIME/nodered"
NODE_PATH="$RUNTIME/node_modules"
CREDS="$RUNTIME/credentials.env"

[ -x "$VENV/bin/python" ] || { echo "❌ Setup fehlt – bitte zuerst: scripts/setup-all.sh"; exit 1; }
[ -f "$CREDS" ] || { echo "❌ Zugangsdaten fehlen – bitte zuerst: scripts/setup-all.sh"; exit 1; }

# Zugangsdaten in die Umgebung laden (MQTT + Node-RED)
set -a
# shellcheck disable=SC1090
source "$CREDS"
set +a

cleanup() {
  echo
  echo "⏹  Beende alle SecureGuard-Dienste …"
  kill 0 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "🔌 MQTT-Broker auf 0.0.0.0:1883 (+ WebSocket 9001) – Auth aktiv"
NODE_PATH="$NODE_PATH" node scripts/mqtt-broker.js &

sleep 1

echo "🖥  FastAPI-Backend auf 0.0.0.0:8000"
DATABASE_PATH="data/secureguard.db" \
MQTT_BROKER="127.0.0.1:1883" \
MQTT_USERNAME="$SG_MQTT_USERNAME" \
MQTT_PASSWORD="$SG_MQTT_PASSWORD" \
"$VENV/bin/uvicorn" main:app --app-dir backend --host 0.0.0.0 --port 8000 &

sleep 2

if [ -x "$NODERED/node_modules/.bin/node-red" ]; then
  echo "📊 Node-RED Dashboard auf 0.0.0.0:1880 (Login: $NODE_RED_ADMIN_USER)"
  # Flow mit aktuellen Broker-Zugangsdaten erzeugen (Platzhalter ersetzen)
  FLOWS_TMP="$RUNTIME/flows.json"
  sed -e "s|__SG_MQTT_USERNAME__|$SG_MQTT_USERNAME|g" \
      -e "s|__SG_MQTT_PASSWORD__|$SG_MQTT_PASSWORD|g" \
      "$ROOT/nodered/flows.json" > "$FLOWS_TMP"
  # MQTT-Broker-Credentials gehören bei Node-RED in die credentials-Datei
  # (Key "user" – siehe @node-red/nodes/core/network/10-mqtt.js)
  mkdir -p "$RUNTIME/nodered-data"
  printf '{\n  "sg-broker": {\n    "user": "%s",\n    "password": "%s"\n  }\n}\n' \
    "$SG_MQTT_USERNAME" "$SG_MQTT_PASSWORD" > "$RUNTIME/nodered-data/flows_cred.json"
  FLOWS="$FLOWS_TMP" node "$NODERED/node_modules/.bin/node-red" \
    -u "$RUNTIME/nodered-data" -s "$ROOT/nodered/settings.js" &
else
  echo "⚠  Node-RED nicht installiert (node-red Paket fehlt) – übersprungen."
fi

echo
echo "=============================================================="
echo " Alle Dienste laufen:"
echo "   MQTT    : mqtt://127.0.0.1:1883  (User: $SG_MQTT_USERNAME)"
echo "   Backend : http://127.0.0.1:8000  (/docs, /api/health)"
echo "   NodeRED : http://127.0.0.1:1880/ui  (User: $NODE_RED_ADMIN_USER)"
echo " Zugangsdaten: $CREDS"
echo " Strg+C beendet alles."
echo "=============================================================="
wait
