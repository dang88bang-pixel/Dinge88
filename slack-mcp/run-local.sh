#!/usr/bin/env bash
# =============================================================================
# Slack-MCP-Server lokal starten (ohne Docker)
# =============================================================================
# Startet das gepinnte Release-Binary von provectus/slack-mcp-server mit dem
# Streamable-HTTP-Transport – praktisch für Entwicklung/Preview oder Rechner
# ohne Docker. Das Backend findet den Server über SLACK_MCP_URL.
#
# Nutzung:
#   ./slack-mcp/run-local.sh                    # Demo-Modus (keine Slack-Tokens)
#   SLACK_MCP_XOXB_TOKEN=xoxb-… ./slack-mcp/run-local.sh
#   SLACK_MCP_PORT=13080 SLACK_MCP_TRANSPORT=sse ./slack-mcp/run-local.sh
#
# Binary-Auflösung (erste Treffer gewinnt):
#   $SLACK_MCP_BIN → slack-mcp/bin/slack-mcp-server-linux-<arch>
#   → Download nach $XDG_CACHE_HOME/secureguard/slack-mcp (mit Checksumme)
#
# Optionen:
#   --foreground   nicht in den Hintergrund legen (Default: exec im Vordergrund)
#   --version      nur die Server-Version ausgeben
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

SLACK_MCP_VERSION="${SLACK_MCP_VERSION:-pv-v1.0.1}"
SLACK_MCP_BASE_URL="${SLACK_MCP_BASE_URL:-https://github.com/provectus/slack-mcp-server/releases/download}"
SLACK_MCP_HOST="${SLACK_MCP_HOST:-127.0.0.1}"
SLACK_MCP_PORT="${SLACK_MCP_PORT:-13080}"
SLACK_MCP_TRANSPORT="${SLACK_MCP_TRANSPORT:-http}"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/secureguard/slack-mcp"

PRINT_VERSION=0
for arg in "$@"; do
  case "$arg" in
    --version) PRINT_VERSION=1 ;;
    --help|-h) sed -n '2,20p' "$0" | sed 's/^# \?//'; exit 0 ;;
  esac
done

arch="$(uname -m)"
case "$arch" in
  x86_64|amd64) arch="amd64" ;;
  aarch64|arm64) arch="arm64" ;;
  *) echo "✖ Keine Architektur '$(uname -m)' – SLACK_MCP_BIN manuell setzen"; exit 1 ;;
esac
bin_name="slack-mcp-server-linux-${arch}"

resolve_binary() {
  if [[ -n "${SLACK_MCP_BIN:-}" && -x "${SLACK_MCP_BIN}" ]]; then
    echo "$SLACK_MCP_BIN"; return
  fi
  if [[ -s "${SCRIPT_DIR}/bin/${bin_name}" ]]; then
    chmod +x "${SCRIPT_DIR}/bin/${bin_name}"
    echo "${SCRIPT_DIR}/bin/${bin_name}"; return
  fi
  mkdir -p "$CACHE_DIR"
  local dest="${CACHE_DIR}/${SLACK_MCP_VERSION}/${bin_name}"
  if [[ ! -s "$dest" ]]; then
    echo "▶ Lade ${bin_name} (${SLACK_MCP_VERSION}) …" >&2
    mkdir -p "$(dirname "$dest")"
    curl -fSL --retry 3 -o "${dest}.part" "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/${bin_name}"
    curl -fSL --retry 3 -o "${dest}.sha256" "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/checksums.txt"
    ( cd "$(dirname "$dest")" \
      && grep " ${bin_name}\$" "$(basename "$dest").sha256" > expected.sha256 \
      && mv "${dest}.part" "$dest" \
      && sha256sum -c expected.sha256 )
    rm -f "${dest}.sha256" "$(dirname "$dest")/expected.sha256" "${dest}.part"
  fi
  chmod +x "$dest"
  echo "$dest"
}

BIN="$(resolve_binary)"

if [[ "$PRINT_VERSION" == "1" ]]; then
  "$BIN" --version
  exit 0
fi

# Ohne Token startet der Server nicht (Cache-Refresh schlägt fehl → Fatal).
# Demo-Modus: SLACK_MCP_XOXP_TOKEN=demo überspringt den Cache-Refresh – der
# Server läuft, Tool-Aufrufe an Slack scheitern aber erwartbar.
if [[ -z "${SLACK_MCP_XOXB_TOKEN:-}${SLACK_MCP_XOXP_TOKEN:-}${SLACK_MCP_XOXC_TOKEN:-}" ]]; then
  echo "⚠ Kein Slack-Token gesetzt → Demo-Modus (SLACK_MCP_XOXP_TOKEN=demo)." >&2
  echo "  Für echten Betrieb: SLACK_MCP_XOXB_TOKEN=xoxb-… (siehe docs/SLACK_MCP.md)" >&2
  export SLACK_MCP_XOXP_TOKEN="${SLACK_MCP_XOXP_TOKEN:-demo}"
fi

export SLACK_MCP_HOST SLACK_MCP_PORT
export SLACK_MCP_USERS_CACHE="${SLACK_MCP_USERS_CACHE:-${CACHE_DIR}/users_cache.json}"
export SLACK_MCP_CHANNELS_CACHE="${SLACK_MCP_CHANNELS_CACHE:-${CACHE_DIR}/channels_cache_v2.json}"

if [[ "$SLACK_MCP_TRANSPORT" == "sse" ]]; then
  url="http://${SLACK_MCP_HOST}:${SLACK_MCP_PORT}/sse"
else
  url="http://${SLACK_MCP_HOST}:${SLACK_MCP_PORT}/mcp"
fi

echo "▶ Slack-MCP-Server ${SLACK_MCP_VERSION} (${bin_name})"
echo "  Transport: ${SLACK_MCP_TRANSPORT}"
echo "  Endpunkt:  ${url}"
echo "  Backend:   SLACK_MCP_URL=${url}  (docker-compose: http://slack-mcp:${SLACK_MCP_PORT}/mcp)"
echo

exec "$BIN" --transport "$SLACK_MCP_TRANSPORT"
