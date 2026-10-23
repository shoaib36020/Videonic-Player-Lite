# Videonic Player v22 — Fullscreen, resume prompt, compact hold-speed

Applies **only** to the successfully compiled v21 `MainActivity.kt`. No other screen, playback-service implementation, stored media, signing configuration, or vault files are changed. The installer compares a SHA-256 hash, backs up the original file, and stops if the current source is different. Do not force it if it says `SAFE STOP`.

## Included changes

1. **Manual fullscreen:** pressing Fullscreen requests and *retains* locked landscape independently of Android's Auto-rotate setting. It stays landscape until Exit Fullscreen (or leaving the player). Without a manual Fullscreen press, the app's enabled auto-rotate preference follows the **system's** user rotation setting (`SCREEN_ORIENTATION_USER`).
2. **Immediate resume:** when resume playback is enabled in Settings and a valid saved position exists, opening a video starts playback at that position instead of presenting a modal dialog first. Videos whose saved position is effectively at the end restart naturally.
3. **Brief restart action:** after the resumed video becomes ready and starts playing, a small *Start from Beginning* action appears above the bottom playback controls for **five seconds** and dismisses automatically. The **X** immediately closes it. Selecting the action requests playback at 00:00, or starts a new session at the default beginning if seeking isn't available.
4. **Hold speed:** the speed selector is now a *174dp-wide* compact pill near the **top** of the video, not centered vertically. It shows the selected rate, tiny marks, and an upward arrow for speed locking. The existing long-press/slide/upward-lock gestures remain unchanged.

Existing player settings, saved positions, headphone behavior, decoder selection, mini-player, and MKV recovery code are not removed. Resume precision in broken or unindexed MKVs still depends on seeking support, and all changes require actual device testing.

## Apply in Codespaces

Upload the ZIP to the project root and run:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v22-fullscreen-resume-speed.zip -d .
python3 videonic-v22-fullscreen-resume-speed/APPLY.py
```

Compile:

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

After successful compilation, build the APK with `:app:assembleDebug` and test the manual landscape lock with **system Auto-rotate OFF**, resume after reopening, the five-second action, and the hold-speed pill in both orientations.
