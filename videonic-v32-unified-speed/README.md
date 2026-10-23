# Videonic Player Lite — v32 Unified Speed Fix

## What changes

Only `app/src/main/java/com/videonic/lite/MainActivity.kt` is replaced with an exact-version, guarded change:

- Selecting a speed in the speed menu changes the same effective speed used by press-and-hold.
- Press-and-hold boosts speed only while held; the speed indicator also shows the effective rate.
- Release **without locking** returns to the rate used immediately before the gesture (e.g. 3.0x).
- Swipe up to **lock** a boost (e.g. 2.0x). The speed menu and actual player now show 2.0x.
- Tap the video to reveal the disappearing lock chip, then tap **Reset to 1x**: it clears the lock and returns **both the icon and playback** to **1.0x**. Reset no longer restores an earlier custom rate like 3.0x.
- Choosing a speed from the menu while a gesture speed is locked replaces the lock and applies that selection everywhere.
- The UI listens to Media3 playback speed updates to stay accurate when a controller reconnects.

**Note:** A video's initial speed still uses your app's configured Default speed setting. Reset to 1.0x applies to the current playback, not the global Default speed preference.

No resources, splash screen, playback service, rotation, playlist, or MKV code are changed.

## Apply over v31 (also accepts v30 MainActivity source)

Upload the `videonic-v32-unified-speed.zip` archive to the Codespaces project's root and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v32-unified-speed.zip -d .
python3 videonic-v32-unified-speed/APPLY.py
```

Run it more than once safely: it will print `ALREADY APPLIED` after the first success. The installer checks your existing source SHA-256, verifies a backup, and stops safely if the file differs. If you see `SAFE STOP`, do not force it; share the output.

## Build debug APK

Your Codespaces repository uses the system `gradle` command rather than a `./gradlew` wrapper:

```bash
gradle :app:assembleDebug \
  --no-daemon \
  --max-workers=1 \
  --no-parallel \
  --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Device tests

1. Set menu speed 3.0x, long-press and release without locking: return to 3.0x and show 3.0x.
2. Set menu speed 3.0x, long-press, swipe up to lock 2.0x: menu and playback show 2.0x. Tap video and tap the lock chip's Reset: both return to 1.0x.
3. Long-press and lock 2.0x, then choose 3.0x in the menu: playback and icon become 3.0x and the gesture lock is cleared.
4. Start a new video and verify it applies the configured Default speed.

### Verification scope

Patch SHA verification and installer behavior can be checked without the rest of the Android project. Full Gradle compilation and physical device playback require running the commands in your Codespaces project.
