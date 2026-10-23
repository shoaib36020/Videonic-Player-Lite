package com.videonic.lite.ui

import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.net.Uri
import android.app.Activity
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videonic.lite.security.PrivateVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PrivatePurple=Color(0xFF7146E6)

@Composable
fun SecurePrivateFolder(onPlay:(File,String)->Unit,onImportedOriginalRemoved:()->Unit = {}) {
    val context=LocalContext.current
    val vault=remember {PrivateVault(context)}
    val scope=rememberCoroutineScope()
    var key by remember {mutableStateOf<ByteArray?>(null)}
    var pin by remember {mutableStateOf("")}
    var firstPin by remember {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf(vault.entries())}
    var pendingDelete by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    var sourcePendingConsent by remember {mutableStateOf<Uri?>(null)}
    var exporting by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    // Previously created vaults retain their original PIN and encryption salt.
    val pinTarget=if(vault.configured()) vault.pinLengthHint() else 4
    DisposableEffect(Unit){
        vault.cleanPlaybackCache()
        onDispose {key?.fill(0)}
    }
    val removalApproval=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            message="Video moved into Private Folder"
            onImportedOriginalRemoved()
        } else {
            message="Video secured. Android did not remove the original."
        }
        sourcePendingConsent=null
    }
    fun requestOriginalRemoval(uri:Uri) {
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
            runCatching {
                val mediaUri=MediaStore.getMediaUri(context,uri)
                val request=MediaStore.createDeleteRequest(context.contentResolver,listOf(mediaUri))
                sourcePendingConsent=uri
                removalApproval.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                message="Video secured. Original could not be removed automatically; remove it in Gallery."
            }
        } else {
            message="Video secured. Original could not be removed automatically; remove it in Gallery."
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentKey=key
        if(uri!=null && currentKey!=null){
            val name=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
                if(it.moveToFirst())it.getString(0) else null
            }}.getOrNull()?:"Private video.mp4"
            busy=true
            scope.launch {
                val result=withContext(Dispatchers.IO){runCatching {
                    val entry=vault.importVideo(uri,name,currentKey)
                    if(!vault.verifyEncryptedEntry(entry,currentKey)) {
                        runCatching { vault.remove(entry) }
                        error("Encrypted file verification failed")
                    }
                    entry
                }}
                busy=false
                result.onSuccess {
                    entries=vault.entries()
                    requestOriginalRemoval(uri)
                }.onFailure {
                    message="Import failed: ${it.localizedMessage}"
                    entries=vault.entries()
                }
            }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { uri ->
        val item=exporting;exporting=null
        val currentKey=key
        if(uri!=null && item!=null && currentKey!=null) {
            busy=true
            scope.launch {
                val result=withContext(Dispatchers.IO){runCatching{vault.exportVideo(item,currentKey,uri)}}
                message=result.fold({"Video exported to your selected folder."},{"Export failed: ${it.localizedMessage}"})
                busy=false
            }
        }
    }
    fun submitPin() {
        if(pin.length<pinTarget) {message="Enter your ${pinTarget}-digit PIN";return}
        if(vault.configured()) {
            key=vault.unlock(pin);pin=""
            message=if(key==null) "Incorrect PIN" else ""
        } else if(firstPin==null) {
            firstPin=pin;pin="";message="Enter the same PIN again to confirm"
        } else {
            if(pin!=firstPin) {
                pin="";firstPin=null;message="PINs did not match. Start again."
            } else {
                val created=vault.setup(pin)
                key=if(created)vault.unlock(pin) else null
                pin="";firstPin=null
                message=if(key==null)"Could not create Private Folder" else ""
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
                                    .background(if(index<pin.length)PrivatePurple
                                        else MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                        Button(onClick={submitPin()},enabled=pin.length>=pinTarget,
                            shape=RoundedCornerShape(100),
                            colors=ButtonDefaults.buttonColors(containerColor=PrivatePurple),
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
                            .background(if(index<pin.length)PrivatePurple else MaterialTheme.colorScheme.outlineVariant))
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
                    colors=ButtonDefaults.buttonColors(containerColor=PrivatePurple),
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
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Encrypted videos",fontWeight=FontWeight.ExtraBold,fontSize=23.sp)
                    Text("${entries.size} videos",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                }
                OutlinedButton(onClick={key?.fill(0);key=null;pin="";vault.cleanPlaybackCache()}) {
                    Icon(Icons.Default.Lock,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(4.dp));Text("Lock")
                }
            }
            Button(enabled=!busy,onClick={picker.launch(arrayOf("video/*"))},
                shape=RoundedCornerShape(100),colors=ButtonDefaults.buttonColors(containerColor=PrivatePurple),
                modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Move video to Private Folder")
            }

            if(entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder,null,tint=PrivatePurple,modifier=Modifier.size(60.dp))
                    Text("No private videos yet",fontWeight=FontWeight.Bold)
                    Text("Move a video here",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                }
            } else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                items(entries,key={it.id}) { entry ->
                    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                    .background(PrivatePurple.copy(alpha=.15f)),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Lock,null,tint=PrivatePurple)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text("Private video",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                FilledTonalButton(enabled=!busy,onClick={
                                    val currentKey=key ?: return@FilledTonalButton
                                    busy=true
                                    scope.launch {
                                        val result=withContext(Dispatchers.IO){runCatching{vault.preparePlayback(entry,currentKey)}}
                                        busy=false
                                        result.onSuccess{onPlay(it,entry.name)}.onFailure{message="Playback failed: ${it.localizedMessage}"}
                                    }
                                }) {Text("Play")}
                                TextButton(enabled=!busy,onClick={exporting=entry;exporter.launch(entry.name)}) {Text("Export")}
                                TextButton(enabled=!busy,onClick={pendingDelete=entry}) {Text("Delete",color=MaterialTheme.colorScheme.error)}
                            }
                        }
                    }
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)

        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(onDismissRequest={pendingDelete=null},shape=RoundedCornerShape(27.dp),
            title={Text("Delete private video?")},
            text={Text("Permanently delete ${entry.name} from Private Folder?")},
            confirmButton={Button(onClick={
                runCatching{vault.remove(entry)}.onFailure{message="Delete failed: ${it.localizedMessage}"}
                entries=vault.entries();pendingDelete=null
            }){Text("Delete")}},
            dismissButton={TextButton(onClick={pendingDelete=null}){Text("Cancel")}})
    }
}
