# Videonic Lite v12 Favorites Count Fix

This update corrects the issue where Me shows “1 Favorite” while the Favorites page is empty.

- The Me card and Favorites screen use the same set of favorites whose video IDs exist in the current media scan.
- Old favorites whose videos are missing are **not deleted from storage**; they will appear again if those videos become available.
- Opening a Me subpage resets the old search query, so a Videos search cannot hide all Favorites accidentally.
- No player, settings, Private Folder or other source files are changed.

**Apply:**

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v12-favorites-count-fix.zip -d .
python3 videonic-v12-favorites-count-fix/APPLY.py
```

**Build test APK:**

```bash
cd /workspaces/Videonic-Player-Lite
gradle :app:assembleDebug \
--no-daemon \
--max-workers=1 \
--no-parallel \
--no-configuration-cache \
-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
-Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

If the installer shows `SAFE STOP`, do not overwrite your current Kotlin file; it means the code has changed since v12. Share the current file for an adapted patch. Full Android compilation and on-device behavior are not verified here.
