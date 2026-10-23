# Videonic Lite — Videos and Folders UI v2

This patch changes only the `VideosLibraryHome` and `FolderLibraryHome` Compose screens inside your **existing** `MainActivity.kt`. Existing playback, Recent, Me, Settings, Private Folder, Recycle Bin and Feedback code remains untouched.

## What changes

- Adds more **top spacing** above the Videos and Folders headings to avoid a cramped header.
- Changes the **Videos grid** to the clean, edge-to-edge thumbnail / title / quality layout in your reference. The two-column phone layout keeps labels one line with ellipses; long-press multi-select, open-video and video-menu actions remain.
- Videos list keeps date groups and the custom rows from your previous patch.
- **Folders grid** uses large pastel-colored cards with folder iconography (no video thumbnails).
- **Folders list** uses newly drawn colorful folder artwork (no video thumbnails).
- Folder colors remain stable when filtering or changing sort order.

## Apply to the original GitHub Codespace

Upload `videonic-videos-folders-v2.zip` to the project root, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-videos-folders-v2.zip -d .
python3 videonic-videos-folders-v2/APPLY.py
```

The installer saves a backup and stops without changing anything if it cannot find the earlier screen patch. Running it twice is safe.

## Compile (do not install the APK until this succeeds)

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

This ZIP has been structurally checked, **not** Android-compiled in this environment. If compilation fails, send the first `e:` compiler errors and we will correct them before you build an APK.
