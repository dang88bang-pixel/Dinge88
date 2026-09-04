#!/usr/bin/env bash
# =============================================================================
# SecureGuard – Backend-Stack starten (Docker Compose)
# =============================================================================
# Startet Mosquitto + FastAPI + Node-RED + Slack-MCP und wartet auf
# /api/health.
# Keine Passwörter im Script – MQTT-Auth siehe mosquitto/config/*.example
# (Anwender setzt User/Pass selbst).
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ fehlt: $1"; exit 1; }; }
need docker

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  echo "❌ docker compose nicht gefunden"
  exit 1
fi

echo "→ Starte SecureGuard Stack …"
"${DC[@]}" up --build -d

echo "→ Warte auf Backend-Health …"
for i in $(seq 1 30); do
  if curl -fsS --max-time 2 http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
    echo "✔ Backend bereit"
    break
  fi
  sleep 2
  if [[ "$i" -eq 30 ]]; then
    echo "⚠ Backend noch nicht erreichbar – Logs:"
    "${DC[@]}" logs --tail=40 backend || true
    exit 1
  fi
done

echo "→ Prüfe Slack-MCP-Integration …"
if slack_health=$(curl -fsS --max-time 8 http://127.0.0.1:8000/api/slack/health 2>/dev/null); then
  case "$slack_health" in
    *'"reachable": true'*|*'"reachable":true'*)
      echo "✔ Slack-MCP erreichbar"
      ;;
    *'"configured": false'*|*'"configured":false'*)
      echo "⚠ Slack nicht konfiguriert (.env: SLACK_MCP_* – siehe docs/SLACK_MCP.md)"
      ;;
    *)
      echo "⚠ Slack-MCP nicht erreichbar (Tokens gesetzt? Demo-Modus aktiv?)"
      ;;
  esac
else
  echo "⚠ /api/slack/health nicht abrufbar"
fi

echo ""
echo "Dienste:"
echo "  Backend    http://127.0.0.1:8000/api/health"
echo "  Slack-MCP  http://127.0.0.1:8000/api/slack/health  (MCP: 127.0.0.1:13080/mcp)"
echo "  Node-RED   http://127.0.0.1:1880"
echo "  MQTT       tcp://127.0.0.1:1883"
echo ""
echo "Smoke: ./scripts/smoke-check.sh"
echo "Stop:  docker compose down"
