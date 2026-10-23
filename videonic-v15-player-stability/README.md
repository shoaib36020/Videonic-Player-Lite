# Videonic Lite v15 — Player stability and referenced player UI

This update expects **v14 plus the successful v14.1 Material3 opt-in fix**. Replaces exactly two Kotlin files, `MainActivity.kt` and `VideonicPlaybackService.kt`. It **does not** change `LocalLibrary`, the database/preferences, PIN or encrypted videos, VideoRepository, Feedback, Android manifest, package name, or Gradle dependencies.

## Implemented

1. **Decoder recovery**: previous v14 service released the working player before the new renderer was ready. v15 keeps the same MediaSession and old player, stops the old decoder to free hardware capacity, prepares the replacement *paused*, swaps the session only on `STATE_READY`, and resumes playback. Failure or 15-second timeout releases the replacement and resumes the old session at the saved position. The command succeeds only after a usable replacement reaches ready state. This is a significant change and requires device validation.
2. **Seeking**: replaces the three competing full-screen gesture recognizers with one touch handler. A horizontal drag previews target time and commits a single seek when released, while the timeline slider likewise seeks only after lifting the finger. Double-tap jumps according to the user's chosen seek duration. Left/right vertical gestures still adjust brightness/volume. The long-press boost can still trigger after a held finger moves; completely stationary hold detection is not implemented in this version. Long-press boost remains configurable; speed is always selectable from the Speed button.
3. **Error recovery**: errors show Retry automatic and Open in another player rather than silently stopping. Retry automatic switches to the default decoder priority. Media3's decoder fallback setting remains supported.
4. **Player UI**: dark rounded, selected-row-highlighted **Speed, Audio Tracks, Subtitles, Decoder, Decoder Details, Aspect Ratio** sheets, matching the requested references as closely as possible without changing the Media3 playback architecture. Decoder sheets were already added in v14; v15 fixes the surrounding sheets. Locked-screen overlay gets a top pill and a large circular unlock button. Transport controls and the four bottom actions have rounded translucent circular surfaces. More menu uses a dark surface. Volume/brightness vertical fill indicators reduced to 96dp.
5. **Audio/subtitles**: select a real embedded audio/subtitle stream. Audio can be disabled, subtitles can be off. `Add subtitle file` uses Android's picker for SRT/VTT/ASS and attaches the chosen subtitle URI to the current MediaItem while attempting to retain playback position.
6. **No fabricated codec support**: Android Media3 ExoPlayer uses Android's platform video decoders. A 10-bit HEVC/AV1 file needs a compatible profile/hardware/software video decoder. The three codec-priority modes alone cannot add missing codec support. If a video cannot be decoded, use the `Other player` action or test a separate native software decoder backend (e.g. LibVLC) in a dedicated future integration. No Android app can guarantee *every* video format/resolution on every phone.

## Install (in Codespaces)

1. Upload `videonic-v15-player-stability.zip` to `/workspaces/Videonic-Player-Lite`.
2. Run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v15-player-stability.zip -d .
python3 videonic-v15-player-stability/APPLY.py
```

If `SAFE STOP` appears, **do not force**. Upload your `MainActivity.kt` and `VideonicPlaybackService.kt` for a version-aware patch.

## Compile (before APK)

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

On successful compilation replace `:app:compileDebugKotlin` with `:app:assembleDebug` to build `app/build/outputs/apk/debug/app-debug.apk`.

**Device test**: Start with one ordinary MP4 you know plays. Seek back/forward by swipe and slider, test all player sheets. Switch Automatic -> Hardware -> Software while playing and confirm state/position. Test an HEVC 10-bit sample separately and report its codec/resolution and any error text. Test lock, PiP, media notification, headphones, and Recent resume. Avoid testing with an important private or irreplaceable video.

**Verification**: ZIP and installer checks and Kotlin syntax parsing are run outside Codespaces. **A complete Android Gradle build and device-level decoding test are not possible here.** Treat API incompatibilities reported by Gradle as real issues to be fixed from the compiler output.
