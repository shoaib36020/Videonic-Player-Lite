#!/usr/bin/env python3
"""Apply UI-only v33 over the exact Videonic v32 MainActivity source.

No Gradle, signing key, Media3 service, data storage, or splash resources touched.
"""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import os
import shutil
import tempfile

ROOT = Path.cwd().resolve()
PATCH = Path(__file__).resolve().parent
TARGET = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE = PATCH / 'MainActivity.kt'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
BASE_SHA = '5665e4f962864020b601fb5443368eb14b10fd61a8bbfe3d5317119494240ecd'
NEW_SHA = '7b0a91d5103aff972a62cd25ae76ca68f44048c06e016f53ebb89de098cefe34'


def sha(p: Path):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def stop(msg: str):
    raise SystemExit('SAFE STOP: '+msg+' (no app source overwritten)')


if not SOURCE.is_file() or sha(SOURCE) != NEW_SHA:
    stop('The v33 package is incomplete or corrupted')
if not MANIFEST.is_file() or not TARGET.is_file():
    stop('Run from the root of your Videonic Android project')
actual = sha(TARGET)
if actual == NEW_SHA:
    print('ALREADY APPLIED: Videonic v33 clean UI patch is installed; no changes.')
    raise SystemExit(0)
if actual != BASE_SHA:
    stop('Existing MainActivity.kt differs from v32; no files overwritten. Share the message before applying anything else')
backup = ROOT/'videonic-ui-backups'/('before-v33-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))/TARGET.relative_to(ROOT)
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(TARGET, backup)
if sha(backup)!= BASE_SHA:
    stop('Source backup verification failed')
fd,tmp=tempfile.mkstemp(prefix='.videonic-v33-',dir=TARGET.parent)
staged=Path(tmp)
try:
    with os.fdopen(fd,'wb') as handle:
        handle.write(SOURCE.read_bytes())
        handle.flush()
        os.fsync(handle.fileno())
    if sha(staged)!=NEW_SHA:
        raise RuntimeError('Staged source checksum mismatch')
    os.chmod(staged,TARGET.stat().st_mode)
    if sha(TARGET)!=BASE_SHA:
        stop('MainActivity.kt changed during patch application; retry against a clean v32 source')
    os.replace(staged,TARGET)
except BaseException:
    staged.unlink(missing_ok=True)
    raise
print('SUCCESS: Videonic v33 clean UI / Recent ordering update installed.')
print('Verified original backup:',backup)
print('Only MainActivity.kt was modified. The application version was not changed.')
