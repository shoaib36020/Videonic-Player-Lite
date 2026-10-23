# Videonic Google Play signing verification correction

The first release helper could report `ERROR: AAB has no JAR signature block` after `jar signed.` because its `grep -q` exited early and the preceding `unzip` command received SIGPIPE while `pipefail` was enabled. That was a helper false-negative, not proof the bundle was unsigned.

This package changes **only** that check (`grep -Eiq` -> `grep -Ei ... >/dev/null`), allowing `unzip` to finish. There are no Android project changes and no private keys.

## Quick recovery when the previous AAB was built and signed

```
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-google-play-release-fix.zip -d .
bash videonic-google-play-release/RELEASE.sh verify
```

A successful check prints `jar verified.` plus the AAB path and SHA-256 hash. Do **not** use `build` again unless verification genuinely fails and the situation has been diagnosed, since that would attempt to regenerate the AAB. `verify` does not prompt for signing passwords and does not modify the bundle.

Full original usage follows.

---

# Videonic Player Lite — Google Play Release Helper

This tool **does not contain the Videonic Android project**. Run it within your original Codespaces project after applying v32. It does **not modify** any Android source, Gradle settings, or library data. It builds `:app:bundleRelease` and signs an unsigned AAB separately using Java `jarsigner`.

## Requirements

- Your Google Play Console app must be created and use the **same applicationId/package name** as the AAB. Package name cannot be casually changed after publication.
- Google Play requires **targetSdk 36 or higher** for a new normal Android phone app starting August 31, 2026. This helper stops if it sees a *literal* `targetSdk` value below 36 in `app/build.gradle(.kts)`; dynamically defined values must be checked yourself.
- Use the **same upload key** for all future releases. If your Play Console already has an upload key, reuse it; do not create another key without following Play's reset procedure.
- The app's `versionCode` must increase on each subsequent update. `versionName` can be `1.0.0` if this is the first release, but it is separate from the patch v32.
- Play Console listing, Data safety, content rating, permissions, testing eligibility and privacy policy are separate requirements.

## In Codespaces

Upload `videonic-google-play-release.zip` to `/workspaces/Videonic-Player-Lite`, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-google-play-release.zip -d .
bash videonic-google-play-release/RELEASE.sh check
```

Confirm `targetSdk >= 36`, the expected application ID and version code. If the target SDK is too low, do NOT bypass the check; update the Android project compatibility and retest the app first.

**Create upload key once** (only if you don't already have one):

```bash
bash videonic-google-play-release/RELEASE.sh key
```

Choose a strong password and complete the identity prompts. The key is stored outside the repository in `$HOME/videonic-play-signing/videonic-upload.jks` and should **never** be committed, posted, or shared. Immediately download a backup from Codespaces to a safe offline location and keep the password separately.

**Build and sign release:**

```bash
bash videonic-google-play-release/RELEASE.sh build
```

The program builds `app/build/outputs/bundle/release/app-release.aab` and writes a separately signed file:

```
app/build/outputs/bundle/release/Videonic-Player-signed.aab
```

After `jar verified` you can download this file from the Codespaces Explorer and upload it to Google Play Console > your app > Testing > Internal testing > Create new release, or the appropriate track.

**Important:** If the build complains `Gradle already signed app-release.aab`, do **not** re-sign it; inspect `app/build.gradle(.kts)` for `signingConfig`. If the release is signed with a debug key, replace that configuration before Google Play submission. Share only the build script with secrets removed for help.

To verify an existing output later:

```bash
bash videonic-google-play-release/RELEASE.sh verify
```

The helper does not confirm on-device behavior, bundle policy compliance or Play Console acceptance. No key is bundled with this ZIP.
