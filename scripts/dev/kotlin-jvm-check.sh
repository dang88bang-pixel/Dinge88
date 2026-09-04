#!/usr/bin/env bash
# =============================================================================
# SecureGuard – Kotlin-JVM-Schnellcheck OHNE Android-SDK/Gradle
# =============================================================================
# Kompiliert die Android-freien Kotlin-Klassen des Projekts und führt ihre
# JVM-Unit-Tests aus. Gedacht für Umgebungen ohne Android-SDK (CI-Container,
# air-gapped-Hosts, Review-Sandboxes) – ersetzt NICHT `./gradlew test`,
# deckt aber die reine Logik ab (z. B. die Anbindungsliste der Einstellungen).
#
# Voraussetzungen (in dieser Reihenfolge gesucht):
#   1) JDK    : $JAVA_HOME bzw. `java` im PATH.
#               Fallback: `pip install jdk4py` (Wheel bringt eine JDK mit).
#   2) kotlinc: `kotlinc` im PATH, $KOTLIN_HOME/bin/kotlinc
#               Fallback: npm-Paket `kotlin-compiler` (enthält die volle Dist).
#   3) junit  : $JUNIT_JAR, ~/.gradle-Cache, ./libs/junit-4*.jar
#
# Nutzung:
#   ./scripts/dev/kotlin-jvm-check.sh
#   JUNIT_JAR=/pfad/junit-4.13.2.jar ./scripts/dev/kotlin-jvm-check.sh
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${OUT:-/tmp/secureguard-kotlin-jvm}"
APP="$ROOT/app/src/main/java/com/secureguard/enterprise"
TEST="$ROOT/app/src/test/java/com/secureguard/enterprise"

# Zu prüfende Android-freie Quellen (Produktionscode + zugehörige Tests).
SOURCES=(
  "$APP/config/IntegrationInfo.kt"
)
TESTS=(
  "$TEST/IntegrationInfoTest.kt"
)
TEST_CLASSES=(
  "com.secureguard.enterprise.IntegrationInfoTest"
)

# ---- 1) JDK -----------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" && -x "$ROOT/.venv/bin/python" ]]; then
  jh=$("$ROOT/.venv/bin/python" - <<'PY' 2>/dev/null || true
try:
    from jdk4py import JAVA_HOME
    print(JAVA_HOME)
except Exception:
    pass
PY
)
  [[ -n "$jh" ]] && export JAVA_HOME="$jh"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java || true)"
fi
if [[ -z "$JAVA" || ! -x "$JAVA" ]]; then
  echo "✖ kein JDK gefunden – 'pip install jdk4py' oder JAVA_HOME setzen" >&2
  exit 1
fi
echo "JDK:     $("$JAVA" -version 2>&1 | head -1)"

# ---- 2) Kotlin-Compiler -----------------------------------------------------
KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [[ -z "$KOTLINC" && -n "${KOTLIN_HOME:-}" && -x "$KOTLIN_HOME/bin/kotlinc" ]]; then
  KOTLINC="$KOTLIN_HOME/bin/kotlinc"
fi
STDLIB=""
if [[ -z "$KOTLINC" ]]; then
  # npm-Dist (enthält bin/kotlinc + lib/kotlin-stdlib.jar)
  cand=$(find "${KOTLIN_NPM_DIR:-/tmp/kc}/package" "$ROOT/node_modules/kotlin-compiler" \
           -maxdepth 2 -name kotlinc -type f 2>/dev/null | head -1 || true)
  [[ -n "$cand" ]] && KOTLINC="$cand"
fi
if [[ -z "$KOTLINC" || ! -x "$KOTLINC" ]]; then
  echo "✖ kein kotlinc gefunden – KOTLIN_HOME setzen oder 'npm pack kotlin-compiler'" >&2
  exit 1
fi
STDLIB="$(dirname "$(dirname "$KOTLINC")")/lib/kotlin-stdlib.jar"
[[ -f "$STDLIB" ]] || STDLIB=""
echo "kotlinc: $KOTLINC"

# ---- 3) JUnit ---------------------------------------------------------------
JUNIT_JAR="${JUNIT_JAR:-}"
if [[ -z "$JUNIT_JAR" ]]; then
  JUNIT_JAR=$(find "$HOME/.gradle/caches/modules-2/files-2.1/junit/junit" \
                   "$ROOT/libs" /tmp/jars \
                   -name 'junit-4*.jar' 2>/dev/null | head -1 || true)
fi
if [[ -z "$JUNIT_JAR" || ! -f "$JUNIT_JAR" ]]; then
  echo "✖ kein junit-4*.jar gefunden – JUNIT_JAR=/pfad/junit-4.13.2.jar setzen" >&2
  exit 1
fi
echo "junit:   $JUNIT_JAR"
echo ""

# ---- Kompilieren ------------------------------------------------------------
rm -rf "$OUT"
mkdir -p "$OUT"
CP="$JUNIT_JAR"
for f in "${SOURCES[@]}" "${TESTS[@]}"; do
  [[ -f "$f" ]] || { echo "✖ Quelle fehlt: $f" >&2; exit 1; }
done
echo "Kompiliere ${#SOURCES[@]} Produktions- + ${#TESTS[@]} Testdatei(en) …"
"$KOTLINC" -nowarn -cp "$CP" -d "$OUT" "${SOURCES[@]}" "${TESTS[@]}"

# ---- Syntax-Guard über die Android-abhängigen Dateien -----------------------
# Ohne Android/Compose auf dem Classpath scheitert die Typprüfung, Syntaxfehler
# (z. B. nicht geschlossene Kommentare/Strings) werden aber trotzdem gemeldet.
OPTIONAL=(
  "$APP/config/EndpointConfig.kt"
  "$APP/services/SlackService.kt"
  "$APP/services/SlackAlertForwarder.kt"
  "$APP/presentation/ui/slack/SlackScreen.kt"
  "$APP/presentation/ui/slack/SlackViewModel.kt"
  "$APP/presentation/ui/settings/SettingsScreen.kt"
  "$APP/presentation/ui/settings/SettingsViewModel.kt"
)
present=()
for f in "${OPTIONAL[@]}"; do [[ -f "$f" ]] && present+=("$f"); done
if [[ "${#present[@]}" -gt 0 ]]; then
  log="$(mktemp)"
  "$KOTLINC" -nowarn -cp "$CP" -d "$OUT-syntax" "${present[@]}" 2>"$log" || true
  if grep -q "syntax error" "$log"; then
    echo "✖ Syntaxfehler in Android-abhängigen Quellen:"
    grep "syntax error" "$log" | head -10
    rm -f "$log"
    exit 1
  fi
  rm -f "$log"
  echo "Syntax-Guard: ${#present[@]} Android-Quellen ohne Syntaxfehler"
fi

# ---- Tests ausführen --------------------------------------------------------
RUN_CP="$OUT:$JUNIT_JAR"
[[ -n "$STDLIB" ]] && RUN_CP="$RUN_CP:$STDLIB"
echo ""
"$JAVA" -cp "$RUN_CP" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
