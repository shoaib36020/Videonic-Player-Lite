#!/usr/bin/env python3
"""Guarded v26 patch: Kotlin changes + safe native Android launch theme.
Requires exact v25 MainActivity.kt; makes timestamped backups.
"""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT=Path.cwd()
PATCH=Path(__file__).resolve().parent
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
SOURCE=PATCH/'MainActivity.kt'
ORIGINAL_SHA='a30f4767428f0cdcb29622d4c97f8fa268711e519ba75890ed89895c7dafbf07'
NEW_SHA='48e961ce9b6a225bd50c2020a2a556650219e6111cd795473258d7fce4a6b4ed'
ANDROID_NS='http://schemas.android.com/apk/res/android'
THEME_NAME='VideonicLaunchV26'


def digest(value:bytes)->str:
    return hashlib.sha256(value).hexdigest()

def safe_stop(message:str)->None:
    raise SystemExit('SAFE STOP: '+message+'. No source changed.')

def launch_resource_changes():
    manifest=ROOT/'app/src/main/AndroidManifest.xml'
    if not manifest.is_file():
        return {},'No AndroidManifest.xml; the native launch theme was skipped.'
    old=manifest.read_bytes()
    try:
        tree=ET.fromstring(old)
    except ET.ParseError:
        return {},'AndroidManifest.xml could not be parsed; native launch theme skipped.'
    app=tree.find('application')
    if app is None:
        return {},'Application manifest element missing; native launch theme skipped.'
    entry=None
    for node in app.findall('activity'):
        name=node.get('{'+ANDROID_NS+'}name','')
        if name=='MainActivity' or name.endswith('.MainActivity'):
            entry=node
            break
    if entry is None:
        return {},'MainActivity not declared directly in manifest; native launch theme skipped.'
    theme_attr='{'+ANDROID_NS+'}theme'
    existing=entry.get(theme_attr) or app.get(theme_attr)
    if existing=='@style/'+THEME_NAME:
        # Previously installed: validate bundled files instead of changing theme again.
        return {},'Native launch theme already set.'
    if not existing or not re.fullmatch(r'@style/[A-Za-z0-9_.]+',existing):
        return {},'Original activity/application theme not resolvable; native launch theme skipped for safety.'
    # Keep original manifest formatting; only replace an existing android:theme on
    # the MainActivity opening tag, or add one. Parse again before committing.
    text=old.decode('utf-8')
    start_re=re.compile(r'<activity\b[^>]*>',re.S)
    matches=[]
    for match in start_re.finditer(text):
        tag=match.group(0)
        name=re.search(r'android:name\s*=\s*(["\'])(.*?)\1',tag,re.S)
        if name and (name.group(2)=='MainActivity' or name.group(2).endswith('.MainActivity')):
            matches.append(match)
    if len(matches)!=1:
        return {},'MainActivity tag ambiguous; native launch theme skipped for safety.'
    found=matches[0]
    tag=found.group(0)
    if re.search(r'android:theme\s*=',tag):
        tag=re.sub(r'android:theme\s*=\s*(["\']).*?\1',
                   'android:theme="@style/'+THEME_NAME+'"',tag,count=1,flags=re.S)
    else:
        if tag.endswith('/>'):
            tag=tag[:-2]+' android:theme="@style/'+THEME_NAME+'" />'
        else:
            tag=tag[:-1]+' android:theme="@style/'+THEME_NAME+'">'
    revised=text[:found.start()]+tag+text[found.end():]
    try:
        new_root=ET.fromstring(revised)
        app_new=new_root.find('application')
        assert app_new is not None
        assert any(n.get(theme_attr)=='@style/'+THEME_NAME for n in app_new.findall('activity'))
    except (ET.ParseError,AssertionError):
        return {},'Validation of edited manifest failed; native launch theme skipped.'
    resources=ROOT/'app/src/main/res'
    main_style=resources/'values/videonic_launch_v26.xml'
    v31_style=resources/'values-v31/videonic_launch_v26.xml'
    drawable=resources/'drawable/videonic_launch_v26.xml'
    if any(p.exists() for p in (main_style,v31_style,drawable)):
        return {},'A v26 launch-theme resource already exists; native launch theme skipped for safety.'
    # Base style inherits the manifest's real current theme, not a guessed parent.
    # Android 12+ splash fields belong in values-v31 to avoid resource API warnings.
    def themed_style(for_android_12:bool):
        extra=('  <item name="android:windowSplashScreenBackground">#FFFFFF</item>\n'
               '  <item name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_brand</item>\n') if for_android_12 else ''
        return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'<style name="{THEME_NAME}" parent="{existing}">\n'
                '  <item name="android:windowBackground">@drawable/videonic_launch_v26</item>\n'
                +extra+'</style>\n</resources>\n').encode()
    background=('<?xml version="1.0" encoding="utf-8"?>\n'
      '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
      '  <item android:drawable="@android:color/white"/>\n'
      '  <item android:drawable="@drawable/videonic_brand" android:gravity="center" '
      'android:width="128dp" android:height="128dp"/>\n'
      '</layer-list>\n').encode()
    changes={manifest:revised.encode('utf-8'),
             main_style:themed_style(False),v31_style:themed_style(True),drawable:background}
    for key,value in changes.items():
        if key.suffix=='.xml':
            try:ET.fromstring(value)
            except ET.ParseError as exc:safe_stop(f'Generated XML invalid: {key.name}: {exc}')
    return changes, 'Native branded starting window configured for Android 12+ and earlier devices.'

if not MAIN.is_file():
    safe_stop('MainActivity.kt missing; run from Videonic project root')
if not SOURCE.is_file() or digest(SOURCE.read_bytes()) != NEW_SHA:
    safe_stop('Patch payload corrupted')
prior=digest(MAIN.read_bytes())
if prior == NEW_SHA:
    print('ALREADY APPLIED: v26 MainActivity.kt; nothing replaced.')
    print('No resource files modified during repeat installation.')
    sys.exit(0)
if prior!=ORIGINAL_SHA:
    safe_stop('Requires exact v25 MainActivity.kt; unknown/customized source')

changes,status=launch_resource_changes()
changes[MAIN]=SOURCE.read_bytes()
# Do not overwrite any existing files unexpectedly. Every existing file receives
# a same-project backup before any write is attempted.
backup_dir=ROOT/'videonic-ui-backups'/('before-v26-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
backup_dir.mkdir(parents=True,exist_ok=False)
existed={}
for path in changes:
    existed[path]=path.is_file()
    if existed[path]:
        relative=path.relative_to(ROOT)
        dest=backup_dir/relative
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(path,dest)
        if digest(dest.read_bytes())!=digest(path.read_bytes()):
            safe_stop('Backup verification failed for '+str(relative))
written=[]
try:
    for path,payload in changes.items():
        path.parent.mkdir(parents=True,exist_ok=True)
        fd,name=tempfile.mkstemp(prefix='.v26-',dir=path.parent)
        tmp=Path(name)
        try:
            with os.fdopen(fd,'wb') as handle:handle.write(payload)
            if path.is_file(): os.chmod(tmp,path.stat().st_mode)
            else:os.chmod(tmp,0o644)
            if digest(tmp.read_bytes())!=digest(payload):raise RuntimeError('Staging checksum mismatch: '+str(path))
            os.replace(tmp,path)
            written.append(path)
        finally:
            if tmp.exists():tmp.unlink()
except BaseException:
    for path in reversed(written):
        if existed[path]:shutil.copy2(backup_dir/path.relative_to(ROOT),path)
        else:path.unlink(missing_ok=True)
    raise
print('SUCCESS: Videonic v26 installed.')
print(status)
print('Backup:',backup_dir.relative_to(ROOT))
print('Files:',*[str(p.relative_to(ROOT)) for p in changes],sep='\n  ')
print('Next: gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel')
