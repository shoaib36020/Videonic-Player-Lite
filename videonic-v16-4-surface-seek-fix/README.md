# Videonic Lite v16.4 — mini-player surface and seek improvements

Target: your **successful v16.3** compilation, applied to the unmodified v16.3 source.

## Fixes

- Removes the mini-player's **second live PlayerView** which was attached to the same media session, with a forced Zoom resize mode. The mini bar now displays `VideoThumb` only. Video/audio playback continues; only the visual preview switches to a thumbnail.
- Each return from the mini bar creates a clean full-screen PlayerView, initialized as **Original/Fit**. It cannot inherit the mini-player's Zoom surface.
- Uses Media3 `SeekParameters.CLOSEST_SYNC`, which tries the nearest keyframe and may improve performance on long/sparse-GOP video. It sacrifices some precision. It cannot make a video with **no usable seek index** seekable.

## Apply

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-4-surface-seek-fix.zip -d .
python3 videonic-v16-4-surface-seek-fix/APPLY.py
```

## Compile

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Build and size check (after compile succeeds)

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
stat -c "%s bytes" app/build/outputs/apk/debug/app-debug.apk
```

The native dependency list is unchanged, so there should not be a major size increase. The final APK size needs to be measured in your Codespace.

## Test sequence

1. Play a video, turn to landscape, go Back to Videos, return to portrait, and tap the mini-player.
2. Check the image is undistorted and the aspect label is Original.
3. Test seek with a standard seekable MP4. Then test the exact HDTC file; record whether the seek slider is disabled, snaps back, or freezes. These outcomes have different root causes.
4. If the HDTC file still does not seek, share its **container, codec, duration** and whether another local Android video player can seek that same file. HDTC is a source label, not a file codec or indexing format. We cannot promise seeking for a file without an index.

No changes to private-folder data, player library, file deletion, or external playback. Android compilation and device behavior have not been tested here.
