#!/usr/bin/env python3
"""Videonic v14: guarded, atomic MainActivity + PlaybackService update."""
from datetime import datetime
from pathlib import Path
import hashlib
import os
import sys

ROOT = Path.cwd()
PACKAGE = Path(__file__).resolve().parent
PATHS = {
    "MainActivity.kt": ROOT / "app/src/main/java/com/videonic/lite/MainActivity.kt",
    "VideonicPlaybackService.kt": ROOT / "app/src/main/java/com/videonic/lite/player/VideonicPlaybackService.kt",
}
# This full source includes all v13 fixes and supports either v12 source if v13
# has not yet been applied. Don't update a newer or locally edited source.
ALLOWED_MAIN = {
    "1b5568d95fb7f9b079c736fb1f044e4efe58d61cf8cafe2488c9310bc06c2278",  # v13
    "19ef078d33882dd19fa1bdad71b35a58fb4f24e393e85ae3f2deeb7928b2cd40",  # v12
    "cfcaf2931ee9a30a59c81520f0144585db360b14ec464ec23bfa47c947000294",  # v12 favorites
}
ALLOWED_SERVICE = {
    "71c666059e3aae0a00e6c2d630a9947acb6259fc9fc268b3f4f4cd42b5f37e7c",  # v9..v13
}
EXPECTED_OUTPUT = {
    "MainActivity.kt": "ff2502fcb44a8a3297d9adb369db1fa13065c31fafd42f42fac78e1e4e1d27cd",
    "VideonicPlaybackService.kt": "a692ae9fe622fd1d6238ccd7ff0c1e3fe76576ef7eb296693a5f03eeafbb3c74",
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def stop(message):
    sys.exit("SAFE STOP: " + message + "; existing project files were not changed.")


for name, dest in PATHS.items():
    if not dest.is_file():
        stop(f"Missing {dest}; run this installer from the project root")
    if not (PACKAGE / name).is_file():
        stop(f"Archive missing {name}; unzip v14 again")

current = {name: path.read_bytes() for name, path in PATHS.items()}
updated = {name: (PACKAGE / name).read_bytes() for name in PATHS}
for name, data in updated.items():
    if digest(data) != EXPECTED_OUTPUT[name]:
        stop(f"v14 source integrity check failed for {name}")

if all(current[name] == updated[name] for name in PATHS):
    print("ALREADY APPLIED: Videonic Lite v14 Decoder + Player controls are installed.")
    raise SystemExit(0)

for name, valid in [("MainActivity.kt", ALLOWED_MAIN), ("VideonicPlaybackService.kt", ALLOWED_SERVICE)]:
    old_hash = digest(current[name])
    if old_hash not in valid and old_hash != EXPECTED_OUTPUT[name]:
        print(f"{name} SHA-256: {old_hash}")
        stop(f"Unexpected {name} version. Provide your current source for a tailored update")

backup_dir = ROOT / "videonic-ui-backups" / ("before-v14-decoder-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
try:
    backup_dir.mkdir(parents=True, exist_ok=False)
    for name, data in current.items():
        backup = backup_dir / name
        backup.write_bytes(data)
        if backup.read_bytes() != data:
            raise OSError(f"Backup verification failed for {name}")
except Exception as exc:
    stop(f"Could not create both backups: {exc}")

temps = {}
replaced = []
try:
    for name, target in PATHS.items():
        tmp = target.with_name(target.name + ".v14tmp")
        tmp.write_bytes(updated[name])
        if digest(tmp.read_bytes()) != EXPECTED_OUTPUT[name]:
            raise OSError(f"Temporary source integrity check failed for {name}")
        temps[name] = tmp
    for name, target in PATHS.items():
        os.replace(temps[name], target)
        replaced.append(name)
except Exception as exc:
    for name in replaced:
        PATHS[name].write_bytes(current[name])
    for tmp in temps.values():
        tmp.unlink(missing_ok=True)
    stop(f"Installation failed and original files restored: {exc}")

print("SUCCESS: Videonic Lite v14 decoder settings and player sheets installed.")
print("Updated: MainActivity.kt and VideonicPlaybackService.kt")
print("Safe backup folder:", backup_dir)
print("Next: Compile in Codespaces before building the APK.")
