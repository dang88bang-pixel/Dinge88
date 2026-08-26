#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – Alle lokal ausführbaren Tests
#   1. Backend-Pytest (API/Contract/Migration/Backup-Restore)
#   2. Gradle-Unit-Tests
#   3. PlatformIO-Firmware-Build (ohne Flash)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
BACKEND_VENV="${BACKEND_VENV_DIR:-$HOME/.secureguard-backend-venv}"
PIO_VENV="${PIO_VENV_DIR:-$HOME/.secureguard-platformio}"
FAIL=0

echo "==> 1/3 Backend-Tests"
if [[ -x "$BACKEND_VENV/bin/python" && -d backend/tests ]]; then
  (cd backend && "$BACKEND_VENV/bin/python" -m pytest -q) || FAIL=1
else
  echo "⚠ Backend-venv fehlt – ./scripts/setup-backend.sh"
fi

echo "==> 2/3 Gradle-Unit-Tests"
if [[ -x "./gradlew" ]]; then
  if [[ -n "${ANDROID_HOME:-}" || -f local.properties ]]; then
    ./gradlew :app:testDebugUnitTest --no-daemon || FAIL=1
  else
    echo "⚠ ANDROID_HOME/local.properties fehlt – ./scripts/install-android-sdk.sh"
  fi
else
  echo "⚠ gradlew fehlt"
fi

echo "==> 3/3 PlatformIO-Firmware-Build"
if [[ -x "$PIO_VENV/bin/pio" ]]; then
  (cd firmware/secureguard_esp32 && "$PIO_VENV/bin/pio" run --target build) || FAIL=1
elif command -v pio >/dev/null 2>&1; then
  (cd firmware/secureguard_esp32 && pio run --target build) || FAIL=1
else
  echo "⚠ pio fehlt – ./scripts/setup-platformio.sh"
fi

[[ "$FAIL" -eq 0 ]] && echo "✅ Alle Tests bestanden." || echo "❌ Mindestens ein Test fehlgeschlagen."
exit "$FAIL"
