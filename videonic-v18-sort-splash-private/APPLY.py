#!/usr/bin/env python3
"""Guarded v18 patch: viewport sort, one splash, brand icon, private UI + MKV cache.
Only installed over the v17 MainActivity and v8 Private Folder code shipped in the
prior patches. Copies originals before touching files and stops on unknown edits.
"""
from pathlib import Path
from datetime import datetime
import hashlib
import os
import re
import sys

ROOT=Path.cwd()
PKG=Path(__file__).resolve().parent
SRC=ROOT/'app/src/main'
JAVA=SRC/'java/com/videonic/lite'
RES=SRC/'res'
FILES={
 JAVA/'MainActivity.kt': PKG/'MainActivity.kt',
 JAVA/'ui/PrivateFolderScreen.kt': PKG/'PrivateFolderScreen.kt',
 JAVA/'security/PrivateVault.kt': PKG/'PrivateVault.kt',
}
EXPECTED={
 JAVA/'MainActivity.kt': 'e358fab923f1421bcaad568c2641e04e2652e72f7d611d72509a83f6ecb2b7f6',
 JAVA/'ui/PrivateFolderScreen.kt': '4b744dde5b4869cfca476f54c643689d0c50ee9032594e6f63108d40e3f7c4da',
 JAVA/'security/PrivateVault.kt': None,
}
MANIFEST=SRC/'AndroidManifest.xml'
BASE_STYLE=RES/'values/styles.xml'
V31=RES/'values-v31/styles.xml'
NIGHT_V31=RES/'values-night-v31/styles.xml'
BRAND=RES/'drawable-nodpi/videonic_brand.png'

def sha(data):return hashlib.sha256(data).hexdigest()
def stop(msg):sys.exit('SAFE STOP: '+msg+' (nothing changed)')

if not all(p.is_file() for p in [*FILES,MANIFEST,BASE_STYLE,BRAND]):
    stop('Expected project files / logo are missing. Run in Videonic-Player-Lite root.')
if not all(p.is_file() for p in FILES.values()):stop('The v18 ZIP is incomplete.')
# The vault has not changed since v8; calculate expected hash from published v8.
EXPECTED[JAVA/'security/PrivateVault.kt']='e989730febdf5692e7ec64021aac0d766a26e3265227ff0ed5d2161995e36464'
old={p:p.read_bytes() for p in [*FILES,MANIFEST,BASE_STYLE,V31,NIGHT_V31] if p.is_file()}
new={target:payload.read_bytes() for target,payload in FILES.items()}
for path,payload in FILES.items():
    current=old[path]
    if sha(current) not in [EXPECTED[path],sha(payload.read_bytes())]:
        stop(f'{path.relative_to(ROOT)} differs from the expected v17/v8 source')

manifest=old[MANIFEST].decode('utf-8')
if 'android:label="Videonic Player"' not in manifest:
    stop('Expected v17 Android app label not found')
# Use exactly the same un-tinted blue brand PNG in the Android launcher and the in-app splash.
icons=re.findall(r'android:icon="([^"]+)"',manifest)
if len(icons)!=1:stop('Unexpected AndroidManifest app-icon declaration')
if not any(x.startswith('@drawable/') or x.startswith('@mipmap/') for x in icons):
    stop('Unsupported launcher icon reference')
manifest=re.sub(r'android:icon="[^"]+"','android:icon="@drawable/videonic_brand"',manifest,count=1)
if 'android:roundIcon=' in manifest:
    manifest=re.sub(r'android:roundIcon="[^"]+"','android:roundIcon="@drawable/videonic_brand"',manifest,count=1)
else:
    manifest=manifest.replace('android:icon="@drawable/videonic_brand"',
        'android:icon="@drawable/videonic_brand" android:roundIcon="@drawable/videonic_brand"',1)
new[MANIFEST]=manifest.encode('utf-8')

styles=old[BASE_STYLE].decode('utf-8')
pattern=r'(<style\s+name=["\']Theme\.Videonic["\'][^>]*>)(.*?)(</style>)'
m=re.search(pattern,styles,re.S)
if m is None:stop('Theme.Videonic not found in values/styles.xml')
section=m.group(0)
if 'windowSplashScreenAnimatedIcon' in section or 'windowSplashScreenBackground' in section:
    stop('Base theme already has custom splash configuration')
splash_items=('\n<item name="android:windowSplashScreenBackground">#FFFFFF</item>'
              '\n<item name="android:windowSplashScreenAnimatedIcon">@drawable/videonic_brand</item>')
v31_style=styles[:m.start()]+m.group(1)+m.group(2)+splash_items+m.group(3)+styles[m.end():]
for path in [V31,NIGHT_V31]:
    if path in old and old[path].decode('utf-8')!=v31_style:
        stop(f'{path.relative_to(ROOT)} has existing custom styles; not overwriting')
    new[path]=v31_style.encode('utf-8')

if all(old.get(p)==content for p,content in new.items()):
    print('ALREADY APPLIED: Videonic Player v18')
    sys.exit(0)

# All source files are either the expected old version or the expected new version.
# The files and logo are backed up in the original directory structure.
backup=ROOT/'videonic-ui-backups'/('before-v18-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for p,data in old.items():
        target=backup/p.relative_to(ROOT)
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data)
        if sha(target.read_bytes())!=sha(data):raise OSError('Backup verification failed')
except Exception as e:stop('Backup failure: '+str(e))

written=[]
try:
    for p,data in new.items():
        p.parent.mkdir(parents=True,exist_ok=True)
        temp=p.with_name(p.name+'.videonic-v18-tmp')
        temp.write_bytes(data)
        if sha(temp.read_bytes())!=sha(data):raise OSError('Write check failed')
        os.replace(temp,p)
        written.append(p)
except Exception as e:
    for p in written:
        if p in old:p.write_bytes(old[p])
        else:p.unlink(missing_ok=True)
    stop('Install rolled back due to: '+str(e))
print('SUCCESS: Videonic Player v18 applied. Backups:',backup)
print('Next: compileDebugKotlin in Codespaces; then build the debug APK.')
