# Videonic Lite — bottom sheet update v3

Targets the latest Videonic V2 repaired UI source, which must compile before applying this patch.

Changes **only** the Sort & View bottom sheet, the video options row styling/Details label, and the folder ordering action. It does **not** change the media player, Recent, Me, Favorites, Private Folder, Recycle Bin, Feedback backend, or other dialogs.

## Apply

From the root of the Codespaces repository:

```bash
unzip -o videonic-sheet-update-v3.zip -d .
python3 videonic-sheet-update-v3/APPLY.py
```

The apply script creates a backup under `videonic-ui-backups/` and exits without modifying source if anchors are not recognized.

## Compile

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

**Do not install another APK** until compilation passes. Android UI compilation and runtime interactions have not been verified in this environment.
