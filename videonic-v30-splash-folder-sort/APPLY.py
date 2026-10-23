#!/usr/bin/env python3
"""Videonic v30 splash, folder sort/grid and compact speed lock overlay.
Run from Videonic project root after exact v29. Changes source and splash images only.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, os, shutil, tempfile
import xml.etree.ElementTree as ET

ROOT=Path.cwd()
PKG=Path(__file__).resolve().parent
RES=ROOT/'app/src/main/res'
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
DEST_ICON=RES/'drawable/videonic_splash_center_v28.xml'
DEST_BITMAP=RES/'drawable-nodpi/videonic_splash_composite_v30.png'
OLD_NAME=RES/'drawable-nodpi/videonic_splash_name_compact_v28.png'
OLD_MAIN='06b821f20a26928dc58ccc837b424dadcffed2a187f35e59bf71fbb7a707be81'
NEW_MAIN='b3703a82c2d1f6fd7caa41b8c1aea7c054e5dd43324b888ce8ca0faf51bc109e'
OLD_XML='e475652ebecad07eab1d994aaa62e85836aae6173925cf811fcb2fe8018b62c8'
OLD_NAME_SHA='0417c28d209de8306547678c5fcd15d543bdb1ffe3f6ad1411b8115dccf17a99'
NEW_XML='778f60655874ea1cf261b30230bbd714e2655bcd531894961b806c2c9ae3102a'
NEW_BITMAP='02931d7975f5f5a15a9618436abfcb413de2dd507742bfd9db8e2e3b6c96a966'
ANDROID='{http://schemas.android.com/apk/res/android}'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def stop(msg):raise SystemExit('SAFE STOP: '+msg+'. No files changed.')
def parsed(path):
    try: return ET.parse(path).getroot()
    except (OSError,ET.ParseError) as e: stop(f'invalid XML {path}: {e}')

expected={
    MAIN:(PKG/'MainActivity.kt',NEW_MAIN),
    DEST_ICON:(PKG/'videonic_splash_center_v28.xml',NEW_XML),
    DEST_BITMAP:(PKG/'videonic_splash_composite_v30.png',NEW_BITMAP),
}
for src,want in expected.values():
    if not src.is_file() or sha(src)!=want: stop('v30 package is missing or damaged: '+src.name)
parsed(PKG/'videonic_splash_center_v28.xml')
manifest=ROOT/'app/src/main/AndroidManifest.xml'
if not MAIN.is_file() or not manifest.is_file():stop('Android project files missing (run from the project root)')
app=parsed(manifest).find('application')
if app is None:stop('AndroidManifest application missing')
activities=[a for a in app.findall('activity') if (a.get(ANDROID+'name') or '').endswith('MainActivity')]
if len(activities)!=1:stop('Cannot identify a unique MainActivity')
style_ref=activities[0].get(ANDROID+'theme')
if style_ref not in ('@style/VideonicLaunchV26','@style/VideonicLaunchV27'):
    stop('Expected v29-compatible VideonicLaunchV26/V27 theme')
variant=style_ref.split('/')[-1]
style_file=RES/'values-v31'/('videonic_launch_v26.xml' if variant=='VideonicLaunchV26' else 'videonic_launch_v27.xml')
styles=[s for s in parsed(style_file).findall('style') if s.get('name')==variant]
if len(styles)!=1:stop('Missing the installed splash style')
items={x.get('name'):(x.text or '').strip() for x in styles[0].findall('item')}
if items.get('android:windowSplashScreenAnimatedIcon')!='@drawable/videonic_splash_center_v28' or 'android:windowSplashScreenBrandingImage' in items:
    stop('Splash style differs from v28/v29; will not overwrite')
if not OLD_NAME.is_file() or sha(OLD_NAME)!=OLD_NAME_SHA:
    stop('Missing original v28 title artwork')

current_main=sha(MAIN)
xml_hash=sha(DEST_ICON) if DEST_ICON.is_file() else None
bitmap_hash=sha(DEST_BITMAP) if DEST_BITMAP.is_file() else None
if current_main==NEW_MAIN and xml_hash==NEW_XML and bitmap_hash==NEW_BITMAP:
    print('ALREADY APPLIED: Videonic v30 is installed, nothing changed.')
    raise SystemExit(0)
if current_main!=OLD_MAIN:stop('MainActivity.kt is not the exact v29 file')
if xml_hash!=OLD_XML:stop('v28/v29 splash drawable was modified')
if DEST_BITMAP.exists():stop('v30 composite image already exists but does not match installer payload')

backup=ROOT/'videonic-ui-backups'/('before-v30-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
try:
    for dest in expected:
        if dest.exists():
            back=backup/dest.relative_to(ROOT)
            back.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(dest,back)
            if sha(back)!=sha(dest):raise RuntimeError('Backup verification failed: '+str(dest))
except BaseException:
    shutil.rmtree(backup,ignore_errors=True)
    raise
written=[]
try:
    for dest,(src,want) in expected.items():
        dest.parent.mkdir(parents=True,exist_ok=True)
        fd,path=tempfile.mkstemp(prefix='.videonic-v30-',dir=dest.parent)
        temp=Path(path)
        try:
            with os.fdopen(fd,'wb') as f:f.write(src.read_bytes())
            if sha(temp)!=want:raise RuntimeError('Staged patch integrity failed')
            if dest.is_file():os.chmod(temp,dest.stat().st_mode)
            os.replace(temp,dest)
            written.append(dest)
        finally:
            temp.unlink(missing_ok=True)
except BaseException:
    for dest in reversed(written):
        back=backup/dest.relative_to(ROOT)
        if back.is_file():shutil.copy2(back,dest)
        else:dest.unlink(missing_ok=True)
    raise
print('SUCCESS: Videonic v30 installed — compact safe splash, folder sort/grid, speed lock chip timeout.')
print('Backup:',backup.relative_to(ROOT))
print('No player-service, MKV, rotation or playlist data files changed.')
