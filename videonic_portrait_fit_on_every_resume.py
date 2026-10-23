#!/usr/bin/env python3
"""Videonic: hard-guard portrait FIT on normal playback and mini-player resume.

Run from the Videonic-Player-Lite project root. This is a targeted follow-up to
prior fullscreen/zoom patches. It preserves landscape aspect-ratio choices and
only allows the fullscreen-stretch flag to select FILL in landscape.
"""
from pathlib import Path
import re
import sys

P = Path.cwd() / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
if not P.is_file():
    raise SystemExit('ERROR: MainActivity.kt was not found. Run this script from the Videonic-Player-Lite project root.')

source = P.read_text()
original = source
changes = []

required = [
    ('var fullscreenStretch by remember(playing?.id)', 'fullscreen-stretch state'),
    ('var aspectIndex by remember', 'aspect-ratio state'),
    ('val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE', 'orientation state'),
    ('playerSurfaceEpoch++', 'player-surface recreation state'),
    ('video.resizeMode' if False else 'AspectRatioFrameLayout.RESIZE_MODE_FIT', 'FIT resize mode'),
]
for marker, label in required:
    if marker not in source:
        raise SystemExit(f'ABORTED: expected {label} was not found. No changes written.')

# Restrict the replacement to the expanded PlayerView, not the mini-player's own FIT block.
player_start = source.find('if(playerExpanded && playing!=null)')
if player_start < 0:
    raise SystemExit('ABORTED: expanded player section was not found. No changes written.')
resize_start = source.find('view.resizeMode=', player_start)
if resize_start < 0:
    raise SystemExit('ABORTED: expanded PlayerView resize mode was not found. No changes written.')
resize_end = source.find('\n                                }', resize_start)
# The first matching close after the resize expression should close when(aspectIndex).
if resize_end < 0:
    raise SystemExit('ABORTED: could not locate the end of the expanded PlayerView resize mode. No changes written.')
resize_end += len('\n                                }')
old_resize = source[resize_start:resize_end]
new_resize = '''view.resizeMode=if(!landscape) AspectRatioFrameLayout.RESIZE_MODE_FIT else if(fullscreenStretch) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex) {
                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }'''
# Avoid accidental partial replacement: accept only known variants of the four-mode selector.
known = [
    'view.resizeMode=if(fullscreenStretch && landscape) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex) {\n                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM\n                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL\n                                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH\n                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT\n                                }',
    'view.resizeMode=if(fullscreenStretch) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex) {\n                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM\n                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL\n                                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH\n                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT\n                                }',
    'view.resizeMode=when(aspectIndex) {\n                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM\n                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL\n                                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH\n                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT\n                                }',
    'view.resizeMode=if(pinchZoomActive) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex) {\n                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM\n                                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL\n                                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH\n                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT\n                                }',
]
if old_resize != new_resize:
    if old_resize not in known:
        # Accept harmless indentation variations, but require the expected mode branches.
        normalized = re.sub(r'\s+', ' ', old_resize).strip()
        normalized_known = [re.sub(r'\s+', ' ', x).strip() for x in known]
        if normalized not in normalized_known:
            raise SystemExit('ABORTED: expanded PlayerView resize block did not match a known version. No changes written.')
    source = source[:resize_start] + new_resize + source[resize_end:]
    changes.append('force portrait PlayerView to FIT regardless of a stale aspectIndex')

# Reset temporary zoom/aspect state whenever the expanded player is opened in portrait.
# Keep any existing surface recreation/scrubbing logic intact.
effect_start = source.find('LaunchedEffect(playerExpanded, landscape)')
if effect_start < 0:
    raise SystemExit('ABORTED: playerExpanded/landscape effect was not found. No changes written.')
effect_end = source.find('// Rotation priority:', effect_start)
if effect_end < 0:
    raise SystemExit('ABORTED: rotation-priority boundary was not found. No changes written.')
effect_block = source[effect_start:effect_end]
reset = '''if (!landscape) {
                fullscreenStretch=false
                aspectIndex=0
                pinchZoomActive=false
                videoZoom=1f
                videoPanX=0f
                videoPanY=0f
                zoomIndicatorVisible=false
            }
            '''
if 'fullscreenStretch=false' not in effect_block or 'videoZoom=1f' not in effect_block:
    open_anchor = 'if (playerExpanded) {'
    if effect_block.count(open_anchor) != 1:
        raise SystemExit('ABORTED: could not safely locate the expanded-player open branch. No changes written.')
    effect_block = effect_block.replace(open_anchor, open_anchor + '\n            ' + reset.rstrip(), 1)
    source = source[:effect_start] + effect_block + source[effect_end:]
    changes.append('reset stale stretch/zoom/pan/aspect state on every portrait reopen')

# Make sure the mini-player always restores FIT even if it is recreated from a prior transformed view.
mini_start = source.find('if(miniSurfaceReady && controller!=null)')
if mini_start >= 0:
    mini_end = source.find('modifier=Modifier.fillMaxSize()', mini_start)
    mini_block = source[mini_start:mini_end if mini_end >= 0 else len(source)]
    if 'view.resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT' not in mini_block:
        raise SystemExit('ABORTED: mini-player FIT mode could not be verified. No changes written.')

if source == original:
    print('ALREADY PATCHED: portrait is guarded to FIT and portrait reopen resets are installed.')
    raise SystemExit(0)

backup = P.with_name(P.name + '.before-portrait-fit-every-resume')
i = 2
while backup.exists():
    backup = P.with_name(P.name + f'.before-portrait-fit-every-resume-{i}')
    i += 1
backup.write_text(original)
P.write_text(source)
print('PATCH APPLIED.')
print(f'Backup: {backup}')
for change in changes:
    print(f' - {change}')
print('Portrait normal playback and mini-player resume are forced to FIT; landscape fullscreen stretch remains available.')
