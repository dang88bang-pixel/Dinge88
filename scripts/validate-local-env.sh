#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – Lokale Build-Umgebung validieren
#   prüft: JDK 17, JAVA_HOME, Android SDK (35/35.0.0/platform-tools),
#          Lizenzen, local.properties, Python, PlatformIO, Backend-venv
# ══════════════════════════════════════════════════════════════════════════
set -uo pipefail

[[ -f "$HOME/.secureguard-env.sh" ]] && source "$HOME/.secureguard-env.sh" || true

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
BACKEND_VENV="${BACKEND_VENV_DIR:-$HOME/.secureguard-backend-venv}"
PIO_VENV="${PIO_VENV_DIR:-$HOME/.secureguard-platformio}"
FAIL=0
ok(){ echo "  ✔ $1"; }
bad(){ echo "  ✘ $1"; FAIL=1; }

echo "==> SecureGuard Lokale Build-Umgebung"

echo "--- JDK 17 ---"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  ok "JAVA_HOME=$JAVA_HOME"
else
  bad "JAVA_HOME fehlt oder kein java unter JAVA_HOME"
fi
if command -v java >/dev/null 2>&1; then
  VER=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}')
  ok "java -version: $VER"
else
  bad "java nicht im PATH"
fi

echo "--- Android SDK ---"
SDKMAN="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMAN" ]] && ok "Command-Line Tools" || bad "Command-Line Tools fehlen ($SDKMAN)"
[[ -d "$ANDROID_HOME/platforms/android-35" ]] && ok "Platform android-35" || bad "Platform android-35 fehlt"
[[ -d "$ANDROID_HOME/build-tools/35.0.0" ]] && ok "Build-Tools 35.0.0" || bad "Build-Tools 35.0.0 fehlen"
[[ -x "$ANDROID_HOME/platform-tools/adb" ]] && ok "Platform-Tools (adb)" || bad "adb fehlt"

PROPS="$REPO_ROOT/local.properties"
if [[ -f "$PROPS" ]]; then
  ok "local.properties vorhanden"
  grep -q '^sdk.dir=' "$PROPS" && ok "sdk.dir gesetzt" || bad "sdk.dir fehlt in local.properties"
else
  bad "local.properties fehlt (cp local.properties.example local.properties)"
fi

echo "--- Python / PlatformIO ---"
command -v python3 >/dev/null 2>&1 && ok "python3 $(python3 --version 2>&1)" || bad "python3 fehlt"
if command -v pio >/dev/null 2>&1; then
  ok "pio: $(command -v pio)"
elif [[ -x "$PIO_VENV/bin/pio" ]]; then
  ok "pio (venv): $PIO_VENV/bin/pio"
else
  bad "pio nicht gefunden – ./scripts/setup-platformio.sh"
fi

echo "--- Backend ---"
if [[ -x "$BACKEND_VENV/bin/python" ]]; then
  ok "Backend venv: $BACKEND_VENV"
else
  bad "Backend venv fehlt – ./scripts/setup-backend.sh"
fi

echo "--- Gradle Wrapper ---"
[[ -x "$REPO_ROOT/gradlew" ]] && ok "gradlew ausführbar" || bad "gradlew nicht ausführbar"

echo ""
if [[ "$FAIL" -eq 0 ]]; then
  echo "✅ Alle lokal prüfbaren Build-Voraussetzungen sind erfüllt."
else
  echo "❌ Einige Voraussetzungen fehlen. Skripte: ./scripts/install-jdk17.sh, ./scripts/install-android-sdk.sh, ./scripts/setup-platformio.sh, ./scripts/setup-backend.sh"
fi
exit "$FAIL"
