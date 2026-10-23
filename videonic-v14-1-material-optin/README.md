# Videonic Lite v14.1 — Material3 opt-in compile fix

Fixes six compiler errors at MainActivity.kt lines 2936–3071 by annotating `DecoderChoiceSheet` and `PlayerDecoderDetailsSheet` with `@OptIn(ExperimentalMaterial3Api::class)`.

Upload the archive to `/workspaces/Videonic-Player-Lite` and run:

```bash
unzip -o videonic-v14-1-material-optin.zip -d .
python3 videonic-v14-1-material-optin/APPLY.py
```

Then compile with your existing Gradle command. Patch only changes two annotations; the playback service and other screens are untouched. The installer makes a backup, is idempotent, and refuses unmatched source. It was tested against the distributed v14 source but Android build still needs to be run in Codespaces.
