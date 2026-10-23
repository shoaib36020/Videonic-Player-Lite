#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK missing. Build using gradle :app:assembleDebug first."
  exit 2
fi
bytes=$(stat -c%s "$APK")
limit=30000000
printf 'Debug APK size: %.2f MB (%s bytes)\n' "$(awk -v v="$bytes" 'BEGIN{printf "%.2f",v/1000000}')" "$bytes"
if (( bytes > limit )); then
  echo "OVER LIMIT: APK is larger than 30 MB. Do not distribute this build as a <=30MB APK."
  exit 1
fi
echo "UNDER LIMIT: APK is 30 MB or smaller."
