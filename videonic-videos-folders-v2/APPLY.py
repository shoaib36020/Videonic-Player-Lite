#!/usr/bin/env python3
"""Targeted Videos / Folders screen update; does not modify other app screens."""
from pathlib import Path
from datetime import datetime
import sys

root = Path.cwd()
target = root / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
parts = Path(__file__).resolve().parent
if not target.is_file():
    sys.exit(f'ERROR: MainActivity.kt not found under {root}. Open the Videonic project root.')
text = target.read_text(encoding='utf-8')
marker = '// VIDEONIC_VIDEOS_FOLDERS_UI_V2'
if marker in text:
    print('Videos / Folders UI v2 was already applied. Nothing changed.')
    sys.exit(0)
anchors = [
    '@Composable\nprivate fun VideosLibraryHome(',
    '@Composable\nprivate fun FolderLibraryHome(',
    '@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun VideoList('
]
for anchor in anchors:
    if text.count(anchor) != 1:
        sys.exit('ERROR: Current MainActivity.kt does not match the previous three-screen UI patch. No files changed. Needed: '+anchor[:80])
a, b, c = [text.index(v) for v in anchors]
if not (a < b < c):
    sys.exit('ERROR: Unexpected screen order. No files changed.')
video = (parts/'VideosLibraryHome.ktpart').read_text(encoding='utf-8')
folder = (parts/'FolderLibraryHome.ktpart').read_text(encoding='utf-8')
updated = text[:a] + marker + '\n' + video + folder + text[c:]
backup = target.with_name(f'MainActivity.kt.before-videos-folders-v2-{datetime.now():%Y%m%d-%H%M%S}.bak')
backup.write_text(text, encoding='utf-8')
# Temporary write and atomic replacement so partial writes cannot corrupt the source.
tmp = target.with_name('MainActivity.kt.ui-v2.tmp')
tmp.write_text(updated, encoding='utf-8')
tmp.replace(target)
print('SUCCESS: Updated Videos list, Videos grid, Folders list and Folders grid.')
print('No other screens or project files were touched.')
print('Backup:', backup)
