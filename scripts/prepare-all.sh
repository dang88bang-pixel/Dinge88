#!/usr/bin/env bash
# =============================================================================
# SecureGuard – Vollständige Bereitstellung (Check + Hinweise)
# =============================================================================
# Prüft Repo-Integrität, optional Stack/Tests. Keine Passwort-Generierung.
# Passwörter/PINs/Keys setzt der Anwender selbst.
#
#   ./scripts/prepare-all.sh
#   ./scripts/prepare-all.sh --with-stack
#   ./scripts/prepare-all.sh --with-tests
# =============================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WITH_STACK=0
WITH_TESTS=0
for arg in "$@"; do
  case "$arg" in
    --with-stack) WITH_STACK=1 ;;
    --with-tests) WITH_TESTS=1 ;;
    --help|-h)
      sed -n '2,20p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
  esac
done

ok=0; fail=0; warn=0
pass() { echo "✔ $1"; ok=$((ok+1)); }
bad()  { echo "✖ $1"; fail=$((fail+1)); }
note() { echo "⚠ $1"; warn=$((warn+1)); }

echo "=== SecureGuard – Vollständige Bereitstellung ==="
echo "Root: $ROOT"
echo ""

# ---- Repo structure ----
echo "--- Struktur ---"
for p in \
  app/src/main/AndroidManifest.xml \
  app/build.gradle.kts \
  backend/main.py \
  backend/Dockerfile \
  docker-compose.yml \
  mosquitto/config/mosquitto.conf \
  nodered/flows.json \
  firmware/secureguard_esp32/platformio.ini \
  scripts/start-stack.sh \
  scripts/smoke-check.sh \
  scripts/create-release-keystore.sh \
  docs/GO_LIVE.md \
  docs/READY_TO_GO_CHECKLISTE.md \
  docs/TESTING.md \
  docs/SQLCIPHER_AND_SIGNING.md \
  local.properties.example \
  .github/workflows/build-release.yml
do
  if [[ -e "$p" ]]; then pass "$p"; else bad "fehlt: $p"; fi
done

# ---- App feature markers ----
echo ""
echo "--- App-Funktionen (Code-Marker) ---"
markers=(
  "SqlCipherHelper:data/local/SqlCipherHelper.kt"
  "DatabaseKeyManager:security/DatabaseKeyManager.kt"
  "AgentService:services/AgentService.kt"
  "MqttService:services/MqttService.kt"
  "BackendSyncService:services/BackendSyncService.kt"
  "PrivacyService:services/PrivacyService.kt"
  "HealthMonitorService:services/HealthMonitorService.kt"
  "ExportService:services/ExportService.kt"
  "AuthManager:services/AuthManager.kt"
  "EndpointConfig:config/EndpointConfig.kt"
)
base="app/src/main/java/com/secureguard/enterprise"
for m in "${markers[@]}"; do
  name="${m%%:*}"; rel="${m#*:}"
  if [[ -f "$base/$rel" ]]; then pass "$name"; else bad "$name ($rel)"; fi
done

screens=$(find app/src/main/java/com/secureguard/enterprise/presentation -name '*Screen.kt' 2>/dev/null | wc -l | tr -d ' ')
pass "UI-Screens: $screens"

# ---- local.properties ----
echo ""
echo "--- Lokale Config ---"
if [[ -f local.properties ]]; then
  pass "local.properties vorhanden"
  grep -q 'sdk.dir=' local.properties && pass "sdk.dir gesetzt" || note "sdk.dir fehlt in local.properties"
else
  note "local.properties fehlt – kopiere: cp local.properties.example local.properties"
fi

# ---- Tools ----
echo ""
echo "--- Werkzeuge ---"
command -v git >/dev/null && pass "git" || bad "git"
command -v docker >/dev/null && pass "docker" || note "docker fehlt (Stack nur mit Docker)"
command -v java >/dev/null && pass "java $(java -version 2>&1 | head -1)" || note "JDK fehlt – App-Build nur mit JDK 17+"
command -v curl >/dev/null && pass "curl" || note "curl fehlt (smoke-check eingeschränkt)"

# ---- Optional stack ----
if [[ "$WITH_STACK" -eq 1 ]]; then
  echo ""
  echo "--- Stack starten ---"
  if command -v docker >/dev/null; then
    ./scripts/start-stack.sh
    ./scripts/smoke-check.sh || true
  else
    bad "docker für --with-stack erforderlich"
  fi
fi

# ---- Optional tests ----
if [[ "$WITH_TESTS" -eq 1 ]]; then
  echo ""
  echo "--- Unit-Tests ---"
  if [[ -x ./gradlew ]] && command -v java >/dev/null; then
    ./gradlew :app:testDebugUnitTest --no-daemon || bad "Unit-Tests fehlgeschlagen"
  else
    note "gradlew/java nicht verfügbar – Tests übersprungen"
  fi
fi

echo ""
echo "=== Ergebnis: $ok ok · $warn Hinweise · $fail Fehler ==="
echo ""
echo "Nächste Schritte (Anwender):"
echo "  1) cp local.properties.example local.properties   # sdk.dir + optionale Keys"
echo "  2) ./scripts/start-stack.sh && ./scripts/smoke-check.sh"
echo "  3) ./gradlew :app:testDebugUnitTest :app:assembleDebug"
echo "  4) adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo "  5) In der App: Endpunkte, PIN (selbst wählen), optional Keystore-Passwort"
echo ""
echo "Docs: docs/GO_LIVE.md"
echo "Alle Funktionen bleiben erhalten – nur Konfiguration/Hardware fehlt ggf. noch."
[[ "$fail" -eq 0 ]]
