#!/usr/bin/env python3
"""Videonic Player v22: safe source-only patch against the successful v21 build."""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import os
import shutil
import sys
import tempfile

ROOT = Path.cwd()
PKG = Path(__file__).resolve().parent
TARGET = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE = PKG / 'MainActivity.kt'
EXPECTED_V21 = 'f2bf5e4e5e90e2926a8cb8127f49aadf4d591f101f1116c238d5ac613b679dd3'
EXPECTED_V22 = '3b01b90cdffebb404d8015a391fa1a327b4ba63c52579afba5c6526175a025a8'

def digest(b): return hashlib.sha256(b).hexdigest()
def stop(reason): raise SystemExit('SAFE STOP: ' + reason + ' — no source files were modified.')

if not TARGET.is_file(): stop('MainActivity.kt was not found in the expected project root')
if not SOURCE.is_file(): stop('Patch MainActivity.kt is missing')
new_data=SOURCE.read_bytes()
old_data=TARGET.read_bytes()
if digest(new_data)!=EXPECTED_V22: stop('ZIP source integrity check failed')
if digest(old_data)==EXPECTED_V22:
    print('ALREADY APPLIED: Videonic Player v22; nothing changed.')
    sys.exit(0)
if digest(old_data)!=EXPECTED_V21:
    stop('Expected the compiled v21 MainActivity.kt; this file differs. Send the current file for a compatible patch')

stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
backup_dir=ROOT/'videonic-ui-backups'/('before-v22-'+stamp)
backup_dir.mkdir(parents=True,exist_ok=False)
backup=backup_dir/'MainActivity.kt'
shutil.copy2(TARGET,backup)
if digest(backup.read_bytes())!=EXPECTED_V21: stop('Backup verification failed')

staged=None
try:
    with tempfile.NamedTemporaryFile(dir=TARGET.parent,prefix='.videonic-v22-',suffix='.kt',delete=False) as tmp:
        staged=Path(tmp.name)
        tmp.write(new_data)
    os.chmod(staged,TARGET.stat().st_mode)
    if digest(staged.read_bytes())!=EXPECTED_V22: stop('Staged-file integrity check failed')
    # Protect changes from an external edit while the backup was being created.
    if digest(TARGET.read_bytes())!=EXPECTED_V21: stop('Current source changed while preparing the update')
    os.replace(staged,TARGET)
finally:
    if staged and staged.exists(): staged.unlink()
print('SUCCESS: Videonic Player v22 fullscreen, resume prompt and mini speed selector applied.')
print('Backup:',backup.relative_to(ROOT))
print('Only MainActivity.kt changed. Compile in Codespaces before building an APK.')
