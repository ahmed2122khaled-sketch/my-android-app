#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
  if ! command -v java >/dev/null 2>&1; then
    echo "Java is required to run Gradle Wrapper." >&2
    exit 1
  fi
  exec java -jar "$JAR" "$@"
fi
LOCAL_GRADLE="$APP_HOME/.tools/gradle-9.7.1/bin/gradle"
if [ -x "$LOCAL_GRADLE" ]; then exec "$LOCAL_GRADLE" --project-dir "$APP_HOME" "$@"; fi
if command -v gradle >/dev/null 2>&1; then exec gradle --project-dir "$APP_HOME" "$@"; fi
echo "Gradle Wrapper JAR is missing. Run tools/fetch-gradle-wrapper.sh or tools/bootstrap-linux.sh first." >&2
exit 127
