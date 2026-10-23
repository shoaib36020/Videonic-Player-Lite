#!/usr/bin/env python3
"""Videonic v16.1 compact APK: preserve v16 player UX, remove heavyweight native LibVLC."""
from pathlib import Path
from datetime import datetime
import hashlib
import os
import re
import sys

ROOT = Path.cwd()
PKG = Path(__file__).resolve().parent
MAIN = ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
VLC = ROOT/'app/src/main/java/com/videonic/lite/player/VideonicSoftwarePlayerActivity.kt'
GRADLE = ROOT/'app/build.gradle.kts'
MANIFEST = ROOT/'app/src/main/AndroidManifest.xml'
FILES = (MAIN, VLC, GRADLE, MANIFEST)
V16_MAIN = '2039607fe8dba68ebc494582bc8612cc55d3039588d40381ef837c9934d9dad4'
V16_VLC = '562e00c3dd6632106a15b51c0b210d9fa545f658c7a5c9724cbb4a7367c4ea2d'
COMPACT_MAIN = '10e46cb0706e5463240fe6d57e570cb2e7b590912d56bc486a2f74b20166ed26'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def stop(message):
    sys.exit('SAFE STOP: ' + message + ' No project files were changed.')

for needed in (MAIN, GRADLE, MANIFEST, PKG/'MainActivity.kt'):
    if not needed.is_file():
        stop('Missing ' + str(needed) + '. Run from your Codespaces project root after extracting ZIP.')

new_main = (PKG/'MainActivity.kt').read_bytes()
if digest(new_main) != COMPACT_MAIN:
    stop('The ZIP contents failed verification.')

old = {path: path.read_bytes() if path.exists() else None for path in FILES}
current_main = digest(old[MAIN])
if current_main not in {V16_MAIN, COMPACT_MAIN}:
    print('MainActivity SHA256: ' + current_main)
    stop('MainActivity differs from distributed v16. Upload current Kotlin source for a tailored patch.')
if old[VLC] is not None and digest(old[VLC]) != V16_VLC:
    stop('The software player file has been changed; refusing to delete it.')

gradle = old[GRADLE].decode('utf-8')
manifest = old[MANIFEST].decode('utf-8')

# Only remove the exact standalone libvlc dependency. Keep all other dependencies.
lib_line = re.compile(r'(?m)^\s*implementation\("org\.videolan\.android:libvlc-all:[0-9][^"\n]*"\)\s*\n')
if gradle.count('org.videolan.android:libvlc-all') > 1:
    stop('Multiple LibVLC dependency declarations exist in Gradle.')
if 'org.videolan.android:libvlc-all' in gradle and not lib_line.search(gradle):
    stop('Unexpected LibVLC Gradle dependency structure.')
new_gradle, count = lib_line.subn('\n', gradle)
if count:
    new_gradle = re.sub(r'(?m)^\s*// LGPL-2\.1 native software video fallback[^\n]*\n', '', new_gradle)

# Remove exactly the activity created by the v16 installer.
activity_line = re.compile(r'(?m)^\s*<activity android:name="\.player\.VideonicSoftwarePlayerActivity" android:exported="false" android:configChanges="orientation\|screenSize\|keyboardHidden" android:resizeableActivity="true"\s*/>\s*\n')
if 'VideonicSoftwarePlayerActivity' in manifest and not activity_line.search(manifest):
    stop('Unexpected software player AndroidManifest entry.')
new_manifest, count_manifest = activity_line.subn('\n', manifest)
if 'VideonicSoftwarePlayerActivity' in new_manifest:
    stop('Another software player manifest entry remains.')

if current_main == V16_MAIN and old[VLC] is None:
    stop('v16 main source exists but LibVLC activity is already missing. Project is inconsistent.')
if current_main == COMPACT_MAIN and old[VLC] is None and new_gradle == gradle and new_manifest == manifest:
    print('ALREADY APPLIED: Videonic v16.1 compact version is installed.')
    sys.exit(0)

replacement = {MAIN: new_main, VLC: None, GRADLE: new_gradle.encode(), MANIFEST: new_manifest.encode()}
if all(replacement[p] == old[p] for p in FILES):
    print('ALREADY APPLIED.')
    sys.exit(0)

backup = ROOT/'videonic-ui-backups'/('before-v16-1-compact-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    backup.mkdir(parents=True, exist_ok=False)
    for p in FILES:
        if old[p] is None:
            continue
        folder = backup / str(p.relative_to(ROOT))
        folder.parent.mkdir(parents=True, exist_ok=True)
        folder.write_bytes(old[p])
        if folder.read_bytes() != old[p]:
            raise IOError('Backup verification failed for ' + str(p))
except Exception as exc:
    stop('Could not create complete backups: ' + str(exc))

staged = {}
changed = []
try:
    for p in FILES:
        if replacement[p] is None or replacement[p] == old[p]:
            continue
        tmp = p.with_name(p.name + '.v16-1tmp')
        tmp.write_bytes(replacement[p])
        if digest(tmp.read_bytes()) != digest(replacement[p]):
            raise IOError('Staging verification failed for ' + str(p))
        staged[p] = tmp
    for p in FILES:
        if replacement[p] == old[p]:
            continue
        if replacement[p] is None:
            p.unlink(missing_ok=True)
        else:
            os.replace(staged[p], p)
        changed.append(p)
except Exception as exc:
    for p in changed:
        if old[p] is None:
            p.unlink(missing_ok=True)
        else:
            p.write_bytes(old[p])
    for tmp in staged.values():
        tmp.unlink(missing_ok=True)
    stop('Installation failed; source restored: ' + str(exc))
finally:
    for tmp in staged.values():
        tmp.unlink(missing_ok=True)

print('SUCCESS: Videonic Lite v16.1 compact applied.')
print('Removed native LibVLC dependency and software player Activity.')
print('Kept v16 swipe, hold-speed, rotation, aspect-ratio and gesture UI fixes.')
print('Created backup at:', backup)
print('NEXT: compileDebugKotlin, then assembleDebug, then measure app-debug.apk size.')
