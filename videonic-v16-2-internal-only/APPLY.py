#!/usr/bin/env python3
"""Videonic Lite v16.2: no external-player actions, internal decoder recovery only.

Version guarded. Backs up MainActivity.kt and atomically installs known source bytes.
"""
from pathlib import Path
from datetime import datetime
import hashlib, os, sys

ROOT = Path.cwd()
PKG = Path(__file__).resolve().parent
MAIN = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
INCOMING = PKG / 'MainActivity.kt'
OLD_SHA = '10e46cb0706e5463240fe6d57e570cb2e7b590912d56bc486a2f74b20166ed26'
NEW_SHA = '23b5ca72517123e6d5886c6be18b01ab6d09bc824a18789a5182b65e74790027'

def sha(content):
    return hashlib.sha256(content).hexdigest()

def stop(msg):
    sys.exit('SAFE STOP: ' + msg + ' No existing source files were modified.')

if not MAIN.is_file() or not INCOMING.is_file():
    stop('Missing source file. Extract ZIP in Codespaces project root and run APPLY.py from there.')
original = MAIN.read_bytes()
incoming = INCOMING.read_bytes()
if sha(incoming) != NEW_SHA:
    stop('Update source failed SHA-256 integrity validation.')
current = sha(original)
if current == NEW_SHA:
    print('ALREADY APPLIED: Videonic v16.2 internal-only playback choices.')
    sys.exit(0)
if current != OLD_SHA:
    print('Actual MainActivity.kt SHA-256:', current)
    stop('This update expects the distributed v16.1 Compact source. Send your current file for a tailored update.')

backup = ROOT / 'videonic-ui-backups' / ('before-v16-2-internal-only-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    destination = backup / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
    destination.parent.mkdir(parents=True, exist_ok=False)
    destination.write_bytes(original)
    if sha(destination.read_bytes()) != current:
        raise RuntimeError('Backup content verification failed')
except Exception as exc:
    stop('Could not complete checked backup: ' + str(exc))

temporary = MAIN.with_name(MAIN.name + '.v16-2tmp')
try:
    temporary.write_bytes(incoming)
    if sha(temporary.read_bytes()) != NEW_SHA:
        raise RuntimeError('Staged source content mismatch')
    os.replace(temporary, MAIN)
except Exception as exc:
    temporary.unlink(missing_ok=True)
    if sha(MAIN.read_bytes()) != current:
        MAIN.write_bytes(original)
    stop('Could not install patch: ' + str(exc))
finally:
    temporary.unlink(missing_ok=True)
print('SUCCESS: v16.2 in-app playback recovery applied; no external-player actions.')
print('Backup:', backup)
print('NEXT: compile Kotlin in Codespaces and check APK size before installing.')
