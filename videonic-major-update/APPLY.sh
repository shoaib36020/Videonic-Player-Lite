#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
DEST=app/src/main/java/com/videonic/lite/MainActivity.kt
HERE="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp "$DEST" "${DEST}.backup-major-${STAMP}"
TMP="$(mktemp)"
set +e
git merge-file -p "$DEST" "$HERE/BASE_MainActivity.kt" "$HERE/UPDATED_MainActivity.kt" > "$TMP"
RC=$?
set -e
if [ "$RC" -ne 0 ]; then
  echo "STOPPED: automatic merge conflict. Your project has NOT been changed."
  echo "Backup: ${DEST}.backup-major-${STAMP}"
  echo "Send me the conflict output or latest source archive; do not force-replace the file."
  rm -f "$TMP"
  exit 1
fi
if grep -Eq '^(<<<<<<<|=======|>>>>>>>)' "$TMP"; then
  echo 'STOPPED: conflict marker detected'; rm -f "$TMP"; exit 1
fi
mv "$TMP" "$DEST"
echo "SUCCESS: source update merged. Backup: ${DEST}.backup-major-${STAMP}"
echo "Next: run the Kotlin compilation command in README.txt."
