# Videonic Lite v16.9 — Live Mini-Player, Clean Track Labels & MKV Stability

Update **only** the compiled v16.8 source. Does not change MkvClusterSeekIndex.kt, data stores, vault, favorites, navigation, the theme, dependencies, or other app pages.

## Changes

1. **Audio and subtitle choices:** Displays only language names (e.g. Hindi, English) and selection ticks. Removes codec/channel count/MIME subtext. Functional options such as Off / Disable audio / Add subtitle file remain.
2. **Real video in mini-player:** When collapsed, the 88×56 mini window attaches its own **Fit** PlayerView to the existing MediaController (not a second playback session). The expanded PlayerView is released first and the thumbnail appears briefly during the surface handoff. Reopening full player restores the existing aspect-ratio boundary.
3. **Delayed jump safeguards:** New playback resets the displayed progress clock. Leaving or reopening the player sends cancellation to the playback service so delayed MKV recovery from an earlier visit — even for the same video — cannot swap the player later.
4. **MKV seek improvements:** Prepares a possible Cluster index on a background worker **after** playback starts, without seeking or changing decoders. A recovered candidate is prepared at the *requested* time, rather than preparing at the old time and performing a second seek. The service abandons canceled requests and limits candidate preparation to four seconds.
5. **No seek warnings:** No extra seek options, popups, or external-player links have been introduced.

## Install

Upload ZIP into `/workspaces/Videonic-Player-Lite`, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v16-9-mini-seek-tracks.zip -d .
python3 videonic-v16-9-mini-seek-tracks/APPLY.py
```

Compile before generating a new APK:

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Live test before Play Store AAB

- Play from Start Over on affected MKV; do **not** touch seek controls for 30 seconds. Watch for any unsolicited 17–18-second jump.
- Collapse to the bottom bar and confirm the **moving video** is displayed (not only a thumbnail). Reopen it in portrait and rotate; verify original aspect ratio.
- Swipe seek / use 10-second buttons / drag the timeline on the affected MKV. Test again after the index has prewarmed.
- Check that Audio and Subtitle sheets show language names with no codec/channel details.
- Check the debug APK is under **30 MB**.

## Technical limitations

An MKV missing Cues or usable Cluster timestamps may **still be slow to seek or unseekable**. No code can promise instantaneous random access to arbitrary malformed media without a valid seek map, and the compact app does not include a heavyweight remuxer or native decoder. An MKV with unusual or discontinuous timestamps may still make its initial displayed position jump; to diagnose that conclusively, a short sample or MediaInfo plus device logs is needed. No user files are rewritten by this patch.

**No full Android SDK compilation or on-device playback test was performed here.** A passing Codespaces compile and phone test are needed before building a release-signed AAB.
