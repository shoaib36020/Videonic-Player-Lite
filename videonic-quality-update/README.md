# Videonic Lite — Player & Collections Quality Update

This package updates the **existing Codespaces Android project**, not the Hostinger feedback server.

## Changes

- More playback menu keeps the controls visible after choosing an action. Track and decoder popups no longer cause an immediate hidden toolbar.
- New audio/subtitle chooser with descriptive track cards, language/format details, selected-track radio indicator, unsupported tracks disabled, and Subtitles Off.
- PiP: checks device support and active media, reports failure, requests a video-based aspect ratio. Manifest now handles more PiP configuration changes without Activity recreation.
- Me: removes duplicate Theme destination; appearance remains under Settings.
- Private Folder: redesigned lock/unlock, empty-state, encrypted video cards, import/play/export/delete, and safety explanations. Encryption behavior is unchanged.
- Recycle Bin: modern summary header, refresh button, empty/error/loading UI, restore confirmation. Operates on Android MediaStore, not another gallery app's proprietary bin.
- Preserves existing feedback UI and your Hostinger URL.

## Installation

1. Upload `videonic-quality-update.zip` to your repository root in Codespaces.
2. `unzip -o videonic-quality-update.zip -d .`
3. `python3 videonic-quality-update/APPLY.py`
4. Run `gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process`

The script refuses to modify your project if it cannot recognize the latest major + secure feedback source. Backups have suffix `.before-quality-<timestamp>`.

## Validation note

The Python patch has been exercised against a reconstructed version of the source you uploaded. **Android/Kotlin compilation has not been performed** in the packaging environment. Compile in Codespaces first. Test real PiP on an Android device: OEMs may restrict PiP and streaming playback behavior varies.

The three-dot menu itself closes after an action (normal Android menu behavior); the *player toolbar* should remain visible and track-related dialogs open as expected.
