#!/usr/bin/env python3
"""Small, source-guarded patch for Videonic V2 Sort & Video Options sheets.

Only changes the two sheets, the Folders sorting route/state, and their tiny helper.
Stops before writing if any expected anchor has changed.
"""
from pathlib import Path
from datetime import datetime
import sys

root=Path.cwd()
target=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
if not target.is_file():sys.exit('STOP: Run this inside the Videonic repository root.')
s=target.read_text(encoding='utf-8')
marker='// VIDEONIC_SORT_AND_OPTIONS_SHEET_V3_20261019'
if marker in s:sys.exit('Already applied. Nothing changed.')
if '// VIDEONIC_VIDEOS_FOLDERS_UI_V2_REPAIR_20261019' not in s:
    sys.exit('STOP: Apply Videonic UI V2 repair first. No source changed.')
state_anchor='    var folderDescending by remember { mutableStateOf(false) }\n'
route_anchor='''                        val groups=grouped.filter{it.first.contains(query,true)}
                            .sortedBy{it.first.lowercase()}
                            .let{if(folderDescending)it.reversed() else it}'''
route_replacement='''                        val folderComparator=when(folderSortField) {
                            "Date added" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.maxOfOrNull{it.modified} ?: 0L }
                            "Size" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.sumOf{it.size} }
                            "Duration" -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.second.sumOf{it.duration} }
                            else -> compareBy<Pair<String,List<LocalVideo>>> { item -> item.first.lowercase(Locale.US) }
                        }
                        val groups=grouped.filter{it.first.contains(query,true)}
                            .sortedWith(if(folderDescending) folderComparator.reversed() else folderComparator)'''
folder_sort_anchor='                            onToggleSort={folderDescending=!folderDescending},'
sort_start='        if(sortDialog) AlertDialog('
sort_end='        if(playlistDialog) AlertDialog('
sheet_helper_anchor='@Composable\nprivate fun SheetAction('
video_title_anchor='SheetAction(Icons.Default.Info,"Properties",Muted)'
video_video_header='''                    HorizontalDivider(Modifier.padding(vertical=14.dp))'''
sheet_action_style='''    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(onClick=onClick)
        .padding(vertical=11.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))'''
for name,anchor,expected in [
    ('folder state',state_anchor,1),('folder sorting',route_anchor,1),('folder sort button',folder_sort_anchor,1),
    ('sort sheet start',sort_start,1),('sort sheet end',sort_end,1),('action helper',sheet_helper_anchor,1),
    ('video Details',video_title_anchor,1),('video sheet header',video_video_header,1),('sheet action styling',sheet_action_style,1),
]:
    if s.count(anchor)!=expected:
        sys.exit(f'STOP: {name} differs ({s.count(anchor)} matches). No source changed.')
start=s.index(sort_start);end=s.index(sort_end,start)
if end<=start: sys.exit('STOP: Unexpected sort sheet location.')
result=s
result=result.replace(state_anchor,state_anchor+'    var folderSortField by remember { mutableStateOf("Name") }\n',1)
result=result.replace(route_anchor,route_replacement,1)
result=result.replace(folder_sort_anchor,'                            onToggleSort={sortDialog=true},',1)
# Replace just one old sort dialog, leaving other dialogs and their state untouched.
start=result.index(sort_start);end=result.index(sort_end,start)
new_sheet='''        if(sortDialog) SortAndViewBottomSheet(
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
'''
result=result[:start]+new_sheet+result[end:]
# Keep the video menu actions and security dialogs unchanged; improve the visual look.
result=result.replace(video_title_anchor,'SheetAction(Icons.Default.Info,"Details",Muted)',1)
result=result.replace(sheet_action_style,'''    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(onClick=onClick)
        .padding(vertical=12.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))''',1)
result=result.replace('''        Text(label,fontSize=16.sp,fontWeight=FontWeight.Medium)
    }
}
private fun sortDescription''','''        Text(label,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    }
}
private fun sortDescription''',1)
part=(Path(__file__).resolve().parent/'SortAndViewBottomSheet.ktpart').read_text(encoding='utf-8')
if result.count(sheet_helper_anchor)!=1:sys.exit('STOP: Sheet insertion anchor changed.')
result=result.replace(sheet_helper_anchor,marker+'\n'+part.strip()+'\n\n'+sheet_helper_anchor,1)
if result==s:sys.exit('STOP: Nothing modified.')
backup_dir=root/'videonic-ui-backups';backup_dir.mkdir(exist_ok=True)
backup=backup_dir/f'MainActivity.before-sheet-v3.{datetime.now():%Y%m%d-%H%M%S}.kt'
backup.write_text(s,encoding='utf-8')
tmp=target.with_name('MainActivity.kt.sheetv3.tmp')
tmp.write_text(result,encoding='utf-8')
tmp.replace(target)
print('SUCCESS: Sort & View bottom sheet + Video Options styling updated.')
print('Folder sorting now supports name, date added, total size and total duration.')
print('Other pages and playback logic unchanged.')
print('Backup:',backup)
