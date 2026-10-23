# Videonic Lite — Videos Home v4 (2026-10-19)

This patch requires your Videos/Folders UI v2 repaired version and Sort & Video Options Bottom Sheets v3. It changes only the **Videos home** UI/behavior and **bottom navigation spacing**. It does not change Folders, Recent, Me, Settings, Private Folder, playback controls, or the feedback backend.

## Changes
- Videos date separators: Today, Yesterday, weekday + date (current year), or dated year (older), plus the video count. For Name/Size/Duration sorts, one All Videos group preserves the global sort order.
- Small search icon in the Videos header opens/closes the existing search field.
- Floating round blue button now has a Play icon. It expands/resumes a currently minimized video, otherwise opens the newest available Recent item at its saved position. Displays a message when no recent video exists.
- Adds 14dp of additional space below the shared bottom navigation bar (moves it up slightly). No change to which tabs or screens it contains.

## Apply
1. Upload `videonic-v4-video-home.zip` into the root of `/workspaces/Videonic-Player-Lite`.
2. Run exactly these terminal commands:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v4-video-home.zip -d .
python3 videonic-v4-video-home/APPLY.py
```

This makes a timestamped backup in `videonic-ui-backups`. It aborts without editing if required parts of the expected source aren't found. Re-running after a successful install is safe.

## Kotlin compilation check
```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Only build the APK once that reports BUILD SUCCESSFUL. This update was structurally tested against the v3 source file but was not compiled in an Android SDK here.
