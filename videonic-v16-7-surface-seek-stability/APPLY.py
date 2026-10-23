#!/usr/bin/env python3
"""Guarded install for Videonic Lite v16.7; only MainActivity and PlaybackService."""
from pathlib import Path
from hashlib import sha256
from datetime import datetime
import os,sys
root=Path.cwd()
patch=Path(__file__).resolve().parent
files=[('app/src/main/java/com/videonic/lite/MainActivity.kt', 'MainActivity.kt'), ('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt', 'VideonicPlaybackService.kt')]
old_hashes=['18281c11d19851ca4742bd2d5a286845ea4bba06cc4858205cb67864ee40503a', 'cf102bf113c2a96d725a7232b2533762505adee44f78db06e1d27a8839b0287c']
new_hashes=['0ae6061da1bb8dc435bc265d5219fe8c392734b95f24d767079b6e735831e529', 'b170fab1374911d3c8afe71ff1babe9c8d6b534ef4cd61c592f83ebaaf0b7891']
def digest(data):return sha256(data).hexdigest() if data is not None else None
def stop(msg):sys.exit('SAFE STOP: '+msg+' No project files were changed.')
paths=[root/rel for rel,_ in files]
if not paths[0].is_file():stop('Run this script from /workspaces/Videonic-Player-Lite')
source=[]
for _,name in files:
    f=patch/name
    if not f.is_file():stop('Missing patch source: '+name)
    source.append(f.read_bytes())
if [digest(data) for data in source]!=new_hashes:stop('Patch integrity check failed')
current=[p.read_bytes() if p.is_file() else None for p in paths]
actual=[digest(data) for data in current]
if actual==new_hashes:
    print('ALREADY APPLIED: v16.7 is installed.');sys.exit(0)
if actual!=old_hashes:
    for path,d in zip(paths,actual):print('Current SHA256:',path,d)
    stop('Unexpected source version. Expected the unmodified v16.6 patch. Do not force overwrite.')
backup=root/'videonic-ui-backups'/('before-v16-7-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for (relative,_),data in zip(files,current):
        destination=backup/relative
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_bytes(data)
        if digest(destination.read_bytes())!=digest(data):raise OSError('Backup verification failed')
except Exception as e:stop('Cannot create backup: '+str(e))
temps=[]
try:
    for path,data,expected in zip(paths,source,new_hashes):
        temp=path.with_suffix(path.suffix+'.v16-7-stage')
        temp.write_bytes(data);temps.append(temp)
        if digest(temp.read_bytes())!=expected:raise OSError('Staged file verification failed')
    for path,temp in zip(paths,temps):os.replace(temp,path)
except Exception as e:
    for path,data in zip(paths,current):
        if data is not None:path.write_bytes(data)
    stop('Apply failed, original files restored: '+str(e))
finally:
    for temp in temps:temp.unlink(missing_ok=True)
print('SUCCESS: v16.7 applied. Backups:',backup)
print('NEXT: gradle :app:compileDebugKotlin (do not build APK until compilation succeeds)')
