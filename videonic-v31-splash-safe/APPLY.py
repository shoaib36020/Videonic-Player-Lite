#!/usr/bin/env python3
"""Fix Videonic v30 native splash cropping. Run from Android project root."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, os, shutil, tempfile
import xml.etree.ElementTree as ET

ROOT=Path.cwd()
PKG=Path(__file__).resolve().parent
RES=ROOT/'app/src/main/res'
DEST=RES/'drawable-nodpi/videonic_splash_composite_v30.png'
SRC=PKG/'videonic_splash_composite_v30.png'
DRAWABLE=RES/'drawable/videonic_splash_center_v28.xml'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
OLD_SHA='02931d7975f5f5a15a9618436abfcb413de2dd507742bfd9db8e2e3b6c96a966'
NEW_SHA='2f238dae7e0053b48b0196400abf8578ef0736b95886babadf4a8fdb99fb65b2'
EXPECTED_XML='778f60655874ea1cf261b30230bbd714e2655bcd531894961b806c2c9ae3102a'
A='{http://schemas.android.com/apk/res/android}'

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def stop(reason): raise SystemExit('SAFE STOP: '+reason+'; nothing was changed.')
def read_xml(p):
    try: return ET.parse(p).getroot()
    except (OSError,ET.ParseError) as exc: stop('Invalid XML '+str(p)+': '+str(exc))

if not SRC.is_file() or sha(SRC)!=NEW_SHA: stop('Patch archive image missing or corrupt')
if not MANIFEST.is_file() or not DEST.is_file(): stop('Run from the existing Videonic Android project root')
if not DRAWABLE.is_file() or sha(DRAWABLE)!=EXPECTED_XML:
    stop('Splash layer-list does not match v30')
root=read_xml(MANIFEST)
app=root.find('application')
if app is None: stop('AndroidManifest application not found')
acts=[node for node in app.findall('activity') if (node.get(A+'name') or '').endswith('MainActivity')]
if len(acts)!=1: stop('Could not identify single MainActivity entry')
ref=acts[0].get(A+'theme')
if ref not in ('@style/VideonicLaunchV26','@style/VideonicLaunchV27'):
    stop('The installed splash theme is not supported')
variant=ref.split('/')[-1]
style_file=RES/'values-v31'/('videonic_launch_v26.xml' if variant=='VideonicLaunchV26' else 'videonic_launch_v27.xml')
styles=[s for s in read_xml(style_file).findall('style') if s.get('name')==variant]
if len(styles)!=1: stop('Launch theme definition does not match v30')
items={e.get('name'):(e.text or '').strip() for e in styles[0].findall('item')}
if items.get('android:windowSplashScreenAnimatedIcon')!='@drawable/videonic_splash_center_v28':
    stop('Splash style icon path has changed')
if 'android:windowSplashScreenBrandingImage' in items:
    stop('Splash branding should not be separately placed at bottom')

found=sha(DEST)
if found==NEW_SHA:
    print('ALREADY APPLIED: v31 splash fix present, nothing changed.')
    raise SystemExit(0)
if found!=OLD_SHA: stop('Expected the exact v30 splash image; no source overwritten')

backups=ROOT/'videonic-ui-backups'
back=backups/('before-v31-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))/DEST.relative_to(ROOT)
back.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(DEST,back)
if sha(back)!=OLD_SHA: stop('Backup integrity failed')
fd,path=tempfile.mkstemp(prefix='.videonic-v31-',dir=DEST.parent)
tmp=Path(path)
try:
    with os.fdopen(fd,'wb') as stream: stream.write(SRC.read_bytes())
    if sha(tmp)!=NEW_SHA: raise RuntimeError('Written asset failed checksum')
    os.chmod(tmp, DEST.stat().st_mode)
    os.replace(tmp,DEST)
except BaseException:
    tmp.unlink(missing_ok=True)
    if sha(DEST)!=OLD_SHA: shutil.copy2(back,DEST)
    raise
print('SUCCESS: Videonic v31 single splash artwork installed.')
print('Original artwork backup:',back)
print('Playback, sorting, rotations and speed-lock code were not modified.')
