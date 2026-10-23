# Videonic Lite v16.1 — Compact app package (30 MB target)

This change is designed for users who prioritize a **main APK no larger than 30 MB**. It removes the heavy LibVLC native library that was introduced in v16 while preserving the rest of the v16 player UI updates.

## Preserved
- Rotating fullscreen icon and rotation behavior fixes
- Hold-to-select playback-speed bar, horizontal seek adjustments
- Tap-to-cycle aspect ratio, compact volume/brightness indicators
- Decoder preference UI, Media3 player, playlists, history, PiP and headphone settings
- Existing Private Folder and local saved data

## Compromise for 10-bit media
Videonic continues to decode using Android device-supported hardware/software codecs through Media3. Devices without a suitable 10-bit HEVC/AV1 decoder **cannot be guaranteed to play those files inside the app**. For a problematic file you may use *Player → ⋮ → Open in compatible player (10-bit)*. This uses Android's normal player chooser and grants read access to the public video URI. It does not export private vault content. If there is no compatible external player installed, Android may not offer a useful choice.

Native LibVLC software playback has been removed from the main app to respect the size cap. Keeping a full bundled software decoder in a guaranteed <=30MB universal APK is not something this patch can promise. Alternate future options are a separate on-demand codec module or a separately installed compatible player.

## Install in Codespaces

1. Upload `videonic-v16-1-compact.zip` to your project root. Run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-1-compact.zip -d .
python3 videonic-v16-1-compact/APPLY.py
```

2. Compile:

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

3. Build APK:

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

4. **Measure the APK** (30 MB interpreted as 30,000,000 bytes):

```bash
bash videonic-v16-1-compact/CHECK_SIZE.sh
```

The patch removes LibVLC's native size overhead, but the final APK size must be checked after building on Codespaces; under 30 MB **is a target, not a measured guarantee**. If the check still exceeds 30 MB, send the exact reported size so we can shrink the other bundled dependencies safely.

Do not uninstall the app or erase its data. The installer checks against the **distributed v16 MainActivity.kt**, backs up all modified/deleted source files, and refuses to overwrite unexpected edits. Apply it only after v16 is installed in the source project.
