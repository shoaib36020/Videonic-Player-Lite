#!/usr/bin/env python3
"""Videonic v28: put name directly below the icon on Android 12+ splash.
Apply from the project root to a correctly installed v27 source.
Only resources change. Does not replace app code or modify settings.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import os
import re
import shutil
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PATCH = Path(__file__).resolve().parent
MAIN = ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
RES = ROOT/'app/src/main/res'
MAIN_V27_SHA = 'b60931da598afe16e7561213735f01ff0df8412c0077a990f7b31d11240b5fe4'
XML_PAYLOAD = PATCH/'videonic_splash_center_v28.xml'
PNG_PAYLOAD = PATCH/'videonic_splash_name_compact_v28.png'
XML_SHA = 'e475652ebecad07eab1d994aaa62e85836aae6173925cf811fcb2fe8018b62c8'
PNG_SHA = '0417c28d209de8306547678c5fcd15d543bdb1ffe3f6ad1411b8115dccf17a99'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stop(msg):
    raise SystemExit('SAFE STOP: '+msg+'. No files changed.')


def parse(data, label):
    try:
        return ET.fromstring(data)
    except ET.ParseError as e:
        stop(f'{label}: malformed XML: {e}')

if not MAIN.is_file() or sha(MAIN) != MAIN_V27_SHA:
    stop('Requires the exact installed v27 MainActivity.kt; apply v27 first')
for p, expect in ((XML_PAYLOAD, XML_SHA), (PNG_PAYLOAD, PNG_SHA)):
    if not p.is_file() or sha(p) != expect:
        stop('Damaged patch file: '+p.name)
manifest = ROOT/'app/src/main/AndroidManifest.xml'
if not manifest.is_file():
    stop('AndroidManifest.xml was not found')
android_name = '{http://schemas.android.com/apk/res/android}name'
android_theme = '{http://schemas.android.com/apk/res/android}theme'
mr = parse(manifest.read_bytes(), 'AndroidManifest.xml')
application = mr.find('application')
if application is None:
    stop('Manifest application missing')
activity = [x for x in application.findall('activity') if (x.get(android_name) or '').endswith('MainActivity')]
if len(activity) != 1:
    stop('Cannot identify the MainActivity launch theme')
style_name = activity[0].get(android_theme)
if style_name not in ('@style/VideonicLaunchV26', '@style/VideonicLaunchV27'):
    stop('Expected installed v27 native launch theme, got '+repr(style_name))
variant = style_name.split('/')[-1]
style_file = RES / 'values-v31' / ('videonic_launch_v26.xml' if variant=='VideonicLaunchV26' else 'videonic_launch_v27.xml')
if not style_file.is_file():
    stop('Missing v27 API31 splash style '+str(style_file))
icon_dest = RES / 'drawable' / 'videonic_splash_center_v28.xml'
name_dest = RES / 'drawable-nodpi' / 'videonic_splash_name_compact_v28.png'
old_data = style_file.read_bytes()
old_xml = parse(old_data, str(style_file))
style = [x for x in old_xml.findall('style') if x.get('name') == variant]
if len(style)!=1:
    stop('Unexpected API31 splash style structure')
items = {x.get('name'):(x.text or '').strip() for x in style[0].findall('item')}
if items.get('android:windowSplashScreenAnimatedIcon')=='@drawable/videonic_splash_center_v28' and not ('android:windowSplashScreenBrandingImage' in items):
    if icon_dest.is_file() and name_dest.is_file() and sha(icon_dest)==XML_SHA and sha(name_dest)==PNG_SHA:
        print('ALREADY APPLIED: v28 centered splash resources are installed. No changes.')
        raise SystemExit(0)
    stop('v28 style exists but the resources are missing or modified')
if items.get('android:windowSplashScreenAnimatedIcon')!='@drawable/videonic_brand' or items.get('android:windowSplashScreenBrandingImage')!='@drawable/videonic_splash_name_v27':
    stop('v27 splash style differs from expected; refusing unsafe edits')
if icon_dest.exists() or name_dest.exists():
    stop('v28 resource names already exist but launch theme is v27')
new = old_data.decode('utf-8')
new, count = re.subn(r'(<item\s+name="android:windowSplashScreenAnimatedIcon"\s*>\s*)@drawable/videonic_brand(\s*</item>)',
            r'\g<1>@drawable/videonic_splash_center_v28\2',new)
if count!=1:
    stop('Could not safely find original icon style item')
new, count = re.subn(r'\s*<item\s+name="android:windowSplashScreenBrandingImage"\s*>\s*@drawable/videonic_splash_name_v27\s*</item>', '', new)
if count!=1:
    stop('Could not safely find bottom branding style item')
updated=parse(new.encode(),str(style_file))
styles=[x for x in updated.findall('style') if x.get('name')==variant]
if len(styles)!=1 or any(x.get('name')=='android:windowSplashScreenBrandingImage' for x in styles[0].findall('item')):
    stop('BrandingImage was not safely removed')
parse(XML_PAYLOAD.read_bytes(), XML_PAYLOAD.name)

changes={style_file:new.encode(),icon_dest:XML_PAYLOAD.read_bytes(),name_dest:PNG_PAYLOAD.read_bytes()}
backup=ROOT/'videonic-ui-backups'/('before-v28-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
backup.mkdir(parents=True,exist_ok=False)
for path in changes:
    if path.exists():
        dest=backup/path.relative_to(ROOT)
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(path,dest)
        if sha(dest)!=sha(path):
            stop('Backup failed: '+str(path))
written=[]
try:
    for path,content in changes.items():
        path.parent.mkdir(parents=True,exist_ok=True)
        fd,tmp_name=tempfile.mkstemp(prefix='.v28-',dir=path.parent)
        tmp=Path(tmp_name)
        try:
            with os.fdopen(fd,'wb') as out:
                out.write(content)
            if tmp.read_bytes()!=content:
                raise RuntimeError('Staged resource mismatch '+str(path))
            if path.exists():
                os.chmod(tmp,path.stat().st_mode)
            os.replace(tmp,path)
            written.append(path)
        finally:
            tmp.unlink(missing_ok=True)
except BaseException:
    for path in reversed(written):
        old=backup/path.relative_to(ROOT)
        if old.is_file():
            shutil.copy2(old,path)
        else:
            path.unlink(missing_ok=True)
    raise
print('SUCCESS: Videonic v28 — app name now under icon on the single Android splash screen.')
print('Backup:',backup.relative_to(ROOT))
print('Only API31 splash resources changed; Kotlin/player behavior left untouched.')
print('Next: gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel')
