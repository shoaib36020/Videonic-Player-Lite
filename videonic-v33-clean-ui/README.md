# Videonic Player Lite — v33 Clean UI + Recent Ordering

## What's fixed

- **Recent history:** always retains recently-watched order from `LocalLibrary.recent()`; it never inherits the Videos tab's Name/Size/Duration/Date sort.
- **Duration chips:** black backgrounds are less opaque in both video list thumbnails and grid-card overlays (approximately 46–48% black).
- **Compact library:** main Videos/Folder headings have less wasted top space, row thumbnails are smaller (138×82dp → 116×70dp), list rows and grid spacing are tighter, and folder tiles/art are shorter.
- **Typography:** soften formerly ExtraBold list/folder headings and video titles to Bold or SemiBold, retaining legibility.
- **Touch feedback:** shape-aware clipping applied before `clickable`/`combinedClickable` in targeted rounded UI elements (video rows, cards, folders, playlist cards, favorites, theme choices, sort list/grid pills, settings rows, floating resume button). This keeps touch ripples inside rounded corners.
- **Floating navigation:** smaller bottom gap, lower glass bar height, smaller floating resume control. Both classic and glass navigation remain available.

No changes to media service, decoder, MKV seeking and bookmarks, screen lock, auto rotate, playback speed, private vault, playlist storage, permissions, splash resources, or the upload key.

## Installation — requires v32 source (used in v1.0.2)

Upload `videonic-v33-clean-ui.zip` to `/workspaces/Videonic-Player-Lite`, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v33-clean-ui.zip -d .
python3 videonic-v33-clean-ui/APPLY.py
```

Safe checks: only accepts the exact v32 `MainActivity.kt` hash; creates a verified backup under `videonic-ui-backups/`; repeated application is harmless. If it says `SAFE STOP`, **do not force**; the source differs from the known version, so provide the error and your latest source.

## Build Android debug APK first

```bash
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Check: `app/build/outputs/apk/debug/app-debug.apk`.

Test: Recents stays watch-order when Videos is sorted by name; video/folder list and grid; different screen sizes; both Classic and Liquid Glass navigation; light/dark settings; ripple clipping; playback, speed and MKV resume.

## Only after testing — release to Google Play

Your current published internal/closed test already used `versionCode = 3`. To create the next Play release use **v1.0.3 with version code 4**. From project root:

```bash
python3 videonic-v33-clean-ui/PREPARE_PLAY_UPDATE.py
bash videonic-google-play-release/RELEASE.sh check
```

**Back up / move the existing signed AAB before signing**, because the release helper intentionally refuses to overwrite its destination.

```bash
mkdir -p app/build/outputs/bundle/backups
mv -n app/build/outputs/bundle/release/Videonic-Player-signed.aab \
  app/build/outputs/bundle/backups/Videonic-Player-v1.0.2-signed.aab
bash videonic-google-play-release/RELEASE.sh build
bash videonic-google-play-release/RELEASE.sh verify
```

If the backup destination already exists, inspect it before moving anything further. Sign with the **same existing upload key**. Upload only verified `Videonic-Player-signed.aab`, not `app-release.aab`.

**Verification limit:** Installer and static source checks were performed. Full Android Gradle compilation and runtime rendering must be verified in your Codespaces and on device.
