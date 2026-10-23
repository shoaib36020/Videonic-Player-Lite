# Videonic Player v20 — Folder video tools + player gestures

Requires the **v18 or v19.1 source** already installed and compiling in GitHub Codespaces. v19 and v19.1 changed Android branding resources/manifest, not `MainActivity.kt`, so the guarded installer accepts its exact v18 MainActivity hash. This patch **only changes MainActivity.kt**; it does not touch AndroidManifest.xml, launcher assets, player service, Private Folder, signing, or library data.

## Changes

1. **Center double-tap** toggles Play/Pause. Left and right double-tap seeking remains controlled by the existing double-tap seek preference. Double-taps on the visible center Play button are also handled without toggling twice.
2. **Inside a folder**, the video header includes **Search**, **Sort** and a **Grid/List** toggle. The toggle uses the Folders preference rather than overwriting the Videos page preference; sorting still uses the existing video sorting sheet.
3. **Hold for speed** is a compact, horizontally centered overlay, with playback controls hidden while holding. Move sideways to choose speed.
4. **Speed lock:** While holding for speed, **swipe upward** to lock the chosen speed; it stays active when the finger lifts. A compact **locked speed / Tap to unlock** pill appears above the center, and tapping it restores the speed from before the hold. Normal quick hold/release still restores the original speed automatically. Selecting a speed manually in the existing speed sheet also clears the lock. The lock is cleared when a new video is opened or the player is closed.
5. **Resolution labels** now classify cinematic video frames by their longer stored dimension, so a 1920×800 file can display **1080p** rather than **720p**. The colors use the same classification logic. If Android's metadata is wrong, a badge can still be wrong; don't treat the label as proof of a codec's rendered output.

## Install

Upload `videonic-v20-player-polish.zip` to the root of your Codespace, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v20-player-polish.zip -d .
python3 videonic-v20-player-polish/APPLY.py
```

The installer creates a backup under `videonic-ui-backups/before-v20-*` and safely stops if the current `MainActivity.kt` is not the expected version. Do **not** override a SAFE STOP.

## Compile

```bash
gradle :app:compileDebugKotlin \
--no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
-Pkotlin.compiler.execution.strategy=in-process
```

After a successful compile, build the APK with `:app:assembleDebug` using the same arguments, then verify the result remains under **30 MB**. Do not move to the Google Play AAB until this APK passes your phone tests.

## Device test checklist

- Double-tap the **center of the picture** while controls are hidden, then while shown. Confirm it toggles Play/Pause **once**. Verify left/right double-tap still seeks as configured.
- Open a folder with videos. Use Search, Sort, and switch between Grid/List. Confirm the main Videos layout setting has not changed.
- Hold a finger on the video until the compact speed bar appears. Verify controls disappear and the selected speed changes with a horizontal drag. Release normally: speed restores.
- Hold again; swipe **upward** to lock. Confirm the pinned-speed pill appears. Tap it to return to your prior speed. Open a different video to confirm the lock clears.
- Check the badge on the previously mislabelled widescreen 1080p video; compare to **Video Details → Resolution**, not its filename.

## Verification status

Verified installer apply, idempotence, backups, and source version conflict behavior against the exact v18/v19.1 MainActivity. A Kotlin standalone parser check found no syntax errors, but **full Compose/Android compilation and device behavior must be tested in Codespaces**. No new dependencies are added.
