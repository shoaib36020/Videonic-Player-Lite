#!/usr/bin/env python3
"""Videonic v16.3: guarded patch for portrait aspect reset and CBR seeking."""
from pathlib import Path
import hashlib,os,sys
from datetime import datetime

ROOT=Path.cwd()
DIR=Path(__file__).resolve().parent
rel_paths=(
    Path('app/src/main/java/com/videonic/lite/MainActivity.kt'),
    Path('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt'),
)
inputs=(DIR/'MainActivity.kt', DIR/'VideonicPlaybackService.kt')
OLD=('23b5ca72517123e6d5886c6be18b01ab6d09bc824a18789a5182b65e74790027','80234b7f4919b1bcc8e89062ba7a651f25daad18f75ae4e2fc9e1f2373afbfdb')
NEW=('5ef61cf4d856ccbbd18f4f4d77f446aa5c997f6b9428608321a73f7e042ab603','c9ebcdd5a6991059cd167dcd9d177a770945051426ff543c887e9048eddc0f9a')
def sha(b):return hashlib.sha256(b).hexdigest()
def stop(msg):sys.exit('SAFE STOP: '+msg+' No project source files were modified.')
if not all((ROOT/p).is_file() for p in rel_paths):
    stop('Expected Android project sources not found. Run from Codespaces project root.')
if not all(p.is_file() for p in inputs):stop('Update files are missing; extract ZIP again.')
original=[(ROOT/p).read_bytes() for p in rel_paths]
incoming=[p.read_bytes() for p in inputs]
if [sha(b) for b in incoming] != list(NEW):stop('ZIP contents failed integrity check.')
actual=[sha(b) for b in original]
if actual==list(NEW):
    print('ALREADY APPLIED: v16.3 rotation and seeking update.')
    sys.exit(0)
if actual!=list(OLD):
    print('Current MainActivity SHA256:',actual[0])
    print('Current PlaybackService SHA256:',actual[1])
    stop('This patch expects distributed v16.2 and the unchanged v15 player service. Send current source for a tailored patch.')
backup=ROOT/'videonic-ui-backups'/('before-v16-3-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for p,b in zip(rel_paths,original):
        d=backup/p;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes(b)
        if sha(d.read_bytes())!=sha(b):raise IOError('Backup verification failure')
except Exception as ex:stop('Backup failed: '+str(ex))
# Stage everything and verify BEFORE replacing either source.
staged=[]
try:
    for p,b,new in zip(rel_paths,incoming,NEW):
        dst=ROOT/p
        tmp=dst.with_name(dst.name+'.v16-3-temp')
        tmp.write_bytes(b)
        staged.append(tmp)
        if sha(tmp.read_bytes())!=new:raise IOError('Staged checksum mismatch')
    for p,tmp in zip(rel_paths,staged):os.replace(tmp,ROOT/p)
except Exception as ex:
    for p,b in zip(rel_paths,original):
        dst=ROOT/p
        if sha(dst.read_bytes())!=sha(b):dst.write_bytes(b)
    stop('Install failed: '+str(ex))
finally:
    for tmp in staged:tmp.unlink(missing_ok=True)
print('SUCCESS: Videonic v16.3 applied. Backups:',backup)
print('NEXT: compile Kotlin in Codespaces. Then build APK & check <=30 MB.')
