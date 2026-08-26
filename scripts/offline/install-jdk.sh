#!/usr/bin/env bash
# Entpackt OpenJDK 17 aus dem Offline-Repo und setzt JAVA_HOME/PATH.
# Läuft auf dem Zielrechner OHNE Internetzugang.
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

JDK_DIR="${OFFLINE_REPO}/jdk"
[[ -d "$JDK_DIR" ]] || die "Kein JDK im Offline-Repo: $JDK_DIR (zuerst download-all.sh)"

# shellcheck disable=SC1091
[[ -f "${JDK_DIR}/jdk.env" ]] && source "${JDK_DIR}/jdk.env"

ARCHIVE="$(find "$JDK_DIR" -maxdepth 1 -type f \( -name '*.tar.gz' -o -name '*.zip' \) | head -1)"
[[ -n "$ARCHIVE" ]] || die "Kein JDK-Archiv in $JDK_DIR"

INSTALL_ROOT="${JAVA_INSTALL_ROOT:-${HOME}/.secureguard/jdk}"
mkdir_p "$INSTALL_ROOT"

# Bereits entpackt?
EXISTING="$(find "$INSTALL_ROOT" -maxdepth 3 -type f -path '*/bin/java' 2>/dev/null | head -1 || true)"
if [[ -n "$EXISTING" ]]; then
  JAVA_HOME_RESOLVED="$(cd "$(dirname "$EXISTING")/.." && pwd)"
  # macOS: Contents/Home
  if [[ -d "${JAVA_HOME_RESOLVED}/Contents/Home" ]]; then
    JAVA_HOME_RESOLVED="${JAVA_HOME_RESOLVED}/Contents/Home"
  fi
  ok "JDK bereits installiert: $JAVA_HOME_RESOLVED"
else
  log "Entpacke $(basename "$ARCHIVE") → $INSTALL_ROOT"
  case "$ARCHIVE" in
    *.tar.gz|*.tgz) tar -xzf "$ARCHIVE" -C "$INSTALL_ROOT" ;;
    *.zip)
      need_cmd unzip
      unzip -q -o "$ARCHIVE" -d "$INSTALL_ROOT"
      ;;
    *) die "Unbekanntes Archivformat: $ARCHIVE" ;;
  esac
  JAVA_BIN="$(find "$INSTALL_ROOT" -maxdepth 4 -type f -path '*/bin/java' | head -1)"
  [[ -n "$JAVA_BIN" ]] || die "java nach dem Entpacken nicht gefunden"
  JAVA_HOME_RESOLVED="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
  if [[ -d "${JAVA_HOME_RESOLVED}/Contents/Home" ]]; then
    JAVA_HOME_RESOLVED="${JAVA_HOME_RESOLVED}/Contents/Home"
  fi
fi

# Profil-Snippet schreiben (nicht automatisch sourcen – User entscheidet)
ENV_FILE="${HOME}/.secureguard/env.sh"
mkdir_p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
# SecureGuard Enterprise – Toolchain-Umgebung (generiert von install-jdk.sh)
export JAVA_HOME="${JAVA_HOME_RESOLVED}"
export PATH="\$JAVA_HOME/bin:\$PATH"
EOF

# Aktuelle Session
export JAVA_HOME="${JAVA_HOME_RESOLVED}"
export PATH="${JAVA_HOME}/bin:${PATH}"

ok "JAVA_HOME=${JAVA_HOME}"
java -version 2>&1 | head -3 || true
ok "Umgebung dauerhaft laden:  source ${ENV_FILE}"
# Optional in .bashrc eintragen
MARKER="# SecureGuard Enterprise toolchain"
if [[ -f "${HOME}/.bashrc" ]] && ! grep -qF "$MARKER" "${HOME}/.bashrc" 2>/dev/null; then
  if [[ "${SG_AUTO_BASHRC:-0}" == "1" ]]; then
    printf '\n%s\n[ -f "%s" ] && . "%s"\n' "$MARKER" "$ENV_FILE" "$ENV_FILE" >> "${HOME}/.bashrc"
    ok "Eintrag in ~/.bashrc ergänzt"
  else
    log "Tipp: SG_AUTO_BASHRC=1 ./install-jdk.sh  – schreibt source-Zeile in ~/.bashrc"
  fi
fi
