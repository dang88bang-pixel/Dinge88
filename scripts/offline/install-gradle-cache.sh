#!/usr/bin/env bash
# Spielt den gespiegelten Gradle-Cache in GRADLE_USER_HOME ein.
# Läuft auf dem Zielrechner OHNE Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SRC="${OFFLINE_REPO}/gradle"
[[ -d "$SRC" ]] || die "Kein Gradle-Cache im Offline-Repo: $SRC"

DEST="${GRADLE_USER_HOME:-${HOME}/.gradle}"
mkdir_p "$DEST"

log "Gradle-Cache → ${DEST}"

if [[ -d "${SRC}/wrapper" ]]; then
  mkdir_p "${DEST}/wrapper"
  rsync -a "${SRC}/wrapper/" "${DEST}/wrapper/" 2>/dev/null \
    || cp -a "${SRC}/wrapper/." "${DEST}/wrapper/"
  ok "wrapper"
fi

if [[ -d "${SRC}/caches" ]]; then
  mkdir_p "${DEST}/caches"
  rsync -a "${SRC}/caches/" "${DEST}/caches/" 2>/dev/null \
    || cp -a "${SRC}/caches/." "${DEST}/caches/"
  ok "caches"
fi

# Distribution-ZIP in wrapper/dists legen, falls separat vorhanden
DIST_ZIP="$(find "$SRC" -maxdepth 1 -name 'gradle-*-bin.zip' | head -1 || true)"
if [[ -n "$DIST_ZIP" ]]; then
  # Gradle erwartet: wrapper/dists/gradle-X.Y-bin/<hash>/gradle-X.Y-bin.zip
  VER="$(basename "$DIST_ZIP" | sed -E 's/gradle-([0-9.]+)-bin\.zip/\1/')"
  DIST_DIR="${DEST}/wrapper/dists/gradle-${VER}-bin/offline"
  mkdir_p "$DIST_DIR"
  cp -n "$DIST_ZIP" "${DIST_DIR}/gradle-${VER}-bin.zip"
  # Marker, dass Download „fertig“ ist
  touch "${DIST_DIR}/gradle-${VER}-bin.zip.ok" 2>/dev/null || true
  ok "Distribution gradle-${VER} bereitgestellt"
fi

# env.sh
ENV_FILE="${HOME}/.secureguard/env.sh"
mkdir_p "$(dirname "$ENV_FILE")"
if [[ -f "$ENV_FILE" ]]; then
  grep -q 'GRADLE_USER_HOME' "$ENV_FILE" 2>/dev/null || \
    echo "export GRADLE_USER_HOME=\"${DEST}\"" >> "$ENV_FILE"
else
  echo "export GRADLE_USER_HOME=\"${DEST}\"" > "$ENV_FILE"
fi
export GRADLE_USER_HOME="$DEST"

ok "GRADLE_USER_HOME=${DEST}"
log "Offline-Build:  cd ${REPO_ROOT} && ./gradlew :app:assembleDebug --offline"
