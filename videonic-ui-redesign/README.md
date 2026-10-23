# Videonic Lite · Reference UI Update

This package was generated from the exact 1,402-line `MainActivity.kt` supplied in this conversation. It only replaces `app/src/main/java/com/videonic/lite/MainActivity.kt`. All imported classes and other app files are left unchanged.

## Included screen updates
- Videos: large heading, integrated branding, wide rounded search, inline sort indication and view switch; flatter list and grid surfaces
- Folders: folder cover art from video thumbnails, item counts, dividers, simple title
- Recent: continue-watching hero, watch progress, earlier watched list, clear history
- Me: blue gradient profile/storage card, 2-column collection tiles, settings/about/support
- Settings: grouped section headings and rounded toggle rows, existing settings behavior preserved
- Playlists: prominent New button and a designed empty state
- Favorites: 2-column video grid
- About: app icon, description and legal navigation cards
- Video action menu: rounded Material3 bottom sheet with existing working actions
- Recycle Bin: simplified header, amber note and rounded restore cards

The app will not display the phrase `Personal Media Hub` in this file. The Private Folder, Android trash operations, feedback endpoint, playback engine and navigation routines are preserved.

## Apply in GitHub Codespaces

1. Upload `videonic-reference-ui.zip` to the project root in Codespaces.
2. From the project-root terminal run:

```bash
unzip -o videonic-reference-ui.zip -d .
python3 videonic-ui-redesign/APPLY.py
```

If it prints STOP, do NOT force the update. The script deliberately refuses to overwrite source differing from the uploaded baseline.

## Compile without building/installing an APK

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

Once it compiles, build a debug APK with the same command replacing `:app:compileDebugKotlin` with `:app:assembleDebug`.

## Limitations

Android compilation has not been performed by this package author because the Android SDK/Gradle tooling is unavailable in this runtime. Appearance depends on device font scaling and screen size. Only the source screens in this file are changed; UI implemented in separate `SecurePrivateFolder` and `FeedbackScreen` Kotlin files is preserved, not restyled by this package.
