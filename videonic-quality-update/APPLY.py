#!/usr/bin/env python3
from pathlib import Path
import shutil, datetime, sys
root=Path.cwd(); main=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'; manifest=root/'app/src/main/AndroidManifest.xml'
private=root/'app/src/main/java/com/videonic/lite/ui/PrivateFolderScreen.kt'
for p in [main,manifest,private]:
    if not p.exists(): raise SystemExit(f'SAFE STOP: Missing {p}')
s=main.read_text(); m=manifest.read_text()
anchors=['SecurePrivateFolder(onPlay=', 'FeedbackScreen()', 'private fun RecycleBinScreen()', 'var overflowOpen by remember', 'if(!inPip && trackDialog!=null && controller!=null)']
if any(x not in s for x in anchors): raise SystemExit('SAFE STOP: latest major + secure + feedback update not found; no files changed')
if 'VIDEONIC_QUALITY_UPDATE_V1' in s: raise SystemExit('Already applied. No changes made.')
original=s

def once(a,b):
 global s
 if s.count(a)!=1: raise SystemExit(f'SAFE STOP: expected one occurrence for {a[:75]!r}; found {s.count(a)}')
 s=s.replace(a,b,1)

# Keep playback controls visible after actions in More. Menus naturally dismiss when selecting an action, but toolbar stays.
s=s.replace('onClick={trackDialog="Audio";overflowOpen=false;controlTick++}', 'onClick={trackDialog="Audio";overflowOpen=false;controlsVisible=true;controlTick++}')
s=s.replace('onClick={decoderInfo=true;overflowOpen=false;controlTick++}', 'onClick={decoderInfo=true;overflowOpen=false;controlsVisible=true;controlTick++}')
s=s.replace('controller?.repeatMode=repeatMode;overflowOpen=false;controlTick++','controller?.repeatMode=repeatMode;overflowOpen=false;controlsVisible=true;controlTick++')
s=s.replace('controller?.shuffleModeEnabled=shuffled;overflowOpen=false;controlTick++','controller?.shuffleModeEnabled=shuffled;overflowOpen=false;controlsVisible=true;controlTick++')
s=s.replace('onClick={overflowOpen=false;activity.pictureInPicture()}', 'onClick={overflowOpen=false;controlsVisible=true;activity.pictureInPicture()}')
s=s.replace('onClick={overflowOpen=false;controlsVisible=false;activity.captureFrame();controlTick++}', 'onClick={overflowOpen=false;controlsVisible=true;activity.captureFrame();controlTick++}')
# Eliminate duplicate Appearance destination. It remains accessible in Settings.
s=s.replace('        item { MeEntry(Icons.Default.Brightness6,"Theme","System, Light or Dark"){open("Theme")} }\n','')
# New PiP method: reliable entered flag and device support, dynamic aspect ratio, no silent swallow.
start=s.index('    fun pictureInPicture() {')
end=s.index('    fun captureFrame() {',start)
s=s[:start]+'''    fun pictureInPicture() {
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
''' + s[end:]
# Track selection: rich, scrollable with clear selection and available-track details.
start=s.index('                if(!inPip && trackDialog!=null && controller!=null) {')
end=s.index('                if(!inPip && speedDialog)',start)
s=s[:start]+'''                if(!inPip && trackDialog!=null && controller!=null) {
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
''' + s[end:]
# Main app recycle screen is replaced by a better composable (still relies on Android MediaStore, cannot see other gallery app bins).
start=s.index('@Composable\nprivate fun RecycleBinScreen() {')
end=s.index('@Composable\nprivate fun PolicyScreen',start)
s=s[:start]+'''@Composable
private fun RecycleBinScreen() {
    val context=LocalContext.current
    var trashed by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var restore by remember { mutableStateOf<Pair<Uri,String>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode!=Activity.RESULT_OK) error="Restore was cancelled or denied"
        restore=null
        reload++
    }
    LaunchedEffect(reload) {
        busy=true;error=null
        if(Build.VERSION.SDK_INT>=30) {
            val result=withContext(Dispatchers.IO) {runCatching {
                val args=Bundle().apply {putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_ONLY)}
                val found=mutableListOf<Pair<Uri,String>>()
                context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),args,null)?.use { c ->
                    val id=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val name=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    while(c.moveToNext()) found.add(android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(id)) to (c.getString(name)?:"Video"))
                }
                found
            }}
            trashed=result.getOrDefault(emptyList())
            error=result.exceptionOrNull()?.localizedMessage
        } else error="Android 11 or newer is required for system Trash"
        busy=false
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.6f)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.DeleteOutline,null,Modifier.size(38.dp),tint=Accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Recycle Bin",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(if(busy) "Checking device trash…" else "${trashed.size} videos available to restore",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Android's MediaStore trash is shown here. Other gallery apps may keep a separate recycle bin; items can expire automatically.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            OutlinedButton(onClick={reload++},enabled=!busy) {Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Refresh")}
        }
        when {
            busy -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
            error!=null -> Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline,null,tint=MaterialTheme.colorScheme.error,modifier=Modifier.size(35.dp))
                Spacer(Modifier.height(12.dp));Text("Couldn't read device trash",fontWeight=FontWeight.Bold)
                Text(error!!,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trashed.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DeleteSweep,null,tint=Accent,modifier=Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp));Text("Nothing in device trash",fontWeight=FontWeight.SemiBold)
                    Text("Trashed videos you can access will appear here",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                }
            }
            else -> LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(trashed,key={it.first.toString()}) { item ->
                    ElevatedCard(shape=RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Surface(shape=RoundedCornerShape(12.dp),color=Accent.copy(alpha=.12f)) {
                                Icon(Icons.Default.Movie,null,tint=Accent,modifier=Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.second,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                                Text("In device trash",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick={restore=item}) {Text("Restore")}
                        }
                    }
                }
            }
        }
    }
    restore?.let { item -> AlertDialog(
        onDismissRequest={restore=null},shape=RoundedCornerShape(24.dp),
        icon={Icon(Icons.Default.Restore,null,tint=Accent)},
        title={Text("Restore this video?")},
        text={Text("${item.second} will be restored to its original device location if Android permits it.")},
        confirmButton={Button(onClick={
            if(Build.VERSION.SDK_INT>=30) runCatching {
                val pending=MediaStore.createTrashRequest(context.contentResolver,listOf(item.first),false)
                restoreLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }.onFailure {error=it.localizedMessage;restore=null}
        }){Text("Restore")}},
        dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}}
    )}
}

''' + s[end:]
# Marker
s=s.replace('private val Accent = Color(0xFF1169E8)','// VIDEONIC_QUALITY_UPDATE_V1\nprivate val Accent = Color(0xFF1169E8)',1)
# PiP config changes to avoid activity destruction due to bounds adjustment.
m=m.replace('android:configChanges="orientation|screenSize|keyboardHidden"','android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode"')
if 'android:supportsPictureInPicture="true"' not in m: raise SystemExit('SAFE STOP: PiP manifest config missing')

folder_src=Path(__file__).resolve().parent/'PrivateFolderScreen.kt'
if not folder_src.exists(): raise SystemExit('SAFE STOP: Missing private-folder UI patch')
new_private=folder_src.read_text()
# Backups then writes, after validation
stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
for file in (main, manifest, private):
 shutil.copy2(file,file.with_name(file.name+'.before-quality-'+stamp))
main.write_text(s); manifest.write_text(m);private.write_text(new_private)
print('Applied player menu, subtitle/audio, PiP, Me, Private Folder and Recycle Bin improvements.')
print('Backups: *.before-quality-'+stamp)
