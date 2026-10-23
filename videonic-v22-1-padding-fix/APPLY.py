#!/usr/bin/env python3
"""Small targeted v22.1 Kotlin compile fix. No unrelated source changes."""
from pathlib import Path
import hashlib
from datetime import datetime, timezone
import shutil

root=Path.cwd()
target=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
old='.padding(horizontal=16.dp,bottom=if(landscape) 78.dp else 142.dp)'
new='.padding(start=16.dp,end=16.dp,bottom=if(landscape) 78.dp else 142.dp)'
before='3b01b90cdffebb404d8015a391fa1a327b4ba63c52579afba5c6526175a025a8'
after='49b803ca6809f9733161352bbd7115188fc396352e544dc3265839d297d75931'
if not target.exists():
    raise SystemExit('SAFE STOP: MainActivity.kt not found. Run from your project root.')
original=target.read_bytes()
checksum=hashlib.sha256(original).hexdigest()
if checksum==after:
    print('ALREADY APPLIED: v22.1')
    raise SystemExit(0)
if checksum!=before:
    raise SystemExit('SAFE STOP: Expected exact v22 file. No change made; do not overwrite a modified file.')
text=original.decode('utf-8')
if text.count(old)!=1:
    raise SystemExit('SAFE STOP: Expected exactly one matching padding expression.')
modified=text.replace(old,new,1).encode('utf-8')
assert hashlib.sha256(modified).hexdigest()==after
stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ')
backup=root/'videonic-ui-backups'/('before-v22-1-'+stamp)/'MainActivity.kt'
backup.parent.mkdir(parents=True,exist_ok=False)
shutil.copy2(target,backup)
if hashlib.sha256(target.read_bytes()).hexdigest()!=before:
    raise SystemExit('SAFE STOP: File changed before applying update.')
target.write_bytes(modified)
print('SUCCESS: v22.1 compile fix applied.')
print('Backup:',backup)
