#!/usr/bin/env bash
# =============================================================================
# Slack-MCP-Server (provectus/slack-mcp-server) – Binary spiegeln
# =============================================================================
# Läuft auf dem Rechner MIT Internetzugang. Lädt die gepinnten Release-Binaries
# (linux amd64 + arm64) und legt sie ab unter:
#   ${OFFLINE_REPO}/slack-mcp/     (Offline-Transfer, USB/Netzlaufwerk)
#   slack-mcp/bin/                 (Build-Kontext: Dockerfile nutzt sie direkt)
#
# Die Prüfsumme wird gegen die release-eigene checksums.txt verifiziert.
#
# Nutzung:
#   ./scripts/offline/download-slack-mcp.sh
#   SLACK_MCP_VERSION=pv-v1.0.1 ./scripts/offline/download-slack-mcp.sh
#   ./scripts/offline/download-slack-mcp.sh --only-local   # nur slack-mcp/bin/
# =============================================================================
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SLACK_MCP_VERSION="${SLACK_MCP_VERSION:-pv-v1.0.1}"
SLACK_MCP_BASE_URL="${SLACK_MCP_BASE_URL:-https://github.com/provectus/slack-mcp-server/releases/download}"
SLACK_MCP_ARCHES="${SLACK_MCP_ARCHES:-amd64 arm64}"

ONLY_LOCAL=0
for arg in "$@"; do
  case "$arg" in
    --only-local) ONLY_LOCAL=1 ;;
    --help|-h) sed -n '2,17p' "$0" | sed 's/^# \?//'; exit 0 ;;
    *) warn "Unbekanntes Argument: $arg" ;;
  esac
done

REPO_BIN_DIR="${REPO_ROOT}/slack-mcp/bin"
MIRROR_DIR="${OFFLINE_REPO}/slack-mcp"
mkdir_p "$REPO_BIN_DIR"
[[ "$ONLY_LOCAL" == "1" ]] || mkdir_p "$MIRROR_DIR"

log "Slack-MCP-Server ${SLACK_MCP_VERSION} (${SLACK_MCP_ARCHES})"

# mktemp -d: common.sh:download() überspringt existierende Zieldateien – ein
# leeres mktemp-File würde therefore als "bereits vorhanden" gelten.
TMP_DIR="$(mktemp -d)"
CHECKSUMS="${TMP_DIR}/checksums.txt"
trap 'rm -rf "$TMP_DIR"' EXIT
download "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/checksums.txt" "$CHECKSUMS"

for arch in $SLACK_MCP_ARCHES; do
  bin="slack-mcp-server-linux-${arch}"
  dest="${REPO_BIN_DIR}/${bin}"
  if [[ -s "$dest" ]]; then
    ok "Bereits vorhanden: ${bin}"
  else
    download "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/${bin}" "$dest"
  fi

  expected="$(grep " ${bin}\$" "$CHECKSUMS" | awk '{print $1}' | head -1 || true)"
  actual="$(sha256_file "$dest")"
  if [[ -z "$expected" ]]; then
    die "checksums.txt enthält keinen Eintrag für ${bin} (Version ${SLACK_MCP_VERSION}?)"
  fi
  if [[ "$expected" != "$actual" ]]; then
    rm -f "$dest"
    die "Checksumme mismatch für ${bin}: erwartet ${expected}, erhalten ${actual}"
  fi
  ok "Checksumme OK: ${bin} (${actual:0:12}…)"

  if [[ "$ONLY_LOCAL" != "1" ]]; then
    cp -f "$dest" "${MIRROR_DIR}/${bin}"
  fi
done

cat > "${REPO_BIN_DIR}/VERSION" <<EOF
# Von scripts/offline/download-slack-mcp.sh erzeugt – nicht manuell editieren.
SLACK_MCP_VERSION=${SLACK_MCP_VERSION}
SLACK_MCP_BASE_URL=${SLACK_MCP_BASE_URL}
DOWNLOADED_UTC=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

echo
ok "Binaries: ${REPO_BIN_DIR} (docker build nutzt sie automatisch)"
[[ "$ONLY_LOCAL" == "1" ]] || ok "Spiegel:   ${MIRROR_DIR}"
log "Weiter:  docker compose up --build -d slack-mcp   (oder ./slack-mcp/run-local.sh)"
