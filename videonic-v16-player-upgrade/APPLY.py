#!/usr/bin/env python3
"""v16 player patch — version-gated, checked backups, atomic multi-file update."""
from pathlib import Path
from datetime import datetime
import hashlib,os,sys

ROOT=Path.cwd()
PKG=Path(__file__).resolve().parent
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
VLC=ROOT/'app/src/main/java/com/videonic/lite/player/VideonicSoftwarePlayerActivity.kt'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
GRADLE=ROOT/'app/build.gradle.kts'
HASH_BASE={
 '86fb72ecd1374720e841cfdc4b3c73340c08350192c8a3ccefdcaa777ced9ae1', # original v15
 '4ff98010a3e59fbaa88b6bf83a2e1af1957244b74ccdd10f8a4a578d73d29ae9', # user's fixed v15 (bad import removed)
}
HASH_NEW='''2039607fe8dba68ebc494582bc8612cc55d3039588d40381ef837c9934d9dad4'''
HASH_VLC='''562e00c3dd6632106a15b51c0b210d9fa545f658c7a5c9724cbb4a7367c4ea2d'''
LIB='implementation("org.videolan.android:libvlc-all:3.6.5")'
COMPONENT='android:name=".player.VideonicSoftwarePlayerActivity"'

def digest(x):return hashlib.sha256(x).hexdigest()
def stop(message):sys.exit('SAFE STOP: '+message+' No project files were modified.')

files=[MAIN,VLC,MANIFEST,GRADLE]
if any(not path.is_file() for path in [MAIN,MANIFEST,GRADLE]):stop('Run from the main Videonic GitHub Codespaces project root.')
if any(not (PKG/name).is_file() for name in ['MainActivity.kt','VideonicSoftwarePlayerActivity.kt']):stop('Extract the whole ZIP before running APPLY.py.')
if digest((PKG/'MainActivity.kt').read_bytes())!=HASH_NEW or digest((PKG/'VideonicSoftwarePlayerActivity.kt').read_bytes())!=HASH_VLC:stop('ZIP source integrity mismatch.')
old={file:file.read_bytes() if file.exists() else None for file in files}
main_hash=digest(old[MAIN])
if main_hash not in HASH_BASE and main_hash!=HASH_NEW:
 print('Existing MainActivity.kt SHA256:',main_hash)
 stop('This is not the v15 source after the import fix. Please upload the latest Kotlin file for a tailored patch.')
current_gradle=GRADLE.read_text()
current_manifest=MANIFEST.read_text()
if 'dependencies {' not in current_gradle:stop('Unexpected app/build.gradle.kts structure.')
if '<application ' not in current_manifest or '<activity android:name=".MainActivity"' not in current_manifest:stop('Unexpected manifest structure.')
if VLC.exists() and old[VLC]!=(PKG/'VideonicSoftwarePlayerActivity.kt').read_bytes():stop('There is already a different SoftwarePlayerActivity; no overwrite allowed.')
if LIB not in current_gradle and 'org.videolan.android:libvlc-all' in current_gradle:stop('Different LibVLC version already configured.')
if COMPONENT not in current_manifest and 'VideonicSoftwarePlayerActivity' in current_manifest:stop('Another software-player manifest definition exists.')
# Do not change any user Gradle options, app ID, versions, or existing manifest attributes.
new_gradle=current_gradle if LIB in current_gradle else current_gradle.replace('dependencies {','dependencies {\n    // LGPL-2.1 native software video fallback (HEVC Main10, when supported by CPU)\n    '+LIB,1)
new_manifest=current_manifest if COMPONENT in current_manifest else current_manifest.replace('<activity android:name=".MainActivity"','<activity android:name=".player.VideonicSoftwarePlayerActivity" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden" android:resizeableActivity="true"/>\n        <activity android:name=".MainActivity"',1)
new={MAIN:(PKG/'MainActivity.kt').read_bytes(),VLC:(PKG/'VideonicSoftwarePlayerActivity.kt').read_bytes(),GRADLE:new_gradle.encode(),MANIFEST:new_manifest.encode()}
if all(old[f]==new[f] for f in files):
 print('ALREADY APPLIED: Videonic v16 player upgrade is installed.');sys.exit(0)
backup=ROOT/'videonic-ui-backups'/('before-v16-player-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
 backup.mkdir(parents=True,exist_ok=False)
 for f in files:
  if old[f] is not None:
   destination=backup/f.name
   destination.write_bytes(old[f]);assert destination.read_bytes()==old[f]
except Exception as e:stop('Unable to create verified source backup: '+str(e))
# Stage all files and verify hashes BEFORE replacing any.
temps={};changed=[]
try:
 for f in files:
  if old[f]==new[f]:continue
  if not f.parent.is_dir():raise OSError('Directory missing: '+str(f.parent))
  temp=f.with_name(f.name+'.v16tmp')
  temp.write_bytes(new[f]);assert digest(temp.read_bytes())==digest(new[f])
  temps[f]=temp
 for f,temp in temps.items():
  os.replace(temp,f);changed.append(f)
except Exception as e:
 for f in changed:
  if old[f] is None:f.unlink(missing_ok=True)
  else:f.write_bytes(old[f])
 for temp in temps.values():temp.unlink(missing_ok=True)
 stop('Patch failed, source restored: '+str(e))
print('SUCCESS: Videonic Lite v16 player upgrade applied.')
print('Source backup:',backup)
print('New native dependency: libvlc-all 3.6.5; first build downloads ~83 MB of Android libraries.')
print('Next: compileDebugKotlin in Codespaces. Do not install an APK until it compiles.')
