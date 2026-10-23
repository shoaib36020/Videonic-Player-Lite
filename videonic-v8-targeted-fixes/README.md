# Videonic Lite v8 — Targeted functionality fixes

This package expects the **exact v7 sources** (the v7 build you reported as successful). The installer checks SHA-256 of all files BEFORE touching anything and backs up all four Kotlin files.

## Apply in the original GitHub Codespace

Upload `videonic-v8-targeted-fixes.zip` to the project root, then run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v8-targeted-fixes.zip -d .
python3 videonic-v8-targeted-fixes/APPLY.py
```

The installer must print `SUCCESS`. If it prints `SAFE STOP`, **do not bypass the check** or copy the new Kotlin files manually; send us the output to adapt the patch.

## Compile before generating an APK

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

### Changed

1. Folders circular Search/Sort tap target uses Material3 shaped Surface to clip press feedback.
2. Folders preserve the same original pastel illustrations in dark mode.
3. Video Options remains open when toggling Favorites; Playlist picker overlays then returns to Video Options after close.
4. Home while a video is expanded and playing attempts PiP; headphones continue to control explicit background-audio permission. Paused media-session notification removal is added to playback service.
5. Double Back exits from Videos root (first press shows a short toast).
6. Brand-new private vault: enter and confirm a 4-digit PIN using responsive circular keys and four indicators. Already created vaults continue to accept their existing original PINs and cryptographic verifier; older PINs are NOT changed or reset.
7. Import now encrypts AND decrypts-to-verify the private file before asking Android to remove the original media. An Android approval dialog may appear. If permission is denied or deletion is unsupported, the secured file remains and the original will remain in Gallery; this is communicated without claiming the file was moved. The user may delete the Gallery original manually.
8. Removed static security notices from the vault screen, but retained essential error messages and a confirmation for permanently deleting a private video.

### Limitations

- A four-digit PIN offers less protection against offline guessing than a longer PIN. The encryption format itself is unchanged, and decrypted playback can still use app cache.
- Android and Media3 may still display an *active foreground media-service notification* while playing in PiP. This patch removes a leftover **paused** notification when headphone mode is off. Completely suppressing active service notifications safely would require changing the Media3 session architecture and Android foreground-service behavior, and is not claimed here.
- PiP depends on the phone supporting PiP and allowing it for Videonic; unsupported devices will pause playback upon Home instead.
- The project has NOT been Android-compiled in this container. Compilation and device testing must be performed in Codespaces before release.
