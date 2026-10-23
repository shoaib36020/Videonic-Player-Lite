#!/usr/bin/env python3
"""Videonic v9 installer. Exact v8 Kotlin hashes; minimal manifest edit; atomic backups."""
from pathlib import Path
from datetime import datetime
import hashlib, os, sys

ROOT = Path.cwd()
HERE = Path(__file__).resolve().parent
MAIN = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SERVICE = ROOT / 'app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
EXPECTED = {
 'MainActivity.kt': 'af04f47f05786a947a914080ac50dac55efa2a860f22bfb81e51a8e64a61b129',
 'VideonicPlaybackService.kt': '20e58caaefb4eca1dc0dbc5b1d2da87d2697e32deb8148ed61ef13671f1c0237',
}
NEW = {
 'MainActivity.kt': 'b2b2651fa5ae3a6416ccfa56588fb0c1ec1c7074a86a426180ea73f22a5dda7a',
 'VideonicPlaybackService.kt': '71c666059e3aae0a00e6c2d630a9947acb6259fc9fc268b3f4f4cd42b5f37e7c',
}
PATHS = {'MainActivity.kt': MAIN, 'VideonicPlaybackService.kt': SERVICE}
ORIGINAL_XML = 'android:name=".MainActivity" android:exported="true" android:configChanges'
UPDATED_XML = 'android:name=".MainActivity" android:exported="true" android:launchMode="singleTop" android:configChanges'

def digest(raw): return hashlib.sha256(raw).hexdigest()
def stop(msg): sys.exit('SAFE STOP: ' + msg + ' No files changed.')

for p in (*PATHS.values(), MANIFEST):
    if not p.is_file(): stop(f'Missing {p.relative_to(ROOT)}.')

current = {key:path.read_bytes() for key,path in PATHS.items()}
content = {key:(HERE/key).read_bytes() for key in PATHS}
manifest_before=MANIFEST.read_bytes()
manifest_text=manifest_before.decode('utf-8')

for key in PATHS:
    if digest(content[key]) != NEW[key]: stop(f'Corrupt ZIP: {key}.')

if all(digest(current[key])==NEW[key] for key in PATHS) and UPDATED_XML in manifest_text:
    print('ALREADY APPLIED: v9 notification + navigation update is installed.')
    sys.exit(0)
for key in PATHS:
    if digest(current[key]) != EXPECTED[key]:
        print(f'Current SHA256 for {key}: {digest(current[key])}')
        stop(f'{key} does not match the expected v8 source. Please send the output; do not force install.')
if manifest_text.count(ORIGINAL_XML)!=1 or UPDATED_XML in manifest_text:
    stop('AndroidManifest.xml Activity declaration is different than expected.')

new_manifest=manifest_text.replace(ORIGINAL_XML,UPDATED_XML,1).encode('utf-8')
backup=ROOT/'videonic-ui-backups'/('before-v9-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True,exist_ok=False)
for key,path in PATHS.items(): (backup/key).write_bytes(current[key])
(backup/'AndroidManifest.xml').write_bytes(manifest_before)
try:
    for key,path in PATHS.items():
        temp=path.with_name(path.name+'.v9-tmp')
        temp.write_bytes(content[key]); os.replace(temp,path)
    tmp=MANIFEST.with_name('AndroidManifest.xml.v9-tmp')
    tmp.write_bytes(new_manifest);os.replace(tmp,MANIFEST)
except Exception as exc:
    for key,path in PATHS.items(): path.write_bytes(current[key])
    MANIFEST.write_bytes(manifest_before)
    stop(f'Update failed; all originals restored: {exc}')

print('SUCCESS: v9 applied — notification tap opens current video, nested Back navigation fixed, selectable glass nav.')
print('Backup:',backup)
print('Next: compile with your existing low-memory Gradle command; do not install until BUILD SUCCESSFUL.')
