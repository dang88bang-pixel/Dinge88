# `slack-mcp/bin/` – optionale Offline-Binaries (nur für `run-local.sh`)

Hier abgelegte Binaries werden von **`slack-mcp/run-local.sh`** (lokaler
Betrieb ohne Docker) **bevorzugt** verwendet. Das `Dockerfile` baut den Server
hingegen aus dem gepinnten Release-Tag mit `CGO_ENABLED=0` — die offiziellen
Release-Binaries sind glibc-gelinkt und liefen auf dem Alpine-Runtime-Image
nicht (`slack-mcp-server: not found`, exit 127).

## Befüllen (Rechner mit Internetzugang)

```bash
./scripts/offline/download-slack-mcp.sh --only-local
```

Das lädt die gepinnte Version (`SLACK_MCP_VERSION`, Default `pv-v1.0.1`) von
<https://github.com/provectus/slack-mcp-server/releases>, prüft sie gegen die
release-eigene `checksums.txt` und legt ab:

```
slack-mcp/bin/slack-mcp-server-linux-amd64
slack-mcp/bin/slack-mcp-server-linux-arm64
slack-mcp/bin/VERSION
```

## Git

Binaries sind **nicht** versioniert (`.gitignore`: `slack-mcp/bin/*`, ausgenommen
`README.md` / `.gitkeep`) – sie sind 15–20 MB groß und werden über Release +
Checksumme bezogen. `VERSION` dokumentiert, was lokal heruntergeladen wurde.
