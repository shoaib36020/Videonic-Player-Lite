# Videonic Player Lite v30 — Splash, Folder Sort, Speed Lock

**Incremental patch for an existing Android project with v29 already applied.** It is not a standalone app or an APK. The v29 pull-to-refresh feature stays in place.

## Updates

1. **Android 12+ splash crop correction.** Replaces the v28 two-item layer-list (whose positioning offsets overlapped and cut the logo/title on devices) with a single composed raster containing the Videonic logo and **Videonic Player** immediately beneath it. All visible artwork fits inside Android's circular splash-icon safe zone. Leaves the system's native single splash and configured theme intact, without another Compose splash screen. The artwork will appear smaller than a fully custom splash on some Android versions because the platform clips splash icons.
2. **Videos within an opened Folder: Sort and View now work independently.** The Sort panel now reports the actual folder view mode and updates the *folder* list/grid selection (including the Grid option). Folder video sort uses the new persistent `folder_video_sort` preference instead of silently overwriting global Videos sort. Folders root sorting remains separately controlled.
3. **Temporary speed lock bubble.** After long-press + swipe up, a compact pill labeled e.g. `2.0x locked · Reset` appears near the top for 3 seconds then disappears automatically **without changing playback speed**. A normal tap on the video reveals it for 3 more seconds. Tap the pill to return to the speed that was active before speed lock. The existing player speed selector can also change/reset speed; holding again is disabled while already speed-locked. The hold-speed gesture and its help text remain unchanged.

No changes are made to the MKV playback service, MediaStore query, playlist storage, player orientation logic, screen-lock feature, or signing settings.

## Install over the exact v29 source

Place `videonic-v30-splash-folder-sort.zip` in `/workspaces/Videonic-Player-Lite` and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v30-splash-folder-sort.zip -d .
python3 videonic-v30-splash-folder-sort/APPLY.py
```

The installer validates the exact v29 `MainActivity.kt` and v28/v29 splash resources and supports both V26 and V27 launch-theme variants. It makes a verified backup under `videonic-ui-backups/`, safely skips a repeated successful installation, and stops instead of overwriting unknown source changes.

## Build test APK in Codespaces

The Codespaces project uses `gradle`, not `./gradlew`:

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Acceptance tests on phone

- Force-close the app, relaunch, and verify the native startup screen shows a **whole** blue Videonic icon with **Videonic Player** directly below it, neither overlapping nor clipped, and does not then display a second branded splash.
- In Folders, open a folder, select **Sort > Grid** and verify its videos change to a grid. Change back to List; set Name, Size, Date, or Duration sorting and ascending/descending; leave/reopen folder and verify persistence. Check the Videos tab sort/grid remains unchanged.
- Long-press and swipe up to lock a speed. Check the small locked-speed pill disappears after about 3 seconds while fast playback **continues**. Tap the video once to bring it back, then tap **Reset** and verify normal prior speed returns. Verify regular press-and-hold releases to prior speed without locking.
- Regression-check screen Lock, auto-rotation/manual fullscreen, MKV resume, playlists, Favorites, media pull-to-refresh, and startup time.

## Validation performed

Verified incremental installer on representative v29 fixtures using the two launch-theme variants, resource XML validity, output hashes, verified backups, idempotence and rejection of modified sources. Inspected splash art against Android's circular safe area: no nontransparent pixels outside it. **Not compiled with Android Gradle or tested on device**; full Android project is not present in this package.
