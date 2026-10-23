#!/usr/bin/env python3
"""Guarded Recycle Bin + Recent progress UI update for Videonic Lite v12."""
from datetime import datetime
from pathlib import Path
import hashlib
import os
import sys

ROOT = Path.cwd()
TARGET = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE = Path(__file__).resolve().parent / 'MainActivity.kt'
EXPECTED_INPUTS = {
    # v12 source, before the small Favorites-count fix
    '19ef078d33882dd19fa1bdad71b35a58fb4f24e393e85ae3f2deeb7928b2cd40',
    # v12 source with the Favorites-count fix applied
    'cfcaf2931ee9a30a59c81520f0144585db360b14ec464ec23bfa47c947000294',
}
EXPECTED_OUTPUT = '1b5568d95fb7f9b079c736fb1f044e4efe58d61cf8cafe2488c9310bc06c2278'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def stop(message):
    sys.exit('SAFE STOP: ' + message + '; no source files were modified.')

if not TARGET.is_file():
    stop('MainActivity.kt missing. Run from the GitHub Codespaces project root')
if not SOURCE.is_file():
    stop('The update source is missing. Unzip the package again')
current = TARGET.read_bytes()
update = SOURCE.read_bytes()
if digest(update) != EXPECTED_OUTPUT:
    stop('The update source failed integrity verification')
if digest(current) == EXPECTED_OUTPUT:
    print('ALREADY APPLIED: v13 Recycle Bin + Recent fixes are installed.')
    raise SystemExit(0)
if digest(current) not in EXPECTED_INPUTS:
    print('Current MainActivity.kt SHA-256:', digest(current))
    stop('Expected v12 or v12 + Favorites-count fix. Please provide your latest source for a tailored patch')

backup_dir = ROOT / 'videonic-ui-backups'
backup_dir.mkdir(exist_ok=True)
backup = backup_dir / ('before-v13-recycle-recent-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.kt')
backup.write_bytes(current)
if backup.read_bytes() != current:
    stop('Backup verification failed')

temp = TARGET.with_name(TARGET.name + '.v13tmp')
try:
    temp.write_bytes(update)
    if digest(temp.read_bytes()) != EXPECTED_OUTPUT:
        raise RuntimeError('Temporary file integrity check failed')
    os.replace(temp, TARGET)
except Exception as exc:
    if temp.exists():
        temp.unlink()
    if TARGET.read_bytes() != current:
        TARGET.write_bytes(current)
    stop(f'File update failed; original restored: {exc}')

print('SUCCESS: v13 Recycle Bin + Recent progress UI installed.')
print('Also retains the v12 Favorites count fix.')
print('Updated: MainActivity.kt only')
print('Backup:', backup)
