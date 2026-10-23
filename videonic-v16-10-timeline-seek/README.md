# Videonic Lite v16.10 — Startup and Seeking Reliability

Requires **exact v16.9** sources from `videonic-v16-9-mini-seek-tracks.zip` and applies to your existing Codespaces project. This is a targeted patch, **not** a full application ZIP.

## Changes

- Normalizes fresh playback to Media3's natural default start position (`C.TIME_UNSET`) instead of seeking to 0 before a new MKV timeline exists.
- Queues a seek requested during the initial loading/buffering state and applies the most recent target when Media3 reaches READY. This prevents premature unseekable-file detection.
- Coalesces repeated seek-button presses/slider gestures rather than restarting and canceling the MKV recovery scanner repeatedly.
- Keeps the slider's requested position temporarily while a seek is in flight, but saves only the actual confirmed position to Recent/history.
- Allows later recovery attempts following a cooldown, instead of permanently rejecting further seeks for the same MKV after one failure.
- Prevents a late index trial from replacing a playing session after it has advanced more than six seconds from the captured position.
- Permits the recovered MKV index when a local content provider reports **unknown input length**, instead of silently disabling it.
- Changes the MKV scanner to positional `FileChannel.read(buffer, offset)`, which does not change a potentially shared file descriptor's read cursor while playback proceeds.
- Starts background index preparation only once the video has reached READY and the source has been open for 4.5 seconds, rather than during initial buffering.
- Adds `VideonicTimeline` diagnostics to Android Logcat **without filenames**, making it possible to distinguish deliberate seeks from source timestamp jumps if they persist.

**Not changed:** navigation, data storage, saved media, private folder, codec libraries, video renderer, or UI design. No new native dependency or external-player link.

## Apply

Upload `videonic-v16-10-timeline-seek.zip` into the **project root**, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-10-timeline-seek.zip -d .
python3 videonic-v16-10-timeline-seek/APPLY.py
```

If the script says `SAFE STOP`, don't force it. Share the hashes printed by the script.

## Compile first

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

## Test after a successful APK build

1. Open an affected MKV **from the beginning** and let it play for 30 seconds without touching playback controls. Look for jumps in the time and picture.
2. Close and reopen the same video, test the **first** seek before and after the player reaches READY.
3. Tap forward by 10s repeatedly and drag the progress slider to a distant time. Verify the seek is actually applied, not merely the slider moving.
4. Test swipe-seeking, then mini-player and decoder switching again.

### Important limitation

No patch can guarantee random access in an MKV with truly missing or damaged seek information, nor can changing the time display reconstruct missing video frames. This patch addresses **app-originated state/timing problems**, and helps us distinguish those from genuine file timestamp/index problems. Android/Media3 compilation and on-device behavior are not tested in this package.

If unexpected jumps remain and you can use Android Debug Bridge on a Windows PC with USB debugging enabled, capture the diagnostic trace **after reproducing once**:

```bash
adb logcat -d -s VideonicTimeline:I > videonic-timeline.txt
```

Only submit that diagnostic file if you are comfortable sharing playback timestamps; the log intentionally omits video titles.
