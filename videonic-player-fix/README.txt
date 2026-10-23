Videonic Player Lite — current-source patch (2026-10-17)
Based on the supplied 989-line MainActivity.kt.

Included:
- Top player bar uses status bar inset + minimum 48dp targets, simplified actions
- Landscape bottom controls use compact icon-only layout
- Aspect choices Original, Fit, Crop, Stretch; Original/Fit both preserve native aspect on fit-to-screen (Media3)
- Shuffle appears only in overflow menu; repeat available there
- Separate Volume overflow item removed; swipe overlay remains
- Opt-in Headphone background mode. Default background playback paused on Activity onStop outside PiP
- Grid adaptive columns, sort changes reset list to top

NOT INCLUDED: MediaStore trash/restore, encrypted private storage, multi-select,
feedback server, Google Play policy pages, actual HW/SW manual decoder switch.
These require separate implementation and verification.

How to apply:
  unzip -o videonic-player-layout-fix.zip -d .
  bash videonic-player-fix/APPLY.sh
Then compileDebugKotlin and report result; do NOT install yet.

This patch has NOT been compiled in Android SDK environment by the assistant.
