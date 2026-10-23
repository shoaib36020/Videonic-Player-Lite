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

# Fix 1: pinch zoom must not force RESIZE_MODE_FILL. Keep the user's chosen
# aspect-ratio mode so a portrait video enlarges proportionally instead of
# being stretched to the full portrait viewport.
old_resize = 'view.resizeMode=if(pinchZoomActive) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex) {'
new_resize = 'view.resizeMode=when(aspectIndex) {'
resize_count = source.count(old_resize)
if resize_count != 1:
    raise SystemExit(
        'ABORTED: expected exactly one forced-FILL resize-mode line from the '
        f'previous zoom patch; found {resize_count}. No changes written.'
    )
source = source.replace(old_resize, new_resize, 1)

# Fix 2: slightly reduce only the four bottom-row action icons (Lock, Speed,
# Aspect Ratio, Fullscreen). Keep their touch targets and layout unchanged.
old_icons = 'Icon(icon,label,tint=Color.White,modifier=Modifier.size(if(compact) 21.dp else 25.dp))'
new_icons = 'Icon(icon,label,tint=Color.White,modifier=Modifier.size(if(compact) 19.dp else 22.dp))'
icon_count = source.count(old_icons)
if icon_count != 1:
    raise SystemExit(
        'ABORTED: expected exactly one PlayerAction icon-size definition; '
        f'found {icon_count}. No changes written.'
    )
source = source.replace(old_icons, new_icons, 1)

if source == original:
    raise SystemExit('ABORTED: no source changes were made.')

backup = P.with_name(P.name + '.before-portrait-zoom-icon-fix')
index = 2
while backup.exists():
    backup = P.with_name(P.name + f'.before-portrait-zoom-icon-fix-{index}')
    index += 1
backup.write_text(original)
P.write_text(source)

print('PATCH APPLIED.')
print(f'Backup: {backup}')
print('Fixed: pinch zoom no longer forces FILL/stretch mode.')
print('Adjusted: bottom-row action icons 21/25dp -> 19/22dp; touch targets unchanged.')
