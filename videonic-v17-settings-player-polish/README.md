# Videonic Player v17 — Settings and Player polish

Requires the exact **v16.10** MainActivity.kt. This installer checks the full SHA-256 source hash and refuses other revisions, so it cannot accidentally erase later edits.

## Changes

- Hide status/notification and navigation bars in landscape fullscreen using AndroidX WindowInsetsControllerCompat; restore the system bars on exit or PiP.
- Prevent playback overlay buttons (header, transport, bottom controls) from displaying while the brightness or volume gesture indicator is active.
- Add 2.5x to hold-speed steps and reduce bar padding, dot sizes, overall height; keep hold-speed temporary.
- Make the Playback Speed sheet scrollable so all 11 speeds from 0.25x through 4x, including 2.5x, are available on compact phones.
- Change visible app branding and installed Android launcher label to **Videonic Player** without changing application ID, private data, or signing.
- Show a short branded startup screen with the app icon and **Videonic Player** label. Android's initial OS splash remains system-controlled.
- Persist Videos sort and Folders sort/order with SharedPreferences. They survive returning Home and a process restart.
- Settings > Grid View applies the selected layout to Videos, Folders, Favorites, Playlists, and video lists in folders/playlist details. Independent page toggles still work afterwards. Recent retains its specialized Continue Watching layout.
- Fullscreen / Exit Fullscreen uses a forced orientation choice (portrait/landscape) that cannot be immediately reset by auto-rotate; reverts to the app's auto-rotate preference when leaving the player.

## Install

Upload `videonic-v17-settings-player-polish.zip` to your Codespaces project root, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v17-settings-player-polish.zip -d .
python3 videonic-v17-settings-player-polish/APPLY.py
```

**Compile first:**
```bash
gradle :app:compileDebugKotlin \
 --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
 -Pkotlin.compiler.execution.strategy=in-process
```

**Build APK only after BUILD SUCCESSFUL:**
```bash
gradle :app:assembleDebug \
 --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
 -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Check it remains <=30 MB before installing.

## Manual tests

1. Play portrait, auto-rotate to landscape, verify notification/status bar is hidden; tap Exit Fullscreen while still holding the phone landscape. UI must return to portrait and show system bars.
2. Swipe to adjust volume and brightness; only the small side indicator should appear until it fades (controls hidden).
3. Long-press over video for temporary speed. 2.5x is available, and the bar fits smaller screens.
4. Tap Speed button; scroll sheet to see 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 2.5x, 3x, 4x.
5. Set a non-default sort, leave app Home and relaunch. Sort should persist.
6. Set Settings > Grid View and verify Videos, Folders, Favorites and Playlists.
7. Cold launch shows Videonic Player brand, and app icon label says Videonic Player.

This patch does not change the MKV seek index, decoder, playback service, Private Folder, or media files. Android device testing is still required. Installer smoke tests do **not** verify APK compilation or device behavior.
