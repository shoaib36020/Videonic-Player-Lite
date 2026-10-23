# Videonic Player Lite v29 — Pull to refresh

**An incremental update over v28, not a standalone Android project.** All v28 splash resources stay untouched. This patch replaces only `app/src/main/java/com/videonic/lite/MainActivity.kt` after verifying the exact prior version.

## What's new

- In **Videos**, **Folders**, and individual folder contents, swipe downward while at the *top* of the scrollable list or grid to refresh.
- A small blue loading spinner appears briefly. There is **no new fixed refresh icon or button**.
- The refresh reruns `VideoRepository(context).load()` on `Dispatchers.IO` and updates the full list, video counts, folders, and folders' contents based on Android **MediaStore**. No unnecessary thumbnail recoding or extra full-file walk.
- Existing videos remain on screen during a manual refresh; refreshes do not reopen the full-screen “Scanning videos...” view.
- A stale in-progress scan is canceled when another rescan request arrives, so an older scan cannot override the newest results.
- MediaStore query errors do not wipe the previous list; a brief message is displayed instead.
- The gesture is only active on library pages (not the video player, Me, playlist, or app settings). A normal upward scroll is unchanged.

Note: MediaStore can show files **only after Android has indexed them**. Pulling refresh cannot force other apps or unindexed folders to become indexed by Android.

## Apply over v28

Upload `videonic-v29-pull-to-refresh.zip` into your Codespaces project root:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v29-pull-to-refresh.zip -d .
python3 videonic-v29-pull-to-refresh/APPLY.py
```

If the installer prints `SAFE STOP`, **do not force-copy MainActivity.kt**. It means the prior version differs and needs review. The install makes a timestamped, verified source backup in `videonic-ui-backups/`. Running the installer again is safe.

## Build/debug APK in your Codespaces

Your Codespaces uses system `gradle`, not `./gradlew`:

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Result: `app/build/outputs/apk/debug/app-debug.apk`.

Before creating your signed AAB, verify pulling down works in Videos list/grid, Folders list/grid, an opened folder, and after adding/removing a video from another app. Verify existing fullscreen, Lock, MKV resume, playlists, and v28 splash are unchanged.

**Validation:** The guarded installer is tested on v28 synthetic fixtures; the code was checked statically. Gradle compilation and device behavior cannot be tested without the full Android project.
