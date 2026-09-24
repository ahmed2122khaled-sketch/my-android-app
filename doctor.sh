#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_VERSION="9.7.1"
SDK="$ROOT/.tools/android-sdk"
GRADLE_DIR="$ROOT/.tools/gradle-$GRADLE_VERSION"
fail=0
printf 'Project: %s\n' "$ROOT"
printf 'Java: %s\n' "$(java -version 2>&1 | head -1 || true)"
if command -v gradle >/dev/null 2>&1; then echo 'Gradle system: READY'; else echo 'Gradle system: MISSING'; fi
if [ -x "$GRADLE_DIR/bin/gradle" ]; then echo 'Bundled Gradle: READY'; else echo 'Bundled Gradle: MISSING'; fail=1; fi
if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then echo 'Android sdkmanager: READY'; else echo 'Android sdkmanager: MISSING'; fail=1; fi
if [ -x "$SDK/platform-tools/adb" ]; then echo 'Android platform-tools/adb: READY'; else echo 'Android platform-tools/adb: MISSING'; fail=1; fi
if [ -x "$SDK/build-tools/36.0.0/aapt2" ]; then echo 'Android build-tools 36.0.0: READY'; else echo 'Android build-tools 36.0.0: MISSING'; fail=1; fi
if [ -d "$SDK/platforms/android-36" ]; then echo 'Android platform android-36: READY'; else echo 'Android platform android-36: MISSING'; fail=1; fi
if [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then echo 'Gradle wrapper JAR: READY'; else echo 'Gradle wrapper JAR: MISSING'; fi
if [ "$fail" -ne 0 ]; then
  echo 'RESULT: TOOLCHAIN INCOMPLETE' >&2
  exit 2
fi
echo 'RESULT: TOOLCHAIN READY'
