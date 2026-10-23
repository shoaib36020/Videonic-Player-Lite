# Videonic Lite v16 — Player rotation, gestures, UI & native 10-bit fallback

This patch targets **the successfully compiled v15 source after removing the erroneous `awaitPointerEventScope` import**. It changes the player in `MainActivity.kt`, adds a separate `VideonicSoftwarePlayerActivity.kt` for optional native software playback, and inserts one line in `app/build.gradle.kts` and one activity entry in `AndroidManifest.xml`. It does **not** touch Media3 PlaybackService, saved videos, playlists, favorites, history data, feedback, vault/PIN/encryption, the package name, or other screens.

## Changes

- **Auto-rotation / fullscreen**: The icon reflects actual landscape vs portrait orientation. The fullscreen button rotates to landscape or back to portrait. Re-entering the player reapplies the auto-rotate preference so returning to the library doesn't leave rotation stale.
- **Hold-speed selector**: Hold on the *video picture* for ~340 ms; the speed bar appears beneath the header with 0.5×, 1×, 1.5×, 2×, 3×, 4× positions. Move your finger horizontally while holding to change the temporary speed. Releasing restores your previous speed. The ordinary Speed button remains independent.
- **Seeking**: One gesture handler, a single seek on release, and proportional seeking distances based on the video duration. On videos whose extractor exposes no seek points, the player displays an honest non-seekable message instead of pretending to seek. For difficult files, try the new native fallback.
- **Aspect ratio**: Tapping the aspect button cycles Original → Crop → Stretch → Width, with no modal list.
- **Brightness/volume gestures**: Complete vertical indicators are narrower (44 dp outer vs previous 70 dp), shorter (58 dp track vs 96 dp), and have reduced padding/icons/text. Brightness remains left, volume right.
- **Light-theme player menu**: Explicit white menu text and icons over the dark playback popup surface.
- **Built-in software video decoder**: Player ⋮ → **Try 10-bit software playback**, or the **Try software decoder** button after a Media3 playback error, opens a native LibVLC-based player inside the app. It reads the same video URI, starts near the current playback position, provides play/pause, ±10 s, seek bar (when seekable), and saves position to the existing LocalLibrary. The original Media3 player pauses; no video is copied or deleted. Press Back to return. Native fallback currently uses a simpler playback screen and does not yet implement the primary player's notification/headphone/PiP controls.

## Important limitations

**No application can guarantee every video codec, 10-bit profile, resolution, or seek operation on every Android device.** Media3 uses Android's device decoders; LibVLC adds its own software decode capability (including many HEVC Main10/10-bit files), but high bitrate/4K 10-bit may stutter or fail if the CPU is too slow. Some damaged files and files without seek indices cannot seek reliably even in LibVLC. If a video still fails, share its codec, profile, resolution, file size and exact error.

The new dependency is `org.videolan.android:libvlc-all:3.6.5` from Maven Central (~83 MB AAR). The first Codespaces build must download it; the APK will be substantially larger. This library is licensed under **LGPL-2.1**. Before distributing the final app, include the required third-party license, attributions, source/relocation information, and examine Google Play native-library compatibility.

VideoLAN library source: https://code.videolan.org/videolan/libvlcjni
License: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html

## Installation in Codespaces

1. Upload `videonic-v16-player-upgrade.zip` to `/workspaces/Videonic-Player-Lite`.
2. Run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-player-upgrade.zip -d .
python3 videonic-v16-player-upgrade/APPLY.py
```

Installer creates verified backups in `videonic-ui-backups/`, verifies the expected v15 source hash, supports repeat application, and stops safely if local source differs. Don't force-overwrite files on `SAFE STOP`.

3. **Compile first** (this Android build has *not* been executed in this environment):

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

4. Only after `BUILD SUCCESSFUL`, build with `:app:assembleDebug` instead. APK: `app/build/outputs/apk/debug/app-debug.apk`. Install over the existing app; do not uninstall or clear app data.

5. Device-test: normal H.264 720p → rotation and exit-fullscreen → close and reopen, rotate again → long stationary hold then horizontal move → aspect cycling → brightness left, volume right → seek in a normal indexed MP4 → HEVC 10-bit using the **built-in software fallback**. Test the error recovery, both light and dark themes, and headphone/PiP in the primary Media3 player.

**Verification performed**: inspected the v15 file versions, syntax-only Kotlin analysis without Android classpath, Python installer checks (backups, repeat, conflicts), package consistency. **Not yet verified**: full Android/Kotlin compilation with Media3/LibVLC dependencies, size on device, and actual 10-bit hardware/CPU decoding. Report compiler `e:` lines if it fails; do not install an uncompiled APK.
