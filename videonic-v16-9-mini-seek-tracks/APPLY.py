#!/usr/bin/env python3
"""Guarded v16.9 update for the exact compiled v16.8 source.
Only edits MainActivity and VideonicPlaybackService; never touches app data.
"""
from pathlib import Path
from hashlib import sha256
from datetime import datetime
import os, sys

ROOT = Path.cwd()
PATCH = Path(__file__).resolve().parent
SRC = [
    ("app/src/main/java/com/videonic/lite/MainActivity.kt", "MainActivity.kt", "OLD_MAIN", "NEW_MAIN"),
    ("app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt", "VideonicPlaybackService.kt", "OLD_SVC", "NEW_SVC"),
]
INDEX = ROOT/"app/src/main/java/com/videonic/lite/player/MkvClusterSeekIndex.kt"
HASHES = {'OLD_MAIN': '8955f48062059aa1fcb0e38b359f6ea6aaf147b2147e82649c14ec4fd2656610', 'OLD_SVC': 'cdc610ec264aed9c3862aba7c55075f21f220428917552f7fcef86d072e4a549', 'INDEX': '125c89ba1afb01f1be6862375591ee6d87cf3d3d199e9f4078cc5ca0738a7876', 'NEW_MAIN': 'd2e89b51ee6bf7aa3c2cccce9760901c29562d6224f26daba711eb4db83eaecf', 'NEW_SVC': '1bbac9fbabb4c979620107760368eefed1f1915500655fb3e8753cc9b9c63259'}

def digest(data): return sha256(data).hexdigest() if data is not None else None
def stop(msg): sys.exit("SAFE STOP: " + msg + ". No project files were changed.")
if not INDEX.is_file() or digest(INDEX.read_bytes())!=HASHES["INDEX"]:
    stop("Unexpected MKV index source; this patch requires the exact v16.8 files")
paths=[ROOT/rel for rel,*_ in SRC]
payloads=[]
for _,name,_,new_hash in SRC:
    f=PATCH/name
    if not f.is_file():stop("Missing update payload: "+name)
    data=f.read_bytes()
    if digest(data)!=HASHES[new_hash]:stop("Update checksum failed: "+name)
    payloads.append(data)
existing=[p.read_bytes() if p.is_file() else None for p in paths]
actual=[digest(b) for b in existing]
expected_old=[HASHES[item[2]] for item in SRC]
expected_new=[HASHES[item[3]] for item in SRC]
if actual==expected_new:
    print("ALREADY APPLIED: Videonic Lite v16.9 is installed.")
    sys.exit(0)
if actual!=expected_old:
    for p,h in zip(paths,actual):print("CURRENT:",p,h)
    stop("Source differs from compiled v16.8; do not overwrite custom changes")
backup=ROOT/"videonic-ui-backups"/("before-v16-9-"+datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
try:
    for (rel,_,_,_),data in zip(SRC,existing):
        dst=backup/rel
        dst.parent.mkdir(parents=True,exist_ok=True)
        dst.write_bytes(data)
        if digest(dst.read_bytes())!=digest(data):raise OSError("backup checksum mismatch")
except Exception as e:stop("Backup failed: "+str(e))
staged=[]
try:
    for path,data,wanted in zip(paths,payloads,expected_new):
        temp=path.with_suffix(path.suffix+".v169-tmp")
        temp.write_bytes(data)
        staged.append(temp)
        if digest(temp.read_bytes())!=wanted:raise OSError("staging failed")
    for path,temp in zip(paths,staged):os.replace(temp,path)
except Exception as e:
    for path,data in zip(paths,existing):
        if data is not None:path.write_bytes(data)
    stop("Install failed; original files restored: "+str(e))
finally:
    for temp in staged:temp.unlink(missing_ok=True)
print("SUCCESS: Videonic Lite v16.9 update applied.")
print("Backup:",backup)
print("Next: Compile :app:compileDebugKotlin in Codespaces before building APK.")
