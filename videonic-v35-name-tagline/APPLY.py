#!/usr/bin/env python3
"""Videonic v35: change launcher label to Videonic; use original icon and a minimal tagline.

Requires v34 installed over v33; updates only AndroidManifest.xml and the native
splash PNG.  Doesn't change applicationId, any Kotlin, signing, or release versions.
"""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path.cwd().resolve()
PATCH = Path(__file__).resolve().parent
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
SPLASH = ROOT / 'app/src/main/res/drawable-nodpi/videonic_splash_composite_v30.png'
PLAY_ICON = ROOT / 'videonic-brand-assets/Google-Play-Icon-512.png'
V34_MARKER = ROOT / 'videonic-brand-assets/.videonic-v34-installed'
V35_MARKER = ROOT / 'videonic-brand-assets/.videonic-v35-installed'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def stop(why: str) -> None:
    raise SystemExit('SAFE STOP: ' + why + '; app unchanged')


def dimensions(p: Path):
    data=p.read_bytes()[:24]
    if data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
        return None
    return struct.unpack('>II', data[16:24])


def launcher_label(manifest_text: str) -> str | None:
    root = ET.fromstring(manifest_text)
    app = root.find('application')
    if app is None:
        stop('Missing application element in manifest')
    acts = [a for a in app.findall('activity') if (a.get(ANDROID_NS+'name') or '').endswith('MainActivity')]
    if len(acts) != 1:
        stop('Expected exactly one MainActivity, found '+str(len(acts)))
    return acts[0].get(ANDROID_NS+'label') or app.get(ANDROID_NS+'label')


def updated_manifest(text: str) -> str:
    # Keep formatting, comments, intent filters and all non-label attributes intact.
    pattern = re.compile(r'<application\b[^>]*>', re.S)
    matches=list(pattern.finditer(text))
    if len(matches)!=1:
        stop('Expected one <application> start tag')
    match=matches[0]
    tag=match.group()
    attr=re.compile(r'\sandroid:label\s*=\s*(["\']).*?\1', re.S)
    if attr.search(tag):
        tag=attr.sub(' android:label="Videonic"',tag,count=1)
    else:
        tag=tag[:-1]+' android:label="Videonic">'
    new=text[:match.start()]+tag+text[match.end():]
    # Existing explicit activity labels override application labels on the launcher.
    patterns=list(re.finditer(r'<activity\b[^>]*>', new, re.S))
    target=[m for m in patterns if re.search(r'android:name\s*=\s*["\'][^"\']*MainActivity["\']',m.group())]
    if len(target)!=1:
        stop('Expected exactly one MainActivity activity tag')
    a=target[0]; tag=a.group()
    if attr.search(tag):
        tag=attr.sub(' android:label="Videonic"',tag,count=1)
        new=new[:a.start()]+tag+new[a.end():]
    # If MainActivity has no explicit label, the app label is inherited.
    try:
        value=launcher_label(new)
    except ET.ParseError as e:
        stop('Proposed manifest XML invalid: '+str(e))
    if value!='Videonic':
        stop('Unable to confirm final launcher label')
    return new


if not all(p.is_file() for p in (MANIFEST,SPLASH,PLAY_ICON,V34_MARKER)):
    stop('Install v34 first; expected manifest, splash, original icon and v34 marker')
marker_lines=V34_MARKER.read_text().strip().splitlines()
if len(marker_lines)!=2 or not all(re.fullmatch(r'[0-9a-f]{64}',x) for x in marker_lines):
    stop('Invalid v34 marker')
if sha(PLAY_ICON)!=marker_lines[1]:
    stop('Current original APK icon differs from v34; do not substitute another icon')
if dimensions(PLAY_ICON)!=(512,512) or dimensions(SPLASH)!=(864,864):
    stop('Unexpected icon or splash dimensions')

# An installed v35 patch is safe to re-run, never regenerating or overwriting.
if V35_MARKER.exists():
    records=V35_MARKER.read_text().strip().splitlines()
    if len(records)==2 and sha(MANIFEST)==records[0] and sha(SPLASH)==records[1]:
        print('ALREADY APPLIED: v35 launcher name and minimal splash unchanged.')
        raise SystemExit(0)
    stop('v35 marker present but files were changed since installation')
if sha(SPLASH)!=marker_lines[0]:
    stop('Expected original v34 splash was modified')
manifest_text=MANIFEST.read_text(encoding='utf-8')
try:
    ET.fromstring(manifest_text)
except ET.ParseError as e:
    stop('Existing Android manifest invalid: '+str(e))
new_manifest=updated_manifest(manifest_text)
if new_manifest==manifest_text:
    stop('Existing launcher name already Videonic; expected v34 state')

# Safety: check the native splash style still points to the artwork v34 installed.
root=ET.fromstring(manifest_text)
app=root.find('application')
act=next(x for x in app.findall('activity') if (x.get(ANDROID_NS+'name') or '').endswith('MainActivity'))
style=act.get(ANDROID_NS+'theme')
if style not in ('@style/VideonicLaunchV26','@style/VideonicLaunchV27'):
    stop('Unrecognized v34 splash theme '+str(style))
stylepath = ROOT / ('app/src/main/res/values-v31/videonic_launch_v26.xml' if style.endswith('V26') else 'app/src/main/res/values-v31/videonic_launch_v27.xml')
try:
    styleroot=ET.parse(stylepath).getroot()
    items={e.get('name'): (e.text or '').strip() for st in styleroot.findall('style') for e in st.findall('item')}
except (OSError,ET.ParseError):
    stop('Could not verify existing splash style')
if items.get('android:windowSplashScreenAnimatedIcon') != '@drawable/videonic_splash_center_v28' or 'android:windowSplashScreenBrandingImage' in items:
    stop('Unexpected native splash style; the original v34 single splash is required')

try:
    subprocess.run(['javac','-version'],check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
except (OSError, subprocess.CalledProcessError):
    stop('Java JDK unavailable')

with tempfile.TemporaryDirectory(prefix='videonic-v35-') as tmpdir:
    tmp=Path(tmpdir)
    shutil.copy2(PATCH/'SplashV35.java',tmp/'SplashV35.java')
    subprocess.run(['javac',str(tmp/'SplashV35.java')],check=True)
    produced=tmp/'v35-splash.png'
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(tmp),'SplashV35',str(PLAY_ICON),str(produced)],check=True)
    if dimensions(produced)!=(864,864):
        stop('Invalid rendered splash')
    original_manifest_sha=sha(MANIFEST)
    original_splash_sha=sha(SPLASH)
    if original_splash_sha!=marker_lines[0] or MANIFEST.read_text(encoding='utf-8')!=manifest_text:
        stop('Source modified concurrently')
    backup_base=ROOT/'videonic-ui-backups'/('before-v35-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
    backup_manifest=backup_base/MANIFEST.relative_to(ROOT)
    backup_splash=backup_base/SPLASH.relative_to(ROOT)
    for dest,src in ((backup_manifest,MANIFEST),(backup_splash,SPLASH)):
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(src,dest)
        if sha(dest)!=sha(src):
            stop('Failed to verify backup')
    # Stage changes and recover from backup on any write failure.
    staged=MANIFEST.with_name('.AndroidManifest.v35.tmp')
    try:
        staged.write_text(new_manifest,encoding='utf-8')
        os.chmod(staged,MANIFEST.stat().st_mode)
        if sha(MANIFEST)!=original_manifest_sha or sha(SPLASH)!=original_splash_sha:
            stop('Source changed while preparing v35')
        os.replace(staged,MANIFEST)
        shutil.copy2(produced,SPLASH)
        if launcher_label(MANIFEST.read_text(encoding='utf-8'))!='Videonic' or sha(SPLASH)!=sha(produced):
            raise RuntimeError('New app label or splash did not verify')
        V35_MARKER.write_text(sha(MANIFEST)+'\n'+sha(SPLASH)+'\n')
    except BaseException:
        staged.unlink(missing_ok=True)
        shutil.copy2(backup_manifest,MANIFEST)
        shutil.copy2(backup_splash,SPLASH)
        V35_MARKER.unlink(missing_ok=True)
        raise
    print('SUCCESS: Launcher app name: Videonic')
    print('SUCCESS: Splash: original Videonic icon / Videonic / Lite Video Player')
    print('Unchanged: app package, version code, Kotlin, SDK, R8, JKS, Google Play original icon.')
    print('Verified backups:',backup_base.relative_to(ROOT))
