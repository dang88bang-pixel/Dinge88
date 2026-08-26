#!/usr/bin/env bash
# =============================================================================
# SecureGuard Enterprise – Release-Keystore erzeugen + CI-Secrets vorbereiten
# =============================================================================
# Erzeugt ein RSA-2048 Keystore, speichert es unter app/secureguard-keystore.jks
# (gitignored) und gibt die Base64-Zeile + gh-secret-Befehle aus.
#
# Nutzung:
#   ./scripts/create-release-keystore.sh
#   ./scripts/create-release-keystore.sh --repo OWNER/NAME
#   KEYSTORE_PASSWORD=geheim KEY_ALIAS=secureguard ./scripts/create-release-keystore.sh
#
# Danach lokal:
#   export KEYSTORE_PASSWORD=... KEY_ALIAS=secureguard KEY_PASSWORD=...
#   ./gradlew :app:assembleRelease
#
# CI (GitHub):
#   gh secret set KEYSTORE_BASE64 < keystore.b64
#   gh secret set KEYSTORE_PASSWORD
#   gh secret set KEY_ALIAS
#   gh secret set KEY_PASSWORD
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REPO=""
for arg in "$@"; do
  case "$arg" in
    --repo=*) REPO="${arg#*=}" ;;
    --repo) shift || true ;;
    --help|-h)
      sed -n '2,25p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
  esac
done

need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ fehlt: $1"; exit 1; }; }
need keytool

OUT_JKS="${ROOT}/app/secureguard-keystore.jks"
OUT_B64="${ROOT}/app/secureguard-keystore.b64"
ALIAS="${KEY_ALIAS:-secureguard}"
STORE_PASS="${KEYSTORE_PASSWORD:-}"
KEY_PASS="${KEY_PASSWORD:-}"

if [[ -z "$STORE_PASS" ]]; then
  # Zufälliges Passwort (nur einmal anzeigen / speichern)
  if command -v openssl >/dev/null 2>&1; then
    STORE_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
  else
    STORE_PASS="$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)"
  fi
  echo "→ Generiertes KEYSTORE_PASSWORD: $STORE_PASS"
  echo "  (bitte sicher verwahren – wird nicht erneut angezeigt)"
fi
KEY_PASS="${KEY_PASS:-$STORE_PASS}"

if [[ -f "$OUT_JKS" ]]; then
  echo "⚠  Keystore existiert bereits: $OUT_JKS"
  echo "   Löschen falls neu erzeugen gewünscht, oder Secrets nur neu setzen."
else
  echo "→ Erzeuge Keystore: $OUT_JKS"
  keytool -genkeypair -v \
    -keystore "$OUT_JKS" \
    -storetype JKS \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=SecureGuard Enterprise, OU=Mobile, O=SecureGuard, L=Berlin, ST=BE, C=DE"
  echo "✔ Keystore erzeugt"
fi

# Base64 (einzeilig, CI-tauglich)
if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w0 "$OUT_JKS" > "$OUT_B64"
else
  base64 "$OUT_JKS" | tr -d '\n' > "$OUT_B64"
fi
echo "✔ Base64: $OUT_B64 ($(wc -c < "$OUT_B64") bytes)"

# local.properties Hinweis (nicht committen)
LP="${ROOT}/local.properties"
if [[ -f "$LP" ]]; then
  if ! grep -q '^KEYSTORE_PASSWORD=' "$LP" 2>/dev/null; then
    {
      echo ""
      echo "# Release signing (von create-release-keystore.sh)"
      echo "KEYSTORE_PASSWORD=$STORE_PASS"
      echo "KEY_ALIAS=$ALIAS"
      echo "KEY_PASSWORD=$KEY_PASS"
    } >> "$LP"
    echo "✔ Einträge in local.properties ergänzt"
  fi
fi

echo ""
echo "=== Lokal bauen ==="
echo "  export KEYSTORE_PASSWORD='$STORE_PASS'"
echo "  export KEY_ALIAS='$ALIAS'"
echo "  export KEY_PASSWORD='$KEY_PASS'"
echo "  ./gradlew :app:assembleRelease"
echo ""
echo "=== GitHub Secrets ==="
if [[ -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
  echo "→ Setze Secrets für $REPO …"
  gh secret set KEYSTORE_BASE64 --repo "$REPO" < "$OUT_B64"
  gh secret set KEYSTORE_PASSWORD --repo "$REPO" <<< "$STORE_PASS"
  gh secret set KEY_ALIAS --repo "$REPO" <<< "$ALIAS"
  gh secret set KEY_PASSWORD --repo "$REPO" <<< "$KEY_PASS"
  echo "✔ Secrets gesetzt"
else
  echo "  gh secret set KEYSTORE_BASE64 --repo OWNER/NAME < app/secureguard-keystore.b64"
  echo "  gh secret set KEYSTORE_PASSWORD --repo OWNER/NAME"
  echo "  gh secret set KEY_ALIAS --repo OWNER/NAME          # Wert: $ALIAS"
  echo "  gh secret set KEY_PASSWORD --repo OWNER/NAME"
  echo ""
  echo "  Oder: ./scripts/create-release-keystore.sh --repo OWNER/NAME"
fi

echo ""
echo "⚠  app/secureguard-keystore.jks und .b64 sind gitignored – niemals committen."
