# Videonic Player — Lite Video Player

**Status: early Android alpha source, not Play Store release-ready.**

This is a native Kotlin/Compose Android starter containing MediaStore video discovery, folder browsing, search, sorting, basic favorites, a Media3 MediaSessionService and video playback, seeking, speed control, repeat-one, orientation toggle and light/dark mode. It is NOT the complete requested feature set yet.

## Build in GitHub
1. Create a GitHub repository and upload the contents of this directory to the repository root.
2. Open **Actions → Videonic Android Build → Run workflow**.
3. Download the `Videonic-Lite-Android-Build` artifact when the build succeeds.
4. The debug APK can be installed for testing. The release AAB generated without signing configuration is **unsigned and not Play Store upload-ready**.

## Codespaces
Open the repository in Codespaces. The included devcontainer supplies Java. For builds, the GitHub Actions workflow installs the Android SDK and Gradle. Codespaces local builds additionally require Android SDK command-line tools, platform 36, build tools 36 and Gradle 8.11.1.

## Known limitations
- This source has **not been compiled or run** in the current environment (Android SDK/Gradle and network are unavailable).
- No verified release signing, APK or AAB is included.
- Not yet implemented: persistent history/resume, playlists, thumbnail previews, advanced gestures, auto-next queue, subtitle/audio track selection, equalizer, decoder selection UI, secure private folder, recycle bin, PiP controls, mini-player, secure file operations, and functional playback settings beyond the listed basics.
- Background playback service is present but user-facing background-mode opt-in and notification flow need testing/implementation.
- Test on an Android device and resolve any build errors before publishing.

## Play Store release prerequisites
Replace the provisional app ID if necessary **before** first publication, add privacy policy, create and securely back up a release/upload keystore, configure signed AAB output, run Android tests and check Play Console policies.
