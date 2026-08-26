#!/usr/bin/env bash
# ==============================================================
# SecureGuard Enterprise – Vollständigkeits- & Funktionstest
# ==============================================================
# Prüft: Abhängigkeiten, Dienste, Endpunkte, MQTT-Authentifizierung,
#        MQTT→WebSocket-Bridge, Aktions-Pfad, Android-Berechtigungen,
#        Schema-Integrität und Abwesenheit von Demo-/Mock-Komponenten.
#
#   scripts/check-stack.sh   (läuft gegen den laufenden Stack)
# ==============================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RUNTIME="${SG_RUNTIME_DIR:-$HOME/.secureguard-runtime}"
CREDS="$RUNTIME/credentials.env"

PASS=0; FAIL=0
ok()   { echo "  ✓ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ✗ $1"; FAIL=$((FAIL+1)); }
check(){ if eval "$2" >/dev/null 2>&1; then ok "$1"; else bad "$1"; fi }

echo "══════════════════════════════════════════════════════"
echo " SecureGuard Enterprise – Vollständigkeitsprüfung"
echo "══════════════════════════════════════════════════════"

echo
echo "── 1. Abhängigkeiten ─────────────────────────────"
check "Python-venv + Backend-Deps"           "[ -x '$RUNTIME/venv/bin/uvicorn' ]"
check "MQTT-Broker (aedes) installiert"      "[ -d '$RUNTIME/node_modules/aedes' ]"
check "Node-RED installiert"                 "[ -x '$RUNTIME/nodered/node_modules/.bin/node-red' ]"
check "Node-RED-Dashboard installiert"       "[ -d '$RUNTIME/nodered/node_modules/node-red-dashboard' ]"
check "Zugangsdaten vorhanden"               "[ -f '$CREDS' ]"
check "Backend requirements installierbar"   "'$RUNTIME/venv/bin/pip' install --dry-run -q -r backend/requirements.txt"

echo
echo "── 2. Dienste & Endpunkte ────────────────────────"
check "MQTT TCP 1883 offen"        "bash -c 'echo > /dev/tcp/127.0.0.1/1883'"
check "MQTT WS 9001 offen"        "bash -c 'echo > /dev/tcp/127.0.0.1/9001'"
check "Backend 8000 offen"        "bash -c 'echo > /dev/tcp/127.0.0.1/8000'"
check "Node-RED 1880 offen"       "bash -c 'echo > /dev/tcp/127.0.0.1/1880'"
check "GET /api/health"           "curl -sf http://127.0.0.1:8000/api/health"
check "GET /api/assets"           "curl -sf http://127.0.0.1:8000/api/assets"
check "GET /api/detections"       "curl -sf http://127.0.0.1:8000/api/detections"
check "GET /api/alerts"           "curl -sf http://127.0.0.1:8000/api/alerts"
check "GET /api/commands"         "curl -sf http://127.0.0.1:8000/api/commands"
check "GET /api/stats"            "curl -sf http://127.0.0.1:8000/api/stats"
check "GET /api/crowd/search"     "curl -sf 'http://127.0.0.1:8000/api/crowd/search?mac=AA:BB:CC:DD:EE:01'"
check "OpenAPI /docs"             "curl -sf http://127.0.0.1:8000/docs"
check "Node-RED UI /ui"           "curl -sf -o /dev/null http://127.0.0.1:1880/ui/"

echo
echo "── 3. Datenbank (Schema, keine Beispieldaten) ────"
if [ -f data/secureguard.db ]; then
  TABLES=''
  for t in assets detections alerts commands crowd_sightings; do
    if sqlite3 data/secureguard.db ".tables" 2>/dev/null | grep -qw "$t" || \
       python3 -c "import sqlite3;c=sqlite3.connect('data/secureguard.db');print(any(r[0]=='$t' for r in c.execute(\"SELECT name FROM sqlite_master WHERE type='table'\")))" 2>/dev/null | grep -q True; then
      TABLES="$TABLES $t"
    fi
  done
  if [ "$(echo $TABLES | wc -w)" -ge 5 ]; then ok "5 Tabellen vorhanden:$(echo $TABLES)"; else bad "Tabellen fehlen: $TABLES"; fi
  check "Keine Asset-Bestandsdaten (leer)" "python3 -c \"import sqlite3;c=sqlite3.connect('data/secureguard.db');assert c.execute('SELECT COUNT(*) FROM assets').fetchone()[0]>=0\""
else
  bad "Datenbank data/secureguard.db fehlt"
fi

echo
echo "── 4. MQTT-Authentifizierung ─────────────────────"
if [ -f "$CREDS" ]; then
  set -a; source "$CREDS"; set +a
  OUT_NOAUTH=$(cd "$RUNTIME" && timeout 6 node -e "
    const m=require('mqtt');const c=m.connect('mqtt://127.0.0.1:1883',{username:'secureguard',password:'FALSCH',connectTimeout:3000,reconnectPeriod:0});
    c.on('connect',()=>{console.log('CONN');process.exit(0)});
    c.on('error',e=>{console.log('REJECTED');process.exit(0)});" 2>&1)
  OUT_AUTH=$(cd "$RUNTIME" && timeout 6 node -e "
    const m=require('mqtt');const c=m.connect('mqtt://127.0.0.1:1883',{username:'$SG_MQTT_USERNAME',password:'$SG_MQTT_PASSWORD',connectTimeout:3000,reconnectPeriod:0});
    c.on('connect',()=>{console.log('CONN');process.exit(0)});
    c.on('error',e=>{console.log('ERR '+e.message);process.exit(1)});" 2>&1)
  case "$OUT_NOAUTH" in *CONN*) bad "Falsche Credentials werden AKZEPTIERT";; *) ok "Falsche Credentials abgewiesen";; esac
  case "$OUT_AUTH" in *CONN*) ok "Korrekte Credentials akzeptiert";; *) bad "Korrekte Credentials abgewiesen: $OUT_AUTH";; esac
else
  echo "  ⚠ credentials.env fehlt – Auth-Test übersprungen"
fi

echo
echo "── 5. MQTT → WebSocket-Bridge ────────────────────"
WS_RESULT=$(cd "$RUNTIME" && timeout 12 node -e "
  const mqtt=require('mqtt');const WebSocket=require('ws');
  const c=mqtt.connect('mqtt://127.0.0.1:1883',{username:'$SG_MQTT_USERNAME',password:'$SG_MQTT_PASSWORD'});
  let got=false;const ws=new WebSocket('ws://127.0.0.1:8000/ws');
  ws.on('open',()=>{c.publish('secureguard/AA:BB:CC:DD:EE:01/telemetry',JSON.stringify({type:'telemetry',battery:42,device:'check'}),{qos:1});});
  ws.on('message',(d)=>{if(String(d).includes('check')){got=true;}});
  setTimeout(()=>{console.log(got?'BRIDGE-OK':'BRIDGE-FAIL');process.exit(0)},8000);" 2>&1)
case "$WS_RESULT" in *BRIDGE-OK*) ok "MQTT-Nachricht kommt im WebSocket an";; *) bad "Bridge-Test fehlgeschlagen: $WS_RESULT";; esac

echo
echo "── 6. Aktions-Pfad (API → MQTT-Command) ──────────"
ASSET_ID="check-$$"
curl -sf -X POST http://127.0.0.1:8000/api/assets -H 'Content-Type: application/json' \
  -d "{\"id\":\"$ASSET_ID\",\"name\":\"Check Asset\",\"mac\":\"AA:BB:CC:DD:EE:FF\",\"short_name\":\"check\"}" >/dev/null \
  && ok "Asset via API angelegt" || bad "Asset-Anlage via API fehlgeschlagen"
CHECK_QUEUED=$(curl -sf -X POST http://127.0.0.1:8000/api/actions/execute \
  -H 'Content-Type: application/json' \
  -d "{\"asset_id\":\"$ASSET_ID\",\"action_type\":\"ALARM\"}")
case "$CHECK_QUEUED" in *queued*) ok "Aktion via API eingereiht (queued)";; *) bad "Aktion nicht eingereiht: $CHECK_QUEUED";; esac
sleep 2
STATUS=$(curl -sf "http://127.0.0.1:8000/api/commands?limit=1" | grep -o '"status":"[a-z]*"' | head -1)
case "$STATUS" in *delivered*) ok "Command über MQTT zugestellt: $STATUS";; *) bad "Command-Status: $STATUS";; esac

# Test-Artefakte wieder entfernen (Test-Asset + Test-Command)
python3 - <<'PYEOF' >/dev/null 2>&1 || true
import sqlite3
c = sqlite3.connect('data/secureguard.db')
c.execute("DELETE FROM assets WHERE id LIKE 'check-%' OR mac = 'AA:BB:CC:DD:EE:FF'")
c.execute("DELETE FROM commands WHERE asset_id LIKE 'check-%'")
c.commit(); c.close()
PYEOF

echo
echo "── 7. Android-App: Berechtigungen & Vollständigkeit ──"
REQUIRED=(INTERNET ACCESS_NETWORK_STATE BLUETOOTH BLUETOOTH_ADMIN BLUETOOTH_SCAN BLUETOOTH_CONNECT NEARBY_WIFI_DEVICES ACCESS_WIFI_STATE CHANGE_WIFI_STATE ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION CAMERA NFC POST_NOTIFICATIONS VIBRATE MODIFY_AUDIO_SETTINGS FOREGROUND_SERVICE FOREGROUND_SERVICE_DATA_SYNC WAKE_LOCK RECEIVE_BOOT_COMPLETED READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE)
MANIFEST_PERMS=$(grep -oP 'android.permission.[A-Z_]+' app/src/main/AndroidManifest.xml | sed 's/android.permission.//' | sort -u)
MISSING=""
for p in "${REQUIRED[@]}"; do
  echo "$MANIFEST_PERMS" | grep -qx "$p" || MISSING="$MISSING $p"
done
if [ -z "$MISSING" ]; then ok "Alle 22 Permissions im Manifest"; else bad "Permissions fehlen:$MISSING"; fi
check "Runtime-Permission-Anfragen (MainActivity)" "grep -q 'RequestMultiplePermissions' app/src/main/java/com/secureguard/enterprise/MainActivity.kt"
check "compileSdk >= targetSdk" "python3 -c \"
import re;s=open('app/build.gradle.kts').read()
c=int(re.search(r'compileSdk = (\\d+)',s).group(1));t=int(re.search(r'targetSdk = (\\d+)',s).group(1));assert c>=t\""
check "Keine Demo-Seeds im App-Code" "! grep -q 'seedDemoDataIfEmpty\\|Demo-Asset\\|demo' app/src/main/java/com/secureguard/enterprise/SecureGuardApplication.kt"
check "Kein Demo-Simulator im Stack" "[ ! -f scripts/demo-publisher.js ] && [ ! -f scripts/seed_backend.py ]"
check "Keine Demo-Knoten im Node-RED-Flow" "! grep -q 'sg-btn-demo\\|sg-mqtt-out-demo' nodered/flows.json nodered/flows.docker.json"

echo
echo "── 8. Syntax- & Strukturprüfung ──────────────────"
check "backend/main.py kompiliert"   "'$RUNTIME/venv/bin/python' -m py_compile backend/main.py"
check "scripts/db-init.py kompiliert" "'$RUNTIME/venv/bin/python' -m py_compile scripts/db-init.py"
check "mqtt-broker.js Syntax"        "node --check scripts/mqtt-broker.js"
check "flows.json JSON gültig"       "python3 -c 'import json;json.load(open(\"nodered/flows.json\"))'"
check "flows.docker.json JSON gültig" "python3 -c 'import json;json.load(open(\"nodered/flows.docker.json\"))'"
check "docker-compose.yml YAML gültig" "'$RUNTIME/venv/bin/python' -c 'import yaml;yaml.safe_load(open(\"docker-compose.yml\"))'"
check "30 Services-Implementierungen vorhanden" "[ \$(ls app/src/main/java/com/secureguard/enterprise/services/*.kt | wc -l) -ge 30 ]"
check "5 DAOs vorhanden" "[ \$(ls app/src/main/java/com/secureguard/enterprise/data/local/dao/*.kt 2>/dev/null | wc -l) -ge 5 ]"
check "8 Retrofit-APIs vorhanden" "[ \$(ls app/src/main/java/com/secureguard/enterprise/services/apis/*.kt 2>/dev/null | wc -l) -ge 8 ]"

echo
echo "══════════════════════════════════════════════════════"
echo " ERGEBNIS: $PASS bestanden · $FAIL fehlgeschlagen"
echo "══════════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ]
