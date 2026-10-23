#!/usr/bin/env python3
"""Apply the exact Videonic v32 unified-speed update to a v30/v31 source tree.

Run from the Android project's root. Fail closed if MainActivity differs, preserve
an integrity-checked backup, and never touch playback-service or resources.
"""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import os
import shutil
import tempfile

ROOT = Path.cwd()
PATCH = Path(__file__).resolve().parent
TARGET = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE = PATCH / 'MainActivity.kt'
BASE_SHA = 'b3703a82c2d1f6fd7caa41b8c1aea7c054e5dd43324b888ce8ca0faf51bc109e'
NEW_SHA = '5665e4f962864020b601fb5443368eb14b10fd61a8bbfe3d5317119494240ecd'


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stop(message: str):
    raise SystemExit('SAFE STOP: ' + message + ' (nothing changed)')


if not SOURCE.is_file() or sha(SOURCE) != NEW_SHA:
    stop('The v32 archive is missing or corrupt')
if not (ROOT / 'app/src/main/AndroidManifest.xml').is_file() or not TARGET.is_file():
    stop('Run from the existing Videonic project root')

actual = sha(TARGET)
if actual == NEW_SHA:
    print('ALREADY APPLIED: Videonic v32 speed sync fix is installed; no changes.')
    raise SystemExit(0)
if actual != BASE_SHA:
    stop('MainActivity.kt does not match the supported v30/v31 version; no overwrite')

backup = ROOT / 'videonic-ui-backups' / ('before-v32-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')) / TARGET.relative_to(ROOT)
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(TARGET, backup)
if sha(backup) != BASE_SHA:
    stop('Backup integrity verification failed')

fd, staging_path = tempfile.mkstemp(prefix='.videonic-v32-', dir=TARGET.parent)
staging = Path(staging_path)
try:
    with os.fdopen(fd, 'wb') as f:
        f.write(SOURCE.read_bytes())
        f.flush()
        os.fsync(f.fileno())
    if sha(staging) != NEW_SHA:
        raise RuntimeError('Staged source checksum did not match')
    os.chmod(staging, TARGET.stat().st_mode)
    if sha(TARGET) != BASE_SHA:
        stop('MainActivity changed while applying the patch')
    os.replace(staging, TARGET)
except BaseException:
    staging.unlink(missing_ok=True)
    if sha(TARGET) != BASE_SHA:
        shutil.copy2(backup, TARGET)
    raise

print('SUCCESS: Videonic v32 unified speed sync installed.')
print('Backup:', backup)
print('Reset now returns to 1.0x; speed menu and speed lock share the same speed state.')
print('Resources, playback service, MKV resume, orientation, and playlists were not changed.')
