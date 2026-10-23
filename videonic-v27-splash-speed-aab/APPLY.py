#!/usr/bin/env python3
"""Guarded incremental Videonic v27 patch.
Run from the full Android project root. v26 MainActivity required.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path.cwd()
PATCH=Path(__file__).resolve().parent
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
PAYLOAD=PATCH/'MainActivity.kt'
NAME_IMAGE=PATCH/'videonic_splash_name_v27.png'
ORIGINAL_SHA='48e961ce9b6a225bd50c2020a2a556650219e6111cd795473258d7fce4a6b4ed'
NEW_SHA='b60931da598afe16e7561213735f01ff0df8412c0077a990f7b31d11240b5fe4'
ANDROID_NS='http://schemas.android.com/apk/res/android'


def sha(data:bytes)->str:
    return hashlib.sha256(data).hexdigest()


def safe_stop(reason:str):
    raise SystemExit('SAFE STOP: '+reason+'. No files changed.')


def parsed(payload:bytes, label:str):
    try:
        return ET.fromstring(payload)
    except ET.ParseError as e:
        safe_stop(f'{label} is invalid XML: {e}')


def v27_resources():
    """Patch existing V26 launch theme; or create it when v26 skipped theme setup."""
    manifest=ROOT/'app/src/main/AndroidManifest.xml'
    if not manifest.is_file():
        safe_stop('Missing AndroidManifest.xml')
    data=manifest.read_bytes()
    mroot=parsed(data,'manifest')
    app=mroot.find('application')
    if app is None:
        safe_stop('Manifest missing <application>')
    matches=[a for a in app.findall('activity') if
             (a.get('{'+ANDROID_NS+'}name','')=='MainActivity' or
              a.get('{'+ANDROID_NS+'}name','').endswith('.MainActivity'))]
    if len(matches)!=1:
        safe_stop('MainActivity manifest entry is ambiguous or missing')
    attr='{'+ANDROID_NS+'}theme'
    current=matches[0].get(attr)
    res=ROOT/'app/src/main/res'
    v26_base=res/'values/videonic_launch_v26.xml'
    v26_v31=res/'values-v31/videonic_launch_v26.xml'
    v26_drawable=res/'drawable/videonic_launch_v26.xml'
    name_dest=res/'drawable-xxhdpi/videonic_splash_name_v27.png'
    if name_dest.exists():
        safe_stop('v27 brand image already exists but MainActivity is still v26; check files')
    changes={name_dest:NAME_IMAGE.read_bytes()}

    if current=='@style/VideonicLaunchV26':
        if not all(p.is_file() for p in (v26_base,v26_v31,v26_drawable)):
            safe_stop('Manifest uses V26 launch theme but some theme resource files are missing')
        base=v26_base.read_bytes()
        if not any(s.attrib.get('name')=='VideonicLaunchV26' for s in parsed(base,'v26 base style')):
            safe_stop('Unexpected v26 base style')
        style=v26_v31.read_text(encoding='utf-8')
        expected='  <item name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_brand</item>\n'
        if style.count(expected)!=1 or 'windowSplashScreenBrandingImage' in style:
            safe_stop('V26 API31 splash theme differs from the supported source')
        style=style.replace(expected,expected+
            '  <item name="android:windowSplashScreenBrandingImage">@drawable/videonic_splash_name_v27</item>\n')
        bg=v26_drawable.read_text(encoding='utf-8')
        ref=('  <item android:drawable="@drawable/videonic_brand" android:gravity="center" '
             'android:width="128dp" android:height="128dp"/>\n')
        if bg.count(ref)!=1:
            safe_stop('V26 legacy splash background differs from the supported source')
        bg=bg.replace(ref,ref+
            '  <item android:drawable="@drawable/videonic_splash_name_v27" android:gravity="center" '
            'android:top="190dp" android:width="200dp" android:height="80dp"/>\n')
        changes[v26_v31]=style.encode()
        changes[v26_drawable]=bg.encode()
        note='Updated the installed v26 launch theme and removed the duplicate Compose splash.'
    else:
        # v26 explicitly allowed skipping the launch theme when it couldn't be
        # safely located. Support the common conventional @style case here.
        original=current or app.get(attr)
        if not original or not re.fullmatch(r'@style/[A-Za-z0-9_.]+',original):
            safe_stop('Could not identify the original theme to create a v27 native splash')
        for p in (v26_base,v26_v31,v26_drawable):
            if p.exists():
                safe_stop('Some v26 launch resources exist but manifest does not use them')
        base=res/'values/videonic_launch_v27.xml'
        api31=res/'values-v31/videonic_launch_v27.xml'
        drawable=res/'drawable/videonic_launch_v27.xml'
        if any(p.exists() for p in (base,api31,drawable)):
            safe_stop('V27 splash resource already exists')
        # Modify only the MainActivity opening tag (no wholesale XML serialize).
        body=data.decode('utf-8')
        pattern=re.compile(r'<activity\b[^>]*>', re.S)
        found=[]
        for hit in pattern.finditer(body):
            match=re.search(r'android:name\s*=\s*(["\'])(.*?)\1',hit.group(0),re.S)
            if match and (match.group(2)=='MainActivity' or match.group(2).endswith('.MainActivity')):
                found.append(hit)
        if len(found)!=1:
            safe_stop('Could not uniquely find MainActivity opening tag')
        hit=found[0]
        tag=hit.group(0)
        if re.search(r'android:theme\s*=',tag):
            tag=re.sub(r'android:theme\s*=\s*(["\']).*?\1',
                'android:theme="@style/VideonicLaunchV27"',tag,count=1,flags=re.S)
        elif tag.endswith('/>'):
            tag=tag[:-2]+' android:theme="@style/VideonicLaunchV27" />'
        else:
            tag=tag[:-1]+' android:theme="@style/VideonicLaunchV27">'
        body=body[:hit.start()]+tag+body[hit.end():]
        updated=parsed(body.encode(),'modified manifest')
        entry=updated.find('application')
        if entry is None or not any(a.get(attr)=='@style/VideonicLaunchV27' for a in entry.findall('activity')):
            safe_stop('Manifest edit did not validate')
        changes[manifest]=body.encode()
        def themed(platform:bool):
            extra=('  <item name="android:windowSplashScreenBackground">#FFFFFF</item>\n'
                   '  <item name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_brand</item>\n'
                   '  <item name="android:windowSplashScreenBrandingImage">@drawable/videonic_splash_name_v27</item>\n'
                  ) if platform else ''
            return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'<style name="VideonicLaunchV27" parent="{original}">\n'
                '  <item name="android:windowBackground">@drawable/videonic_launch_v27</item>\n'
                +extra+'</style>\n</resources>\n').encode()
        changes[base]=themed(False)
        changes[api31]=themed(True)
        changes[drawable]=('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:drawable="@android:color/white"/>
  <item android:drawable="@drawable/videonic_brand" android:gravity="center" android:width="128dp" android:height="128dp"/>
  <item android:drawable="@drawable/videonic_splash_name_v27" android:gravity="center" android:top="190dp" android:width="200dp" android:height="80dp"/>
</layer-list>
''').encode()
        note='Created a single native branded launch theme with the app name.'
    for target,payload in changes.items():
        if target.suffix=='.xml':
            parsed(payload,str(target))
    return changes,note

if not MAIN.is_file():
    safe_stop('MainActivity.kt is missing; run in the root of the Videonic Android project')
if not PAYLOAD.is_file() or sha(PAYLOAD.read_bytes())!=NEW_SHA:
    safe_stop('v27 MainActivity.kt patch is corrupted')
if not NAME_IMAGE.is_file() or not NAME_IMAGE.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
    safe_stop('v27 brand image is missing or damaged')
previous=sha(MAIN.read_bytes())
if previous==NEW_SHA:
    print('ALREADY APPLIED: MainActivity is at v27; nothing changed.')
    sys.exit(0)
if previous!=ORIGINAL_SHA:
    safe_stop('Requires exact v26 MainActivity.kt; unknown/edited source')

changes,note=v27_resources()
changes[MAIN]=PAYLOAD.read_bytes()
backup=ROOT/'videonic-ui-backups'/('before-v27-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
backup.mkdir(parents=True,exist_ok=False)
existed={}
for path in changes:
    existed[path]=path.is_file()
    if existed[path]:
        relative=path.relative_to(ROOT)
        target=backup/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(path,target)
        if sha(target.read_bytes())!=sha(path.read_bytes()):
            safe_stop(f'Backup failed for {relative}')
written=[]
try:
    for path,contents in changes.items():
        path.parent.mkdir(parents=True,exist_ok=True)
        fd,name=tempfile.mkstemp(prefix='.v27-',dir=path.parent)
        tmp=Path(name)
        try:
            with os.fdopen(fd,'wb') as stream:
                stream.write(contents)
            if existed[path]:
                os.chmod(tmp,path.stat().st_mode)
            else:
                os.chmod(tmp,0o644)
            if sha(tmp.read_bytes())!=sha(contents):
                raise RuntimeError(f'Staged file checksum mismatch: {path}')
            os.replace(tmp,path)
            written.append(path)
        finally:
            tmp.unlink(missing_ok=True)
except BaseException:
    for path in reversed(written):
        if existed[path]:
            shutil.copy2(backup/path.relative_to(ROOT),path)
        else:
            path.unlink(missing_ok=True)
    raise
print('SUCCESS: Videonic v27 patch installed.')
print(note)
print('Backup:',backup.relative_to(ROOT))
print('Files:',*[str(p.relative_to(ROOT)) for p in changes],sep='\n  ')
print('Next: gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel')
