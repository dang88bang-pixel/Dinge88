#!/usr/bin/env bash
#
# 🛡️ SecureGuard Enterprise – GitHub-Setup für den main-Branch
#
# Erstellt (falls nötig) das Ziel-Repo, setzt die Signing-Secrets,
# pusht den aktuellen Stand auf `main` und erstellt bei Bedarf einen
# Release-Tag, damit GitHub Actions die APK baut.
#
# Voraussetzungen:
#   - gh + git im PATH, `gh auth login` bereits ausgeführt
#   - optional: release-keystore.jks (wenn vorhanden, wird signiert gebaut)
#
# Nutzung:
#   chmod +x scripts/setup-github.sh
#   ./scripts/setup-github.sh [--repo OWNER/NAME] [--tag v1.0.0]
#
# Beispiel (alles):
#   ./scripts/setup-github.sh --repo dang88bang-pixel/secureguard-enterprise --tag v1.0.0
#
set -euo pipefail

# --- Konfiguration ------------------------------------------------------
REPO="${1:-}"
TAG=""
for arg in "$@"; do
  case "$arg" in
    --repo=*) REPO="${arg#*=}" ;;
    --tag=*)  TAG="${arg#*=}" ;;
    --repo) : ;; --tag) : ;;
  esac
done

if [ -z "$REPO" ]; then
  echo "❌ Bitte --repo OWNER/NAME angeben (z. B. --repo dang88bang-pixel/secureguard-enterprise)"
  exit 1
fi

# --- 1) Repo sicherstellen ----------------------------------------------
if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "✔ Repo existiert bereits: $REPO"
else
  echo "→ Erstelle öffentliches Repo: $REPO"
  gh repo create "$REPO" --public --source . --push
fi

# --- 2) Signing-Secrets setzen ------------------------------------------
if [ -f "app/secureguard-keystore.jks" ] || [ -f "release-keystore.jks" ]; then
  KS="app/secureguard-keystore.jks"
  [ ! -f "$KS" ] && KS="release-keystore.jks"
  echo "→ Keystore gefunden: $KS"
  base64 -w0 "$KS" | gh secret set KEYSTORE_BASE64 --repo "$REPO"

  if [ -n "${KEYSTORE_PASSWORD:-}" ]; then gh secret set KEYSTORE_PASSWORD --repo "$REPO" <<<"$KEYSTORE_PASSWORD"; fi
  if [ -n "${KEY_ALIAS:-}" ]; then          gh secret set KEY_ALIAS          --repo "$REPO" <<<"$KEY_ALIAS"; fi
  if [ -n "${KEY_PASSWORD:-}" ]; then       gh secret set KEY_PASSWORD       --repo "$REPO" <<<"$KEY_PASSWORD"; fi

  echo "⚠  KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD wurden nur gesetzt, falls sie als"
  echo "   Umgebungsvariablen gesetzt waren. Sonst bitte manuell setzen:"
  echo "     gh secret set KEYSTORE_PASSWORD --repo $REPO"
  echo "     gh secret set KEY_ALIAS         --repo $REPO"
  echo "     gh secret set KEY_PASSWORD      --repo $REPO"
else
  echo "⚠  Kein Keystore gefunden – das APK wird UNSIGNIERT gebaut."
  echo "   Keystore erzeugen: keytool -genkey -v -keystore app/secureguard-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias secureguard"
fi

# --- 3) Auf main pushen --------------------------------------------------
echo "→ Pushe aktuellen Stand auf main..."
if git ls-remote --exit-code origin main >/dev/null 2>&1; then
  git pull origin main --rebase
fi

# Erstelle/aktualisiere main aus dem aktuellen Arbeitsstand.
# (Falls bereits auf main: einfach push; sonst neuen main-Zweig anlegen.)
if [ "$(git branch --show-current)" = "main" ]; then
  git push origin main
else
  git checkout -B main HEAD
  git push origin main --set-upstream
fi

# --- 4) Optional: Release-Tag -------------------------------------------
if [ -n "$TAG" ]; then
  echo "→ Erstelle und pushe Tag: $TAG"
  git tag -a "$TAG" -m "Release $TAG"
  git push origin "$TAG"
  echo "✔ Release-Build wird in GitHub Actions gestartet. APK erscheint unter Releases."
else
  echo "✔ Push auf main abgeschlossen. Für ein Release:"
  echo "     git tag -a v1.0.0 -m 'Release v1.0.0' && git push origin v1.0.0"
fi

echo ""
echo "✅ Fertig. Workflow ansehen:  gh run list --repo $REPO"
