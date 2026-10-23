# Videonic Lite v16.5 — Optional in-app seek recovery

## What the screenshots establish
The current Media3 player reports `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` unavailable for this particular file. The timeline, swipe gesture, double-tap, and forward/back buttons all respect that capability. Merely removing the capability check would not create a valid seek map.

## Targeted change
- Adds **Try alternate seeking** under the expanded player's three-dot menu **only when seeking is unavailable** (Android 11 / API 30+).
- Attempts Android platform MediaParser instead of Media3's default container extractor. This is an alternative **parser**, not a new video codec or an external video app. It may be able to read seek points the default parser misses.
- Requires the alternative parser to report current-media seeking available **before** it replaces the working session. Otherwise rolls back to the original Media3 session and playback position.
- When the alternative parser is in use, shows **Use standard seek engine** to restore the original extractor.
- Replaces overconfident seek-index errors with parser-specific explanations; tapping unavailable skip buttons now shows a useful on-screen recovery hint instead of appearing inert.
- No native library added. Does not change app permissions or format/decoder dependencies.

## Limitations
This **cannot manufacture an index missing from the file**. On some non-standard MKV/MP4 files both parsers can be unable to seek; the file will need its container's seek metadata regenerated. Platform MediaParser requires Android 11+. MediaParser may not support every container, codec, subtitle track, or corner case; original video remains unchanged. Do not claim success until tested with your problem file. Original playback is expected to resume if the parser fails.

## Install
1. Upload ZIP to `/workspaces/Videonic-Player-Lite` in GitHub Codespaces.
2. Run:
```
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-5-seek-recovery.zip -d .
python3 videonic-v16-5-seek-recovery/APPLY.py
```
3. Compile:
```
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```
4. Only after successful compilation, build APK with the same options using `:app:assembleDebug`.

## Live test
Open the problematic movie → three-dot menu → **Try alternate seeking** → wait. If it succeeds, seek to a nearby time first, then to the midpoint. If it restores original playback, please provide the **full filename including .mkv/.mp4 extension** and Video Details screen; those determine the next viable repair. Also test a normally-seekable MP4 and track/subtitle menus for regressions.

**Safety**: Installer compares exact v16.4 SHA256s, creates verified backups, fails on modified sources, and is idempotent. The Android SDK and phone playback are not available here; source compile and live behavior are unverified.
