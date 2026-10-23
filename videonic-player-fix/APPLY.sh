#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
source_file='app/src/main/java/com/videonic/lite/MainActivity.kt'
patch_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
expected="$(cat "$patch_dir/SOURCE_SHA256.txt")"
actual="$(sha256sum "$source_file" | cut -d ' ' -f1)"
if [[ "$actual" != "$expected" ]]; then
  echo 'STOP: Your MainActivity.kt does not match the uploaded archive.'
  echo 'No source files were changed. Send the current file/backup for reconciliation.'
  exit 2
fi
backup="$source_file.before-player-fix-$(date +%Y%m%d-%H%M%S)"
cp "$source_file" "$backup"
cp "$patch_dir/MainActivity.kt" "$source_file"
echo "Applied current-source player fix. Backup: $backup"
echo 'Next: run Gradle :app:compileDebugKotlin before generating an APK.'
