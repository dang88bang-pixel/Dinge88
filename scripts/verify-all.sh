#!/usr/bin/env bash
#
# Vollständige Prüfung aller lokal ausführbaren Ebenen des Projekts.
#
#   bash scripts/verify-all.sh
#
# Läuft in dieser Reihenfolge, schnellste und aussagekräftigste Stufe zuerst:
#
#   1. Befehlssatz-Drift  (App ↔ 3D-Konsole ↔ Firmware)
#   2. 3D-Konsole         (Vitest: Fachlogik + komplettes HUD in jsdom)
#   3. Konsolen-Bundle    (Vite-Build + Abgleich mit den App-Assets)
#   4. Backend            (pytest: alle Endpunkte, API-Key, WebSocket)
#
# NICHT enthalten – und bewusst nicht vorgetäuscht:
#   * Der Android-Build (:app:assembleDebug) braucht JDK + Android-SDK.
#     Er läuft im CI-Job "android" (ci/workflows/ci.yml).
#   * Die WebGL-Szene (console3d/src/scene/world.js) braucht einen echten
#     Browser. In den Tests ist sie durch eine aufzeichnende Attrappe ersetzt.
set -uo pipefail

cd "$(dirname "$0")/.."

FAILED=()
run () {
  local name="$1"; shift
  printf '\n\033[1m▶ %s\033[0m\n' "$name"
  if "$@"; then
    printf '\033[32m✔ %s\033[0m\n' "$name"
  else
    printf '\033[31m✘ %s\033[0m\n' "$name"
    FAILED+=("$name")
  fi
}

run "Befehlssatz – Drift zwischen App, Konsole und Firmware" \
  python3 scripts/check-action-drift.py

run "3D-Konsole – Tests" \
  bash -c 'cd console3d && npm test --silent'

run "3D-Konsole – Bundle bauen" \
  bash -c 'cd console3d && npm run build >/dev/null && echo "Bundle gebaut: $(du -sh dist | cut -f1)"'

run "3D-Konsole – Bundle gegen App-Assets" \
  bash -c 'diff -r console3d/dist app/src/main/assets/console3d >/dev/null \
    && echo "app/src/main/assets/console3d ist aktuell" \
    || { echo "Bundle veraltet – beheben mit: bash scripts/sync-console3d.sh"; exit 1; }'

run "Backend – Tests" \
  python3 -m pytest backend/tests -q

printf '\n\033[1m── Ergebnis ──\033[0m\n'
if [ ${#FAILED[@]} -eq 0 ]; then
  printf '\033[32mAlle lokal prüfbaren Stufen sind grün.\033[0m\n'
  printf 'Offen bleibt der Android-Build – der läuft nur im CI (Job "android").\n'
  exit 0
fi
printf '\033[31m%d Stufe(n) fehlgeschlagen:\033[0m\n' "${#FAILED[@]}"
printf '  ✘ %s\n' "${FAILED[@]}"
exit 1
