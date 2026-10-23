#!/usr/bin/env python3
"""Guarded v16.6 MKV seek+portrait-surface patch (no added native decoder)."""
from pathlib import Path
from datetime import datetime
from hashlib import sha256
import os,sys
ROOT=Path.cwd(); SRC=Path(__file__).resolve().parent
FILES=[('app/src/main/java/com/videonic/lite/MainActivity.kt', 'MainActivity.kt'), ('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt', 'VideonicPlaybackService.kt'), ('app/src/main/java/com/videonic/lite/player/MkvClusterSeekIndex.kt', 'MkvClusterSeekIndex.kt'), ('app/src/main/res/layout/videonic_player_texture.xml', 'res/layout/videonic_player_texture.xml')]
OLD=['b91f52b780f43804d5299811e9d4ade013eade315d8c1a4d719773c9f0cdbbbf', '665e06796907d24dc396458813a14aa3519dd87bd3105b37b7b79c50ce2b1577', None, None]
NEW=['18281c11d19851ca4742bd2d5a286845ea4bba06cc4858205cb67864ee40503a', 'cf102bf113c2a96d725a7232b2533762505adee44f78db06e1d27a8839b0287c', '188aa0c7cbeb08d080cab11d41401a6eb6b15ec8b415b7659207c5424becc8fc', 'f0af7f558f164016b71b52d07d90f84319aa1f52ffe635d9289b8828453c3726']
def digest(b):return sha256(b).hexdigest()
def stop(message):sys.exit('SAFE STOP: '+message+' Nothing has been changed.')
if not (ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt').is_file():
 stop('Run this script from the original Android project root.')
source=[];dest=[]
for relative,patch in FILES:
 f=SRC/patch
 if not f.is_file(): stop('Missing ZIP file '+patch)
 source.append(f.read_bytes());dest.append(ROOT/relative)
if [digest(v) for v in source]!=NEW:stop('ZIP file integrity check failed.')
current=[d.read_bytes() if d.exists() else None for d in dest]
actual=[digest(v) if v is not None else None for v in current]
if actual==NEW:
 print('ALREADY APPLIED: v16.6 MKV seek recovery is installed.');sys.exit(0)
if actual!=OLD:
 for path,checksum in zip(dest,actual):print('Current:',path,'SHA256:',checksum)
 stop('Your files differ from the unmodified v16.5 patch. Please share your latest sources; do not overwrite them.')
backup=ROOT/'videonic-ui-backups'/('before-v16-6-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
 for (relative,_),data in zip(FILES,current):
  if data is None:continue
  b=backup/relative;b.parent.mkdir(parents=True,exist_ok=True);b.write_bytes(data)
  if digest(b.read_bytes())!=digest(data):raise IOError('Backup verification failed: '+str(b))
except Exception as exc:stop('Could not create backups: '+str(exc))
staged=[]
try:
 for path,data,expected in zip(dest,source,NEW):
  path.parent.mkdir(parents=True,exist_ok=True)
  temp=path.with_name(path.name+'.v16-6-tmp')
  temp.write_bytes(data);staged.append(temp)
  if digest(temp.read_bytes())!=expected:raise IOError('Staging verification failed')
 for path,temp in zip(dest,staged):os.replace(temp,path)
except Exception as exc:
 for path,data in zip(dest,current):
  if data is None:path.unlink(missing_ok=True)
  else:path.write_bytes(data)
 stop('Installation failed; originals restored: '+str(exc))
finally:
 for temp in staged:temp.unlink(missing_ok=True)
print('SUCCESS: v16.6 installed. Backups:',backup)
print('IMPORTANT: Run Gradle compile first. This experimental MKV indexer needs device testing.')
