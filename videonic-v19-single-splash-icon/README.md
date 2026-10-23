# Videonic Player v19 — Single splash and full icon

This is a **branding-only** patch for v18. It does **not** change Kotlin playback, sort behavior, vault encryption, or app data.

## What it changes

- **One visible branded splash**: Android 12+ requires a brief OS-provided starting splash. This patch makes that system stage **plain white with no icon**, leaving the existing in-app screen as the **only branded splash** (centered blue app icon and **Videonic Player** below it). Both light and dark mode use white for this launch presentation.
- **Full-bleed launcher icon**: An adaptive launcher icon on Android 8+ has a blue background behind the whole icon instead of an Android-generated white tile; older launchers use an opaque blue square. The app's white Play symbol remains white in dark mode.
- **Consistent in-app logo**: The triangular symbol in the previously transparent PNG is filled white; previously, dark backgrounds showed through its center. Existing `videonic_brand` references automatically use the corrected artwork.
- The launcher name stays **Videonic Player**.

Note: Android's mandatory OS startup stage on Android 12+ cannot be fully disabled. This patch hides *its duplicate logo* to avoid a second branded screen. A brief plain white transition may still appear on cold start.

## Apply

Upload the ZIP to the root of the Codespace containing your **applied v18** project, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v19-single-splash-icon.zip -d .
python3 videonic-v19-single-splash-icon/APPLY.py
```

The installer checks for the exact v18 `MainActivity.kt`, manifest, resource styles, and brand icon. It creates an archive of originals under `videonic-ui-backups/before-v19-*` and refuses unexpected changes.

## Compile

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

For the APK:

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`. Install *over* your existing app; do **not** uninstall it, because app data and private videos could be lost. Android launcher icon caches may take some time to refresh; restarting the launcher or device can help after installation.

Android compilation and on-device launcher/splash behavior must still be tested in Codespaces and on your phone. The patch adds no native libraries and should have negligible APK size impact, but check the 30 MB size target after building.
