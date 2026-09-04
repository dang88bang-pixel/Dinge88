#!/usr/bin/env bash
# =============================================================================
# SecureGuard – Smoke-Check aller lokalen Dienste
# =============================================================================
# Prüft Backend-Health, MQTT-Port, Node-RED (optional), die
# Slack-MCP-Integration (/api/slack/health) und die Abhängigkeits-Inventur
# (/api/system/dependencies), die das App-Einstellungsmenü anzeigt.
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

  # Slack-MCP: Konfiguration + Erreichbarkeit des MCP-Servers
  if body=$(curl -fsS --max-time 10 "$BACKEND_URL/api/slack/health" 2>/dev/null); then
    case "$body" in
      *'"configured": false'*|*'"configured":false'*)
        echo "– Slack nicht konfiguriert (übersprungen)"
        ;;
      *'"reachable": true'*|*'"reachable":true'*)
        echo "✔ Slack-MCP erreichbar"
        echo "  $body" | head -c 300
        echo
        ok=$((ok + 1))
        ;;
      *)
        echo "✖ Slack-MCP nicht erreichbar"
        echo "  $body" | head -c 300
        echo
        fail=$((fail + 1))
        ;;
    esac
  else
    echo "✖ Backend /api/slack/health"
    fail=$((fail + 1))
  fi
  # Abhängigkeits-Inventur (Einstellungen → „🧩 Anbindungen & Abhängigkeiten").
  # probe=false: keine Netzwerk-Checks im Backend, nur Konfigurationsstand.
  if body=$(curl -fsS --max-time 10 "$BACKEND_URL/api/system/dependencies?probe=false" 2>/dev/null); then
    ids=$(printf '%s' "$body" | grep -o '"id": *"[a-z-]*"' | grep -o '[a-z-]*"$' | tr -d '"' | tr '\n' ' ')
    count=$(printf '%s' "$body" | grep -o '"count": *[0-9]*' | grep -o '[0-9]*$')
    if [[ -n "$count" && "$count" -ge 1 ]]; then
      echo "✔ Abhängigkeiten: $count Einträge ($ids)"
      ok=$((ok + 1))
    else
      echo "✖ Abhängigkeiten: unerwartete Antwort"
      echo "  $(printf '%s' "$body" | head -c 200)"
      fail=$((fail + 1))
    fi
  else
    echo "✖ Backend /api/system/dependencies"
    fail=$((fail + 1))
  fi
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
