#!/usr/bin/env python3
"""Videonic v36: polish the v35 native splash and improve horizontal hold-speed sensitivity.

Updates only MainActivity.kt (one gesture scaling expression) and the existing
native splash PNG. Requires the exact v33 UI/v35 branding baseline.
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

ROOT = Path.cwd().resolve()
PATCH = Path(__file__).resolve().parent
CODE = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
SPLASH = ROOT / 'app/src/main/res/drawable-nodpi/videonic_splash_composite_v30.png'
ICON = ROOT / 'videonic-brand-assets/Google-Play-Icon-512.png'
V35 = ROOT / 'videonic-brand-assets/.videonic-v35-installed'
V36 = ROOT / 'videonic-brand-assets/.videonic-v36-installed'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
KNOWN_MAIN_SHA = '7b0a91d5103aff972a62cd25ae76ca68f44048c06e016f53ebb89de098cefe34'
OLD = 'val movement=(dx / (size.width.coerceAtLeast(1)*.10f)).toInt()'
NEW = '''// A short 18dp horizontal drag selects one step in either direction.
                                                        // Independent of screen width; lock/reset semantics remain unchanged.
                                                        val movement=(dx / 18.dp.toPx()).toInt()'''


def digest(p:Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()

def stop(message:str):
    raise SystemExit('SAFE STOP: '+message+'; nothing was changed.')

def dims(p:Path):
    raw=p.read_bytes()[:24]
    if raw[:8]!=b'\x89PNG\r\n\x1a\n' or raw[12:16]!=b'IHDR':return None
    return struct.unpack('>II',raw[16:24])

required=[CODE,SPLASH,ICON,MANIFEST,V35,PATCH/'SplashV36.java']
if not all(p.is_file() for p in required):
    stop('This patch requires v35 already installed over v34, plus the original project resources')
records=V35.read_text().strip().splitlines()
if len(records)!=2 or not all(re.fullmatch(r'[0-9a-f]{64}',v) for v in records):
    stop('v35 patch marker invalid')
if digest(MANIFEST)!=records[0]:
    stop('Android manifest no longer matches the verified v35 installation')
if dims(ICON)!=(512,512) or dims(SPLASH)!=(864,864):
    stop('Original icon or splash has unexpected dimensions')
if V36.exists():
    r=V36.read_text().strip().splitlines()
    if len(r)==2 and r[0]==digest(CODE) and r[1]==digest(SPLASH):
        print('ALREADY APPLIED: Videonic v36 splash and speed swipe unchanged.')
        raise SystemExit(0)
    stop('v36 marker present but installed files were changed')
if digest(CODE)!=KNOWN_MAIN_SHA or digest(SPLASH)!=records[1]:
    stop('Expected v35 baseline modified; cannot safely apply gesture and splash update')
source=CODE.read_text(encoding='utf-8')
if source.count(OLD)!=1:
    stop('Expected original speed gesture formula not found exactly once')
updated=source.replace(OLD,NEW,1)
if updated.count(NEW)!=1 or updated.count(OLD)!=0:
    stop('Speed gesture update did not verify')

try:
    subprocess.run(['javac','-version'],check=True,capture_output=True)
except (OSError,subprocess.CalledProcessError):
    stop('Java JDK is required to render the native splash artwork')

with tempfile.TemporaryDirectory(prefix='videonic-v36-') as scratch:
    tmp=Path(scratch)
    shutil.copy2(PATCH/'SplashV36.java',tmp/'SplashV36.java')
    subprocess.run(['javac','SplashV36.java'],check=True,cwd=tmp)
    artwork=tmp/'splash.png'
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(tmp),'SplashV36',str(ICON),str(artwork)],check=True,cwd=tmp)
    if dims(artwork)!=(864,864):
        stop('Native splash generator produced an invalid PNG')
    main_sha=digest(CODE); splash_sha=digest(SPLASH)
    if main_sha!=KNOWN_MAIN_SHA or splash_sha!=records[1]:
        stop('Source files changed while update was being prepared')
    backup=ROOT/'videonic-ui-backups'/('before-v36-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S-%fZ'))
    for current in (CODE,SPLASH):
        saved=backup/current.relative_to(ROOT)
        saved.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(current,saved)
        if digest(saved)!=digest(current):
            stop('Could not verify source backup')
    staged=CODE.with_name('.MainActivity.v36.tmp')
    try:
        staged.write_text(updated,encoding='utf-8')
        os.chmod(staged,CODE.stat().st_mode)
        if digest(CODE)!=main_sha or digest(SPLASH)!=splash_sha:
            stop('Detected concurrent modification before applying')
        os.replace(staged,CODE)
        shutil.copy2(artwork,SPLASH)
        if CODE.read_text(encoding='utf-8')!=updated or digest(SPLASH)!=digest(artwork):
            raise RuntimeError('Updated files did not pass verification')
        V36.write_text(digest(CODE)+'\n'+digest(SPLASH)+'\n')
    except BaseException:
        staged.unlink(missing_ok=True)
        for current in (CODE,SPLASH):
            shutil.copy2(backup/current.relative_to(ROOT),current)
        V36.unlink(missing_ok=True)
        raise
print('SUCCESS: refined Videonic title (lighter weight) and clearer Lite Video Player subtitle')
print('SUCCESS: press-and-hold speed now advances one step per 18dp horizontal swipe, left or right')
print('Unchanged: launcher icon/name, speed lock/reset, gestures other than speed sensitivity, Kotlin playback logic, app version, signing')
print('Verified backups:',backup.relative_to(ROOT))
