# Videonic Lite v16.3 — Portrait Player & Seeking Fix

A minimal two-file update for **the distributed v16.2 internal-only version**. Keeps your 30 MB APK requirement and does not add external-player actions or native codec libraries.

## Fixed

- **Reopen after landscape rotation:** On every fresh expanded-player entry, video aspect selection resets to Original / Fit (rather than retaining Width / Stretch / Crop from the last session). The on-screen frame immediately starts as Fit, retaining the video’s actual proportions.
- **Rotate back to portrait:** Reset to Original / Fit when the phone’s real orientation changes; a manually selected alternative ratio remains in effect until the next rotation or player reopening.
- **Return from mini player / Back / notification:** The player’s temporary aspect and scrub state is cleared appropriately, without stopping the current media session simply to change layouts.
- **Approximate seek for some CBR files:** Enables Media3 `DefaultExtractorsFactory.setConstantBitrateSeekingEnabled(true)` in the existing player service. This only affects extractors that support approximate byte seeking, such as some transport/audio streams; files lacking required seek indexes can remain unseekable.
- **Seek feedback:** Explicitly indicates a missing/unusable index instead of incorrectly treating it as a decoder selection problem.

**Important:** `HDTC` and `Blu-ray` describe video sources, not codecs or seek indexes. This patch does NOT guarantee every such file becomes seekable and does not add missing 10-bit codecs. For a still-unseekable file, inspect codec, container, and whether the file was indexed/remuxed.

## Install

Upload `videonic-v16-3-rotation-seeking.zip` to the Codespaces root then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-3-rotation-seeking.zip -d .
python3 videonic-v16-3-rotation-seeking/APPLY.py
```

If it says `SAFE STOP`, do not force it. Send the current files so the patch can be adapted. Backup is made automatically.

## Compile

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

After **BUILD SUCCESSFUL**, build `:app:assembleDebug` using the same flags and check size with `bash videonic-v16-2-internal-only/CHECK_SIZE.sh` (or `stat -c%s app/build/outputs/apk/debug/app-debug.apk`).

## Device tests

1. Select Width on a video. Rotate to landscape. Press Back to the Videos page. Tap mini-player to reopen in portrait: label and video should return to Original / Fit.
2. Repeat without changing aspect manually and with auto-rotation toggled on/off.
3. Try seeking with the problematic file in the timeline, through horizontal gestures, and using +/-10s. If the player still says no usable seek index, the file may need remuxing; this release does not invent seek support.
4. Confirm regular formats, PiP, notification resume, headset playback and four aspect-ratio cycle choices still behave as before.

No app uninstall is required. Android compilation, APK size, and real device seek behavior must be verified in Codespaces/on device; these are not claimed tested here.
