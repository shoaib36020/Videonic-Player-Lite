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

def count_once(text, label, haystack=None):
    target = source if haystack is None else haystack
    count = target.count(text)
    if count != 1:
        raise SystemExit(f'ABORTED: expected one match for {label}; found {count}. No changes written.')

def replace_once(old, new, label):
    global source
    count_once(old, label)
    source = source.replace(old, new, 1)

def replace_region(start_marker, end_marker, replacement, label):
    global source
    start = source.find(start_marker)
    if start < 0:
        raise SystemExit(f'ABORTED: start marker for {label} was not found. No changes written.')
    end = source.find(end_marker, start + len(start_marker))
    if end < 0:
        raise SystemExit(f'ABORTED: end marker for {label} was not found. No changes written.')
    source = source[:start] + replacement + source[end:]

# Confirm this is the file produced by the previous player-zoom patch, rather
# than applying edits to an unrelated/older version of MainActivity.kt.
for marker, label in [
    ('var videoZoom by remember(playing?.id)', 'existing pinch-zoom state'),
    ('val systemBackEdge=32.dp.toPx()', 'Android Back-edge protection'),
    ('Modifier.width(72.dp)', 'previously widened volume/brightness bar'),
    ('val nextZoom=videoZoom*factor', 'existing pinch gesture implementation'),
]:
    if marker not in source:
        raise SystemExit(f'ABORTED: expected {label} marker was not found. No changes written.')

# 1. Restore the original slim volume/brightness panel. Show only the current
# volume level, not "current / max", which was the reason the digits clipped.
replace_once(
    'gestureLabel="Volume ${(volumeLevel*maxVolume).toInt()} / $maxVolume";gestureMode="Volume";gestureTick++',
    'gestureLabel="Volume ${(volumeLevel*maxVolume).toInt()}";gestureMode="Volume";gestureTick++',
    'volume gesture label',
)
replace_once('Modifier.width(72.dp).padding(vertical=7.dp,horizontal=4.dp)',
             'Modifier.width(44.dp).padding(vertical=7.dp,horizontal=4.dp)',
             'original volume/brightness panel width')
replace_once('else "${(level*maxVolume).toInt()} / $maxVolume"',
             'else "${(level*maxVolume).toInt()}"',
             'volume progress number only')

# 2. Add a visible zoom percentage and explicit state for the MX-style mode.
state_anchor = '    var videoPanY by remember(playing?.id) { mutableFloatStateOf(0f) }'
new_state = state_anchor + '''
    // MX-style pinch zoom: 100% fills the whole player view; 200% is the maximum.
    // Zoom/pan state is keyed to the media item, not to fullscreen/miniplayer changes.
    var pinchZoomActive by remember(playing?.id) { mutableStateOf(false) }
    var zoomIndicatorVisible by remember(playing?.id) { mutableStateOf(false) }
    var zoomIndicatorTick by remember(playing?.id) { mutableIntStateOf(0) }
    LaunchedEffect(zoomIndicatorTick) {
        if (zoomIndicatorVisible) {
            delay(900L)
            zoomIndicatorVisible = false
        }
    }'''
replace_once(state_anchor, new_state, 'pinch zoom state anchor')

# 3. Do not reset the selected aspect ratio when the user minimizes and reopens
# the player, or when the display rotates. Reset only when a different video opens.
orientation_start = '    // The phone\'s actual orientation remains authoritative for the fullscreen control.'
orientation_end = '    // Rotation priority:'
orientation_replacement = '''    // The actual display orientation controls the fullscreen icon, but it must not
    // silently change the user's selected aspect ratio when rotating or reopening.
    LaunchedEffect(landscape) { isFullscreen=landscape }
    LaunchedEffect(playing?.id) {
        aspectIndex=0
        videoZoom=1f
        videoPanX=0f
        videoPanY=0f
        pinchZoomActive=false
        zoomIndicatorVisible=false
    }
    LaunchedEffect(playerExpanded, landscape) {
        if (playerExpanded) {
            scrubbing=false
            playerSurfaceEpoch++
        }
    }
'''
replace_region(orientation_start, orientation_end, orientation_replacement, 'orientation/aspect persistence block')

# Remove the two old resets that explicitly changed the aspect mode while
# leaving for the mini-player or reopening it.
replace_once(
    'playerExpanded=false;isFullscreen=false;aspectIndex=0;scrubbing=false;activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED',
    'playerExpanded=false;isFullscreen=false;scrubbing=false;activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED',
    'back-button aspect reset',
)
# Match only the mini-player open callback, not other player-opening flows.
mini_open_old = '''                                    cancelOldSeekRecovery()
                                    playerOpenEpoch++
                                    aspectIndex=0; scrubbing=false; playerSurfaceEpoch++
                                    playerExpanded=true; controlsVisible=true; controlTick++'''
mini_open_new = '''                                    cancelOldSeekRecovery()
                                    playerOpenEpoch++
                                    scrubbing=false; playerSurfaceEpoch++
                                    playerExpanded=true; controlsVisible=true; controlTick++'''
replace_once(mini_open_old, mini_open_new, 'mini-player aspect reset')

# 4. Make the pinch mode fill the player exactly at 100%, allow zoom-out, and
# cap zoom-in at 200%. Keep two-finger pan inside the extra image area.
old_pinch_update = '''if(factor.isFinite() && factor>0f && nextZoom.isFinite() && nextZoom>0f) {
                                                                videoZoom=nextZoom
                                                            }
                                                        }
                                                        val nextPanX=videoPanX+(currentCenterX-previousCenterX)
                                                        val nextPanY=videoPanY+(currentCenterY-previousCenterY)
                                                        if(nextPanX.isFinite()) videoPanX=nextPanX
                                                        if(nextPanY.isFinite()) videoPanY=nextPanY'''
new_pinch_update = '''if(factor.isFinite() && factor>0f && nextZoom.isFinite() && nextZoom>0f) {
                                                                videoZoom=nextZoom.coerceIn(0.25f,2f)
                                                                pinchZoomActive=true
                                                                zoomIndicatorVisible=true
                                                                zoomIndicatorTick++
                                                            }
                                                        }
                                                        val maxPanX=(size.width.toFloat()*(videoZoom-1f)/2f).coerceAtLeast(0f)
                                                        val maxPanY=(size.height.toFloat()*(videoZoom-1f)/2f).coerceAtLeast(0f)
                                                        val nextPanX=videoPanX+(currentCenterX-previousCenterX)
                                                        val nextPanY=videoPanY+(currentCenterY-previousCenterY)
                                                        if(nextPanX.isFinite()) videoPanX=nextPanX.coerceIn(-maxPanX,maxPanX)
                                                        if(nextPanY.isFinite()) videoPanY=nextPanY.coerceIn(-maxPanY,maxPanY)'''
replace_once(old_pinch_update, new_pinch_update, 'bounded zoom and clamped two-finger pan')

# In pinch mode, FILL means 100% is exactly edge-to-edge; the graphics-layer
# scale then gives 25%-200% zoom without a jump in the selected aspect setting.
player_section = source.find('if(playerExpanded && playing!=null)')
resize_anchor = 'view.resizeMode=when(aspectIndex)'
resize_pos = source.find(resize_anchor, player_section)
if player_section < 0 or resize_pos < 0:
    raise SystemExit('ABORTED: expanded PlayerView resize-mode block was not found. No changes written.')
source = source[:resize_pos] + source[resize_pos:].replace(
    resize_anchor,
    'view.resizeMode=if(pinchZoomActive) AspectRatioFrameLayout.RESIZE_MODE_FILL else when(aspectIndex)',
    1,
)

# The player root must clip zoomed content at the display edges instead of
# letting the scaled TextureView draw beyond its window bounds.
replace_once('Box(Modifier.fillMaxSize().background(Color.Black))',
             'Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black))',
             'player viewport clipping')
if 'import androidx.compose.ui.draw.clipToBounds' not in source:
    package_end = source.find('\n')
    if package_end < 0:
        raise SystemExit('ABORTED: Kotlin package line not found. No changes written.')
    source = source[:package_end + 1] + '\nimport androidx.compose.ui.draw.clipToBounds\n' + source[package_end + 1:]

# Tapping the aspect-ratio button exits temporary pinch mode cleanly; the chosen
# mode then remains stable when entering/leaving the mini-player.
replace_once(
    'aspectIndex=(aspectIndex+1)%4\n                                gestureLabel="Aspect:',
    'pinchZoomActive=false;videoZoom=1f;videoPanX=0f;videoPanY=0f\n                                aspectIndex=(aspectIndex+1)%4\n                                gestureLabel="Aspect:',
    'aspect-ratio button pinch reset',
)

# 5. Show the percentage briefly while the user pinches.
zoom_ui_anchor = '                if(!inPip && holdSpeedActive) {'
zoom_ui = '''                if(!inPip && zoomIndicatorVisible) {
                    Surface(
                        modifier=Modifier.align(Alignment.Center),
                        shape=RoundedCornerShape(50),
                        color=Color(0xE91A2030),
                        border=BorderStroke(.8.dp,Color.White.copy(alpha=.28f)),
                        shadowElevation=5.dp
                    ) {
                        Text(
                            "${((videoZoom*100f)+0.5f).toInt()}%",
                            modifier=Modifier.padding(horizontal=20.dp,vertical=10.dp),
                            color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold
                        )
                    }
                }
'''
replace_once(zoom_ui_anchor, zoom_ui + zoom_ui_anchor, 'zoom percentage overlay insertion point')

# 6. Clip ripple/pressed-state effects to the round button shape. The large
# center play/pause control uses combinedClickable on the Surface modifier, so
# its ripple must be clipped explicitly (Surface shape alone does not clip it).
replace_once(
    'Modifier.size(if(landscape) 62.dp else 72.dp)\n                                .combinedClickable(',
    'Modifier.size(if(landscape) 62.dp else 72.dp).clip(RoundedCornerShape(100))\n                                .combinedClickable(',
    'center play/pause circular ripple',
)

# The top playback toolbar uses 48dp icon buttons; clip their indications too.
if source.count('modifier=Modifier.size(48.dp)') != 4:
    raise SystemExit('ABORTED: expected four 48dp playback-toolbar buttons. No changes written.')
source = source.replace('modifier=Modifier.size(48.dp)',
                        'modifier=Modifier.size(48.dp).clip(RoundedCornerShape(50))')

# Round player-action helper buttons and the mini-player controls.
helper_start = source.find('private fun PlayerCircleControl(')
helper_end = source.find('private fun VideoThumb(', helper_start)
if helper_start < 0 or helper_end < 0:
    raise SystemExit('ABORTED: player circular-control helpers were not found. No changes written.')
helper = source[helper_start:helper_end]
if helper.count('IconButton(onClick=onClick) {') != 2:
    raise SystemExit('ABORTED: expected two IconButtons in the circular-control helpers. No changes written.')
helper = helper.replace('IconButton(onClick=onClick) {',
                        'IconButton(onClick=onClick,modifier=Modifier.clip(RoundedCornerShape(50))) {')
source = source[:helper_start] + helper + source[helper_end:]
replace_once(
    'IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play()}){Icon(if(playingNow)Icons.Default.Pause else Icons.Default.PlayArrow,"Play pause",tint=Accent)}',
    'IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play()},modifier=Modifier.clip(RoundedCornerShape(50))){Icon(if(playingNow)Icons.Default.Pause else Icons.Default.PlayArrow,"Play pause",tint=Accent)}',
    'mini-player play/pause ripple',
)
replace_once(
    'IconButton(onClick={closePlayer()}){Icon(Icons.Default.Close,"Close player")}',
    'IconButton(onClick={closePlayer()},modifier=Modifier.clip(RoundedCornerShape(50))){Icon(Icons.Default.Close,"Close player")}',
    'mini-player close ripple',
)

if source == original:
    raise SystemExit('ABORTED: no source changes were made.')

backup = P.with_name(P.name + '.before-mx-zoom-fix')
if backup.exists():
    backup = P.with_name(P.name + '.before-mx-zoom-fix-2')
backup.write_text(original)
P.write_text(source)
print('PATCH APPLIED.')
print(f'Backup: {backup}')
print('Applied: original slim volume/brightness panel; current volume number only;')
print('aspect-ratio persistence across mini-player/rotation; 25%-200% pinch zoom;')
print('100% edge-to-edge FILL mode; temporary zoom percentage; bounded pan;')
print('and clipped circular playback-button ripple effects.')
