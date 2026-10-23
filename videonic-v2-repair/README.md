# Videonic Videos & Folders V2 — compatible installer

This update repairs the incompatible dependency in the earlier v2 patch. It supports:

- The **10-screen reference UI** release (no `VideosLibraryHome` function).
- The **earlier three-screen** patch (contains `VideosLibraryHome` and `FolderLibraryHome`).

It changes the Videos and Folders home layouts, not playback, Recent, Me, Private Folder or Feedback.

## Apply

Extract this ZIP to the Codespaces project root, then run:

```bash
python3 videonic-v2-repair/APPLY.py
```

The installer creates a source backup and refuses unknown source shapes.

## Verify Kotlin compilation

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

If compilation fails, share the `e:` Kotlin error lines and keep the current APK installed.
