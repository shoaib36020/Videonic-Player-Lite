# Videonic Player v34 — Original APK icon + clean splash

This fixes the missing **installed branding update**. It **does not use a newly generated icon**. It finds the original blue play-button PNG in your Videonic project (the one the APK already uses), renders a native splash with that icon and **Videonic Player** directly beneath it on white, and exports a matching **512×512 PNG** for your Google Play listing.

This patch does **not** add a second splash, create decorative waves, add a loading animation, or change playback. It preserves the launcher icon already on the installed APK because that is the one you selected.

## Install (after v33)

Upload `videonic-v34-original-apk-brand.zip` to `/workspaces/Videonic-Player-Lite`. In Codespaces terminal:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v34-original-apk-brand.zip -d .
python3 videonic-v34-original-apk-brand/APPLY.py
```

The script validates the existing v33 `MainActivity.kt`, the known v31 splash, and the native splash theme. It uses only the existing project icon PNG and your already-installed Java JDK. It keeps a verified backup of the old splash under `videonic-ui-backups`. If your project differs, it safely stops without overwriting app files. You can rerun it safely.

After success, find the **exact icon matching your app** (not one of the separately generated images) at:

`videonic-brand-assets/Google-Play-Icon-512.png`

For Play Console's icon field, download this PNG from Codespaces and upload it.

## APK build

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Output `app/build/outputs/apk/debug/app-debug.apk`.

Do not build a Play release until you verify the splash screen on the phone. If updating Play later, versionCode must be higher than the highest code you've uploaded; use `videonic-v33-clean-ui/PREPARE_PLAY_UPDATE.py` if currently on version code 3, which prepares 1.0.3 (code 4). Keep your existing `.jks` upload key.

## Limitations

The supplied ZIP is an **incremental project patch**, not a full APK. Java compilation and a simulated Android resources fixture are checked here, but a complete Android Gradle build and real-device appearance require your Codespaces project.
