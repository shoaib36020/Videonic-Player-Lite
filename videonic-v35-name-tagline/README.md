# Videonic v35 — short launcher name & minimal tagline

### Exact changes

- **Name under the installed app icon on the Android home screen:** `Videonic` (previously `Videonic Player`).
- **Splash screen:** same **original installed blue Videonic icon**, white background, centered `Videonic` with a small `Lite Video Player` tagline beneath it.
- No extra patterns, animation, loading dots, or secondary splash.
- Existing app package `com.videonic.lite`, Kotlin code, video playback, icon resource, Play Store listing, signing keystore, SDK, and Gradle version settings are **unchanged**.

### Run in GitHub Codespaces

Upload the ZIP to `/workspaces/Videonic-Player-Lite` and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v35-name-tagline.zip -d .
bash videonic-v35-name-tagline/BUILD_APK.sh
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

If you'd rather apply only the branding without building, run:

`python3 videonic-v35-name-tagline/APPLY.py`

**Prerequisite:** v34 must already be applied (the package checks its marker and generated icon). This patch creates verified backups of the Android manifest and splash and can be run repeatedly without changes. Stop and share any SAFE STOP message instead of forcing an installation.

**This ZIP is an incremental update, not a complete project or compiled APK.** The APK must be built in your Codespaces, where the full source exists. Do not build a Play AAB or change your version code until you have tested the debug APK.

When the updated build is installed, Android's launcher may cache the old app name briefly. Restart the home launcher or device if the icon still shows the old label; avoid clearing the app's data.
