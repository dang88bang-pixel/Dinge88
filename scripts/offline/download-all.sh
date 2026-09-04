#!/usr/bin/env bash
# =============================================================================
# SecureGuard Enterprise – Offline-Repository aufbauen
# =============================================================================
# Auf dem Rechner MIT Internetzugang ausführen. Erzeugt ./offline_repo/ mit:
#   jdk/            OpenJDK 17 (Temurin)
#   android-sdk/    platforms, build-tools, platform-tools, cmdline-tools
#   platformio/     ESP32-Plattform + Libs (+ optionaler Cache)
#   gradle/         Dependency- + Wrapper-Cache
#   slack-mcp/      Slack-MCP-Server-Binaries (amd64 + arm64)
#
# Danach offline_repo/ per USB/Netzlaufwerk auf den Zielrechner kopieren und
# dort scripts/offline/install-offline.sh ausführen.
#
# Nutzung:
#   ./scripts/offline/download-all.sh
#   ./scripts/offline/download-all.sh --skip-gradle
#   OFFLINE_REPO=/media/usb/sg-offline ./scripts/offline/download-all.sh
# =============================================================================
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SKIP_GRADLE=0
SKIP_PIO=0
SKIP_JDK=0
SKIP_SDK=0
SKIP_SLACK=0
for arg in "$@"; do
  case "$arg" in
    --skip-gradle) SKIP_GRADLE=1 ;;
    --skip-pio|--skip-platformio) SKIP_PIO=1 ;;
    --skip-jdk) SKIP_JDK=1 ;;
    --skip-sdk|--skip-android) SKIP_SDK=1 ;;
    --skip-slack|--skip-slack-mcp) SKIP_SLACK=1 ;;
    --help|-h)
      sed -n '2,20p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *) warn "Unbekanntes Argument: $arg" ;;
  esac
done

mkdir_p "${OFFLINE_REPO}"
log "Offline-Repo: ${OFFLINE_REPO}"
log "Repo-Root:    ${REPO_ROOT}"
echo

[[ "$SKIP_JDK" == "1" ]] || bash "${SCRIPT_DIR}/download-jdk.sh"
echo
[[ "$SKIP_SDK" == "1" ]] || bash "${SCRIPT_DIR}/download-android-sdk.sh"
echo
[[ "$SKIP_PIO" == "1" ]] || bash "${SCRIPT_DIR}/download-platformio.sh"
echo
[[ "$SKIP_GRADLE" == "1" ]] || bash "${SCRIPT_DIR}/download-gradle-deps.sh"
echo
[[ "$SKIP_SLACK" == "1" ]] || bash "${SCRIPT_DIR}/download-slack-mcp.sh"
echo

# Gesamt-Manifest
MANIFEST="${OFFLINE_REPO}/MANIFEST.txt"
write_manifest_header "$MANIFEST"
while IFS= read -r -d '' f; do
  # Nur Dateien bis 50 MB einzeln hashen – große Caches als Verzeichnis-Marker
  sz=$(wc -c < "$f" 2>/dev/null || echo 0)
  if [[ "$sz" -lt 52428800 ]]; then
    append_manifest "$MANIFEST" "$f" "$OFFLINE_REPO"
  fi
done < <(find "$OFFLINE_REPO" -type f \
  ! -path '*/gradle/caches/*' \
  ! -path '*/platformio/cache/*' \
  ! -name 'MANIFEST.txt' \
  -print0 2>/dev/null)

# README im Transfer-Ordner
cat > "${OFFLINE_REPO}/README.txt" <<EOF
SecureGuard Enterprise – Offline-Repository
===========================================
Erzeugt: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Host:    $(hostname 2>/dev/null || echo unknown)

Inhalt:
  jdk/           OpenJDK ${JAVA_MAJOR} (portables Archiv)
  android-sdk/   Android SDK (platforms, build-tools, platform-tools)
  platformio/    ESP32-Plattform + Bibliotheken
  gradle/        Gradle Wrapper + Dependency-Cache
  slack-mcp/     Slack-MCP-Server (provectus) Binaries amd64/arm64

Installation auf dem Offline-Rechner:
  1. Diesen Ordner nach <ziel>/offline_repo kopieren
     (oder OFFLINE_REPO auf den USB-Pfad setzen)
  2. Im Projektroot:
       ./scripts/offline/install-offline.sh
  3. API-Keys setzen:
       cp local.properties.example local.properties
       # sdk.dir wurde vom Install-Skript gesetzt – Keys ergänzen
  4. Bauen:
       ./gradlew :app:assembleDebug --offline
  5. Firmware:
       cd firmware/secureguard_esp32 && pio run

Siehe docs/OFFLINE_SETUP.md für Details.
EOF

# Pack-Option: tar des gesamten Repos (ohne doppelte Kompression der inneren Archive)
ARCHIVE="${REPO_ROOT}/secureguard-offline-repo-$(date +%Y%m%d).tar"
log "Optional: Archiv erstellen → ${ARCHIVE}"
if command -v tar >/dev/null 2>&1; then
  tar -cf "$ARCHIVE" -C "$(dirname "$OFFLINE_REPO")" "$(basename "$OFFLINE_REPO")" \
    && ok "Archiv: $ARCHIVE ($(du -h "$ARCHIVE" | awk '{print $1}'))" \
    || warn "tar fehlgeschlagen – Ordner manuell kopieren"
fi

echo
ok "Fertig. Offline-Repo: ${OFFLINE_REPO}"
du -sh "${OFFLINE_REPO}" 2>/dev/null | awk '{print "  Gesamtgröße: "$1}' || true
echo
log "Nächster Schritt: Ordner auf USB kopieren und auf dem Zielrechner"
log "  ./scripts/offline/install-offline.sh"
