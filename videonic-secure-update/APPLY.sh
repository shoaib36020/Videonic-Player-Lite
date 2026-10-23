#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST='app/src/main/java/com/videonic/lite/MainActivity.kt'
python3 - "$DEST" "$HERE" <<'PY'
from pathlib import Path
import sys, datetime, shutil
p=Path(sys.argv[1]);here=Path(sys.argv[2]);s=p.read_text()
checks=['"Private Folder" -> PrivateFolderScreen()', '"Support & Feedback" -> LazyColumn(', 'fun closePlayer() {', 'playing=null; playerExpanded=false; speedDialog=false; lockPlayer=false']
missing=[a for a in checks if a not in s]
if missing:
    raise SystemExit('SAFE STOP: expected major-update anchors not present: '+', '.join(missing))
if 'SecurePrivateFolder(onPlay=' in s:
    raise SystemExit('Already applied: no changes made')
backup=p.with_name(p.name+'.before-secure-'+datetime.datetime.now().strftime('%Y%m%d%H%M%S'))
shutil.copy2(p,backup)
s=s.replace('import com.videonic.lite.player.VideonicPlaybackService','import com.videonic.lite.ui.SecurePrivateFolder\nimport com.videonic.lite.ui.FeedbackScreen\nimport com.videonic.lite.security.PrivateVault\nimport com.videonic.lite.player.VideonicPlaybackService',1)
s=s.replace('"Private Folder" -> PrivateFolderScreen()', '''"Private Folder" -> SecurePrivateFolder(onPlay={ file,name ->
                                openVideo(LocalVideo(-(file.name.hashCode().toLong().and(0x7fffffffL)+1L),Uri.fromFile(file),name,0L,file.length(),"Private",System.currentTimeMillis()))
                            })''',1)
a=s.index('                            "Support & Feedback" -> LazyColumn(')
b=s.index('                            "Settings" -> LazyColumn(',a)
s=s[:a]+'                            "Support & Feedback" -> FeedbackScreen()\n'+s[b:]
s=s.replace('        playing=null; playerExpanded=false; speedDialog=false; lockPlayer=false','        if(playing?.folder == "Private") PrivateVault(context).cleanPlaybackCache()\n        playing=null; playerExpanded=false; speedDialog=false; lockPlayer=false',1)
s=s.replace('val queue=if(autoPlayNext) videos.ifEmpty { listOf(video) } else listOf(video)','val queue=if(autoPlayNext && video.folder != "Private") videos.ifEmpty { listOf(video) } else listOf(video)',1)
p.write_text(s)
manifest=Path('app/src/main/AndroidManifest.xml')
m=manifest.read_text()
if 'android.permission.INTERNET' not in m:
    m=m.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">','<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET"/>',1)
    manifest.write_text(m)
print('Updated MainActivity:',p,'backup:',backup)
PY
cp "$HERE/android/com/videonic/lite/security/PrivateVault.kt" app/src/main/java/com/videonic/lite/security/PrivateVault.kt 2>/dev/null || { mkdir -p app/src/main/java/com/videonic/lite/security; cp "$HERE/android/com/videonic/lite/security/PrivateVault.kt" app/src/main/java/com/videonic/lite/security/PrivateVault.kt; }
mkdir -p app/src/main/java/com/videonic/lite/ui
cp "$HERE/android/com/videonic/lite/ui/PrivateFolderScreen.kt" app/src/main/java/com/videonic/lite/ui/PrivateFolderScreen.kt
cp "$HERE/android/com/videonic/lite/ui/FeedbackScreen.kt" app/src/main/java/com/videonic/lite/ui/FeedbackScreen.kt
printf '\nUpdate applied. Run :app:compileDebugKotlin before APK packaging.\n'
