# Videonic Lite v10 — Full-container Liquid Glass navigation

This is an **incremental update on top of exact v9 source**, NOT a complete Android project. It changes only `MainActivity.kt` and specifically the bottom navigation renderer, plus one Compose import. All other screens, playback, Private Folder, Feedback, and Media3 service are left untouched.

## What changed

- Full floating navigation container (behind **all 4 icons**) has more visible blue-tinted, semitranslucent glass gradient.
- Layered light reflection and luminous edging run across the entire bar, not only the active tab.
- Each inactive icon has its own subtle rounded glass chip; selected tab remains a blue pill.
- Supports dark and light themes, keeps rounded touch clipping and the exact existing click handlers.
- Classic mode is unchanged in behavior and visual intent. Liquid Glass continues to be optional in Me → Settings → Appearance → Bottom Navigation.
- Does not implement guaranteed real-time backdrop blur; Compose gradients/reflections are compatible across Android versions.

## Applying from GitHub Codespaces

Upload ZIP to repo root:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v10-full-glass-navigation.zip -d .
python3 videonic-v10-full-glass-navigation/APPLY.py
```

Expected: `SUCCESS: v10 full-bar Liquid Glass navigation installed.`

If you see `SAFE STOP`, **do not force overwrite**; send the output and latest MainActivity.kt instead. The installer backs up the old file under `videonic-ui-backups`.

Compile and build your debug APK:

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Download: `app/build/outputs/apk/debug/app-debug.apk`.

**Build status:** Installer/source checks passed here, Android Gradle build has *not* been run here. This file is source only, not an APK.
