#!/usr/bin/env bash
# Baut das Android-Projekt einmal online und kopiert den Gradle-Cache
# (Dependencies + Wrapper-Distribution) ins Offline-Repo.
#
# Läuft auf dem Rechner MIT Internetzugang.
# Voraussetzungen: JDK 17, Android-SDK (lokal oder soeben gespiegelt)
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

OUT_DIR="${OFFLINE_REPO}/gradle"
mkdir_p "$OUT_DIR"

need_cmd java
JAVA_VER="$(java -version 2>&1 | head -1 || true)"
log "Java: $JAVA_VER"

# SDK-Pfad: offline_repo oder ANDROID_HOME oder local.properties
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" && -d "${OFFLINE_REPO}/android-sdk" ]]; then
  SDK_ROOT="${OFFLINE_REPO}/android-sdk"
fi
if [[ -z "$SDK_ROOT" && -f "${REPO_ROOT}/local.properties" ]]; then
  SDK_ROOT="$(grep -E '^sdk\.dir=' "${REPO_ROOT}/local.properties" | cut -d= -f2- | tr -d '\r' || true)"
  # local.properties escaped Windows-Pfade: C\:\\Users\\...
  SDK_ROOT="${SDK_ROOT//\\/}"
fi
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || die "Android-SDK nicht gefunden. Zuerst download-android-sdk.sh oder ANDROID_HOME setzen."

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

# local.properties für den Prefetch-Build schreiben (ohne Secrets)
LP="${REPO_ROOT}/local.properties"
if [[ ! -f "$LP" ]]; then
  log "Erzeuge temporäre local.properties mit sdk.dir…"
  # Gradle erwartet auf Windows escaped Backslashes – unter Linux normaler Pfad
  printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$LP"
  TEMP_LP=1
else
  # sdk.dir sicherstellen
  if ! grep -q '^sdk\.dir=' "$LP"; then
    printf '\nsdk.dir=%s\n' "$SDK_ROOT" >> "$LP"
  fi
  TEMP_LP=0
fi

cd "$REPO_ROOT"
chmod +x gradlew 2>/dev/null || true

log "Gradle-Dependencies auflösen (assembleDebug) – kann mehrere Minuten dauern…"
# --refresh-dependencies stellt sicher, dass der Cache voll ist
./gradlew :app:assembleDebug --no-daemon --refresh-dependencies || \
  ./gradlew :app:dependencies --no-daemon || \
  warn "Build nicht vollständig – Cache trotzdem kopieren"

GRADLE_HOME="${GRADLE_USER_HOME:-${HOME}/.gradle}"
log "Gradle-Cache kopieren von ${GRADLE_HOME}…"

# Wrapper-Distribution + Caches (ohne locks/journals)
mkdir_p "${OUT_DIR}/wrapper" "${OUT_DIR}/caches" "${OUT_DIR}/jdks"
if [[ -d "${GRADLE_HOME}/wrapper" ]]; then
  rsync -a "${GRADLE_HOME}/wrapper/" "${OUT_DIR}/wrapper/" 2>/dev/null \
    || cp -a "${GRADLE_HOME}/wrapper/." "${OUT_DIR}/wrapper/"
fi
if [[ -d "${GRADLE_HOME}/caches" ]]; then
  # modules-2 + jars-* + transforms sind die wichtigen Teile
  rsync -a \
    --exclude='*.lock' \
    --exclude='gc.properties' \
    "${GRADLE_HOME}/caches/" "${OUT_DIR}/caches/" 2>/dev/null \
    || cp -a "${GRADLE_HOME}/caches/." "${OUT_DIR}/caches/"
fi

# Gradle-Distribution separat (falls wrapper sie geladen hat)
DIST_HINT="${OUT_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
if [[ ! -f "$DIST_HINT" ]]; then
  GRADLE_URL="https://github.com/gradle/gradle-distributions/releases/download/v${GRADLE_VERSION}.0/gradle-${GRADLE_VERSION}-bin.zip"
  # Fallback auf services.gradle.org
  download "$GRADLE_URL" "$DIST_HINT" \
    || download "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "$DIST_HINT" \
    || warn "Gradle-Distribution konnte nicht separat geladen werden (steht ggf. im wrapper-Cache)"
fi

cat > "${OUT_DIR}/gradle.env" <<EOF
GRADLE_VERSION=${GRADLE_VERSION}
GRADLE_USER_HOME_REL=gradle
EOF

if [[ "${TEMP_LP:-0}" == "1" ]]; then
  rm -f "$LP"
fi

ok "Gradle-Offline-Cache: $OUT_DIR"
du -sh "$OUT_DIR" 2>/dev/null | awk '{print "  Größe: "$1}' || true
