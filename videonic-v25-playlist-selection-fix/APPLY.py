#!/usr/bin/env python3
"""Install Videonic Player Lite v25 on exact v24, with backup and integrity guard."""
import hashlib
import os
import shutil
import sys
import tempfile
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path.cwd()
PACKAGE=Path(__file__).resolve().parent
TARGET=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE=PACKAGE/'MainActivity.kt'
V24='d09933031660f05b8225c63e3683a941efeeabe7f685d46045ea2c634ea836b2'
V25='a30f4767428f0cdcb29622d4c97f8fa268711e519ba75890ed89895c7dafbf07'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def stop(message):
    raise SystemExit(f'SAFE STOP: {message}. No source changed.')

if not TARGET.is_file():
    stop('MainActivity.kt not found; run from /workspaces/Videonic-Player-Lite')
if not SOURCE.is_file():
    stop('Patch package incomplete')
if sha(SOURCE.read_bytes()) != V25:
    stop('Patch source checksum mismatch')
original=TARGET.read_bytes()
original_sha=sha(original)
if original_sha == V25:
    print('ALREADY APPLIED: Videonic v25, nothing to change.')
    sys.exit(0)
if original_sha != V24:
    stop('Requires exact v24 MainActivity.kt; other versions or customized file detected')

stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
backup_dir=ROOT/'videonic-ui-backups'/('before-v25-'+stamp)
backup_dir.mkdir(parents=True, exist_ok=False)
backup=backup_dir/'MainActivity.kt'
shutil.copy2(TARGET,backup)
if sha(backup.read_bytes()) != V24:
    stop('Backup integrity check failed')

staged=None
try:
    with tempfile.NamedTemporaryFile(dir=TARGET.parent,prefix='.videonic-v25-',suffix='.kt',delete=False) as f:
        staged=Path(f.name)
        f.write(SOURCE.read_bytes())
    os.chmod(staged,TARGET.stat().st_mode)
    if sha(staged.read_bytes()) != V25:
        stop('Staged checksum mismatch')
    if sha(TARGET.read_bytes()) != original_sha:
        stop('MainActivity.kt changed during patch')
    os.replace(staged,TARGET)
finally:
    if staged and staged.exists():
        staged.unlink()
print('SUCCESS: Videonic v25 playlist selection update applied.')
print('Backup:',backup.relative_to(ROOT))
print('Modified: app/src/main/java/com/videonic/lite/MainActivity.kt')
print('Next: run the Gradle APK build. The patch installer does not compile Android source.')
