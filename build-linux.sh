#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_VERSION="9.7.1"
GRADLE_DIR="$ROOT/.tools/gradle-$GRADLE_VERSION"
SDK="$ROOT/.tools/android-sdk"
fail(){ echo "BUILD BLOCKED: $*" >&2; exit 2; }
[ -x "$GRADLE_DIR/bin/gradle" ] || fail "Bundled Gradle $GRADLE_VERSION is missing; run tools/bootstrap-linux.sh."
[ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ] || fail "Android command-line tools are missing; run tools/bootstrap-linux.sh."
[ -x "$SDK/platform-tools/adb" ] || fail "Android platform-tools are missing; run tools/bootstrap-linux.sh."
[ -x "$SDK/build-tools/36.0.0/aapt2" ] || fail "Android Build Tools 36.0.0 are missing; run tools/bootstrap-linux.sh."
[ -d "$SDK/platforms/android-36" ] || fail "Android platform android-36 is missing; run tools/bootstrap-linux.sh."
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$GRADLE_DIR/bin:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"
cd "$ROOT"
"$GRADLE_DIR/bin/gradle" --no-daemon --stacktrace :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -s "$APK" ] || fail "Gradle reported success but APK was not produced at $APK."
sha256sum "$APK"
printf '%s\n' "APK=$APK" "BUILD RESULT: PASS"
