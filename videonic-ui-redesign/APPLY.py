#!/usr/bin/env python3
"""Safe replacement for the exact MainActivity uploaded by the user."""
from pathlib import Path
from datetime import datetime
import hashlib
import shutil
import sys

project = Path.cwd()
target = project / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
payload = Path(__file__).resolve().parent / 'MainActivity.kt'
expected = '66c2eea8d23c02a65514ee65231300172421eccf4411a19dd07c64d8f41661ca'
if not target.is_file() or not (project / 'app/build.gradle.kts').exists():
    sys.exit('STOP: Open the Videonic-Player-Lite project root and try again.')
actual = hashlib.sha256(target.read_bytes()).hexdigest()
new = hashlib.sha256(payload.read_bytes()).hexdigest()
if actual == new:
    print('Already applied. No changes needed.')
    sys.exit(0)
if actual != expected:
    print('STOP: Your current MainActivity.kt differs from the file used for this redesign.')
    print('Nothing was changed. Existing SHA256:', actual)
    print('Upload your newer MainActivity.kt if there were changes since your export.')
    sys.exit(2)
backup_dir = project / 'videonic-ui-backups'
backup_dir.mkdir(exist_ok=True)
backup = backup_dir / ('MainActivity.' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.kt')
shutil.copy2(target,backup)
shutil.copy2(payload,target)
print('UI redesign applied to:', target)
print('Backup:', backup)
print('No other source files modified.')
