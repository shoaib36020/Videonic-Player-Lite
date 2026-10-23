from pathlib import Path

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

def require_once(text, label):
    count = source.count(text)
    if count != 1:
        raise SystemExit(
            f'ABORTED: expected exactly one match for {label}; found {count}. '
            'No changes were written.'
        )

def replace_once(old, new, label):
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f'ABORTED: expected exactly one match for {label}; found {count}. '
            'No changes were written.'
        )
    source = source.replace(old, new, 1)
    if old != new:
        changes.append(label)

# Guard against applying this patch to an unrelated/older project revision.
if 'var videoZoom by remember(playing?.id)' not in source:
    raise SystemExit(
        'ABORTED: the existing Videonic pinch-zoom state was not found. '
        'No changes were written. Please send the full terminal output.'
    )

# 1. Keep fullscreen stretching independent from pinch zoom and the aspect-ratio selector.
state_line = '    var isFullscreen by remember { mutableStateOf(false) }'
if 'var fullscreenStretch by remember(playing?.id)' not in source:
    require_once(state_line, 'fullscreen state anchor')
    source = source.replace(
        state_line,
        '    var fullscreenStretch by remember(playing?.id) { mutableStateOf(false) }\n' + state_line,
        1,
    )
    changes.append('added a separate fullscreen-stretch state')

# Clear the temporary fullscreen stretch when leaving the expanded player. This
# prevents a later portrait/miniplayer reopening from inheriting fullscreen stretch.
effect = '''    LaunchedEffect(playerExpanded) {
        if (!playerExpanded) fullscreenStretch=false
    }
'''
if effect not in source:
    insert_anchor = '    var fullscreenStretch by remember(playing?.id) { mutableStateOf(false) }'
    require_once(insert_anchor, 'fullscreen-stretch state')
    source = source.replace(insert_anchor, insert_anchor + '\n' + effect.rstrip() + '\n', 1)
    changes.append('reset fullscreen stretch when leaving expanded playback')

# 2. Fullscreen click explicitly selects FILL; pinch zoom alone must not.
new_resize = 'view.resizeMode=if(fullscreenStretch) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
pinch_resize = 'view.resizeMode=if(pinchZoomActive) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
plain_resize = 'view.resizeMode=when(aspectIndex)'
if new_resize not in source:
    if source.count(pinch_resize) == 1:
        replace_once(pinch_resize, new_resize, 'decouple pinch zoom from FILL resize mode')
    elif source.count(plain_resize) == 1:
        replace_once(plain_resize, new_resize, 'enable FILL resize mode only for fullscreen stretch')
    else:
        raise SystemExit(
            'ABORTED: the expected PlayerView resize-mode block was not found. '
            'No changes were written.'
        )

# 3. Ensure the video surface itself occupies the complete player viewport in
# fullscreen-stretch mode. Some older revisions constrain Original mode to the
# source ratio, so make that constraint conditional on not stretching fullscreen.
if 'val videoFrame=if(fullscreenStretch || aspectIndex!=0) Modifier.fillMaxSize()' not in source:
    old_frame = 'val videoFrame=if(aspectIndex!=0) Modifier.fillMaxSize()'
    if source.count(old_frame) == 1:
        replace_once(
            old_frame,
            'val videoFrame=if(fullscreenStretch || aspectIndex!=0) Modifier.fillMaxSize()',
            'let fullscreen stretch use the full viewport',
        )
    elif 'val videoFrame = Modifier.fillMaxSize()' in source or 'val videoFrame=Modifier.fillMaxSize()' in source:
        # The existing zoom patch already makes the video surface full-size.
        pass
    else:
        raise SystemExit(
            'ABORTED: could not verify the video viewport sizing code. '
            'No changes were written.'
        )

# 4. Tapping Fullscreen enables stretch; tapping Exit fullscreen disables it.
orientation_line = 'forcedPlayerOrientation=if(landscape) "portrait" else "landscape"'
if 'fullscreenStretch=!landscape' not in source:
    replace_once(
        orientation_line,
        'fullscreenStretch=!landscape\n                                ' + orientation_line,
        'toggle fullscreen stretch from the fullscreen control',
    )

# Explicitly selecting an aspect-ratio mode overrides fullscreen stretch, so
# the existing Original/Crop/Stretch/Width control still works as before.
aspect_old = 'aspectIndex=(aspectIndex+1)%4\n                                gestureLabel="Aspect:'
aspect_new = 'fullscreenStretch=false;aspectIndex=(aspectIndex+1)%4\n                                gestureLabel="Aspect:'
if aspect_new not in source:
    replace_once(aspect_old, aspect_new, 'preserve manual aspect-ratio control')

# 5. Make the bottom-row action glyphs slightly smaller without changing their
# circular button/touch-target sizes. Support both not-yet-applied and already-applied versions.
icon_old = 'Icon(icon,label,tint=Color.White,modifier=Modifier.size(if(compact) 21.dp else 25.dp))'
icon_new = 'Icon(icon,label,tint=Color.White,modifier=Modifier.size(if(compact) 19.dp else 22.dp))'
if icon_new not in source:
    replace_once(icon_old, icon_new, 'slightly reduce bottom-row icon size')

if source == original:
    print('ALREADY PATCHED: fullscreen stretch is enabled and the bottom-row icons are already reduced.')
    raise SystemExit(0)

backup = P.with_name(P.name + '.before-fullscreen-stretch-fix')
index = 2
while backup.exists():
    backup = P.with_name(P.name + f'.before-fullscreen-stretch-fix-{index}')
    index += 1
backup.write_text(original)
P.write_text(source)

print('PATCH APPLIED.')
print(f'Backup: {backup}')
print('Changes:')
for item in changes:
    print(f' - {item}')
print('Result: clicking Fullscreen stretches the video to every screen edge without aspect-ratio cropping;')
print('pinch zoom no longer triggers fullscreen stretch by itself; aspect-ratio selection remains available;')
print('bottom-row icon glyphs are 19/22dp instead of 21/25dp, with touch targets unchanged.')
