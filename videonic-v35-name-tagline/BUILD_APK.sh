#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
python3 videonic-v35-name-tagline/APPLY.py
if command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
elif [ -f ./gradlew ]; then
  chmod +x ./gradlew
  GRADLE=./gradlew
else
  echo 'ERROR: Gradle unavailable. Please share terminal output.' >&2
  exit 1
fi
"$GRADLE" :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC' \
  -Pkotlin.compiler.execution.strategy=in-process
printf '\nAPK ready: app/build/outputs/apk/debug/app-debug.apk\n'
ls -lh app/build/outputs/apk/debug/app-debug.apk
