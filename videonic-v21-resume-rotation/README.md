# Videonic Player v21 — resume, rotation and compact hold-speed

**Requires:** the successful v20 code from `videonic-v20-player-polish.zip`. The installer checks the actual SHA-256 of `MainActivity.kt` and backs it up before replacement. It will safely stop if that file was edited after v20.

## What is changed

- **Resume**: opening the currently playing video from the library or Recent reattaches the current session instead of resetting its queue. Playback position is saved from the actual Media3 controller when pausing, going Back, closing the mini-player, or the Activity moves to the background. The periodic position persistence is limited to roughly every 2 seconds and only saves a position for its matching video ID.
- **Resume after app restart**: an explicit Resume request uses the saved position when creating the media queue, then verifies it once when the Media3 timeline becomes ready (only if that file is actually seekable). It does not keep retrying and cannot seek unexpectedly many seconds later.
- **Fullscreen/rotation**: tapping Fullscreen initiates landscape; if the app's Auto-rotate option is enabled, the temporary forced landscape lock is cleared once Android has rotated, so subsequent phone rotation can work. An explicit Exit Fullscreen continues to hold portrait to avoid immediate landscape bounce-back.
- **Hold-speed**: the overlay is now a tiny centered dark pill (up to 230dp wide), rather than a wide chart. It displays the selected speed and small tick marks; swipe sideways to adjust; swipe up to lock; tap the lock pill to unlock. The gesture itself is unchanged.
- **Smoothness**: less frequent UI polling and SharedPreferences writes during ordinary playback.

**Limitations:** an MKV without a usable seek index may still be unable to resume at an exact position; this patch does not add a decoder or change the MKV seek-recovery service. The actual device must be tested. No source files other than MainActivity are modified, no signing/brand/data changes, no new dependencies, and the APK target remains 30 MB.

## Install

Upload `videonic-v21-resume-rotation.zip` to the Codespaces project root, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v21-resume-rotation.zip -d .
python3 videonic-v21-resume-rotation/APPLY.py
```

Compile first:

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Only after **BUILD SUCCESSFUL**, assemble the APK by replacing `compileDebugKotlin` with `assembleDebug`.

## Phone checks

1. Open a normal seekable video, play at least 25 seconds, press Back to the mini-player, tap the video again. Verify position was not reset.
2. Exit the app normally; relaunch, tap that video and choose **Resume**. Verify the app starts near the saved time (not 00:00).
3. Tap Fullscreen in portrait with app **Auto-rotate** enabled, then rotate the phone back upright. Verify the player follows orientation. Tap Fullscreen/Exit again to confirm the button still works.
4. Long-press the picture: the centered speed control should be much smaller. Drag sideways to change speed; swipe up to lock; tap lock chip to unlock.
5. Check representative MKV and MP4 playback, PiP, and APK size before considering an AAB.
