#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
BASE='videonic-redesign/app/src/main/java/com/videonic/lite'
DEST='app/src/main/java/com/videonic/lite'
[[ -f "$BASE/MainActivity.kt" && -f "$BASE/data/LocalLibrary.kt" && -f "$DEST/MainActivity.kt" ]] || { echo 'Required source files missing; no changes applied' >&2; exit 1; }
TS=$(date +%Y%m%d-%H%M%S)
BACKUP="videonic-backups/$TS"
mkdir -p "$BACKUP/data" "$DEST/data"
cp "$DEST/MainActivity.kt" "$BACKUP/MainActivity.kt"
if [[ -f "$DEST/data/LocalLibrary.kt" ]];then cp "$DEST/data/LocalLibrary.kt" "$BACKUP/data/LocalLibrary.kt";fi
cp "$BASE/MainActivity.kt" "$DEST/MainActivity.kt"
cp "$BASE/data/LocalLibrary.kt" "$DEST/data/LocalLibrary.kt"
echo "Playback + Me/Settings update applied. Backups at $BACKUP"
echo 'Compile before building/installing the APK.'
