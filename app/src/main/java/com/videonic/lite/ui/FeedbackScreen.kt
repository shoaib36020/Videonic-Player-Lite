package com.videonic.lite.ui

import android.os.Build
import android.util.Base64
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val FEEDBACK_API_URL = "https://wheat-stork-207923.hostingersite.com/api/feedback"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var category by remember{mutableStateOf("Bug Report")}
    var title by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("")}
    var contact by remember{mutableStateOf("")}
    var attachments by remember{mutableStateOf<List<Uri>>(emptyList())}
    var status by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){ chosen->
        attachments=chosen.take(5)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Support & Feedback",style=MaterialTheme.typography.headlineSmall)
        Text("Describe a bug, ask a question, or suggest an improvement. Videos and personal files are never attached automatically.")
        Text("Your report is sent securely to Videonic support. Only the details and screenshots you choose to submit are included.",style=MaterialTheme.typography.bodySmall)
        Text("Category")
        listOf("Bug Report","Feature Request","Query","Feedback","Other").chunked(3).forEach{row->
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){row.forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)})}}
        }
        OutlinedTextField(title,{title=it.take(120)},label={Text("Title")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(message,{message=it.take(4000)},label={Text("Description")},minLines=5,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(contact,{contact=it.take(180)},label={Text("Reply email (optional)")},modifier=Modifier.fillMaxWidth())
        OutlinedButton(enabled=!busy,onClick={picker.launch("image/*")}){Text("Attach screenshots (${attachments.size}/5)")}
        Text("Attach up to five screenshots. Images are sent only after you press Submit. Limit 2 MB each.",style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy && title.isNotBlank() && message.length>=10,onClick={
            val url=FEEDBACK_API_URL
            busy=true;status="Sending..."
            scope.launch{
                val result=withContext(Dispatchers.IO){runCatching{
                    val a=JSONArray()
                    attachments.forEachIndexed{i,uri->
                        val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Cannot read attachment")
                        require(bytes.size<=2_000_000){"Screenshot ${i+1} exceeds 2 MB"}
                        a.put(JSONObject().put("filename","screenshot_${i+1}.jpg").put("contentType",context.contentResolver.getType(uri)?:"image/jpeg").put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)))
                    }
                    val data=JSONObject().put("category",category).put("title",title.trim()).put("message",message.trim())
                        .put("replyTo",contact.trim()).put("device","${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}")
                        .put("appVersion","1.0.0-alpha01").put("screenshots",a)
                    val connection=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=12000;readTimeout=20000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8")}
                    try{
                        connection.outputStream.use{it.write(data.toString().toByteArray(Charsets.UTF_8))}
                        if(connection.responseCode !in 200..299)error("Server returned ${connection.responseCode}")
                    }finally{connection.disconnect()}
                }}
                busy=false;status=result.fold({"Feedback submitted successfully. Thank you."},{"Could not submit: ${it.localizedMessage}"})
                if(result.isSuccess){message="";title="";attachments=emptyList()}
            }
        }){Text(if(busy)"Submitting..." else "Submit feedback")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank())Text(status)
    }
}
