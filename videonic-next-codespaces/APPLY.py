#!/usr/bin/env python3
"""Conservative source patch for Videonic Lite (after quality + feedback updates)."""
from pathlib import Path
from datetime import datetime
import sys, shutil
root=Path.cwd()
main=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
repo=root/'app/src/main/java/com/videonic/lite/data/VideoRepository.kt'
if not main.exists() or not repo.exists(): sys.exit('STOP: run inside Videonic project root')
s=main.read_text(); r=repo.read_text()
marker='VIDEONIC_LIBRARY_POLISH_20261017'
if marker in s:sys.exit('Already applied; no changes needed')

def sub(src, old, new, name):
    count=src.count(old)
    if count != 1: sys.exit(f'SAFE STOP: cannot locate {name} uniquely ({count} matches). No files changed. Please send the latest source archive.')
    return src.replace(old,new,1)

s=sub(s, 'private val Speeds = listOf(.25f,.5f,1f,1.5f,2f,2.5f,3f,4f)', '''private val Speeds = listOf(.25f,.5f,1f,1.5f,2f,2.5f,3f,4f)
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
}''', 'version marker')

s=sub(s, '''        playing=video; playerExpanded=true; controlsVisible=true; lockPlayer=false; controlTick++''', '''        // Headphone mode must be deliberately enabled for each opened video.
        backgroundAudio=false
        appPrefs.edit().putBoolean("background_audio_enabled",false).apply()
        playing=video; playerExpanded=true; controlsVisible=true; lockPlayer=false; controlTick++''', 'headphone per-video reset')

s=sub(s, '''                                if(tab!="Me" && subpage==null) {''', '''                                if(tab!="Me" && tab!="Folders" && subpage==null) {''', 'folder toolbar cleanup')

s=sub(s, '''                            Text(if(tab=="Recent")"${shown.size} recently watched" else "${shown.size} videos",Modifier.weight(1f),color=Muted,fontSize=13.sp)''', '''                            if(tab=="Recent") Text("${shown.size} recently watched",Modifier.weight(1f),color=Muted,fontSize=13.sp)
                            else Spacer(Modifier.weight(1f))''', 'hide total video count')

s=sub(s, '''                        Text("${fileSize(video.size)} · ${clock(video.duration)}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=Muted)''', '''                        Text("${resolutionLabel(video.width,video.height)} · ${fileSize(video.size)}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=Muted)''', 'grid resolution')

s=sub(s, '''                        Text("${fileSize(video.size)} • ${clock(video.duration)}",fontSize=11.sp,color=Muted)''', '''                        Text("${resolutionLabel(video.width,video.height)} · ${fileSize(video.size)} · ${clock(video.duration)}",fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)''', 'list resolution')

lines=s.splitlines(keepends=True)
matching=[i for i,line in enumerate(lines) if 'item { SettingsToggle(Icons.Default.Headset,' in line]
if len(matching)!=1:sys.exit('SAFE STOP: background audio setting not found uniquely')
lines[matching[0]]='                                item { Text("Background audio must be enabled from the headphone button for each video.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(vertical=8.dp)) }\n'
s=''.join(lines)



# Resume choice and add-videos playlist picker.
s=sub(s, '    var playlistDialog by remember { mutableStateOf(false) }', '''    var playlistDialog by remember { mutableStateOf(false) }
    var resumeCandidate by remember { mutableStateOf<LocalVideo?>(null) }
    var playlistPickerName by remember { mutableStateOf<String?>(null) }
    var playlistPickerIds by remember { mutableStateOf<Set<Long>>(emptySet()) }''', 'dialog state')
s=sub(s, '''    fun openVideo(video: LocalVideo) {
        // Headphone mode''', '''    fun startVideo(video: LocalVideo, startAt:Long) {
        // Headphone mode''', 'start video')
s=sub(s, '},start,if(resumePlayback) library.position(video.id) else 0L)', '},start,startAt)', 'start position')
s=sub(s, '    fun closePlayer() {', '''    fun openVideo(video:LocalVideo) {
        val saved=if(resumePlayback) library.position(video.id) else 0L
        if(saved>5000L) resumeCandidate=video else startVideo(video,0L)
    }
    fun closePlayer() {''', 'resume choice')
s=sub(s, '        videoMenu?.let { video ->', '''        resumeCandidate?.let { candidate ->
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
        videoMenu?.let { video ->''', 'resume and playlist dialogs')
s=sub(s, '''                                val set=library.playlistItems(name)
                                Row''', '''                                val set=library.playlistItems(name)
                                FilledTonalButton(onClick={playlistPickerName=name;playlistPickerIds=emptySet()},modifier=Modifier.padding(horizontal=16.dp)) {
                                    Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add videos")
                                }
                                Row''', 'playlist plus button')

# Make video info fields available from MediaStore, without changing established call sites.
r=sub(r, '''val modified: Long)''', '''val modified: Long, val width:Int=0, val height:Int=0)''', 'LocalVideo dimension fields')
r=sub(r, '''MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_MODIFIED)''', '''MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT)''', 'MediaStore projection')
r=sub(r, '''            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)''', '''            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)''', 'dimension cursor')
r=sub(r, '''if(cursor.isNull(dateCol)) 0 else cursor.getLong(dateCol)))''', '''if(cursor.isNull(dateCol)) 0 else cursor.getLong(dateCol),
                    if(widthCol<0 || cursor.isNull(widthCol))0 else cursor.getInt(widthCol),
                    if(heightCol<0 || cursor.isNull(heightCol))0 else cursor.getInt(heightCol)))''', 'dimension population')

stamp=datetime.now().strftime('%Y%m%d-%H%M%S')
for file in [main,repo]: shutil.copy2(file,file.with_name(file.name+'.before-library-polish-'+stamp))
main.write_text(s);repo.write_text(r)
# Install adaptive launcher icon with a blue background rather than a white plate.
assets=Path(__file__).resolve().parent/'resources'
for source,relative in [
    ('ic_launcher.xml','app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml'),
    ('videonic_launcher_colors.xml','app/src/main/res/values/videonic_launcher_colors.xml')]:
    dest=root/relative
    dest.parent.mkdir(parents=True,exist_ok=True)
    if dest.exists():shutil.copy2(dest,dest.with_name(dest.name+'.before-library-polish-'+stamp))
    shutil.copy2(assets/source,dest)
manifest=root/'app/src/main/AndroidManifest.xml'
old='android:icon="@drawable/videonic_brand"'
if old in manifest.read_text():
    shutil.copy2(manifest,manifest.with_name(manifest.name+'.before-library-polish-'+stamp))
    manifest.write_text(manifest.read_text().replace(old,'android:icon="@mipmap/ic_launcher"',1))
print('Applied: hide video count, clean Folders toolbar, per-video headphone mode, video quality labels.')
print('Backups created for MainActivity.kt and VideoRepository.kt.')
print('Next: compile Kotlin before building APK. Other requested features have NOT been changed.')
