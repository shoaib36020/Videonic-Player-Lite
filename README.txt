VIDEONIC TABLET SCREENSHOT GENERATOR — GITHUB ACTIONS
======================================================
Creates real screenshots by running your existing Videonic app on Android
virtual 7-inch and 10-inch tablets on GitHub Actions. No Android Studio,
physical tablet, release signing key, or Play Store login is required.

1. In GitHub Codespaces terminal:
   cd /workspaces/Videonic-Player-Lite
   unzip -o videonic-tablet-screenshots-github.zip -d .
   git status --short

2. Make sure your latest tested Videonic source is already committed and
   pushed to GitHub. GitHub Actions builds the source on GitHub, NOT unsaved
   files only present in your Codespace. NEVER commit your .jks upload key.
   Do not use 'git add -A' until checking for private files.

3. Stage only the workflow:
   git add .github/workflows/videonic-tablet-screenshots.yml
   git add .github/scripts/capture-tablet-screenshots.sh
   git commit -m "Add GitHub tablet screenshots for Google Play"
   git push origin main

4. Open repository on github.com, go to Actions, click "Videonic Tablet
   Screenshots", and click "Run workflow" → "Run workflow".

5. Open the completed Actions run, scroll to Artifacts, download:
   videonic-7-inch-screenshots
   videonic-10-inch-screenshots

6. Extract both ZIPs, inspect images carefully. Only upload screenshots that
   actually show Videonic. Each ZIP contains raw screen captures; where
   navigation into Folders succeeds, a Folders screenshot will be included.
   If a permissions prompt, blank screen, or non-app UI appears, do NOT
   upload it. Share the GitHub Actions error or screenshot for correction.

Caveat: The workflow has not been run against your actual private Codespaces
project. If repository source is stale, it will show an old version. Gradle
is installed by GitHub Actions, because your repository has no ./gradlew.

Technical dependencies: GitHub Actions, reactivecircus/android-emulator-runner
@v2, Android API 35 AVD, Gradle 8.14, and Android SDK. Only a DEBUG APK is
built; the JKS and signed AAB are never accessed. Original synthetic videos
are generated for the screenshots, not copyrighted media.
