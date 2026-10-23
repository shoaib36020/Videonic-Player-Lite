# Videonic Player Lite v24 — MKV resume protection + reliable rotation

## Compatibility

**Requires successfully applied v23.** This ZIP is an incremental MainActivity.kt replacement, NOT a complete Android project or APK. The installer checks the *exact* v23 source hash and creates a verified backup before applying v24; custom changes safely stop installation. Only `app/src/main/java/com/videonic/lite/MainActivity.kt` is modified. v23's smaller 19dp lock icon, 3-second hide/reveal, v22.1 compile padding fix, five-second resume prompt, and compact hold-speed are retained.

## Fixes

1. **MKV startup resume**: after Media3 becomes READY, if the source reports unseekable, a single bounded attempt invokes the project's existing `ACTION_BUILD_MKV_SEEK_INDEX` service recovery, which previously only ran on manual seeks. If a seemingly seekable MKV still jumps near 0:00, one early verification can trigger the same recovery. It does not endlessly retry or interrupt the current playback with a persistent loop.
2. **Preserve bookmark**: an MKV whose resume attempt fails will NOT overwrite the saved position with its fallback 0:00 clock from timers, pauses, Back, close-player or Activity.onStop. Successful playback near the saved position unlocks ordinary position saving again. Intentional manual seek or 'Start from Beginning' clears the protection.
3. **Resume action accurately timed**: 'Start from Beginning' appears for 5 seconds only AFTER resumed playback is actually near the saved timestamp. It never falsely appears simply because an unseekable MKV started from 0:00. This waits up to 20 seconds for a slow recovery.
4. **In-app Auto rotate ON**: uses the device physical orientation sensor (`SCREEN_ORIENTATION_SENSOR`) while video is expanded, including if Android's global rotation toggle is off. **In-app Auto rotate OFF**: holds the present screen angle rather than still rotating due to Android's settings.
5. **Manual Fullscreen**: button forces and retains landscape even when both Android and Videonic Auto rotate are OFF. Exit Fullscreen goes to portrait; with in-app Auto rotate ON, it resumes sensor-following when the phone is physically brought upright (avoids immediate sideways bounce-back).
6. **Lock**: freezes the exact displayed orientation no matter the auto-rotate settings. Smaller, self-hiding unlock control remains unchanged. Unlock returns to the previous manual/fullscreen/auto-rotate rule.
7. **One orientation owner**: pressing Fullscreen or changing settings no longer independently overrides the orientation controller.

## Install in GitHub Codespaces

Upload `videonic-v24-mkv-resume-auto-rotate.zip` to the root of your project, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v24-mkv-resume-auto-rotate.zip -d .
python3 videonic-v24-mkv-resume-auto-rotate/APPLY.py
```

Compile and build (use Gradle wrapper if available):

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Debug APK path after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

## Phone test matrix

- App Auto rotate ON, Android global Auto-rotate OFF: while unlocked and not manually fullscreen, moving the phone should rotate the video. Press Lock; rotate the phone: no change. Wait 3 seconds: unlock icon hidden; tap image: icon shows; tap to unlock: sensor follows again.
- App Auto rotate OFF, global ON: natural phone turns should **not** rotate the playback screen. Press Fullscreen: force landscape; remain there despite phone turns. Exit: portrait. Lock in each orientation and check freezing.
- App Auto rotate ON, manual Fullscreen: force landscape and keep it there even if upright; Exit returns portrait. Bring phone upright to re-enable automatic rotation; turning sideways again follows sensor.
- Play common seekable MP4, seekable MKV, and a previously unseekable MKV long enough to create a bookmark. Close/reopen each, verify the restored frame and timer; for a stubborn MKV inspect Logcat tag `VideonicTimeline`. Test Background, Back, mini-player, Start from Beginning, and subsequent resume.
- Rebuild APK and test on-device; static inspection and installer checks cannot prove Android runtime behavior.

## Limitations

Some MKVs are malformed or lack usable cues/index entries; the existing playback service cannot necessarily reconstruct all of them. This patch *uses but does not modify* the service-side index recovery logic, since VideonicPlaybackService.kt was not supplied. There is **no guarantee that every unseekable MKV can be resumed**. A previously overwritten bookmark cannot be reconstructed. If failures continue after testing v24, provide the full `VideonicPlaybackService.kt` plus an example MKV's MediaInfo or relevant `VideonicTimeline` logs to fix its extractor/indexing path accurately. No dummy success messages are produced.
