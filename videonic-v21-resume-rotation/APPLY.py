#!/usr/bin/env python3
"""Guarded Videonic Player v21 MainActivity-only installer for v20."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import os
import shutil
import sys
import tempfile

ROOT=Path.cwd()
PATCH=Path(__file__).resolve().parent
TARGET=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE=PATCH/'MainActivity.kt'
V20_SHA='58a6568f475ea3717b75dc929bf1829fa409fcb84992315d6eee099d72fc4ea4'
V21_SHA=''

def hash_bytes(data): return hashlib.sha256(data).hexdigest()

def stop(reason):
    raise SystemExit(f'SAFE STOP: {reason} — no app source was changed.')

if not TARGET.is_file(): stop('No MainActivity.kt at the expected project path')
if not SOURCE.is_file(): stop('The patch is incomplete: MainActivity.kt is missing')
new_bytes=SOURCE.read_bytes()
if not new_bytes.startswith(b'package com.videonic.lite\n'):
    stop('Patch source failed the package check')
current=TARGET.read_bytes()
if current==new_bytes:
    print('ALREADY APPLIED: Videonic Player v21, no changes needed.')
    sys.exit(0)
if hash_bytes(current)!=V20_SHA:
    stop('Expected Videonic Player v20 source; your MainActivity.kt differs. Do not force this update')

stamp=datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f')
backup_dir=ROOT/'videonic-ui-backups'/('before-v21-'+stamp)
backup_dir.mkdir(parents=True, exist_ok=False)
backup=backup_dir/'MainActivity.kt'
shutil.copy2(TARGET,backup)
if backup.read_bytes()!=current: stop('Backup verification failed')
with tempfile.NamedTemporaryFile(dir=TARGET.parent,prefix='.videonic-v21-',suffix='.kt',delete=False) as f:
    temp=Path(f.name)
    f.write(new_bytes)
try:
    os.chmod(temp,TARGET.stat().st_mode)
    if temp.read_bytes()!=new_bytes: stop('Staged file verification failed')
    os.replace(temp,TARGET)
finally:
    if temp.exists(): temp.unlink()
print('SUCCESS: Videonic Player v21 resume, rotation and compact speed patch applied.')
print('Backup:',backup.relative_to(ROOT))
print('Only MainActivity.kt changed. Compile before assembling an APK.')
