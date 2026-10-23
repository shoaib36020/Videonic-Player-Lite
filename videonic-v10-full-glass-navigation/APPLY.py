#!/usr/bin/env python3
"""Exact v9 -> v10 navigation-only update with safe backup and conflict detection."""
from pathlib import Path
from datetime import datetime
import hashlib, os, sys

ROOT=Path.cwd()
TARGET=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
INCOMING=Path(__file__).resolve().parent/'MainActivity.kt'
EXPECTED_SHA='b2b2651fa5ae3a6416ccfa56588fb0c1ec1c7074a86a426180ea73f22a5dda7a'
NEW_SHA='713a4b607b0664793ccc2eff0655f00a022c1a3368b7e859fd7cc5d545026e87'

def sha(data): return hashlib.sha256(data).hexdigest()
def stop(msg): sys.exit('SAFE STOP: '+msg+' — no project files were changed.')
if not TARGET.is_file(): stop('MainActivity.kt is missing. Run this from the Codespaces repository root.')
raw=TARGET.read_bytes()
new=INCOMING.read_bytes()
if sha(new)!=NEW_SHA: stop('The update ZIP is damaged or incomplete.')
if sha(raw)==NEW_SHA:
    print('ALREADY APPLIED: full-bar Liquid Glass navigation v10 is installed.')
    sys.exit(0)
if sha(raw)!=EXPECTED_SHA:
    print('Current source SHA256:',sha(raw))
    stop('MainActivity.kt does not match the expected v9 project. Do not force-overwrite.')
backup=ROOT/'videonic-ui-backups'/('before-v10-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True,exist_ok=False)
(backup/'MainActivity.kt').write_bytes(raw)
try:
    tmp=TARGET.with_name(TARGET.name+'.v10-tmp')
    tmp.write_bytes(new)
    os.replace(tmp,TARGET)
except Exception as exc:
    TARGET.write_bytes(raw)
    stop('Install failed, previous source restored: '+str(exc))
print('SUCCESS: v10 full-bar Liquid Glass navigation installed.')
print('Backup:', backup)
print('Next: compileDebugKotlin, then assembleDebug if compilation succeeds.')
