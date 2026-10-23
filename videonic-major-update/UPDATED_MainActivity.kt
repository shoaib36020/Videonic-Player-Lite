package com.videonic.lite

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.os.storage.StorageManager
import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.net.Uri
import android.app.PictureInPictureParams
import android.media.AudioManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Rational
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.videonic.lite.data.LocalLibrary
import com.videonic.lite.data.LocalVideo
import com.videonic.lite.data.VideoRepository
import com.videonic.lite.player.VideonicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Accent = Color(0xFF1169E8)
private val Pale = Color(0xFFF1F5FA)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF64748B)
private val Speeds = listOf(.25f,.5f,1f,1.5f,2f,2.5f,3f,4f)

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private val pipActive = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, VideonicPlaybackService::class.java))
        future = MediaController.Builder(this, token).buildAsync().also { f ->
            f.addListener({ runCatching { controller.value = f.get() } }, MoreExecutors.directExecutor())
        }
        setContent { VideonicApp(controller.value, this, pipActive.value) }
    }
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive.value = isInPictureInPictureMode
    }
    fun pictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build()) }
        }
    }
    fun captureFrame() {
        val dm=resources.displayMetrics
        val image=Bitmap.createBitmap(dm.widthPixels,dm.heightPixels,Bitmap.Config.ARGB_8888)
        PixelCopy.request(window,image,{ result ->
            if(result==PixelCopy.SUCCESS) {
                runCatching {
                    val values=ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME,"Videonic_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                        if(Build.VERSION.SDK_INT>=29)put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Videonic")
                    }
                    if(Build.VERSION.SDK_INT>=29){
                        val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                            ?: error("Unable to create image")
                        contentResolver.openOutputStream(uri)?.use{ image.compress(Bitmap.CompressFormat.PNG,100,it) }
                            ?: error("Unable to save image")
                    }else{
                        val file=java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                            "Videonic_${System.currentTimeMillis()}.png")
                        file.outputStream().use{ image.compress(Bitmap.CompressFormat.PNG,100,it) }
                    }
                }.onSuccess {Toast.makeText(this,"Snapshot saved",Toast.LENGTH_SHORT).show()}
                 .onFailure {Toast.makeText(this,"Snapshot failed: ${it.message}",Toast.LENGTH_SHORT).show()}
            } else Toast.makeText(this,"Snapshot unavailable for this video",Toast.LENGTH_SHORT).show()
            image.recycle()
        },Handler(Looper.getMainLooper()))
    }
    override fun onStop() {
        super.onStop()
        // Pause on Home unless the user explicitly enabled headphone background audio.
        val enabled = getSharedPreferences("videonic", MODE_PRIVATE)
            .getBoolean("background_audio_enabled", false)
        if (!enabled && !pipActive.value && !isChangingConfigurations) controller.value?.pause()
    }
    override fun onDestroy() {
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideonicApp(controller: MediaController?, activity: MainActivity, inPip: Boolean) {
    val context = LocalContext.current
    val library = remember { LocalLibrary(context) }
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("Videos") }
    var folder by remember { mutableStateOf<String?>(null) }
    var subpage by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<LocalVideo?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Newest") }
    var sortDialog by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingTrash by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<LocalVideo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameAfterConsent by remember { mutableStateOf<Pair<LocalVideo,String>?>(null) }
    var bulkPlaylist by remember { mutableStateOf(false) }
    var feedbackType by remember { mutableStateOf("Bug report") }
    var feedbackMessage by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(library.grid()) }
    val appPrefs=remember { context.getSharedPreferences("videonic", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(appPrefs.getString("appearance_mode", "system") ?: "system") }
    val systemDark=isSystemInDarkTheme()
    val dark=when(themeMode){"dark"->true;"light"->false;else->systemDark}
    var resumePlayback by remember { mutableStateOf(library.resumePlayback()) }
    var autoPlayNext by remember { mutableStateOf(library.autoPlayNext()) }
    var autoRotate by remember { mutableStateOf(library.autoRotate()) }
    var keepScreenOn by remember { mutableStateOf(library.keepScreenOn()) }
    var defaultSpeed by remember { mutableFloatStateOf(library.defaultSpeed()) }
    var playlistCreateFor by remember { mutableStateOf<LocalVideo?>(null) }
    var confirmPlaylistDelete by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(library.favorites()) }
    var recent by remember { mutableStateOf(library.recent()) }
    var playlistNames by remember { mutableStateOf(library.playlists()) }
    var playlistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var videoMenu by remember { mutableStateOf<LocalVideo?>(null) }
    var addToPlaylist by remember { mutableStateOf<LocalVideo?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<LocalVideo?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var playingNow by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(library.defaultSpeed()) }
    var speedDialog by remember { mutableStateOf(false) }
    var aspectIndex by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lockPlayer by remember { mutableStateOf(false) }
    var controlTick by remember { mutableIntStateOf(0) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var shuffled by remember { mutableStateOf(false) }
    var trackDialog by remember { mutableStateOf<String?>(null) }
    var volumePopup by remember { mutableStateOf(false) }
    var brightnessPopup by remember { mutableStateOf(false) }
    var decoderInfo by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(.5f) }
    var brightnessLevel by remember { mutableFloatStateOf(.5f) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gestureTick by remember { mutableIntStateOf(0) }
    var gestureMode by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var backgroundAudio by remember {
        mutableStateOf(appPrefs.getBoolean("background_audio_enabled", false))
    }
    var aspectDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    LaunchedEffect(Unit) {
        volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        brightnessLevel = activity.window.attributes.screenBrightness.takeIf { it >= 0f }
            ?: (Settings.System.getInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS,128) / 255f)
    }
    fun setVolume(value: Float) {
        volumeLevel=value.coerceIn(0f,1f)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,(volumeLevel*maxVolume).toInt(),0)
        gestureLabel="Volume ${(volumeLevel*maxVolume).toInt()} / $maxVolume";gestureMode="Volume";gestureTick++
    }
    fun setBrightness(value: Float) {
        brightnessLevel=value.coerceIn(.02f,1f)
        activity.window.attributes=activity.window.attributes.apply { screenBrightness=brightnessLevel }
        gestureLabel="Brightness ${(brightnessLevel*100).toInt()}%";gestureMode="Brightness";gestureTick++
    }
    val permission = if(Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED) }
    val refreshPending=remember { mutableIntStateOf(0) }
    val renameLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val operation=renameAfterConsent
        renameAfterConsent=null
        if(result.resultCode==Activity.RESULT_OK && operation!=null) {
            val values=ContentValues().apply {put(MediaStore.Video.Media.DISPLAY_NAME,operation.second)}
            runCatching {context.contentResolver.update(operation.first.uri,values,null,null)}
                .onSuccess { if(it>0)refreshPending.value++ else notice="Android did not rename this video" }
                .onFailure {notice="Rename failed: ${it.localizedMessage}"}
        }
    }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds=emptySet()
            pendingTrash=emptyList()
            refreshPending.value++
        } else pendingTrash=emptyList()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted=it }

    fun refresh() {
        if (!permitted) { loading=false; return }
        activity.lifecycleScope.launch {
            loading=true; scanError=null
            val result = withContext(Dispatchers.IO) { runCatching { VideoRepository(context).load() } }
            videos=result.getOrDefault(emptyList())
            scanError=result.exceptionOrNull()?.localizedMessage ?: if(result.isFailure) "MediaStore scan failed" else null
            loading=false
        }
    }
    LaunchedEffect(permitted,refreshPending.intValue) { if(permitted) refresh() else loading=false }
    fun openVideo(video: LocalVideo) {
        playing=video; playerExpanded=true; controlsVisible=true; lockPlayer=false; controlTick++
        library.visited(video.id); recent=library.recent()
        val queue=if(autoPlayNext) videos.ifEmpty { listOf(video) } else listOf(video)
        val start=queue.indexOfFirst{it.id==video.id}.coerceAtLeast(0)
        controller?.setMediaItems(queue.map{MediaItem.Builder().setUri(it.uri)
            .setMediaId(it.id.toString()).build()},start,if(resumePlayback) library.position(video.id) else 0L)
        controller?.repeatMode=repeatMode
        controller?.shuffleModeEnabled=shuffled
        controller?.prepare()
        speed=defaultSpeed
        controller?.setPlaybackSpeed(speed)
        activity.requestedOrientation=if(autoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if(keepScreenOn) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.play()
    }
    fun closePlayer() {
        playing?.let { library.savePosition(it.id, position) }
        controller?.stop(); controller?.clearMediaItems()
        playing=null; playerExpanded=false; speedDialog=false; lockPlayer=false
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    DisposableEffect(controller) {
        if(controller==null) onDispose {} else {
            val listener=object: Player.Listener {
                override fun onIsPlayingChanged(isPlaying:Boolean) { playingNow=isPlaying }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val item=videos.firstOrNull { it.id.toString()==mediaItem?.mediaId }
                    if(item!=null) {
                        playing=item
                        library.visited(item.id)
                        recent=library.recent()
                    }
                }
                override fun onPlaybackStateChanged(playbackState:Int) {
                    duration=controller.duration.coerceAtLeast(0)
                    position=controller.currentPosition.coerceAtLeast(0)
                }
            }
            controller.addListener(listener)
            playingNow=controller.isPlaying
            onDispose { controller.removeListener(listener) }
        }
    }
    LaunchedEffect(controller, playing) {
        while(controller!=null && playing!=null) {
            position=controller.currentPosition.coerceAtLeast(0)
            duration=controller.duration.coerceAtLeast(0)
            playing?.let { if(position > 2000) library.savePosition(it.id,position) }
            delay(1000)
        }
    }
    LaunchedEffect(controlTick,playerExpanded,lockPlayer,speedDialog,trackDialog,volumePopup,brightnessPopup) {
        if(playerExpanded && !lockPlayer && !speedDialog && trackDialog==null && !volumePopup && !brightnessPopup) {
            delay(5000)
            controlsVisible=false
        }
    }
    LaunchedEffect(gestureTick) { if(gestureLabel!=null){delay(1400);gestureLabel=null;gestureMode=null} }
    BackHandler(playerExpanded || folder!=null || subpage!=null || tab!="Videos") {
        when {
            playerExpanded -> { if(lockPlayer){lockPlayer=false;controlsVisible=true} else {playerExpanded=false; isFullscreen=false; speedDialog=false; activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED} }
            subpage!=null -> subpage=null
            folder!=null -> { folder=null;query="" }
            else -> { tab="Videos";query="" }
        }
    }

    val scheme=if(dark) darkColorScheme(primary=Accent,background=Color(0xFF101726)) else lightColorScheme(primary=Accent,background=Color.White,surface=Color.White)
    MaterialTheme(colorScheme=scheme) {
        val surface=MaterialTheme.colorScheme.background
        val textColor=MaterialTheme.colorScheme.onBackground
        if(playerExpanded && playing!=null) {
            val item=playing!!
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if(controller!=null) AndroidView(
                    factory={ ctx -> PlayerView(ctx).apply { useController=false; player=controller; layoutParams=ViewGroup.LayoutParams(-1,-1) } },
                    update={ it.player=controller; it.resizeMode=when(aspectIndex){2->AspectRatioFrameLayout.RESIZE_MODE_ZOOM;3->AspectRatioFrameLayout.RESIZE_MODE_FILL;else->AspectRatioFrameLayout.RESIZE_MODE_FIT} },
                    modifier=Modifier.fillMaxSize()
                )
                if(!inPip) {
                    Box(Modifier.fillMaxSize()
                        .pointerInput(lockPlayer) {
                            detectTapGestures(
                                onTap={ if(!lockPlayer) {controlsVisible=!controlsVisible;controlTick++} },
                                onDoubleTap={tap ->
                                    if(!lockPlayer){
                                        controller?.let { player ->
                                            val delta=if(tap.x>size.width/2f)10000L else -10000L
                                            player.seekTo((player.currentPosition+delta).coerceAtLeast(0))
                                            gestureLabel=if(delta>0)"+10 seconds" else "-10 seconds"
                                            gestureTick++;controlTick++
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(lockPlayer) {
                            if(!lockPlayer) {
                                var direction = 0 // 0 undecided, 1 horizontal, 2 brightness, 3 volume
                                var travelX = 0f
                                var travelY = 0f
                                var startedOnLeft = false
                                detectDragGestures(
                                    onDragStart = { start ->
                                        direction=0; travelX=0f; travelY=0f
                                        startedOnLeft=start.x < size.width / 2f
                                    },
                                    onDragEnd = { direction=0 },
                                    onDragCancel = { direction=0 },
                                    onDrag = { change, drag ->
                                        travelX += drag.x; travelY += drag.y
                                        if(direction == 0 && kotlin.math.max(kotlin.math.abs(travelX),kotlin.math.abs(travelY)) >= 20f) {
                                            direction = if(kotlin.math.abs(travelX) > kotlin.math.abs(travelY)*1.25f) 1
                                                else if(kotlin.math.abs(travelY) > kotlin.math.abs(travelX)*1.25f)
                                                    if(startedOnLeft) 2 else 3
                                                else 0
                                        }
                                        if(direction != 0) {
                                            change.consume()
                                            when(direction) {
                                                1 -> controller?.let {player ->
                                                    if(player.duration > 0L) {
                                                        val amount=(drag.x / size.width.toFloat() * 45000L).toLong()
                                                        player.seekTo((player.currentPosition+amount).coerceIn(0L,player.duration))
                                                        gestureLabel="Seek ${clock(player.currentPosition)}"
                                                        gestureMode=null; gestureTick++
                                                    }
                                                }
                                                2 -> setBrightness(brightnessLevel-drag.y/size.height*1.7f)
                                                3 -> setVolume(volumeLevel-drag.y/size.height*1.7f)
                                            }
                                            controlTick++
                                        }
                                    }
                                )
                            }
                        }
                        .pointerInput(lockPlayer) {
                            if(!lockPlayer) {
                                var index=Speeds.indexOf(speed).coerceAtLeast(0)
                                var travel=0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart={gestureLabel="Speed ${speedLabel(speed)}x";gestureTick++;controlsVisible=true},
                                    onDragEnd={gestureLabel="Speed ${speedLabel(speed)}x";gestureTick++;controlTick++},
                                    onDragCancel={gestureLabel=null},
                                    onDrag={change,drag ->
                                        change.consume();travel+=drag.x
                                        if(kotlin.math.abs(travel)>44f){
                                            index=(index+if(travel>0)1 else -1).coerceIn(Speeds.indices)
                                            speed=Speeds[index];controller?.setPlaybackSpeed(speed)
                                            travel=0f
                                            gestureLabel="Speed ${speedLabel(speed)}x";gestureTick++;controlTick++
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
                if(!inPip && gestureLabel!=null) {
                    if (gestureMode == "Brightness" || gestureMode == "Volume") {
                        val isBright = gestureMode == "Brightness"
                        val level = if(isBright) brightnessLevel else volumeLevel
                        val tint = if(isBright) Color(0xFFFFB91D) else Color(0xFF1489F5)
                        Surface(
                            modifier=Modifier.align(if(isBright) Alignment.CenterStart else Alignment.CenterEnd)
                                .padding(horizontal=20.dp),
                            shape=RoundedCornerShape(45.dp),
                            color=Color(0xEE101B31),
                            border=BorderStroke(2.dp,tint)
                        ) {
                            Column(
                                Modifier.width(76.dp).padding(vertical=17.dp,horizontal=8.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(if(isBright) Icons.Default.Brightness6 else Icons.Default.VolumeUp,null,tint=tint,modifier=Modifier.size(28.dp))
                                Text(if(isBright) "${(level*100).toInt()}%" else "${(level*maxVolume).toInt()} / $maxVolume",color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1)
                                Box(Modifier.width(22.dp).height(190.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF384254))) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f,1f)).align(Alignment.BottomCenter).background(tint,RoundedCornerShape(20.dp)))
                                }
                                Text(if(isBright) "BRIGHT" else "VOLUME",color=Color.LightGray,fontSize=10.sp)
                            }
                        }
                    } else Surface(modifier=Modifier.align(Alignment.Center).padding(top=170.dp),shape=RoundedCornerShape(15.dp),color=Color(0xDF132438)) {
                        Text(gestureLabel!!,color=Color.White,modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp),fontWeight=FontWeight.Bold)
                    }
                }
                if(!inPip && lockPlayer) {
                    Surface(shape=RoundedCornerShape(16.dp),color=Color.Black.copy(alpha=.7f),modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=45.dp)) {
                        TextButton(onClick={lockPlayer=false;controlsVisible=true;controlTick++}) {
                            Icon(Icons.Default.LockOpen,null,tint=Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock controls",color=Color.White)
                        }
                    }
                }
                if(!inPip && controlsVisible && !lockPlayer) {
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .background(Color.Black.copy(alpha=.70f))
                            .statusBarsPadding()
                            .padding(horizontal=8.dp, vertical=if(landscape) 2.dp else 5.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        IconButton(onClick={playerExpanded=false;isFullscreen=false;activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED},modifier=Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)
                        }
                        Text(item.title,Modifier.weight(1f),color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                        IconButton(onClick={
                            backgroundAudio=!backgroundAudio
                            appPrefs.edit().putBoolean("background_audio_enabled",backgroundAudio).apply()
                            controlTick++
                        },modifier=Modifier.size(48.dp)) {
                            Icon(Icons.Default.Headset,"Background audio ${if(backgroundAudio) "enabled" else "disabled"}",tint=if(backgroundAudio)Accent else Color.White)
                        }
                        IconButton(onClick={trackDialog="Subtitles";controlTick++},modifier=Modifier.size(48.dp)) {
                            Icon(Icons.Default.Subtitles,"Subtitles",tint=Color.White)
                        }
                        Box {
                            IconButton(onClick={overflowOpen=true;controlTick++},modifier=Modifier.size(48.dp)) {
                                Icon(Icons.Default.MoreVert,"More playback tools",tint=Color.White)
                            }
                            DropdownMenu(expanded=overflowOpen,onDismissRequest={overflowOpen=false}) {
                                DropdownMenuItem(text={Text("Audio tracks")},leadingIcon={Icon(Icons.Default.Audiotrack,null)},onClick={trackDialog="Audio";overflowOpen=false;controlTick++})
                                DropdownMenuItem(text={Text("Decoder details")},leadingIcon={Icon(Icons.Default.Memory,null)},onClick={decoderInfo=true;overflowOpen=false;controlTick++})
                                DropdownMenuItem(text={Text("Repeat: "+when(repeatMode){Player.REPEAT_MODE_ONE->"One";Player.REPEAT_MODE_ALL->"All";else->"Off"})},leadingIcon={Icon(Icons.Default.Repeat,null)},onClick={
                                    repeatMode=when(repeatMode){Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ONE;Player.REPEAT_MODE_ONE->Player.REPEAT_MODE_ALL;else->Player.REPEAT_MODE_OFF}
                                    controller?.repeatMode=repeatMode;overflowOpen=false;controlTick++
                                })
                                DropdownMenuItem(text={Text(if(shuffled)"Shuffle: On" else "Shuffle: Off")},leadingIcon={Icon(Icons.Default.Shuffle,null)},onClick={shuffled=!shuffled;controller?.shuffleModeEnabled=shuffled;overflowOpen=false;controlTick++})
                                DropdownMenuItem(text={Text("Picture in picture")},leadingIcon={Icon(Icons.Default.PictureInPictureAlt,null)},onClick={overflowOpen=false;activity.pictureInPicture()})
                                DropdownMenuItem(text={Text("Save snapshot")},leadingIcon={Icon(Icons.Default.CameraAlt,null)},onClick={overflowOpen=false;controlsVisible=false;activity.captureFrame();controlTick++})
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={controller?.seekToPreviousMediaItem();controlTick++}) { Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White) }
                        IconButton(onClick={controller?.let { it.seekTo((it.currentPosition-10000).coerceAtLeast(0)) };controlTick++}) { Icon(Icons.Default.Replay10,"Back 10 seconds",tint=Color.White,modifier=Modifier.size(32.dp)) }
                        Surface(shape=RoundedCornerShape(100),color=Accent,modifier=Modifier.size(74.dp)) {
                            IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play();controlTick++}) { Icon(if(playingNow) Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",tint=Color.White,modifier=Modifier.size(42.dp)) }
                        }
                        IconButton(onClick={controller?.let { it.seekTo(it.currentPosition+10000) };controlTick++}) { Icon(Icons.Default.Forward10,"Forward 10 seconds",tint=Color.White,modifier=Modifier.size(32.dp)) }
                        IconButton(onClick={controller?.seekToNextMediaItem();controlTick++}) { Icon(Icons.Default.SkipNext,"Next",tint=Color.White) }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.64f)).navigationBarsPadding().padding(horizontal=12.dp,vertical=if(landscape) 2.dp else 8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(clock(position),color=Color.White,fontSize=12.sp)
                            Slider(
                                value=if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f,
                                onValueChange={controller?.seekTo((it*duration).toLong());controlTick++},
                                modifier=Modifier.weight(1f).padding(horizontal=8.dp)
                            )
                            Text(clock(duration),color=Color.White,fontSize=12.sp)
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround) {
                            PlayerAction(Icons.Default.Lock,"Lock",landscape){lockPlayer=true;controlsVisible=false;controlTick++}
                            PlayerAction(Icons.Default.Speed,"${speedLabel(speed)}x",landscape){speedDialog=true;controlTick++}
                            PlayerAction(Icons.Default.AspectRatio,listOf("Original","Fit","Crop","Stretch")[aspectIndex],landscape){aspectDialog=true;controlTick++}
                            PlayerAction(if(isFullscreen)Icons.Default.FullscreenExit else Icons.Default.Fullscreen,if(isFullscreen)"Exit fullscreen" else "Fullscreen",landscape){
                                isFullscreen=!isFullscreen
                                activity.requestedOrientation=if(isFullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                controlTick++
                            }
                        }
                    }
                }
                if(!inPip && aspectDialog) AlertDialog(
                    onDismissRequest={aspectDialog=false},
                    title={Text("Video aspect ratio")},
                    text={Column {
                        listOf("Original", "Fit", "Crop", "Stretch").forEachIndexed { index,label ->
                            Row(Modifier.fillMaxWidth().clickable{aspectIndex=index;aspectDialog=false;controlTick++}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected=aspectIndex==index,onClick={aspectIndex=index;aspectDialog=false;controlTick++})
                                Spacer(Modifier.width(12.dp));Text(label)
                            }
                        }
                    }}, confirmButton={TextButton(onClick={aspectDialog=false}){Text("Close")}}
                )
                if(!inPip && volumePopup) PlayerLevelDialog("Volume",volumeLevel,{setVolume(it)},{volumePopup=false;controlTick++})
                if(!inPip && brightnessPopup) PlayerLevelDialog("Brightness",brightnessLevel,{setBrightness(it)},{brightnessPopup=false;controlTick++})
                if(!inPip && decoderInfo) AlertDialog(onDismissRequest={decoderInfo=false},title={Text("Video decoder")},text={Text("Current codec: ${controller?.videoSize?.let { "${it.width} x ${it.height}" } ?: "Unknown"}. The player uses Android Media3 decoder selection with hardware preference and software fallback. Manual switching requires rebuilding the playback engine and is not yet available.")},confirmButton={TextButton(onClick={decoderInfo=false}){Text("OK")}})
                if(!inPip && trackDialog!=null && controller!=null) {
                    val selectedType=if(trackDialog=="Audio")C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    val groups=controller.currentTracks.groups.filter{it.type==selectedType}
                    AlertDialog(onDismissRequest={trackDialog=null;controlTick++},shape=RoundedCornerShape(22.dp),title={Text(if(selectedType==C.TRACK_TYPE_AUDIO)"Audio tracks" else "Subtitles")},text={
                        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                            if(selectedType==C.TRACK_TYPE_TEXT) TextButton(onClick={
                                controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                                trackDialog=null;controlTick++
                            }){Text("Off")}
                            if(groups.isEmpty())Text("No ${if(selectedType==C.TRACK_TYPE_AUDIO)"audio" else "subtitle"} tracks found",color=Muted)
                            groups.forEachIndexed{groupIndex, group->
                                for(i in 0 until group.length){
                                    val format=group.getTrackFormat(i)
                                    val lang=format.language?.takeUnless{it=="und"} ?: "Unknown language"
                                    val label=format.label ?: "${if(selectedType==C.TRACK_TYPE_AUDIO)"Audio" else "Subtitle"} ${groupIndex+1}.${i+1}"
                                    TextButton(enabled=group.isTrackSupported(i),onClick={
                                        controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(selectedType,false)
                                            .clearOverridesOfType(selectedType)
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup,listOf(i)))
                                            .build()
                                        trackDialog=null;controlTick++
                                    }) {Text("$label · $lang${if(group.isTrackSelected(i))" ✓" else ""}")}
                                }
                            }
                        }
                    },confirmButton={TextButton(onClick={trackDialog=null;controlTick++}){Text("Close")}})
                }
                if(!inPip && speedDialog) AlertDialog(onDismissRequest={speedDialog=false;controlTick++},shape=RoundedCornerShape(22.dp),title={Text("Playback speed")},text={
                    Column { Speeds.chunked(4).forEach { row -> Row { row.forEach { option ->
                        TextButton(onClick={speed=option;controller?.setPlaybackSpeed(speed);speedDialog=false;controlTick++},modifier=Modifier.weight(1f)) { Text("${speedLabel(option)}x",color=if(speed==option)Accent else MaterialTheme.colorScheme.onSurface) }
                    } } } }
                },confirmButton={TextButton(onClick={speedDialog=false;controlTick++}){Text("Close")}})
            }
        } else {
            Scaffold(
                containerColor=surface,
                topBar={
                    Column {
                        TopAppBar(
                            title={Row(verticalAlignment=Alignment.CenterVertically) {
                                if(folder==null && subpage==null) {
                                    Image(painter=painterResource(R.drawable.videonic_brand),contentDescription=null,modifier=Modifier.size(30.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if(folder!=null)folder!! else if(subpage!=null)subpage!! else "Videonic Lite",maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold,color=textColor)
                            }},
                            navigationIcon={if(folder!=null || subpage!=null)IconButton(onClick={folder=null;subpage=null;query=""}){Icon(Icons.Default.ArrowBack,"Back")} },
                            actions={
                                if(tab!="Me" && subpage==null) {
                                    IconButton(onClick={refresh()}){Icon(Icons.Default.Refresh,"Refresh",tint=Accent)}
                                    IconButton(onClick={grid=!grid;library.setGrid(grid)}){Icon(if(grid)Icons.Default.ViewList else Icons.Default.GridView,"Toggle grid",tint=Accent)}
                                    IconButton(onClick={sortDialog=true}){Icon(Icons.Default.Sort,"Sort",tint=Accent)}
                                }
                            },
                            colors=TopAppBarDefaults.topAppBarColors(containerColor=surface)
                        )
                        if(tab!="Me" && (tab!="Folders" || folder!=null) && subpage!="Playlists" && subpage!="About" && subpage!="Settings") {
                            OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("Search videos...",color=Muted)},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=5.dp))
                        }
                    }
                },
                bottomBar={
                    Column {
                        if(playing!=null) {
                            val item=playing!!
                            Surface(color=if(dark)Color(0xFF203047) else Color(0xFFE8F1FF),modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().clickable{playerExpanded=true;controlsVisible=true;controlTick++}.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    if(controller!=null) {
                                        AndroidView(
                                            factory={ ctx -> PlayerView(ctx).apply {
                                                useController=false
                                                player=controller
                                                resizeMode=AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            } },
                                            update={ it.player=controller },
                                            modifier=Modifier.size(width=88.dp,height=56.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else VideoThumb(item,Modifier.size(width=88.dp,height=56.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                                    IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play()}){Icon(if(playingNow)Icons.Default.Pause else Icons.Default.PlayArrow,"Play pause",tint=Accent)}
                                    IconButton(onClick={closePlayer()}){Icon(Icons.Default.Close,"Close player")}
                                }
                            }
                        }
                        NavigationBar(containerColor=surface) {
                            listOf("Videos","Folders","Recent","Me").forEach { name ->
                                NavigationBarItem(selected=tab==name,onClick={tab=name;folder=null;subpage=null;query=""},icon={Icon(when(name){"Videos"->Icons.Default.VideoLibrary;"Folders"->Icons.Default.Folder;"Recent"->Icons.Default.History;else->Icons.Default.Person},name)},label={Text(name)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent,selectedTextColor=Accent))
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if(selectedIds.isNotEmpty()) {
                        val chosen=videos.filter { it.id in selectedIds }
                        Surface(color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("${selectedIds.size} selected",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                                    TextButton(onClick={selectedIds=emptySet()}){Text("Cancel")}
                                    TextButton(onClick={selectedIds=videos.map { it.id }.toSet()}){Text("All")}
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick={chosen.forEach{if(it.id.toString() !in library.favorites()) library.toggleFavorite(it.id)};favorites=library.favorites();selectedIds=emptySet()}){Text("Favorite")}
                                    TextButton(onClick={
                                        val send=Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type="video/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(chosen.map{it.uri}))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send,"Share videos"))
                                    }){Text("Share")}
                                    TextButton(onClick={bulkPlaylist=true}){Text("Playlist")}
                                    TextButton(onClick={pendingTrash=chosen}){Text("Trash")}
                                }
                            }
                        }
                    }
                    if(!permitted && tab!="Me") {
                        EmptyState("Allow access to your videos","Videonic only reads your local media library.") {
                            Button(onClick={ask.launch(permission)}) { Text("Allow video access") }
                            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) {Text("Open app settings")}
                        }
                    } else if(loading && tab!="Me") {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally) {CircularProgressIndicator();Spacer(Modifier.height(12.dp));Text("Scanning videos...",color=Muted)}}
                    } else if(scanError!=null && tab!="Me") {
                        EmptyState("Unable to scan videos",scanError.orEmpty()) {Button(onClick={refresh()}){Text("Retry")}}
                    } else if(tab=="Me") {
                        when(subpage) {
                            "Favorites" -> VideoList(videos.filter{it.id.toString() in favorites}.sortedWith(videoComparator(sort)).filter{it.title.contains(query,true)},grid,library, {openVideo(it)},{videoMenu=it},favorites,"No favorites yet",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})
                            "Playlists" -> {
                                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("My playlists",Modifier.weight(1f),fontWeight=FontWeight.Bold);FilledTonalButton(onClick={playlistDialog=true}){Icon(Icons.Default.Add,null);Text("New")}}
                                LazyColumn { items(playlistNames) { name ->
                                    ListItem(headlineContent={Text(name)},supportingContent={Text("${library.playlistItems(name).size} videos")},leadingContent={Icon(Icons.Default.PlaylistPlay,null,tint=Accent)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable{subpage="Playlist:$name"})
                                } }
                            }
                            "Theme" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                AppearanceModePicker(themeMode) { choice ->
                                    themeMode=choice
                                    appPrefs.edit().putString("appearance_mode",choice).apply()
                                }
                            }
                            "Private Folder" -> PrivateFolderScreen()
                            "Recycle Bin" -> RecycleBinScreen()
                            "Privacy Policy", "Terms of Use", "Permissions & Storage", "Open Source Licenses" -> PolicyScreen(subpage!!)
                            "Support & Feedback" -> LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                item {Text("Support & Feedback",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
                                item {Text("Tell us about playback problems, accessibility issues, feature requests, or other questions. Your message is sent only when you choose to open your email app and send it.")}
                                item {Text("Category",fontWeight=FontWeight.Bold)}
                                item {Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {listOf("Bug report","Feature request","Other").forEach { label -> FilterChip(selected=feedbackType==label,onClick={feedbackType=label},label={Text(label,fontSize=11.sp)})} } }
                                item {OutlinedTextField(feedbackMessage,{feedbackMessage=it.take(5000)},label={Text("Describe your issue or suggestion")},minLines=5,modifier=Modifier.fillMaxWidth())}
                                item {Button(enabled=feedbackMessage.isNotBlank(),onClick={
                                    val intent=Intent(Intent.ACTION_SENDTO).apply {
                                        data=Uri.parse("mailto:contact@instantcod.online")
                                        putExtra(Intent.EXTRA_SUBJECT,"Videonic Lite - $feedbackType")
                                        putExtra(Intent.EXTRA_TEXT,"$feedbackMessage\n\nApp: Videonic Lite\nAndroid: ${Build.VERSION.RELEASE}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}")
                                    }
                                    runCatching {context.startActivity(intent)}.onFailure {notice="No email application available. Please install or configure an email app."}
                                }){Text("Continue to email app")} }
                                item {Text("No email is sent automatically. Review the message in your email app before sending.",color=Muted,fontSize=12.sp)}
                            }
                            "Settings" -> LazyColumn(
                                contentPadding=PaddingValues(horizontal=16.dp,vertical=18.dp),
                                verticalArrangement=Arrangement.spacedBy(10.dp)
                            ) {
                                item { SettingsHeading("Appearance", "Choose how Videonic looks") }
                                item { AppearanceModePicker(themeMode) { choice ->
                                    themeMode=choice
                                    appPrefs.edit().putString("appearance_mode",choice).apply()
                                } }
                                item { SettingsToggle(Icons.Default.GridView,"Video grid layout","Show video tiles instead of rows",grid) {grid=it;library.setGrid(it)} }
                                item { SettingsHeading("Playback", "Control your watching experience") }
                                item { SettingsToggle(Icons.Default.Headset,"Background audio (headphones)","Off by default: leaving the app pauses playback",backgroundAudio) {backgroundAudio=it;appPrefs.edit().putBoolean("background_audio_enabled",it).apply()} }
                                item { SettingsToggle(Icons.Default.ScreenRotation,"Auto rotate","Allow rotation while the video player is open",autoRotate) {autoRotate=it;library.setAutoRotate(it);if(playerExpanded)activity.requestedOrientation=if(it)ActivityInfo.SCREEN_ORIENTATION_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED} }
                                item { SettingsToggle(Icons.Default.Restore,"Resume playback","Continue from your last watched position",resumePlayback) {resumePlayback=it;library.setResumePlayback(it)} }
                                item { SettingsToggle(Icons.Default.SkipNext,"Auto play next","Automatically continue to the next video",autoPlayNext) {autoPlayNext=it;library.setAutoPlayNext(it)} }
                                item { SettingsToggle(Icons.Default.LightMode,"Keep screen awake","Keep the display on while playing",keepScreenOn) {keepScreenOn=it;library.setKeepScreenOn(it);if(playerExpanded){if(it)activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}} }
                                item { SettingsHeading("Default playback speed", "Applied when you start a new video") }
                                item {
                                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.40f)) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("${speedLabel(defaultSpeed)}x",color=Accent,fontWeight=FontWeight.SemiBold)
                                            Spacer(Modifier.height(8.dp))
                                            listOf(Speeds.take(4),Speeds.drop(4)).forEach { speeds ->
                                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                                    speeds.forEach { option ->
                                                        val active=defaultSpeed==option
                                                        TextButton(onClick={defaultSpeed=option;library.setDefaultSpeed(option)},modifier=Modifier.weight(1f)) {
                                                            Text("${speedLabel(option)}x",color=if(active)Accent else MaterialTheme.colorScheme.onSurface,fontSize=12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Text("Changes are saved automatically.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(8.dp)) }
                            }
                            "About" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlayCircleFilled,null,tint=Accent,modifier=Modifier.size(68.dp))
                                Spacer(Modifier.height(12.dp));Text("Videonic Lite",fontWeight=FontWeight.Bold,fontSize=24.sp)
                                Text("Version 1.0.0 • Native Android",color=Muted)
                                Spacer(Modifier.height(20.dp));Text("Videonic Lite plays videos from the media library on your Android device. Organize favorites and playlists, resume videos, change playback speed, and select available audio and subtitle tracks.",color=Muted)
                                Spacer(Modifier.height(16.dp))
                                listOf("Privacy Policy","Terms of Use","Permissions & Storage","Open Source Licenses","Support & Feedback").forEach { name ->
                                    MeEntry(Icons.Default.Info,name,"Read more") { subpage=name }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            else -> if(subpage?.startsWith("Playlist:")==true) {
                                val name=subpage!!.removePrefix("Playlist:")
                                val set=library.playlistItems(name)
                                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("${set.size} videos",Modifier.weight(1f),color=Muted);TextButton(onClick={confirmPlaylistDelete=name}){Text("Delete playlist")}}
                                VideoList(set.mapNotNull{id->videos.find{it.id.toString()==id}}.filter{it.title.contains(query,true)},grid,library,{openVideo(it)},{videoMenu=it},favorites,"Playlist is empty",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})
                            } else MeScreen(videos,favorites,playlistNames,library,{subpage=it})
                        }
                    } else if(tab=="Folders" && folder==null) {
                        val folderGroups=videos.groupBy{it.folder}.toList().sortedBy{it.first.lowercase()}
                        if(folderGroups.isEmpty()) EmptyState("No folders found","Videos in your device folders will appear here.") {}
                        else LazyColumn(contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            items(folderGroups,key={it.first}) { entry ->
                                Surface(color=if(dark)MaterialTheme.colorScheme.surfaceVariant else Pale,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{folder=entry.first;query=""}) {
                                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                                        Surface(color=Color(0xFFDDEBFF),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Folder,null,tint=Accent,modifier=Modifier.padding(12.dp).size(26.dp))}
                                        Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(entry.first,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${entry.second.size} videos",color=Accent,fontSize=12.sp)}
                                        Icon(Icons.Default.ChevronRight,"Open folder",tint=Muted)
                                    }
                                }
                            }
                        }
                    } else {
                        val shown=when(tab){
                            "Recent"-> recent.mapNotNull{id->videos.find{it.id.toString()==id}}
                            "Folders"-> videos.filter{it.folder==folder}
                            else-> videos
                        }.filter{it.title.contains(query,true)}.let{if(tab=="Recent" && sort=="Newest")it else it.sortedWith(videoComparator(sort))}
                        Row(Modifier.fillMaxWidth().padding(horizontal=17.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(if(tab=="Recent")"${shown.size} recently watched" else "${shown.size} videos",Modifier.weight(1f),color=Muted,fontSize=13.sp)
                            if(tab=="Recent" && recent.isNotEmpty())TextButton(onClick={confirmClear=true}){Icon(Icons.Default.DeleteSweep,null,tint=Color(0xFFE05555));Text("Clear",color=Color(0xFFE05555))}
                        }
                        key(sort,grid,tab,folder,subpage) {
                            VideoList(shown,grid,library,{openVideo(it)},{videoMenu=it},favorites,when(tab){"Recent"->"No recently watched videos";else->"No videos found"},selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})
                        }
                    }
                }
            }
        }
        renameTarget?.let { video -> AlertDialog(
            onDismissRequest={renameTarget=null},title={Text("Rename video")},
            text={Column {
                Text("Android may ask you to approve changing this file.",color=Muted)
                OutlinedTextField(renameText,{renameText=it.take(180)},label={Text("Filename")},singleLine=true)
            }},confirmButton={TextButton(enabled=renameText.isNotBlank(),onClick={
                val filename=renameText.trim()
                renameTarget=null
                if(Build.VERSION.SDK_INT>=30){
                    runCatching {
                        val request=MediaStore.createWriteRequest(context.contentResolver,listOf(video.uri))
                        renameAfterConsent=video to filename
                        renameLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure {notice="Unable to request rename: ${it.localizedMessage}"}
                } else notice="Renaming in this build requires Android 11 or newer"
            }){Text("Continue")}},dismissButton={TextButton(onClick={renameTarget=null}){Text("Cancel")}}
        ) }
        if(bulkPlaylist) AlertDialog(onDismissRequest={bulkPlaylist=false},
            title={Text("Add selected videos to playlist")},
            text={Column {
                if(playlistNames.isEmpty()) Text("Create a playlist in Me first.")
                playlistNames.forEach { name ->TextButton(onClick={
                    videos.filter{it.id in selectedIds}.forEach {library.addToPlaylist(name,it.id)}
                    selectedIds=emptySet();bulkPlaylist=false;notice="Added to $name"
                }){Text(name)} }
            }},confirmButton={TextButton(onClick={bulkPlaylist=false}){Text("Close")}})
        pendingTrash.takeIf { it.isNotEmpty() }?.let { chosen -> AlertDialog(
            onDismissRequest={pendingTrash=emptyList()},
            title={Text("Move ${chosen.size} videos to trash?")},
            text={Text("Android will ask you to approve this operation. Trashed videos can usually be restored until your device permanently removes them.")},
            confirmButton={TextButton(onClick={
                if(Build.VERSION.SDK_INT>=30){
                    runCatching {
                        val pi=MediaStore.createTrashRequest(context.contentResolver,chosen.map{it.uri},true)
                        trashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }.onFailure{notice="Trash request failed: ${it.localizedMessage}";pendingTrash=emptyList()}
                }else{notice="Trash requires Android 11 or newer";pendingTrash=emptyList()}
            }){Text("Continue")}},
            dismissButton={TextButton(onClick={pendingTrash=emptyList()}){Text("Cancel")}}
        ) }
        if(sortDialog) AlertDialog(
            onDismissRequest={sortDialog=false},shape=RoundedCornerShape(22.dp),title={Text("Sort videos")},
            text={Column {
                listOf("Newest","Oldest","Name A-Z","Name Z-A","Largest","Smallest","Longest","Shortest").forEach {option ->
                    val selected = sort == option
                    Surface(color=if(selected)Accent.copy(alpha=.12f) else Color.Transparent,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable{sort=option;sortDialog=false}.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=selected,onClick={sort=option;sortDialog=false})
                            Spacer(Modifier.width(10.dp));Text(option,color=if(selected)Accent else MaterialTheme.colorScheme.onSurface,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }},confirmButton={TextButton(onClick={sortDialog=false}){Text("Done")}}
        )
        if(playlistDialog) AlertDialog(onDismissRequest={playlistDialog=false;playlistCreateFor=null},shape=RoundedCornerShape(24.dp),title={Text(if(playlistCreateFor==null)"Create playlist" else "Create & add video")},text={Column {
            Text("Give your collection a name",color=Muted,fontSize=13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value=newPlaylistName,onValueChange={newPlaylistName=it.take(45)},label={Text("Playlist name")},singleLine=true)
        }},confirmButton={TextButton(onClick={
            val name=newPlaylistName.trim()
            if(library.createPlaylist(name)){
                playlistCreateFor?.let {library.addToPlaylist(name,it.id)}
                playlistNames=library.playlists();newPlaylistName="";playlistDialog=false;playlistCreateFor=null;addToPlaylist=null
                notice="Playlist created${if(name.isNotEmpty()) ": $name" else ""}"
            }else notice="Choose a unique playlist name"
        }){Text("Create")}},dismissButton={TextButton(onClick={playlistDialog=false;playlistCreateFor=null}){Text("Cancel")}})
        videoMenu?.let { video ->
            AlertDialog(onDismissRequest={videoMenu=null},title={Text(video.title,maxLines=2)},text={Column {
                TextButton(onClick={library.toggleFavorite(video.id);favorites=library.favorites();videoMenu=null}) {Text(if(video.id.toString() in favorites)"Remove favorite" else "Add to favorites")}
                TextButton(onClick={addToPlaylist=video;videoMenu=null}){Text("Add to playlist")}
                TextButton(onClick={renameTarget=video;renameText=video.title;videoMenu=null}){Text("Rename video")}
                if(tab=="Recent")TextButton(onClick={confirmRemove=video;videoMenu=null}){Text("Remove from recent")}
                if(subpage?.startsWith("Playlist:")==true)TextButton(onClick={confirmRemove=video;videoMenu=null}){Text("Remove from playlist")}
                TextButton(onClick={
                    val share=Intent(Intent.ACTION_SEND).apply{type="video/*";putExtra(Intent.EXTRA_STREAM,video.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                    context.startActivity(Intent.createChooser(share,"Share video"));videoMenu=null
                }){Text("Share video")}
            }},confirmButton={TextButton(onClick={videoMenu=null}){Text("Close")}})
        }
        addToPlaylist?.let { video -> AlertDialog(
            onDismissRequest={addToPlaylist=null},shape=RoundedCornerShape(24.dp),
            title={Text("Save to playlist")},
            text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(video.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=Muted,fontSize=13.sp)
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f),modifier=Modifier.fillMaxWidth().clickable{
                    playlistCreateFor=video;newPlaylistName="";addToPlaylist=null;playlistDialog=true
                }) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.AddCircleOutline,"Create playlist",tint=Accent)
                        Spacer(Modifier.width(10.dp));Text("Create new playlist",fontWeight=FontWeight.SemiBold)
                    }
                }
                playlistNames.forEach { name ->
                    val already=video.id.toString() in library.playlistItems(name)
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f),modifier=Modifier.fillMaxWidth().clickable{
                        library.addToPlaylist(name,video.id);addToPlaylist=null;notice=if(already)"Already in $name" else "Added to $name"
                    }) {Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.PlaylistPlay,null,tint=Accent);Spacer(Modifier.width(10.dp))
                        Text(name,Modifier.weight(1f),maxLines=1);if(already)Icon(Icons.Default.Check,null,tint=Accent)
                    }}
                }
            }},confirmButton={TextButton(onClick={addToPlaylist=null}){Text("Done")}}
        ) }
        confirmPlaylistDelete?.let { name -> AlertDialog(
            onDismissRequest={confirmPlaylistDelete=null},shape=RoundedCornerShape(24.dp),
            title={Text("Delete playlist?")},
            text={Text("Delete '$name' from your collections? Your video files will remain on the device.")},
            confirmButton={TextButton(onClick={library.deletePlaylist(name);playlistNames=library.playlists();subpage="Playlists";confirmPlaylistDelete=null}){Text("Delete playlist")}},
            dismissButton={TextButton(onClick={confirmPlaylistDelete=null}){Text("Cancel")}}
        ) }
        if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear recent history?")},text={Text("This will not delete any video files.")},confirmButton={TextButton(onClick={library.clearRecent();recent=emptyList();confirmClear=false}){Text("Clear history")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancel")}})
        confirmRemove?.let{video->AlertDialog(onDismissRequest={confirmRemove=null},title={Text("Remove from collection?")},text={Text("The original video file will not be deleted.")},confirmButton={TextButton(onClick={
            if(subpage?.startsWith("Playlist:")==true) library.removeFromPlaylist(subpage!!.removePrefix("Playlist:"),video.id)
            else {library.removeRecent(video.id); recent=library.recent()}
            confirmRemove=null;notice="Removed from collection"
        }){Text("Remove")}},dismissButton={TextButton(onClick={confirmRemove=null}){Text("Cancel")}})}
        notice?.let{message->AlertDialog(onDismissRequest={notice=null},title={Text("Videonic Lite")},text={Text(message)},confirmButton={TextButton(onClick={notice=null}){Text("OK")}})}
    }
}

@Composable
private fun PlayerAction(icon: androidx.compose.ui.graphics.vector.ImageVector,label:String,compact:Boolean=false,onClick:()->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        IconButton(onClick=onClick,modifier=Modifier.size(if(compact) 40.dp else 48.dp)) {
            Icon(icon,label,tint=Color.White,modifier=Modifier.size(if(compact) 21.dp else 24.dp))
        }
        if(!compact) Text(label,color=Color.White,fontSize=11.sp)
    }
}

@Composable
private fun VideoThumb(video:LocalVideo,modifier:Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xFFDFE6F1)),contentAlignment=Alignment.Center) {
        // MediaStore thumbnail is loaded by the OS. A placeholder remains when thumbnails are unavailable.
        val context=LocalContext.current
        var thumbnail by remember(video.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(video.id) {
            thumbnail=withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(video.uri,android.util.Size(240,160),null) }.getOrNull()
            }
        }
        if(thumbnail!=null) Image(bitmap=thumbnail!!.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Movie,null,tint=Accent,modifier=Modifier.size(29.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=.68f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=2.dp)) {Text(clock(video.duration),color=Color.White,fontSize=10.sp)}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoList(videos:List<LocalVideo>,grid:Boolean,library:LocalLibrary,onOpen:(LocalVideo)->Unit,onMenu:(LocalVideo)->Unit,favorites:Set<String>,empty:String, selected:Set<Long> = emptySet(),onSelect:(Long)->Unit = {}) {
    if(videos.isEmpty()) {EmptyState(empty,"Your local videos will appear here."){};return}
    if(grid) {
        LazyVerticalGrid(columns=GridCells.Adaptive(minSize=160.dp),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            gridItems(videos,key={it.id}) {video->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(if(video.id in selected) Accent.copy(alpha=.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f))
                    .combinedClickable(onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},onLongClick={onSelect(video.id)}).padding(8.dp)) {
                    Box {
                        VideoThumb(video,Modifier.fillMaxWidth().aspectRatio(1.5f))
                        if(video.id in selected) Icon(Icons.Default.CheckCircle,"Selected",tint=Accent,modifier=Modifier.align(Alignment.TopEnd).padding(5.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth().height(42.dp),contentAlignment=Alignment.TopStart) {
                        Text(video.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=13.sp,fontWeight=FontWeight.Medium,lineHeight=18.sp)
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("${fileSize(video.size)} · ${clock(video.duration)}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=Muted)
                        IconButton(onClick={onMenu(video)},modifier=Modifier.size(30.dp)){Icon(Icons.Default.MoreVert,"Video options",modifier=Modifier.size(19.dp))}
                    }
                }
            }
        }
    } else LazyColumn(contentPadding=PaddingValues(horizontal=13.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(videos,key={it.id}){video->
            Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f),modifier=Modifier.fillMaxWidth().combinedClickable(onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},onLongClick={onSelect(video.id)})) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box {
                        VideoThumb(video,Modifier.size(width=116.dp,height=76.dp))
                        if(video.id in selected) Icon(Icons.Default.CheckCircle,"Selected",tint=Accent,modifier=Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(video.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("${fileSize(video.size)} • ${clock(video.duration)}",fontSize=11.sp,color=Muted)
                        val progress=library.position(video.id)
                        if(progress>0 && video.duration>0){
                            Spacer(Modifier.height(6.dp))
                            val ratio=(progress.toFloat()/video.duration.coerceAtLeast(1)).coerceIn(0f,1f)
                            Box(Modifier.fillMaxWidth().height(3.dp).background(Accent.copy(alpha=.15f),RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Accent,RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                    IconButton(onClick={onMenu(video)}){Icon(Icons.Default.MoreVert,"Video options")}
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title:String,detail:String,actions:@Composable ()->Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary,null,tint=Accent,modifier=Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp));Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp)
            Spacer(Modifier.height(5.dp));Text(detail,color=Muted,fontSize=13.sp)
            Spacer(Modifier.height(12.dp));actions()
        }
    }
}

@Composable
private fun SettingsHeading(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp)) {
        Text(title,fontWeight=FontWeight.Bold,fontSize=19.sp)
        Text(subtitle,color=Muted,fontSize=12.sp)
    }
}

@Composable
private fun SettingsToggle(icon: androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f)) {
        Row(Modifier.fillMaxWidth().clickable{onChange(!checked)}.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=Accent,modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Medium);Text(subtitle,color=Muted,fontSize=12.sp)}
            Switch(checked=checked,onCheckedChange=onChange)
        }
    }
}

@Composable
private fun MeScreen(videos:List<LocalVideo>,favorites:Set<String>,playlists:List<String>,library:LocalLibrary,open:(String)->Unit) {
    val context=LocalContext.current
    val storage=remember { deviceStorageInfo() }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color=Color(0xFFEBF3FF),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {Image(painter=painterResource(id=R.drawable.videonic_brand),contentDescription="Videonic Lite",modifier=Modifier.size(52.dp));Spacer(Modifier.width(13.dp));Column{Text("Videonic Lite",color=Ink,fontSize=23.sp,fontWeight=FontWeight.Bold)}}
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Metric("Videos","${videos.size}")
                        Metric("Storage","${fileSize(storage.first)} / ${fileSize(storage.second)}")
                        Metric("Favorites","${favorites.size}")
                    }
                }
            }
        }
        item { Text("Collections",modifier=Modifier.padding(top=12.dp,bottom=3.dp),fontWeight=FontWeight.Bold,fontSize=18.sp) }
        item { MeEntry(Icons.Default.Favorite,"Favorites","Saved videos"){open("Favorites")} }
        item { MeEntry(Icons.Default.PlaylistPlay,"Playlists","${playlists.size} lists"){open("Playlists")} }
        item { MeEntry(Icons.Default.Lock,"Private Folder","PIN-protected collection"){open("Private Folder")} }
        item { MeEntry(Icons.Default.DeleteOutline,"Recycle Bin","Review recently trashed videos"){open("Recycle Bin")} }
        item { Text("Preferences",modifier=Modifier.padding(top=12.dp,bottom=3.dp),fontWeight=FontWeight.Bold,fontSize=18.sp) }
        item { MeEntry(Icons.Default.Settings,"Settings","Appearance and preferences"){open("Settings")} }
        item { MeEntry(Icons.Default.Brightness6,"Theme","System, Light or Dark"){open("Theme")} }
        item { MeEntry(Icons.Default.Info,"About","App information and legal pages"){open("About")} }
        item { MeEntry(Icons.Default.Email,"Support & Feedback","Report a problem or suggest a feature"){open("Support & Feedback")} }
        item { MeEntry(Icons.Default.Info,"Privacy Policy","Media access and device data"){open("Privacy Policy")} }
        item { MeEntry(Icons.Default.Description,"Terms of Use","Your responsibilities and limitations"){open("Terms of Use")} }
    }
}

@Composable
private fun Metric(label:String,value:String) { Column { Text(value,color=Ink,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(label,color=Muted,fontSize=11.sp) } }
@Composable
private fun MeEntry(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=Accent);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Medium);Text(subtitle,color=Muted,fontSize=12.sp)};Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
private fun clock(ms:Long):String {val s=ms.coerceAtLeast(0)/1000;return if(s>=3600)String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60) else String.format(Locale.US,"%02d:%02d",s/60,s%60)}
private fun fileSize(bytes:Long):String=if(bytes>=1024L*1024*1024)String.format(Locale.US,"%.1f GB",bytes.toDouble()/(1024*1024*1024)) else String.format(Locale.US,"%.1f MB",bytes.toDouble()/(1024*1024))
private fun speedLabel(value:Float)=if(value==value.toInt().toFloat())"${value.toInt()}.0" else value.toString()
private fun videoComparator(sort: String): Comparator<LocalVideo> = when (sort){"Oldest"->compareBy{it.modified};"Name A-Z"->compareBy{it.title.lowercase()};"Name Z-A"->compareByDescending{it.title.lowercase()};"Largest"->compareByDescending{it.size};"Smallest"->compareBy{it.size};"Longest"->compareByDescending{it.duration};"Shortest"->compareBy{it.duration};else->compareByDescending{it.modified}}


@Composable
private fun PlayerLevelDialog(title:String,value:Float,onChange:(Float)->Unit,onDismiss:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        shape=RoundedCornerShape(24.dp),
        title={Text(title,fontWeight=FontWeight.Bold)},
        text={Column {
            Text("${(value*100).toInt()}%",fontSize=22.sp,color=Accent,fontWeight=FontWeight.Bold)
            Slider(value=value.coerceIn(0f,1f),onValueChange=onChange)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={onChange((value-.1f).coerceAtLeast(0f))}) {Icon(Icons.Default.Remove,null);Text("Lower")}
                TextButton(onClick={onChange((value+.1f).coerceAtMost(1f))}) {Icon(Icons.Default.Add,null);Text("Higher")}
            }
        }},
        confirmButton={TextButton(onClick=onDismiss){Text("Done")}}
    )
}

private fun deviceStorageInfo():Pair<Long,Long> = runCatching {
    val fs=StatFs(Environment.getDataDirectory().absolutePath)
    val total=fs.totalBytes
    val used=(total-fs.availableBytes).coerceAtLeast(0L)
    used to total
}.getOrDefault(0L to 0L)

@Composable
private fun AppearanceModePicker(mode:String,onChange:(String)->Unit) {
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("App appearance",fontWeight=FontWeight.Bold,fontSize=17.sp)
            Text("Choose a theme for the library and settings",fontSize=12.sp,color=Muted)
            Spacer(Modifier.height(9.dp))
            listOf("system" to "Follow device settings","light" to "Light","dark" to "Dark").forEach { (value,label) ->
                Row(Modifier.fillMaxWidth().clickable{onChange(value)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=mode==value,onClick={onChange(value)})
                    Spacer(Modifier.width(8.dp))
                    Text(label,modifier=Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PrivateFolderScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.Lock,null,tint=Accent,modifier=Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Private Folder",fontWeight=FontWeight.Bold,fontSize=22.sp)
        Spacer(Modifier.height(8.dp))
        Text("Secure importing and encrypted playback are not enabled in this build. Your videos have not been moved or hidden.",color=Muted)
    }
}

@Composable
private fun RecycleBinScreen() {
    val context=LocalContext.current
    var trashed by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var restore by remember { mutableStateOf<Pair<Uri,String>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        restore=null
        reload++
    }
    LaunchedEffect(reload) {
        busy=true
        if(Build.VERSION.SDK_INT>=30) {
            val result=withContext(Dispatchers.IO) {runCatching {
                val q=android.os.Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                }
                val found=mutableListOf<Pair<Uri,String>>()
                context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),q,null)?.use { cursor ->
                    val id=cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val name=cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    while(cursor.moveToNext()) found.add(
                        android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cursor.getLong(id)) to (cursor.getString(name)?:"Video"))
                }
                found
            }}
            trashed=result.getOrDefault(emptyList())
            error=result.exceptionOrNull()?.localizedMessage
        }else error="Recycle Bin requires Android 11 or newer"
        busy=false
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Recycle Bin",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Videos in Android's system trash may be restored until the device removes them automatically. Other gallery apps may use a different trash system.",color=Muted)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick={reload++}){Text("Refresh trash")}
        if(busy) CircularProgressIndicator()
        else if(error!=null) Text("Unable to access trash: $error")
        else if(trashed.isEmpty()) Text("No trashed videos available")
        else LazyColumn {items(trashed,key={it.first.toString()}) { video ->
            ListItem(headlineContent={Text(video.second,maxLines=2)},
                leadingContent={Icon(Icons.Default.Restore,"Restore")},
                trailingContent={TextButton(onClick={restore=video}){Text("Restore")}})
        }}
    }
    restore?.let{video->AlertDialog(onDismissRequest={restore=null},title={Text("Restore video?")},
        text={Text("Android will ask you to approve restoring ${video.second}.")},
        confirmButton={TextButton(onClick={
            if(Build.VERSION.SDK_INT>=30)runCatching {
                val pi=MediaStore.createTrashRequest(context.contentResolver,listOf(video.first),false)
                restoreLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure{error=it.localizedMessage;restore=null}
        }){Text("Continue")}},
        dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}})}
}

@Composable
private fun PolicyScreen(page:String) {
    val sections:List<Pair<String,String>> = when(page) {
        "Privacy Policy" -> listOf(
            "Overview" to "Videonic Lite is a local video player. This description applies to this Android build. It is not a substitute for the developer's published Google Play privacy policy.",
            "Media library" to "If you allow video access, the app reads filenames, durations, file sizes and media locations through Android MediaStore to display videos and play the files you choose. The app does not upload your videos as part of normal playback.",
            "Preferences and history" to "Favorites, playlists, watch positions, view preferences and playback settings are stored on your device in application storage. They may be erased if you clear app data or uninstall the app.",
            "Sharing and feedback" to "Sharing uses Android's chooser. Feedback opens an email application so you can review and send your message. Emails you send may contain the issue description and basic device details. Your email provider handles delivery under its own privacy practices.",
            "Permission control" to "You can revoke media access in Android Settings. Renaming, trashing or deleting other apps' media may require Android's separate consent dialog. The app does not request unrestricted all-files access.",
            "Security and changes" to "A device can still be accessed by other apps with their own permissions. Do not use a collection marked unavailable as a secure vault. This information should be updated if analytics, ads, crash reporting or an online backend is introduced."
        )
        "Terms of Use" -> listOf(
            "Purpose" to "Use Videonic Lite to browse and play media that you own or are authorized to access.",
            "Your files" to "You are responsible for protecting important files. Android may ask you to confirm file operations. Back up media before permanently deleting it.",
            "Media compatibility" to "Playback depends on device decoding capabilities, file condition and Android Media3 support. Not every audio, video or subtitle format is available on every device.",
            "Third-party services" to "When you share media or send feedback, you choose an external app and its own terms apply.",
            "Limitations" to "The player is offered without a guarantee that every media file will play or that device data cannot be lost. Applicable consumer rights remain unaffected."
        )
        "Permissions & Storage" -> listOf(
            "Video access" to "Android asks for access to local videos so you can see them in Videos and Folders. The app uses the Android media library rather than unrestricted file-system scanning.",
            "File changes" to "On supported Android versions, moving videos to trash requires a system approval dialog. Other file management may need its own approval. Granting video-read access does not authorize automatic deletion or rename.",
            "Notifications" to "Media playback may use a notification when a foreground playback service runs. Notification permission rules vary by Android version.",
            "Device storage" to "The Me page shows device internal storage usage and total capacity, which includes other apps and files, not just videos.",
            "Your choices" to "You can revoke permissions or clear locally saved history and preferences in Android settings. Revoke access if you no longer want the app to see local videos."
        )
        else -> listOf(
            "Android Jetpack Compose" to "The application interface uses Jetpack Compose and Material components from AndroidX.",
            "AndroidX Media3" to "Video playback is powered by AndroidX Media3, including ExoPlayer and media sessions.",
            "Other libraries" to "The app also uses Kotlin, Kotlin coroutines, AndroidX Activity and lifecycle components, and Guava types used by Media3. Consult the matching dependency versions' licenses in the project before publication.",
            "License notices" to "This in-app summary is not a complete reproduction of all third-party license notices. Generate and ship the actual dependency license texts before a public release."
        )
    }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(page,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall)}
        items(sections){section->Column {
            Text(section.first,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(section.second,style=MaterialTheme.typography.bodyMedium)
        }}
    }
}
