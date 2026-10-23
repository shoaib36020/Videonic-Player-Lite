# Videonic v36 — cleaner splash typography + faster hold-speed scrubbing

## What changed

- **Native splash typography only:** existing original Videonic blue play icon, pure white background, `Videonic` in a cleaner, medium-weight sans-serif title, with a larger, clearer `Lite Video Player` caption. No animations or extra splash screen.
- **Hold-speed sensitivity:** once press-and-hold activates, each **18dp horizontal swipe** advances one existing speed step, right to faster and left to slower. Previously each step needed 10% of video width; on a wide device that felt excessively slow. For example, from 2× to 4× takes about 54dp rather than roughly 30% of screen width.
- Existing speeds, the long-press activation delay, up-to-lock gesture, 3-second locked-speed chip, unified speed menu, and reset-to-1× behavior are unchanged.

## Requirements

Videonic project with the v33 UI source and v35 splash update already installed. This patch is **incremental** (not a standalone project or APK). It refuses mismatched source files. No app version, JKS, SDK or R8 configuration changes.

## Install in GitHub Codespaces

Upload `videonic-v36-splash-speed.zip` to `/workspaces/Videonic-Player-Lite`, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v36-splash-speed.zip -d .
python3 videonic-v36-splash-speed/APPLY.py
```

## Build test APK

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Test on device

- Splash: one centered icon, balanced title/subtitle, no clipping.
- Press and hold playback to begin quick speed; short horizontal swipes right/left change speed. Hold and swipe up to lock; locked indicator hides after three seconds; tapping Reset returns to 1×.
- Speed icon remains synchronized and MKV resume, rotation, playlists, Recents and other features still behave correctly.

If install says `SAFE STOP`, **do not force** the patch. Provide the displayed error and current `MainActivity.kt` hash. This package does not claim an Android Gradle build has run.
