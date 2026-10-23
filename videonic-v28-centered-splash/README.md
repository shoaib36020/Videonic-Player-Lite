# Videonic Player Lite v28 — Name directly below splash icon

This is a **resource-only patch over installed v27**. No Kotlin code, player settings, rotation, library, playlist, MKV resume, or gestures are changed.

## Why v27 showed the name at the bottom
Android 12 and newer position `android:windowSplashScreenBrandingImage` at the **bottom** of the system splash. That resource cannot be moved immediately below the icon with a positioning option.

## What v28 does
- Removes `windowSplashScreenBrandingImage` from the Android 12+ launch theme.
- Creates a centered composite launch icon that uses your **existing Videonic blue icon** plus **Videonic Player** text directly beneath it.
- Keeps exactly one native Android launch splash. There is no second Compose splash or artificial splash delay.
- Keeps the previous launch treatment on Android 11 and older (where v27 already places the label near the icon).

**Android limitation:** Android masks the system splash icon to a central circular area. The icon + name are grouped within that protected region, so they are more compact than the custom v26 Compose illustration. The exact placement/scale must be verified on your phone after building.

## Install

Upload `videonic-v28-centered-splash.zip` to your Codespaces project root and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v28-centered-splash.zip -d .
python3 videonic-v28-centered-splash/APPLY.py
```

The script checks that v27 source and splash theme match expected versions, verifies its own files, makes a timestamped backup of modified resources, and safely stops on unknown/modified files. Re-running is safe.

## Build debug APK

```bash
cd /workspaces/Videonic-Player-Lite
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Verification
- Installer tested on synthetic project fixtures for **both supported v27 launch theme variants**.
- Backups and repeated installations verified; unsupported edits cause a safe stop.
- XML resources parsed successfully.
- **No complete Android project is available here**, so Gradle/AAPT compilation and on-device positioning **have not** been verified. Test the debug APK before any AAB release.
