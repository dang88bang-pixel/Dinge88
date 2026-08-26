#!/usr/bin/env bash
# Bindet das gespiegelte Android-SDK ein und schreibt local.properties.
# Läuft auf dem Zielrechner OHNE Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SRC="${OFFLINE_REPO}/android-sdk"
[[ -d "$SRC" ]] || die "Kein Android-SDK im Offline-Repo: $SRC"

INSTALL_ROOT="${ANDROID_INSTALL_ROOT:-${HOME}/.secureguard/android-sdk}"

if [[ -d "${INSTALL_ROOT}/platform-tools" ]] || [[ -d "${INSTALL_ROOT}/platforms" ]]; then
  ok "Android-SDK bereits unter $INSTALL_ROOT"
else
  log "Kopiere Android-SDK → $INSTALL_ROOT (kann dauern)…"
  mkdir_p "$INSTALL_ROOT"
  rsync -a "$SRC/" "$INSTALL_ROOT/" 2>/dev/null || cp -a "$SRC/." "$INSTALL_ROOT/"
fi

# Fallback: wenn nur ZIP vorhanden, entpacken
if [[ ! -x "${INSTALL_ROOT}/cmdline-tools/latest/bin/sdkmanager" \
   && ! -f "${INSTALL_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]]; then
  ZIP="$(find "${OFFLINE_REPO}/android-cmdline-tools" -name 'commandlinetools-*.zip' 2>/dev/null | head -1 || true)"
  if [[ -n "$ZIP" ]]; then
    log "cmdline-tools aus ZIP entpacken…"
    TMP="$(mktemp -d)"
    unzip -q -o "$ZIP" -d "$TMP"
    mkdir_p "${INSTALL_ROOT}/cmdline-tools"
    if [[ -d "${TMP}/cmdline-tools" ]]; then
      rm -rf "${INSTALL_ROOT}/cmdline-tools/latest"
      mv "${TMP}/cmdline-tools" "${INSTALL_ROOT}/cmdline-tools/latest"
    fi
    rm -rf "$TMP"
  fi
fi

export ANDROID_HOME="$INSTALL_ROOT"
export ANDROID_SDK_ROOT="$INSTALL_ROOT"

# env.sh ergänzen
ENV_FILE="${HOME}/.secureguard/env.sh"
mkdir_p "$(dirname "$ENV_FILE")"
if [[ -f "$ENV_FILE" ]]; then
  grep -q 'ANDROID_HOME' "$ENV_FILE" 2>/dev/null || cat >> "$ENV_FILE" <<EOF
export ANDROID_HOME="${INSTALL_ROOT}"
export ANDROID_SDK_ROOT="${INSTALL_ROOT}"
export PATH="\$ANDROID_HOME/platform-tools:\$PATH"
EOF
else
  cat > "$ENV_FILE" <<EOF
# SecureGuard Enterprise – Toolchain-Umgebung
export ANDROID_HOME="${INSTALL_ROOT}"
export ANDROID_SDK_ROOT="${INSTALL_ROOT}"
export PATH="\$ANDROID_HOME/platform-tools:\$PATH"
EOF
fi

# local.properties schreiben / aktualisieren (sdk.dir, Keys bleiben erhalten)
LP="${REPO_ROOT}/local.properties"
EXAMPLE="${REPO_ROOT}/local.properties.example"

if [[ ! -f "$LP" && -f "$EXAMPLE" ]]; then
  cp "$EXAMPLE" "$LP"
  log "local.properties aus Example erzeugt"
fi

# sdk.dir setzen (Gradle: Forward-Slashes auch unter Windows)
SDK_DIR_PROP="$INSTALL_ROOT"
if [[ "$(detect_os)" == "windows" ]]; then
  SDK_DIR_PROP="${INSTALL_ROOT//\\//}"
fi

if [[ -f "$LP" ]]; then
  if grep -q '^sdk\.dir=' "$LP"; then
    # In-place ersetzen
    tmp="${LP}.tmp"
    grep -v '^sdk\.dir=' "$LP" > "$tmp" || true
    printf 'sdk.dir=%s\n' "$SDK_DIR_PROP" >> "$tmp"
    mv "$tmp" "$LP"
  else
    printf '\n# Android SDK (offline)\nsdk.dir=%s\n' "$SDK_DIR_PROP" >> "$LP"
  fi
else
  printf 'sdk.dir=%s\n' "$SDK_DIR_PROP" > "$LP"
fi

ok "ANDROID_HOME=${ANDROID_HOME}"
ok "local.properties → sdk.dir=${SDK_DIR_PROP}"
if [[ -d "${INSTALL_ROOT}/platforms" ]]; then
  log "Installierte Platforms:"
  ls "${INSTALL_ROOT}/platforms" 2>/dev/null | sed 's/^/  /' || true
fi
