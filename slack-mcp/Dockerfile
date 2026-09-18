# syntax=docker/dockerfile:1
# =============================================================================
# Slack-MCP-Server (provectus/slack-mcp-server) für den SecureGuard-Stack
# =============================================================================
# Baut den Server aus dem gepinnten Release-Tag und startet ihn mit dem
# Streamable-HTTP-Transport auf 0.0.0.0:13080 → das Backend nutzt ihn als
# MCP-Server (backend/slack_mcp.py, Endpunkte /api/slack/*).
#
# CGO_ENABLED=0 erzwingt ein statisches Binary. Das ist zwingend nötig:
# die offiziellen Release-Binaries des Forks werden dynamisch gegen glibc
# gebaut (das Makefile setzt kein CGO_ENABLED=0) und laufen auf einem
# Alpine/musl-Runtime-Image nicht ("slack-mcp-server: not found").
# Der Multi-Stage-Build übernimmt daher das Build-Verfahren des
# Upstream-Dockerfiles (golang:1.25 + CGO_ENABLED=0).
#
# Build/Version:
#   docker build -t secureguard/slack-mcp ./slack-mcp
#   docker build --build-arg SLACK_MCP_VERSION=pv-v1.0.1 -t secureguard/slack-mcp ./slack-mcp
# =============================================================================
FROM golang:1.25 AS build

# Gepinnte Version des provectus-Forks (Releases: pv-vX.Y.Z).
ARG SLACK_MCP_VERSION=pv-v1.0.1

RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates

WORKDIR /src

# Quelle exakt am Release-Tag auschecken (deterministisch, kein `latest`).
RUN git clone --depth 1 --branch "${SLACK_MCP_VERSION}" \
      https://github.com/provectus/slack-mcp-server.git .

# Statisches Binary (läuft ohne glibc auf dem musl-Runtime-Image).
# Wichtig: die offiziellen Release-Binaries sind glibc-gelinkt und würden auf
# Alpine mit "not found" (exit 127) scheitern — deshalb hier aus Quelle bauen.
ENV CGO_ENABLED=0
RUN go build -trimpath -ldflags="-s -w" \
      -o /out/slack-mcp-server ./cmd/slack-mcp-server

FROM alpine:3.22

RUN apk add --no-cache ca-certificates curl

COPY --from=build /out/slack-mcp-server /usr/local/bin/slack-mcp-server

RUN addgroup -S -g 10001 slackmcp; \
    adduser -S -u 10001 -G slackmcp -h /app/mcp-server slackmcp; \
    mkdir -p /app/mcp-server; \
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
