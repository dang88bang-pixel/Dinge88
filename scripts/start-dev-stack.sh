#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – lokalen Dev-Stack starten
#   - Docker-Compose: MQTT (Mosquitto), Backend, Node-RED
#   - falls Docker fehlt: nur Backend via venv/uvicorn
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "==> Starte Docker-Stack (MQTT + Backend + Node-RED)"
  docker compose up --build -d
  echo "   MQTT:      mqtt://localhost:1883"
  echo "   Backend:   http://localhost:8000"
  echo "   Node-RED:  http://localhost:1880"
else
  BACKEND_VENV="${BACKEND_VENV_DIR:-$HOME/.secureguard-backend-venv}"
  if [[ -x "$BACKEND_VENV/bin/uvicorn" ]]; then
    echo "==> Docker nicht verfügbar – starte Backend direkt"
    (cd backend && "$BACKEND_VENV/bin/uvicorn" main:app --host 0.0.0.0 --port 8000 --reload)
  else
    echo "❌ Weder Docker Compose noch Backend-venv verfügbar." >&2
    echo "   Docker: docker compose up --build  |  Backend: ./scripts/setup-backend.sh" >&2
    exit 1
  fi
fi
