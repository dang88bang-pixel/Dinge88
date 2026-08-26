#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Komplett-Setup (ohne Docker)
# ==============================================================
# Richtet ALLE Abhängigkeiten + Dienste lokal ein:
#   1. MQTT-Broker  (aedes)          → TCP 1883 + WebSocket 9001
#   2. Backend      (FastAPI/Uvicorn)→ HTTP 8000
#   3. Node-RED     (Dashboard)      → HTTP 1880
#   4. SQLite-Datenbank              → data/secureguard.db (Demo-Seed)
#   5. Demo-Telemetrie-Simulator    → publiziert live auf MQTT
#
# Voraussetzungen: Python 3.10+, Node.js 18+, npm
# ==============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RUNTIME="${SG_RUNTIME_DIR:-$HOME/.secureguard-runtime}"
VENV="$RUNTIME/venv"
NODE_MODULES="$RUNTIME/node_modules"
NODERED="$RUNTIME/nodered"

echo "=============================================================="
echo " SecureGuard Enterprise – Setup"
echo " Runtime-Verzeichnis: $RUNTIME"
echo "=============================================================="

# ---------- 1. Python-Backend ----------
echo
echo "[1/5] Python-Backend (FastAPI) …"
python3 -m venv "$VENV"
"$VENV/bin/pip" install --quiet --upgrade pip
"$VENV/bin/pip" install --quiet -r backend/requirements.txt
echo "      ✓ Backend-Abhängigkeiten installiert"

# ---------- 2. MQTT-Broker ----------
echo
echo "[2/5] MQTT-Broker (aedes) …"
mkdir -p "$RUNTIME/broker"
if [ -d "$NODE_MODULES" ] && [ -d "$NODE_MODULES/aedes" ] && [ -d "$NODE_MODULES/websocket-stream" ]; then
  echo "      ✓ vorhanden"
else
  npm install --prefix "$RUNTIME" --no-audit --no-fund aedes websocket-stream mqtt >/dev/null
  echo "      ✓ installiert (aedes, websocket-stream, mqtt)"
fi

# ---------- 3. Node-RED ----------
echo
echo "[3/5] Node-RED + Dashboard …"
if [ -d "$NODERED/node_modules/node-red" ] && [ -d "$NODERED/node_modules/node-red-dashboard" ]; then
  echo "      ✓ vorhanden"
else
  mkdir -p "$NODERED"
  ( cd "$NODERED" && npm init -y >/dev/null 2>&1 && \
    npm install --no-audit --no-fund node-red@3.1.9 node-red-dashboard@3.6.6 >/dev/null )
  echo "      ✓ installiert (node-red 3.1.9 + node-red-dashboard 3.6.6)"
fi

# ---------- 4. Datenbank ----------
echo
echo "[4/5] Datenbank 'data/secureguard.db' …"
DATABASE_PATH="data/secureguard.db" "$VENV/bin/python" scripts/seed_backend.py

# ---------- 5. Beispielkonfiguration ----------
echo
echo "[5/5] Beispielkonfiguration 'local.properties' …"
if [ ! -f local.properties ]; then
  cp local.properties.example local.properties
fi

cat <<'EOF'

==============================================================
 Fertig! Starte die Dienste jetzt mit:

   scripts/start-services.sh

 Dienste:
   MQTT (TCP)     : 127.0.0.1:1883   – App, ESP32, Backend
   MQTT (WebSock) : 127.0.0.1:9001   – Browser/Node-RED
   Backend API    : http://127.0.0.1:8000
   Node-RED       : http://127.0.0.1:1880  (Login ohne Passwort)
   Demo-Telemetrie: läuft automatisch mit

 Produktiv mit Docker:  docker compose up --build
==============================================================
EOF
