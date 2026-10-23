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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.videonic.lite.ui.SecurePrivateFolder
import com.videonic.lite.ui.FeedbackScreen
import com.videonic.lite.security.PrivateVault
import com.videonic.lite.player.VideonicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// VIDEONIC_UI_V7_PLAYLISTS_SETTINGS_FAVORITES_VAULT_TRASH
// VIDEONIC_QUALITY_UPDATE_V1
private val Accent = Color(0xFF2563EB)
private val Pale = Color(0xFFF3F5FB)
// Text tones follow MaterialTheme, including System Dark mode.
private val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground
private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Speeds = listOf(.25f,.5f,1f,1.5f,2f,2.5f,3f,4f)
// VIDEONIC_LIBRARY_POLISH_20261017
private fun resolutionLabel(width:Int,height:Int):String {
    val shortSide=minOf(width,height)
    return when {
        shortSide>=2160 -> "4K"
        shortSide>=1440 -> "2K"
        shortSide>=1080 -> "1080p"
        shortSide>=720 -> "720p"
        shortSide>=480 -> "480p"
        shortSide>0 -> "${shortSide}p"
        else -> "—"
    }
}

// Resolution colors are intentionally distinct across Videos and Recent.
@Composable
private fun qualityTint(width:Int,height:Int):Color {
    val dark=MaterialTheme.colorScheme.background.luminance()<.22f
    return when {
        minOf(width,height)>=2160 -> if(dark) Color(0xFFFFD16B) else Color(0xFFC05B04)
        minOf(width,height)>=1440 -> if(dark) Color(0xFFD6ADFF) else Color(0xFF7C3AED)
        minOf(width,height)>=1080 -> if(dark) Color(0xFF92BAFF) else Color(0xFF2563EB)
        minOf(width,height)>=720 -> if(dark) Color(0xFF77E1C7) else Color(0xFF078A78)
        minOf(width,height)>=480 -> if(dark) Color(0xFF80D1E5) else Color(0xFF087A98)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun QualityInline(width:Int,height:Int,fontSize: androidx.compose.ui.unit.TextUnit=12.sp) {
    Text(resolutionLabel(width,height),color=qualityTint(width,height),
        fontWeight=FontWeight.Bold,fontSize=fontSize,maxLines=1)
}

// Preserve the actual page hierarchy rather than dropping to the Me root.
private fun parentPage(page:String?):String?=when {
    page=="Theme" -> "Settings"
    page?.startsWith("Playlist:")==true -> "Playlists"
    page in listOf("Privacy Policy","Terms of Use","Open Source Licenses","Permissions & Storage") -> "About"
    else -> null
}


class MainActivity : ComponentActivity() {
    companion object { const val ACTION_OPEN_CURRENT_VIDEO = "com.videonic.lite.action.OPEN_CURRENT_VIDEO" }
    var notificationOpenRequest by mutableIntStateOf(0)
        private set
    private var future: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private val pipActive = mutableStateOf(false)
    // Updated by Compose to avoid automatically entering PiP for file pickers or settings screens.
    var playerScreenVisible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_OPEN_CURRENT_VIDEO) notificationOpenRequest++
        val token = SessionToken(this, ComponentName(this, VideonicPlaybackService::class.java))
        future = MediaController.Builder(this, token).buildAsync().also { f ->
            f.addListener({ runCatching { controller.value = f.get() } }, MoreExecutors.directExecutor())
        }
        setContent { VideonicApp(controller.value, this, pipActive.value) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_CURRENT_VIDEO) notificationOpenRequest++
    }
    fun finishNotificationOpen() { notificationOpenRequest = 0 }
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive.value = isInPictureInPictureMode
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val headphones = getSharedPreferences("videonic", MODE_PRIVATE)
            .getBoolean("background_audio_enabled", false)
        // Ordinary video playback enters PiP on Home. Headphone mode retains background audio.
        if (!headphones && playerScreenVisible && controller.value?.isPlaying == true && !pipActive.value) {
            pictureInPicture()
        }
    }
    fun pictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this,"Picture-in-picture is not supported on this device",Toast.LENGTH_LONG).show()
            return
        }
        if (controller.value?.currentMediaItem == null) {
            Toast.makeText(this,"Play a video before entering PiP",Toast.LENGTH_SHORT).show()
            return
        }
        val videoSize=controller.value?.videoSize
        val w=(videoSize?.width ?: 16).coerceAtLeast(1)
        val h=(videoSize?.height ?: 9).coerceAtLeast(1)
        val ratio=(w.toFloat()/h).coerceIn(0.42f,2.39f)
        runCatching {
            val base=1000
            val params=PictureInPictureParams.Builder()
                .setAspectRatio(Rational((ratio*base).toInt().coerceAtLeast(1),base))
                .build()
            val entered=enterPictureInPictureMode(params)
            if(!entered) Toast.makeText(this,"Couldn't open picture-in-picture. Check Android app settings.",Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this,"Picture-in-picture unavailable: ${it.localizedMessage}",Toast.LENGTH_LONG).show()
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
    var librarySearchVisible by remember { mutableStateOf(false) }
    var folderDescending by remember { mutableStateOf(false) }
    var folderSortField by remember { mutableStateOf("Name") }
    val appPrefs=remember { context.getSharedPreferences("videonic", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(appPrefs.getString("appearance_mode", "system") ?: "system") }
    var navigationStyle by remember { mutableStateOf(appPrefs.getString("navigation_style", "classic") ?: "classic") }
    var navigationStyleDialog by remember { mutableStateOf(false) }
    val systemDark=isSystemInDarkTheme()
    val dark=when(themeMode){"dark"->true;"light"->false;else->systemDark}
    var resumePlayback by remember { mutableStateOf(library.resumePlayback()) }
    var autoPlayNext by remember { mutableStateOf(library.autoPlayNext()) }
    var autoRotate by remember { mutableStateOf(library.autoRotate()) }
    var keepScreenOn by remember { mutableStateOf(library.keepScreenOn()) }
    var defaultSpeed by remember { mutableFloatStateOf(library.defaultSpeed()) }
    var seekSeconds by remember { mutableIntStateOf(appPrefs.getInt("seek_seconds",10)) }
    var gestureLevelsEnabled by remember { mutableStateOf(appPrefs.getBoolean("gesture_levels_enabled",true)) }
    var doubleTapEnabled by remember { mutableStateOf(appPrefs.getBoolean("double_tap_seek_enabled",true)) }
    var longPressBoost by remember { mutableFloatStateOf(appPrefs.getFloat("long_press_boost",2f)) }
    var settingsSpeedDialog by remember { mutableStateOf<String?>(null) }
    var playlistCreateFor by remember { mutableStateOf<LocalVideo?>(null) }
    var confirmPlaylistDelete by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(library.favorites()) }
    var recent by remember { mutableStateOf(library.recent()) }
    var playlistNames by remember { mutableStateOf(library.playlists()) }
    var playlistDialog by remember { mutableStateOf(false) }
    var resumeCandidate by remember { mutableStateOf<LocalVideo?>(null) }
    var playlistPickerName by remember { mutableStateOf<String?>(null) }
    var playlistPickerIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
    // Reconnect the player surface to the EXISTING Media3 session. Never start a second player.
    // Private file:// playback remains behind the Private Folder unlock if activity was recreated.
    LaunchedEffect(controller, videos, activity.notificationOpenRequest) {
        if(activity.notificationOpenRequest <= 0) return@LaunchedEffect
        val item=controller?.currentMediaItem ?: return@LaunchedEffect
        val uri=item.localConfiguration?.uri
        val liveVideo = videos.firstOrNull { it.id.toString()==item.mediaId }
            ?: playing?.takeIf { it.id.toString()==item.mediaId }
            ?: uri?.takeIf { it.scheme=="content" }?.let { mediaUri ->
                LocalVideo(item.mediaId.toLongOrNull() ?: -1L, mediaUri,
                    android.net.Uri.decode(mediaUri.lastPathSegment ?: "Video"),
                    controller.duration.coerceAtLeast(0L),0L,"Media",System.currentTimeMillis())
            }
        if(liveVideo!=null) {
            playing=liveVideo;playerExpanded=true;controlsVisible=true;lockPlayer=false;controlTick++
            activity.finishNotificationOpen()
        } else if(!loading && uri?.scheme=="file") {
            // Do not bypass the PIN for private files after the Activity was recreated.
            tab="Me";subpage="Private Folder";activity.finishNotificationOpen()
        }
    }
    fun startVideo(video: LocalVideo, startAt:Long) {
        // Headphone mode must be deliberately enabled for each opened video.
        backgroundAudio=false
        appPrefs.edit().putBoolean("background_audio_enabled",false).apply()
        playing=video; playerExpanded=true; controlsVisible=true; lockPlayer=false; controlTick++
        library.visited(video.id); recent=library.recent()
        val queue=if(autoPlayNext && video.folder != "Private") videos.ifEmpty { listOf(video) } else listOf(video)
        val start=queue.indexOfFirst{it.id==video.id}.coerceAtLeast(0)
        controller?.setMediaItems(queue.map{MediaItem.Builder().setUri(it.uri)
            .setMediaId(it.id.toString()).build()},start,startAt)
        controller?.repeatMode=repeatMode
        controller?.shuffleModeEnabled=shuffled
        controller?.prepare()
        speed=defaultSpeed
        controller?.setPlaybackSpeed(speed)
        activity.requestedOrientation=if(autoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if(keepScreenOn) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.play()
    }
    fun openVideo(video:LocalVideo) {
        val saved=if(resumePlayback) library.position(video.id) else 0L
        if(saved>5000L) resumeCandidate=video else startVideo(video,0L)
    }
    fun closePlayer() {
        playing?.let { library.savePosition(it.id, position) }
        controller?.stop(); controller?.clearMediaItems()
        if(playing?.folder == "Private") PrivateVault(context).cleanPlaybackCache()
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
            subpage!=null -> subpage=parentPage(subpage)
            folder!=null -> { folder=null;query="" }
            else -> { tab="Videos";query="" }
        }
    }

    SideEffect { activity.playerScreenVisible = playerExpanded && playing != null }
    var lastRootBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(!playerExpanded && folder==null && subpage==null && tab=="Videos") {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastRootBackAt <= 2000L) activity.finish()
        else { lastRootBackAt=now; Toast.makeText(context,"Press Back again to exit",Toast.LENGTH_SHORT).show() }
    }

    val scheme=if(dark) darkColorScheme(
        primary=Color(0xFF89B2FF),onPrimary=Color(0xFF071225),
        background=Color(0xFF0E1422),onBackground=Color(0xFFF5F7FF),
        surface=Color(0xFF1B273B),onSurface=Color(0xFFF5F7FF),
        surfaceVariant=Color(0xFF28364C),onSurfaceVariant=Color(0xFFAFBED4),
        outline=Color(0xFF677993),error=Color(0xFFFF8B89)
    ) else lightColorScheme(
        primary=Accent,background=Color.White,onBackground=Color(0xFF0F172A),
        surface=Color.White,onSurface=Color(0xFF0F172A),
        surfaceVariant=Color(0xFFF0F3F8),onSurfaceVariant=Color(0xFF64748B)
    )
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
                                    if(!lockPlayer && doubleTapEnabled){
                                        controller?.let { player ->
                                            val delta=if(tap.x>size.width/2f)seekSeconds*1000L else -seekSeconds*1000L
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
                                                    if(!gestureLevelsEnabled) 0 else if(startedOnLeft) 2 else 3
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
                                    onDragStart={
                                        index=Speeds.indexOf(longPressBoost).coerceAtLeast(0)
                                        speed=Speeds[index];controller?.setPlaybackSpeed(speed)
                                        gestureLabel="Speed ${speedLabel(speed)}x";gestureTick++;controlsVisible=true
                                    },
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
                                DropdownMenuItem(text={Text("Audio tracks")},leadingIcon={Icon(Icons.Default.Audiotrack,null)},onClick={trackDialog="Audio";overflowOpen=false;controlsVisible=true;controlTick++})
                                DropdownMenuItem(text={Text("Decoder details")},leadingIcon={Icon(Icons.Default.Memory,null)},onClick={decoderInfo=true;overflowOpen=false;controlsVisible=true;controlTick++})
                                DropdownMenuItem(text={Text("Repeat: "+when(repeatMode){Player.REPEAT_MODE_ONE->"One";Player.REPEAT_MODE_ALL->"All";else->"Off"})},leadingIcon={Icon(Icons.Default.Repeat,null)},onClick={
                                    repeatMode=when(repeatMode){Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ONE;Player.REPEAT_MODE_ONE->Player.REPEAT_MODE_ALL;else->Player.REPEAT_MODE_OFF}
                                    controller?.repeatMode=repeatMode;overflowOpen=false;controlsVisible=true;controlTick++
                                })
                                DropdownMenuItem(text={Text(if(shuffled)"Shuffle: On" else "Shuffle: Off")},leadingIcon={Icon(Icons.Default.Shuffle,null)},onClick={shuffled=!shuffled;controller?.shuffleModeEnabled=shuffled;overflowOpen=false;controlsVisible=true;controlTick++})
                                DropdownMenuItem(text={Text("Picture in picture")},leadingIcon={Icon(Icons.Default.PictureInPictureAlt,null)},onClick={overflowOpen=false;controlsVisible=true;activity.pictureInPicture()})
                                DropdownMenuItem(text={Text("Save snapshot")},leadingIcon={Icon(Icons.Default.CameraAlt,null)},onClick={overflowOpen=false;controlsVisible=true;activity.captureFrame();controlTick++})
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={controller?.seekToPreviousMediaItem();controlTick++}) { Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White) }
                        IconButton(onClick={controller?.let { it.seekTo((it.currentPosition-seekSeconds*1000L).coerceAtLeast(0)) };controlTick++}) { Icon(Icons.Default.FastRewind,"Seek back",tint=Color.White,modifier=Modifier.size(32.dp)) }
                        Surface(shape=RoundedCornerShape(100),color=Accent,modifier=Modifier.size(74.dp)) {
                            IconButton(onClick={if(controller?.isPlaying==true)controller.pause() else controller?.play();controlTick++}) { Icon(if(playingNow) Icons.Default.Pause else Icons.Default.PlayArrow,"Play or pause",tint=Color.White,modifier=Modifier.size(42.dp)) }
                        }
                        IconButton(onClick={controller?.let { it.seekTo(it.currentPosition+seekSeconds*1000L) };controlTick++}) { Icon(Icons.Default.FastForward,"Seek forward",tint=Color.White,modifier=Modifier.size(32.dp)) }
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
                if(!inPip && decoderInfo) AlertDialog(onDismissRequest={decoderInfo=false},title={Text("Video decoder")},text={Text("Video resolution: ${controller?.videoSize?.let { "${it.width} x ${it.height}" } ?: "Unknown"}. The player uses Android Media3 decoder selection with hardware preference and software fallback. Manual switching requires rebuilding the playback engine and is not yet available.")},confirmButton={TextButton(onClick={decoderInfo=false}){Text("OK")}})
                if(!inPip && trackDialog!=null && controller!=null) {
                    val selectedType=if(trackDialog=="Audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    val groups=controller.currentTracks.groups.filter { it.type==selectedType }
                    val subtitles=selectedType==C.TRACK_TYPE_TEXT
                    AlertDialog(
                        onDismissRequest={trackDialog=null;controlsVisible=true;controlTick++},
                        shape=RoundedCornerShape(26.dp),
                        icon={Icon(if(subtitles) Icons.Default.Subtitles else Icons.Default.Audiotrack,null,tint=Accent,modifier=Modifier.size(28.dp))},
                        title={Text(if(subtitles) "Subtitle tracks" else "Audio tracks",fontWeight=FontWeight.Bold)},
                        text={
                            Column(Modifier.fillMaxWidth().heightIn(max=390.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(if(subtitles) "Choose an embedded subtitle track for this video" else "Choose the audio track used during playback",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                                if(subtitles) {
                                    val off=controller.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                    Surface(shape=RoundedCornerShape(14.dp),color=if(off) Accent.copy(alpha=.11f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f)) {
                                        Row(Modifier.fillMaxWidth().clickable {
                                            controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true)
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                                            trackDialog=null;controlsVisible=true;controlTick++
                                        }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Icon(Icons.Default.Subtitles,null,tint=if(off) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(12.dp));Text("Subtitles off",Modifier.weight(1f),fontWeight=FontWeight.Medium)
                                            RadioButton(selected=off,onClick=null)
                                        }
                                    }
                                }
                                if(groups.isEmpty()) {
                                    Text(if(subtitles) "No embedded subtitles found in this video." else "No selectable audio tracks found for this video.",modifier=Modifier.padding(vertical=18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                groups.forEachIndexed { groupIndex,group ->
                                    for(i in 0 until group.length) {
                                        val fmt=group.getTrackFormat(i)
                                        val lang=fmt.language?.takeUnless{it=="und" || it.isBlank()} ?: "Language unknown"
                                        val title=fmt.label?.takeIf{it.isNotBlank()} ?: "${if(subtitles) "Subtitle" else "Audio"} ${groupIndex+1}.${i+1}"
                                        val supported=group.isTrackSupported(i)
                                        val chosen=group.isTrackSelected(i)
                                        val description=listOfNotNull(lang,fmt.sampleMimeType?.substringAfter('/'),
                                            if(!subtitles && fmt.channelCount>0) "${fmt.channelCount} ch" else null,
                                            if(!supported) "Unsupported" else null).joinToString(" · ")
                                        Surface(shape=RoundedCornerShape(14.dp),color=if(chosen) Accent.copy(alpha=.11f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)) {
                                            Row(Modifier.fillMaxWidth().clickable(enabled=supported) {
                                                controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon()
                                                    .setTrackTypeDisabled(selectedType,false)
                                                    .clearOverridesOfType(selectedType)
                                                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup,listOf(i)))
                                                    .build()
                                                trackDialog=null;controlsVisible=true;controlTick++
                                            }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(title,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                                    Text(description,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
                                                }
                                                RadioButton(selected=chosen,onClick=null,enabled=supported)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton={TextButton(onClick={trackDialog=null;controlsVisible=true;controlTick++}){Text("Done")}}
                    )
                }
                if(!inPip && speedDialog) AlertDialog(onDismissRequest={speedDialog=false;controlTick++},shape=RoundedCornerShape(22.dp),title={Text("Playback speed")},text={
                    Column { Speeds.chunked(4).forEach { row -> Row { row.forEach { option ->
                        TextButton(onClick={speed=option;controller?.setPlaybackSpeed(speed);speedDialog=false;controlTick++},modifier=Modifier.weight(1f)) { Text("${speedLabel(option)}x",color=if(speed==option)Accent else MaterialTheme.colorScheme.onSurface) }
                    } } } }
                },confirmButton={TextButton(onClick={speedDialog=false;controlTick++}){Text("Close")}})
            }
        } else {
            Scaffold(
                containerColor=if(dark)surface else if(tab=="Me")Pale else Color.White,
                topBar={
                    if(!inPip && !(folder==null && subpage==null && (tab=="Videos" || tab=="Folders"))) Column(Modifier.fillMaxWidth().background(
                        if(tab=="Me" && folder==null && subpage==null && !dark) Pale else surface
                    ).statusBarsPadding()) {
                        val root = subpage == null && folder == null
                        val label = when {
                            folder != null -> folder!!
                            subpage != null -> if(subpage!!.startsWith("Playlist:")) subpage!!.removePrefix("Playlist:") else subpage!!
                            else -> tab
                        }
                        Row(Modifier.fillMaxWidth()
                            .padding(start=20.dp,end=20.dp,top=44.dp,bottom=12.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            if(!root) {
                                Surface(Modifier.size(44.dp).clickable {
                                    if(folder!=null)folder=null else subpage=parentPage(subpage)
                                    query=""
                                },shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surface) {
                                    Box(contentAlignment=Alignment.Center){Icon(Icons.Default.ArrowBack,"Back",tint=textColor)}
                                }
                                Spacer(Modifier.width(14.dp))
                            }
                            Text(label,Modifier.weight(1f),fontSize=if(root)32.sp else 29.sp,
                                fontWeight=FontWeight.ExtraBold,color=textColor,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                            if(root && tab=="Videos") {
                                Image(painterResource(R.drawable.videonic_brand),"Videonic Lite",
                                    Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)))
                            }
                            if(subpage=="Playlists") {
                                Button(onClick={playlistDialog=true},shape=RoundedCornerShape(50)){
                                    Icon(Icons.Default.Add,null); Spacer(Modifier.width(4.dp)); Text("New")
                                }
                            }
                        }
                        if(root && tab=="Videos") {
                            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth()
                                .padding(horizontal=20.dp,vertical=6.dp),singleLine=true,
                                shape=RoundedCornerShape(32.dp),placeholder={Text("Search videos",color=Muted)},
                                leadingIcon={Icon(Icons.Default.Search,null,tint=Muted)},
                                colors=OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor=Color.Transparent,
                                    unfocusedBorderColor=Color.Transparent,
                                    focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f),
                                    unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f)))
                            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clickable{sortDialog=true}.padding(vertical=8.dp),
                                    verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sort,"Sort",tint=textColor,modifier=Modifier.size(21.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(sortDescription(sort),color=textColor,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=1)
                                }
                                IconButton(onClick={grid=!grid;library.setGrid(grid)}){
                                    Icon(if(grid)Icons.Default.ViewList else Icons.Default.GridView,
                                        "Change view",tint=Muted)
                                }
                            }
                        }
                        if(root && tab=="Folders") {
                            Text("${videos.map{it.folder}.distinct().size} folders",
                                color=Muted,modifier=Modifier.padding(start=22.dp,bottom=10.dp),fontSize=14.sp)
                        }
                        if(root && tab=="Recent") {
                            Row(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,bottom=15.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text("${recent.size} recently watched",Modifier.weight(1f),
                                    color=Muted,fontSize=14.sp)
                                if(recent.isNotEmpty()) Text("Clear",color=Color(0xFFD92D20),
                                    fontSize=14.sp,fontWeight=FontWeight.Bold,
                                    modifier=Modifier.clickable{confirmClear=true}.padding(8.dp))
                            }
                        }
                        if(folder!=null || (subpage=="Favorites") || subpage?.startsWith("Playlist:")==true) {
                            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth()
                                .padding(horizontal=20.dp,vertical=5.dp),singleLine=true,
                                placeholder={Text("Search videos")},leadingIcon={Icon(Icons.Default.Search,null)},
                                shape=RoundedCornerShape(22.dp))
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
                        VideonicBottomNavigation(tab,navigationStyle=="glass") { name ->
                            tab=name;folder=null;subpage=null;query=""
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
                            "Favorites" -> ReferenceFavoritesScreen(
                                videos=videos.filter{it.id.toString() in favorites}.filter{it.title.contains(query,true)},
                                favorites=favorites,onOpen={openVideo(it)},onMenu={videoMenu=it},
                                onFavorite={video->library.toggleFavorite(video.id);favorites=library.favorites()},
                                selected=selectedIds,
                                onSelect={id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id}
                            )
                            "Playlists" -> {
                                if(playlistNames.isEmpty()) ReferenceEmptyPlaylist(onCreate={playlistDialog=true})
                                else ReferencePlaylistList(playlistNames,library) { name ->subpage="Playlist:$name" }
                            }
                            "Theme" -> ReferenceThemeScreen(themeMode) { choice ->
                                themeMode=choice;appPrefs.edit().putString("appearance_mode",choice).apply()
                            }
                            "Private Folder" -> SecurePrivateFolder(onImportedOriginalRemoved={refreshPending.intValue++},onPlay={ file,name ->
                                openVideo(LocalVideo(-(file.name.hashCode().toLong().and(0x7fffffffL)+1L),Uri.fromFile(file),name,0L,file.length(),"Private",System.currentTimeMillis()))
                            })
                            "Recycle Bin" -> RecycleBinScreen(onChanged={refreshPending.value++})
                            "Privacy Policy", "Terms of Use", "Permissions & Storage", "Open Source Licenses" -> PolicyScreen(subpage!!)
                            "Support & Feedback" -> FeedbackScreen()
                            "Settings" -> ReferenceSettingsScreen(
                                resumePlayback=resumePlayback,
                                autoPlayNext=autoPlayNext,
                                defaultSpeed=defaultSpeed,
                                seekSeconds=seekSeconds,
                                autoRotate=autoRotate,
                                keepScreenOn=keepScreenOn,
                                gestureLevels=gestureLevelsEnabled,
                                doubleTap=doubleTapEnabled,
                                longPressBoost=longPressBoost,
                                themeMode=themeMode,
                                grid=grid,
                                navigationStyle=navigationStyle,
                                onResume={resumePlayback=it;library.setResumePlayback(it)},
                                onAutoPlay={autoPlayNext=it;library.setAutoPlayNext(it)},
                                onSpeed={settingsSpeedDialog="speed"},
                                onSeek={settingsSpeedDialog="seek"},
                                onAutoRotate={autoRotate=it;library.setAutoRotate(it);
                                    if(playerExpanded) activity.requestedOrientation=if(it) ActivityInfo.SCREEN_ORIENTATION_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED},
                                onKeepScreenOn={keepScreenOn=it;library.setKeepScreenOn(it);
                                    if(playerExpanded) {
                                        if(it) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    }},
                                onGesture={gestureLevelsEnabled=it;appPrefs.edit().putBoolean("gesture_levels_enabled",it).apply()},
                                onDoubleTap={doubleTapEnabled=it;appPrefs.edit().putBoolean("double_tap_seek_enabled",it).apply()},
                                onBoost={settingsSpeedDialog="boost"},
                                onTheme={subpage="Theme"},
                                onGrid={grid=it;library.setGrid(it)},
                                onNavigationStyle={navigationStyleDialog=true},
                                onDecoderInfo={notice="Videonic currently uses Android Media3 hardware decoding when supported and automatic software fallback. Manual decoder selection is not available in this build."}
                            )
                            "About" -> LazyColumn(
                                contentPadding=PaddingValues(horizontal=20.dp,vertical=20.dp),
                                verticalArrangement=Arrangement.spacedBy(15.dp),
                                horizontalAlignment=Alignment.CenterHorizontally) {
                                item {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                        Image(painterResource(R.drawable.videonic_brand),"Videonic Lite",
                                            Modifier.size(100.dp).clip(RoundedCornerShape(22.dp)))
                                        Spacer(Modifier.height(16.dp))
                                        Text("Videonic Lite",fontWeight=FontWeight.ExtraBold,fontSize=27.sp)
                                        Text("Version 1.0.0",color=Accent,modifier=Modifier.padding(8.dp))
                                    }
                                }
                                item {
                                    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
                                        Text("Videonic Lite helps you browse and play videos, organize favorites and playlists, resume watching, and customize playback. Available formats depend on your Android device.",
                                            modifier=Modifier.padding(20.dp),color=Muted,lineHeight=24.sp,
                                            fontSize=14.sp)
                                    }
                                }
                                item {
                                    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
                                        Column {
                                            listOf("Privacy Policy","Terms of Use","Open Source Licenses","Permissions & Storage").forEachIndexed {index,name ->
                                                ReferenceMeRow(when(index){0->Icons.Default.Shield;1->Icons.Default.Description;2->Icons.Default.Code;else->Icons.Default.Storage},
                                                    name,"Read more",Accent,Color(0xFFDFEAFE)){subpage=name}
                                                if(index<3)HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.22f))
                                            }
                                        }
                                    }
                                }
                            }

                            else -> if(subpage?.startsWith("Playlist:")==true) {
                                val name=subpage!!.removePrefix("Playlist:")
                                val set=library.playlistItems(name)
                                FilledTonalButton(onClick={playlistPickerName=name;playlistPickerIds=emptySet()},modifier=Modifier.padding(horizontal=16.dp)) {
                                    Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add videos")
                                }
                                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("${set.size} videos",Modifier.weight(1f),color=Muted);TextButton(onClick={confirmPlaylistDelete=name}){Text("Delete playlist")}}
                                VideoList(set.mapNotNull{id->videos.find{it.id.toString()==id}}.filter{it.title.contains(query,true)},grid,library,{openVideo(it)},{videoMenu=it},favorites,"Playlist is empty",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})
                            } else MeScreen(videos,favorites,playlistNames,library,{subpage=it})
                        }
                     } else if(tab=="Folders" && folder==null) {
                        val grouped=videos.groupBy{it.folder}.toList()
                        val folderComparator=when(folderSortField) {
                            "Date added" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.maxOfOrNull{it.modified} ?: 0L }
                            "Size" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.sumOf{it.size} }
                            "Duration" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.sumOf{it.duration} }
                            else -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.first.lowercase(Locale.US) }
                        }
                        val groups=grouped.filter{it.first.contains(query,true)}
                            .sortedWith(if(folderDescending) folderComparator.reversed() else folderComparator)
                        FolderLibraryHome(
                            folders=groups,grid=grid,query=query,
                            showSearch=librarySearchVisible || query.isNotBlank(),
                            descending=folderDescending,
                            onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
                            onToggleSort={sortDialog=true},
                            onQueryChange={query=it},
                            onOpenFolder={name->folder=name;query=""}
                        )
                    } else {
                        val shown=when(tab){
                            "Recent"-> recent.mapNotNull{id->videos.find{it.id.toString()==id}}
                            "Folders"-> videos.filter{it.folder==folder}
                            else-> videos
                        }.filter{it.title.contains(query,true)}.let{if(tab=="Recent" && sort=="Newest")it else it.sortedWith(videoComparator(sort))}


                        // Keep scroll states alive while toggling Videos list and grid.
                        key(sort,tab,folder,subpage) {
                            if(tab=="Recent") ReferenceRecentScreen(shown,library,{openVideo(it)}, {videoMenu=it})
                            else if(tab=="Videos" && folder==null) VideosLibraryHome(
                                videos=shown,grid=grid,sortLabel=sort,query=query,
                                showSearch=librarySearchVisible || query.isNotBlank(),
                                library=library,selected=selectedIds,
                                onSelect={id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id},
                                onOpen={openVideo(it)},onMenu={videoMenu=it},
                                onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
                                onResumeVideo={
                                    if(controller==null) {
                                        notice="Player is connecting. Please try again."
                                    } else if(playing!=null && controller.currentMediaItem!=null) {
                                        playerExpanded=true;controlsVisible=true;controlTick++
                                        controller.play()
                                    } else {
                                        val lastVideo=recent.firstNotNullOfOrNull { id ->
                                            videos.firstOrNull { it.id.toString()==id }
                                        }
                                        if(lastVideo==null) notice="No recently played video to resume"
                                        else startVideo(lastVideo,library.position(lastVideo.id).coerceAtLeast(0L))
                                    }
                                },
                                onToggleGrid={grid=!grid;library.setGrid(grid)},
                                onOpenSort={sortDialog=true},onQueryChange={query=it}
                            )
                            else VideoList(shown,grid,library,{openVideo(it)},{videoMenu=it},favorites,
                                "No videos found",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})
                        }
                    }
                }
            }
        }
        if(navigationStyleDialog) AlertDialog(
            onDismissRequest={navigationStyleDialog=false},shape=RoundedCornerShape(28.dp),
            title={Text("Bottom navigation style",fontWeight=FontWeight.Bold)},
            text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                listOf("classic" to "Classic", "glass" to "Liquid glass").forEach { (value,label) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                        navigationStyle=value
                        appPrefs.edit().putString("navigation_style",value).apply()
                        navigationStyleDialog=false
                    }.padding(horizontal=8.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=navigationStyle==value,onClick={
                            navigationStyle=value
                            appPrefs.edit().putString("navigation_style",value).apply()
                            navigationStyleDialog=false
                        })
                        Spacer(Modifier.width(10.dp))
                        Text(label,modifier=Modifier.weight(1f),fontWeight=FontWeight.Medium)
                    }
                }
            }},confirmButton={TextButton(onClick={navigationStyleDialog=false}){Text("Done")}}
        )
        settingsSpeedDialog?.let { kind ->
            val values=when(kind) {
                "seek" -> listOf("5 sec","10 sec","15 sec","30 sec")
                "boost" -> listOf("1.5x","2.0x","2.5x","3.0x","4.0x")
                else -> Speeds.map { "${speedLabel(it)}x" }
            }
            AlertDialog(onDismissRequest={settingsSpeedDialog=null},shape=RoundedCornerShape(28.dp),
                title={Text(when(kind){"seek"->"Seek duration";"boost"->"Long-press speed boost";else->"Default speed"},fontWeight=FontWeight.Bold)},
                text={Column(Modifier.heightIn(max=370.dp).verticalScroll(rememberScrollState())){
                    values.forEach { value ->
                        val selected=when(kind) {
                            "seek" -> value.substringBefore(" ").toInt()==seekSeconds
                            "boost" -> value.removeSuffix("x").toFloat()==longPressBoost
                            else -> value.removeSuffix("x").toFloat()==defaultSpeed
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable {
                            when(kind) {
                                "seek" -> {seekSeconds=value.substringBefore(" ").toInt();appPrefs.edit().putInt("seek_seconds",seekSeconds).apply()}
                                "boost" -> {longPressBoost=value.removeSuffix("x").toFloat();appPrefs.edit().putFloat("long_press_boost",longPressBoost).apply()}
                                else -> {defaultSpeed=value.removeSuffix("x").toFloat();library.setDefaultSpeed(defaultSpeed)}
                            }
                            settingsSpeedDialog=null
                        }.padding(11.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(value,Modifier.weight(1f),fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
                            if(selected)Icon(Icons.Default.CheckCircle,"Selected",tint=Accent)
                        }
                    }
                }},confirmButton={TextButton(onClick={settingsSpeedDialog=null}){Text("Close")}})
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
        if(sortDialog) SortAndViewBottomSheet(
            isFolders=(tab=="Folders" && folder==null),
            videoSort=sort,
            folderSort=folderSortField,
            folderDescending=folderDescending,
            grid=grid,
            onVideoSort={sort=it},
            onFolderSort={folderSortField=it},
            onFolderDescending={folderDescending=it},
            onGrid={grid=it;library.setGrid(it)},
            onDismiss={sortDialog=false}
        )
        if(playlistDialog) AlertDialog(
            onDismissRequest={playlistDialog=false;playlistCreateFor=null},
            shape=RoundedCornerShape(30.dp),
            containerColor=MaterialTheme.colorScheme.surface,
            title={Text(if(playlistCreateFor==null) "New playlist" else "Create playlist",fontWeight=FontWeight.ExtraBold,fontSize=24.sp)},
            text={Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Give your playlist a name.",color=Muted,fontSize=14.sp)
                OutlinedTextField(value=newPlaylistName,onValueChange={newPlaylistName=it.take(45)},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    placeholder={Text("Playlist name")},shape=RoundedCornerShape(17.dp))
            }},
            confirmButton={Button(enabled=newPlaylistName.trim().isNotEmpty(),onClick={
                val name=newPlaylistName.trim()
                if(library.createPlaylist(name)) {
                    playlistCreateFor?.let{library.addToPlaylist(name,it.id)}
                    playlistNames=library.playlists();newPlaylistName="";playlistDialog=false;playlistCreateFor=null;addToPlaylist=null
                } else notice="Choose a unique playlist name"
            },shape=RoundedCornerShape(50.dp)){Text("Create",modifier=Modifier.padding(horizontal=14.dp))}},
            dismissButton={TextButton(onClick={playlistDialog=false;playlistCreateFor=null}){Text("Cancel",color=Muted)}})
        resumeCandidate?.let { candidate ->
            val saved=library.position(candidate.id)
            AlertDialog(onDismissRequest={resumeCandidate=null},shape=RoundedCornerShape(24.dp),
                title={Text("Continue watching?")},
                text={Text("Resume from ${clock(saved)} or start from the beginning?",maxLines=3)},
                confirmButton={TextButton(onClick={resumeCandidate=null;startVideo(candidate,saved)}) {Text("Resume")}},
                dismissButton={TextButton(onClick={resumeCandidate=null;startVideo(candidate,0L)}) {Text("Start over")}})
        }
        playlistPickerName?.let { target ->
            AlertDialog(onDismissRequest={playlistPickerName=null;playlistPickerIds=emptySet()},
                shape=RoundedCornerShape(24.dp),title={Text("Add videos to $target")},
                text={Column {
                    Text("Select videos",color=Muted,fontSize=12.sp)
                    Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState())) {
                        videos.forEach { v ->
                            Row(Modifier.fillMaxWidth().clickable {
                                playlistPickerIds=if(v.id in playlistPickerIds) playlistPickerIds-v.id else playlistPickerIds+v.id
                            }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=v.id in playlistPickerIds,onCheckedChange={ checked ->
                                    playlistPickerIds=if(checked)playlistPickerIds+v.id else playlistPickerIds-v.id
                                })
                                Text(v.title,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                            }
                        }
                    }
                }},confirmButton={TextButton(enabled=playlistPickerIds.isNotEmpty(),onClick={
                    playlistPickerIds.forEach { library.addToPlaylist(target,it) }
                    notice="${playlistPickerIds.size} videos added to $target"
                    playlistPickerName=null;playlistPickerIds=emptySet()
                }) {Text("Add selected")}},
                dismissButton={TextButton(onClick={playlistPickerName=null;playlistPickerIds=emptySet()}){Text("Cancel")}})
        }
        videoMenu?.takeUnless { addToPlaylist!=null || (playlistDialog && playlistCreateFor!=null) }?.let { video ->
            ModalBottomSheet(onDismissRequest={videoMenu=null},
                shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),
                containerColor=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        VideoThumb(video,Modifier.size(width=92.dp,height=65.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(video.title,fontWeight=FontWeight.Bold,maxLines=2,
                                overflow=TextOverflow.Ellipsis)
                            Text("${resolutionLabel(video.width,video.height)} · ${fileSize(video.size)}",
                                color=Muted,fontSize=12.sp)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical=14.dp))
                    SheetAction(Icons.Default.PlayArrow,"Play",Accent){videoMenu=null;openVideo(video)}
                    SheetAction(if(video.id.toString() in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if(video.id.toString() in favorites)"Remove from favorites" else "Add to favorites",
                        Color(0xFFE73559)) {
                        library.toggleFavorite(video.id);favorites=library.favorites()
                        // Keep the video-options bottom sheet open and show the new heart state.
                    }
                    SheetAction(Icons.Default.PlaylistAdd,"Add to playlist",Accent) {
                        addToPlaylist=video // picker overlays the menu; returns here when dismissed
                    }
                    SheetAction(Icons.Default.Share,"Share",Color(0xFF0D9488)) {
                        val share=Intent(Intent.ACTION_SEND).apply {
                            type="video/*";putExtra(Intent.EXTRA_STREAM,video.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share,"Share video"));videoMenu=null
                    }
                    SheetAction(Icons.Default.Edit,"Rename",Color(0xFFD97706)) {
                        renameTarget=video;renameText=video.title;videoMenu=null
                    }
                    SheetAction(Icons.Default.Info,"Details",Muted) {
                        videoMenu=null;notice="${video.title}\n${fileSize(video.size)} · ${resolutionLabel(video.width,video.height)} · ${clock(video.duration)}"
                    }
                    if(tab=="Recent") SheetAction(Icons.Default.History,"Remove from recent",Muted) {
                        confirmRemove=video;videoMenu=null
                    }
                    if(subpage?.startsWith("Playlist:")==true) SheetAction(Icons.Default.RemoveCircleOutline,"Remove from playlist",Muted) {
                        confirmRemove=video;videoMenu=null
                    }
                    SheetAction(Icons.Default.DeleteOutline,"Move to recycle bin",Color(0xFFD92D20)) {
                        pendingTrash=listOf(video);videoMenu=null
                    }
                }
            }
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
                        library.addToPlaylist(name,video.id);addToPlaylist=null
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

// VIDEONIC_SORT_AND_OPTIONS_SHEET_V3_20261019
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortAndViewBottomSheet(
    isFolders:Boolean,
    videoSort:String,
    folderSort:String,
    folderDescending:Boolean,
    grid:Boolean,
    onVideoSort:(String)->Unit,
    onFolderSort:(String)->Unit,
    onFolderDescending:(Boolean)->Unit,
    onGrid:(Boolean)->Unit,
    onDismiss:()->Unit
) {
    val field = if(isFolders) folderSort else when(videoSort) {
        "Name A-Z", "Name Z-A" -> "Name"
        "Largest", "Smallest" -> "Size"
        "Longest", "Shortest" -> "Duration"
        else -> "Date added"
    }
    val descending = if(isFolders) folderDescending else when(videoSort) {
        "Name A-Z", "Smallest", "Shortest", "Oldest" -> false
        else -> true
    }
    fun update(fieldName:String,desc:Boolean) {
        if(isFolders) {
            onFolderSort(fieldName)
            onFolderDescending(desc)
        } else {
            onVideoSort(when(fieldName) {
                "Name" -> if(desc) "Name Z-A" else "Name A-Z"
                "Size" -> if(desc) "Largest" else "Smallest"
                "Duration" -> if(desc) "Longest" else "Shortest"
                else -> if(desc) "Newest" else "Oldest"
            })
        }
    }
    ModalBottomSheet(
        onDismissRequest=onDismiss,
        shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),
        containerColor=MaterialTheme.colorScheme.surface,
        dragHandle={BottomSheetDefaults.DragHandle()}
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal=24.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(bottom=22.dp)
        ) {
            Text("Sort by",fontSize=26.sp,fontWeight=FontWeight.ExtraBold,
                color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.padding(bottom=12.dp))
            listOf("Name","Date added","Size","Duration").forEach { option ->
                Row(Modifier.fillMaxWidth().height(51.dp).clickable{update(option,descending)},
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(option,Modifier.weight(1f),fontWeight=if(field==option)FontWeight.Bold else FontWeight.Normal,
                        color=if(field==option)Accent else MaterialTheme.colorScheme.onSurface,fontSize=16.sp)
                    if(field==option) Icon(Icons.Default.Check,"Selected",tint=Accent,modifier=Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("ORDER",color=Muted,fontWeight=FontWeight.Bold,fontSize=12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp)) {
                listOf(false to "Ascending",true to "Descending").forEach { (desc,label) ->
                    Surface(shape=RoundedCornerShape(16.dp),
                        color=if(descending==desc)MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation=if(descending==desc)2.dp else 0.dp,
                        modifier=Modifier.weight(1f).clickable{update(field,desc)}) {
                        Box(Modifier.height(45.dp),contentAlignment=Alignment.Center) {
                            Text(label,fontWeight=FontWeight.Bold,fontSize=14.sp,
                                color=if(descending==desc)Ink else Muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("VIEW",color=Muted,fontWeight=FontWeight.Bold,fontSize=12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp)) {
                listOf(false to "List",true to "Grid").forEach { (isGrid,label) ->
                    Surface(shape=RoundedCornerShape(16.dp),
                        color=if(grid==isGrid)MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation=if(grid==isGrid)2.dp else 0.dp,
                        modifier=Modifier.weight(1f).clickable{onGrid(isGrid)}) {
                        Box(Modifier.height(45.dp),contentAlignment=Alignment.Center) {
                            Text(label,fontWeight=FontWeight.Bold,fontSize=14.sp,
                                color=if(grid==isGrid)Ink else Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,
    tint:Color,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(onClick=onClick)
        .padding(vertical=12.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha=.12f)),contentAlignment=Alignment.Center) {
            Icon(icon,null,tint=tint)
        }
        Spacer(Modifier.width(16.dp))
        Text(label,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    }
}
private fun sortDescription(sort:String):String=when(sort){
    "Newest"->"Date added · Newest";"Oldest"->"Date added · Oldest";
    "Name A-Z"->"Name · A–Z";"Name Z-A"->"Name · Z–A";
    "Largest"->"Size · Largest";"Smallest"->"Size · Smallest";
    "Longest"->"Duration · Longest";"Shortest"->"Duration · Shortest";
    else->sort
}
@Composable
private fun ReferenceEmptyPlaylist(onCreate:()->Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=18.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(width=215.dp,height=169.dp),contentAlignment=Alignment.Center) {
                Box(Modifier.size(154.dp,130.dp).offset(x=(-29).dp,y=(-8).dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xFFD6E4FF)))
                Box(Modifier.size(162.dp,140.dp).offset(x=24.dp,y=(-18).dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xFFBFD3FF)))
                Box(Modifier.size(155.dp,120.dp).offset(y=18.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B82F6),Color(0xFF4338CA)))),
                    contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.PlaylistPlay,null,tint=Color.White,modifier=Modifier.size(57.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("No playlists yet",fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(11.dp))
            Text("Group videos into a playlist and play them back to back.",
                color=Muted,fontSize=15.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,lineHeight=23.sp)
            Spacer(Modifier.height(26.dp))
            Button(onClick=onCreate,shape=RoundedCornerShape(100),modifier=Modifier.height(52.dp)) {
                Text("Create playlist",modifier=Modifier.padding(horizontal=18.dp),fontWeight=FontWeight.Bold)
            }
        }
    }
}

// VIDEONIC V7: Reference Favorites, Playlists, Settings, and Theme screens.
@Composable
private fun ReferencePlaylistList(names:List<String>,library:LocalLibrary,onOpen:(String)->Unit) {
    LazyColumn(contentPadding=PaddingValues(horizontal=20.dp,vertical=22.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        items(names,key={it}) { name ->
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,
                shadowElevation=2.dp,modifier=Modifier.fillMaxWidth().clickable{onOpen(name)}) {
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(57.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3B82F6),Color(0xFF4338CA)))),
                        contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.PlaylistPlay,null,tint=Color.White,modifier=Modifier.size(31.dp))
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name,fontWeight=FontWeight.ExtraBold,fontSize=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${library.playlistItems(name).size} videos",color=Muted,fontSize=13.sp)
                    }
                    Icon(Icons.Default.ChevronRight,"Open playlist",tint=Muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceFavoritesScreen(videos:List<LocalVideo>,favorites:Set<String>,
    onOpen:(LocalVideo)->Unit,onMenu:(LocalVideo)->Unit,onFavorite:(LocalVideo)->Unit,
    selected:Set<Long>,onSelect:(Long)->Unit) {
    if(videos.isEmpty()) {
        EmptyState("No favorites yet","Use a video's heart or options menu to save it here."){}
        return
    }
    LazyVerticalGrid(columns=GridCells.Fixed(2),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=18.dp,bottom=36.dp),
        horizontalArrangement=Arrangement.spacedBy(13.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        gridItems(videos,key={it.id}) { video ->
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,
                shadowElevation=3.dp,modifier=Modifier.fillMaxWidth().combinedClickable(
                    onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},
                    onLongClick={onSelect(video.id)})) {
                Column(Modifier.padding(9.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.32f)) {
                        RecentVideoArtwork(video,Modifier.fillMaxSize())
                        if(video.id in selected) Icon(Icons.Default.CheckCircle,"Selected",tint=Accent,
                            modifier=Modifier.align(Alignment.TopStart).padding(7.dp))
                        Surface(shape=RoundedCornerShape(30.dp),color=Color.White,
                            modifier=Modifier.align(Alignment.TopEnd).padding(8.dp).size(38.dp)) {
                            IconButton(onClick={onFavorite(video)},modifier=Modifier.fillMaxSize()) {
                                Icon(if(video.id.toString() in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    if(video.id.toString() in favorites)"Remove favorite" else "Add favorite",
                                    tint=Color(0xFFE73559),modifier=Modifier.size(21.dp))
                            }
                        }
                        Surface(shape=RoundedCornerShape(12.dp),color=Color(0xAA101827),
                            modifier=Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                            Text(clock(video.duration),color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,
                                modifier=Modifier.padding(horizontal=9.dp,vertical=4.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(video.title,modifier=Modifier.fillMaxWidth(),fontSize=14.sp,
                        fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(100),color=qualityTint(video.width,video.height).copy(alpha=.13f)) {
                            Text(resolutionLabel(video.width,video.height),color=qualityTint(video.width,video.height),
                                modifier=Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=11.sp,fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(fileSize(video.size),color=Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.weight(1f))
                        IconButton(onClick={onMenu(video)},modifier=Modifier.size(25.dp)) {
                            Icon(Icons.Default.MoreVert,"More options",tint=Muted,modifier=Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceSectionCard(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(25.dp),color=MaterialTheme.colorScheme.surface,
        shadowElevation=2.dp,modifier=Modifier.fillMaxWidth()) { Column(content=content) }
}

@Composable
private fun ReferenceSettingEntry(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,
    tint:Color,bg:Color,description:String?=null,checked:Boolean?=null,value:String?=null,
    onClick:()->Unit={},onChecked:((Boolean)->Unit)?=null) {
    Row(Modifier.fillMaxWidth().clickable {
        if(checked!=null) onChecked?.invoke(!checked) else onClick()
    }.padding(start=17.dp,end=17.dp,top=13.dp,bottom=13.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(
            if(MaterialTheme.colorScheme.background.luminance()<.22f) tint.copy(alpha=.19f) else bg),
            contentAlignment=Alignment.Center) { Icon(icon,null,tint=tint,modifier=Modifier.size(23.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
            if(description!=null)Text(description,fontSize=11.sp,color=Muted,maxLines=2)
        }
        if(checked!=null) Switch(checked=checked,onCheckedChange=onChecked,
            modifier=Modifier.height(38.dp))
        else if(value!=null) Text(value,color=Accent,fontWeight=FontWeight.Bold,fontSize=14.sp)
    }
}

@Composable
private fun ReferenceSettingsScreen(
    resumePlayback:Boolean,autoPlayNext:Boolean,defaultSpeed:Float,seekSeconds:Int,
    autoRotate:Boolean,keepScreenOn:Boolean,gestureLevels:Boolean,doubleTap:Boolean,longPressBoost:Float,
    themeMode:String,grid:Boolean,navigationStyle:String,
    onResume:(Boolean)->Unit,onAutoPlay:(Boolean)->Unit,onSpeed:()->Unit,onSeek:()->Unit,
    onAutoRotate:(Boolean)->Unit,onKeepScreenOn:(Boolean)->Unit,onGesture:(Boolean)->Unit,
    onDoubleTap:(Boolean)->Unit,onBoost:()->Unit,onTheme:()->Unit,onGrid:(Boolean)->Unit,
    onNavigationStyle:()->Unit,onDecoderInfo:()->Unit
) {
    LazyColumn(contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("PLAYBACK",color=Muted,letterSpacing=1.sp,fontWeight=FontWeight.Bold,fontSize=13.sp,
            modifier=Modifier.padding(top=2.dp,start=5.dp))}
        item {
            ReferenceSectionCard {
                ReferenceSettingEntry(Icons.Default.PlayArrow,"Resume playback",Accent,Color(0xFFDFEAFE),
                    checked=resumePlayback,onChecked=onResume)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.SkipNext,"Auto-play next",Accent,Color(0xFFDFEAFE),
                    checked=autoPlayNext,onChecked=onAutoPlay)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.Speed,"Default speed",Accent,Color(0xFFDFEAFE),
                    value="${speedLabel(defaultSpeed)}x",onClick=onSpeed)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.Restore,"Seek duration",Accent,Color(0xFFDFEAFE),
                    value="$seekSeconds sec",onClick=onSeek)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.Memory,"Hardware acceleration",Accent,Color(0xFFDFEAFE),
                    value="Auto",onClick=onDecoderInfo)
            }
        }
        item {Text("GESTURES",color=Muted,letterSpacing=1.sp,fontWeight=FontWeight.Bold,fontSize=13.sp,
            modifier=Modifier.padding(top=11.dp,start=5.dp))}
        item {
            ReferenceSectionCard {
                ReferenceSettingEntry(Icons.Default.Tune,"Volume & brightness",Color(0xFF7C3AED),Color(0xFFEDE2FC),
                    checked=gestureLevels,onChecked=onGesture)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.TouchApp,"Double-tap to seek",Color(0xFF7C3AED),Color(0xFFEDE2FC),
                    checked=doubleTap,onChecked=onDoubleTap)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.Speed,"Long-press speed boost",Color(0xFF7C3AED),Color(0xFFEDE2FC),
                    value="${speedLabel(longPressBoost)}x",onClick=onBoost)
            }
        }
        item {Text("APPEARANCE",color=Muted,letterSpacing=1.sp,fontWeight=FontWeight.Bold,fontSize=13.sp,
            modifier=Modifier.padding(top=11.dp,start=5.dp))}
        item {
            ReferenceSectionCard {
                ReferenceSettingEntry(Icons.Default.DarkMode,"Theme",Color(0xFFD97706),Color(0xFFFFEDCA),
                    value=themeMode.replaceFirstChar { it.uppercaseChar().toString() },onClick=onTheme)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.GridView,"Grid view",Color(0xFFD97706),Color(0xFFFFEDCA),
                    checked=grid,onChecked=onGrid)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.AutoAwesome,"Bottom navigation",Accent,Color(0xFFDFEAFE),
                    value=if(navigationStyle=="glass")"Liquid glass" else "Classic",onClick=onNavigationStyle)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.ScreenRotation,"Auto rotate",Accent,Color(0xFFDFEAFE),
                    checked=autoRotate,onChecked=onAutoRotate)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
                ReferenceSettingEntry(Icons.Default.LightMode,"Keep screen awake",Accent,Color(0xFFDFEAFE),
                    checked=keepScreenOn,onChecked=onKeepScreenOn)
            }
        }
        item {Text("Changes are saved automatically.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=8.dp,bottom=20.dp))}
    }
}

@Composable
private fun ReferenceThemeScreen(mode:String,onChange:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=16.dp)) {
        Text("Choose how Videonic Lite looks. System follows your phone setting.",
            color=Muted,fontSize=14.sp,lineHeight=23.sp)
        Spacer(Modifier.height(23.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(11.dp)) {
            listOf("light","dark","system").forEach { option ->
                val active=mode==option
                Column(Modifier.weight(1f).clickable{onChange(option)},horizontalAlignment=Alignment.CenterHorizontally) {
                    val light=Color(0xFFF4F6FA)
                    val night=Color(0xFF0F172A)
                    Surface(shape=RoundedCornerShape(20.dp),color=if(option=="dark")night else light,
                        border=BorderStroke(if(active)2.dp else 1.dp,if(active)Accent else Color(0xFFDDE2EA)),
                        modifier=Modifier.fillMaxWidth().aspectRatio(.58f)) {
                        Box {
                            Row(Modifier.fillMaxSize()) {
                                if(option=="system") {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(light))
                                    Box(Modifier.weight(1f).fillMaxHeight().background(night))
                                }
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=15.dp),
                                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                Box(Modifier.fillMaxWidth(.53f).height(9.dp).clip(RoundedCornerShape(100)).background(
                                    if(option=="dark")Color(0xFFD6D9E3) else Color(0xFF424A60)))
                                repeat(3) {
                                    Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(
                                        if(option=="dark")Color(0xFF273247) else if(option=="system")Color(0xFFD0D7E4) else Color.White))
                                }
                            }
                            if(active) {
                                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)
                                    .clip(RoundedCornerShape(100)).background(Accent),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Check,"Selected",tint=Color.White,modifier=Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    Text(option.replaceFirstChar{it.uppercaseChar().toString()},fontWeight=FontWeight.ExtraBold,fontSize=15.sp)
                }
            }
        }
    }
}

// VIDEONIC_RECENT_ME_V5_20261019
@Composable
private fun RecentVideoArtwork(video:LocalVideo,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var thumbnail by remember(video.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.id) {
        thumbnail=withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(video.uri,android.util.Size(600,500),null) }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF323341)),contentAlignment=Alignment.Center) {
        if(thumbnail!=null) {
            Image(thumbnail!!.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFB28D78),Color(0xFF443945)))))
            Icon(Icons.Default.Movie,null,tint=Color.White.copy(alpha=.48f),modifier=Modifier.size(33.dp))
        }
    }
}

@Composable
private fun RecentWatchRing(progress:Float,modifier:Modifier=Modifier) {
    val safeProgress=progress.coerceIn(0f,1f)
    Box(modifier.size(52.dp).clip(RoundedCornerShape(100)).background(Color.White),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val lineWidth=4.dp.toPx()
            drawArc(color=Color(0xFFE1E5EC),startAngle=-90f,sweepAngle=360f,
                useCenter=false,style=Stroke(width=lineWidth,cap=StrokeCap.Round))
            if(safeProgress>0f) drawArc(color=Accent,startAngle=-90f,
                sweepAngle=360f*safeProgress,useCenter=false,
                style=Stroke(width=lineWidth,cap=StrokeCap.Round))
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(100)).background(Color(0xFFF8FAFF)),
            contentAlignment=Alignment.Center) {
            Icon(Icons.Default.PlayArrow,"Resume video",tint=Accent,modifier=Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceRecentScreen(videos:List<LocalVideo>,library:LocalLibrary,
    onOpen:(LocalVideo)->Unit,onMenu:(LocalVideo)->Unit) {
    if(videos.isEmpty()) {
        EmptyState("Nothing watched yet","Videos you watch will appear here."){}
        return
    }
    val hero=videos.first()
    val heroDuration=hero.duration.coerceAtLeast(0L)
    val heroWatched=library.position(hero.id).coerceIn(0L,heroDuration)
    val heroRatio=if(heroDuration>0L) heroWatched.toFloat()/heroDuration else 0f
    LazyColumn(
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=10.dp,bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)
    ) {
        item(key="continue") {
            Box(Modifier.fillMaxWidth().aspectRatio(1.065f).clip(RoundedCornerShape(32.dp))
                .clickable { onOpen(hero) }) {
                RecentVideoArtwork(hero,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color(0x553A241B),Color(0x66534240),Color(0xE921222A)))))
                Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Surface(shape=RoundedCornerShape(100),color=Color.White.copy(alpha=.23f)) {
                        Text("CONTINUE WATCHING",modifier=Modifier.padding(horizontal=13.dp,vertical=8.dp),
                            fontSize=11.sp,color=Color.White,fontWeight=FontWeight.ExtraBold)
                    }
                    Column {
                        Text(hero.title,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.ExtraBold,
                            maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=25.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            QualityInline(hero.width,hero.height,12.sp)
                            Text(" · ${fileSize(hero.size)} · ${clock(hero.duration)}",
                                color=Color.White.copy(alpha=.85f),fontSize=12.sp,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment=Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                LinearProgressIndicator(progress={heroRatio},
                                    modifier=Modifier.fillMaxWidth().height(5.dp)
                                        .clip(RoundedCornerShape(100)),
                                    color=Color.White,trackColor=Color.White.copy(alpha=.34f))
                                Spacer(Modifier.height(8.dp))
                                Text("${clock((heroDuration-heroWatched).coerceAtLeast(0L))} left",
                                    color=Color.White.copy(alpha=.93f),fontSize=12.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Surface(shape=RoundedCornerShape(100),color=Color.White,modifier=Modifier.size(60.dp)) {
                                Box(contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow,"Continue watching",tint=Accent,
                                        modifier=Modifier.size(31.dp))
                                }
                            }
                        }
                    }
                }
                // Visible options menu, independent of the card's Play gesture.
                IconButton(
                    onClick={onMenu(hero)},
                    modifier=Modifier.align(Alignment.TopEnd).padding(top=12.dp,end=12.dp)
                        .background(Color.Black.copy(alpha=.28f),RoundedCornerShape(100))
                ) {
                    Icon(Icons.Default.MoreVert,"Video options",tint=Color.White)
                }
            }
        }
        if(videos.size>1) {
            item(key="earlier-label") {
                Text("EARLIER",color=Color(0xFF9BA8BA),fontWeight=FontWeight.Bold,
                    letterSpacing=1.sp,fontSize=13.sp,
                    modifier=Modifier.padding(top=4.dp))
            }
            items(videos.drop(1),key={it.id}) { video ->
                val videoDuration=video.duration.coerceAtLeast(0L)
                val watched=library.position(video.id).coerceIn(0L,videoDuration)
                val watchFraction=if(videoDuration>0L) watched.toFloat()/videoDuration else 0f
                val percent=(watchFraction*100).toInt().coerceIn(0,100)
                val leftSeconds=((videoDuration-watched).coerceAtLeast(0L)+999L)/1000L
                val remaining=if(leftSeconds<60L) "${leftSeconds}s left" else "${clock(videoDuration-watched)} left"
                Row(Modifier.fillMaxWidth().combinedClickable(onClick={onOpen(video)},
                    onLongClick={onMenu(video)}).padding(bottom=6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(94.dp)) {
                        RecentVideoArtwork(video,Modifier.size(84.dp).align(Alignment.TopStart))
                        RecentWatchRing(watchFraction,Modifier.align(Alignment.BottomEnd))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(video.title,color=MaterialTheme.colorScheme.onBackground,
                            fontSize=15.sp,fontWeight=FontWeight.ExtraBold,maxLines=2,
                            overflow=TextOverflow.Ellipsis,lineHeight=20.sp)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            QualityInline(video.width,video.height,12.sp)
                            Text(" · ${fileSize(video.size)} · ${clock(video.duration)}",
                                color=Muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text("${percent}% watched · $remaining",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,
                            fontWeight=FontWeight.SemiBold,maxLines=1,
                            overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick={onMenu(video)},modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert,"Video options",tint=Muted)
                    }
                }
            }
        }
    }
}

// Whole-bar Liquid Glass navigation: the entire floating menu is glass, not just the active tab.
// Uses Compose gradients, reflections and alpha; no false claim of device backdrop-blur support.
@Composable
private fun VideonicBottomNavigation(selectedTab:String,glass:Boolean,onSelect:(String)->Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < .22f
    val outline = RoundedCornerShape(38.dp)
    val background = if(glass) {
        if(isDark) Brush.linearGradient(listOf(
            Color(0xF0445E84), Color(0xE02B3F62), Color(0xE5304065)))
        else Brush.linearGradient(listOf(
            Color(0xEDF1F8FF), Color(0xE5FFFFFF), Color(0xDADEEBFF)))
    } else Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))

    Box(
        Modifier.fillMaxWidth()
            .padding(start=16.dp,end=16.dp,top=8.dp,bottom=22.dp)
            .shadow(if(glass)18.dp else 8.dp,outline)
            .clip(outline)
            .background(background)
            .border(
                BorderStroke(if(glass)1.6.dp else .5.dp,
                    if(glass) if(isDark) Color.White.copy(alpha=.43f)
                        else Color.White.copy(alpha=.99f)
                    else MaterialTheme.colorScheme.outline.copy(alpha=.08f)),
                outline
            )
    ) {
        if(glass) {
            // Entire-bar sheen, visibly present behind every icon and between tabs.
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
                Color(0x443D9DFF), Color.White.copy(alpha=if(isDark).10f else .40f),
                Color(0x445E77FF)))))
            Box(Modifier.fillMaxWidth().height(25.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(
                    Color.White.copy(alpha=if(isDark).29f else .85f),Color.Transparent))))
            Box(Modifier.fillMaxWidth(.85f).height(2.dp).align(Alignment.TopCenter)
                .background(Brush.horizontalGradient(listOf(
                    Color.Transparent, Color.White.copy(alpha=.92f), Color.Transparent))))
            Box(Modifier.fillMaxWidth(.84f).height(2.dp).align(Alignment.BottomCenter)
                .background(Brush.horizontalGradient(listOf(
                    Color.Transparent, Color(0xFF71BAFF).copy(alpha=.48f),Color.Transparent))))
            Box(Modifier.size(120.dp).align(Alignment.CenterStart).background(
                Brush.radialGradient(listOf(
                    Color(0x665DC9FF),Color.Transparent))))
            Box(Modifier.size(120.dp).align(Alignment.CenterEnd).background(
                Brush.radialGradient(listOf(
                    Color(0x446D71FF),Color.Transparent))))
        }
        Row(
            Modifier.fillMaxWidth().padding(if(glass)8.dp else 6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(if(glass)5.dp else 0.dp)
        ) {
            listOf("Videos","Folders","Recent","Me").forEach { name ->
                val selected = selectedTab==name
                val pillShape=RoundedCornerShape(32.dp)
                val chipBackground = when {
                    selected && glass -> Brush.linearGradient(listOf(
                        Color(0xFF71B9FF), Color(0xFF2672F0),Color(0xFF3150DF)))
                    selected -> Brush.linearGradient(listOf(Accent,Accent))
                    glass && isDark -> Brush.verticalGradient(listOf(
                        Color.White.copy(alpha=.18f), Color.White.copy(alpha=.06f)))
                    glass -> Brush.verticalGradient(listOf(
                        Color.White.copy(alpha=.54f),Color(0xA9DFECFF)))
                    else -> Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent))
                }
                val chipBorder = if(glass) {
                    if(selected)Color.White.copy(alpha=.65f)
                    else Color.White.copy(alpha=if(isDark).25f else .76f)
                } else Color.Transparent
                Row(
                    Modifier.weight(if(selected)1.55f else .8f)
                        .clip(pillShape)
                        .background(chipBackground)
                        .then(if(glass) Modifier.border(.75.dp,chipBorder,pillShape) else Modifier)
                        .clickable(onClick={onSelect(name)})
                        .padding(vertical=13.dp,horizontal=6.dp),
                    horizontalArrangement=Arrangement.Center,
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    Icon(
                        when(name) {"Videos"->Icons.Default.VideoLibrary;
                            "Folders"->Icons.Default.Folder;
                            "Recent"->Icons.Default.History;
                            else->Icons.Default.Person}, name,
                        tint=if(selected)Color.White else if(glass && isDark)
                            Color(0xFFE8F2FF) else if(glass) Color(0xFF637DA5)
                            else Color(0xFF94A3B8),
                        modifier=Modifier.size(24.dp)
                    )
                    if(selected) {
                        Spacer(Modifier.width(6.dp))
                        Text(name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
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
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
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

// VIDEONIC_VIDEOS_FOLDERS_UI_V2_REPAIR_20261019
// VIDEONIC_VIDEOS_HOME_V4_20261019
private fun dateHeading(timeMillis:Long):String {
    val zone=java.time.ZoneId.systemDefault()
    val date=java.time.Instant.ofEpochMilli(timeMillis.coerceAtLeast(0L))
        .atZone(zone).toLocalDate()
    val today=java.time.LocalDate.now(zone)
    return when {
        date == today -> "TODAY"
        date == today.minusDays(1) -> "YESTERDAY"
        date.year == today.year -> date.format(
            java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US)
        ).uppercase(Locale.US)
        else -> date.format(
            java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy",Locale.US)
        ).uppercase(Locale.US)
    }
}

@Composable
private fun HomeCircleButton(icon:androidx.compose.ui.graphics.vector.ImageVector, description:String,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(100),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.size(50.dp)) {
        Box(contentAlignment=Alignment.Center){Icon(icon,description,tint=Ink,modifier=Modifier.size(22.dp))}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideosLibraryHome(
    videos:List<LocalVideo>,
    grid:Boolean,
    sortLabel:String,
    query:String,
    showSearch:Boolean,
    library:LocalLibrary,
    selected:Set<Long>,
    onSelect:(Long)->Unit,
    onOpen:(LocalVideo)->Unit,
    onMenu:(LocalVideo)->Unit,
    onToggleSearch:()->Unit,
    onResumeVideo:()->Unit,
    onToggleGrid:()->Unit,
    onOpenSort:()->Unit,
    onQueryChange:(String)->Unit
) {
    val sortDescription=when(sortLabel) {
        "Newest" -> "Newest first"
        "Oldest" -> "Oldest first"
        "Name A-Z" -> "Name A-Z"
        "Name Z-A" -> "Name Z-A"
        "Largest" -> "Largest first"
        "Smallest" -> "Smallest first"
        "Longest" -> "Longest first"
        "Shortest" -> "Shortest first"
        else -> sortLabel
    }
    val columns=if(LocalConfiguration.current.screenWidthDp>=720) 3 else 2
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState=androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var lastViewGrid by remember { mutableStateOf(grid) }
    LaunchedEffect(grid) {
        if(lastViewGrid!=grid) {
            val sourceIndex=if(lastViewGrid) gridState.firstVisibleItemIndex*columns
                else listState.firstVisibleItemIndex
            val sourceOffset=if(lastViewGrid) gridState.firstVisibleItemScrollOffset
                else listState.firstVisibleItemScrollOffset
            if(grid) gridState.scrollToItem(sourceIndex/columns,sourceOffset)
            else listState.scrollToItem(sourceIndex,sourceOffset)
            lastViewGrid=grid
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Extra top breathing room; Scaffold already provides system-bar insets.
            Row(
                Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=44.dp,bottom=22.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Videos",fontSize=36.sp,fontWeight=FontWeight.ExtraBold,color=Ink,maxLines=1)
                    Spacer(Modifier.height(3.dp))
                    Text("${videos.size} videos · $sortDescription",color=Muted,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onToggleSearch,modifier=Modifier.size(38.dp)) {
                        Icon(Icons.Default.Search,"Search videos",tint=Ink,modifier=Modifier.size(22.dp))
                    }
                    HomeCircleButton(Icons.Default.Sort,"Sort videos",onOpenSort)
                    HomeCircleButton(if(grid)Icons.Default.ViewList else Icons.Default.GridView,"Change view",onToggleGrid)
                }
            }
            if(showSearch) {
                OutlinedTextField(
                    value=query,onValueChange=onQueryChange,
                    placeholder={Text("Search videos",color=Muted)},
                    leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,
                    shape=RoundedCornerShape(24.dp),
                    modifier=Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=8.dp)
                )
            }
            if(videos.isEmpty()) {
                EmptyState("No videos found","Your local videos will appear here."){}
            } else if(grid) {
                // Grid appearance is unique to Videos; Favorites and other grids stay unchanged.
                LazyVerticalGrid(
                    state=gridState,
                    columns=GridCells.Fixed(columns),
                    contentPadding=PaddingValues(start=20.dp,end=20.dp,top=10.dp,bottom=110.dp),
                    horizontalArrangement=Arrangement.spacedBy(14.dp),
                    verticalArrangement=Arrangement.spacedBy(22.dp)
                ) {
                    gridItems(videos,key={it.id}) {video ->
                        Column(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},
                                onLongClick={onSelect(video.id)}
                            )
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                VideoThumb(video,Modifier.fillMaxWidth().aspectRatio(1.46f).clip(RoundedCornerShape(23.dp)))
                                if(video.id in selected) Icon(
                                    Icons.Default.CheckCircle,"Selected",tint=Accent,
                                    modifier=Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp)
                                )
                                Surface(
                                    shape=RoundedCornerShape(100),
                                    color=Color.Black.copy(alpha=.36f),
                                    modifier=Modifier.align(Alignment.TopEnd).padding(5.dp).size(35.dp)
                                ) {
                                    IconButton(onClick={onMenu(video)},modifier=Modifier.size(35.dp)) {
                                        Icon(Icons.Default.MoreVert,"Video options",tint=Color.White,modifier=Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                video.title,color=Ink,fontSize=14.sp,fontWeight=FontWeight.ExtraBold,
                                maxLines=1,overflow=TextOverflow.Ellipsis,
                                modifier=Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                QualityInline(video.width,video.height,13.sp)
                                Text(" · ${fileSize(video.size)}",color=Muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state=listState,
                    contentPadding=PaddingValues(start=20.dp,end=20.dp,top=6.dp,bottom=110.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    items(videos,key={it.id}) { video ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},
                                onLongClick={onSelect(video.id)}
                            ).padding(vertical=10.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Box {
                                VideoThumb(video,Modifier.size(width=138.dp,height=82.dp))
                                if(video.id in selected) Icon(Icons.Default.CheckCircle,"Selected",tint=Accent,modifier=Modifier.align(Alignment.TopEnd).padding(4.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(video.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,
                                    fontWeight=FontWeight.ExtraBold,color=Ink)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    QualityInline(video.width,video.height,12.sp)
                                    Text(" · ${fileSize(video.size)}",color=Muted,fontSize=12.sp)
                                }
                            }
                            IconButton(onClick={onMenu(video)}) {
                                Icon(Icons.Default.MoreVert,"Options",tint=Muted)
                            }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.20f))
                    }
                }
            }
        }
        Surface(
            shape=RoundedCornerShape(100),color=Accent,shadowElevation=10.dp,
            modifier=Modifier.align(Alignment.BottomEnd).padding(end=22.dp,bottom=24.dp).size(68.dp).clickable(onClick=onResumeVideo)
        ) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.PlayArrow,"Resume latest video",tint=Color.White,modifier=Modifier.size(37.dp)) }
        }
    }
}

@Composable
private fun ColorFolderArt(accent:Color,pale:Color,modifier:Modifier=Modifier) {
    // A drawn folder illustration: intentionally does NOT load any video thumbnails.
    // Keep the same colorful folder artwork in light and dark themes.
    val folderBody=pale
    Box(modifier) {
        Box(
            Modifier.align(Alignment.TopStart).padding(start=2.dp,top=3.dp)
                .fillMaxWidth(.38f).fillMaxHeight(.34f)
                .clip(RoundedCornerShape(topStart=15.dp,topEnd=13.dp,bottomEnd=2.dp,bottomStart=2.dp))
                .background(accent.copy(alpha=.77f))
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(.83f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(24.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(folderBody,accent.copy(alpha=if(folderBody==pale) .63f else .35f))
                ))
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(.48f).align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha=.13f))
            )
        }
    }
}

@Composable
private fun FolderLibraryHome(
    folders:List<Pair<String,List<LocalVideo>>>,
    grid:Boolean,
    query:String,
    showSearch:Boolean,
    descending:Boolean,
    onToggleSearch:()->Unit,
    onToggleSort:()->Unit,
    onQueryChange:(String)->Unit,
    onOpenFolder:(String)->Unit
) {
    val palette=listOf(
        Color(0xFFDDE7FA) to Color(0xFF2D6AE3),
        Color(0xFFE9DDFB) to Color(0xFF8A52E8),
        Color(0xFFD7F0EB) to Color(0xFF2AA594),
        Color(0xFFF7E9C8) to Color(0xFFD88608),
        Color(0xFFF7DDE6) to Color(0xFFEA476C),
        Color(0xFFE2F2D5) to Color(0xFF66A83B),
        Color(0xFFE0E9FA) to Color(0xFF5B81E4),
        Color(0xFFE8DDF8) to Color(0xFF9967E8)
    )
    fun colorsFor(name:String):Pair<Color,Color> {
        val index=((name.hashCode().toLong() and 0x7fffffffL) % palette.size).toInt()
        return palette[index]
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=44.dp,bottom=22.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Folders",fontSize=36.sp,fontWeight=FontWeight.ExtraBold,color=Ink,maxLines=1)
                Spacer(Modifier.height(3.dp))
                Text(if(grid)"${folders.size} folders · ${if(descending) "Z-A" else "A-Z"}" else "${folders.size} folders",color=Muted,fontSize=13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                HomeCircleButton(Icons.Default.Search,"Search folders",onToggleSearch)
                HomeCircleButton(Icons.Default.Sort,"Sort folders",onToggleSort)
            }
        }
        if(showSearch) {
            OutlinedTextField(
                value=query,onValueChange=onQueryChange,
                placeholder={Text("Search folders",color=Muted)},leadingIcon={Icon(Icons.Default.Search,null)},
                singleLine=true,shape=RoundedCornerShape(24.dp),
                modifier=Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=8.dp)
            )
        }
        if(grid) {
            LazyVerticalGrid(
                columns=GridCells.Fixed(2),
                contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=92.dp),
                horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)
            ) {
                gridItems(folders,key={it.first}) { entry ->
                    val (pale,accent)=colorsFor(entry.first)
                    Surface(
                        shape=RoundedCornerShape(28.dp),
                        color=pale,
                        modifier=Modifier.fillMaxWidth().aspectRatio(.88f).clickable{onOpenFolder(entry.first)}
                    ) {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Surface(shape=RoundedCornerShape(100),color=Color.White.copy(alpha=.94f),modifier=Modifier.size(44.dp)) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Icon(Icons.Default.FolderOpen,null,tint=accent,modifier=Modifier.size(22.dp))
                                    }
                                }
                                Icon(Icons.Default.FolderOpen,null,tint=accent.copy(alpha=.25f),modifier=Modifier.size(60.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(entry.first,color=Color(0xFF101828),fontWeight=FontWeight.ExtraBold,fontSize=15.sp,lineHeight=19.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(7.dp))
                            Text("${entry.second.size} video${if(entry.second.size==1)"" else "s"}",color=accent,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=92.dp),
                verticalArrangement=Arrangement.spacedBy(13.dp)
            ) {
                items(folders,key={it.first}) { entry ->
                    val (pale,accent)=colorsFor(entry.first)
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable{onOpenFolder(entry.first)}.padding(vertical=7.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            ColorFolderArt(accent,pale,Modifier.size(width=116.dp,height=85.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.first,fontWeight=FontWeight.ExtraBold,color=Ink,fontSize=16.sp,lineHeight=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Spacer(Modifier.height(5.dp))
                                Text("${entry.second.size} video${if(entry.second.size==1)"" else "s"}",color=Muted,fontSize=13.sp)
                            }
                            Icon(Icons.Default.ChevronRight,"Open folder",tint=Color(0xFFD0D6E0))
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.20f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoList(videos:List<LocalVideo>,grid:Boolean,library:LocalLibrary,onOpen:(LocalVideo)->Unit,onMenu:(LocalVideo)->Unit,favorites:Set<String>,empty:String, selected:Set<Long> = emptySet(),onSelect:(Long)->Unit = {}) {
    if(videos.isEmpty()) {EmptyState(empty,"Your local videos will appear here."){};return}
    if(grid) {
        LazyVerticalGrid(columns=GridCells.Adaptive(minSize=156.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
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
                        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                            QualityInline(video.width,video.height,11.sp)
                            Text(" · ${fileSize(video.size)}",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=Muted)
                        }
                        IconButton(onClick={onMenu(video)},modifier=Modifier.size(30.dp)){Icon(Icons.Default.MoreVert,"Video options",modifier=Modifier.size(19.dp))}
                    }
                }
            }
        }
    } else LazyColumn(contentPadding=PaddingValues(horizontal=13.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(videos,key={it.id}){video->
            Surface(shape=RoundedCornerShape(19.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().combinedClickable(onClick={if(selected.isNotEmpty())onSelect(video.id) else onOpen(video)},onLongClick={onSelect(video.id)})) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box {
                        VideoThumb(video,Modifier.size(width=128.dp,height=88.dp))
                        if(video.id in selected) Icon(Icons.Default.CheckCircle,"Selected",tint=Accent,modifier=Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(video.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            QualityInline(video.width,video.height,11.sp)
                            Text(" · ${fileSize(video.size)} · ${clock(video.duration)}",fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
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
    Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=6.dp)) {
        Text(title.uppercase(Locale.US),fontWeight=FontWeight.Bold,fontSize=13.sp,color=Muted)
    }
}
@Composable
private fun SettingsToggle(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,
    subtitle:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable{onChange(!checked)}.padding(horizontal=15.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha=.17f)),contentAlignment=Alignment.Center) {
                Icon(icon,null,tint=Accent)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                if(subtitle.isNotBlank())Text(subtitle,color=Muted,fontSize=11.sp,maxLines=2)
            }
            Switch(checked,onCheckedChange=onChange)
        }
    }
}

@Composable
private fun MeScreen(videos:List<LocalVideo>,favorites:Set<String>,playlists:List<String>,library:LocalLibrary,open:(String)->Unit) {
    val storage=remember { deviceStorageInfo() }
    LazyColumn(contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item {
            Surface(shape=RoundedCornerShape(28.dp),color=Color.Transparent) {
                Column(Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF3B82F6),Color(0xFF4338CA))),
                    RoundedCornerShape(28.dp)).padding(20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.videonic_brand),"Videonic Lite",
                            Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Videonic Lite",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text("Version 1.0.0",color=Color.White.copy(alpha=.8f),fontSize=13.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha=.16f)).padding(vertical=16.dp),
                        horizontalArrangement=Arrangement.SpaceEvenly) {
                        WhiteMetric("Videos","${videos.size}")
                        WhiteMetric("Used of ${fileSize(storage.second)}","${fileSize(storage.first)}")
                        WhiteMetric("Favorites","${favorites.size}")
                    }
                }
            }
        }
        item { Text("Collections",fontWeight=FontWeight.ExtraBold,fontSize=22.sp,
            color=MaterialTheme.colorScheme.onBackground) }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CollectionTile("Favorites","${favorites.size} videos",Icons.Default.Favorite,
                        Color(0xFFFCE2E9),Color(0xFFE73559),Modifier.weight(1f)){open("Favorites")}
                    CollectionTile("Playlists","${playlists.size} lists",Icons.Default.PlaylistPlay,
                        Color(0xFFDBEAFE),Accent,Modifier.weight(1f)){open("Playlists")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CollectionTile("Private Folder","PIN protected",Icons.Default.Lock,
                        Color(0xFFE9DDFE),Color(0xFF7C3AED),Modifier.weight(1f)){open("Private Folder")}
                    CollectionTile("Recycle Bin","Trashed videos",Icons.Default.DeleteOutline,
                        Color(0xFFFFEAC5),Color(0xFFD97706),Modifier.weight(1f)){open("Recycle Bin")}
                }
            }
        }
        item { Text("Preferences",fontWeight=FontWeight.ExtraBold,fontSize=22.sp,
            modifier=Modifier.padding(top=4.dp)) }
        item {
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,
                shadowElevation=3.dp) {
                Column {
                    ReferenceMeRow(Icons.Default.Settings,"Settings","Appearance and preferences",
                        Accent,Color(0xFFDFEAFE)){open("Settings")}
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.22f))
                    ReferenceMeRow(Icons.Default.Info,"About","App information and legal",
                        Color(0xFF0D9488),Color(0xFFD6F5EE)){open("About")}
                }
            }
        }
        item { ReferenceMeRow(Icons.Default.Email,"Support & Feedback","Get help or share an idea",
            Accent,Color(0xFFDFEAFE)){open("Support & Feedback")} }
    }
}
@Composable
private fun WhiteMetric(label:String,value:String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value,color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=19.sp,maxLines=1)
        Text(label,color=Color.White.copy(alpha=.8f),fontSize=12.sp)
    }
}
@Composable
private fun CollectionTile(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    bg:Color,tint:Color,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(modifier.height(133.dp).clickable(onClick=onClick),shape=RoundedCornerShape(25.dp),
        color=MaterialTheme.colorScheme.surface,shadowElevation=3.dp) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(
                if(MaterialTheme.colorScheme.background.luminance()<.22f) tint.copy(alpha=.23f) else bg),
                contentAlignment=Alignment.Center){Icon(icon,null,tint=tint)}
            Column {
                Text(title,fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,
                    overflow=TextOverflow.Ellipsis)
                Text(subtitle,color=Muted,fontSize=12.sp,maxLines=1)
            }
        }
    }
}
@Composable
private fun ReferenceMeRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,
    subtitle:String,tint:Color,bg:Color,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(
            if(MaterialTheme.colorScheme.background.luminance()<.22f) tint.copy(alpha=.23f) else bg),
            contentAlignment=Alignment.Center){Icon(icon,null,tint=tint)}
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Text(subtitle,fontSize=12.sp,color=Muted)
        }
        Icon(Icons.Default.ChevronRight,null,tint=Color(0xFFCBD5E1))
    }
}
@Composable
private fun MeEntry(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit) {
    ReferenceMeRow(icon,title,subtitle,Accent,Color(0xFFDFEAFE),onClick)
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

private data class TrashedClip(val uri:Uri,val name:String,val size:Long,
    val duration:Long,val expiresSeconds:Long?)

@Composable
private fun TrashedClipArtwork(clip:TrashedClip,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var thumbnail by remember(clip.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(clip.uri) {
        thumbnail=withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(clip.uri,android.util.Size(220,140),null) }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(17.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF343747),Color(0xFFBC9882)))),
        contentAlignment=Alignment.Center) {
        if(thumbnail!=null) Image(thumbnail!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Movie,null,tint=Color.White.copy(alpha=.55f),modifier=Modifier.size(30.dp))
        Surface(shape=RoundedCornerShape(12.dp),color=Color(0xB0111727),
            modifier=Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            Text(clock(clip.duration),color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.padding(horizontal=8.dp,vertical=3.dp))
        }
    }
}

@Composable
private fun RecycleBinScreen(onChanged:()->Unit={}) {
    val context=LocalContext.current
    var clips by remember { mutableStateOf<List<TrashedClip>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var restore by remember { mutableStateOf<TrashedClip?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode!=Activity.RESULT_OK) error="Android did not approve the restore request"
        else onChanged()
        restore=null;reload++
    }
    val deleteLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode!=Activity.RESULT_OK) error="Permanent deletion was cancelled or denied"
        else onChanged()
        confirmEmpty=false;reload++
    }
    LaunchedEffect(reload) {
        busy=true;error=null
        if(Build.VERSION.SDK_INT>=30) {
            val result=withContext(Dispatchers.IO) { runCatching {
                val args=Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_ONLY) }
                val found=mutableListOf<TrashedClip>()
                val projection=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,MediaStore.Video.Media.DURATION,MediaStore.MediaColumns.DATE_EXPIRES)
                context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,projection,args,null)?.use { c ->
                    val id=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val name=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val size=c.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val duration=c.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val expires=c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                    while(c.moveToNext()) {
                        found.add(TrashedClip(
                            android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(id)),
                            c.getString(name)?:"Video",if(size>=0&&!c.isNull(size))c.getLong(size) else 0L,
                            if(duration>=0&&!c.isNull(duration))c.getLong(duration) else 0L,
                            if(expires>=0&&!c.isNull(expires))c.getLong(expires) else null
                        ))
                    }
                }
                found
            } }
            clips=result.getOrDefault(emptyList());error=result.exceptionOrNull()?.localizedMessage
        } else error="Android 11 or later is required for the system Recycle Bin"
        busy=false
    }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={reload++},enabled=!busy) {Icon(Icons.Default.Refresh,"Refresh bin",tint=Muted)}
            Surface(shape=RoundedCornerShape(100),color=Color(0xFFFCE4E1),
                modifier=Modifier.clickable(enabled=!busy && clips.isNotEmpty()){confirmEmpty=true}) {
                Text("Empty",color=Color(0xFFD92D20),fontWeight=FontWeight.ExtraBold,fontSize=14.sp,
                    modifier=Modifier.padding(horizontal=18.dp,vertical=10.dp))
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=if(MaterialTheme.colorScheme.background.luminance()<.22f)
                Color(0xFF46321F) else Color(0xFFFFF3DA)) {
            Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Info,null,tint=Color(0xFFB45309))
                Spacer(Modifier.width(13.dp))
                Text("Android automatically removes trashed videos after their expiry date, typically around 30 days. Other gallery apps may use a separate bin.",
                    fontSize=13.sp,lineHeight=20.sp,
                    color=if(MaterialTheme.colorScheme.background.luminance()<.22f)Color(0xFFFFDCA7) else Color(0xFF9A4D09))
            }
        }
        when {
            busy -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null -> Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline,null,tint=MaterialTheme.colorScheme.error,modifier=Modifier.size(38.dp))
                Text("Unable to load Android trash",fontWeight=FontWeight.Bold)
                Text(error.orEmpty(),color=Muted)
                OutlinedButton(onClick={reload++}){Text("Retry")}
            }
            clips.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DeleteSweep,null,tint=Accent,modifier=Modifier.size(60.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Recycle Bin is empty",fontWeight=FontWeight.ExtraBold,fontSize=20.sp)
                    Text("Your trashed videos will appear here.",color=Muted,fontSize=13.sp)
                }
            }
            else -> LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp),
                contentPadding=PaddingValues(bottom=24.dp)) {
                items(clips,key={it.uri.toString()}) { clip ->
                    Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,
                        shadowElevation=2.dp) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            TrashedClipArtwork(clip,Modifier.size(width=104.dp,height=76.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(clip.name,fontWeight=FontWeight.Bold,fontSize=14.sp,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                val remaining=clip.expiresSeconds?.let { seconds ->
                                    val days=(((seconds-System.currentTimeMillis()/1000L).coerceAtLeast(0L)+86399L)/86400L)
                                    "$days days left"
                                } ?: "In device trash"
                                Text("${fileSize(clip.size)} · $remaining",color=Muted,fontSize=11.sp,maxLines=1,
                                    overflow=TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(shape=RoundedCornerShape(100),color=MaterialTheme.colorScheme.primaryContainer,
                                modifier=Modifier.clickable {restore=clip}) {
                                Text("Restore",modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp),
                                    color=Accent,fontWeight=FontWeight.Bold,fontSize=12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    restore?.let { clip ->
        AlertDialog(onDismissRequest={restore=null},shape=RoundedCornerShape(26.dp),
            title={Text("Restore this video?")},
            text={Text("Restore ${clip.name} to its original location? Android may request approval.")},
            confirmButton={Button(onClick={
                if(Build.VERSION.SDK_INT>=30)runCatching{
                    val pending=MediaStore.createTrashRequest(context.contentResolver,listOf(clip.uri),false)
                    restoreLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                }.onFailure{error=it.localizedMessage;restore=null}
            }){Text("Restore")}},
            dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}})
    }
    if(confirmEmpty) AlertDialog(onDismissRequest={confirmEmpty=false},shape=RoundedCornerShape(26.dp),
        title={Text("Permanently delete ${clips.size} videos?")},
        text={Text("This permanently deletes accessible videos in the Android system trash. This cannot be undone. Android will ask you to approve deletion.")},
        confirmButton={Button(onClick={
            if(Build.VERSION.SDK_INT>=30)runCatching{
                val pending=MediaStore.createDeleteRequest(context.contentResolver,clips.map{it.uri})
                deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }.onFailure{error=it.localizedMessage;confirmEmpty=false}
        },colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) {
            Text("Delete permanently")
        }},dismissButton={TextButton(onClick={confirmEmpty=false}){Text("Cancel")}})
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
