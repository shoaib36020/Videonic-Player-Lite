#!/usr/bin/env python3
"""Videonic Player v19: one branded splash, full-bleed launcher icon.

Safe to apply after the distributed v18 source. Touches only Android manifest,
Android 12+ splash themes, and icon assets. It does not modify Kotlin, private
vault or playback. Creates backups and refuses unexpected source versions.
"""
from datetime import datetime
from pathlib import Path
import hashlib
import os
import re
import sys

ROOT=Path.cwd()
HERE=Path(__file__).resolve().parent
SRC=ROOT/'app/src/main'
ACTIVITY=SRC/'java/com/videonic/lite/MainActivity.kt'
MANIFEST=SRC/'AndroidManifest.xml'
LIGHT=SRC/'res/values-v31/styles.xml'
DARK=SRC/'res/values-night-v31/styles.xml'
BRAND=SRC/'res/drawable-nodpi/videonic_brand.png'

BASE_ACTIVITY_SHA='ebdf5607a08d53cc553fd54c1dab6f9ea315aa7763dd60b1602565bac7611406'
V18_SHA={
 MANIFEST:'3f1ac7dc5b4a11b6bbcbcdea69ac40d9b28cd76c428ab942f384bc8f1f25d426',
 LIGHT:'50b2681c54cf04a285ba3431b90d2f789a9ab73aca84e8a7197f9ea5bd8a112f',
 DARK:'50b2681c54cf04a285ba3431b90d2f789a9ab73aca84e8a7197f9ea5bd8a112f',
 BRAND:'c182feb7a7d4ed434e3846b4b93858a4f57d1291ab33cb56d33e841daaa97405',
}

V19_SHA={
 MANIFEST:'ee30a487d61b775fef4e7bf228f512315637dfaf22837487fc650a638acdbda2',
 LIGHT:'cdb63c51eb30816d1451266c810ab9a8c24f4193195720d9be0f62c003877fac',
 DARK:'cdb63c51eb30816d1451266c810ab9a8c24f4193195720d9be0f62c003877fac',
 BRAND:'043bdb25330a84833405f766aef4646694352a9aa0a3043522567255d3992b93',
}

def sha(data): return hashlib.sha256(data).hexdigest()
def stop(message):sys.exit('SAFE STOP: '+message+' — no changes were made.')

if not ACTIVITY.is_file() or sha(ACTIVITY.read_bytes())!=BASE_ACTIVITY_SHA:
    stop('MainActivity.kt does not match the distributed v18 build; send your current file for a compatible patch')

replace={}
old={}
for p in V18_SHA:
    if not p.is_file():stop('Expected v18 file is missing: '+str(p.relative_to(ROOT)))
    old[p]=p.read_bytes()
    if sha(old[p]) not in (V18_SHA[p],V19_SHA[p]):
        stop('Unexpected edits to '+str(p.relative_to(ROOT)))

manifest=old[MANIFEST].decode('utf-8')
manifest2=manifest.replace('android:icon="@drawable/videonic_brand"',
                           'android:icon="@mipmap/videonic_launcher"')
manifest2=manifest2.replace('android:roundIcon="@drawable/videonic_brand"',
                            'android:roundIcon="@mipmap/videonic_launcher"')
if 'android:icon="@mipmap/videonic_launcher"' not in manifest2 or 'android:roundIcon="@mipmap/videonic_launcher"' not in manifest2:
    stop('Cannot find expected icon references in manifest')
replace[MANIFEST]=manifest2.encode('utf-8')

for p in (LIGHT,DARK):
    content=old[p].decode('utf-8')
    updated=content.replace('name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_brand',
                            'name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_splash_blank')
    if 'windowSplashScreenAnimatedIcon">@drawable/videonic_splash_blank' not in updated:
        stop('Cannot find expected Android 12 splash icon item in '+str(p.relative_to(ROOT)))
    replace[p]=updated.encode('utf-8')

# Use one normalized logo for in-app branding: the white triangle must stay
# white even on dark backgrounds instead of becoming a transparent dark hole.
for payload in (HERE/'resources').rglob('*'):
    if payload.is_file():
        replace[SRC/'res'/payload.relative_to(HERE/'resources')]=payload.read_bytes()

# Validate every preexisting destination before creating any backup or writing.
for p,output in replace.items():
    if p.exists():
        old.setdefault(p,p.read_bytes())
        actual=sha(old[p])
        accepted=(sha(output),)
        if p in V18_SHA:accepted=(V18_SHA[p],V19_SHA[p])
        if actual not in accepted:
            stop('Unexpected existing resource or edits at '+str(p.relative_to(ROOT)))

if all(p.is_file() and p.read_bytes()==output for p,output in replace.items()):
    print('ALREADY APPLIED: Videonic Player v19 splash and icon fix')
    sys.exit(0)

backup=ROOT/'videonic-ui-backups'/('before-v19-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for p,content in old.items():
        dest=backup/p.relative_to(ROOT)
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(content)
        if sha(dest.read_bytes())!=sha(content):raise IOError('Backup hash mismatch')
except Exception as e:
    stop('Backup failed: '+str(e))

written=[]
try:
    for p,content in replace.items():
        p.parent.mkdir(parents=True,exist_ok=True)
        temp=p.with_name(p.name+'.v19-tmp')
        temp.write_bytes(content)
        if sha(temp.read_bytes())!=sha(content):raise IOError('Temporary write hash mismatch')
        os.replace(temp,p)
        written.append(p)
except Exception as e:
    for p in reversed(written):
        if p in old:p.write_bytes(old[p])
        else:p.unlink(missing_ok=True)
    stop('Changes rolled back after write failure: '+str(e))

print('SUCCESS: Videonic Player v19 icon and single branded splash applied.')
print('Saved originals:',backup)
print('Next: :app:compileDebugKotlin, then :app:assembleDebug in GitHub Codespaces.')
