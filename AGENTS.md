# Repository Guidance

## Project
- This is a native Android app with one Gradle module: `:app`.
- Active application code is under `app/src/main`; the main Kotlin entry point is `app/src/main/java/com/videonic/lite/MainActivity.kt`.
- The app uses Kotlin, Jetpack Compose, Media3, Java 17, compile SDK 36, and minimum SDK 26.
- Root-level `videonic-*` directories, backup files, and patch scripts are historical snapshots or tooling. Do not edit them unless the task explicitly targets them.

## Build and Verification
- There is no Gradle wrapper checked into the repository.
- CI installs Java 17, Gradle 8.11.1, and Android SDK 36, then runs:
  `gradle --no-daemon :app:assembleDebug :app:bundleRelease`
- Local builds require a compatible Gradle installation and Android SDK platform/build tools 36. The README notes these prerequisites may be unavailable in some Codespaces environments.
- No test source sets are currently documented; when adding tests, follow Android Gradle conventions and run the narrowest relevant test task plus the CI build when practical.
- Release bundles from CI are unsigned; do not treat them as Play Store-ready.

## Working Conventions
- Keep changes focused on the active app module and preserve existing Kotlin/Compose patterns.
- Check the current worktree before editing. Existing user changes, including modified assets and `MainActivity.kt`, must be preserved.
- Avoid unrelated edits to generated build outputs, archived copies, or release artifacts.
