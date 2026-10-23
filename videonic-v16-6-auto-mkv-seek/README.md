# Videonic Lite v16.6 — automatic MKV seek recovery + video surface

For the reported files that play but show **no usable seek index**.

## Changes

- Remove the three-dot **Try alternate seeking** item entirely.
- Swipes, double tap, 10-second buttons, and timeline all use a common seek request.
- When an MKV is reported unseekable, the first seek automatically tries Android MediaParser (Android 11+) and then a lightweight in-app read-only MKV Cluster index on failure.
- Indexing reads EBML *headers* and Cluster timestamps/offsets only. It does not rewrite, copy, or delete the video.
- If the file lacks readable Cluster timestamps or Android cannot seek to indexed positions, Videonic reports this rather than pretending every MKV is seekable.
- PlayerView uses a **TextureView** in the expanded player to avoid stale SurfaceView scaling after portrait/landscape transitions; the mini-player still uses only a thumbnail.
- No LibVLC, FFmpeg, external playback button, or heavyweight new dependency. Preserve the target of ≤30 MB, but check actual APK size.

## Apply

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-6-auto-mkv-seek.zip -d .
python3 videonic-v16-6-auto-mkv-seek/APPLY.py
```

The installer **requires the exact v16.5 files** for both changed Kotlin source files, backs them up, and rejects diverged versions. If the app has newer edits, do not force this patch.

## Compile before APK

```bash
gradle :app:compileDebugKotlin \
--no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
-Pkotlin.compiler.execution.strategy=in-process
```

**Known limitations:** This is experimental and has not been Android-compiled or exercised on the user's MKVs. For certain malformed, streaming, or unknown-size Matroska Clusters the index cannot be generated. The index is approximate and may jump near (rather than exactly to) a requested frame. The player's decoder capability remains device dependent.

If compilation fails, capture errors via `gradle :app:compileDebugKotlin --console=plain ... > build-error.log 2>&1` and share the `e:` lines.
