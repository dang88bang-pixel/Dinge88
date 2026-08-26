#!/usr/bin/env bash
# Installiert PlatformIO-Pakete aus dem Offline-Repo und schreibt
# firmware/secureguard_esp32/platformio.ini auf lokale Archive um.
# Läuft auf dem Zielrechner OHNE Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SRC="${OFFLINE_REPO}/platformio"
[[ -d "$SRC" ]] || die "Kein PlatformIO-Ordner im Offline-Repo: $SRC"

FIRMWARE_DIR="${REPO_ROOT}/firmware/secureguard_esp32"
PIO_INI="${FIRMWARE_DIR}/platformio.ini"
PIO_HOME="${PLATFORMIO_CORE_DIR:-${HOME}/.platformio}"

# 1) Cache 1:1 spiegeln (zuverlässigster Offline-Weg)
if [[ -d "${SRC}/cache" ]]; then
  log "PlatformIO-Cache → ${PIO_HOME}"
  mkdir_p "$PIO_HOME"
  for sub in platforms packages lib; do
    if [[ -d "${SRC}/cache/${sub}" ]]; then
      mkdir_p "${PIO_HOME}/${sub}"
      rsync -a "${SRC}/cache/${sub}/" "${PIO_HOME}/${sub}/" 2>/dev/null \
        || cp -a "${SRC}/cache/${sub}/." "${PIO_HOME}/${sub}/"
      ok "Cache übernommen: ${sub}"
    fi
  done
fi

# 2) Einzelne Archive per pio pkg install (falls CLI vorhanden)
ensure_pio_offline() {
  if command -v pio >/dev/null 2>&1 || command -v platformio >/dev/null 2>&1; then
    return 0
  fi
  # Offline: pip wheel aus Repo? – meist nicht vorhanden
  warn "pio nicht installiert. Installiere PlatformIO auf einem Online-Rechner"
  warn "  (pip install platformio) und kopiere ~/.local + ~/.platformio mit,"
  warn "  ODER installiere per USB ein vorbereitetes pio-Standalone."
  return 1
}

install_archives() {
  local dir="$1" kind="$2"  # kind=platform|library
  [[ -d "$dir" ]] || return 0
  local f
  shopt -s nullglob
  for f in "$dir"/*.{tar.gz,zip,tar.xz}; do
    [[ -f "$f" ]] || continue
    log "Installiere ${kind}: $(basename "$f")"
    if [[ "$kind" == "platform" ]]; then
      pio pkg install --platform "$f" 2>/dev/null \
        || pio platform install "$f" 2>/dev/null \
        || warn "Konnte Platform nicht installieren: $f"
    else
      pio pkg install --library "$f" 2>/dev/null \
        || pio lib install "$f" 2>/dev/null \
        || warn "Konnte Library nicht installieren: $f"
    fi
  done
  shopt -u nullglob
}

if ensure_pio_offline; then
  if ! command -v pio >/dev/null 2>&1 && command -v platformio >/dev/null 2>&1; then
    pio() { platformio "$@"; }
    export -f pio
  fi
  install_archives "${SRC}/platforms" platform
  install_archives "${SRC}/libs" library
fi

# 3) platformio.ini mit file://-Pfaden schreiben (falls Archive existieren)
mkdir_p "$FIRMWARE_DIR"

# Bestehende Archive auflisten (ohne mapfile → bash-3/macOS-tauglich)
PLAT_ARCHIVE=""
LIB_ARCHIVES=""
if [[ -d "${SRC}/platforms" ]]; then
  PLAT_ARCHIVE="$(find "${SRC}/platforms" -type f \( -name '*.tar.gz' -o -name '*.zip' \) 2>/dev/null | sort | head -1 || true)"
fi
if [[ -d "${SRC}/libs" ]]; then
  LIB_ARCHIVES="$(find "${SRC}/libs" -type f \( -name '*.tar.gz' -o -name '*.zip' \) 2>/dev/null | sort || true)"
fi

PLATFORM_LINE="espressif32"
if [[ -n "$PLAT_ARCHIVE" ]]; then
  PLATFORM_LINE="$(to_file_uri "$PLAT_ARCHIVE")"
fi

LIB_LINES=""
if [[ -n "$LIB_ARCHIVES" ]]; then
  while IFS= read -r a; do
    [[ -z "$a" ]] && continue
    LIB_LINES="${LIB_LINES}    $(to_file_uri "$a")"$'\n'
  done <<< "$LIB_ARCHIVES"
else
  # Registry-Namen als Fallback (wenn Cache gespiegelt wurde, löst PIO lokal auf)
  LIB_LINES="    knolleary/PubSubClient@^2.8
    sandeepmistry/LoRa@^0.8.0
    bblanchon/ArduinoJson@^7.0.0
"
fi

# platformio.ini nur überschreiben, wenn wir offline-tauglich konfigurieren
# Backup behalten
if [[ -f "$PIO_INI" ]]; then
  cp -n "$PIO_INI" "${PIO_INI}.bak" 2>/dev/null || true
fi

cat > "$PIO_INI" <<EOF
; SecureGuard Enterprise – ESP32 Gateway
; Generiert/aktualisiert von scripts/offline/install-platformio.sh
; Online-Default: platform = espressif32, lib_deps = Registry-Namen
; Offline:        platform/lib_deps = file://… Archive aus offline_repo/

[platformio]
default_envs = esp32dev
src_dir = .

[env:esp32dev]
platform = ${PLATFORM_LINE}
board = esp32dev
framework = arduino
monitor_speed = 115200

build_flags =
    -DCORE_DEBUG_LEVEL=1
    -DSECUREGUARD_FIRMWARE=1

; Hauptsketch liegt als .ino im Projektroot dieses Ordners
build_src_filter =
    +<*>
    -<**/.pio/**>

lib_deps =
${LIB_LINES}
; Optional: lokalen lib_extra_dirs-Pfad freigeben
; lib_extra_dirs = ${SRC}/libs

upload_speed = 921600
board_build.partitions = default.csv
EOF

ok "platformio.ini geschrieben: $PIO_INI"
if [[ -n "$PLAT_ARCHIVE" || -d "${SRC}/cache/platforms" ]]; then
  ok "Offline-Platform-Quellen vorhanden"
else
  warn "Keine Platform-Archive – Build braucht ggf. Netzwerk oder Cache-Kopie"
fi

log "Firmware bauen (sobald pio verfügbar):"
log "  cd ${FIRMWARE_DIR} && pio run"
