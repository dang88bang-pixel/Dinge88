#!/usr/bin/env bash
# Lädt OpenJDK 17 (Eclipse Temurin / Adoptium) als portables Archiv.
# Läuft auf dem Rechner MIT Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

OS="$(detect_os)"
ARCH="$(detect_arch)"
OUT_DIR="${OFFLINE_REPO}/jdk"
mkdir_p "$OUT_DIR"

# Adoptium API: neuestes GA JDK 17 Hotspot
# https://api.adoptium.net/
case "$OS" in
  linux)   ADOPT_OS="linux";   EXT="tar.gz" ;;
  mac)     ADOPT_OS="mac";     EXT="tar.gz" ;;
  windows) ADOPT_OS="windows"; EXT="zip" ;;
  *) die "Unbekanntes OS: $OS" ;;
esac

case "$ARCH" in
  x64)     ADOPT_ARCH="x64" ;;
  aarch64) ADOPT_ARCH="aarch64" ;;
  *)       ADOPT_ARCH="x64"; warn "Arch $ARCH → Fallback x64" ;;
esac

API_URL="https://api.adoptium.net/v3/binary/latest/${JAVA_MAJOR}/ga/${ADOPT_OS}/${ADOPT_ARCH}/jdk/hotspot/normal/eclipse?project=jdk"
DEST="${OUT_DIR}/OpenJDK${JAVA_MAJOR}U-jdk_${ADOPT_ARCH}_${ADOPT_OS}_hotspot.${EXT}"

log "JDK ${JAVA_MAJOR} (${ADOPT_OS}/${ADOPT_ARCH}) von Adoptium laden…"
download "$API_URL" "$DEST"

# Metadaten für Install-Skript
cat > "${OUT_DIR}/jdk.env" <<EOF
JAVA_VERSION=${JAVA_MAJOR}
JDK_ARCHIVE=$(basename "$DEST")
JDK_OS=${ADOPT_OS}
JDK_ARCH=${ADOPT_ARCH}
EOF

ok "JDK-Archiv bereit: $DEST"
ok "Größe: $(du -h "$DEST" | awk '{print $1}')"
