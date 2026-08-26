#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Komplett-Setup (ohne Docker)
# ==============================================================
# Richtet ALLE Abhängigkeiten + Dienste lokal ein:
#   1. MQTT-Broker  (aedes)          → TCP 1883 + WebSocket 9001
#   2. Backend      (FastAPI/Uvicorn)→ HTTP 8000
#   3. Node-RED     (Dashboard)      → HTTP 1880 (Login geschützt)
#   4. SQLite-Datenbank              → data/secureguard.db (nur Schema)
#   5. Zugangsdaten (MQTT + Dashboard) → ~/.secureguard-runtime/credentials.env
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
CREDS="$RUNTIME/credentials.env"

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
if [ -d "$NODE_MODULES/aedes" ] && [ -d "$NODE_MODULES/websocket-stream" ]; then
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

# ---------- 4. Datenbank (nur Schema, keine Beispieldaten) ----------
echo
echo "[4/5] Datenbank 'data/secureguard.db' …"
DATABASE_PATH="data/secureguard.db" "$VENV/bin/python" scripts/db-init.py

# ---------- 5. Zugangsdaten generieren ----------
echo
echo "[5/5] Zugangsdaten (MQTT-Broker + Node-RED) …"
random_pw() {
  python3 -c "import secrets,string;print(''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(${1:-24})))"
}

if [ ! -f "$CREDS" ]; then
  MQTT_PASS="$(random_pw 24)"
  ADMIN_PASS="$(random_pw 16)"
  ADMIN_HASH="$({
    cd "$NODERED" && node -e "console.log(require('bcryptjs').hashSync('$ADMIN_PASS', 10))"
  })"
  {
    echo "# Von scripts/setup-all.sh generiert – NICHT einchecken!"
    echo "SG_MQTT_USERNAME=secureguard"
    echo "SG_MQTT_PASSWORD=$MQTT_PASS"
    echo "NODE_RED_ADMIN_USER=admin"
    echo "NODE_RED_ADMIN_PASSWORD=$ADMIN_PASS"
    echo "# Hash in einfachen Anführungszeichen, damit source() nichts expandiert"
    printf "NODE_RED_ADMIN_PASS_HASH='%s'\n" "$ADMIN_HASH"
  } > "$CREDS"
  chmod 600 "$CREDS"
  echo "      ✓ generiert: $CREDS (MQTT + Dashboard-Login)"
  echo
  echo "  ┌────────────────────────────────────────────────────┐"
  echo "  │  MQTT   Benutzer: secureguard                     │"
  echo "  │  MQTT   Passwort: $MQTT_PASS"
  echo "  │  NodeRED Benutzer: admin                          │"
  echo "  │  NodeRED Passwort: $ADMIN_PASS"
  echo "  └────────────────────────────────────────────────────┘"
else
  echo "      ✓ vorhanden: $CREDS"
fi

# ---------- 6. Beispielkonfiguration ----------
if [ ! -f local.properties ]; then
  cp local.properties.example local.properties
  cat >> local.properties <<EOF

# Vom Setup generierte MQTT-Zugangsdaten (für den lokalen Broker)
MQTT_USERNAME=secureguard
MQTT_PASSWORD=$(grep '^SG_MQTT_PASSWORD=' "$CREDS" | cut -d= -f2)
EOF
  echo "      ✓ local.properties erzeugt (inkl. MQTT-Zugangsdaten)"
fi

cat <<'EOF'

==============================================================
 Fertig! Starte die Dienste jetzt mit:

   scripts/start-services.sh

 Dienste:
   MQTT (TCP)     : 127.0.0.1:1883   (KEINE Nutzungseinschränkungen –
                                      Zugangsdaten erzeugt die App selbst)
   MQTT (WebSock) : 127.0.0.1:9001
   Backend API    : http://127.0.0.1:8000
   Node-RED       : http://127.0.0.1:1880  (Login erforderlich)

 App → Einstellungen → „Anbindungen“ → „🔑 Zugangsdaten in App erzeugen“
 (Benutzer + 24-Zeichen-Passwort werden direkt auf dem Gerät erzeugt)

 Produktiv mit Docker:  docker compose up --build
==============================================================
EOF
