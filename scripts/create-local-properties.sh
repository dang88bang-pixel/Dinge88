#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – local.properties erzeugen
#   - kopiert local.properties.example -> local.properties
#   - setzt sdk.dir/ANDROID_HOME aus installiertem SDK
#   - setzt keine Secrets (müssen manuell/git-secret hinterlegt werden)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$REPO_ROOT/local.properties"
EXAMPLE="$REPO_ROOT/local.properties.example"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [[ -f "$PROPS" ]]; then
  echo "✔ local.properties existiert bereits – überschreibe NICHT, ergänze nur SDK-Pfade."
else
  cp "$EXAMPLE" "$PROPS"
  echo "✔ local.properties aus Beispiel erzeugt."
fi

SDK_ESC=$(printf '%s' "$ANDROID_HOME" | sed 's/\\/\\\\/g; s/:/\\:/g')
for key in sdk.dir ANDROID_HOME ANDROID_SDK_ROOT; do
  if grep -q "^$key=" "$PROPS"; then
    sed -i "s|^$key=.*|$key=$SDK_ESC|" "$PROPS"
  else
    printf '%s=%s\n' "$key" "$SDK_ESC" >> "$PROPS"
  fi
done

echo "✔ $REPO_ROOT/local.properties"
echo ""
echo "WICHTIG: API-Keys/Broker-URLs bitte manuell in local.properties setzen."
echo "Datei ist in .gitignore – sie wird nie eingecheckt."
