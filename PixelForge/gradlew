#!/bin/sh
# PixelForge one-step Gradle launcher (self-bootstrapping).
# On first run it fetches the matching gradle-wrapper.jar, then delegates
# to Gradle exactly like the standard wrapper. Requires JDK 17+ and network.
set -e
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$JAR" ]; then
  echo "[PixelForge] Fetching gradle-wrapper.jar (first run only)..."
  mkdir -p "$APP_HOME/gradle/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$JAR"
  else
    echo "Need curl or wget to bootstrap the Gradle wrapper." >&2
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
