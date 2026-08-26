#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Android-Build-Setup (JDK + SDK + APK)
# ==============================================================
# Installiert JDK 17 und die Android SDK Komponenten und baut die
# App als Debug- und Release-APK.
#
#   ./scripts/setup-android.sh          # alles
#   ./scripts/setup-android.sh build    # nur bauen (SDK bereits da)
#
# API-Keys/Endpunkte vorher in local.properties eintragen!
# ==============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
JDK_VERSION=17
COMPILE_SDK=35
BUILD_TOOLS=35.0.0
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

require() { command -v "$1" >/dev/null 2>&1 || { echo "❌ $1 fehlt – bitte installieren"; exit 1; }; }

install_jdk() {
  if command -v java >/dev/null 2>&1 && [ "$(java -version 2>&1 | head -1 | grep -oP 'version \"\K[0-9]+')" = "$JDK_VERSION" ]; then
    echo "✓ JDK $JDK_VERSION bereits vorhanden"
    return
  fi
  echo "→ Installiere JDK $JDK_VERSION …"
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-17-jdk openjdk-17-jre-headless unzip
  elif command -v brew >/dev/null 2>&1; then
    brew install --quiet openjdk@17
  else
    echo "❌ Kein Paketmanager gefunden (apt-get/brew). JDK 17 manuell installieren."
    exit 1
  fi
  echo "✓ JDK $JDK_VERSION installiert"
}

install_sdk() {
  if [ -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "✓ Android SDK bereits vorhanden ($SDK_ROOT)"
  else
    echo "→ Installiere Android SDK cmdline-tools nach $SDK_ROOT …"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    local tmp; tmp="$(mktemp -d)"
    curl -fSL -o "$tmp/cmdline.zip" "$CMDLINE_TOOLS_URL"
    unzip -q -o "$tmp/cmdline.zip" -d "$SDK_ROOT/cmdline-tools"
    mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
  echo "→ Installiere SDK-Komponenten (platforms;android-$COMPILE_SDK, build-tools;$BUILD_TOOLS, platform-tools) …"
  "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
    "platforms;android-$COMPILE_SDK" "build-tools;$BUILD_TOOLS" "platform-tools"
}

write_local_properties() {
  if [ ! -f local.properties ]; then
    cat > local.properties <<EOF
sdk.dir=$SDK_ROOT
EOF
    echo "✓ local.properties erzeugt (sdk.dir=$SDK_ROOT)"
  fi
}

# SDK ist auch auf GitHub-Runnern verfügbar – lokale Konfiguration immer setzen.
echo "sdk.dir=$SDK_ROOT" > /tmp/sg-sdkdir
SDK_DIR_FOUND=""

case "${1:-all}" in
  all)
    require curl; require unzip
    install_jdk
    install_sdk
    write_local_properties
    ;;
  build)
    write_local_properties
    ;;
  *)
    echo "Verwendung: $0 [all|build]"; exit 1
    ;;
esac

echo
echo "→ Baue Debug-APK …"
./gradlew :app:assembleDebug --no-daemon --stacktrace

echo
echo "→ Baue Release-APK …"
./gradlew :app:assembleRelease --no-daemon --stacktrace

echo
echo "=============================================================="
echo " Fertig! APKs:"
ls -la app/build/outputs/apk/debug/ app/build/outputs/apk/release/ 2>/dev/null || true
echo "=============================================================="
