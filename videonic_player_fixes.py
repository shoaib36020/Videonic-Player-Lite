from pathlib import Path
import textwrap

p = Path('/workspaces/Videonic-Player-Lite/app/src/main/java/com/videonic/lite/MainActivity.kt')
if not p.is_file():
    raise SystemExit(f'ERROR: Source file not found: {p}')
s = p.read_text()
original = s

def require_once(haystack, needle, label):
    n = haystack.count(needle)
    if n != 1:
        raise SystemExit(f'ABORTED: expected exactly one match for {label}; found {n}. No changes written.')

# Add graphicsLayer import.
if 'import androidx.compose.ui.graphics.graphicsLayer' not in s:
    if not s.startswith('package '):
        raise SystemExit('ABORTED: Kotlin package declaration was not found.')
    first_nl = s.index('\n')
    s = s[:first_nl + 1] + '\nimport androidx.compose.ui.graphics.graphicsLayer\n' + s[first_nl + 1:]

# Add independent, per-video zoom and pan state.
state_anchor = '    var aspectIndex by remember { mutableIntStateOf(0) }'
require_once(s, state_anchor, 'aspectIndex state anchor')
if 'var videoZoom by remember(playing?.id)' not in s:
    s = s.replace(state_anchor, state_anchor + '''
    // Pinch zoom and two-finger panning are independent of aspect-ratio mode.
    var videoZoom by remember(playing?.id) { mutableFloatStateOf(1f) }
    var videoPanX by remember(playing?.id) { mutableFloatStateOf(0f) }
    var videoPanY by remember(playing?.id) { mutableFloatStateOf(0f) }''', 1)

# Preserve selected mode across rotations; start landscape playback in Crop mode.
landscape_line = '    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE'
require_once(s, landscape_line, 'landscape state line')
landscape_end = s.index(landscape_line) + len(landscape_line)
rotation_marker = '    // Rotation priority:'
rot_start = s.find(rotation_marker, landscape_end)
if rot_start < 0:
    raise SystemExit('ABORTED: rotation-priority section not found. No changes written.')
orientation_block = '''
    // The phone's actual orientation remains authoritative for the fullscreen control.
    LaunchedEffect(landscape) { isFullscreen=landscape }

    // Start landscape playback in Crop mode so the video can reach both side edges.
    // Rotation recreates the output surface without resetting a deliberate aspect choice.
    LaunchedEffect(playerExpanded) {
        if (playerExpanded) {
            aspectIndex = if (landscape) 1 else 0
            scrubbing = false
            videoZoom = 1f
            videoPanX = 0f
            videoPanY = 0f
        }
    }
    LaunchedEffect(playerExpanded, landscape) {
        if (playerExpanded) {
            if (landscape && aspectIndex == 0) aspectIndex = 1
            playerSurfaceEpoch++
        }
    }
'''
s = s[:landscape_end] + orientation_block + s[rot_start:]

# Keep the player immersive and edge-to-edge in portrait AND landscape.
immersive_start = s.find('    // Use Android\'s immersive video mode;')
audio_marker = '    val audioManager = remember'
immersive_end = s.find(audio_marker, immersive_start)
if immersive_start < 0 or immersive_end < 0:
    raise SystemExit('ABORTED: immersive/system-bars section not found. No changes written.')
immersive_block = '''    // Keep the player edge-to-edge and immersive in portrait and landscape.
    // Android can still reveal the notification shade through a deliberate system gesture.
    DisposableEffect(playerExpanded, inPip) {
        val window = activity.window
        val decor = window.decorView
        val insets = androidx.core.view.WindowCompat.getInsetsController(window, decor)
        val immersive = playerExpanded && !inPip
        var effectActive = immersive
        val fullscreenFlag = android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        val wasFullscreen = (window.attributes.flags and fullscreenFlag) != 0
        val previousCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            window.attributes.layoutInDisplayCutoutMode else 0

        val focusListener = object : android.view.ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (hasFocus && effectActive) {
                    decor.post {
                        if (effectActive) {
                            insets.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
            }
        }

        if (immersive) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs
            }
            window.addFlags(fullscreenFlag)
            insets.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            decor.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        } else {
            insets.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (immersive) {
                effectActive = false
                if (decor.viewTreeObserver.isAlive) {
                    decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                }
                if (wasFullscreen) window.addFlags(fullscreenFlag) else window.clearFlags(fullscreenFlag)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val attrs = window.attributes
                    attrs.layoutInDisplayCutoutMode = previousCutoutMode
                    window.attributes = attrs
                }
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                insets.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
'''
s = s[:immersive_start] + immersive_block + s[immersive_end:]

# Make the video surface use the complete edge-to-edge player bounds.
ratio_start = s.find('                        val mediaRatio = if(item.width>0 && item.height>0)')
if ratio_start < 0:
    raise SystemExit('ABORTED: video ratio block not found. No changes written.')
android_view_marker = '                        AndroidView('
android_view_start = s.find(android_view_marker, ratio_start)
if android_view_start < 0:
    raise SystemExit('ABORTED: AndroidView after video ratio block not found. No changes written.')
# Keep the replacement scoped to the current full-screen player block.
s = s[:ratio_start] + '                        val videoFrame = Modifier.fillMaxSize()\n' + s[android_view_start:]

modifier_anchor = '                            modifier=videoFrame\n'
require_once(s, modifier_anchor, 'full-screen PlayerView modifier')
s = s.replace(modifier_anchor, '''                            modifier=videoFrame.graphicsLayer {
                                scaleX = videoZoom
                                scaleY = videoZoom
                                translationX = videoPanX
                                translationY = videoPanY
                            }
''', 1)

# Replace the previous pinch-to-toggle-orientation logic with continuous zoom + pan.
pointer_anchor = '.pointerInput(lockPlayer,doubleTapEnabled,gestureLevelsEnabled,seekSeconds,controller) {'
if pointer_anchor not in s:
    # A prior revision may already include landscape in the key list.
    pointer_anchor = '.pointerInput(lockPlayer,doubleTapEnabled,gestureLevelsEnabled,seekSeconds,controller,landscape) {'
require_once(s, pointer_anchor, 'player pointerInput anchor')
pointer_start = s.index(pointer_anchor)
# Keep Android's edge-swipe Back gesture from being interpreted as a video seek.
if 'val systemBackEdge=32.dp.toPx()' not in s[pointer_start:pointer_start + 12000]:
    down_anchor = 'val down=awaitFirstDown(requireUnconsumed=false)'
    down_pos = s.find(down_anchor, pointer_start)
    if down_pos < 0:
        raise SystemExit('ABORTED: first-down gesture anchor not found. No changes written.')
    down_end = s.index('\n', down_pos) + 1
    down_line_start = s.rfind('\n', 0, down_pos) + 1
    down_indent = s[down_line_start:down_pos]
    edge_code = '''val systemBackEdge=32.dp.toPx()
if(down.position.x <= systemBackEdge || down.position.x >= size.width-systemBackEdge) {
    while(true) {
        val edgeEvent=awaitPointerEvent()
        if(edgeEvent.changes.none { it.pressed }) break
    }
    return@awaitEachGesture
}
'''
    s = s[:down_end] + textwrap.indent(edge_code, down_indent) + s[down_end:]

# Find the outer pointer loop and replace any old pinch implementation before its first-pointer handling.
start_hold = s.find('val startHold=Runnable', pointer_start)
if start_hold < 0:
    raise SystemExit('ABORTED: hold-speed gesture section not found. No changes written.')
loop_start = s.find('while(!finished) {', start_hold)
if loop_start < 0:
    raise SystemExit('ABORTED: main pointer loop not found. No changes written.')
change_anchor = 'val change=event.changes.firstOrNull {it.id==down.id}'
change_pos = s.find(change_anchor, loop_start)
if change_pos < 0:
    raise SystemExit('ABORTED: first-pointer event anchor not found. No changes written.')
change_line_end = s.index('\n', change_pos) + 1
loop_line_start = s.rfind('\n', 0, loop_start) + 1
loop_indent = s[loop_line_start:loop_start]
pinch_block = '''while(!finished) {
    val event=awaitPointerEvent()
    val pressedPointers=event.changes.filter { it.pressed }

    // Two fingers continuously zoom and pan the video. This gesture never seeks,
    // changes orientation, or triggers a tap. There is no fixed maximum zoom.
    if(pressedPointers.size >= 2) {
        holdHandler.removeCallbacks(startHold)
        val firstId=pressedPointers[0].id
        val secondId=pressedPointers[1].id
        var previousDistance:Float = run {
            val dx=pressedPointers[0].position.x-pressedPointers[1].position.x
            val dy=pressedPointers[0].position.y-pressedPointers[1].position.y
            kotlin.math.sqrt((dx*dx+dy*dy).toDouble()).toFloat()
        }
        var previousCenterX=(pressedPointers[0].position.x+pressedPointers[1].position.x)/2f
        var previousCenterY=(pressedPointers[0].position.y+pressedPointers[1].position.y)/2f
        pressedPointers.forEach { it.consume() }
        var pinchActive=true

        while(pinchActive) {
            val pinchEvent=awaitPointerEvent()
            val first=pinchEvent.changes.firstOrNull { it.id==firstId }
            val second=pinchEvent.changes.firstOrNull { it.id==secondId }

            if(first==null || second==null || !first.pressed || !second.pressed) {
                pinchEvent.changes.forEach { it.consume() }
                pinchActive=false
            } else {
                val dx=first.position.x-second.position.x
                val dy=first.position.y-second.position.y
                val currentDistance=kotlin.math.sqrt((dx*dx+dy*dy).toDouble()).toFloat()
                val currentCenterX=(first.position.x+second.position.x)/2f
                val currentCenterY=(first.position.y+second.position.y)/2f

                if(previousDistance>0f && currentDistance>0f) {
                    val factor=currentDistance/previousDistance
                    val nextZoom=videoZoom*factor
                    if(factor.isFinite() && factor>0f && nextZoom.isFinite() && nextZoom>0f) {
                        videoZoom=nextZoom
                    }
                }
                val nextPanX=videoPanX+(currentCenterX-previousCenterX)
                val nextPanY=videoPanY+(currentCenterY-previousCenterY)
                if(nextPanX.isFinite()) videoPanX=nextPanX
                if(nextPanY.isFinite()) videoPanY=nextPanY

                previousDistance=currentDistance
                previousCenterX=currentCenterX
                previousCenterY=currentCenterY
                pinchEvent.changes.forEach { it.consume() }
            }
        }
        finished=true
        continue
    }

    val change=event.changes.firstOrNull {it.id==down.id}
'''
s = s[:loop_start] + textwrap.indent(pinch_block, loop_indent) + s[change_line_end:]

# Widen the vertical volume/brightness indicator so two-digit levels aren't clipped.
bright_marker = 'val isBright = gestureMode == "Brightness"'
bright_pos = s.find(bright_marker)
if bright_pos < 0:
    raise SystemExit('ABORTED: volume/brightness popup section not found. No changes written.')
width_pos = s.find('Modifier.width(44.dp)', bright_pos)
if width_pos < 0:
    raise SystemExit('ABORTED: 44dp volume popup width not found. No changes written.')
s = s[:width_pos] + s[width_pos:].replace('Modifier.width(44.dp)', 'Modifier.width(72.dp)', 1)

if s == original:
    raise SystemExit('ABORTED: no source changes were made.')
backup = p.with_name(p.name + '.before-zoom-fullscreen-fix')
if not backup.exists():
    backup.write_text(original)
p.write_text(s)
print('PATCH APPLIED.')
print(f'Backup: {backup}')
print('Applied: portrait+landscape immersive mode, edge-to-edge/cutout layout,')
print('full-size video frame, continuous unbounded pinch zoom + two-finger pan,')
print('Android Back edge protection, landscape crop default, and wider level popup.')
