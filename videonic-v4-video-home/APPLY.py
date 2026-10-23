#!/usr/bin/env python3
"""Targeted UI update for Videonic Lite v3 Videos page and nav spacing.
No other screens, classes or Android resources are modified.
"""
from pathlib import Path
from datetime import datetime
import sys

root = Path(__file__).resolve().parent.parent
source = root / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
if not source.is_file():
    print('ERROR: MainActivity.kt is missing. Extract this ZIP into the Videonic-Player-Lite repository root.')
    sys.exit(1)

original = source.read_text(encoding='utf-8')
marker = '// VIDEONIC_VIDEOS_HOME_V4_20261019'
if marker in original:
    print('ALREADY APPLIED: Videonic Videos Home v4. No files changed.')
    sys.exit(0)

if ('private fun VideosLibraryHome(' not in original or
        'SortAndViewBottomSheet(' not in original or
        'private fun FolderLibraryHome(' not in original or
        'VIDEONIC_VIDEOS_FOLDERS_UI_V2_REPAIR_20261019' not in original):
    print('ERROR: This project is not the expected Videos/Folders v2 + Bottom Sheets v3 source. No files changed.')
    sys.exit(1)

changes = []
def replace_once(old, new, label):
    global original
    count = original.count(old)
    if count != 1:
        raise ValueError(f'{label}: expected exactly one match; found {count}. NO files changed.')
    original = original.replace(old, new, 1)
    changes.append(label)

try:
    replace_once('''private fun dateHeading(timeMillis:Long):String {
    val zone=java.time.ZoneId.systemDefault()
    val date=java.time.Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    val today=java.time.LocalDate.now(zone)
    return when(date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d",Locale.US)).uppercase(Locale.US)
    }
}''', '''// VIDEONIC_VIDEOS_HOME_V4_20261019
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
}''', 'Improved date section headings')

    replace_once('''    onMenu:(LocalVideo)->Unit,
    onToggleSearch:()->Unit,
    onToggleGrid:()->Unit,
    onOpenSort:()->Unit,
    onQueryChange:(String)->Unit
) {
    val sortDescription''', '''    onMenu:(LocalVideo)->Unit,
    onToggleSearch:()->Unit,
    onResumeVideo:()->Unit,
    onToggleGrid:()->Unit,
    onOpenSort:()->Unit,
    onQueryChange:(String)->Unit
) {
    val sortDescription''', 'Videos callback signature')

    replace_once('''                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    HomeCircleButton(Icons.Default.Sort,"Sort videos",onOpenSort)
                    HomeCircleButton(if(grid)Icons.Default.ViewList else Icons.Default.GridView,"Change view",onToggleGrid)
                }
''', '''                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onToggleSearch,modifier=Modifier.size(38.dp)) {
                        Icon(Icons.Default.Search,"Search videos",tint=Ink,modifier=Modifier.size(22.dp))
                    }
                    HomeCircleButton(Icons.Default.Sort,"Sort videos",onOpenSort)
                    HomeCircleButton(if(grid)Icons.Default.ViewList else Icons.Default.GridView,"Change view",onToggleGrid)
                }
''', 'Compact search button in Videos header')

    replace_once('''                val groups=videos.groupBy { dateHeading(it.modified) }
''', '''                // Use chronological date sections only for date sorting. Other sort
                // choices remain globally ordered instead of being regrouped by date.
                val groups=if(sortLabel=="Newest" || sortLabel=="Oldest") {
                    videos.groupBy { dateHeading(it.modified) }
                } else linkedMapOf("ALL VIDEOS" to videos)
''', 'Date grouping follows the selected sorting')

    replace_once('''                        item(heading) {
                            Spacer(Modifier.height(4.dp))
                            Text(heading,color=Muted,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        }
''', '''                        item(heading) {
                            Spacer(Modifier.height(5.dp))
                            Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(heading,color=Muted,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${itemsForDate.size} video${if(itemsForDate.size==1) "" else "s"}",
                                    color=Muted,fontSize=11.sp
                                )
                            }
                        }
''', 'Date heading with count')

    replace_once('''            modifier=Modifier.align(Alignment.BottomEnd).padding(end=22.dp,bottom=24.dp).size(68.dp).clickable(onClick=onToggleSearch)
        ) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Search,"Search",tint=Color.White,modifier=Modifier.size(32.dp)) }
        }
''', '''            modifier=Modifier.align(Alignment.BottomEnd).padding(end=22.dp,bottom=24.dp).size(68.dp).clickable(onClick=onResumeVideo)
        ) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.PlayArrow,"Resume latest video",tint=Color.White,modifier=Modifier.size(37.dp)) }
        }
''', 'Floating Resume/Play button')

    replace_once('''                                onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
                                onToggleGrid={grid=!grid;library.setGrid(grid)},
                                onOpenSort={sortDialog=true},onQueryChange={query=it}
''', '''                                onToggleSearch={librarySearchVisible=!librarySearchVisible;if(!librarySearchVisible)query=""},
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
''', 'Actual latest-video resume behavior')

    replace_once('''                        Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
                            shape=RoundedCornerShape(40.dp),color=MaterialTheme.colorScheme.surface,
''', '''                        Surface(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=8.dp,bottom=22.dp),
                            shape=RoundedCornerShape(40.dp),color=MaterialTheme.colorScheme.surface,
''', 'Bottom navigation lifted with space beneath')

except ValueError as error:
    print('ERROR:', error)
    sys.exit(1)

backup_dir = root / 'videonic-ui-backups'
backup_dir.mkdir(exist_ok=True)
backup = backup_dir / ('MainActivity.before-video-home-v4.' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.kt')
backup.write_text(source.read_text(encoding='utf-8'),encoding='utf-8')
source.write_text(original, encoding='utf-8')
print('SUCCESS: Videos date headings, header search, resume-play button, and bottom navigation spacing updated.')
print('Changed:')
for name in changes: print('  -', name)
print('Backup:', backup)
print('No other source files changed.')
