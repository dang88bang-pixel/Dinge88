# syntax=docker/dockerfile:1
# =============================================================================
# Slack-MCP-Server (provectus/slack-mcp-server) für den SecureGuard-Stack
# =============================================================================
# Lädt das gepinnte Release-Binary des Forks (Go, statisch) und startet es mit
# dem Streamable-HTTP-Transport auf 0.0.0.0:13080 → das Backend nutzt es als
# MCP-Server (backend/slack_mcp.py, Endpunkte /api/slack/*).
#
# Offline-/Air-Gapped-Betrieb: Binary vor dem Build ablegen (dann kein Download)
#   ./scripts/offline/download-slack-mcp.sh      → slack-mcp/bin/
#
# Build/Version:
#   docker build -t secureguard/slack-mcp ./slack-mcp
#   docker build --build-arg SLACK_MCP_VERSION=pv-v1.0.1 -t secureguard/slack-mcp ./slack-mcp
# =============================================================================
FROM alpine:3.22

# Gepinnte Version des provectus-Forks (Releases: pv-vX.Y.Z).
ARG SLACK_MCP_VERSION=pv-v1.0.1
# amd64 | arm64 – wird von docker buildx automatisch gesetzt.
ARG TARGETARCH=amd64
ARG SLACK_MCP_BASE_URL=https://github.com/provectus/slack-mcp-server/releases/download

RUN apk add --no-cache ca-certificates curl

# Offline-Binary (optional, sonst leer – siehe slack-mcp/bin/README.md).
COPY bin/ /tmp/slack-bin/

RUN set -eux; \
    arch="${TARGETARCH:-amd64}"; \
    bin="slack-mcp-server-linux-${arch}"; \
    if [ -s "/tmp/slack-bin/${bin}" ]; then \
      # Offline-Pfad: Binary wurde von scripts/offline/download-slack-mcp.sh \
      # bereits gegen checksums.txt geprüft. \
      echo "Nutze lokales Binary: /tmp/slack-bin/${bin}"; \
      cp "/tmp/slack-bin/${bin}" "/tmp/${bin}"; \
    else \
      curl -fSL --retry 3 -o "/tmp/${bin}" \
        "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/${bin}"; \
      curl -fSL --retry 3 -o /tmp/checksums.txt \
        "${SLACK_MCP_BASE_URL}/${SLACK_MCP_VERSION}/checksums.txt"; \
      grep " ${bin}$" /tmp/checksums.txt > /tmp/expected.sha256; \
      test -s /tmp/expected.sha256 || { echo "keine Checksumme für ${bin}"; exit 1; }; \
      # sha256sum -c prüft gegen den Dateinamen in checksums.txt → cwd /tmp. \
      (cd /tmp && sha256sum -c /tmp/expected.sha256); \
    fi; \
    install -m 0755 "/tmp/${bin}" /usr/local/bin/slack-mcp-server; \
    rm -rf /tmp/slack-bin "/tmp/${bin}" /tmp/checksums.txt /tmp/expected.sha256; \
    mkdir -p /app/mcp-server; \
    addgroup -S -g 10001 slackmcp; \
    adduser -S -u 10001 -G slackmcp -h /app/mcp-server slackmcp; \
    chown -R slackmcp:slackmcp /app; \
    slack-mcp-server --version

# Cache für User-/Channel-Verzeichnis (Volume in docker-compose.yml).
# Ohne Cache funktionieren #channel-/@user-Auflösungen nicht – siehe
# docs/SLACK_MCP.md ("Limitations & Cache").
ENV SLACK_MCP_USERS_CACHE=/app/mcp-server/users_cache.json \
    SLACK_MCP_CHANNELS_CACHE=/app/mcp-server/channels_cache_v2.json \
    SLACK_MCP_CHANNEL_MEMBERS_CACHE=/app/mcp-server/channel_members_cache.json \
    SLACK_MCP_HOST=0.0.0.0 \
    SLACK_MCP_PORT=13080

WORKDIR /app/mcp-server
USER slackmcp
EXPOSE 13080

# MCP-Handshake als Healthcheck: prüft, dass der Server JSON-RPC beantwortet
# (funktioniert auch mit gesetztem SLACK_MCP_API_KEY – die Auth liegt auf den
# Tool-Aufrufen, nicht auf initialize/tools_list).
HEALTHCHECK --interval=30s --timeout=6s --start-period=15s --retries=3 \
  CMD curl -fsS --max-time 5 -o /dev/null -X POST \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json, text/event-stream' \
      -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"secureguard-healthcheck","version":"1.0"}}}' \
      "http://127.0.0.1:${SLACK_MCP_PORT}/mcp" || exit 1

ENTRYPOINT ["slack-mcp-server"]
CMD ["--transport", "http"]
