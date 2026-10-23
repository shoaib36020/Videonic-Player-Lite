# Videonic Player v18 — Sorting, Splash, App Icon, Private Folder

**Apply only after the previously compiled v17 update.** The installer checks the exact MainActivity.kt distributed in v17 and the Private Folder files distributed in v8. It stops on unexpected source versions instead of replacing newer work. Backups are created in `videonic-ui-backups/before-v18-*`.

## Updates

1. **Videos sorting:** Changing ascending/descending, sort field, or sort order keeps the existing list/grid scroll state. The sort no longer recreates the Videos screen. Index-based lists keep the same viewport instead of jumping to the new position of an old item. Folder sorting also stops relocating the viewport to a re-sorted folder.
2. **Responsive grids:** Videos displays a single grid column on very narrow screens; Folders uses adaptive columns instead of a fixed two-column layout.
3. **Consistent splash:** White background on both light and dark devices; the same existing blue `videonic_brand.png` is the launcher icon, Android 12+ system splash icon, and branded in-app intro. The app name `Videonic Player` is shown beneath the centered logo in the in-app intro. No dark-blue second branded intro.
4. **Private Folder PIN:** Screen-height- and width-aware keypad/dot/header sizing, including a two-column keypad on short landscape displays, to avoid scrolling on common small handsets, without resetting existing PINs or encrypted files.
5. **Large private videos:** The existing vault code already encrypts in 64 KiB streaming chunks with a `Long` file-size counter and has **no hard-coded MB/GB cap**. This is preserved. The temp playback file now keeps the source extension (e.g. `.mkv`), instead of always `.mp4`.

## Important storage / security constraints

- Importing into Private Folder is a **secure move**, not an in-place rename: the file is encrypted, then verified, and Android may ask permission before the unencrypted original can be removed. During import the device needs sufficient spare storage to hold the encrypted copy. This is a physical storage requirement, not an arbitrary size limit.
- Temporary decrypted files are still used during private playback and removed by the existing cleanup mechanism. Existing PIN-derived keys and encrypted files are not changed.
- Android 12+ controls a brief system launch-splash stage; the system and in-app stages use the same white background and same logo to avoid two different splash designs.

## Install in GitHub Codespaces

Upload `videonic-v18-sort-splash-private.zip` into `/workspaces/Videonic-Player-Lite` and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v18-sort-splash-private.zip -d .
python3 videonic-v18-sort-splash-private/APPLY.py
```

Expected: `SUCCESS: Videonic Player v18 applied.`

### Compile before generating an APK

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Full Android compilation was **not** performed in this container. Source and installer integrity checks are not a substitute for an Android build and live device testing.

**Recommended device tests**

- Scroll partway down in Videos list and grid, change sort direction/field, and check the viewport does not jump.
- Test sorting in Folders.
- Switch Android to Light and Dark mode and cold-launch the app; confirm the same white splash, blue logo, and title.
- Check icon on the home screen and app settings; Android may mask it to a round shape.
- Test the Private Folder PIN on narrow portrait and short landscape phones; verify the entire keypad fits.
- Import a large video only with sufficient free space, and confirm Android removes the original if you approve the system prompt. Don't delete the original manually until the encrypted video has been verified.
- Build the APK and check it is still <=30 MB before release.
