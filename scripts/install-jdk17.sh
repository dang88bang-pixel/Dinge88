#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – JDK 17 Installation (Linux/macOS)
#   - installiert Temurin JDK 17, falls nicht vorhanden
#   - setzt JAVA_HOME dauerhaft in ~/.secureguard-env.sh
#   - alternativ: vorhandenes JDK 17 akzeptieren
#   - kein Root erforderlich (userland in ~/.jdks, opt-in: --system)
#
# Nutzung:
#   ./scripts/install-jdk17.sh            # Userland (kein sudo)
#   ./scripts/install-jdk17.sh --system   # apt-get/Linux root (optional)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

MODE="${1:-userland}"
JDK_DIR="${JDK_DIR:-$HOME/.jdks}"
ENV_FILE="$HOME/.secureguard-env.sh"

echo "==> JDK 17 Setup (Modus: $MODE)"

if command -v java >/dev/null 2>&1; then
  VERSION=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}')
  if [[ "$VERSION" == 1.* ]]; then VERSION="${VERSION#1.}"; fi
  if [[ "$VERSION" == 17* ]]; then
    JAVA_HOME_CURRENT=$(readlink -f "$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")")
    echo "✔ JDK $VERSION bereits vorhanden: $JAVA_HOME_CURRENT"
    JAVA_HOME="$JAVA_HOME_CURRENT"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ "$MODE" == "system" ]] && command -v apt-get >/dev/null 2>&1; then
    echo "==> Versuche openjdk-17-jdk via apt-get zu installieren..."
    sudo apt-get update -y || true
    sudo apt-get install -y openjdk-17-jdk || true
    for candidate in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/java-17-*; do
      if [[ -x "$candidate/bin/java" ]]; then JAVA_HOME="$candidate"; break; fi
    done
  fi

  if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "==> Installiere Temurin JDK 17 nach $JDK_DIR ..."
    mkdir -p "$JDK_DIR"
    OS_ARCH=""
    case "$(uname -s)" in
      Linux) OS_ARCH="linux" ;;
      Darwin) OS_ARCH="mac" ;;
    esac
    if [[ -z "$OS_ARCH" ]]; then
      echo "❌ Nicht unterstütztes Betriebssystem: $(uname -s)" >&2
      exit 1
    fi
    CURL_ARCH="$(uname -m)"
    case "$CURL_ARCH" in
      x86_64) curl_arch="x64" ;;
      aarch64) curl_arch="aarch64" ;;
      arm64) curl_arch="aarch64" ;;
      *) echo "❌ Nicht unterstützte Architektur: $CURL_ARCH" >&2; exit 1 ;;
    esac
    DEST_DIR="$JDK_DIR/jdk-17"
    if [[ ! -x "$DEST_DIR/bin/java" ]]; then
      DL_URL=""
      # 1) API query (latest GA)
      API_JSON=$(curl -fsL --connect-timeout 10 \
        "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=$curl_arch&image_type=jdk&os=$OS_ARCH" 2>/dev/null || true)
      if [[ -n "$API_JSON" ]]; then
        DL_URL=$(python3 - "$API_JSON" <<'PY' 2>/dev/null || true
import sys, json
try:
    data = json.loads(sys.argv[1])
    if data:
        print(data[0]["binary"]["package"]["link"])
except Exception:
    pass
PY
)
      fi
      # 2) direkter binary-redirect (kein JSON nötig)
      if [[ -z "$DL_URL" ]]; then
        BIN_URL="https://api.adoptium.net/v3/binary/latest/17/ga/$OS_ARCH/$curl_arch/jdk/hotspot/normal/eclipse"
        if curl -fL -sI --connect-timeout 10 "$BIN_URL" 2>/dev/null | grep -q "200\|302\|Location"; then
          DL_URL="$BIN_URL"
        fi
      fi
      # 3) GitHub-Adoptium-Release (falls api.adoptium.net blockiert ist)
      if [[ -z "$DL_URL" ]]; then
        echo "==> Fallback: GitHub-Adoptium-Release abfragen ..."
        GH_JSON_FILE="$(mktemp)"
        trap 'rm -f "$GH_JSON_FILE"' EXIT
        if curl -fsL --connect-timeout 12 \
          "https://api.github.com/repos/adoptium/temurin17-binaries/releases/latest" \
          -o "$GH_JSON_FILE" 2>/dev/null; then
          DL_URL=$(OS_ARCH="$OS_ARCH" CURL_ARCH="$curl_arch" GH_JSON_FILE="$GH_JSON_FILE" python3 - <<'PY' 2>/dev/null || true
import json, os
data = json.load(open(os.environ["GH_JSON_FILE"]))
needle = "jdk_{}_{}_hotspot".format(os.environ["CURL_ARCH"], os.environ["OS_ARCH"])
for asset in data.get("assets", []):
    name = asset.get("name", "")
    if needle in name and name.endswith(".tar.gz") and "debugimage" not in name and "testimage" not in name:
        print(asset["browser_download_url"])
        break
PY
)
        fi
        rm -f "$GH_JSON_FILE"
        trap - EXIT
      fi
      if [[ -z "$DL_URL" ]]; then
        echo "❌ JDK-Download konnte nicht aufgelöst werden. Bitte manuell installieren:" >&2
        echo "   sudo apt-get install openjdk-17-jdk  ODER  Temurin JDK 17 herunterladen und JAVA_HOME setzen." >&2
        exit 1
      fi
      DL_NAME=$(basename "$DL_URL" | sed 's/%2B/+/g')
      echo "==> Download $DL_NAME ..."
      curl -fL --connect-timeout 15 -o "$JDK_DIR/$DL_NAME" "$DL_URL"
      rm -rf "$DEST_DIR"
      mkdir -p "$JDK_DIR/extract"
      tar -xzf "$JDK_DIR/$DL_NAME" -C "$JDK_DIR/extract"
      EXTRACT_DIR=$(find "$JDK_DIR/extract" -maxdepth 1 -type d -name 'jdk-17*' | head -1)
      [[ -n "$EXTRACT_DIR" ]] || EXTRACT_DIR="$JDK_DIR/extract/jdk-17"
      mv "$EXTRACT_DIR" "$DEST_DIR"
      rm -rf "$JDK_DIR/extract"
      rm -f "$JDK_DIR/$DL_NAME"
      chmod +x "$DEST_DIR/bin/java"
    fi
    JAVA_HOME="$DEST_DIR"
    echo "✔ JDK installiert: $JAVA_HOME"
  fi
fi

"$JAVA_HOME/bin/java" -version

# Persistente Umgebung für die Shell
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<'EOF'
# SecureGuard Enterprise – lokale Build-Umgebung
# eingebunden via: source ~/.secureguard-env.sh
export JAVA_HOME="__JAVA_HOME__"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_SDK_HOME="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
python3 - "$ENV_FILE" "$JAVA_HOME" <<'PY'
import sys
path, java = sys.argv[1], sys.argv[2]
data = open(path).read().replace("__JAVA_HOME__", java)
open(path, "w").write(data)
PY
echo "✔ Umgebung geschrieben: $ENV_FILE"
echo "   Einbinden:  source $ENV_FILE"
