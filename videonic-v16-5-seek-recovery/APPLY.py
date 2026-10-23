#!/usr/bin/env python3
"""Guarded v16.5 experimental seek-recovery installer (no new codec binaries)."""
from pathlib import Path
from hashlib import sha256
from datetime import datetime
import os,sys
ROOT=Path.cwd()
SRC=Path(__file__).resolve().parent
NAMES=['MainActivity.kt','VideonicPlaybackService.kt']
DEST=[Path('app/src/main/java/com/videonic/lite/MainActivity.kt'),Path('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt')]
OLD=['73cb1a44eb9e5b9dce97e4f63e1cd607fa394d3047da6b11af1f1f3889fa3f12', '259484396d092b3adab98ce4cc2368a08083b357597a47928571e1ff7ba09d34']
NEW=['b91f52b780f43804d5299811e9d4ade013eade315d8c1a4d719773c9f0cdbbbf', '665e06796907d24dc396458813a14aa3519dd87bd3105b37b7b79c50ce2b1577']
def digest(data):return sha256(data).hexdigest()
def stop(msg):sys.exit('SAFE STOP: '+msg+' No project source has been changed.')
if not all((SRC/n).is_file() for n in NAMES):stop('Patch files missing; extract the full ZIP.')
if not all((ROOT/d).is_file() for d in DEST):stop('Run from the Videonic Android project root.')
original=[(ROOT/d).read_bytes() for d in DEST]
updated=[(SRC/n).read_bytes() for n in NAMES]
if [digest(v) for v in updated]!=NEW:stop('Patch ZIP contents do not match expected SHA256.')
actual=[digest(v) for v in original]
if actual==NEW:
 print('ALREADY APPLIED: v16.5 seek recovery patch is installed.');sys.exit(0)
if actual!=OLD:
 for d,h in zip(DEST,actual):print('Current:',d,'SHA256',h)
 stop('Current files are not unmodified v16.4. Do not overwrite; share your current Kotlin sources for a tailored patch.')
backup=ROOT/'videonic-ui-backups'/('before-v16-5-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
 for d,b in zip(DEST,original):
  target=backup/d;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
  if digest(target.read_bytes())!=digest(b):raise OSError('Backup checksum mismatch: '+str(d))
except Exception as e:stop('Backup creation failed: '+str(e))
staged=[]
try:
 for d,b,n in zip(DEST,updated,NEW):
  dest=ROOT/d
  temporary=dest.with_name(dest.name+'.v16-5-tmp')
  temporary.write_bytes(b);staged.append(temporary)
  if digest(temporary.read_bytes())!=n:raise OSError('Staging checksum mismatch')
 for d,tmp in zip(DEST,staged):os.replace(tmp,ROOT/d)
except Exception as e:
 for d,b in zip(DEST,original):
  if digest((ROOT/d).read_bytes())!=digest(b):(ROOT/d).write_bytes(b)
 stop('Unable to install; restored original sources: '+str(e))
finally:
 for tmp in staged:tmp.unlink(missing_ok=True)
print('SUCCESS: v16.5 optional in-app seek recovery installed.')
print('Verified backup:',backup)
print('NEXT: Compile :app:compileDebugKotlin in Codespaces before assembling an APK.')
