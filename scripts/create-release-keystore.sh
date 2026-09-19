#!/usr/bin/env bash
# =============================================================================
# SecureGuard Enterprise – Release-Keystore (PKCS12) erzeugen + CI-Secrets vorbereiten
# =============================================================================
# Erzeugt einen RSA-2048 PKCS12-Keystore unter `secureguard-keystore.p12`
# (Repo-Root, gitignored). PKCS12 ist der von apksigner/AGP empfohlene,
# industrieübliche Standard (kein veraltetes JKS).
#
# WICHTIG: Passwörter legt der Anwender selbst fest – es wird nichts generiert.
#
# Nutzung:
#   export KEYSTORE_PASSWORD='…dein starkes Passwort…'
#   export KEY_ALIAS=secureguard                # optional
#   export KEY_PASSWORD='…dein Key-Passwort…'   # optional, default = KEYSTORE_PASSWORD
#   ./scripts/create-release-keystore.sh
#   ./scripts/create-release-keystore.sh --repo OWNER/NAME
#
# Danach lokal:
#   export KEYSTORE_PASSWORD=... KEY_ALIAS=secureguard KEY_PASSWORD=...
#   ./gradlew :app:assembleRelease
#
# CI (GitHub) – Secrets werden im Repo-Settings → Actions → Secrets gesetzt:
#   ANDROID_KEYSTORE_BASE64     ← Inhalt von secureguard-keystore.b64 (einzeilig)
#   ANDROID_KEYSTORE_PASSWORD   ← KEYSTORE_PASSWORD
#   ANDROID_KEY_ALIAS           ← KEY_ALIAS
#   ANDROID_KEY_PASSWORD        ← KEY_PASSWORD (i. d. R. = KEYSTORE_PASSWORD)
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REPO=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo=*) REPO="${1#*=}"; shift ;;
    --repo) shift; REPO="${1:-}"; shift || true ;;
    --help|-h) sed -n '2,28p' "$0" | sed 's/^# \?//'; exit 0 ;;
    *) echo "Unbekanntes Argument: $1 (siehe --help)"; exit 1 ;;
  esac
done

need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ fehlt: $1"; exit 1; }; }
need openssl

OUT_P12="${ROOT}/secureguard-keystore.p12"
OUT_B64="${ROOT}/secureguard-keystore.b64"
ALIAS="${KEY_ALIAS:-secureguard}"
STORE_PASS="${KEYSTORE_PASSWORD:-}"
KEY_PASS="${KEY_PASSWORD:-$STORE_PASS}"

if [[ -z "$STORE_PASS" ]]; then
  echo "❌ KEYSTORE_PASSWORD ist nicht gesetzt."
  echo ""
  echo "   Passwörter legt der Anwender selbst fest – das Script generiert keine."
  echo "   Beispiel:"
  echo "     export KEYSTORE_PASSWORD='…dein starkes Passwort…'"
  echo "     export KEY_ALIAS=secureguard                # optional"
  echo "     ./scripts/create-release-keystore.sh"
  exit 1
fi

# PKCS12 erfordert identisches Store-/Key-Passwort (Android-Konvention).
if [[ "$KEY_PASS" != "$STORE_PASS" ]]; then
  echo "⚠  PKCS12/Apksigner: Key-Passwort == Store-Passwort empfohlen."
  echo "   Das Script verwendet für den Export das Store-Passwort für beide."
  KEY_PASS="$STORE_PASS"
fi

if [[ -f "$OUT_P12" ]]; then
  echo "⚠  Keystore existiert bereits: $OUT_P12"
  echo "   Löschen falls neu erzeugen gewünscht, oder nur Base64/Secrets neu setzen."
else
  echo "→ Erzeuge PKCS12-Keystore: $OUT_P12"
  echo "   Alias: $ALIAS (Passwörter aus Umgebung – nicht im Klartext geloggt)"
  TMPDIR_K="$(mktemp -d)"
  trap 'rm -rf "$TMPDIR_K"' EXIT
  # RSA-2048, Selbstsignatur, 10000 Tage Gültigkeit (~27 Jahre, deckt Play-Konsolen-Anforderung).
  openssl req -x509 -newkey rsa:2048 -sha256 -days 10000 -nodes \
    -keyout "$TMPDIR_K/key.pem" -out "$TMPDIR_K/cert.pem" \
    -subj "/CN=SecureGuard Enterprise/OU=Mobile/O=SecureGuard/L=Berlin/ST=BE/C=DE" 2>/dev/null
  openssl pkcs12 -export -name "$ALIAS" \
    -inkey "$TMPDIR_K/key.pem" -in "$TMPDIR_K/cert.pem" \
    -out "$OUT_P12" -passout "pass:$STORE_PASS" 2>/dev/null
  echo "✔ Keystore erzeugt (PKCS12, RSA-2048)"
fi

# Base64 (einzeilig, CI-tauglich)
if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w0 "$OUT_P12" > "$OUT_B64"
else
  base64 "$OUT_P12" | tr -d '\n' > "$OUT_B64"
fi
echo "✔ Base64: $OUT_B64"

echo ""
echo "=== GitHub Secrets (Repo-Settings → Actions → Secrets) ==="
echo "  ANDROID_KEYSTORE_BASE64     ← Inhalt von $OUT_B64 (einzeilig)"
echo "  ANDROID_KEYSTORE_PASSWORD   ← KEYSTORE_PASSWORD"
echo "  ANDROID_KEY_ALIAS           ← $ALIAS"
echo "  ANDROID_KEY_PASSWORD        ← KEY_PASSWORD (i. d. R. = KEYSTORE_PASSWORD)"
if [[ -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
  echo ""
  echo "→ Setze ANDROID_KEYSTORE_BASE64 für $REPO …"
  gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO" < "$OUT_B64"
  echo "→ Übrige Secrets bitte interaktiv setzen:"
  gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
  gh secret set ANDROID_KEY_ALIAS --repo "$REPO" <<< "$ALIAS"
  gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"
  echo "✔ Secrets gesetzt (Passwörter von euch eingegeben)"
else
  echo ""
  echo "  gh secret set ANDROID_KEYSTORE_BASE64 --repo OWNER/NAME < secureguard-keystore.b64"
  echo "  gh secret set ANDROID_KEYSTORE_PASSWORD --repo OWNER/NAME   # euer Passwort"
  echo "  gh secret set ANDROID_KEY_ALIAS --repo OWNER/NAME           # Wert: $ALIAS"
  echo "  gh secret set ANDROID_KEY_PASSWORD --repo OWNER/NAME        # euer Key-Passwort"
fi

echo ""
echo "⚠  secureguard-keystore.p12 und .b64 sind gitignored – niemals committen."
echo "⚠  Passwörter niemals ins Repo, Logs oder Chat schreiben."
