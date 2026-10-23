#!/usr/bin/env python3
"""Videonic UI V2 repair. Supports the reference UI release and partial UI release.

Only patches Videos home / Folders home functions and their routing.
No changes to service, secure vault, feedback, Me or Recent screens.
"""
from pathlib import Path
from datetime import datetime
import sys

root=Path.cwd()
target=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
parts=Path(__file__).resolve().parent
if not target.is_file():
    sys.exit('STOP: Project source file not found. Open the Videonic repo root.')
text=target.read_text(encoding='utf-8')
marker='// VIDEONIC_VIDEOS_FOLDERS_UI_V2_REPAIR_20261019'
if marker in text or '// VIDEONIC_VIDEOS_FOLDERS_UI_V2\n' in text:
    print('This V2 screen update is already applied. No changes needed.')
    sys.exit(0)

video_anchor='@Composable\nprivate fun VideosLibraryHome('
folder_anchor='@Composable\nprivate fun FolderLibraryHome('
list_anchor='@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun VideoList('
video_part=(parts/'VideosLibraryHome.ktpart').read_text(encoding='utf-8').rstrip()+'\n\n'
folder_part=(parts/'FolderLibraryHome.ktpart').read_text(encoding='utf-8').rstrip()+'\n\n'

# The older three-screen build can be upgraded in place with the V2 functions.
if text.count(video_anchor)==1 and text.count(folder_anchor)==1 and text.count(list_anchor)==1:
    a=text.index(video_anchor)
    b=text.index(folder_anchor)
    c=text.index(list_anchor)
    if not a < b < c:
        sys.exit('STOP: Unexpected Videos/Folders function order. Nothing changed.')
    updated=text[:a]+marker+'\n'+video_part+folder_part+text[c:]
    mode='Three-screen predecessor upgraded'

# The 10-screen reference UI did NOT contain those functions. Integrate them
# into that version without replacing code for unrelated screens.
elif text.count(video_anchor)==0 and text.count(folder_anchor)==0 and text.count(list_anchor)==1:
    if 'private fun ReferenceRecentScreen(' not in text or 'private fun ReferenceMeScreen(' not in text:
        # The reference Me function can have different naming, so rely on unique
        # documented header and Folders source shapes below instead.
        if 'private fun ReferenceRecentScreen(' not in text:
            sys.exit('STOP: This is not the expected reference UI release. Nothing changed.')

    grid_state='    var grid by remember { mutableStateOf(library.grid()) }\n'
    top_anchor='if(!inPip) Column(Modifier.fillMaxWidth().background(surface)) {'
    folder_start='} else if(tab=="Folders" && folder==null) {'
    folder_end='} else {\n                        val shown=when(tab){'
    video_branch='''                            if(tab=="Recent") ReferenceRecentScreen(shown,library,{openVideo(it)}, {videoMenu=it})
                            else VideoList(shown,grid,library,{openVideo(it)},{videoMenu=it},favorites,
                                "No videos found",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})'''
    checks={grid_state:1,top_anchor:1,folder_start:1,folder_end:1,video_branch:1,list_anchor:1}
    for anchor, expected in checks.items():
        found=text.count(anchor)
        if found!=expected:
            sys.exit(f'STOP: Reference UI differs around {anchor[:80]!r} ({found} matches). No files changed.')

    updated=text
    updated=updated.replace(grid_state,grid_state+'''    var librarySearchVisible by remember { mutableStateOf(false) }
    var folderDescending by remember { mutableStateOf(false) }
''',1)
    updated=updated.replace(top_anchor,'''if(!inPip && !(folder==null && subpage==null && (tab=="Videos" || tab=="Folders"))) Column(Modifier.fillMaxWidth().background(surface)) {''',1)
    start=updated.index(folder_start)
    end=updated.index(folder_end,start)
    new_folder='''} else if(tab=="Folders" && folder==null) {
                        val grouped=videos.groupBy{it.folder}.toList()
                        val groups=grouped.filter{it.first.contains(query,true)}
                            .sortedBy{it.first.lowercase()}
                            .let{if(folderDescending)it.reversed() else it}
                        FolderLibraryHome(
                            folders=groups,grid=grid,query=query,
                            showSearch=librarySearchVisible || query.isNotBlank(),
                            descending=folderDescending,
                            onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
                            onToggleSort={folderDescending=!folderDescending},
                            onQueryChange={query=it},
                            onOpenFolder={name->folder=name;query=""}
                        )
                    '''
    updated=updated[:start]+new_folder+updated[end:]
    new_video_branch='''                            if(tab=="Recent") ReferenceRecentScreen(shown,library,{openVideo(it)}, {videoMenu=it})
                            else if(tab=="Videos" && folder==null) VideosLibraryHome(
                                videos=shown,grid=grid,sortLabel=sort,query=query,
                                showSearch=librarySearchVisible || query.isNotBlank(),
                                library=library,selected=selectedIds,
                                onSelect={id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id},
                                onOpen={openVideo(it)},onMenu={videoMenu=it},
                                onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
                                onToggleGrid={grid=!grid;library.setGrid(grid)},
                                onOpenSort={sortDialog=true},onQueryChange={query=it}
                            )
                            else VideoList(shown,grid,library,{openVideo(it)},{videoMenu=it},favorites,
                                "No videos found",selectedIds,{id->selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id})'''
    if updated.count(video_branch)!=1:
        sys.exit('STOP: Video route has changed. Nothing changed.')
    updated=updated.replace(video_branch,new_video_branch,1)
    utilities='''private fun dateHeading(timeMillis:Long):String {
    val zone=java.time.ZoneId.systemDefault()
    val date=java.time.Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    val today=java.time.LocalDate.now(zone)
    return when(date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d",Locale.US)).uppercase(Locale.US)
    }
}

@Composable
private fun HomeCircleButton(icon:androidx.compose.ui.graphics.vector.ImageVector, description:String,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(100),color=Color(0xFFF3F5FA),modifier=Modifier.size(50.dp).clickable(onClick=onClick)) {
        Box(contentAlignment=Alignment.Center){Icon(icon,description,tint=Ink,modifier=Modifier.size(22.dp))}
    }
}

'''
    updated=updated.replace(list_anchor,marker+'\n'+utilities+video_part+folder_part+list_anchor,1)
    mode='Reference redesign adapted directly to V2'
else:
    sys.exit('STOP: Current MainActivity does not match either supported release. Nothing changed. Send MainActivity.kt for a tailored patch.')

if updated==text:
    sys.exit('STOP: Nothing to change.')
backup_dir=root/'videonic-ui-backups'
backup_dir.mkdir(exist_ok=True)
backup=backup_dir/f'MainActivity.before-v2-repair.{datetime.now():%Y%m%d-%H%M%S}.kt'
backup.write_text(text,encoding='utf-8')
tmp=target.with_name('MainActivity.kt.v2repair.tmp')
tmp.write_text(updated,encoding='utf-8')
tmp.replace(target)
print('SUCCESS: '+mode)
print('Updated Videos list/grid and Folders list/grid only.')
print('Backup:', backup)
