# Videonic Player Lite v25 — Add to playlist multi-select fix

## Starting version
Apply **only on the exact v24 MKV Resume & Auto-Rotate Fix** (v24 must already be installed). This is an incremental Kotlin patch, not a standalone project/APK. The script safely refuses unknown/customized MainActivity.kt. Only `app/src/main/java/com/videonic/lite/MainActivity.kt` is replaced, with a timestamped backup and integrity verification.

## Problem in v24
From Videos → (three dots) → Add to playlist, tapping any listed playlist **immediately** added the video and closed the dialog. "Done" only closed the dialog, and unchecking existing membership was impossible. Creating a new playlist also immediately added and dismissed the picker.

## What changes in v25
- The three-dot sheet closes when you open **Add to playlist**, so it no longer stacks behind the selector.
- Existing playlists that already contain the chosen video start checked.
- Tap any playlist row or checkbox area to check/uncheck; stay in the selection dialog to pick multiple playlists.
- Tap **Done** once: add to all checked playlists, remove from unchecked previously-selected playlists; do not delete the original videos.
- Tap **Cancel**, Back, or dismiss: membership changes are discarded.
- **Create new playlist** opens the existing name dialog. After Create, you return to the selection dialog with the newly created playlist checked but video membership still pending until Done. Cancel in the name dialog returns to selection without making a new playlist.
- The independent "New playlist" path from the Playlists tab remains available.
- Nothing in MKV seek/resume, rotation, lock, speed gestures, layout styling outside of the picker, or playback service is changed.

*Note:* Creating the playlist itself is immediate. If you subsequently cancel the video selection, the new playlist still exists, empty.

## Install — GitHub Codespaces
Upload ZIP to Codespaces project root, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v25-playlist-selection-fix.zip -d .
python3 videonic-v25-playlist-selection-fix/APPLY.py
```

Build APK:

```bash
cd /workspaces/Videonic-Player-Lite
chmod +x ./gradlew
./gradlew :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Output: `app/build/outputs/apk/debug/app-debug.apk` after a successful Gradle build.

## Manual testing
1. Video with no playlists → open 3-dot menu → Add to playlist. Tap two playlist rows; neither should immediately close dialog. Press Done. Reopen; both checked.
2. Uncheck one of the two → Done. Reopen; that one unchecked; the other remains checked.
3. Tap another playlist → Cancel. Reopen; membership unchanged.
4. Create new playlist from picker. The picker reappears and its new name starts checked. Done adds video. From another video, Create and then cancel picker; playlist remains available but should be empty.
5. Confirm existing MKV playback/resume, fullscreen/auto-rotate/lock, favorites and share actions remain functional; they are unchanged by this patch.

## Validation
Patch content verified against exact v24 MainActivity source; diff inspected. Installer tested in an isolated simulated project for apply/backup/idempotence/wrong-version rejection. **Full Android Gradle compile and real-device runtime testing are not possible from the provided MainActivity-only patch ZIPs**. If the Gradle build fails, send the complete error output.
