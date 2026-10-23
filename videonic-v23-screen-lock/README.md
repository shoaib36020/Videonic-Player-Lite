# Videonic Player Lite v23 — frozen rotation + disappearing lock control

## Compatibility

**Install this over v22 OR v22.1.** It requires the existing project with `app/src/main/java/com/videonic/lite/MainActivity.kt` at exactly one of these base versions. It is NOT a complete Android Studio project or a prebuilt APK. It includes the **v22.1 Kotlin padding fix**, so installing v22.1 separately first is not necessary if you are already on v22. The `APPLY.py` script SHA-checks the previous source, saves a verified backup, and changes **only MainActivity.kt**. Any custom edits cause a safe stop rather than an overwrite.

## Changes

1. **Orientation freezes when player Lock is enabled:** `ActivityInfo.SCREEN_ORIENTATION_LOCKED` preserves the exact current screen rotation, irrespective of the app or phone's Auto-rotate state. On Unlock, the prior rotation policy returns: manual Fullscreen remains landscape until exited; otherwise the app option respects Android's user rotation setting.
2. **Smaller unlock icon:** visual circle reduced from **62dp to 38dp**, glyph from **30dp to 19dp**, but keeps a **48dp touch target**.
3. **Auto-hide:** unlock icon appears as soon as Lock is pressed, then disappears after **3 seconds**. Tapping anywhere on the locked video reveals it for another 3 seconds. Tapping the icon unlocks, showing playback controls. The old permanent `Screen locked` banner / `Tap to unlock` label are removed.
4. **Lock really blocks interactions:** While locked, tapping the image doesn't pause or resume, double-tap seek, adjust volume/brightness, or trigger hold-speed. Speed-locked controls and the resume-action button are hidden while the screen is locked; playback itself continues normally.
5. **Prior v22.1 compiler fix retained:** Invalid `.padding(horizontal=...,bottom=...)` replaced with `.padding(start=...,end=...,bottom=...)`.

No changes to the player service, codec handling, stored positions, private folder, app brand, Gradle files, or signing.

## Install from GitHub Codespaces terminal

Upload `videonic-v23-screen-lock.zip` to your project root:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v23-screen-lock.zip -d .
python3 videonic-v23-screen-lock/APPLY.py
```

Compile (this environment only has incremental source ZIPs and cannot build the full app):

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Only after **BUILD SUCCESSFUL**, build an APK:

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Device checks

1. Turn system Auto-rotate **ON**, open video in portrait, press Lock. Turn the phone in every direction; screen should NOT rotate. Wait ~3 seconds; unlock icon disappears. Tap empty video area; small unlock icon reappears. Tap it; auto-rotation resumes according to app/system settings.
2. Turn system Auto-rotate **OFF**. Enter manual Fullscreen, then Lock. Verify landscape stays locked, tap to show/unlock, then Exit Fullscreen returns portrait.
3. With Lock enabled, verify taps/long-press/double-tap/swipes do not change playback, seek, volume, brightness, or speed; playback keeps running normally.
4. Verify Back unlocks first, then the next Back leaves the expanded player. Reopen video and confirm resume/5-second action; test mini-player, hold-speed gesture, MKV/MP4 seeking.
5. Check that the build completes with no compiler errors and test the APK on an actual Android device. A ZIP integrity/static source check is not equivalent to Kotlin compilation.
