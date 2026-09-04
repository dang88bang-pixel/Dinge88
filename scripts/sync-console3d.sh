#!/usr/bin/env bash
# Baut das 3D Operations Center und spiegelt das Bundle in die App-Assets.
#
# Die Android-App liefert die Konsole offline über einen virtuellen
# https-Origin aus (siehe presentation/ui/ops/OpsCenter3DScreen.kt).
# Deshalb muss das gebaute Bundle mitversioniert werden.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/console3d"
DST="$ROOT/app/src/main/assets/console3d"

echo "==> Build console3d"
cd "$SRC"
if [ ! -d node_modules ]; then
  npm ci || npm install
fi
npm run build

echo "==> Sync -> app/src/main/assets/console3d"
rm -rf "$DST"
mkdir -p "$DST"
cp -R "$SRC/dist/." "$DST/"

SIZE=$(du -sh "$DST" | cut -f1)
echo "==> Fertig. Bundle-Groesse: $SIZE"
echo "    Naechster Schritt: ./gradlew :app:assembleDebug"
