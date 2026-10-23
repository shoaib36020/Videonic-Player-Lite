# Videonic Lite v6 — UI polish

Updates source based on the compiled Videonic v5 UI. This ZIP contains only `MainActivity.kt` and a source-version-guarded installer. It does not change AndroidManifest, player service, security/vault, local library database, Feedback or other files.

## Fixes

- No date headings above video rows (sort still works).
- Preserve currently visible video position when switching List ↔ Grid, including from Sort & View.
- New dark theme color scheme, adaptive typography and surfaces, folder artwork and sheet controls.
- Recent and Me headers use status-bar-safe top spacing, aligned with Videos/Folders.
- Recent video hero and earlier rows show a tappable three-dot menu.
- Resolution metadata uses readable tier-based colors for 480p, 720p, 1080p, 2K, and 4K across Videos, Recent, and shared video rows.

## Apply (project root)

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v6-video-recents-dark.zip -d .
python3 videonic-v6-video-recents-dark/APPLY.py
```

If the source check fails, do not override it: the current Kotlin file has diverged. Share it for an adapted update. The installer makes a backup in `videonic-ui-backups`. Re-running after success is safe.

## Compile

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Only build your next APK after this passes. This update was source-checked, not Android SDK-compiled here.
