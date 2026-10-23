#!/usr/bin/env python3
"""Only replace the bottom navigation function. Preserve all other Videonic source."""
from pathlib import Path
from datetime import datetime
import hashlib, os, sys

root=Path.cwd()
target=root/'app/src/main/java/com/videonic/lite/MainActivity.kt'
nav_path=Path(__file__).resolve().parent/'navigation.kt.fragment'
start='@Composable\nprivate fun VideonicBottomNavigation(selectedTab:String,glass:Boolean,onSelect:(String)->Unit) {'
end='\n@Composable\nprivate fun PlayerAction('
expected={
    'V9_SHA': '3458643c12deb908ff4df0937d986491aabd90205c3b1131e30cb54f7ad58ae9',
    'V10_SHA': 'e62cb68dd361740b9c497368bb7185f51bcdc7977e132666a7caf0bb4a9f2bbb'
}
new_hash='995dd0814462e17e50b23b8052b9c21eb96eb04005ffb018907f5b4b1edf6ba4'

def digest(data): return hashlib.sha256(data.encode()).hexdigest()
def stop(reason): sys.exit('SAFE STOP: '+reason+'; no files changed.')
if not target.is_file():stop('Current MainActivity.kt not found. Run from repository root')
src=target.read_text()
nav=nav_path.read_text()
if digest(nav)!=new_hash:stop('Patch file failed verification')
if src.count(start)!=1 or src.count(end)!=1:stop('Navigation function markers differ from expected')
start_at=src.index(start)
end_at=src.index(end,start_at)
old=src[start_at:end_at]
if digest(old)==new_hash:
    print('ALREADY APPLIED: v11 clear glass navigation is installed.')
    raise SystemExit(0)
if digest(old) not in expected.values():
    print('Current navigation SHA256:',digest(old))
    stop('Current navigation differs from supported v9/v10. Upload latest MainActivity.kt before changing it')
backups=root/'videonic-ui-backups'
backups.mkdir(exist_ok=True)
backup=backups/('before-v11-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'.kt')
backup.write_text(src)
new=src[:start_at]+nav+src[end_at:]
try:
    tmp=target.with_suffix('.kt.v11tmp')
    tmp.write_text(new)
    os.replace(tmp,target)
except Exception as exc:
    target.write_text(src)
    stop('Failed to apply update; original restored: '+str(exc))
assert target.read_text()==new
print('SUCCESS: clear transparent glass bar installed. Original navigation height restored.')
print('Only VideonicBottomNavigation was modified.')
print('Backup:',backup)
