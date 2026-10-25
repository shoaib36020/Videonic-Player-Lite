package com.videonic.lite.ui

import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.os.Build
import android.os.Environment
import android.graphics.Bitmap
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.videonic.lite.data.LocalVideo
import com.videonic.lite.security.PrivateVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun PrivateSourceThumbnail(video:LocalVideo,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.id) {
        bitmap=withContext(Dispatchers.IO) {
            runCatching {
                if(Build.VERSION.SDK_INT>=29) {
                    context.contentResolver.loadThumbnail(video.uri,android.util.Size(420,280),null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,
                        android.content.ContentUris.parseId(video.uri),
                        MediaStore.Video.Thumbnails.MINI_KIND,null)
                }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Movie,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.size(30.dp))
    }
}

@Composable
private fun PrivateEntryThumbnail(entry:PrivateVault.Entry,key:ByteArray,
    availableVideos:List<LocalVideo>,vault:PrivateVault,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var bitmap by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id,availableVideos) {
        bitmap=withContext(Dispatchers.IO) {
            vault.loadThumbnail(entry,key) ?: availableVideos.firstOrNull {
                it.title==entry.name && it.size==entry.size
            }?.let { source ->
                runCatching {
                    if(Build.VERSION.SDK_INT>=29) {
                        context.contentResolver.loadThumbnail(source.uri,android.util.Size(480,320),null)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,
                            android.content.ContentUris.parseId(source.uri),
                            MediaStore.Video.Thumbnails.MINI_KIND,null)
                    }
                }.getOrNull()
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Movie,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.size(30.dp))
    }
}

@Composable
private fun PrivateEntryActions(entry:PrivateVault.Entry,busy:Boolean,selected:Boolean,
    onPlay:()->Unit,onUnlock:()->Unit,onToggleSelection:()->Unit) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick={expanded=true},enabled=!busy,modifier=Modifier.size(40.dp)) {
            Icon(Icons.Default.MoreVert,"Video options",modifier=Modifier.size(22.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            DropdownMenuItem(text={Text("Play")},
                leadingIcon={Icon(Icons.Default.PlayArrow,null)},enabled=!busy,
                onClick={expanded=false;onPlay()})
            DropdownMenuItem(text={Text("Unlock to original folder")},
                leadingIcon={Icon(Icons.Default.LockOpen,null)},enabled=!busy,
                onClick={expanded=false;onUnlock()})
            DropdownMenuItem(text={Text(if(selected) "Deselect" else "Select")},
                leadingIcon={Icon(Icons.Default.CheckCircleOutline,null)},enabled=!busy,
                onClick={expanded=false;onToggleSelection()})
        }
    }
}

@Composable
fun SecurePrivateFolder(
    unlockedKey:ByteArray?,
    onUnlockChange:(ByteArray?)->Unit,
    availableVideos:List<LocalVideo>,
    pendingLockVideo:LocalVideo?=null,
    onLockHandled:()->Unit={},
    cleanStaleCache:Boolean,
    onStaleCacheCleaned:()->Unit,
    onPlay:(File,String)->Unit,
    onImportedOriginalRemoved:()->Unit = {}
) {
    val context=LocalContext.current
    val privateAccent=MaterialTheme.colorScheme.primary
    val appPrefs=remember(context) { context.getSharedPreferences("videonic",Context.MODE_PRIVATE) }
    var privateGrid by remember { mutableStateOf(appPrefs.getBoolean("private_folder_grid",false)) }
    val vault=remember {PrivateVault(context)}
    val scope=rememberCoroutineScope()
    var key by remember(unlockedKey) {mutableStateOf(unlockedKey)}
    var pin by remember {mutableStateOf("")}
    var firstPin by remember {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf(vault.entries())}
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var sourcePendingConsent by remember { mutableStateOf<Uri?>(null) }
    var sourcePendingEntry by remember { mutableStateOf<PrivateVault.Entry?>(null) }
    var chooseSource by remember { mutableStateOf(false) }
    var sourceQuery by remember { mutableStateOf("") }
    BackHandler(enabled=chooseSource || selectionMode) {
        if(chooseSource) {
            chooseSource=false
            sourceQuery=""
        } else {
            selectionMode=false
            selectedIds=emptySet()
        }
    }
    fun togglePrivateGrid() {
        privateGrid=!privateGrid
        appPrefs.edit().putBoolean("private_folder_grid",privateGrid).apply()
    }
    val selectedEntries=entries.filter { it.id in selectedIds }
    // Previously created vaults retain their original PIN and encryption salt.
    val pinTarget=if(vault.configured()) vault.pinLengthHint() else 4
    fun refreshEntries() {
        entries=vault.entries()
    }
    DisposableEffect(cleanStaleCache) {
        if(cleanStaleCache) {
            vault.cleanPlaybackCache()
            onStaleCacheCleaned()
        }
        onDispose {}
    }
    val removalApproval=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            message="Video moved into Private Folder"
            onImportedOriginalRemoved()
        } else {
            val imported=sourcePendingEntry
            scope.launch {
                busy=true
                val removed=withContext(Dispatchers.IO) {
                    imported?.let { runCatching { vault.remove(it) } }
                }
                entries=vault.entries()
                busy=false
                message=if(removed?.isSuccess==true)
                    "Move cancelled. The original was kept and no private copy was added."
                else "Android kept the original, and the private copy could not be removed."
            }
        }
        sourcePendingConsent=null
        sourcePendingEntry=null
    }
    fun resolveMediaUri(uri:Uri):Uri?=if(uri.authority=="media") uri else
        runCatching { MediaStore.getMediaUri(context,uri) }.getOrNull()
    fun askDeleteConsent(uri:Uri,entry:PrivateVault.Entry) {
        runCatching {
            val mediaUri=resolveMediaUri(uri) ?: error("Android could not resolve this video to MediaStore")
            val request=MediaStore.createDeleteRequest(context.contentResolver,listOf(mediaUri))
            sourcePendingConsent=uri
            sourcePendingEntry=entry
            removalApproval.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { error ->
            scope.launch {
                busy=true
                val removed=withContext(Dispatchers.IO){runCatching{vault.remove(entry)}}
                entries=vault.entries()
                busy=false
                message=if(removed.isSuccess)
                    "Move cancelled: ${error.localizedMessage}. No private copy was kept."
                else "Could not remove the private copy: ${removed.exceptionOrNull()?.localizedMessage}"
            }
        }
    }
    val manageMediaLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val uri=sourcePendingConsent
        val entry=sourcePendingEntry
        if(uri!=null && entry!=null) {
            val mediaUri=resolveMediaUri(uri)
            if(Build.VERSION.SDK_INT>=30 && MediaStore.canManageMedia(context) && mediaUri!=null) {
                scope.launch {
                    busy=true
                    val removed=withContext(Dispatchers.IO){
                        runCatching{context.contentResolver.delete(mediaUri,null,null)>0}
                    }
                    busy=false
                    if(removed.getOrDefault(false)) {
                        message="Video moved into Private Folder"
                        onImportedOriginalRemoved()
                        sourcePendingConsent=null
                        sourcePendingEntry=null
                    } else askDeleteConsent(uri,entry)
                }
            } else askDeleteConsent(uri,entry)
        }
    }
    fun requestOriginalRemoval(uri:Uri,entry:PrivateVault.Entry) {
        // A Storage Access Framework document may support deletion with its granted URI.
        // Otherwise Android must approve a MediaStore deletion request.
        val removed=runCatching {
            if(DocumentsContract.isDocumentUri(context,uri))
                DocumentsContract.deleteDocument(context.contentResolver,uri)
            else false
        }.getOrDefault(false)
        if(removed) {
            message="Video moved into Private Folder"
            onImportedOriginalRemoved()
        } else if(Build.VERSION.SDK_INT>=30) {
            if(MediaStore.canManageMedia(context)) {
                val mediaUri=resolveMediaUri(uri)
                if(mediaUri==null) askDeleteConsent(uri,entry)
                else scope.launch {
                    busy=true
                    val deleted=withContext(Dispatchers.IO){
                        runCatching{context.contentResolver.delete(mediaUri,null,null)>0}
                    }
                    busy=false
                    if(deleted.getOrDefault(false)) {
                        message="Video moved into Private Folder"
                        onImportedOriginalRemoved()
                    } else askDeleteConsent(uri,entry)
                }
            } else {
                sourcePendingConsent=uri
                sourcePendingEntry=entry
                runCatching {
                    manageMediaLauncher.launch(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                        data=Uri.parse("package:${context.packageName}")
                    })
                }.onFailure { askDeleteConsent(uri,entry) }
            }
        } else {
            scope.launch {
                busy=true
                val removedCopy=withContext(Dispatchers.IO){runCatching{vault.remove(entry)}}
                entries=vault.entries()
                busy=false
                message=if(removedCopy.isSuccess)
                    "Move cancelled. This Android version cannot remove the original automatically."
                else "The original remains and private cleanup failed: ${removedCopy.exceptionOrNull()?.localizedMessage}"
            }
        }
    }
    fun lockVideo(video:LocalVideo) {
        val currentKey=key
        if(currentKey==null || busy || video.folder=="Private") return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO){runCatching {
                vault.importVideo(video.uri,video.title,currentKey,video.modified,video.relativePath)
            }}
            busy=false
            result.onSuccess { entry ->
                entries=vault.entries()
                requestOriginalRemoval(video.uri,entry)
            }.onFailure {
                message="Move failed: ${it.localizedMessage}"
                entries=vault.entries()
            }
        }
    }
    LaunchedEffect(pendingLockVideo?.id,key) {
        val video=pendingLockVideo ?: return@LaunchedEffect
        if(key==null) return@LaunchedEffect
        lockVideo(video)
        onLockHandled()
    }
    fun submitPin() {
        if(pin.length<pinTarget) return
        if(vault.configured()) {
            key=vault.unlock(pin);onUnlockChange(key);pin=""
            message=if(key==null) "Incorrect PIN" else ""
        } else if(firstPin==null) {
            firstPin=pin;pin="";message="Enter the same PIN again to confirm"
        } else {
            if(pin!=firstPin) {
                pin="";firstPin=null;message="PINs did not match. Start again."
            } else {
                val created=vault.setup(pin)
                key=if(created)vault.unlock(pin) else null
                onUnlockChange(key)
                pin="";firstPin=null
                message=if(key==null)"Could not create Private Folder" else ""
            }
        }
    }
    fun playEntry(entry:PrivateVault.Entry) {
        val currentKey=key ?: return
        if(busy) return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching{vault.preparePlayback(entry,currentKey)}
            }
            busy=false
            result.onSuccess{onPlay(it,entry.name)}
                .onFailure{message="Playback failed: ${it.localizedMessage}"}
        }
    }
    fun unlockEntries(targets:List<PrivateVault.Entry>) {
        val currentKey=key ?: return
        if(busy || targets.isEmpty()) return
        busy=true
        scope.launch {
            val results=withContext(Dispatchers.IO) {
                targets.map { entry ->
                    entry to runCatching {
                        val existing=availableVideos.firstOrNull {
                            it.title==entry.name && it.size==entry.size &&
                                (entry.relativePath.isBlank() || it.relativePath==entry.relativePath)
                        }
                        if(existing!=null) {
                            vault.remove(entry)
                            existing.uri
                        } else vault.unlockVideo(entry,currentKey)
                    }
                }
            }
            val unlocked=results.filter { it.second.isSuccess }.map { it.first }
            val failures=results.filter { it.second.isFailure }
            entries=vault.entries()
            if(unlocked.isNotEmpty()) onImportedOriginalRemoved()
            selectedIds=selectedIds-unlocked.map { it.id }.toSet()
            if(entries.isEmpty()) selectionMode=false
            busy=false
            message=when {
                failures.isEmpty() -> "Unlocked ${unlocked.size} video${if(unlocked.size==1) "" else "s"} to their original folder${if(unlocked.size==1) "" else "s"}."
                unlocked.isEmpty() -> "Could not unlock ${failures.size} video${if(failures.size==1) "" else "s"}: ${failures.first().second.exceptionOrNull()?.localizedMessage}"
                else -> "Unlocked ${unlocked.size}; ${failures.size} could not be restored."
            }
        }
    }
    LaunchedEffect(pin,firstPin) {
        // Four-digit new vaults continue automatically after the fourth digit.
        if(!vault.configured() && pin.length==4) submitPin()
    }
    if(key==null) {
        // Fit the entire PIN entry panel into the available viewport, including
        // small or landscape phones, rather than forcing the user to scroll.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short = maxHeight < 620.dp
            val narrow = maxWidth < 360.dp
            val dialSize = minOf(maxWidth * 0.205f,maxHeight * 0.103f,76.dp)
                .coerceAtLeast(40.dp)
            val dialGap = if(short) 2.dp else 7.dp
            val topIconSize = minOf(maxHeight * 0.13f,86.dp).coerceAtLeast(48.dp)
            val titleSize = if(short || narrow) 24.sp else 27.sp
            if(maxWidth > maxHeight && maxHeight < 490.dp) {
                // Short landscape displays use two columns so the entire keypad
                // stays visible without scrolling: description on the left,
                // four keypad rows on the right.
                val landscapeDial=minOf(maxHeight * 0.18f,maxWidth * 0.10f,56.dp)
                    .coerceAtLeast(32.dp)
                Row(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=4.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.SpaceEvenly) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6),Color(0xFF4F35D5)))),
                            contentAlignment=Alignment.Center) {
                            Icon(Icons.Default.Lock,null,tint=Color.White,
                                modifier=Modifier.size(26.dp))
                        }
                        Text(when {
                            vault.configured() -> "Enter your PIN"
                            firstPin!=null -> "Confirm your PIN"
                            else -> "Create your PIN"
                        },fontSize=20.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
                        Text(when {
                            vault.configured() -> "Unlock private videos"
                            firstPin!=null -> "Verify your PIN"
                            else -> "Choose a 4-digit PIN"
                        },fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            repeat(pinTarget) { index ->
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(100))
                                    .background(if(index<pin.length)privateAccent
                                        else MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                        Button(onClick={submitPin()},enabled=pin.length>=pinTarget,
                            shape=RoundedCornerShape(100),
                            colors=ButtonDefaults.buttonColors(containerColor=privateAccent),
                            modifier=Modifier.fillMaxWidth(.96f).height(38.dp)) {
                            Text(if(vault.configured())"Unlock" else if(firstPin!=null)"Create" else "Continue",
                                fontSize=12.sp,maxLines=1)
                        }
                        if(message.isNotBlank()) Text(message,maxLines=2,fontSize=10.sp,
                            color=MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.SpaceEvenly,
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        listOf(listOf("1","2","3"),listOf("4","5","6"),
                            listOf("7","8","9"),listOf("","0","⌫")).forEach { row ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                row.forEach { digit ->
                                    Box(Modifier.size(landscapeDial),contentAlignment=Alignment.Center) {
                                        if(digit.isNotEmpty()) Surface(onClick={
                                            if(digit=="⌫")pin=pin.dropLast(1)
                                            else if(pin.length<pinTarget)pin+=digit
                                            message=""
                                        },shape=RoundedCornerShape(100),
                                        color=if(digit=="⌫")Color.Transparent else MaterialTheme.colorScheme.surface,
                                        modifier=Modifier.fillMaxSize()) {
                                            Box(contentAlignment=Alignment.Center) {
                                                if(digit=="⌫")Icon(Icons.Default.Backspace,"Delete digit",
                                                    modifier=Modifier.size(landscapeDial*0.4f))
                                                else Text(digit,fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=4.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.SpaceEvenly) {
                Box(Modifier.size(topIconSize).clip(RoundedCornerShape(25.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6),Color(0xFF4F35D5)))),
                    contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.Lock,null,tint=Color.White,
                        modifier=Modifier.size(topIconSize * 0.51f))
                }
                Text(when {
                    vault.configured() -> "Enter your PIN"
                    firstPin!=null -> "Confirm your PIN"
                    else -> "Create your PIN"
                },fontSize=titleSize,fontWeight=FontWeight.ExtraBold,maxLines=1)
                Text(when {
                    vault.configured() -> "Unlock your private videos"
                    firstPin!=null -> "Enter the same PIN to verify"
                    else -> "Choose a 4-digit PIN"
                },color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=if(narrow)12.sp else 14.sp,
                    maxLines=1)
                Row(horizontalArrangement=Arrangement.spacedBy(if(short)10.dp else 13.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    repeat(pinTarget){ index ->
                        Box(Modifier.size(if(short)12.dp else 15.dp).clip(RoundedCornerShape(100))
                            .background(if(index<pin.length)privateAccent else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(verticalArrangement=Arrangement.spacedBy(dialGap)) {
                    listOf(listOf("1","2","3"),listOf("4","5","6"),
                        listOf("7","8","9"),listOf("","0","⌫")).forEach { row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                            row.forEach{ digit ->
                                Box(Modifier.size(dialSize),contentAlignment=Alignment.Center) {
                                    if(digit.isNotEmpty()) Surface(onClick={
                                        if(digit=="⌫")pin=pin.dropLast(1)
                                        else if(pin.length<pinTarget)pin+=digit
                                        message=""
                                    },shape=RoundedCornerShape(100),
                                    color=if(digit=="⌫")Color.Transparent else MaterialTheme.colorScheme.surface,
                                    shadowElevation=if(digit=="⌫")0.dp else 2.dp,
                                    modifier=Modifier.fillMaxSize()) {
                                        Box(contentAlignment=Alignment.Center) {
                                            if(digit=="⌫") Icon(Icons.Default.Backspace,"Delete digit",
                                                modifier=Modifier.size(dialSize * 0.35f))
                                            else Text(digit,fontSize=if(short)24.sp else 29.sp,
                                                fontWeight=FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick={submitPin()},enabled=pin.length>=pinTarget,
                    shape=RoundedCornerShape(100),
                    colors=ButtonDefaults.buttonColors(containerColor=privateAccent),
                    modifier=Modifier.fillMaxWidth(.87f).height(if(short)40.dp else 47.dp)) {
                    Text(when {
                        vault.configured() -> "Unlock Private Folder"
                        firstPin!=null -> "Create encrypted folder"
                        else -> "Continue"
                    },fontWeight=FontWeight.Bold,maxLines=1,
                        fontSize=if(narrow)12.sp else 14.sp)
                }
                if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.error,
                    fontSize=11.sp,maxLines=2,modifier=Modifier.padding(horizontal=6.dp))
            }
            }
        }
    } else if(chooseSource) {
        Column(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={chooseSource=false;sourceQuery=""},enabled=!busy) {
                    Icon(Icons.Default.ArrowBack,"Back to Private Folder")
                }
                Column(Modifier.weight(1f)) {
                    Text("Select video to lock",fontWeight=FontWeight.ExtraBold,fontSize=20.sp)
                    Text("The video will return to this folder when unlocked.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                }
                IconButton(onClick={togglePrivateGrid()}) {
                    Icon(if(privateGrid) Icons.Default.ViewList else Icons.Default.GridView,
                        if(privateGrid) "List view" else "Grid view")
                }
            }
            OutlinedTextField(value=sourceQuery,onValueChange={sourceQuery=it},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                placeholder={Text("Search videos")},
                leadingIcon={Icon(Icons.Default.Search,null)},
                shape=RoundedCornerShape(20.dp))
            val sources=availableVideos.filter {
                it.folder!="Private" && it.title.contains(sourceQuery,true)
            }
            if(sources.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Text("No videos found",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if(privateGrid) LazyVerticalGrid(
                columns=GridCells.Adaptive(minSize=150.dp),
                modifier=Modifier.weight(1f),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp),
                contentPadding=PaddingValues(bottom=12.dp)) {
                gridItems(sources,key={it.id}) { video ->
                    Surface(shape=RoundedCornerShape(14.dp),
                        color=MaterialTheme.colorScheme.surface,
                        modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){lockVideo(video)}) {
                        Column {
                            PrivateSourceThumbnail(video,Modifier.fillMaxWidth().aspectRatio(1.4f))
                            Column(Modifier.padding(10.dp)) {
                                Text(video.title,fontWeight=FontWeight.SemiBold,maxLines=2,
                                    overflow=TextOverflow.Ellipsis)
                                Text(video.folder,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else LazyColumn(Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(sources,key={it.id}) { video ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable(enabled=!busy){lockVideo(video)}
                        .padding(horizontal=10.dp,vertical=12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        PrivateSourceThumbnail(video,Modifier.size(width=96.dp,height=64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(video.title,fontWeight=FontWeight.SemiBold,maxLines=2,
                                overflow=TextOverflow.Ellipsis)
                            Text(video.folder,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight,null,
                            tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.13f))
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize=12.sp)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Encrypted videos",fontWeight=FontWeight.ExtraBold,fontSize=23.sp)
                    Text("${entries.size} videos",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                }
                if(entries.isNotEmpty()) {
                    TextButton(enabled=!busy,onClick={
                        selectionMode=!selectionMode
                        selectedIds=emptySet()
                    }) { Text(if(selectionMode) "Cancel" else "Select") }
                }
                IconButton(onClick={togglePrivateGrid()}) {
                    Icon(if(privateGrid) Icons.Default.ViewList else Icons.Default.GridView,
                        if(privateGrid) "List view" else "Grid view")
                }
                Icon(Icons.Default.Lock,"Private Folder",modifier=Modifier.size(20.dp))
            }
            Button(enabled=!busy,onClick={chooseSource=true;sourceQuery=""},
                shape=RoundedCornerShape(100),colors=ButtonDefaults.buttonColors(containerColor=privateAccent),
                modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Move video to Private Folder")
            }
            if(selectionMode && selectedEntries.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(enabled=!busy,onClick={unlockEntries(selectedEntries)},
                        modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LockOpen,null)
                        Spacer(Modifier.width(5.dp))
                        Text("Unlock ${selectedEntries.size}",maxLines=1)
                    }
                }
            }

            if(entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder,null,tint=privateAccent,modifier=Modifier.size(60.dp))
                }
            } else if(privateGrid) LazyVerticalGrid(
                columns=GridCells.Adaptive(minSize=156.dp),
                modifier=Modifier.weight(1f),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp),
                contentPadding=PaddingValues(bottom=12.dp)
            ) {
                gridItems(entries,key={it.id}) { entry ->
                    Surface(shape=RoundedCornerShape(14.dp),
                        color=MaterialTheme.colorScheme.surface,
                        modifier=Modifier.fillMaxWidth()) {
                        Column {
                            PrivateEntryThumbnail(entry,key!!,availableVideos,vault,
                                Modifier.fillMaxWidth().aspectRatio(1.4f))
                            Row(Modifier.fillMaxWidth().padding(start=10.dp,end=4.dp,top=8.dp,bottom=4.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(entry.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                PrivateEntryActions(entry,busy,entry.id in selectedIds,
                                    onPlay={playEntry(entry)},
                                    onUnlock={unlockEntries(listOf(entry))},
                                    onToggleSelection={
                                        selectionMode=true
                                        selectedIds=if(entry.id in selectedIds) selectedIds-entry.id
                                            else selectedIds+entry.id
                                    })
                            }
                        }
                    }
                }
            } else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                items(entries,key={it.id}) { entry ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable(enabled=!busy){
                                    if(selectionMode) {
                                        selectedIds=if(entry.id in selectedIds) selectedIds-entry.id
                                            else selectedIds+entry.id
                                    } else playEntry(entry)
                                }
                                .padding(vertical=6.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(width=116.dp,height=70.dp),
                            ) { PrivateEntryThumbnail(entry,key!!,availableVideos,vault,Modifier.fillMaxSize()) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name,fontWeight=FontWeight.SemiBold,maxLines=2,
                                    overflow=TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(if(entry.trashed) "Previously trashed · choose a destination"
                                    else "Private video",
                                    color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                if(selectionMode) {
                                    Checkbox(checked=entry.id in selectedIds,
                                        onCheckedChange={checked->
                                            selectedIds=if(checked) selectedIds+entry.id
                                                else selectedIds-entry.id
                                        },enabled=!busy)
                                }
                                PrivateEntryActions(entry,busy,entry.id in selectedIds,
                                    onPlay={playEntry(entry)},
                                    onUnlock={unlockEntries(listOf(entry))},
                                    onToggleSelection={
                                        selectionMode=true
                                        selectedIds=if(entry.id in selectedIds) selectedIds-entry.id
                                            else selectedIds+entry.id
                                    })
                            }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.13f))
                    }
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank()) {
                val exportComplete=message.startsWith("Unlocked ")
                Surface(
                    shape=RoundedCornerShape(12.dp),
                    color=if(exportComplete) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.6f),
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Icon(if(exportComplete) Icons.Default.CheckCircle else Icons.Default.Info,
                            null,tint=if(exportComplete) Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.size(18.dp))
                        Text(message,fontSize=12.sp,
                            color=if(exportComplete) Color(0xFF1B5E20)
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

        }
    }
}
