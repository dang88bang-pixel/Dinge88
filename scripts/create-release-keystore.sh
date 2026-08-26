#!/usr/bin/env bash
# =============================================================================
# SecureGuard Enterprise – Release-Keystore erzeugen + CI-Secrets vorbereiten
# =============================================================================
# Erzeugt ein RSA-2048 Keystore unter app/secureguard-keystore.jks (gitignored).
#
# WICHTIG: Passwörter legt der Anwender selbst fest – es wird nichts generiert.
#
# Nutzung:
#   export KEYSTORE_PASSWORD='…dein Passwort…'
#   export KEY_PASSWORD='…dein Key-Passwort…'   # optional, default = KEYSTORE_PASSWORD
#   export KEY_ALIAS=secureguard                # optional
#   ./scripts/create-release-keystore.sh
#   ./scripts/create-release-keystore.sh --repo OWNER/NAME
#
# Danach lokal:
#   export KEYSTORE_PASSWORD=... KEY_ALIAS=secureguard KEY_PASSWORD=...
#   ./gradlew :app:assembleRelease
#
# CI (GitHub) – Secrets vom Anwender setzen:
#   gh secret set KEYSTORE_BASE64 < app/secureguard-keystore.b64
#   gh secret set KEYSTORE_PASSWORD          # interaktiv euer Passwort
#   gh secret set KEY_ALIAS
#   gh secret set KEY_PASSWORD
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REPO=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo=*) REPO="${1#*=}"; shift ;;
    --repo)
      shift
      REPO="${1:-}"
      shift || true
      ;;
    --help|-h)
      sed -n '2,28p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "Unbekanntes Argument: $1 (siehe --help)"
      exit 1
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
  echo "❌ KEYSTORE_PASSWORD ist nicht gesetzt."
  echo ""
  echo "   Passwörter legt der Anwender selbst fest – das Script generiert keine."
  echo "   Beispiel:"
  echo "     export KEYSTORE_PASSWORD='…dein starkes Passwort…'"
  echo "     export KEY_PASSWORD='…dein Key-Passwort…'   # optional"
  echo "     export KEY_ALIAS=secureguard                # optional"
  echo "     ./scripts/create-release-keystore.sh"
  exit 1
fi

# Key-Passwort: nur wenn Anwender gesetzt hat, sonst = Store-Passwort (ebenfalls vom Anwender)
KEY_PASS="${KEY_PASS:-$STORE_PASS}"

if [[ -f "$OUT_JKS" ]]; then
  echo "⚠  Keystore existiert bereits: $OUT_JKS"
  echo "   Löschen falls neu erzeugen gewünscht, oder nur Base64/Secrets neu setzen."
else
  echo "→ Erzeuge Keystore: $OUT_JKS"
  echo "   Alias: $ALIAS (Passwörter aus Umgebung – nicht im Klartext geloggt)"
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
echo "✔ Base64: $OUT_B64"

# local.properties – nur Platzhalter-Hinweise, keine Klartext-Passwörter schreiben
LP="${ROOT}/local.properties"
if [[ -f "$LP" ]]; then
  if ! grep -q '^# KEYSTORE_PASSWORD=' "$LP" 2>/dev/null && ! grep -q '^KEYSTORE_PASSWORD=' "$LP" 2>/dev/null; then
    {
      echo ""
      echo "# Release-Signing (Passwörter selbst setzen – nicht committen)"
      echo "# KEYSTORE_PASSWORD="
      echo "# KEY_ALIAS=$ALIAS"
      echo "# KEY_PASSWORD="
    } >> "$LP"
    echo "→ Hinweise in local.properties ergänzt (Passwörter bitte selbst eintragen)"
  fi
fi

echo ""
echo "=== Lokal bauen ==="
echo "  export KEYSTORE_PASSWORD='…'   # euer Passwort"
echo "  export KEY_ALIAS='$ALIAS'"
echo "  export KEY_PASSWORD='…'        # oder gleiches wie KEYSTORE_PASSWORD"
echo "  ./gradlew :app:assembleRelease"
echo ""
echo "=== GitHub Secrets (Werte vom Anwender) ==="
if [[ -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
  echo "→ Setze KEYSTORE_BASE64 für $REPO …"
  gh secret set KEYSTORE_BASE64 --repo "$REPO" < "$OUT_B64"
  echo "→ KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD bitte interaktiv setzen:"
  gh secret set KEYSTORE_PASSWORD --repo "$REPO"
  gh secret set KEY_ALIAS --repo "$REPO" <<< "$ALIAS"
  gh secret set KEY_PASSWORD --repo "$REPO"
  echo "✔ Secrets gesetzt (Passwörter von euch eingegeben)"
else
  echo "  gh secret set KEYSTORE_BASE64 --repo OWNER/NAME < app/secureguard-keystore.b64"
  echo "  gh secret set KEYSTORE_PASSWORD --repo OWNER/NAME   # euer Passwort"
  echo "  gh secret set KEY_ALIAS --repo OWNER/NAME           # Wert: $ALIAS"
  echo "  gh secret set KEY_PASSWORD --repo OWNER/NAME        # euer Key-Passwort"
  echo ""
  echo "  Oder: KEYSTORE_PASSWORD=… ./scripts/create-release-keystore.sh --repo OWNER/NAME"
fi

echo ""
echo "⚠  app/secureguard-keystore.jks und .b64 sind gitignored – niemals committen."
echo "⚠  Passwörter niemals ins Repo, Logs oder Chat schreiben."
