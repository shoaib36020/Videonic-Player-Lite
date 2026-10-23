# Videonic Lite v16.2 — Internal playback only (compact)

**Goal:** Never recommend or open a separate video player. Retain the current small Media3 APK and existing in-app decoder choices.

## Exact changes

- Removes **Open in compatible player (10-bit)** from the video-player three-dot menu.
- Removes **Open in another player** from that menu.
- Removes the underlying external-video-player launcher helper.
- Replaces **Other player** on the playback-error dialog with **Try software**, **Retry automatic**, and **Close**.
- Retries `prepare()`/`play()` when the selected decoder mode is already active, fixing the previous no-op when retrying Automatic without changing modes.
- When changing modes, uses the existing Media3 service implementation to preserve session/media position and restore playback if a decoder initialization fails.
- No LibVLC dependency is added, no other libraries or permissions change. The existing v16.1 player UI, playlists, vault, history, and all other screens remain the same.

**Important:** This update removes all *external-player options* and improves built-in retry behavior. It **does not add a new video codec**. Codec/container support still depends on the Android system decoders and Media3 extractors. No <=30 MB APK can be promised to decode every existing or future video format/resolution, especially unsupported 10-bit profiles. A later downloadable in-app codec module would provide broader support, but would consume storage beyond the base APK and would require its own engineering and distribution.

## Apply in Codespaces

Upload `videonic-v16-2-internal-only.zip` to `/workspaces/Videonic-Player-Lite`, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-2-internal-only.zip -d .
python3 videonic-v16-2-internal-only/APPLY.py
```

The script refuses unexpected source revisions, creates a verified backup, and safely recognizes repeat runs. It modifies only `MainActivity.kt`.

## Compile, build, check size

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process

gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process

bash videonic-v16-2-internal-only/CHECK_SIZE.sh
```

Expected debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Do not uninstall the current app. Android compilation, APK size, and real device codec behavior have **not** been verified in this environment.
