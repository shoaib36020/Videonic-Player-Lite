#!/usr/bin/env bash
# Videonic Player Lite - Google Play Release Helper
# Works with a full Android project in Codespaces, not standalone source patches.
set -Eeuo pipefail
umask 077

PROJECT_DIR="${VIDEONIC_PROJECT_DIR:-/workspaces/Videonic-Player-Lite}"
KEY_DIR="${VIDEONIC_KEY_DIR:-$HOME/videonic-play-signing}"
KEY_FILE="$KEY_DIR/videonic-upload.jks"
KEY_ALIAS="videonic-upload"
OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/bundle/release"
RAW_BUNDLE="$OUTPUT_DIR/app-release.aab"
SIGNED_BUNDLE="$OUTPUT_DIR/Videonic-Player-signed.aab"

fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
info() { printf '\n== %s ==\n' "$*"; }
require() { command -v "$1" >/dev/null 2>&1 || fail "'$1' was not found. Install it in Codespaces before continuing."; }

usage() {
  cat <<'TXT'
Usage: bash videonic-google-play-release/RELEASE.sh {check|key|build|verify}
  check  Show the app's build configuration; stop on a literal targetSdk below 36.
  key    Create the upload keystore OUTSIDE the Git repository (one time only).
  build  Build a release AAB, manually sign it with the upload key, and verify it.
  verify Verify the signed bundle again and print its SHA-256 hash.

Never upload your .jks file to GitHub or share its password with anyone.
TXT
}

check_project() {
  [ -d "$PROJECT_DIR/app" ] || fail "App module not found at $PROJECT_DIR/app. Set VIDEONIC_PROJECT_DIR if needed."
  [ -f "$PROJECT_DIR/settings.gradle" ] || [ -f "$PROJECT_DIR/settings.gradle.kts" ] || fail "settings.gradle(.kts) is missing."
  local build_file=""
  if [ -f "$PROJECT_DIR/app/build.gradle.kts" ]; then build_file="$PROJECT_DIR/app/build.gradle.kts"
  elif [ -f "$PROJECT_DIR/app/build.gradle" ]; then build_file="$PROJECT_DIR/app/build.gradle"
  else fail "app/build.gradle(.kts) not found"; fi
  printf 'Project: %s\nModule build file: %s\n' "$PROJECT_DIR" "$build_file"
  require python3
  python3 - "$build_file" <<'PY'
import re, sys
path = sys.argv[1]
src = open(path, encoding='utf-8').read()
patterns = {
  'applicationId': r'\bapplicationId\s*(?:=|\()?\s*["\']([^"\']+)',
  'targetSdk': r'\btargetSdk(?:Version)?\s*(?:=|\()?\s*(\d+)\b',
  'compileSdk': r'\bcompileSdk(?:Version)?\s*(?:=|\()?\s*(\d+)\b',
  'versionCode': r'\bversionCode\s*(?:=|\()?\s*(\d+)\b',
  'versionName': r'\bversionName\s*(?:=|\()?\s*["\']([^"\']+)',
}
for label, pattern in patterns.items():
    found = re.findall(pattern, src)
    print(f'{label}: {", ".join(found) if found else "not a literal in module build file — check manually"}')
    if label == 'targetSdk' and found and int(found[-1]) < 36:
        print('STOP: Google Play requires targetSdk >= 36 for standard new Android apps as of Aug 31 2026.',file=sys.stderr)
        sys.exit(3)
PY
}

create_key() {
  require keytool
  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"
  if [ -f "$KEY_FILE" ]; then
    info "Existing upload key found; preserving it"
    printf 'Location: %s\n' "$KEY_FILE"
    printf 'Never replace it if you have already used it to upload your app.\n'
    return
  fi
  info "Create your Google Play upload key (once)"
  printf 'Keystore: %s\nAlias: %s\n' "$KEY_FILE" "$KEY_ALIAS"
  printf 'Enter a strong password when prompted; keep it safely for future updates.\n'
  keytool -genkeypair -v -storetype PKCS12 -keystore "$KEY_FILE" \
      -keyalg RSA -keysize 3072 -validity 10000 -alias "$KEY_ALIAS"
  chmod 600 "$KEY_FILE"
  printf '\nKEY CREATED. Back up this .jks file to secure offline storage before deleting Codespaces.\n'
}

is_signed() {
  local bundle="$1"
  unzip -Z1 "$bundle" | grep -Ei '^META-INF/[^/]+\.(RSA|DSA|EC)$' >/dev/null
}

verify_signed() {
  require jarsigner
  require unzip
  [ -s "$SIGNED_BUNDLE" ] || fail "Signed AAB not found: $SIGNED_BUNDLE"
  unzip -tqq "$SIGNED_BUNDLE" || fail "Signed AAB ZIP integrity check failed"
  is_signed "$SIGNED_BUNDLE" || fail "AAB has no JAR signature block"
  info 'Verify the JAR signature'
  local output
  if ! output="$(jarsigner -verify "$SIGNED_BUNDLE" 2>&1)"; then
    printf '%s\n' "$output"
    fail "jarsigner verification failed"
  fi
  printf '%s\n' "$output"
  printf '%s\n' "$output" | grep -q 'jar verified' || fail "jarsigner did not confirm 'jar verified'"
  info 'Google Play upload file'
  ls -lh "$SIGNED_BUNDLE"
  sha256sum "$SIGNED_BUNDLE"
  printf '\nThe upload key and password must be retained for future updates.\n'
}

build_release() {
  require gradle
  require unzip
  require jarsigner
  require keytool
  check_project
  [ -f "$KEY_FILE" ] || fail "Missing upload key. Run: bash videonic-google-play-release/RELEASE.sh key"
  info 'Build release AAB from the currently applied Videonic source'
  cd "$PROJECT_DIR"
  gradle :app:bundleRelease \
    --no-daemon --max-workers=1 --no-parallel --no-configuration-cache \
    '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC' \
    '-Pkotlin.compiler.execution.strategy=in-process'
  [ -s "$RAW_BUNDLE" ] || fail "Gradle finished but $RAW_BUNDLE does not exist"
  if is_signed "$RAW_BUNDLE"; then
    fail "Gradle already signed app-release.aab. Do not add a second signature. Review app/build.gradle(.kts) signingConfig and existing upload key before proceeding."
  fi
  info 'Sign release AAB with the Google Play upload key'
  [ ! -e "$SIGNED_BUNDLE" ] || fail "Destination already exists: $SIGNED_BUNDLE. Move the old signed bundle to a backup before rebuilding."
  jarsigner -keystore "$KEY_FILE" -storetype PKCS12 \
    -sigalg SHA256withRSA -digestalg SHA-256 \
    -signedjar "$SIGNED_BUNDLE" "$RAW_BUNDLE" "$KEY_ALIAS"
  verify_signed
}

case "${1:-}" in
  check) check_project ;;
  key) create_key ;;
  build) build_release ;;
  verify) verify_signed ;;
  *) usage; [[ -n "${1:-}" ]] && exit 2 || exit 0 ;;
esac
