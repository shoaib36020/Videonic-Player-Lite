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

if 'fullscreenStretch' not in source:
    raise SystemExit(
        'ABORTED: fullscreenStretch state was not found. No changes were written. '
        'This patch expects the previous fullscreen-stretch patch to be applied.'
    )

# 1. The explicit fullscreen stretch must only be active in actual landscape.
old_resize = 'view.resizeMode=if(fullscreenStretch) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
new_resize = 'view.resizeMode=if(fullscreenStretch && landscape) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)'
if old_resize in source:
    if source.count(old_resize) != 1:
        raise SystemExit('ABORTED: multiple fullscreen resize-mode lines found. No changes were written.')
    source = source.replace(old_resize, new_resize, 1)
    changes.append('limit fullscreen FILL to landscape orientation')
elif new_resize not in source:
    raise SystemExit(
        'ABORTED: expected fullscreen resize-mode line was not found. No changes were written. '
        'Please send the full terminal output so I can match your current source safely.'
    )

# 2. If an older revision also sizes the video frame based on fullscreenStretch,
# gate that sizing condition the same way. A frame that is already fillMaxSize is fine:
# RESIZE_MODE_FIT preserves the source aspect ratio in portrait.
old_frame = 'val videoFrame=if(fullscreenStretch || aspectIndex!=0) Modifier.fillMaxSize()'
new_frame = 'val videoFrame=if((fullscreenStretch && landscape) || aspectIndex!=0) Modifier.fillMaxSize()'
if old_frame in source:
    if source.count(old_frame) != 1:
        raise SystemExit('ABORTED: multiple fullscreen video-frame conditions found. No changes were written.')
    source = source.replace(old_frame, new_frame, 1)
    changes.append('limit fullscreen frame sizing to landscape')
elif new_frame not in source:
    # Newer zoom code may already use a full-size video frame; do not alter it.
    if 'val videoFrame=Modifier.fillMaxSize()' not in source and 'val videoFrame = Modifier.fillMaxSize()' not in source:
        if 'val videoFrame' in source:
            raise SystemExit(
                'ABORTED: unrecognized videoFrame expression. No changes were written. '
                'Please send the full terminal output.'
            )

# 3. Clear the temporary fullscreen flag when the real display orientation returns to portrait.
old_effect = 'LaunchedEffect(landscape) { isFullscreen=landscape }'
new_effect = '''LaunchedEffect(landscape) {
        isFullscreen=landscape
        if (!landscape) fullscreenStretch=false
    }'''
if old_effect in source:
    if source.count(old_effect) != 1:
        raise SystemExit('ABORTED: multiple orientation effects found. No changes were written.')
    source = source.replace(old_effect, new_effect, 1)
    changes.append('clear temporary fullscreen stretch when returning to portrait')
elif 'if (!landscape) fullscreenStretch=false' not in source:
    raise SystemExit(
        'ABORTED: expected orientation effect was not found. No changes were written. '
        'Please send the full terminal output.'
    )

if source == original:
    print('ALREADY PATCHED: fullscreen stretching is already restricted to landscape.')
    raise SystemExit(0)

backup = P.with_name(P.name + '.before-portrait-stretch-guard')
index = 2
while backup.exists():
    backup = P.with_name(P.name + f'.before-portrait-stretch-guard-{index}')
    index += 1
backup.write_text(original)
P.write_text(source)

print('PATCH APPLIED.')
print(f'Backup: {backup}')
for item in changes:
    print(f' - {item}')
print('Portrait playback keeps the selected aspect ratio; fullscreen FILL applies only in landscape.')
print('Pinch zoom and bottom-row icon sizes are otherwise unchanged.')
