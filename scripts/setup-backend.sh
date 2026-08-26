#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# SecureGuard Enterprise – Backend-Setup (FastAPI)
#   - legt venv an (~/.secureguard-backend-venv)
#   - installiert requirements.txt (+ dev requirements)
#   - erzeugt .env aus .env.example falls nicht vorhanden
#   - initialisiert SQLite-Datenbank + Migrationen
#   - führt Backend-Tests aus (pytest)
# ══════════════════════════════════════════════════════════════════════════
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
VENV_DIR="${BACKEND_VENV_DIR:-$HOME/.secureguard-backend-venv}"

echo "==> Backend-Setup"

if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 fehlt." >&2
  exit 1
fi

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  echo "==> Erzeuge venv: $VENV_DIR"
  python3 -m venv "$VENV_DIR"
fi

echo "==> Installiere Abhängigkeiten"
"$VENV_DIR/bin/pip" install --upgrade pip
"$VENV_DIR/bin/pip" install -r "$BACKEND_DIR/requirements.txt"
if [[ -f "$BACKEND_DIR/requirements-dev.txt" ]]; then
  "$VENV_DIR/bin/pip" install -r "$BACKEND_DIR/requirements-dev.txt"
fi

if [[ ! -f "$REPO_ROOT/.env" && -f "$REPO_ROOT/.env.example" ]]; then
  echo "==> Erzeuge .env aus .env.example"
  cp "$REPO_ROOT/.env.example" "$REPO_ROOT/.env"
fi

# Datenbank initialisieren
echo "==> Initialisiere Datenbank"
DB_PATH="${DATABASE_PATH:-$REPO_ROOT/data/secureguard.db}"
if [[ -d "$REPO_ROOT/data" ]]; then
  export DATABASE_PATH="$DB_PATH"
fi
(
  cd "$BACKEND_DIR"
  "$VENV_DIR/bin/python" -c "import main; main.run_migrations(); print('✔ DB initialisiert:', main.DB_PATH)"
)

echo "==> Backend-Tests"
if [[ -d "$BACKEND_DIR/tests" ]]; then
  (cd "$BACKEND_DIR" && "$VENV_DIR/bin/python" -m pytest -q) || {
    echo "⚠ Backend-Tests fehlgeschlagen." >&2
    exit 1
  }
else
  echo "⚠ Keine Tests in backend/tests gefunden."
fi

echo ""
echo "✔ Backend-Setup abgeschlossen."
echo "  Start:   $VENV_DIR/bin/uvicorn main:app --host 0.0.0.0 --port 8000 --reload"
echo "  Syntax:  cd $BACKEND_DIR && $VENV_DIR/bin/python -m main"
