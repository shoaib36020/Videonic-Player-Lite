#!/usr/bin/env python3
"""Incremental Videonic v29 pull-to-refresh installer, applied over exact v28.
Run from the full Android project root, not inside the patch directory.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import os
import shutil
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PATCH = Path(__file__).resolve().parent
MAIN = ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
UPDATED = PATCH/'MainActivity.kt'
BASE_SHA = 'b60931da598afe16e7561213735f01ff0df8412c0077a990f7b31d11240b5fe4'
V29_SHA = '06b821f20a26928dc58ccc837b424dadcffed2a187f35e59bf71fbb7a707be81'
V28_DRAWABLE_SHA = 'e475652ebecad07eab1d994aaa62e85836aae6173925cf811fcb2fe8018b62c8'
V28_NAME_SHA = '0417c28d209de8306547678c5fcd15d543bdb1ffe3f6ad1411b8115dccf17a99'
ANDROID = '{http://schemas.android.com/apk/res/android}'

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def fail(message):
    raise SystemExit('SAFE STOP: '+message+'. No files changed.')

if not MAIN.is_file():
    fail('MainActivity.kt missing (run this from /workspaces/Videonic-Player-Lite)')
if not UPDATED.is_file() or sha(UPDATED) != V29_SHA:
    fail('v29 patch payload missing or changed')
# Strict v28 resource validation ensures that this builds atop v28, not v27.
res=ROOT/'app/src/main/res'
drawable=res/'drawable/videonic_splash_center_v28.xml'
name=res/'drawable-nodpi/videonic_splash_name_compact_v28.png'
if not drawable.is_file() or not name.is_file() or sha(drawable)!=V28_DRAWABLE_SHA or sha(name)!=V28_NAME_SHA:
    fail('v28 centered splash resources are not installed unchanged; apply v28 first')
manifest=ROOT/'app/src/main/AndroidManifest.xml'
if not manifest.is_file():
    fail('AndroidManifest.xml missing')
try:
    root=ET.fromstring(manifest.read_bytes())
except ET.ParseError:
    fail('AndroidManifest.xml is invalid')
app=root.find('application')
if app is None:
    fail('AndroidManifest.xml has no application')
activities=[a for a in app.findall('activity') if (a.get(ANDROID+'name') or '').endswith('MainActivity')]
if len(activities)!=1:
    fail('MainActivity manifest entry missing or ambiguous')
theme=activities[0].get(ANDROID+'theme')
if theme not in ('@style/VideonicLaunchV26','@style/VideonicLaunchV27'):
    fail('v28-compatible launch theme not found')
variant=theme.split('/')[-1]
theme_file=res/'values-v31'/('videonic_launch_v26.xml' if variant=='VideonicLaunchV26' else 'videonic_launch_v27.xml')
if not theme_file.is_file():
    fail('v28 Android 12+ theme missing')
try:
    themes=ET.fromstring(theme_file.read_bytes())
except ET.ParseError:
    fail('v28 theme XML invalid')
styles=[el for el in themes.findall('style') if el.get('name')==variant]
if len(styles)!=1:
    fail('v28 theme style missing or ambiguous')
items={item.get('name'):(item.text or '').strip() for item in styles[0].findall('item')}
if items.get('android:windowSplashScreenAnimatedIcon')!='@drawable/videonic_splash_center_v28' or 'android:windowSplashScreenBrandingImage' in items:
    fail('v28 centered splash has been modified')
current=sha(MAIN)
if current == V29_SHA:
    print('ALREADY APPLIED: Videonic v29 pull-to-refresh is installed; no files changed.')
    raise SystemExit(0)
if current != BASE_SHA:
    fail('unexpected MainActivity.kt (requires exact v28 source; do not overwrite your edits)')
backup=ROOT/'videonic-ui-backups'/('before-v29-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
target=backup/MAIN.relative_to(ROOT)
target.parent.mkdir(parents=True,exist_ok=False)
shutil.copy2(MAIN,target)
if sha(target)!=BASE_SHA:
    fail('could not verify v28 backup')
fd,staged=tempfile.mkstemp(prefix='.videonic-v29-',dir=MAIN.parent)
try:
    with os.fdopen(fd,'wb') as f:
        f.write(UPDATED.read_bytes())
    temp=Path(staged)
    if sha(temp)!=V29_SHA:
        fail('could not verify staged v29 source')
    os.chmod(temp,MAIN.stat().st_mode)
    os.replace(temp,MAIN)
except BaseException:
    if sha(MAIN)!=BASE_SHA:
        shutil.copy2(target,MAIN)
    raise
finally:
    Path(staged).unlink(missing_ok=True)
print('SUCCESS: Videonic v29 pull-to-refresh installed.')
print('Backup:',backup.relative_to(ROOT))
print('Updated only:',MAIN.relative_to(ROOT))
print('Videos / Folders: swipe down at top to update from Android MediaStore.')
