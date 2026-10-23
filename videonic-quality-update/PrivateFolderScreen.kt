package com.videonic.lite.ui

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videonic.lite.security.PrivateVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SecurePrivateFolder(onPlay:(File,String)->Unit) {
    val context=LocalContext.current
    val vault=remember {PrivateVault(context)}
    val scope=rememberCoroutineScope()
    val accent=MaterialTheme.colorScheme.primary
    var key by remember {mutableStateOf<ByteArray?>(null)}
    var pin by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf("")}
    var message by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf(vault.entries())}
    var pendingDelete by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    var exporting by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    DisposableEffect(Unit){
        vault.cleanPlaybackCache()
        onDispose { key?.fill(0) }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentKey=key
        if(uri!=null && currentKey!=null) {
            val name=runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else null
            }}.getOrNull() ?: "Private video.mp4"
            busy=true
            scope.launch {
                val result=withContext(Dispatchers.IO) {runCatching {vault.importVideo(uri,name,currentKey)}}
                message=result.fold({"Encrypted copy imported. The original remains in your gallery."},{"Import failed: ${it.localizedMessage}"})
                entries=vault.entries();busy=false
            }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { uri ->
        val item=exporting;exporting=null
        val currentKey=key
        if(uri!=null && item!=null && currentKey!=null) {
            busy=true
            scope.launch {
                val result=withContext(Dispatchers.IO) {runCatching {vault.exportVideo(item,currentKey,uri)}}
                message=result.fold({"Video exported to your selected folder."},{"Export failed: ${it.localizedMessage}"})
                busy=false
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(key==null) Icons.Default.Lock else Icons.Default.LockOpen,null,tint=accent,modifier=Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Private Folder",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(if(key==null) "PIN-protected encrypted storage" else "${entries.size} encrypted videos",style=MaterialTheme.typography.bodySmall)
                }
                if(key!=null) OutlinedButton(onClick={key?.fill(0);key=null;vault.cleanPlaybackCache()}){Text("Lock")}
            }
        }
        if(key==null) {
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(if(vault.configured()) "Unlock your vault" else "Set up your vault",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                    Text(if(vault.configured()) "Enter your PIN to see your encrypted videos." else "Create a 6–32 digit PIN. There is no PIN recovery, so keep it safe.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(32)},modifier=Modifier.fillMaxWidth(),label={Text("PIN")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    if(!vault.configured()) OutlinedTextField(confirm,{confirm=it.filter(Char::isDigit).take(32)},modifier=Modifier.fillMaxWidth(),label={Text("Confirm PIN")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    Button(onClick={
                        if(!vault.configured()) {
                            if(pin!=confirm || !vault.setup(pin)) message="PINs must match and contain at least six digits"
                            else {key=vault.unlock(pin);pin="";confirm="";message="Secure folder created"}
                        } else {
                            key=vault.unlock(pin);pin=""
                            if(key==null) message="Incorrect PIN"
                        }
                    },modifier=Modifier.fillMaxWidth()) {
                        Icon(if(vault.configured()) Icons.Default.LockOpen else Icons.Default.Lock,null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(vault.configured()) "Unlock Private Folder" else "Create Private Folder")
                    }
                }
            }
        } else {
            Button(enabled=!busy,onClick={picker.launch(arrayOf("video/*"))},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add,null)
                Spacer(Modifier.width(8.dp));Text("Import encrypted copy")
            }
            Text("Import encrypts a new copy. It does not remove or hide the original gallery file.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
            if(entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder,null,tint=accent,modifier=Modifier.size(55.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your folder is empty",fontWeight=FontWeight.SemiBold)
                        Text("Import videos to protect encrypted copies",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(entries,key={it.id}) { entry ->
                        ElevatedCard(shape=RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Default.Movie,null,tint=accent,modifier=Modifier.size(30.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                                        Text("Encrypted video",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Lock,null,tint=accent,modifier=Modifier.size(18.dp))
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(enabled=!busy,onClick={
                                        val currentKey=key ?: return@FilledTonalButton
                                        busy=true
                                        scope.launch {
                                            val result=withContext(Dispatchers.IO){runCatching{vault.preparePlayback(entry,currentKey)}}
                                            busy=false
                                            result.onSuccess{onPlay(it,entry.name)}.onFailure{message="Playback failed: ${it.localizedMessage}"}
                                        }
                                    }){Text("Play")}
                                    TextButton(enabled=!busy,onClick={exporting=entry;exporter.launch(entry.name)}) {Text("Export")}
                                    TextButton(enabled=!busy,onClick={pendingDelete=entry}) {Text("Delete",color=MaterialTheme.colorScheme.error)}
                                }
                            }
                        }
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Security note: Temporary decrypted videos may be present in app cache while playing. The cache is cleared when playback closes normally or the vault is opened again; unexpected shutdowns may leave temporary files until the next cleanup.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    pendingDelete?.let { entry ->
        AlertDialog(onDismissRequest={pendingDelete=null},shape=RoundedCornerShape(24.dp),
            icon={Icon(Icons.Default.DeleteForever,null,tint=MaterialTheme.colorScheme.error)},
            title={Text("Delete encrypted video?")},
            text={Text("This permanently deletes the private copy of ${entry.name}. The original gallery video, if present, is not affected.")},
            confirmButton={Button(onClick={
                runCatching{vault.remove(entry)}.onFailure{message="Delete failed: ${it.localizedMessage}"}
                entries=vault.entries();pendingDelete=null
            }){Text("Delete copy")}},
            dismissButton={TextButton(onClick={pendingDelete=null}){Text("Cancel")}})
    }
}
