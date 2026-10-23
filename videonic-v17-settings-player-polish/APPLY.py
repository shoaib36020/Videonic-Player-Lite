#!/usr/bin/env python3
"""v17 settings/player UI patch. Strict v16.10 source check and transactional backups."""
from pathlib import Path
import hashlib,os,sys
from datetime import datetime

ROOT=Path.cwd()
PACKAGE=Path(__file__).resolve().parent
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
PAYLOAD=PACKAGE/'MainActivity.kt'
ORIGINAL_SHA='151deb3dada22b5ff3b81055e4365927925aed0266110e9fae9acca7277975c8'
UPDATED_SHA='e358fab923f1421bcaad568c2641e04e2652e72f7d611d72509a83f6ecb2b7f6'
OLD_LABEL='android:label="Videonic Lite"'
NEW_LABEL='android:label="Videonic Player"'

def digest(raw):return hashlib.sha256(raw).hexdigest()
def safe_stop(reason):sys.exit('SAFE STOP: '+reason+'; no files changed.')
if not MAIN.is_file() or not MANIFEST.is_file():
    safe_stop('Run this installer in the Videonic-Player-Lite repository root')
if not PAYLOAD.is_file():safe_stop('ZIP is missing MainActivity.kt')
updated=PAYLOAD.read_bytes()
if digest(updated)!=UPDATED_SHA:safe_stop('Package integrity check failed')
original=MAIN.read_bytes()
old_manifest=MANIFEST.read_bytes()
try:
    current_manifest=old_manifest.decode('utf-8')
except UnicodeError:
    safe_stop('Cannot decode AndroidManifest.xml')
if current_manifest.count(OLD_LABEL)+current_manifest.count(NEW_LABEL)!=1:
    safe_stop('Unexpected app name in AndroidManifest.xml')
if digest(original)==UPDATED_SHA and NEW_LABEL in current_manifest:
    print('ALREADY APPLIED: Videonic Player v17 settings & UI patch')
    sys.exit(0)
if digest(original)!=ORIGINAL_SHA:
    print('CURRENT MainActivity.kt SHA256:',digest(original))
    safe_stop('MainActivity.kt differs from the expected v16.10 version; do not force it')
if 'android:launchMode="singleTop"' not in current_manifest:
    safe_stop('AndroidManifest.xml is missing the notification-opening singleTop setting')
new_manifest=current_manifest.replace(OLD_LABEL,NEW_LABEL,1).encode('utf-8')
backup=ROOT/'videonic-ui-backups'/('before-v17-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for path, data in [(backup/'MainActivity.kt',original),(backup/'AndroidManifest.xml',old_manifest)]:
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(data)
        if digest(path.read_bytes())!=digest(data):raise OSError('Backup verification failure')
except OSError as e:safe_stop('Backup failed: '+str(e))
main_tmp=MAIN.with_suffix('.kt.v17-tmp')
manifest_tmp=MANIFEST.with_suffix('.xml.v17-tmp')
try:
    main_tmp.write_bytes(updated)
    manifest_tmp.write_bytes(new_manifest)
    if digest(main_tmp.read_bytes())!=UPDATED_SHA:raise OSError('staging checksum mismatch')
    os.replace(main_tmp,MAIN)
    os.replace(manifest_tmp,MANIFEST)
except Exception as exc:
    MAIN.write_bytes(original)
    MANIFEST.write_bytes(old_manifest)
    safe_stop('Install failed and originals were restored: '+str(exc))
finally:
    main_tmp.unlink(missing_ok=True)
    manifest_tmp.unlink(missing_ok=True)
print('SUCCESS: Videonic Player v17 settings & fullscreen patch installed.')
print('Backup:',backup)
print('Compile :app:compileDebugKotlin before building an APK.')
