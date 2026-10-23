# Videonic Lite v12 — Videos, Folders, Playlists, Player and Video Actions

This patch expects **exactly the v11 MainActivity.kt** from v10 plus the v11 clear-glass navigation installer. It changes only `app/src/main/java/com/videonic/lite/MainActivity.kt`; the playback service, Android manifest, private vault and feedback integration are not replaced. It saves an exact backup before changing anything.

## Updated

- **Playlists → Add videos** launches Android's native **Files** document picker with multiple video selection and supports compatible cloud providers (such as Google Drive if installed and enabled). Videos already recognized in Videonic's local MediaStore library are linked without copying. Cloud or other document-only videos are copied into **Movies/Videonic** and added by ID, so the playlist remains playable without keeping a temporary cloud URI open. This is a playlist import, **not** the Private Folder move/delete-original operation.
- **Recent** launches at `LocalLibrary.position(video.id)` without the generic resume prompt. Finished videos start at the beginning.
- **Playback horizontal swipe seek** calculates offsets from the position at gesture start, rather than accumulating jumps each drag event. Vertical brightness/volume gestures and the existing two-side double-tap seek are retained.
- **Video Options → Delete** opens a choice for **Move to Recycle Bin** (Android MediaStore trash) or **Delete Permanently** (Android MediaStore delete). Both require appropriate Android approval (Android 11+ for these flows).
- **Multi-select** uses a two-row adaptive action surface (Favorite, Playlist, Share, Delete, Select all, Close) instead of overflowing text buttons. It never appears in Recent; changing tabs/pages clears selections.
- **Folders header** has its own List/Grid button. Folder view and Videos view have independent saved preferences; folder contents use Folders' view preference.
- **Videos grid** uses compact rounded media cards with duration overlay, colored quality indicator, filename, size, and a visible three-dot button. Other grids remain unchanged.
- **Video option dialogs** for Details, Rename, Add to Playlist, Delete and collection removal use cohesive rounded app styling. System share/file/delete approvals retain their native dialogs intentionally.

## Apply on GitHub Codespaces

1. Upload `videonic-v12-library-actions.zip` to `/workspaces/Videonic-Player-Lite`.
2. Run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v12-library-actions.zip -d .
python3 videonic-v12-library-actions/APPLY.py
```

Do **not** force installation if the script reports `SAFE STOP`. The codespace's actual Kotlin source differs; share your latest `MainActivity.kt` to prepare a compatible patch.

## Compile (do this before building APK)

```bash
gradle :app:compileDebugKotlin \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Once compilation succeeds, use `:app:assembleDebug` with the same Gradle options for the APK.

## Test on device

1. Create a playlist, choose Add videos, pick 1 local and 1 cloud video. Verify content appears and plays.
2. Open a half-watched video in Recent; verify it starts near the saved position.
3. Swipe left/right on an expanded playing video; verify seek changes smoothly. Confirm vertical volume/brightness still work.
4. Use Delete on one video; test both Trash and Permanent Delete with nonessential test clips **only**.
5. Select 2 videos; confirm actions fit on a narrow device; navigate to Recent and check no selection bar remains.
6. Toggle Folders grid/list and Videos grid/list independently, then restart the app to verify preferences.
7. Check Details, Rename, Add to Playlist, and remove-from-collection dialogs in Light and Dark themes.

**Validation:** Kotlin source was parsed with the Kotlin compiler's PSI parser without syntax errors. This is **not** a full Android/Compose type-check or device test; those still require the user's Codespace and phone.
