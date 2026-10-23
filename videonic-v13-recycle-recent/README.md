# Videonic Lite v13 — Recycle Bin + Recent Progress Fix

This update targets the existing v12 source (with or without the Favorites count patch). It replaces **only `MainActivity.kt`**; other Kotlin files, the player service, manifest, private vault, and feedback setup are unchanged.

## What's changed

- **Recycle Bin rows:** Small **Restore** and **Delete permanently** icons; no long-text Restore button.
- **Selection:** Tap the selection icon near **Empty**, or long-press a trashed video; select individual videos or **Select all**, then **Restore** or **Delete** only the selected items. Tap the selection icon again to leave selection mode.
- **Empty:** Still offers to permanently delete *all* videos visible in Android's system trash.
- **Safety:** All permanent deletion operations have an in-app confirmation followed by Android's system approval. Restoration also uses Android's MediaStore approval.
- **Recent:** The Continue Watching progress track no longer uses Material3's end-marker/white dot. Watch position calculations are unchanged.
- **Favorites:** The earlier Favorites-count/search fix remains applied even if this package is installed directly on the original v12 source.

## Install in Codespaces

Upload `videonic-v13-recycle-recent.zip` to the root of `/workspaces/Videonic-Player-Lite` and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v13-recycle-recent.zip -d .
python3 videonic-v13-recycle-recent/APPLY.py
```

The installer makes a timestamped copy in `videonic-ui-backups` and refuses to overwrite modified or unknown source versions. If `SAFE STOP` appears, provide your latest `MainActivity.kt` instead of forcing the patch.

## Compile before live testing

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Only after **BUILD SUCCESSFUL**, build the test APK with `:app:assembleDebug` using the same flags. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Test plan

1. Use expendable video files only for deletion tests. Trash several files in Videos and open Recycle Bin.
2. Confirm each card displays only Restore and Delete icon actions and that each targets only its own item.
3. Long-press the first item, select a second, restore selected; verify unselected items are still in the Bin.
4. Trash more expendable videos, batch-delete selected, and ensure other videos remain.
5. Test **Empty** separately; it should request deletion of all visible trashed items.
6. Check the Recent large card: the progress bar should have no white endpoint dot.

**Validation:** Kotlin source passed a PSI syntax parse and the installer is tested against both supported v12 checksums. Full Android/Compose compilation and phone behavior have not been run in this environment.
