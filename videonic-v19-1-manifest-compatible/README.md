# Videonic Player v19.1 — Splash/launcher compatibility fix

## Why v19 stopped
The v19 installer required an exact SHA-256 hash for `app/src/main/AndroidManifest.xml`. Your Codespaces manifest differs; the installer correctly stopped without changing anything. This replacement makes a **narrow change only to the application icon and splash theme**, retaining unrelated AndroidManifest entries, including existing permissions and services.

## Apply
Upload the ZIP to the root of your existing Codespaces project, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v19-1-manifest-compatible.zip -d .
python3 videonic-v19-1-manifest-compatible/APPLY.py
```

The installer checks that `MainActivity.kt` is the v18 version, validates both XML sources, backs up modified files, and preserves the rest of your manifest. If any unrelated custom icon assets conflict or the v18 sources do not match, it stops without writing. If that happens, share the current `AndroidManifest.xml` so the change can be made exactly.

## Compile

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

It retains the full-bleed launcher blue artwork, white Play mark, and existing branded splash with app name. Android 12+ still has a required system launch stage; the patch replaces the system stage's logo with a blank icon to avoid a second *branded* splash. The installed APK should be tested before assuming the Android launcher cache refreshed. Do not uninstall if the Private Folder contains data.
