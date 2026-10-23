#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
SRC=videonic-patch/app/src/main/java/com/videonic/lite/MainActivity.kt
DST=app/src/main/java/com/videonic/lite/MainActivity.kt
[ -f "$SRC" ] && [ -f "$DST" ] || { echo "Missing patch files"; exit 1; }
mkdir -p videonic-backups
cp "$DST" "videonic-backups/MainActivity.before-me-mini.$(date +%Y%m%d-%H%M%S).kt"
cp "$SRC" "$DST"
echo 'Applied Me, theme, mini player, and progress updates. Compile before installing.'
