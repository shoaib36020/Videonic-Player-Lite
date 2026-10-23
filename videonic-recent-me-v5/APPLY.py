#!/usr/bin/env python3
"""Small guarded Videonic update. Changes only Recent/Me header and body styling.
Preserves existing Videos/Folders screens, bottom sheets, player and user-data stores.
"""
from pathlib import Path
from datetime import datetime
import sys

root = Path.cwd()
source = root / 'app/src/main/java/com/videonic/lite/MainActivity.kt'
part = Path(__file__).resolve().parent / 'ReferenceRecentScreen.ktpart'
if not source.is_file():
    sys.exit('STOP: Open the Videonic repository root before running this script.')
original=source.read_text(encoding='utf-8')
marker='// VIDEONIC_RECENT_ME_V5_20261019'
if marker in original:
    print('ALREADY APPLIED: Recent/Me v5. Nothing changed.')
    sys.exit(0)
required=['// VIDEONIC_SORT_AND_OPTIONS_SHEET_V3_20261019',
          'private fun ReferenceRecentScreen(',
          'private fun MeScreen(',
          'private fun VideosLibraryHome(']
for item in required:
    if item not in original:
        sys.exit(f'STOP: Incompatible source: {item!r} is missing. Nothing changed.')

# All replacement sites must match exactly once BEFORE writing any file.
changes=[]
s=original

def replace_once(before,after,label):
    global s
    count=s.count(before)
    if count!=1:
        raise ValueError(f'{label}: expected 1 anchor, found {count}. No changes made.')
    s=s.replace(before,after,1)
    changes.append(label)

try:
    replace_once('''if(!inPip && !(folder==null && subpage==null && (tab=="Videos" || tab=="Folders"))) Column(Modifier.fillMaxWidth().background(surface)) {''',
                 '''if(!inPip && !(folder==null && subpage==null && (tab=="Videos" || tab=="Folders"))) Column(Modifier.fillMaxWidth().background(
                        if(tab=="Me" && folder==null && subpage==null && !dark) Pale else surface
                    )) {''',
                 'Match Me header background to Me screen')
    replace_once('''.padding(start=20.dp,end=20.dp,top=if(root) 26.dp else 16.dp,bottom=12.dp),''',
                 '''.padding(start=20.dp,end=20.dp,top=if(root) 44.dp else 16.dp,bottom=12.dp),''',
                 'Align Recent and Me headings with Videos/Folders top spacing')
    replace_once('''                            if(root && tab=="Recent" && recent.isNotEmpty()) {
                                TextButton(onClick={confirmClear=true}){Text("Clear",color=Color(0xFFD92D20),fontWeight=FontWeight.SemiBold)}
                            }
''', '', 'Move Recent Clear action to subtitle baseline')
    replace_once('''                        if(root && tab=="Recent") {
                            Text("${recent.size} recently watched",color=Muted,
                                modifier=Modifier.padding(start=22.dp,bottom=12.dp))
                        }
''',
                 '''                        if(root && tab=="Recent") {
                            Row(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,bottom=15.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text("${recent.size} recently watched",Modifier.weight(1f),
                                    color=Muted,fontSize=14.sp)
                                if(recent.isNotEmpty()) Text("Clear",color=Color(0xFFD92D20),
                                    fontSize=14.sp,fontWeight=FontWeight.Bold,
                                    modifier=Modifier.clickable{confirmClear=true}.padding(8.dp))
                            }
                        }
''', 'Position Recent subtitle and Clear as reference')
    start_marker='@Composable\nprivate fun ReferenceRecentScreen('
    end_marker='@Composable\nprivate fun PlayerAction('
    if s.count(start_marker)!=1 or s.count(end_marker)!=1:
        raise ValueError('Recent screen function boundaries changed. No changes made.')
    a=s.index(start_marker)
    b=s.index(end_marker,a)
    if b<=a: raise ValueError('Recent function order invalid. No changes made.')
    new_recent=part.read_text(encoding='utf-8').rstrip()+'\n\n'
    s=s[:a]+new_recent+s[b:]
    changes.append('Continue-watching hero + circular watched-progress play control in Recent')
    # Keep Me changes modest: dashboard content and controls still use original callbacks.
    me_start='@Composable\nprivate fun MeScreen('
    me_end='@Composable\nprivate fun WhiteMetric('
    if s.count(me_start)!=1 or s.count(me_end)!=1:
        raise ValueError('Me screen function boundaries changed. No changes made.')
    a=s.index(me_start)
    b=s.index(me_end,a)
    section=s[a:b]
    old='''                        Text("Videonic Lite",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
'''
    new='''                        Column {
                            Text("Videonic Lite",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text("Version 1.0.0",color=Color.White.copy(alpha=.8f),fontSize=13.sp)
                        }
'''
    if section.count(old)!=1: raise ValueError('Me brand title changed. No changes made.')
    section=section.replace(old,new,1)
    old='''                        WhiteMetric("Storage","${fileSize(storage.first)}")
'''
    new='''                        WhiteMetric("Used of ${fileSize(storage.second)}","${fileSize(storage.first)}")
'''
    if section.count(old)!=1: raise ValueError('Me storage metric changed. No changes made.')
    section=section.replace(old,new,1)
    old='''                    Text("${fileSize(storage.second)} total device storage",color=Color.White.copy(alpha=.78f),
                        fontSize=11.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=7.dp))
'''
    if section.count(old)!=1: raise ValueError('Me storage footer changed. No changes made.')
    section=section.replace(old,'',1)
    s=s[:a]+section+s[b:]
    changes.append('Me card version and device storage metric layout')
    anchor='import androidx.compose.ui.graphics.Brush\n'
    if s.count(anchor)!=1: raise ValueError('Expected Compose Brush import missing. No changes made.')
    s=s.replace(anchor,anchor+'import androidx.compose.foundation.Canvas\nimport androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.drawscope.Stroke\n',1)
    changes.append('Canvas ring drawing imports')
except ValueError as e:
    sys.exit('STOP: '+str(e))

backup_dir=root/'videonic-ui-backups'
backup_dir.mkdir(parents=True,exist_ok=True)
backup=backup_dir/('MainActivity.before-recent-me-v5.'+datetime.now().strftime('%Y%m%d-%H%M%S')+'.kt')
backup.write_text(original,encoding='utf-8')
source.write_text(s,encoding='utf-8')
print('SUCCESS: Recent + Me reference UI v5 applied safely.')
for name in changes: print(' -',name)
print('Source backup:',backup)
print('No other app source or resource files modified.')
