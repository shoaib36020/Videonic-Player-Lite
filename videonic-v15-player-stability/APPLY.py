#!/usr/bin/env python3
"""v15 playback stability/player UI patch; exact-version validation, backups, atomic writes."""
from __future__ import annotations
from pathlib import Path
from datetime import datetime
import hashlib
import os
import sys

ROOT=Path.cwd()
PACKAGE=Path(__file__).resolve().parent
DEST={
    'MainActivity.kt':ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt',
    'VideonicPlaybackService.kt':ROOT/'app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt',
}
EXPECTED_BASE={
    # MainActivity.kt = original v14 + successful v14.1 opt-in fix
    'MainActivity.kt':'3ca1b061db477df6f9231b1b0b2477d736d07eb6b7eb2292db20bf70dc10f576',
    # Service = v14 original; v14.1 only updated MainActivity.kt
    'VideonicPlaybackService.kt':'a692ae9fe622fd1d6238ccd7ff0c1e3fe76576ef7eb296693a5f03eeafbb3c74',
}
EXPECTED_NEW={
    'MainActivity.kt':'86fb72ecd1374720e841cfdc4b3c73340c08350192c8a3ccefdcaa777ced9ae1',
    'VideonicPlaybackService.kt':'80234b7f4919b1bcc8e89062ba7a651f25daad18f75ae4e2fc9e1f2373afbfdb',
}

def hash_bytes(data:bytes)->str:return hashlib.sha256(data).hexdigest()
def stop(s:str):sys.exit('SAFE STOP: '+s+' No project files changed.')

if any(not p.is_file() for p in DEST.values()):
    stop('Run this script from the Videonic project root.')
if any(not (PACKAGE/name).is_file() for name in DEST):
    stop('v15 source files missing; extract the ZIP again.')
old={name:p.read_bytes() for name,p in DEST.items()}
new={name:(PACKAGE/name).read_bytes() for name in DEST}
for name in DEST:
    if hash_bytes(new[name])!=EXPECTED_NEW[name]:stop('Package integrity failure for '+name+'.')
if all(old[name]==new[name] for name in DEST):
    print('ALREADY APPLIED: v15 player stability and UI patch is installed.')
    sys.exit(0)
for name in DEST:
    h=hash_bytes(old[name])
    if h not in (EXPECTED_BASE[name],EXPECTED_NEW[name]):
        print(f'{name} SHA256: {h}')
        stop('Unexpected local source in '+name+'; send latest Kotlin sources for a tailored update.')

backup=ROOT/'videonic-ui-backups'/('before-v15-player-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    backup.mkdir(parents=True,exist_ok=False)
    for name,data in old.items():
        dest=backup/name
        dest.write_bytes(data)
        if dest.read_bytes()!=data:raise OSError('Backup validation failed: '+name)
except Exception as exc:stop('Backup error: '+str(exc))

temps={};replaced=[]
try:
    for name,target in DEST.items():
        if old[name]==new[name]:continue
        temporary=target.with_name(target.name+'.v15tmp')
        temporary.write_bytes(new[name])
        if hash_bytes(temporary.read_bytes())!=EXPECTED_NEW[name]:
            raise OSError('Temporary source failed integrity validation')
        temps[name]=temporary
    for name,temporary in temps.items():
        os.replace(temporary,DEST[name])
        replaced.append(name)
except Exception as exc:
    for name in replaced:
        DEST[name].write_bytes(old[name])
    for temporary in temps.values():temporary.unlink(missing_ok=True)
    stop('Unable to apply v15; original source restored: '+str(exc))
print('SUCCESS: Videonic Lite v15 player recovery, gestures and redesigned sheets applied.')
print('Backup:',backup)
print('Next: compileDebugKotlin in Codespaces; do not assemble APK until compilation succeeds.')
