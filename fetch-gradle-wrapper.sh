#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="9.7.1"
URL="https://services.gradle.org/distributions/gradle-${VERSION}-wrapper.jar"
EXPECTED="7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d"
OUT="$ROOT/gradle/wrapper/gradle-wrapper.jar"
TMP="${OUT}.download"
mkdir -p "$(dirname "$OUT")"
rm -f "$TMP"
printf '%s\n' "Downloading official Gradle Wrapper JAR $VERSION..."
curl -fL --retry 3 --retry-delay 1 -o "$TMP" "$URL"
ACTUAL="$(sha256sum "$TMP" | awk '{print tolower($1)}')"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  rm -f "$TMP"
  echo "ERROR: Wrapper JAR SHA-256 mismatch." >&2
  echo "Expected: $EXPECTED" >&2
  echo "Actual:   $ACTUAL" >&2
  exit 1
fi
mv "$TMP" "$OUT"
printf '%s  %s\n' "$ACTUAL" "$OUT"
