Videonic combined upgrade (2026-10-17)

This package replaces the previous uninstalled playback-upgrade ZIP. DO NOT install both.

Contains:
- Previous playback toolbar, touch gestures, PiP, lock, audio/subtitle selection controls
- Full Me > Settings: appearance + grid, auto rotate, resume playback, auto play next,
  keep screen awake, default speed (persisted locally)
- One-step Create and Add to playlist from a video menu
- Confirmation before deleting a playlist
- Removes 'Local media only • No network streaming' text

Important current scope:
- Auto Play Next is applied when opening a new video; turning it off creates a
  one-item Media3 queue. Existing already-playing queue is unchanged until a video opens.
- Auto Rotate configures the playback screen sensor orientation.
- This is a source update, not a finished APK. It has not been compiled here.
- Existing favorites and Recent history are stored in preferences; installing this update
  does not wipe them. Keep using the same applicationId and avoid uninstall/clear data.
- Work still needed: private folder, MediaStore trash, SMTP feedback backend, external
  subtitle import, hardware decoder override and more device QA.

Apply:
  cd /workspaces/Videonic-Player-Lite
  unzip -o videonic-combined-settings-playback.zip -d .
  bash videonic-redesign/APPLY_IN_CODESPACES.sh

Then compile:
  gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel \
    --no-configuration-cache \
    -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
    -Pkotlin.compiler.execution.strategy=in-process
