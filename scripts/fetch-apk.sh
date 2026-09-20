#!/usr/bin/env bash
# =====================================================================
# Holt die zuletzt von der CI gebaute APK aus dem Delivery-Branch
# (apk-delivery-release | apk-delivery-debug) und legt sie unter releases/ ab:
#
#   releases/SecureGuard-<versionName>-<release|debug>.apk
#   releases/SHA256SUMS.txt      (Prüfsummen aller APKs in releases/)
#   releases/BUILD-INFO-<type>.txt
#
# Verwendung:
#   scripts/fetch-apk.sh [release|debug] [remote]
#
# Voraussetzung: Lesezugriff auf das Git-Remote (Standard: origin).
# Die Delivery-Branches werden vom Gradle-Task `publishApkDelivery`
# (app/build.gradle.kts) nach jedem CI-Build force-gepusht.
# =====================================================================
set -euo pipefail

TYPE="${1:-release}"
REMOTE="${2:-origin}"
case "$TYPE" in
  release|debug) ;;
  *) echo "Nutzung: $0 [release|debug] [remote]" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BRANCH="apk-delivery-$TYPE"
echo "→ Hole $REMOTE/$BRANCH …"
git fetch --quiet --force "$REMOTE" "+refs/heads/$BRANCH:refs/remotes/$REMOTE/$BRANCH"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git archive --format=tar "refs/remotes/$REMOTE/$BRANCH" apk-dist | tar -x -C "$TMP"

DIST="$TMP/apk-dist"
# Gesplittete APKs (> 90 MB) wieder zusammensetzen
for part in "$DIST"/*.apk.part-00; do
  [ -e "$part" ] || continue
  base="${part%.part-00}"
  cat "${base}".part-* > "$base"
  rm -f "${base}".part-*
done

APK="$(find "$DIST" -maxdepth 1 -type f -name '*.apk' | head -n 1)"
[ -n "$APK" ] || { echo "Keine APK in $BRANCH gefunden." >&2; exit 1; }

# Prüfsumme gegen SHA256SUMS.txt des Delivery-Branches
if [ -f "$DIST/SHA256SUMS.txt" ] && grep -q "$(basename "$APK")" "$DIST/SHA256SUMS.txt"; then
  (cd "$DIST" && grep "$(basename "$APK")" SHA256SUMS.txt | sha256sum -c -)
fi

VERSION="$(grep -E '^versionName' "$DIST/BUILD-INFO.txt" 2>/dev/null | awk -F': *' '{print $2}' | tr -d '[:space:]')"
VERSION="${VERSION:-unknown}"
TARGET_DIR="$ROOT/releases"
mkdir -p "$TARGET_DIR"
TARGET="$TARGET_DIR/SecureGuard-${VERSION}-${TYPE}.apk"

cp -f "$APK" "$TARGET"
[ -f "$DIST/BUILD-INFO.txt" ] && cp -f "$DIST/BUILD-INFO.txt" "$TARGET_DIR/BUILD-INFO-${TYPE}.txt"

(cd "$TARGET_DIR" && sha256sum -- *.apk > SHA256SUMS.txt)

echo "✅ $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "   Build-Info:"
sed 's/^/     /' "$DIST/BUILD-INFO.txt" 2>/dev/null | head -12 || true
