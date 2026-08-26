#!/usr/bin/env bash
# Spiegelt Android Command-Line-Tools + platforms/build-tools/platform-tools.
# Läuft auf dem Rechner MIT Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

OS="$(detect_os)"
OUT_DIR="${OFFLINE_REPO}/android-sdk"
TOOLS_DIR="${OFFLINE_REPO}/android-cmdline-tools"
mkdir_p "$OUT_DIR" "$TOOLS_DIR"

case "$OS" in
  linux)   HOST_OS="linux" ;;
  mac)     HOST_OS="mac" ;;
  windows) HOST_OS="win" ;;
  *) die "Unbekanntes OS: $OS" ;;
esac

ZIP_NAME="commandlinetools-${HOST_OS}-${CMDLINE_TOOLS_VERSION}_latest.zip"
ZIP_URL="https://dl.google.com/android/repository/${ZIP_NAME}"
ZIP_PATH="${TOOLS_DIR}/${ZIP_NAME}"

download "$ZIP_URL" "$ZIP_PATH"

# cmdline-tools entpacken und sdkmanager nutzen, um Pakete zu spiegeln
EXTRACT="${TOOLS_DIR}/extract"
rm -rf "$EXTRACT"
mkdir_p "$EXTRACT"
need_cmd unzip
unzip -q -o "$ZIP_PATH" -d "$EXTRACT"

# Google packt als cmdline-tools/… – wir wollen cmdline-tools/latest/bin/sdkmanager
if [[ -d "${EXTRACT}/cmdline-tools" ]]; then
  mkdir_p "${OUT_DIR}/cmdline-tools"
  rm -rf "${OUT_DIR}/cmdline-tools/latest"
  mv "${EXTRACT}/cmdline-tools" "${OUT_DIR}/cmdline-tools/latest"
elif [[ -d "${EXTRACT}/tools" ]]; then
  mkdir_p "${OUT_DIR}/cmdline-tools/latest"
  mv "${EXTRACT}/tools/"* "${OUT_DIR}/cmdline-tools/latest/"
else
  # Manche ZIPs legen bin/ direkt ab
  mkdir_p "${OUT_DIR}/cmdline-tools/latest"
  mv "${EXTRACT}/"* "${OUT_DIR}/cmdline-tools/latest/" 2>/dev/null || true
fi

SDKMANAGER="${OUT_DIR}/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMANAGER" || -f "$SDKMANAGER" ]] || die "sdkmanager nicht gefunden unter $SDKMANAGER"

# Java wird für sdkmanager benötigt
if ! command -v java >/dev/null 2>&1; then
  warn "java nicht im PATH – versuche soeben geladenes JDK…"
  if [[ -f "${OFFLINE_REPO}/jdk/jdk.env" ]]; then
    # shellcheck disable=SC1091
    source "${OFFLINE_REPO}/jdk/jdk.env"
    ARCH_TMP="$(detect_arch)"
    case "$(detect_os)" in
      linux|mac)
        tar -xzf "${OFFLINE_REPO}/jdk/${JDK_ARCHIVE}" -C "${OFFLINE_REPO}/jdk"
        JAVA_HOME_CAND="$(find "${OFFLINE_REPO}/jdk" -maxdepth 3 -type d -name 'bin' | head -1 | xargs dirname)"
        export JAVA_HOME="${JAVA_HOME_CAND}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
        ;;
    esac
  fi
fi
command -v java >/dev/null 2>&1 || die "Java wird für sdkmanager benötigt. Zuerst: ./download-jdk.sh"

log "Android-SDK-Lizenzen akzeptieren…"
yes | "$SDKMANAGER" --sdk_root="$OUT_DIR" --licenses >/dev/null || true

# Paketliste zusammenstellen
PACKAGES=(platform-tools)
# shellcheck disable=SC2206
PACKAGES+=(${ANDROID_PLATFORMS})
# shellcheck disable=SC2206
PACKAGES+=(${ANDROID_BUILD_TOOLS})

log "SDK-Pakete installieren nach ${OUT_DIR}:"
for p in "${PACKAGES[@]}"; do
  echo "  - $p"
done

"$SDKMANAGER" --sdk_root="$OUT_DIR" "${PACKAGES[@]}"

# Original-ZIP behalten (für Offline-Rechner ohne vorheriges Entpacken)
cp -n "$ZIP_PATH" "${OUT_DIR}/../android-cmdline-tools/${ZIP_NAME}" 2>/dev/null || true

# Marker-Datei
cat > "${OUT_DIR}/sdk.env" <<EOF
ANDROID_SDK_ROOT=android-sdk
COMPILE_SDK=${COMPILE_SDK}
BUILD_TOOLS=${BUILD_TOOLS}
CMDLINE_TOOLS_VERSION=${CMDLINE_TOOLS_VERSION}
PACKAGES=${PACKAGES[*]}
EOF

ok "Android-SDK gespiegelt: $OUT_DIR"
du -sh "$OUT_DIR" | awk '{print "  Größe: "$1}'
