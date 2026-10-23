#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
DEST='app/src/main/java/com/videonic/lite/ui/FeedbackScreen.kt'
SOURCE='videonic-feedback-public-fix/app/src/main/java/com/videonic/lite/ui/FeedbackScreen.kt'
[ -f "$DEST" ] || { echo "ERROR: FeedbackScreen.kt missing. Apply secure-feedback update first."; exit 1; }
[ -f "$SOURCE" ] || { echo "ERROR: Fix files missing. Unzip the archive in the project root."; exit 1; }
python3 - "$DEST" "$SOURCE" <<'PY'
from pathlib import Path
import hashlib,sys,datetime,shutil
existing=Path(sys.argv[1]); replacement=Path(sys.argv[2]); current=existing.read_text(); desired=replacement.read_text()
if current==desired:
    print('Already updated. No changes needed.')
    sys.exit(0)
if 'class FeedbackScreen' not in current and 'fun FeedbackScreen()' not in current:
    raise SystemExit('SAFE STOP: Unexpected FeedbackScreen file; no changes made')
if 'Support server HTTPS URL' not in current or 'val url=endpoint.trim()' not in current:
    raise SystemExit('SAFE STOP: Existing feedback screen differs; no changes made. Please share current FeedbackScreen.kt')
backup=existing.with_name(existing.name+'.backup-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
shutil.copy2(existing,backup)
existing.write_text(desired)
print('Updated',existing)
print('Backup',backup)
print('Users will no longer see or enter a server URL.')
PY
