#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – Master-Setup der lokalen Build-Umgebung
#   --all        installiert alle lokal installierbaren Komponenten
#   --jdk        nur JDK 17
#   --sdk        nur Android SDK
#   --pio        nur PlatformIO
#   --backend    nur Backend
#   --validate   nur Validierung
#
# Nutzung:
#   ./scripts/setup-local-env.sh --all
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

DO_JDK=0
DO_SDK=0
DO_PIO=0
DO_BACKEND=0
DO_VALIDATE=0

[[ "$#" -eq 0 ]] && DO_JDK=1 DO_SDK=1 DO_PIO=1 DO_BACKEND=1 DO_VALIDATE=1
for arg in "$@"; do
  case "$arg" in
    --all) DO_JDK=1; DO_SDK=1; DO_PIO=1; DO_BACKEND=1; DO_VALIDATE=1 ;;
    --jdk) DO_JDK=1 ;;
    --sdk) DO_SDK=1 ;;
    --pio) DO_PIO=1 ;;
    --backend) DO_BACKEND=1 ;;
    --validate) DO_VALIDATE=1 ;;
    *) echo "❌ Unbekannte Option: $arg" >&2; exit 1 ;;
  esac
done

# JDK zuerst, damit das SDK/Backend sauber laufen kann
[[ "$DO_JDK" -eq 1 ]] && scripts/install-jdk17.sh
[[ "$DO_SDK" -eq 1 ]] && scripts/install-android-sdk.sh
[[ "$DO_PIO" -eq 1 ]] && scripts/setup-platformio.sh
[[ "$DO_BACKEND" -eq 1 ]] && scripts/setup-backend.sh
[[ "$DO_VALIDATE" -eq 1 ]] && scripts/validate-local-env.sh

echo ""
echo "✅ Setup-Skripte abgeschlossen."
echo "  In der aktuellen Shell ggf. neu einlesen:  source ~/.secureguard-env.sh"
