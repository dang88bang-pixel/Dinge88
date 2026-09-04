#!/usr/bin/env bash
# Übernimmt die vorbereiteten GitHub-Actions-Workflows nach .github/workflows/.
#
# Warum dieser Umweg? Das Token des Arena-Agenten besitzt die Berechtigung
# `workflows` nicht – GitHub lehnt jeden Push ab, der Dateien unter
# .github/workflows/ anlegt oder ändert. Die Workflows liegen deshalb unter
# ci/workflows/ im Repository und werden mit diesem Skript aktiviert.
#
# Verwendung:
#   bash scripts/install-ci-workflows.sh
#   git add .github/workflows && git commit -m "ci: Workflows aktivieren" && git push
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/ci/workflows"
DST="$ROOT/.github/workflows"

if [ ! -d "$SRC" ]; then
  echo "Fehler: $SRC existiert nicht." >&2
  exit 1
fi

mkdir -p "$DST"

changed=0
for file in "$SRC"/*.yml; do
  name="$(basename "$file")"
  target="$DST/$name"
  if [ -f "$target" ] && cmp -s "$file" "$target"; then
    echo "  =  $name (unverändert)"
  else
    cp "$file" "$target"
    echo "  ->  $name aktualisiert"
    changed=$((changed + 1))
  fi
done

echo
if [ "$changed" -eq 0 ]; then
  echo "Alle Workflows sind bereits aktuell."
else
  echo "$changed Workflow(s) übernommen. Nächste Schritte:"
  echo
  echo "  git add .github/workflows"
  echo "  git commit -m \"ci: JDK/Android-SDK ueber offizielle Actions, CI auf jedem Branch\""
  echo "  git push"
  echo
  echo "Der Push muss mit einem Konto oder Token erfolgen, das die Berechtigung"
  echo "'workflows' besitzt (persoenlicher Account oder PAT mit workflow-Scope)."
fi
