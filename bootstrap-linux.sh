#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/.tools"
SDK="$TOOLS/android-sdk"
GRADLE_VERSION="9.7.1"
GRADLE_DIR="$TOOLS/gradle-$GRADLE_VERSION"
mkdir -p "$TOOLS"

# Fetch the official Gradle Wrapper JAR when network access is available.
# The checksum is pinned to Gradle 9.7.1 and is verified before installation.
if [ ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  "$ROOT/tools/fetch-gradle-wrapper.sh" || echo "Wrapper JAR download unavailable; continuing with bundled Gradle bootstrap." >&2
fi

command -v java >/dev/null || { echo "Java is required." >&2; exit 1; }
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[.\"]' '/version/ {print ($2=="1"?$3:$2); exit}')"
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then echo "JDK 17+ is required; found $JAVA_MAJOR." >&2; exit 1; fi

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  tmp="$TOOLS/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -fL --retry 3 -o "$tmp" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$GRADLE_DIR"
  unzip -q "$tmp" -d "$TOOLS"
fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$TOOLS/commandlinetools-linux.zip"
  echo "Downloading Android Command-line Tools..."
  curl -fL --retry 3 -o "$tmp" "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/tmp"
  mkdir -p "$SDK/cmdline-tools/tmp"
  unzip -q "$tmp" -d "$SDK/cmdline-tools/tmp"
  mkdir -p "$SDK/cmdline-tools/latest"
  cp -a "$SDK/cmdline-tools/tmp/cmdline-tools/." "$SDK/cmdline-tools/latest/"
  rm -rf "$SDK/cmdline-tools/tmp"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$GRADLE_DIR/bin:$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"

printf '%s\n' "Bootstrap complete." "ANDROID_HOME=$ANDROID_HOME" "GRADLE=$GRADLE_DIR/bin/gradle"
