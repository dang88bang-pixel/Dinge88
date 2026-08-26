#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – PlatformIO Setup
#   - installiert Python >= 3.9 (falls nötig, via system python)
#   - installiert PlatformIO CLI (pip user / pipx / venv)
#   - installiert ESP32-Platform
#   - installiert LoRa- und PubSubClient-Bibliotheken
#   - prüft Build der Firmware (ohne Flash)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIRMWARE_DIR="$REPO_ROOT/firmware/secureguard_esp32"
VENV_DIR="${PIO_VENV_DIR:-$HOME/.secureguard-platformio}"
ENV_FILE="$HOME/.secureguard-env.sh"

echo "==> PlatformIO Setup"

# --- Python ---------------------------------------------------------------
if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 fehlt. Bitte installieren (z. B. apt install python3)." >&2
  exit 1
fi
echo "✔ Python: $(python3 --version)"

# --- PlatformIO CLI -------------------------------------------------------
if command -v pio >/dev/null 2>&1; then
  PIO_BIN="$(command -v pio)"
elif [[ -x "$VENV_DIR/bin/pio" ]]; then
  PIO_BIN="$VENV_DIR/bin/pio"
else
  echo "==> Installiere PlatformIO in $VENV_DIR ..."
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/pip" install --upgrade pip
  "$VENV_DIR/bin/pip" install platformio
  PIO_BIN="$VENV_DIR/bin/pio"
fi

# Make executable if it's a shim/script
chmod +x "$PIO_BIN" 2>/dev/null || true
echo "✔ PlatformIO: $PIO_BIN"

# persistieren
if [[ -f "$ENV_FILE" ]] && ! grep -q "$VENV_DIR/bin" "$ENV_FILE"; then
  cat >> "$ENV_FILE" <<EOF

export PATH="$VENV_DIR/bin:\$PATH"
EOF
fi

# --- Platform / Libraries -------------------------------------------------
echo "==> Installiere ESP32-Platform ..."
"$PIO_BIN" pkg install --global --platform espressif32 || true

echo "==> Installiere LoRa- und PubSubClient-Bibliotheken ..."
# Reproduzierbare IDs; bei Bedarf werden diese per platformio.ini aufgelöst.
"$PIO_BIN" pkg install --global --library "sandeepmistry/arduino-LoRa@^0.8.0" || true
"$PIO_BIN" pkg install --global --library "knolleary/PubSubClient@^2.8" || true

# --- Firmware-Build -------------------------------------------------------
if [[ -f "$FIRMWARE_DIR/platformio.ini" ]]; then
  echo "==> Baue ESP32-Firmware (ohne Flash)..."
  (cd "$FIRMWARE_DIR" && "$PIO_BIN" run --target build || echo "⚠ Firmware-Build fehlgeschlagen – ggf. Hardware/Board-Config anpassen.")
else
  echo "⚠ platformio.ini fehlt in $FIRMWARE_DIR – überspringe Build."
fi

echo "✔ PlatformIO Setup abgeschlossen."
echo "   Nutzung: source $ENV_FILE && cd $FIRMWARE_DIR && pio run --target upload"
