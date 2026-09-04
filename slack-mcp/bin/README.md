# `slack-mcp/bin/` – optionale Offline-Binaries

Hier abgelegte Binaries werden vom `slack-mcp/Dockerfile` **bevorzugt** verwendet
(kein Download während `docker build`, air-gapped-tauglich).

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
