VIDEONIC LITE — CONSOLIDATED CODESPACES SOURCE UPDATE

Applies to the existing native Kotlin/Compose Android repository.
DO NOT install a new APK until compileDebugKotlin succeeds.

WHAT IS IMPLEMENTED IN THIS PATCH
- Long press any video card to start multiple selection (grid/list).
- Bulk favorite, share, add to existing playlist, and system-trash request.
- Rename from video menu via Android write approval (Android 11+).
- Recycle Bin: queries Android system trashed videos and asks for OS permission to restore.
- Leaving app pauses playback by default, unless explicit headphone/background-audio mode is enabled. PiP is exempt.
- Video list scroll resets upon changing sorting method.
- Selected-video card highlights in responsive grid and list.
- About, privacy, terms, permissions/storage, OSS notices, and user-directed email feedback.

NOT YET IMPLEMENTED / NOT VERIFIED
- Encrypted, PIN-protected Private Folder: intentionally NOT claimed as working. No unsafe fake vault.
- Full SMTP feedback backend: email compose only; message is not sent automatically.
- Bulk rename is not offered (rename applies to one selected video via menu).
- Permanent deletion from trash not offered, to prevent accidental data loss.
- Google Play compliance is NOT certified; legal copy and dependency license notices need publishing review.
- No Android SDK compilation or physical device testing was possible here.

HOW TO APPLY
1. Upload ZIP to project root.
2. unzip -o videonic-major-update.zip -d .
3. bash videonic-major-update/APPLY.sh
4. Gradle Kotlin compile:
   gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process

If compilation fails, capture details:
   gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process > /tmp/videonic-major-errors.log 2>&1
   grep -n -E '^e:|^error:' /tmp/videonic-major-errors.log | head -80

The script creates a timestamped backup and refuses conflicting merges.
