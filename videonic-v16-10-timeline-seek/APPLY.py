#!/usr/bin/env python3
"""Guarded v16.10 source patch. Requires exactly the packaged v16.9 files."""
import hashlib
import os
import sys
from datetime import datetime
from pathlib import Path

PROJECT = Path.cwd()
PACKAGE = Path(__file__).resolve().parent
ENTRIES = [
    ('app/src/main/java/com/videonic/lite/MainActivity.kt', 'MainActivity.kt',
     'd2e89b51ee6bf7aa3c2cccce9760901c29562d6224f26daba711eb4db83eaecf'),
    ('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt', 'VideonicPlaybackService.kt',
     '1bbac9fbabb4c979620107760368eefed1f1915500655fb3e8753cc9b9c63259'),
    ('app/src/main/java/com/videonic/lite/player/MkvClusterSeekIndex.kt', 'MkvClusterSeekIndex.kt',
     '125c89ba1afb01f1be6862375591ee6d87cf3d3d199e9f4078cc5ca0738a7876'),
]

def hashed(data): return hashlib.sha256(data).hexdigest()
def stop(message): sys.exit('SAFE STOP: '+message+'; no project files modified.')

paths=[]
previous=[]
updated=[]
for relative, name, old_sha in ENTRIES:
    destination=PROJECT/relative
    payload=PACKAGE/name
    if not destination.is_file():stop('Missing source '+relative)
    if not payload.is_file():stop('Missing payload '+name)
    before=destination.read_bytes()
    after=payload.read_bytes()
    paths.append(destination)
    previous.append(before)
    updated.append(after)

current=[hashed(x) for x in previous]
old_hashes=[item[2] for item in ENTRIES]
new_hashes=[hashed(x) for x in updated]
if current==new_hashes:
    print('ALREADY APPLIED: v16.10 is installed. Nothing changed.')
    sys.exit(0)
if current!=old_hashes:
    for path,actual in zip(paths,current):print('CURRENT:',path,actual)
    stop('Current source differs from exact v16.9; do not force a replacement')
if old_hashes==new_hashes:stop('Empty or incorrect patch package')

backup=PROJECT/'videonic-ui-backups'/('before-v16-10-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for (relative,_,_),data in zip(ENTRIES,previous):
        dest=backup/relative
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(data)
        if hashed(dest.read_bytes())!=hashed(data):raise OSError('backup verification failed')
except OSError as e:stop('Backup failed: '+str(e))

staged=[]
try:
    for path,data in zip(paths,updated):
        tmp=path.with_suffix(path.suffix+'.v1610-tmp')
        tmp.write_bytes(data)
        if hashed(tmp.read_bytes())!=hashed(data):raise OSError('staging verification failed')
        staged.append(tmp)
    for path,tmp in zip(paths,staged):os.replace(tmp,path)
except Exception as e:
    for path,original in zip(paths,previous):path.write_bytes(original)
    stop('Installer failed and source files were restored: '+str(e))
finally:
    for tmp in staged:tmp.unlink(missing_ok=True)
print('SUCCESS: Videonic Lite v16.10 source update applied.')
print('Backup:',backup)
print('Next: gradle :app:compileDebugKotlin in Codespaces before building an APK.')
