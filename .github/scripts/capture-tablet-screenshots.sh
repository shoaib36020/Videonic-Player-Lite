#!/usr/bin/env bash
# Captures genuine Videonic screen pixels from an Android tablet emulator.
# No Google Play upload key, secret, or production build is required.
set -euo pipefail

TABLET_NAME="${TABLET_NAME:-tablet}"
OUT="screenshots/${TABLET_NAME}"
mkdir -p "$OUT"
APK='app/build/outputs/apk/debug/app-debug.apk'
PACKAGE='com.videonic.lite'

echo "Emulated profile: $TABLET_NAME"
adb wait-for-device
adb shell wm size
adb shell wm density

# Create test videos specifically for screenshots. They are original FFmpeg
# synthetic patterns (not copyrighted films or downloaded third-party assets).
mkdir -p /tmp/videonic-sample-videos
if ! command -v ffmpeg >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq ffmpeg
fi
ffmpeg -hide_banner -loglevel error -y -f lavfi \
  -i 'testsrc2=size=960x540:rate=15' -t 5 -an -c:v mpeg4 -q:v 6 \
  /tmp/videonic-sample-videos/Color_Motion_Demo.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi \
  -i 'smptebars=size=960x540:rate=15' -t 5 -an -c:v mpeg4 -q:v 6 \
  /tmp/videonic-sample-videos/Screen_Test_Demo.mp4

adb shell mkdir -p /sdcard/Movies/Videonic-Demos
adb push /tmp/videonic-sample-videos/Color_Motion_Demo.mp4 /sdcard/Movies/Videonic-Demos/Color_Motion_Demo.mp4
adb push /tmp/videonic-sample-videos/Screen_Test_Demo.mp4 /sdcard/Movies/Videonic-Demos/Screen_Test_Demo.mp4
# Media scanner behavior differs between API levels. Try the standard API;
# subsequent launch/refresh will also read from Android's MediaStore.
for f in Color_Motion_Demo.mp4 Screen_Test_Demo.mp4; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file:///sdcard/Movies/Videonic-Demos/$f" || true
done
sleep 5

adb install -r -g "$APK"
adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_VIDEO || true
adb shell pm grant "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE || true
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
# Dismiss lock screen and keep screenshots in portrait.
adb shell input keyevent 82 || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 0 || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 9

# Force a manual library refresh if the UI supports pull-to-refresh.
RES="$(adb shell wm size | tr -d '\r')"
echo "$RES"
size="${RES##*: }"
W="${size%x*}"
H="${size#*x}"
# Usable fallback for emulator output variants.
if ! [[ "$W" =~ ^[0-9]+$ && "$H" =~ ^[0-9]+$ ]]; then
  W=800; H=1280
fi
XC=$(( W / 2 ))
adb shell input swipe "$XC" "$(( H / 4 ))" "$XC" "$(( H * 3 / 5 ))" 460 || true
sleep 5

# Capture first genuine Android app screen, without modification.
adb exec-out screencap -p > "$OUT/01-videos.png"

echo 'Attempting to open Folders using accessibility tree...'
# Compose semantics often expose the bottom-nav text to Android UIAutomator.
# Find Folders by visible text/content description; no fixed screen taps.
if adb shell uiautomator dump /sdcard/videonic-ui.xml >/dev/null 2>&1; then
  adb exec-out cat /sdcard/videonic-ui.xml > /tmp/videonic-ui.xml || true
  python3 - <<'PY' >/tmp/folders-tap.txt || true
import re, xml.etree.ElementTree as ET
try:
    root = ET.parse('/tmp/videonic-ui.xml').getroot()
    found = []
    for n in root.iter('node'):
        label = ' '.join((n.attrib.get('text',''), n.attrib.get('content-desc',''))).lower()
        if 'folder' not in label:
            continue
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            found.append((int('folders' in label), (x1+x2)//2,(y1+y2)//2))
    if found:
        found.sort(reverse=True)
        print(found[0][1], found[0][2])
except Exception:
    pass
PY
  if [[ -s /tmp/folders-tap.txt ]]; then
    read -r TX TY < /tmp/folders-tap.txt
    adb shell input tap "$TX" "$TY"
    sleep 4
    adb exec-out screencap -p > "$OUT/02-folders.png"
  fi
fi

# If Folders isn't accessible to UIAutomator, capture a second real screen
# with the drawer/list possibly changed. Inspect these before uploading.
if [[ ! -s "$OUT/02-folders.png" ]]; then
  echo 'Folders tab not exposed to automation: capturing second real video view.'
  adb shell input swipe "$XC" "$(( H * 2 / 3 ))" "$XC" "$(( H / 3 ))" 420 || true
  sleep 2
  adb exec-out screencap -p > "$OUT/02-secondary.png"
fi

# Validate PNG headers. These are untouched raw screenshots of app UI.
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
files = list(sorted(p.glob('*.png')))
assert len(files) >= 2, 'Expected at least two screenshots.'
for f in files:
    d=f.read_bytes()
    assert d.startswith(b'\x89PNG\r\n\x1a\n'), f'Invalid PNG: {f}'
    w=int.from_bytes(d[16:20],'big'); h=int.from_bytes(d[20:24],'big')
    print(f'{f}: {w}x{h} ({len(d)//1024} KB)')
    assert 320<=min(w,h) and max(w,h)<=3840, f'{f} out of Play screenshot bounds'
    assert max(w,h)<=2*min(w,h), f'{f} bad aspect ratio'
PY
