#!/usr/bin/env python3
"""Videonic: prevent fullscreen/pinch scaling from leaking into portrait or mini-player.

Run from the Videonic-Player-Lite project root. The script is guarded, repeat-safe,
and backs up MainActivity.kt before writing any change.
"""
from pathlib import Path
import sys

ROOT = Path.cwd()
P = ROOT / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
if not P.is_file():
    raise SystemExit(
        'ERROR: MainActivity.kt was not found. Run this script from the '
        'Videonic-Player-Lite project root in Codespaces.'
    )

source = P.read_text()
original = source
changes = []

required = [
    ('fullscreenStretch', 'fullscreen-stretch state'),
    ('var videoZoom by remember(playing?.id)', 'per-video zoom state'),
    ('var videoPanX by remember(playing?.id)', 'horizontal pan state'),
    ('var videoPanY by remember(playing?.id)', 'vertical pan state'),
    ('var pinchZoomActive by remember(playing?.id)', 'pinch-zoom mode state'),
    ('zoomIndicatorVisible', 'zoom indicator state'),
    ('miniSurfaceReady', 'mini-player surface state'),
]
for marker, label in required:
    if marker not in source:
        raise SystemExit(
            f'ABORTED: expected {label} was not found. No changes written. '
            'This patch is for the Videonic source after the pinch-zoom and fullscreen-stretch patches.'
        )

def replace_one(old, new, label):
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f'ABORTED: expected exactly one match for {label}; found {count}. '
            'No changes written.'
        )
    source = source.replace(old, new, 1)
    if old != new:
        changes.append(label)

# 1. Only the temporary fullscreen action may use FILL, and only in landscape.
resize_old = 'view.resizeMode=if(fullscreenStretch) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
resize_new = 'view.resizeMode=if(fullscreenStretch && landscape) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
if resize_old in source:
    replace_one(resize_old, resize_new, 'restrict fullscreen FILL to landscape')
elif resize_new not in source:
    raise SystemExit('ABORTED: expected the fullscreen PlayerView resize-mode line was not found. No changes written.')

# 2. Returning to portrait must reset the entire temporary transform, not only
# the fullscreen flag. This prevents the previous pinch scale/pan/aspect mode
# from making the next portrait view look stretched or zoomed.
orientation_new = '''LaunchedEffect(landscape) {
        isFullscreen=landscape
        if (!landscape) {
            fullscreenStretch=false
            aspectIndex=0
            pinchZoomActive=false
            videoZoom=1f
            videoPanX=0f
            videoPanY=0f
            zoomIndicatorVisible=false
        }
    }'''
orientation_variants = [
    '''LaunchedEffect(landscape) {
        isFullscreen=landscape
        if (!landscape) fullscreenStretch=false
    }''',
    'LaunchedEffect(landscape) { isFullscreen=landscape }',
]
if orientation_new not in source:
    matched = [v for v in orientation_variants if v in source]
    if len(matched) != 1:
        raise SystemExit(
            'ABORTED: the orientation effect did not match a known version. '
            'No changes written.'
        )
    replace_one(matched[0], orientation_new, 'reset fullscreen, aspect, zoom and pan on portrait return')

# 3. Minimizing into the bottom mini-player must clear temporary scaling before
# its PlayerView is attached. The user's next tap reopens a normal-size portrait view.
expanded_old = '''LaunchedEffect(playerExpanded) {
        if (!playerExpanded) fullscreenStretch=false
    }'''
expanded_new = '''LaunchedEffect(playerExpanded) {
        if (!playerExpanded) {
            fullscreenStretch=false
            aspectIndex=0
            pinchZoomActive=false
            videoZoom=1f
            videoPanX=0f
            videoPanY=0f
            zoomIndicatorVisible=false
        }
    }'''
if expanded_new not in source:
    if expanded_old in source:
        replace_one(expanded_old, expanded_new, 'reset temporary scaling when minimizing to mini-player')
    else:
        raise SystemExit('ABORTED: expected mini-player/minimize reset effect was not found. No changes written.')

# 4. Reset immediately on the explicit Exit Fullscreen tap, instead of waiting
# for the Android orientation callback. Entering fullscreen still enables FILL.
click_old = 'fullscreenStretch=!landscape'
click_new = '''if(landscape) {
                                    fullscreenStretch=false
                                    aspectIndex=0
                                    pinchZoomActive=false
                                    videoZoom=1f
                                    videoPanX=0f
                                    videoPanY=0f
                                    zoomIndicatorVisible=false
                                } else {
                                    fullscreenStretch=true
                                }'''
if click_new not in source:
    replace_one(click_old, click_new, 'reset temporary scaling immediately on Exit Fullscreen')

# 5. The mini-player always uses FIT, and explicitly clears any stale view scale
# before requesting a fresh layout. This is intentionally limited to the mini-player.
mini_old = '''update={ view ->
                                                        view.resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        if(view.player !== controller) view.player=controller
                                                    },'''
mini_new = '''update={ view ->
                                                        view.resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        view.scaleX=1f
                                                        view.scaleY=1f
                                                        if(view.player !== controller) view.player=controller
                                                        view.requestLayout()
                                                        view.invalidate()
                                                    },'''
if mini_new not in source:
    replace_one(mini_old, mini_new, 'force mini-player FIT mode and clear stale view scaling')

# 6. Likewise, do not let Android View-level scale values accumulate on the main
# PlayerView. Pinch zoom remains implemented by the Compose graphics layer and is reset above.
main_update_old = '''                                if(view.player !== controller) view.player=controller
                            },
                            onRelease={ view -> view.player=null },
                            modifier=videoFrame.graphicsLayer {'''
main_update_new = '''                                view.scaleX=1f
                                view.scaleY=1f
                                if(view.player !== controller) view.player=controller
                            },
                            onRelease={ view -> view.player=null },
                            modifier=videoFrame.graphicsLayer {'''
if main_update_new not in source:
    replace_one(main_update_old, main_update_new, 'clear stale Android View scaling on main PlayerView')

if source == original:
    print('ALREADY PATCHED: portrait and mini-player scaling resets are already installed.')
    raise SystemExit(0)

backup = P.with_name(P.name + '.before-portrait-miniplayer-scaling-fix')
index = 2
while backup.exists():
    backup = P.with_name(P.name + f'.before-portrait-miniplayer-scaling-fix-{index}')
    index += 1
backup.write_text(original)
P.write_text(source)

print('PATCH APPLIED.')
print(f'Backup: {backup}')
for item in changes:
    print(f' - {item}')
print('Portrait return and mini-player reopening now reset zoom, pan, aspect mode, and temporary stretch.')
print('Landscape fullscreen still uses FILL; the mini-player explicitly remains FIT.')
print('No unrelated UI, playback, or icon-size code was changed.')
