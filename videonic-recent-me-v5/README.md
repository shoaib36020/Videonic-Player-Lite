# Videonic Lite — Recent and Me UI v5

This is a guarded, limited-scope update for the existing native Android app, after the repaired Videos/Folders UI v2 and Sort & Video Options v3 updates. It is also compatible with Videos Home v4, if already installed.

## Changes
- Recent and Me root page headings use the same 44dp top padding as Videos/Folders, not 26dp.
- The Me root header background matches its pale body in light mode.
- Recent: the Clear action appears beside "X recently watched" and still invokes your original confirmation dialog.
- Recent: a larger, responsive Continue Watching hero card, soft image gradient, top label pill, watch-progress bar, time remaining, and round play button.
- Recent: earlier watched videos display a **circular progress ring** around the play indicator, percentage watched, remaining time, video quality and duration. Tap to open/resume. Long-press to open its existing video options menu.
- Me: keeps the collection cards, Settings, About, Private Folder, Recycle Bin and Support entries; improves app version and storage information. Does not show the unwanted tagline "Personal Media Hub".

**Not modified:** Videos home list/grid, Folders list/grid, playback UI/service, Sort & View sheet, video-options sheet, Private Folder data, Feedback/backend, Recent history storage.

## Apply in GitHub Codespaces

Upload `videonic-recent-me-v5.zip` to the repository root. Run only these shell commands (do not copy the markdown fences):

```sh
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-recent-me-v5.zip -d .
python3 videonic-recent-me-v5/APPLY.py
```

Expected message: `SUCCESS: Recent + Me reference UI v5 applied safely.` If the source doesn't match, the installer refuses to modify it. A timestamped backup is saved in `videonic-ui-backups/`.

## Compile (before generating APK)

```sh
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Android compilation and physical-device testing must happen in Codespaces; this ZIP has been checked structurally against the available latest UI source, not compiled here.
