#!/usr/bin/env python3
"""Guarded installer for Videonic v8, expected exact user-compiled v7 state."""
from pathlib import Path
from datetime import datetime
import hashlib, sys, os
HERE=Path(__file__).resolve().parent
ROOT=Path.cwd()
FILES={'MainActivity.kt': 'app/src/main/java/com/videonic/lite/MainActivity.kt', 'PrivateFolderScreen.kt': 'app/src/main/java/com/videonic/lite/ui/PrivateFolderScreen.kt', 'PrivateVault.kt': 'app/src/main/java/com/videonic/lite/security/PrivateVault.kt', 'VideonicPlaybackService.kt': 'app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt'}
EXPECTED={'MainActivity.kt': '93f028499f089968a84da2e7190a1f5a73f443f9d293565eb7e035077e92f5a2', 'PrivateFolderScreen.kt': '4940269411e54a3ce9b9dd544016ec0f14c65056e13c3e84df61570d82d79094', 'PrivateVault.kt': '78c7f28462503f6d3e9bf1322cea2fb4e109c28bebc18bc7644822582bc1593b', 'VideonicPlaybackService.kt': '24d42f5537a00187e4b60afe55ca6268298045ee111c5b85cc6e9438e4a73902'}
NEW={'MainActivity.kt': 'af04f47f05786a947a914080ac50dac55efa2a860f22bfb81e51a8e64a61b129', 'PrivateFolderScreen.kt': '4b744dde5b4869cfca476f54c643689d0c50ee9032594e6f63108d40e3f7c4da', 'PrivateVault.kt': 'e989730febdf5692e7ec64021aac0d766a26e3265227ff0ed5d2161995e36464', 'VideonicPlaybackService.kt': '20e58caaefb4eca1dc0dbc5b1d2da87d2697e32deb8148ed61ef13671f1c0237'}
def digest(data):return hashlib.sha256(data).hexdigest()
current={}
for short,target in FILES.items():
    p=ROOT/target
    if not p.is_file():sys.exit(f"SAFE STOP: Missing {target}. Nothing changed.")
    current[short]=p.read_bytes()
all_new=all(digest(current[k])==NEW[k] for k in FILES)
if all_new:
    print('ALREADY APPLIED: Videonic v8 source is installed. No changes made.')
    sys.exit(0)
for short,target in FILES.items():
    actual=digest(current[short])
    if actual != EXPECTED[short]:
        print(f"SAFE STOP: {target} does not match the verified v7 source. Nothing changed.")
        print('Found:',actual,'Expected:',EXPECTED[short])
        sys.exit(1)
    if digest((HERE/short).read_bytes()) != NEW[short]:
        sys.exit('SAFE STOP: Corrupted patch ZIP. Nothing changed.')
backup=ROOT/'videonic-ui-backups'/('before-v8-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True,exist_ok=False)
for short,target in FILES.items():
    (backup/short).write_bytes(current[short])
try:
    for short,target in FILES.items():
        p=ROOT/target
        temp=p.with_name(p.name+'.v8-tmp')
        temp.write_bytes((HERE/short).read_bytes())
        os.replace(temp,p)
except Exception as exc:
    for short,target in FILES.items():
        (ROOT/target).write_bytes(current[short])
    sys.exit(f'UPDATE FAILED and all files restored: {exc}')
print('SUCCESS: Videonic v8 targeted fixes applied. Four Kotlin files updated.')
print('Backups:',backup)
print('No changes to Feedback backend, settings database, Playlists, or other resources.')
