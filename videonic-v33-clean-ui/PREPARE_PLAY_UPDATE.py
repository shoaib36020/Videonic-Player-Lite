#!/usr/bin/env python3
"""Optional step AFTER on-device testing: bump v1.0.2/code 3 to v1.0.3/code 4.

Never touches upload key or previously built bundles. Guarded, repeat-safe.
"""
from pathlib import Path
import shutil
p=Path('app/build.gradle.kts')
if not p.is_file():
    raise SystemExit('SAFE STOP: Run from Videonic project root')
s=p.read_text()
if 'versionCode = 4' in s and 'versionName = "1.0.3"' in s:
    print('ALREADY SET: v1.0.3 code 4')
    raise SystemExit(0)
if s.count('versionCode = 3') != 1 or s.count('versionName = "1.0.2"') != 1:
    raise SystemExit('SAFE STOP: Expected v1.0.2 code 3. No version changes made')
backup=p.parent/'build.gradle.kts.before-v33-play'
if backup.exists():
    raise SystemExit('SAFE STOP: Version backup already exists. Review it before proceeding')
shutil.copy2(p,backup)
s=s.replace('versionCode = 3','versionCode = 4',1)
s=s.replace('versionName = "1.0.2"','versionName = "1.0.3"',1)
p.write_text(s)
print('SUCCESS: v1.0.3, version code 4. Gradle backup:',backup)
print('Back up the previous signed AAB before invoking RELEASE.sh build.')
