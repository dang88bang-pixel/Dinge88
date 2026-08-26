#!/usr/bin/env bash
# =============================================================================
# SecureGuard Enterprise – Offline-Toolchain installieren
# =============================================================================
# Auf dem Zielrechner OHNE Internetzugang ausführen, nachdem offline_repo/
# (per USB/Netzlaufwerk) bereitsteht.
#
#   OFFLINE_REPO=/media/usb/offline_repo ./scripts/offline/install-offline.sh
#   ./scripts/offline/install-offline.sh --skip-pio
#
# Danach:
#   source ~/.secureguard/env.sh
#   cp local.properties.example local.properties   # Keys ergänzen
#   ./gradlew :app:assembleDebug --offline
#   cd firmware/secureguard_esp32 && pio run
# =============================================================================
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SKIP_GRADLE=0
SKIP_PIO=0
SKIP_JDK=0
SKIP_SDK=0
for arg in "$@"; do
  case "$arg" in
    --skip-gradle) SKIP_GRADLE=1 ;;
    --skip-pio|--skip-platformio) SKIP_PIO=1 ;;
    --skip-jdk) SKIP_JDK=1 ;;
    --skip-sdk|--skip-android) SKIP_SDK=1 ;;
    --help|-h)
      sed -n '2,18p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
  esac
done

[[ -d "$OFFLINE_REPO" ]] || die "OFFLINE_REPO nicht gefunden: $OFFLINE_REPO
  Setze z. B. OFFLINE_REPO=/mnt/usb/offline_repo"

log "Installiere aus: ${OFFLINE_REPO}"
echo

[[ "$SKIP_JDK" == "1" ]]    || bash "${SCRIPT_DIR}/install-jdk.sh"
echo
[[ "$SKIP_SDK" == "1" ]]    || bash "${SCRIPT_DIR}/install-android-sdk.sh"
echo
[[ "$SKIP_GRADLE" == "1" ]] || bash "${SCRIPT_DIR}/install-gradle-cache.sh"
echo
[[ "$SKIP_PIO" == "1" ]]    || bash "${SCRIPT_DIR}/install-platformio.sh"
echo

ENV_FILE="${HOME}/.secureguard/env.sh"
ok "Toolchain installiert."
echo
log "Umgebung laden:"
echo "  source ${ENV_FILE}"
echo
log "API-Keys / Endpunkte:"
echo "  # local.properties existiert ggf. schon (sdk.dir gesetzt)"
echo "  # Keys aus local.properties.example ergänzen – niemals committen"
echo
log "Android bauen (offline):"
echo "  cd ${REPO_ROOT}"
echo "  source ${ENV_FILE}"
echo "  ./gradlew :app:assembleDebug --offline"
echo
log "ESP32-Firmware:"
echo "  cd ${REPO_ROOT}/firmware/secureguard_esp32"
echo "  pio run"
echo
warn "SQLCipher & produktive API-Keys: bewusst Phase 2 – erst wenn der"
warn "Offline-Android-Build verifiziert ist (siehe docs/OFFLINE_SETUP.md)."
