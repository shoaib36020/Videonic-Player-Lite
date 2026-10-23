#!/usr/bin/env python3
"""Apply Videonic UI v6 safely to exact v5 source; never overwrite a differing version."""
from pathlib import Path
import hashlib, datetime, sys

TARGET = Path('app/src/main/java/com/videonic/lite/MainActivity.kt')
BASE_SHA = '4938aee239609ecbe7a913aa4f1d4cf510f9bfb5d87a80745eb901c6d87f50bf'
PATCH_SHA = 'f5e0a902d87682b086dcf5f244a6b1e0a89c2513238dbbfe72478102377b55fe'
UPDATED = Path(__file__).resolve().parent / 'MainActivity.kt'

def sha(data): return hashlib.sha256(data).hexdigest()

if not TARGET.is_file():
    sys.exit('STOP: Run this inside the Videonic-Player-Lite repository root.')
current = TARGET.read_bytes()
if sha(current) == PATCH_SHA:
    print('ALREADY APPLIED: Videonic v6 is installed. No files changed.')
    sys.exit(0)
if sha(current) != BASE_SHA:
    print('STOP: Your MainActivity.kt is different from the tested v5 source.')
    print('No files changed. Current SHA256:', sha(current))
    print('Please share the latest MainActivity.kt instead of forcing an overwrite.')
    sys.exit(1)
patch=UPDATED.read_bytes()
if sha(patch)!=PATCH_SHA:
    sys.exit('STOP: ZIP integrity check failed. No files changed.')
backup_dir=Path('videonic-ui-backups')
backup_dir.mkdir(exist_ok=True)
backup=backup_dir/('MainActivity.before-v6.'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.kt')
backup.write_bytes(current)
TARGET.write_bytes(patch)
print('SUCCESS: Videonic v6 library and dark theme update installed.')
print('Backup:',backup)
print('Only MainActivity.kt was modified; playback service, vault and feedback remain unchanged.')
