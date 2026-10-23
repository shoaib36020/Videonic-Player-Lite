#!/usr/bin/env python3
"""Guarded Videonic Lite v16.8 install for the exact compiled v16.7 source."""
from pathlib import Path
from hashlib import sha256
from datetime import datetime
import os
import sys

root = Path.cwd()
patch = Path(__file__).resolve().parent
files = [('app/src/main/java/com/videonic/lite/MainActivity.kt', 'MainActivity.kt'), ('app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt', 'VideonicPlaybackService.kt'), ('app/src/main/java/com/videonic/lite/player/MkvClusterSeekIndex.kt', 'MkvClusterSeekIndex.kt')]
expected_old = ['0ae6061da1bb8dc435bc265d5219fe8c392734b95f24d767079b6e735831e529', 'b170fab1374911d3c8afe71ff1babe9c8d6b534ef4cd61c592f83ebaaf0b7891', '188aa0c7cbeb08d080cab11d41401a6eb6b15ec8b415b7659207c5424becc8fc']
expected_new = ['8955f48062059aa1fcb0e38b359f6ea6aaf147b2147e82649c14ec4fd2656610', 'cdc610ec264aed9c3862aba7c55075f21f220428917552f7fcef86d072e4a549', '125c89ba1afb01f1be6862375591ee6d87cf3d3d199e9f4078cc5ca0738a7876']

def checksum(data):
    return sha256(data).hexdigest() if data is not None else None

def stop(message):
    sys.exit("SAFE STOP: " + message + " No project files changed.")

paths = [root / rel for rel,name in files]
payloads = []
for rel,name in files:
    source = patch / name
    if not source.is_file(): stop("Missing patch file: " + name)
    payloads.append(source.read_bytes())
if [checksum(data) for data in payloads] != expected_new:
    stop("Patch checksum verification failed.")

current = [p.read_bytes() if p.is_file() else None for p in paths]
actual = [checksum(data) for data in current]
if actual == expected_new:
    print("ALREADY APPLIED: v16.8 player fix is installed.")
    sys.exit(0)
if actual != expected_old:
    print("Detected file checksums:")
    for p,d in zip(paths,actual): print(" ",str(p),d)
    stop("Unexpected source version. Expected exact v16.7 plus v16.6 MKV index.")

backup = root / "videonic-ui-backups" / ("before-v16-8-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
try:
    for (rel,name),data in zip(files,current):
        target = backup / rel
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data)
        if checksum(target.read_bytes()) != checksum(data):
            raise OSError("Backup verification failed for "+rel)
except Exception as err:
    stop("Failed to back up existing project: "+str(err))

staged = []
try:
    for p,data,wanted in zip(paths,payloads,expected_new):
        temp = p.with_suffix(p.suffix + ".v16-8-stage")
        temp.write_bytes(data)
        staged.append(temp)
        if checksum(temp.read_bytes()) != wanted:
            raise OSError("Staged checksum mismatch: "+str(p))
    for p,temp in zip(paths,staged):
        os.replace(temp,p)
except Exception as err:
    for p,data in zip(paths,current):
        if data is not None: p.write_bytes(data)
    stop("Apply error; original files restored: "+str(err))
finally:
    for temp in staged: temp.unlink(missing_ok=True)

print("SUCCESS: v16.8 source patch applied.")
print("Backup:",backup)
print("NEXT: Compile in Codespaces before building APK.")
