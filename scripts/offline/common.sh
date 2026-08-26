#!/usr/bin/env bash
# Gemeinsame Hilfsfunktionen für SecureGuard Offline-Skripte.
# shellcheck disable=SC2034
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Standard-Transfer-Ordner (USB-Stick / Netzlaufwerk)
OFFLINE_REPO="${OFFLINE_REPO:-${REPO_ROOT}/offline_repo}"

# Toolchain-Versionen (müssen zu CI / app/build.gradle.kts passen)
JAVA_VERSION="${JAVA_VERSION:-17}"
JAVA_MAJOR="${JAVA_VERSION}"
COMPILE_SDK="${COMPILE_SDK:-android-35}"
BUILD_TOOLS="${BUILD_TOOLS:-35.0.0}"
# compileSdk in app/build.gradle.kts ist 34, targetSdk 35 – wir spiegeln beides
ANDROID_PLATFORMS="${ANDROID_PLATFORMS:-platforms;android-34 platforms;android-35}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-build-tools;34.0.0 build-tools;35.0.0}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-11076708}"
GRADLE_VERSION="${GRADLE_VERSION:-8.9}"

# PlatformIO / ESP32
PIO_PLATFORM="${PIO_PLATFORM:-platformio/espressif32}"
PIO_BOARD="${PIO_BOARD:-esp32dev}"
# Bibliotheken aus der Firmware (siehe firmware/secureguard_esp32)
PIO_LIBRARIES=(
  "knolleary/PubSubClient@^2.8"
  "sandeepmistry/LoRa@^0.8.0"
  "bblanchon/ArduinoJson@^7.0.0"
)

# Farben (nur wenn TTY)
if [[ -t 1 ]]; then
  C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'
  C_CYAN=$'\033[36m'; C_BOLD=$'\033[1m'; C_RESET=$'\033[0m'
else
  C_GREEN=; C_YELLOW=; C_RED=; C_CYAN=; C_BOLD=; C_RESET=
fi

log()  { printf '%s▶%s %s\n' "${C_CYAN}" "${C_RESET}" "$*"; }
ok()   { printf '%s✔%s %s\n' "${C_GREEN}" "${C_RESET}" "$*"; }
warn() { printf '%s⚠%s %s\n' "${C_YELLOW}" "${C_RESET}" "$*"; }
err()  { printf '%s✖%s %s\n' "${C_RED}" "${C_RESET}" "$*" >&2; }
die()  { err "$*"; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Befehl nicht gefunden: $1"
}

mkdir_p() {
  mkdir -p "$1"
}

# Portable Download (curl bevorzugt, sonst wget)
download() {
  local url="$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  if [[ -f "$dest" ]]; then
    ok "Bereits vorhanden: $(basename "$dest")"
    return 0
  fi
  log "Download: $url"
  if command -v curl >/dev/null 2>&1; then
    curl -fSL --retry 3 --retry-delay 2 -o "$dest.partial" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$dest.partial" "$url"
  else
    die "Weder curl noch wget verfügbar"
  fi
  mv "$dest.partial" "$dest"
  ok "Gespeichert: $dest"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "unknown"
  fi
}

write_manifest_header() {
  local manifest="$1"
  cat > "$manifest" <<EOF
# SecureGuard Enterprise – Offline-Repository Manifest
# Erzeugt: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# Host:    $(hostname 2>/dev/null || echo unknown)
# Repo:    ${REPO_ROOT}
#
# Format:  SHA256  RELATIVE_PATH
EOF
}

append_manifest() {
  local manifest="$1" file="$2" base="$3"
  local rel hash
  rel="${file#"$base"/}"
  hash="$(sha256_file "$file")"
  printf '%s  %s\n' "$hash" "$rel" >> "$manifest"
}

detect_os() {
  case "$(uname -s)" in
    Linux*)  echo "linux" ;;
    Darwin*) echo "mac" ;;
    MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
    *) echo "linux" ;;
  esac
}

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "x64" ;;
    aarch64|arm64) echo "aarch64" ;;
    armv7*|armv6*) echo "arm" ;;
    *) echo "x64" ;;
  esac
}

# Pfad-Helfer: file:// URL für platformio.ini (POSIX + Windows-tauglich)
to_file_uri() {
  local p="$1"
  # Absolut machen
  if [[ "$p" != /* && "$p" != [A-Za-z]:* ]]; then
    p="$(cd "$(dirname "$p")" && pwd)/$(basename "$p")"
  fi
  # Windows drive letter
  if [[ "$p" =~ ^[A-Za-z]: ]]; then
    local drive="${p:0:1}"
    local rest="${p:2}"
    rest="${rest//\\//}"
    printf 'file:///%s:%s' "$drive" "$rest"
  else
    printf 'file://%s' "$p"
  fi
}
