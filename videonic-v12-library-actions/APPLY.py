#!/usr/bin/env python3
"""Videonic v12 exact-source installer. No other files are touched."""
from datetime import datetime
from pathlib import Path
import hashlib
import os
import sys

ROOT=Path.cwd()
DST=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
SRC=Path(__file__).resolve().parent/'MainActivity.kt'
# v11 equals v10 source with v11 navigation-only patch applied.
SUPPORTED_V11='97ba4ae48f7a35bd003cc337b9fe785193f8314de16981153f9160831b590291'
EXPECTED_PATCH='19ef078d33882dd19fa1bdad71b35a58fb4f24e393e85ae3f2deeb7928b2cd40'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def stop(message):sys.exit('SAFE STOP: '+message+'; nothing changed.')
if not DST.is_file():stop('MainActivity.kt not found. Run from the repository root')
if not SRC.is_file():stop('Patch source missing')
current=DST.read_bytes()
update=SRC.read_bytes()
if sha(update)!=EXPECTED_PATCH:stop('Patch source failed checksum verification')
if sha(current)==sha(update):
    print('ALREADY APPLIED: Videonic v12 is installed. No changes needed.')
    raise SystemExit(0)
if sha(current)!=SUPPORTED_V11:
    print('Current MainActivity.kt SHA-256:',sha(current))
    stop('Source differs from v11. Do not overwrite it. Send the latest MainActivity.kt for a compatible patch')
backup_dir=ROOT/'videonic-ui-backups'
backup_dir.mkdir(exist_ok=True)
backup=backup_dir/('before-v12-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'.kt')
backup.write_bytes(current)
if backup.read_bytes()!=current:stop('Failed to verify backup')
try:
    temp=DST.with_suffix('.kt.v12tmp')
    temp.write_bytes(update)
    if sha(temp.read_bytes())!=EXPECTED_PATCH:raise RuntimeError('Write verification failed')
    os.replace(temp,DST)
except Exception as error:
    if DST.read_bytes()!=current: DST.write_bytes(current)
    stop(f'Update failed; original restored: {error}')
print('SUCCESS: Videonic v12 library/actions update installed.')
print('Changed: MainActivity.kt only')
print('Backup:',backup)
