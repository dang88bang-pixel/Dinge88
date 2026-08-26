#!/usr/bin/env bash
# Lädt ESP32-Platform + Bibliotheken als Archive für Offline-Installation.
# Läuft auf dem Rechner MIT Internetzugang.
#
# Voraussetzungen: Python 3 + pip  ODER  bereits installiertes `pio`
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

OUT_DIR="${OFFLINE_REPO}/platformio"
PLAT_DIR="${OUT_DIR}/platforms"
LIB_DIR="${OUT_DIR}/libs"
mkdir_p "$PLAT_DIR" "$LIB_DIR"

ensure_pio() {
  if command -v pio >/dev/null 2>&1; then
    return 0
  fi
  if command -v platformio >/dev/null 2>&1; then
    alias pio=platformio 2>/dev/null || true
    # shell-Funktion falls alias nicht greift
    pio() { platformio "$@"; }
    export -f pio
    return 0
  fi
  log "PlatformIO CLI nicht gefunden – installiere per pip --user …"
  need_cmd python3
  python3 -m pip install --user -U platformio
  export PATH="${HOME}/.local/bin:${PATH}"
  command -v pio >/dev/null 2>&1 || die "pio nach pip-Install nicht im PATH"
}

ensure_pio
ok "PlatformIO: $(pio --version 2>/dev/null || true)"

log "ESP32-Plattform herunterladen: ${PIO_PLATFORM}"
# pio pkg download legt Archive in --output-dir ab (PIO Core ≥ 6)
if pio pkg download --help 2>&1 | grep -q -- '--output-dir\|--output-path'; then
  OUT_FLAG="--output-dir"
  if pio pkg download --help 2>&1 | grep -q -- '--output-path'; then
    OUT_FLAG="--output-path"
  fi
  pio pkg download --platform "${PIO_PLATFORM}" "${OUT_FLAG}" "${PLAT_DIR}" || \
    pio pkg download -p "${PIO_PLATFORM}" "${OUT_FLAG}" "${PLAT_DIR}"
else
  # Fallback: normales Install → aus Cache kopieren
  warn "pio pkg download nicht verfügbar – nutze Cache-Spiegelung"
  pio pkg install --platform "${PIO_PLATFORM}" || pio platform install espressif32
  PIO_HOME="${PLATFORMIO_CORE_DIR:-${HOME}/.platformio}"
  if [[ -d "${PIO_HOME}/platforms" ]]; then
    cp -a "${PIO_HOME}/platforms/." "${PLAT_DIR}/"
  fi
  if [[ -d "${PIO_HOME}/packages" ]]; then
    mkdir_p "${OUT_DIR}/packages"
    cp -a "${PIO_HOME}/packages/." "${OUT_DIR}/packages/"
  fi
fi

log "Bibliotheken herunterladen…"
for lib in "${PIO_LIBRARIES[@]}"; do
  log "  → $lib"
  if pio pkg download --help 2>&1 | grep -q -- '--output-dir\|--output-path'; then
    OUT_FLAG="--output-dir"
    if pio pkg download --help 2>&1 | grep -q -- '--output-path'; then
      OUT_FLAG="--output-path"
    fi
    pio pkg download --library "$lib" "${OUT_FLAG}" "${LIB_DIR}" || \
      pio pkg download -l "$lib" "${OUT_FLAG}" "${LIB_DIR}" || \
      warn "Download fehlgeschlagen für $lib – bitte manuell prüfen"
  else
    pio pkg install --library "$lib" || pio lib install "$lib" || true
  fi
done

# Zusätzlich: gesamten relevanten Cache spiegeln (robuster Offline-Weg)
PIO_HOME="${PLATFORMIO_CORE_DIR:-${HOME}/.platformio}"
if [[ -d "${PIO_HOME}" ]]; then
  log "PlatformIO-Cache spiegeln (${PIO_HOME})…"
  mkdir_p "${OUT_DIR}/cache"
  for sub in platforms packages lib; do
    if [[ -d "${PIO_HOME}/${sub}" ]]; then
      mkdir_p "${OUT_DIR}/cache/${sub}"
      rsync -a --delete "${PIO_HOME}/${sub}/" "${OUT_DIR}/cache/${sub}/" 2>/dev/null \
        || cp -a "${PIO_HOME}/${sub}/." "${OUT_DIR}/cache/${sub}/"
      ok "Cache: ${sub}"
    fi
  done
fi

# Manifest der heruntergeladenen Archive
MANIFEST="${OUT_DIR}/MANIFEST.txt"
write_manifest_header "$MANIFEST"
while IFS= read -r -d '' f; do
  append_manifest "$MANIFEST" "$f" "$OUT_DIR"
done < <(find "$OUT_DIR" -type f \( -name '*.tar.gz' -o -name '*.zip' -o -name '*.tar.xz' \) -print0 2>/dev/null)

cat > "${OUT_DIR}/platformio.env" <<EOF
PIO_PLATFORM=${PIO_PLATFORM}
PIO_BOARD=${PIO_BOARD}
PIO_LIBRARIES=${PIO_LIBRARIES[*]}
EOF

ok "PlatformIO-Pakete unter: $OUT_DIR"
du -sh "$OUT_DIR" 2>/dev/null | awk '{print "  Größe: "$1}' || true
