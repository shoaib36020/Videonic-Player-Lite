# Videonic Lite v16.7 — Decoder video output, reopening and MKV stability

This patch changes only `MainActivity.kt` and `VideonicPlaybackService.kt` from the **exact v16.6 source**.

## Repairs

1. **Decoder changes causing audio-only/black video:** Media3 keeps the same `MediaController` while `MediaSession.setPlayer()` swaps the underlying `ExoPlayer`. In v16.6 the `PlayerView` retained its old TextureView binding. This patch rebuilds the video surface after both successful and failed decoder switches, and rejects a replacement if it unexpectedly loses the selected video track.
2. **Stretching on mini-player reopen:** The expanded `PlayerView` is recreated for every re-entry/rotation, and explicitly detaches its controller when removed. New surfaces start in Original (Fit) instead of keeping the previous texture state.
3. **Seeking freezes:** Remove the sequential platform-parser test and warning banners. Built-in seeking uses the usual swipe, +/-10s, and timeline interactions. If Media3 says an MKV is unseekable, a **single** optional background cluster-index scan is attempted. It leaves the current playing video running while preparing a candidate. Failed recovery returns silently without stopping the working player. A candidate rebuild is limited to 6 seconds, decoder changes to 12 seconds (instead of 35 seconds).

## Limits

- This patch does not turn damaged or missing MKV cues into guaranteed seekability. Some Matroska Cluster indexes can still fail or produce approximate seeks. For a guaranteed file-specific fix, a short sample of the failing MKV (or its metadata and a reproducible bug report) is needed.
- No additional native library, file deletion, or external-player action is added. The resulting APK still needs a size check against the 30 MB cap.
- This patch has **not** been compiled with the Android SDK or tested with your actual videos. Run compile before building APK.

## Apply

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-7-surface-seek-stability.zip -d .
python3 videonic-v16-7-surface-seek-stability/APPLY.py
```

## Compile

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

If it fails, run the same command redirecting to `build-error.log` and send the `e:` lines. Do not uninstall your installed APK.
