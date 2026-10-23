# Videonic Player Lite v27 — single branded splash + speed-lock guidance

**Apply over the exact v26 source.** This is an incremental Android project patch, not a standalone APK or AAB.

## Changes

1. **One splash instead of two:** Removes the second 220 ms Compose splash and enters the actual app immediately after the native Android splash. The native startup uses the existing blue Videonic icon with a branded name image. On Android 12+, the OS places the app-name branding **near the bottom of the system splash**, not directly under the centered icon; this position is controlled by Android. The extra white/brand splash screen is gone. The installer updates the existing V26 launch theme or safely creates a native V27 theme if V26 skipped theme setup.
2. **Long-press speed help:** Keeps the compact 174dp speed bar and adds a small message directly **below** it: **Swipe up to lock • Release to reset**. This only appears during an active speed hold, and is removed on release or lock. Existing speed lock/unlock and gestures are preserved.
3. No other playlist, favorite, rotation, MKV resume, or library behavior changed.

## Apply patch in Codespaces

Upload ZIP into `/workspaces/Videonic-Player-Lite/`, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v27-splash-speed-aab.zip -d .
python3 videonic-v27-splash-speed-aab/APPLY.py
```

The installer creates timestamped, verified backups under `videonic-ui-backups/`, rejects unknown MainActivity source versions, and is safe to rerun.

## Test APK first

Your Codespaces has system Gradle rather than a `./gradlew` file:

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

On-device checks: launch from a cold start (only the system-branded splash, then Videos screen); long-press video to see label under the bar; swipe up to lock and tap to unlock; verify MKV resume, both landscape orientations, Lock, playlists, Favorites.

## Prepare Android App Bundle (AAB) for Google Play

**Do not treat a debug APK or unsigned AAB as Play-ready.** Before releasing:

- Confirm the `app` module's `release` build type is signed by an **upload key** (keystore stored securely outside Git/repository) and is not debuggable.
- Increment `versionCode` and set an appropriate `versionName` for the release; keep the same package/application ID for updates.
- Retest and resolve any target-SDK, permissions, privacy/data-safety, content, or Play Console requirements.

Then run:

```bash
cd /workspaces/Videonic-Player-Lite
gradle :app:bundleRelease \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
ls -lh app/build/outputs/bundle/release/
```

Expected filename: `app/build/outputs/bundle/release/app-release.aab` (but whether it is **signed** depends on your Gradle release signing setup). An uploaded Play AAB must have proper signing and release metadata. If release signing is not configured, provide `app/build.gradle` or `app/build.gradle.kts` (with any secrets removed) before uploading to Play.

## Limitations

- Patch installer tested against synthetic v26 and original-theme project fixtures; XML validation is not a substitute for Android AAPT/Gradle compilation.
- No real device or complete project build was available in this environment; test APK before uploading a signed release.
- On Android 12+, the mandatory system splash has OS-controlled layout and timing. The name's footer position is not a second splash.
