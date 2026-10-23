# Videonic Codespaces Library & Playback Polish

This update was tested at the patch-script level on the most recent source structure available from the development archive. It has **not been compiled with Android SDK in this environment**.

## Implemented
- Remove overall video count on Videos page.
- Hide refresh, sort and list/grid toolbar icons on Folders tab.
- Display MediaStore video quality labels (480p, 720p, 1080p, 2K, 4K); unknown metadata shows a dash.
- Reset headphone/background-audio mode each time a different video is opened. The player button is the only place to enable it.
- Offer Resume / Start over when reopening a video with more than five seconds of saved progress.
- Add multi-select video picker via an **Add videos** button inside an existing playlist.
- Android adaptive launcher icon uses blue background (no white backing resource).

## Applying
Upload ZIP to project root. Run:
```
unzip -o videonic-next-codespaces.zip -d .
python3 videonic-next-codespaces/APPLY.py
```
Backups are created on success. Installer checks its source anchors first; if different from expected, nothing is changed.

## Kotlin compilation
```
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Not included yet
- Genuine HW/SW decoder rebuild and selection
- Gesture speed interface matching screenshot
- Bulk document copy/move flow with Android file pickers
- Encrypt-and-remove original via selected video action
- Cut and MP3 conversion/transfer features
- Three-dot bottom sheet redesign
Android's modern scoped-storage file operations cannot be universally preauthorized at first launch. Android system or Storage Access Framework consent may be required for individual changes.
