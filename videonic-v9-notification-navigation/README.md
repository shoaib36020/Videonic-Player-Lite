# Videonic Lite v9 — Current-video notification, Back navigation & optional glass nav

A guarded patch that builds on the **exact v8 Kotlin source**. Does not replace Private Folder, Feedback, Favorites or player engine.

## Updates

1. Media3 MediaSession sets an explicit immutable Android `PendingIntent` that opens `MainActivity` on notification tap. The Activity uses the existing MediaController and restores the expanded video screen **without restarting playback**, preserving playback position. If a private file is active after Activity recreation, the app routes to the PIN screen rather than bypassing authentication.
2. Privacy Policy, Terms of Use, Open Source Licenses and Permissions & Storage return to **About**. Theme returns to **Settings**, and individual playlist pages return to **Playlists**. Both the top-left Back button and Android system Back follow these rules.
3. Settings → Appearance → **Bottom navigation**: choose **Classic** (default) or **Liquid glass**. The optional mode uses translucent layered gradients, specular highlights and a rounded blue active pill. It is a **glass-inspired visual style**, not a guaranteed live frosted backdrop blur on all Android versions. Saved locally to `navigation_style`.

## Apply in GitHub Codespaces

Upload `videonic-v9-notification-navigation.zip` to the repo root, then run **only** these terminal commands:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v9-notification-navigation.zip -d .
python3 videonic-v9-notification-navigation/APPLY.py
```

Installer saves a timestamped backup of **MainActivity.kt, VideonicPlaybackService.kt, and AndroidManifest.xml** under `videonic-ui-backups`. It refuses unexpected source versions.

Compile (before creating an APK):

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

When successful, build APK separately using `gradle :app:assembleDebug` with the same options.

## Test checklist

- Start a video, enter PiP, tap its Media3 notification: the existing video should reopen at current position.
- Me → About → Privacy Policy → Back returns to About. Repeat for Terms, Open Source Licenses and Permissions & Storage.
- Me → Settings → Theme → Back returns to Settings.
- Me → Settings → Bottom navigation → Liquid glass: the navigation bar changes; switch back to Classic. Restart app to verify preference is saved.

**Notes**: Android and OEMs may render media notifications differently; active playback notifications can be mandatory. This patch does not suppress required notifications. Notification tapping after process death cannot restore an ended Media3 session. Android compilation and on-device PiP behavior have not been validated in this environment.
