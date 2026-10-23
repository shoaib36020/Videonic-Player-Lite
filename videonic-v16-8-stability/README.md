# Videonic Lite v16.8 — portrait ratio and unexpected jump safeguards

Apply **only** to the successfully compiled v16.7 source with its original
`MkvClusterSeekIndex.kt` from v16.6.

## Changes
- Original/Fit video receives a **second, outer aspect-ratio boundary** based on
  the local media metadata, with decoder-size fallback. Crop/Stretch/Width modes
  still work when deliberately selected. This specifically targets distorted
  dimensions after reopening the mini-player in portrait.
- Prevents delayed asynchronous MKV seek callbacks from applying an old target
  after playback has advanced, restarted, or been reopened.
- Stops very slow MKV index scans from replacing a playing decoder 18–20 seconds
  later. Scans now have a three-second limit; expired results are discarded.
- Rejects reconstructed indexes whose first Cluster timestamp begins unusually
  far from zero to avoid jumping the video forward on a requested seek to start.
- Keeps standard seeking for indexed, seekable video files unchanged.
- No new libraries, permissions, large codecs, or external-player options.

## Install
From the project root:
```bash
unzip -o videonic-v16-8-stability.zip -d .
python3 videonic-v16-8-stability/APPLY.py
```

## Compile
```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Test
1. Start an affected MKV at 0:00 without touching the seek bar. Verify it
   does not jump forward after 20 seconds.
2. Press Back and reopen through the mini-player in portrait. Verify the
   image is not stretched. Repeat after rotating to landscape.
3. Test normal video progress seeking. For a badly indexed MKV,
   seek reconstruction is best effort and may still be unavailable.

**Limitations:** Not compiled against Android SDK in this environment.
This is a guarded repair for two identified code paths, not proof all MKVs
will have reliable seek indexes or that every video metadata record is valid.
Do not uninstall the existing app or erase app data.
