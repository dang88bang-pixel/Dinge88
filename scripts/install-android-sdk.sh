#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – Android SDK Installation (Linux/macOS)
#   - Command-Line Tools (latest)
#   - Platform 35 (android-35)
#   - Build-Tools 35.0.0
#   - Platform-Tools
#   - akzeptiert alle SDK-Lizenzen
#   - schreibt local.properties (Pfade + SDK-Referenzen)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
CMD_TOOLS="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
ENV_FILE="$HOME/.secureguard-env.sh"

echo "==> Android SDK Setup"
echo "    ANDROID_HOME=$ANDROID_HOME"

mkdir -p "$ANDROID_HOME/cmdline-tools"

if [[ ! -x "$CMD_TOOLS" ]]; then
  echo "==> Lade Android Command-Line Tools..."
  OS="linux"
  case "$(uname -s)" in
    Darwin) OS="mac" ;;
  esac
  # Aktuelle Paketnummer aus dem Google-Repository ziehen; Fallback bekannte Version.
  CMDLINE_VER=$(curl -fsL https://dl.google.com/android/repository/repository2-1.xml 2>/dev/null \
    | grep -oP 'commandlinetools-(linux|mac)-\K[0-9]+' | sort -V | tail -1 || true)
  [[ -z "$CMDLINE_VER" ]] && CMDLINE_VER="11076708"
  ZIP="commandlinetools-${OS}-${CMDLINE_VER}_latest.zip"
  echo "==> Download $ZIP ..."
  curl -fSL -o "/tmp/$ZIP" "https://dl.google.com/android/repository/$ZIP"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  unzip -q -o "/tmp/$ZIP" -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "/tmp/$ZIP"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

echo "==> Lizenzen akzeptieren..."
yes | "$CMD_TOOLS" --licenses >/dev/null 2>&1 || true

echo "==> Installiere SDK-Pakete (Plattform 35, Build-Tools 35.0.0, Platform-Tools)"
"$CMD_TOOLS" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null

echo "==> Verifikation"
[[ -d "$ANDROID_HOME/platforms/android-35" ]] && echo "✔ Platform 35" || { echo "❌ Platform 35 fehlt"; exit 1; }
[[ -d "$ANDROID_HOME/build-tools/35.0.0" ]] && echo "✔ Build-Tools 35.0.0" || { echo "❌ Build-Tools fehlen"; exit 1; }
[[ -x "$ANDROID_HOME/platform-tools/adb" ]] && echo "✔ Platform-Tools (adb)" || { echo "❌ adb fehlt"; exit 1; }

# local.properties erzeugen/aktualisieren
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$REPO_ROOT/local.properties"
SDK_ESC=$(printf '%s' "$ANDROID_HOME" | sed 's/\\/\\\\/g; s/:/\\:/g')
if [[ ! -f "$PROPS" ]]; then
  cp "$REPO_ROOT/local.properties.example" "$PROPS"
fi
if grep -q '^sdk.dir=' "$PROPS"; then
  sed -i "s|^sdk.dir=.*|sdk.dir=$SDK_ESC|" "$PROPS"
else
  echo "sdk.dir=$SDK_ESC" >> "$PROPS"
fi
if grep -q '^ANDROID_HOME=' "$PROPS"; then
  sed -i "s|^ANDROID_HOME=.*|ANDROID_HOME=$SDK_ESC|" "$PROPS"
else
  echo "ANDROID_HOME=$SDK_ESC" >> "$PROPS"
fi

# Persistente Env-Datei
if [[ -f "$ENV_FILE" ]]; then
  if ! grep -q '^export ANDROID_HOME=' "$ENV_FILE"; then
    cat >> "$ENV_FILE" <<EOF

export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_SDK_HOME="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:\$PATH"
EOF
  fi
else
  cat > "$ENV_FILE" <<EOF
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_SDK_HOME="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:\$PATH"
EOF
fi

echo "✔ Android SDK bereit: $ANDROID_HOME"
echo "  local.properties: $PROPS"
echo "  Einbinden:  source $ENV_FILE"
