# Videonic Lite — reference screens v7

## Changes

- Playlists: new list and empty state UI; create playlist dialog retains real playlist creation.
- Settings: reference grouped cards, functional Resume/Auto-next/Speed/Seek duration/Gestures/Theme/Grid settings.
- Hardware acceleration: informational Auto decoder row only; no fake HW/SW toggle.
- Theme: three visual previews; System/Light/Dark use existing appearance storage.
- Favorites: reference 2-column cards with a filled RED heart when saved, outline when unsaved. Heart toggles actual library state immediately.
- Private Folder: PIN keypad and protected video list. **Preserves the existing 6–32 digit PIN**, encryption key format, import/export/deletion and playback-cache behavior. The reference shows four circles, but six are shown because changing an existing PIN contract would lock out current users.
- Recycle Bin: metadata cards, Android restore consent, visible Empty action with permanent-delete confirmation and Android system approval. Remaining days are read from MediaStore expiry metadata where available, not invented.
- Header top padding: same 44dp on root and subpages; bottom navigation from v6 is unchanged.

Only MainActivity.kt and the PrivateFolderScreen.kt UI component are replaced. The backup installer **refuses** to run unless the source matches v6 and a supported vault UI version.

## Apply to original Codespace

Upload videonic-v7-playlists-settings.zip to `/workspaces/Videonic-Player-Lite`, then run:

```
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v7-playlists-settings.zip -d .
python3 videonic-v7-playlists-settings/APPLY.py
```

Compile before building APK:
```
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Do not uninstall an existing APK if it contains Private Folder data. Reinstalling as an update requires the same app ID and signing key. Compilation is not verified in this environment; please share any compiler errors.
