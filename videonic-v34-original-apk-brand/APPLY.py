#!/usr/bin/env python3
"""Videonic v34 — use the app's OWN icon in the splash + export Google Play PNG.

No Kotlin code, manifest, launcher icon, upload key, Gradle, or app version changes.
Works only on supported v33 source + v31 splash (the clean white splash).
"""
from pathlib import Path
import hashlib
import re
import shutil
import struct
import subprocess
import tempfile
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path.cwd().resolve()
PATCH = Path(__file__).resolve().parent
RES = ROOT / 'app/src/main/res'
MAIN = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
SPLASH = RES / 'drawable-nodpi/videonic_splash_composite_v30.png'
EXPECTED_SOURCE = '7b0a91d5103aff972a62cd25ae76ca68f44048c06e016f53ebb89de098cefe34'
EXPECTED_SPLASH = '2f238dae7e0053b48b0196400abf8578ef0736b95886babadf4a8fdb99fb65b2'
MARKER = ROOT / 'videonic-brand-assets/.videonic-v34-installed'
OUTPUT = ROOT / 'videonic-brand-assets/Google-Play-Icon-512.png'


def stop(msg):
    raise SystemExit('SAFE STOP: ' + msg + '. No original app files were overwritten.')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_dimensions(path):
    try:
        b = path.read_bytes()[:24]
        if b[:8] != b'\x89PNG\r\n\x1a\n' or b[12:16] != b'IHDR':
            return None
        return struct.unpack('>II', b[16:24])
    except OSError:
        return None


def select_original_launcher():
    # The Android project uses its original launcher for the APK. Never install
    # AI-generated icons in its place. Prefer the source used by Videonic splash.
    # Some project revisions store their logo in density-specific drawable dirs.
    groups = [
        ('videonic_brand', 'videonic_brand.png'),
        ('videonic_launcher', 'videonic_launcher.png'),
        ('ic_launcher', 'ic_launcher.png'),
    ]
    for label, filename in groups:
        options = []
        for p in RES.glob('**/' + filename):
            if not p.is_file() or not p.parent.name.startswith(('drawable','mipmap')):
                continue
            d = png_dimensions(p)
            if d and min(d) >= 96 and abs(d[0]/d[1]-1) < .05:
                options.append((min(d), p))
        if options:
            # Highest density wins. This is the existing APK artwork, not a redraw.
            options.sort(key=lambda x:(-x[0],str(x[1])))
            return options[0][1]
    candidates = [str(p.relative_to(ROOT)) for p in RES.glob('**/*launcher*.png')]
    stop('Could not locate the original Videonic PNG logo in app resources; launcher candidates: '+repr(candidates[:10]))


if not MAIN.is_file() or not MANIFEST.is_file() or not SPLASH.is_file():
    stop('Run the script from the Videonic Android project root after v33')
if sha(MAIN) != EXPECTED_SOURCE:
    stop('MainActivity.kt is not the supported v33 build. Apply v33 first or share the hash')
try:
    xml = ET.parse(MANIFEST)
    app = xml.getroot().find('application')
    activities = [a for a in app.findall('activity') if 'MainActivity' in a.get('{http://schemas.android.com/apk/res/android}name','')]
    assert len(activities) == 1
    style = activities[0].get('{http://schemas.android.com/apk/res/android}theme')
except Exception:
    stop('The launcher activity is not identifiable in AndroidManifest.xml')
if style not in ('@style/VideonicLaunchV26', '@style/VideonicLaunchV27'):
    stop('Unrecognized native splash theme '+str(style))
stylefile = RES / ('values-v31/videonic_launch_v26.xml' if style.endswith('V26') else 'values-v31/videonic_launch_v27.xml')
try:
    xt = ET.parse(stylefile)
    items = {e.get('name'):(e.text or '').strip() for st in xt.findall('style') for e in st.findall('item')}
except Exception:
    stop('Cannot read Android 12 splash resource style')
if items.get('android:windowSplashScreenAnimatedIcon') != '@drawable/videonic_splash_center_v28' or 'android:windowSplashScreenBrandingImage' in items:
    stop('Splash theme was modified; expected the native single-screen v31-style splash')

if MARKER.exists():
    expected=MARKER.read_text().strip().splitlines()
    if len(expected) == 2 and sha(SPLASH) == expected[0] and OUTPUT.is_file() and sha(OUTPUT) == expected[1]:
        print('ALREADY APPLIED: Videonic v34 original-icon splash + Play icon. No changes.')
        raise SystemExit(0)
    stop('v34 marker exists but generated files differ, do not overwrite')
if sha(SPLASH) != EXPECTED_SPLASH:
    stop('Current splash does not match the tested v31 clean screen')
if OUTPUT.exists():
    stop('A Google Play icon already exists in the destination folder')

original = select_original_launcher()
print('Original APK icon found:', original.relative_to(ROOT), png_dimensions(original))
try:
    subprocess.run(['javac','-version'], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
except (OSError, subprocess.CalledProcessError):
    stop('Java JDK compiler unavailable; Codespaces Android Gradle JDK is required')

with tempfile.TemporaryDirectory(prefix='videonic-v34-') as tmp:
    tmp=Path(tmp)
    shutil.copy2(PATCH/'BrandAssets.java',tmp/'BrandAssets.java')
    subprocess.run(['javac',str(tmp/'BrandAssets.java')],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    splash_new=tmp/'splash.png'
    store_new=tmp/'store.png'
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(tmp),'BrandAssets',str(original),str(splash_new),str(store_new)],check=True)
    if png_dimensions(splash_new)!=(864,864) or png_dimensions(store_new)!=(512,512):
        stop('Unexpected generated image sizes')
    if sha(SPLASH)!=EXPECTED_SPLASH:
        stop('Splash changed during image generation')
    backup = ROOT/'videonic-ui-backups'/('before-v34-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))/SPLASH.relative_to(ROOT)
    backup.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(SPLASH,backup)
    if sha(backup)!=EXPECTED_SPLASH:
        stop('Splash backup failed verification')
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(store_new, OUTPUT)
    shutil.copy2(splash_new,SPLASH)
    MARKER.write_text(sha(SPLASH)+'\n'+sha(OUTPUT)+'\n')
    print('SUCCESS: v34 uses the original APK blue play icon with the clean centered Videonic Player splash.')
    print('Google Play icon:', OUTPUT.relative_to(ROOT))
    print('Splash backup:', backup.relative_to(ROOT))
    print('Unchanged: launcher icon, app source, media player, Gradle, app version, signing key.')
