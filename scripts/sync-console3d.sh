#!/usr/bin/env bash
#
# sync-console3d.sh
#
# Baut das 3D Operations Center (console3d/) mit npm/vite und kopiert das
# erzeugte `dist/`-Bundle in die lokalen Android-Assets:
#
#   app/src/main/assets/console3d/
#
# Ablauf (entspricht Spec §32):
#   1. npm install
#   2. npm run build
#   3. ggf. vorhandenes dist/ löschen (vite macht das selbst, hier defensiv)
#   4. Android-Assets-Ziel löschen
#   5. dist/ nach Android-Assets kopieren
#
# Verwendung:
#   scripts/sync-console3d.sh            (nur bauen + kopieren)
#   scripts/sync-console3d.sh --build    (explizit identisch)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONSOLE_DIR="$ROOT/console3d"
DIST_DIR="$CONSOLE_DIR/dist"
ASSETS_DIR="$ROOT/app/src/main/assets/console3d"

echo "▶ [1/5] npm install → $CONSOLE_DIR"
(cd "$CONSOLE_DIR" && npm install)

echo "▶ [2/5] npm run build"
(cd "$CONSOLE_DIR" && npm run build)

if [ ! -f "$DIST_DIR/index.html" ]; then
  echo "✖ Build ohne dist/index.html – Abbruch." >&2
  exit 1
fi

echo "▶ [3/5] dist bereinigen (defensiv)"
rm -rf "$DIST_DIR/.vite"

echo "▶ [4/5] Android-Assets-Ziel löschen: $ASSETS_DIR"
rm -rf "$ASSETS_DIR"

echo "▶ [5/5] dist → Android-Assets kopieren"
mkdir -p "$ASSETS_DIR"
cp -R "$DIST_DIR/." "$ASSETS_DIR/"

TOTAL_BYTES=$(du -sb "$ASSETS_DIR" | cut -f1)
GZIP_BYTES=$(tar -C "$ASSETS_DIR" -czf - . | wc -c)
echo "✔ Fertig. Ziel: $ASSETS_DIR"
echo "  Bundle-Größe : $(numfmt --to=iec "$TOTAL_BYTES" 2>/dev/null || echo "${TOTAL_BYTES} bytes")"
echo "  gzip-Größe   : $(numfmt --to=iec "$GZIP_BYTES" 2>/dev/null || echo "${GZIP_BYTES} bytes")"
