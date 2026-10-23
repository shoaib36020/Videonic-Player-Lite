#!/usr/bin/env python3
"""Guarded Videonic Lite v16.4 mini-player surface and keyframe-seeking patch."""
from pathlib import Path
from datetime import datetime
import hashlib,os,sys
ROOT=Path.cwd()
DIR=Path(__file__).resolve().parent
DESTS=[Path('app/src/main/java/com/videonic/lite/MainActivity.kt'),Path('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt')]
INPUTS=[DIR/'MainActivity.kt',DIR/'VideonicPlaybackService.kt']
OLD=['5ef61cf4d856ccbbd18f4f4d77f446aa5c997f6b9428608321a73f7e042ab603', 'c9ebcdd5a6991059cd167dcd9d177a770945051426ff543c887e9048eddc0f9a']
NEW=['73cb1a44eb9e5b9dce97e4f63e1cd607fa394d3047da6b11af1f1f3889fa3f12', '259484396d092b3adab98ce4cc2368a08083b357597a47928571e1ff7ba09d34']
def digest(v): return hashlib.sha256(v).hexdigest()
def stop(message): sys.exit('SAFE STOP: '+message+' No source files modified.')
if not all(p.is_file() for p in INPUTS):stop('ZIP contents missing. Extract the full ZIP.')
if not all((ROOT/p).is_file() for p in DESTS):stop('Run from the actual Android project root.')
current=[(ROOT/p).read_bytes() for p in DESTS]
incoming=[p.read_bytes() for p in INPUTS]
if [digest(b) for b in incoming]!=NEW:stop('Update file SHA256 mismatch.')
actual=[digest(b) for b in current]
if actual==NEW:
 print('ALREADY APPLIED: v16.4 is installed.')
 sys.exit(0)
if actual!=OLD:
 for p,a in zip(DESTS,actual):print('File:',p,'SHA256:',a)
 stop('Expected the unmodified compiled v16.3 files. Do not overwrite edits; send the current files for a tailored update.')
backup=ROOT/'videonic-ui-backups'/('before-v16-4-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
 for rel,content in zip(DESTS,current):
  dest=backup/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(content)
  if digest(dest.read_bytes())!=digest(content):raise RuntimeError('Backup verification failed')
except Exception as ex:stop('Unable to create verified backup: '+str(ex))
staged=[]
try:
 for rel,content,expected in zip(DESTS,incoming,NEW):
  target=ROOT/rel
  temp=target.with_name(target.name+'.v16-4-stage')
  temp.write_bytes(content);staged.append(temp)
  if digest(temp.read_bytes())!=expected:raise RuntimeError('Staging checksum mismatch')
 for rel,temp in zip(DESTS,staged):os.replace(temp,ROOT/rel)
except Exception as ex:
 for rel,content in zip(DESTS,current):
  if digest((ROOT/rel).read_bytes())!=digest(content):(ROOT/rel).write_bytes(content)
 stop('Could not apply update: '+str(ex))
finally:
 for temp in staged:temp.unlink(missing_ok=True)
print('SUCCESS: v16.4 mini-player surface + seek tuning installed.')
print('Verified backup:',backup)
print('NEXT: run :app:compileDebugKotlin in Codespaces. Android SDK compile was not run here.')
