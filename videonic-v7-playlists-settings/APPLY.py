#!/usr/bin/env python3
"""Apply Videonic v7 to the confirmed v6 files; fail closed on unexpected changes."""
from pathlib import Path
import hashlib, datetime, sys

ROOT=Path.cwd()
MAIN=ROOT/'app/src/main/java/com/videonic/lite/MainActivity.kt'
VAULT=ROOT/'app/src/main/java/com/videonic/lite/ui/PrivateFolderScreen.kt'
HERE=Path(__file__).resolve().parent
MAIN_EXPECT='f5e0a902d87682b086dcf5f244a6b1e0a89c2513238dbbfe72478102377b55fe'
MAIN_NEW='93f028499f089968a84da2e7190a1f5a73f443f9d293565eb7e035077e92f5a2'
VAULT_ALLOWED=['0319b0004d45a3b7b193d67cc3baabfb49db9bec832cbcee7c2ee3b126b87bd1', 'd1ad94dc7ff924f5240f4afb21a8a9f5cd1afb8042d92fca08dccd9aa8594e3d']
VAULT_NEW='4940269411e54a3ce9b9dd544016ec0f14c65056e13c3e84df61570d82d79094'

def sha(b):return hashlib.sha256(b).hexdigest()
if not MAIN.is_file() or not VAULT.is_file():
    sys.exit('SAFE STOP: Could not find MainActivity.kt or PrivateFolderScreen.kt. Open the original Videonic Codespace.')
current_main=MAIN.read_bytes()
current_vault=VAULT.read_bytes()
if sha(current_main)==MAIN_NEW and sha(current_vault)==VAULT_NEW:
    print('ALREADY APPLIED: V7 is installed. No changes made.')
    sys.exit(0)
if sha(current_main)!=MAIN_EXPECT:
    print('SAFE STOP: MainActivity.kt does not match your verified V6 build. Nothing modified.')
    print('Current MainActivity SHA256:',sha(current_main))
    print('Expected:',MAIN_EXPECT)
    print('Please send your current MainActivity.kt so we can prepare a matching patch.')
    sys.exit(1)
if sha(current_vault) not in VAULT_ALLOWED:
    print('SAFE STOP: Your Private Folder UI differs from the previous supported versions. Nothing modified.')
    print('Current PrivateFolderScreen SHA256:',sha(current_vault))
    print('Please send that file; we will preserve your working security code.')
    sys.exit(1)
updated_main=(HERE/'MainActivity.kt').read_bytes()
updated_vault=(HERE/'PrivateFolderScreen.kt').read_bytes()
if sha(updated_main)!=MAIN_NEW or sha(updated_vault)!=VAULT_NEW:
    sys.exit('SAFE STOP: ZIP integrity mismatch. Nothing modified.')
backup_dir=ROOT/'videonic-ui-backups'
backup_dir.mkdir(parents=True,exist_ok=True)
tag=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
main_backup=backup_dir/f'MainActivity.before-v7.{tag}.kt'
vault_backup=backup_dir/f'PrivateFolderScreen.before-v7.{tag}.kt'
main_backup.write_bytes(current_main)
vault_backup.write_bytes(current_vault)
try:
    MAIN.write_bytes(updated_main)
    VAULT.write_bytes(updated_vault)
except Exception as exc:
    MAIN.write_bytes(current_main)
    VAULT.write_bytes(current_vault)
    sys.exit(f'APPLY FAILED: restored originals: {exc}')
print('SUCCESS: Videonic v7 Playlists, Settings, Theme, Favorites, Private Folder PIN, Recycle Bin UI applied.')
print('MainActivity backup:',main_backup)
print('Private Folder UI backup:',vault_backup)
print('Unchanged: playback service, vault encryption, feedback backend, Videos/Folders/Recent and all dependencies.')
