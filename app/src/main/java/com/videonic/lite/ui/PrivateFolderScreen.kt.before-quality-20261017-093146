package com.videonic.lite.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    var key by remember {mutableStateOf<ByteArray?>(null)}
    var pin by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf("")}
    var message by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf(vault.entries())}
    var pendingDelete by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    var exporting by remember {mutableStateOf<PrivateVault.Entry?>(null)}
    DisposableEffect(Unit){ vault.cleanPlaybackCache();onDispose{key?.fill(0)} }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null && key!=null){
            val name=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}}.getOrNull()?:"Private video.mp4"
            busy=true
            scope.launch{
                val result=withContext(Dispatchers.IO){runCatching{vault.importVideo(uri,name,key!!)}}
                message=result.fold({"Imported encrypted copy. The original remains in your gallery."},{"Import failed: ${it.localizedMessage}"})
                entries=vault.entries();busy=false
            }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")){uri->
        val item=exporting;exporting=null
        if(uri!=null && item!=null && key!=null){busy=true;scope.launch{
            val result=withContext(Dispatchers.IO){runCatching{vault.exportVideo(item,key!!,uri)}}
            message=result.fold({"Exported copy to selected location."},{"Export failed: ${it.localizedMessage}"});busy=false
        }}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Private Folder",style=MaterialTheme.typography.headlineSmall)
        if(key==null){
            Text(if(vault.configured())"Unlock your encrypted folder" else "Create a 6–32 digit PIN. This PIN encrypts your imported videos. There is no PIN recovery.")
            OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(32)},label={Text("PIN")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            if(!vault.configured())OutlinedTextField(confirm,{confirm=it.filter(Char::isDigit).take(32)},label={Text("Confirm PIN")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            Button(onClick={
                if(!vault.configured()) {
                    if(pin!=confirm || !vault.setup(pin)) message="Enter matching PINs of at least six digits"
                    else {key=vault.unlock(pin);pin="";confirm="";message="Vault created"}
                } else {key=vault.unlock(pin);pin="";if(key==null)message="Incorrect PIN"}
            }){Text(if(vault.configured())"Unlock" else "Create secure folder")}
        }else{
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${entries.size} encrypted videos",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={key?.fill(0);key=null;vault.cleanPlaybackCache()}){Text("Lock")}
            }
            Text("Import makes an encrypted copy in app-private storage; the original is NOT hidden or deleted. To hide the original, move it to Android Trash separately after confirming the encrypted import works.",style=MaterialTheme.typography.bodySmall)
            Button(enabled=!busy,onClick={picker.launch(arrayOf("video/*"))}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Import video")}
            if(entries.isEmpty())Text("No private videos imported yet")
            entries.forEach{entry->
                ElevatedCard {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(entry.name,maxLines=2)
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled=!busy,onClick={busy=true;scope.launch{
                            val result=withContext(Dispatchers.IO){runCatching{vault.preparePlayback(entry,key!!)}}
                            busy=false
                            result.onSuccess{onPlay(it,entry.name)}.onFailure{message="Could not play: ${it.localizedMessage}"}
                        }}){Text("Play")}
                        TextButton(enabled=!busy,onClick={exporting=entry;exporter.launch(entry.name)}){Text("Export")}
                        TextButton(enabled=!busy,onClick={pendingDelete=entry}){Text("Delete")}
                    }
                }}
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
        Text("Security: files are encrypted with AES-GCM using a key derived from your PIN. Temporary decrypted playback files are created in app cache and cleared when the player closes normally. If Android terminates the app unexpectedly, cache may persist until the next folder visit. This is not a guarantee against a compromised or rooted device.",style=MaterialTheme.typography.bodySmall)
    }
    pendingDelete?.let{item->AlertDialog(onDismissRequest={pendingDelete=null},title={Text("Permanently delete encrypted copy?")},text={Text(item.name)},confirmButton={TextButton(onClick={runCatching{vault.remove(item)}.onFailure{message=it.localizedMessage?:"Delete failed"};entries=vault.entries();pendingDelete=null}){Text("Delete")}},dismissButton={TextButton(onClick={pendingDelete=null}){Text("Cancel")}})}
}
