#!/usr/bin/env python3
"""Guarded Videonic Player v23: lock rotation/UI patch for EXACT v22 or v22.1."""
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
V22 = '3b01b90cdffebb404d8015a391fa1a327b4ba63c52579afba5c6526175a025a8'
V22_1 = '49b803ca6809f9733161352bbd7115188fc396352e544dc3265839d297d75931'
V23 = '1161705c82e676386860ee8a8d7910eee9cfe84a1099575a58d373cd38c3a5aa'


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def stop(reason: str):
    raise SystemExit(f'SAFE STOP: {reason} — no app source was changed.')


if not TARGET.is_file():
    stop('MainActivity.kt was not found; run from /workspaces/Videonic-Player-Lite')
if not SOURCE.is_file():
    stop('ZIP is incomplete: MainActivity.kt missing')
source_bytes = SOURCE.read_bytes()
if sha256(source_bytes) != V23:
    stop('ZIP integrity check failed (v23 source checksum mismatch)')
current_bytes = TARGET.read_bytes()
current_sha = sha256(current_bytes)
if current_sha == V23:
    print('ALREADY APPLIED: Videonic v23, nothing changed.')
    sys.exit(0)
if current_sha not in (V22, V22_1):
    stop('Expected the exact v22 or v22.1 MainActivity.kt; refusing to overwrite custom changes')

stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
backup_dir = ROOT / 'videonic-ui-backups' / ('before-v23-'+stamp)
backup_dir.mkdir(parents=True, exist_ok=False)
backup = backup_dir / 'MainActivity.kt'
shutil.copy2(TARGET, backup)
if sha256(backup.read_bytes()) != current_sha:
    stop('Backup verification failed')

staged = None
try:
    with tempfile.NamedTemporaryFile(dir=TARGET.parent,
            prefix='.videonic-v23-', suffix='.kt', delete=False) as tmp:
        staged = Path(tmp.name)
        tmp.write(source_bytes)
    os.chmod(staged, TARGET.stat().st_mode)
    if sha256(staged.read_bytes()) != V23:
        stop('Staged file verification failed')
    if sha256(TARGET.read_bytes()) != current_sha:
        stop('The source changed while preparing the patch')
    os.replace(staged, TARGET)
finally:
    if staged is not None and staged.exists():
        staged.unlink()

print('SUCCESS: Videonic v23 screen-lock and orientation patch applied.')
print('Previous source:', 'v22' if current_sha == V22 else 'v22.1')
print('Backup:', backup.relative_to(ROOT))
print('v22.1 padding fix is included. Only MainActivity.kt was changed.')
print('Compile in Codespaces before assembling an APK.')
