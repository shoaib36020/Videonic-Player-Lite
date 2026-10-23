#!/usr/bin/env python3
"""Videonic Player v19.1: manifest-drift compatible branded icon/splash update.
Only edits application icon attributes, Android 12 splash theme items and icon
resources; never replaces the complete manifest or an unrelated source file.
"""
from pathlib import Path
from datetime import datetime
import hashlib
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PKG = Path(__file__).resolve().parent
SRC = ROOT / 'app/src/main'
ACTIVITY = SRC / 'java/com/videonic/lite/MainActivity.kt'
MANIFEST = SRC / 'AndroidManifest.xml'
LIGHT = SRC / 'res/values-v31/styles.xml'
DARK = SRC / 'res/values-night-v31/styles.xml'
BRAND = SRC / 'res/drawable-nodpi/videonic_brand.png'
RESOURCE_DIR = PKG / 'resources'
V18_ACTIVITY = 'ebdf5607a08d53cc553fd54c1dab6f9ea315aa7763dd60b1602565bac7611406'
V18_BRAND = 'c182feb7a7d4ed434e3846b4b93858a4f57d1291ab33cb56d33e841daaa97405'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def stop(message):
    sys.exit('SAFE STOP: ' + message + ' — no changes were made.')

for path in (ACTIVITY, MANIFEST, LIGHT, DARK, BRAND):
    if not path.is_file():
        stop('Required v18 project file is missing: ' + str(path.relative_to(ROOT)))
if digest(ACTIVITY.read_bytes()) != V18_ACTIVITY:
    stop('MainActivity.kt is not the expected v18 source. Send the current source for a custom patch.')

old = {}
new = {}

def original(path):
    if path not in old and path.is_file():
        old[path] = path.read_bytes()
    return old.get(path)

# Parse to reject invalid/unexpected Manifest structures, but preserve all
# actual manifest text, permissions, service declarations, and activity settings.
manifest = original(MANIFEST).decode('utf-8')
try:
    xml = ET.fromstring(manifest)
except ET.ParseError as exc:
    stop('AndroidManifest.xml XML cannot be parsed: ' + str(exc))
if xml.tag != 'manifest' or len(xml.findall('application')) != 1:
    stop('Unexpected manifest structure; expected one application element')
open_tag = re.search(r'<application\b[^>]*>', manifest, re.S)
if not open_tag:
    stop('Cannot identify application tag in manifest')
app_tag = open_tag.group(0)
for attr in ('icon', 'roundIcon'):
    pattern = r'(?<![\w:])android:' + attr + r'\s*=\s*(["\'])(.*?)\1'
    matches = list(re.finditer(pattern, app_tag))
    if len(matches) > 1:
        stop('Multiple android:' + attr + ' values on application')
    if len(matches) == 1:
        current = matches[0].group(2)
        if not (current.startswith('@drawable/') or current.startswith('@mipmap/')):
            stop('Cannot safely update unexpected application icon: ' + current)
        app_tag = re.sub(pattern, 'android:' + attr + '="@mipmap/videonic_launcher"', app_tag, count=1)
    else:
        app_tag = app_tag[:-1].rstrip() + ' android:' + attr + '="@mipmap/videonic_launcher">'
updated_manifest = manifest[:open_tag.start()] + app_tag + manifest[open_tag.end():]
try:
    ET.fromstring(updated_manifest)
except ET.ParseError as exc:
    stop('Modified manifest would be invalid XML: ' + str(exc))
new[MANIFEST] = updated_manifest.encode('utf-8')

# Update splash items ONLY inside Theme.Videonic. Preserve other styles.
for path in (LIGHT, DARK):
    source = original(path).decode('utf-8')
    try:
        ET.fromstring(source)
    except ET.ParseError as exc:
        stop(str(path.relative_to(ROOT)) + ' is invalid XML: ' + str(exc))
    pattern = r'(<style\s+name=["\']Theme\.Videonic["\'][^>]*>)(.*?)(</style>)'
    match = re.search(pattern, source, re.S)
    if not match:
        stop('Theme.Videonic not found in ' + str(path.relative_to(ROOT)))
    body = match.group(2)
    item_pattern = r'(<item\s+name=["\']android:windowSplashScreenAnimatedIcon["\']\s*>)([^<]*)(</item>)'
    items = list(re.finditer(item_pattern, body))
    if len(items) != 1:
        stop('Expected a single splash icon item in ' + str(path.relative_to(ROOT)))
    old_splash = items[0].group(2).strip()
    if old_splash not in ('@drawable/videonic_brand', '@drawable/videonic_splash_blank'):
        stop('Unexpected splash icon setting ' + old_splash + ' in ' + str(path.relative_to(ROOT)))
    body = re.sub(item_pattern, lambda m: m.group(1) + '@drawable/videonic_splash_blank' + m.group(3), body, count=1)
    updated_style = source[:match.start()] + match.group(1) + body + match.group(3) + source[match.end():]
    try:
        ET.fromstring(updated_style)
    except ET.ParseError as exc:
        stop('New style XML would be invalid: ' + str(exc))
    new[path] = updated_style.encode('utf-8')

# Existing in-app artwork may be either v18 or already corrected v19.
payloads = list(RESOURCE_DIR.rglob('*'))
if not payloads or not all(x.is_file() for x in payloads if not x.is_dir()):
    stop('Incomplete patch resources')
for entry in payloads:
    if not entry.is_file():
        continue
    dest = SRC / 'res' / entry.relative_to(RESOURCE_DIR)
    content = entry.read_bytes()
    old_content = original(dest)
    if old_content is not None and old_content != content:
        if dest != BRAND or digest(old_content) != V18_BRAND:
            stop('Existing customized icon resource would be overwritten: ' + str(dest.relative_to(ROOT)))
    new[dest] = content

if all(path.is_file() and path.read_bytes() == data for path, data in new.items()):
    print('ALREADY APPLIED: Videonic Player v19.1 branding')
    sys.exit(0)

# Backup all original files that will change, not unrelated sources.
backup = ROOT / 'videonic-ui-backups' / ('before-v19-1-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
try:
    for path, data in old.items():
        if path not in new or new[path] == data:
            continue
        output = backup / path.relative_to(ROOT)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(data)
        if digest(output.read_bytes()) != digest(data):
            raise OSError('Backup checksum mismatch: ' + str(path))
except Exception as exc:
    stop('Backup failed: ' + str(exc))

written = []
try:
    for path, data in new.items():
        if path.is_file() and path.read_bytes() == data:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        temp = path.with_name(path.name + '.v19-1-tmp')
        temp.write_bytes(data)
        if digest(temp.read_bytes()) != digest(data):
            raise OSError('Temporary file checksum mismatch: ' + str(path))
        os.replace(temp, path)
        written.append(path)
except Exception as exc:
    for path in reversed(written):
        if path in old:
            path.write_bytes(old[path])
        else:
            path.unlink(missing_ok=True)
    stop('Changes were rolled back after: ' + str(exc))

print('SUCCESS: Videonic Player v19.1 splash and icon fix applied.')
print('Saved backups at:', backup)
print('Your manifest permissions, services, activities, and other settings were preserved.')
