#!/usr/bin/env python3
"""Targeted v14.1 fix: opt in only the two new Material3 decoder sheets."""
from pathlib import Path
import hashlib
from datetime import datetime
import os
import sys

root=Path.cwd()
source=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
BASE_SHA='ff2502fcb44a8a3297d9adb369db1fa13065c31fafd42f42fac78e1e4e1d27cd'
NEEDED=(
    '@Composable\nprivate fun DecoderChoiceSheet(',
    '@Composable\nprivate fun PlayerDecoderDetailsSheet(',
)
REPLACEMENTS=(
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DecoderChoiceSheet(',
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun PlayerDecoderDetailsSheet(',
)

def sha(data):
    return hashlib.sha256(data).hexdigest()

if not source.is_file():
    sys.exit('SAFE STOP: Run from the Videonic project root (MainActivity.kt not found).')
raw=source.read_bytes()
try:
    content=raw.decode('utf-8')
except UnicodeError:
    sys.exit('SAFE STOP: MainActivity.kt is not UTF-8.')

if all(text in content for text in REPLACEMENTS):
    print('ALREADY APPLIED: Decoder bottom sheets have Material3 opt-ins.')
    sys.exit(0)

if sha(raw)!=BASE_SHA:
    sys.exit('SAFE STOP: MainActivity.kt does not match the original v14 patch. No files changed. Send your current MainActivity.kt for a tailored fix.')

for before, after in zip(NEEDED, REPLACEMENTS):
    if content.count(before)!=1:
        sys.exit('SAFE STOP: Expected decoder sheet was not found exactly once. No files changed.')
    content=content.replace(before,after,1)

backup=root/'videonic-ui-backups'/('before-v14-1-material-optin-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
backup.mkdir(parents=True,exist_ok=False)
saved=backup/'MainActivity.kt'
saved.write_bytes(raw)
if saved.read_bytes()!=raw:
    sys.exit('SAFE STOP: Unable to validate backup. No files changed.')
tmp=source.with_name('MainActivity.kt.v141tmp')
try:
    tmp.write_text(content,encoding='utf-8')
    if tmp.read_text(encoding='utf-8')!=content:
        raise IOError('temporary write failed')
    os.replace(tmp,source)
except Exception as exc:
    tmp.unlink(missing_ok=True)
    sys.exit(f'SAFE STOP: Could not apply update: {exc}. Backup at {saved}')

print('SUCCESS: v14.1 Material3 decoder sheet opt-in applied.')
print(f'Backup: {saved}')
print('Next: compile :app:compileDebugKotlin in Codespaces.')
