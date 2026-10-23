# Videonic Lite v14 — Decoder settings and player UI

Updates **only** `MainActivity.kt` and `VideonicPlaybackService.kt`. Includes the previously shared v13 Recycle Bin, Recent progress, and Favorites fixes. No changes to `PrivateVault`, Private Folder, Feedback, app manifest, or saved library data.

## Included

1. **Settings → Playback → Decoder**: three actual Media3 codec-priority modes: **Automatic** (recommended), **Hardware preferred**, **Software preferred**. You can enable/disable automatic decoder fallback. These preferences are persisted for future playback sessions.
2. **Player → three dots → Decoder**: matching dark rounded selection bottom sheet. A selection sends a Media3 **session command** to the player-owning service. The service creates a renderer with the requested `MediaCodecSelector`, swaps the same MediaSession's player, and restores the playlist, current position, repeat, shuffle, speed, volume, and play/pause state.
3. **Player → three dots → Decoder details**: matching dark details sheet shows the selected preference, last codec name reported by Media3's actual `AnalyticsListener` (if available), selected audio/video format, and video dimensions. Never fabricate hardware/software or codec names.
4. **Gesture volume and brightness displays**: reduce vertical bars from **190dp to 118dp** and tighten the pill layout. The swipe level calculations and the Settings level toggles remain unchanged.
5. Back navigation from Decoder returns to Settings.

**Deliberate scope:** The reference's MX Player-specific “HW+”, per-codec toggles, core count, calibration, and arbitrary color format aren't exposed by this Media3 build. Do not interpret “Hardware preferred” as hardware guaranteed; Media3 can still choose another compatible codec depending on the file and device, especially with fallback enabled.

## Install

Upload `videonic-v14-decoder-player.zip` to `/workspaces/Videonic-Player-Lite` in Codespaces and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v14-decoder-player.zip -d .
python3 videonic-v14-decoder-player/APPLY.py
```

If the installer reports `SAFE STOP`, don't overwrite the project: share the exact message and the current Kotlin file.

## Compile

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

For a test APK, replace `:app:compileDebugKotlin` with `:app:assembleDebug`. Result: `app/build/outputs/apk/debug/app-debug.apk`.

## Live test plan

- Settings → Decoder: Auto/HW/SW reflect the selected value; fallback toggle stays saved. Return and reopen.
- Open a video, advance to around 00:30, and select **Software preferred** in the player's three-dot menu. Verify it remains at about 00:30 rather than restarting, and check audio, video, speed, and pause/resume.
- Open Decoder details: the codec name should appear *only after Android reports one*; unknown fields remain clearly labeled.
- Test hardware selection and automatic selection with one H.264 and (if available) an HEVC/4K video; device support varies.
- Swipe vertically on left and right player halves: see shorter brightness/volume indicators; full range must still work.
- Verify Recent resume, PiP, headphone mode, and notifications are unaffected.

## Verification limits

ZIP contents, guard checks, installer rollback, and **Kotlin PSI syntax** are verified in the file-building environment. An Android SDK / Codespaces Gradle environment is **not** available there, so **full Android compilation and actual Media3 session playback behavior have not been tested**. Do not uninstall your existing app; install over it with the same application ID/signing key once compilation succeeds.
