#!/usr/bin/env bash
set -euo pipefail
cd /workspaces/Videonic-Player-Lite
python3 videonic-v36-splash-speed/APPLY.py
gradle :app:assembleDebug \
  --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" \
  -Pkotlin.compiler.execution.strategy=in-process
ls -lh app/build/outputs/apk/debug/app-debug.apk
