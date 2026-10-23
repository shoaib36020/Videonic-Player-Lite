# Videonic Lite v11 — Clear glass navigation (height restoration)

Updates **only** `VideonicBottomNavigation` in `MainActivity.kt`. The installer accepts the exact v9 or v10 navigation function and leaves all other project files and screens unchanged. It backs up your live source before editing.

## Visual changes
- Transparent, neutral frosted whole-bar finish in Light and Dark modes (no thick blue overlay)
- No separate chips behind inactive icons
- A subtle translucent selection pill with blue icon/label, rather than an opaque blue capsule
- Restores bar content height to the pre-v10 62dp; keeps existing outer margins and system-space layout
- Preserves Classic navigation and all four existing tab callbacks

This is a **translucent glass-style finish**, not genuine real-time iOS backdrop blur. True cross-version background blur would require a dedicated backdrop-rendering approach.

## Apply in Codespaces

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-v11-clear-glass-navigation.zip -d .
python3 videonic-v11-clear-glass-navigation/APPLY.py
```

## Compile

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

## Build APK after successful compilation

```bash
gradle :app:assembleDebug --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Verification

The patch has been statically reviewed and install-tested against the v9 and v10 navigation snippets. Full Android compilation requires your Codespaces Android SDK.
