#!/usr/bin/env python3
"""Safe, atomic incremental Videonic v24 patch for EXACT v23 MainActivity."""
import hashlib
import os
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path.cwd()
PKG=Path(__file__).resolve().parent
TARGET=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE=PKG/'MainActivity.kt'
V23='1161705c82e676386860ee8a8d7910eee9cfe84a1099575a58d373cd38c3a5aa'
V24='d09933031660f05b8225c63e3683a941efeeabe7f685d46045ea2c634ea836b2'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def stop(msg):
    raise SystemExit(f'SAFE STOP: {msg}. No source was changed.')

if not TARGET.is_file():
    stop('Could not find app/src/main/java/com/videonic/lite/MainActivity.kt; run from project root')
if not SOURCE.is_file():
    stop('Patch ZIP is incomplete (missing MainActivity.kt)')
if sha(SOURCE.read_bytes())!=V24:
    stop('Patch source checksum is incorrect')
original=TARGET.read_bytes()
original_hash=sha(original)
if original_hash==V24:
    print('ALREADY APPLIED: Videonic v24; no changes required.')
    sys.exit(0)
if original_hash!=V23:
    stop('Expected the exact v23 MainActivity.kt; custom or older source detected (back it up and check your current version)')

stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
backup_dir=ROOT/'videonic-ui-backups'/('before-v24-'+stamp)
backup_dir.mkdir(parents=True,exist_ok=False)
backup=backup_dir/'MainActivity.kt'
shutil.copy2(TARGET,backup)
if sha(backup.read_bytes())!=V23:
    stop('Failed to verify the original source backup')

staged=None
try:
    with tempfile.NamedTemporaryFile(dir=TARGET.parent,prefix='.videonic-v24-',suffix='.kt',delete=False) as f:
        staged=Path(f.name)
        f.write(SOURCE.read_bytes())
    os.chmod(staged,TARGET.stat().st_mode)
    if sha(staged.read_bytes())!=V24:
        stop('Staged source checksum mismatch')
    if sha(TARGET.read_bytes())!=original_hash:
        stop('MainActivity changed while patch was being prepared')
    os.replace(staged,TARGET)
finally:
    if staged and staged.exists(): staged.unlink()
print('SUCCESS: Videonic v24 MainActivity applied.')
print('Backup:',backup.relative_to(ROOT))
print('Only MainActivity.kt changed. Run a Kotlin build in Codespaces; this script does not build an APK.')
