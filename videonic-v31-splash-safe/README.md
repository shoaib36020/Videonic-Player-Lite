# Videonic v31 — Splash clipping correction (splash-only hotfix)

Fixes the clipped/overlapping Videonic splash logo and title shown in v30.

- **Only one asset changes:** `app/src/main/res/drawable-nodpi/videonic_splash_composite_v30.png`.
- Scales the **existing logo and text together**, instead of offsetting either one.
- Constrains visible pixels to the center 149-pixel radius of the 864×864 splash viewport; simulates and checks a conservative 150-pixel circular crop. Android's system splash mask varies across devices, so this is deliberately more conservative than prior v28/v30 artwork.
- **One native splash remains**, with `Videonic Player` centered directly below the icon; Android requires a compact image inside the mask, so it will appear smaller than a custom full-screen layout.
- Does **not** touch MainActivity.kt, folder sorting, speed locks, video playback, rotation, app data, playlists, or build configuration.

## Install over v30

Copy `videonic-v31-splash-safe.zip` into your Codespaces project root and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v31-splash-safe.zip -d .
python3 videonic-v31-splash-safe/APPLY.py
```

The installer checks that the current artwork and launch theme match v30, creates a verified backup in `videonic-ui-backups/`, supports safely rerunning, and **will not overwrite an unexpected resource**.

## Build APK in Codespaces

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Validation

Passed source image integrity and geometric mask simulation (all visible pixels inside conservative 150 px center circle). Installer tested on v30 mock Android projects with both launch-theme variants, backups, idempotence, and rejection of unexpectedly edited artwork. **Full Android build and phone display remain unverified.**

Expected v30 artwork SHA256: `02931d7975f5f5a15a9618436abfcb413de2dd507742bfd9db8e2e3b6c96a966`
v31 artwork SHA256: `2f238dae7e0053b48b0196400abf8578ef0736b95886babadf4a8fdb99fb65b2`
