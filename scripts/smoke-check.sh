#!/usr/bin/env bash
# =============================================================================
# SecureGuard – Smoke-Check aller lokalen Dienste
# =============================================================================
# Prüft Backend-Health, MQTT-Port, Node-RED (optional).
# Passwörter werden nicht gelesen/ausgegeben.
#
# Nutzung:
#   ./scripts/smoke-check.sh
#   BACKEND_URL=http://192.168.1.10:8000 ./scripts/smoke-check.sh
# =============================================================================
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8000}"
MQTT_HOST="${MQTT_HOST:-127.0.0.1}"
MQTT_PORT="${MQTT_PORT:-1883}"
NODERED_URL="${NODERED_URL:-http://127.0.0.1:1880}"

ok=0
fail=0

check() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "✔ $name"
    ok=$((ok + 1))
  else
    echo "✖ $name"
    fail=$((fail + 1))
  fi
}

echo "=== SecureGuard Smoke-Check ==="
echo "Backend: $BACKEND_URL"
echo ""

if command -v curl >/dev/null 2>&1; then
  if body=$(curl -fsS --max-time 5 "$BACKEND_URL/api/health" 2>/dev/null); then
    echo "✔ Backend /api/health"
    echo "  $body"
    ok=$((ok + 1))
  else
    echo "✖ Backend /api/health (läuft docker compose?)"
    fail=$((fail + 1))
  fi
  check "Backend /api/assets" curl -fsS --max-time 5 "$BACKEND_URL/api/assets"
  check "Backend /api/stats" curl -fsS --max-time 5 "$BACKEND_URL/api/stats"
  check "Node-RED" curl -fsS --max-time 3 "$NODERED_URL" -o /dev/null
else
  echo "⚠ curl fehlt – HTTP-Checks übersprungen"
fi

if command -v nc >/dev/null 2>&1; then
  check "MQTT Port $MQTT_HOST:$MQTT_PORT" nc -z "$MQTT_HOST" "$MQTT_PORT"
elif command -v timeout >/dev/null 2>&1; then
  check "MQTT Port $MQTT_HOST:$MQTT_PORT" \
    bash -c "timeout 2 bash -c '</dev/tcp/$MQTT_HOST/$MQTT_PORT'"
else
  echo "⚠ kein nc – MQTT-Port nicht geprüft"
fi

echo ""
echo "Ergebnis: $ok ok · $fail fehlgeschlagen"
if [[ "$fail" -gt 0 ]]; then
  exit 1
fi
