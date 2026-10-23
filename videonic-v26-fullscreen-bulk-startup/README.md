# Videonic v26 — landscape switching, bulk collections, faster startup

**Apply over exact v25**. This is an incremental update, not a complete Android app.

## Changes
- **Manual fullscreen:** Uses Android `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`. Both phone landscape directions work even if global Auto-rotate is OFF; portrait is excluded. Lock still freezes rotation; Exit Fullscreen returns portrait.
- **Bulk Add to playlist:** Same select-then-Done interaction as one video. Existing playlists are checked, unchecked, or show a `—` for mixed membership; untouched mixed memberships remain unchanged. Check adds all selected videos to that playlist; uncheck removes all; Cancel/Back discards selections. Create new playlist returns to bulk picker, prechecked; no video added until Done.
- **Bulk Favorite:** Confirmation toast states how many previously-unfavorited videos were added, or shows Already in Favorites. Avoids an intrusive confirmation dialog.
- **Startup:** Native launch icon and white theme background are added only when the installer can safely detect the existing main activity theme. Removes the old arbitrary 620ms branding wait (now 220ms). Introduces 18MB bounded thumbnail cache for more fluid repeat scrolling; no new media scanning or networking.

## Install
Upload this ZIP to your Codespaces project root and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v26-fullscreen-bulk-startup.zip -d .
python3 videonic-v26-fullscreen-bulk-startup/APPLY.py
```

**Build (your Codespaces has system Gradle, not `./gradlew`):**

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Testing
1. Manually fullscreen while OS auto-rotate is OFF, rotate to left landscape and right landscape; portrait must not be entered.
2. Enable playback lock, rotate phone; current angle must stay frozen. Unlock, and rotate again.
3. Select 2+ videos → Playlist → toggle several checkboxes. Nothing saves until Done. Open again, uncheck, press Done and verify memberships.
4. For mixed membership (`—`), press Done without editing that playlist and verify existing membership is untouched. Tap row once to add all, twice to remove all.
5. Create playlist inside bulk picker; return, verify checked, press Done.
6. Select videos → Favorite; a confirmation toast should appear and Favorites should show the videos.
7. Cold-launch the app with the device's system Light and Dark mode; branded icon should display before the app, without a blank flash. Scroll a long library twice; the second pass should reuse thumbnails.

## Important limitations
- The script only modifies `AndroidManifest.xml` and creates Android launch resources if it can safely find MainActivity and its parent theme. If unsafe, it skips those resources and says so explicitly; other fixes still apply.
- Thumb caching reduces repeat decoding, but does not speed the initial Android MediaStore scan, which remains dependent on the device.
- Static checks and installer simulation do **not** prove Android compilation or behavior on your phone. Run Gradle and test on-device.
- Backups are kept in `videonic-ui-backups/` under your project. A repeat install recognizes v26 and exits.
