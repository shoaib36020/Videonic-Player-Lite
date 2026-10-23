#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
SRC=videonic-update/app/src/main/java/com/videonic/lite/MainActivity.kt
DST=app/src/main/java/com/videonic/lite/MainActivity.kt
[ -f "$SRC" ] && [ -f "$DST" ] || { echo 'Missing source. Run unzip first.';exit 1; }
mkdir -p videonic-backups app/src/main/res/drawable-nodpi
TS=$(date +%Y%m%d-%H%M%S)
cp "$DST" "videonic-backups/MainActivity.before-v3.$TS.kt"
cp "$SRC" "$DST"
cp videonic-update/app/src/main/res/drawable-nodpi/videonic_brand.png app/src/main/res/drawable-nodpi/videonic_brand.png
python3 - <<'PY2'
from pathlib import Path
p=Path('app/src/main/AndroidManifest.xml')
s=p.read_text()
if '@drawable/videonic_icon' in s:
    Path('videonic-backups/AndroidManifest.before-v3.xml').write_text(s)
    p.write_text(s.replace('@drawable/videonic_icon','@drawable/videonic_brand'))
print('Brand icon configured.')
PY2
echo 'Patch applied. Run Kotlin compilation before APK build.'
