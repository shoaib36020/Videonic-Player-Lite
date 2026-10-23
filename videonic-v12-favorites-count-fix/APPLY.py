#!/usr/bin/env python3
"""Small guarded v12 Favorites-count fix: MainActivity.kt only."""
from datetime import datetime
from pathlib import Path
import hashlib, os, sys

TARGET=Path.cwd()/"app/src/main/java/com/videonic/lite/MainActivity.kt"
EXPECTED_BEFORE='19ef078d33882dd19fa1bdad71b35a58fb4f24e393e85ae3f2deeb7928b2cd40'
EXPECTED_AFTER='cfcaf2931ee9a30a59c81520f0144585db360b14ec464ec23bfa47c947000294'
REPLACEMENTS=[('    var favorites by remember { mutableStateOf(library.favorites()) }\n', '    var favorites by remember { mutableStateOf(library.favorites()) }\n    // Only count favorite IDs for videos currently visible in the media library.\n    // Preserve missing IDs in local storage: they may become available after permissions are restored.\n    val availableFavorites = remember(favorites, videos) {\n        favorites.intersect(videos.map { it.id.toString() }.toSet())\n    }\n'), ('                                videos=videos.filter{it.id.toString() in favorites}.filter{it.title.contains(query,true)},\n                                favorites=favorites,onOpen={openVideo(it)},onMenu={videoMenu=it},', '                                videos=videos.filter{it.id.toString() in availableFavorites}.filter{it.title.contains(query,true)},\n                                favorites=availableFavorites,onOpen={openVideo(it)},onMenu={videoMenu=it},'), ('                            } else MeScreen(videos,favorites,playlistNames,library,{subpage=it})', '                            } else MeScreen(videos,availableFavorites,playlistNames,library,{destination -> query="";subpage=destination})')]

def digest(data): return hashlib.sha256(data).hexdigest()
def stop(msg): sys.exit("SAFE STOP: "+msg+"; source unchanged.")
if not TARGET.is_file(): stop("MainActivity.kt missing. Run this script from the project root")
old=TARGET.read_bytes()
current_sha=digest(old)
if current_sha==EXPECTED_AFTER:
    print("ALREADY APPLIED: Favorites count fix is installed.")
    raise SystemExit(0)
if current_sha!=EXPECTED_BEFORE:
    print("Current MainActivity.kt SHA-256:",current_sha)
    stop("Expected the exact v12 source. Do not force; provide your current MainActivity.kt")
try:
    text=old.decode("utf-8")
    for before,after in REPLACEMENTS:
        if text.count(before)!=1: stop("Expected code fragment not found exactly once")
        text=text.replace(before,after,1)
    new=text.encode("utf-8")
    if digest(new)!=EXPECTED_AFTER: stop("Patched source checksum mismatch")
    backup_dir=Path.cwd()/"videonic-ui-backups"
    backup_dir.mkdir(exist_ok=True)
    backup=backup_dir/("before-v12-favorites-"+datetime.now().strftime("%Y%m%d-%H%M%S-%f")+".kt")
    backup.write_bytes(old)
    if backup.read_bytes()!=old: stop("Backup verification failed")
    temp=TARGET.with_name(TARGET.name+".favtmp")
    try:
        temp.write_bytes(new)
        if digest(temp.read_bytes())!=EXPECTED_AFTER: raise IOError("Temp verification failed")
        os.replace(temp,TARGET)
    finally:
        if temp.exists(): temp.unlink()
except Exception as e:
    stop(f"Could not apply: {e}")
print("SUCCESS: v12 Favorites count/search consistency fix applied.")
print("Saved IDs are preserved; only available videos contribute to the displayed count.")
print("Backup:",backup)
