#!/usr/bin/env python3
"""Safe, MainActivity-only Videonic Player v20 source update.

Only accepts the exact v18/v19.1 MainActivity; all newer Android resources,
playback services, libraries and Google Play signing configurations stay intact.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import sys
import tempfile
from datetime import datetime, timezone

ROOT = Path.cwd()
PKG = Path(__file__).resolve().parent
DEST = ROOT / "app/src/main/java/com/videonic/lite/MainActivity.kt"
NEW = PKG / "MainActivity.kt"
BASE_SHA256 = "ebdf5607a08d53cc553fd54c1dab6f9ea315aa7763dd60b1602565bac7611406"

def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def stop(message: str) -> None:
    raise SystemExit("SAFE STOP: " + message + " — no files changed.")

if not DEST.is_file():
    stop("Cannot find MainActivity.kt. Run from /workspaces/Videonic-Player-Lite")
if not NEW.is_file():
    stop("Patch is incomplete: missing MainActivity.kt")
original = DEST.read_bytes()
updated = NEW.read_bytes()
if not updated.startswith(b"package com.videonic.lite\n"):
    stop("Invalid patch source")
if digest(original) == digest(updated):
    print("ALREADY APPLIED: Videonic Player v20 — nothing changed.")
    raise SystemExit(0)
if digest(original) != BASE_SHA256:
    stop("Your MainActivity.kt differs from the verified v18/v19.1 source. Do not force the patch; share the latest file to get a compatible update")
backup_dir = ROOT / "videonic-ui-backups" / ("before-v20-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f"))
backup_dir.mkdir(parents=True, exist_ok=False)
backup_file = backup_dir / "MainActivity.kt"
shutil.copy2(DEST, backup_file)
if digest(backup_file.read_bytes()) != digest(original):
    stop("Backup verification failed")
with tempfile.NamedTemporaryFile(prefix=".videonic-v20-", suffix=".kt", dir=DEST.parent, delete=False) as handle:
    temp = Path(handle.name)
    handle.write(updated)
try:
    os.chmod(temp, DEST.stat().st_mode)
    if digest(temp.read_bytes()) != digest(updated):
        stop("Staged file verification failed")
    os.replace(temp, DEST)
finally:
    if temp.exists():
        temp.unlink()
print("SUCCESS: Videonic Player v20 applied.")
print("Backup:", backup_file.relative_to(ROOT))
print("Only MainActivity.kt was changed. Compile before assembling an APK.")
