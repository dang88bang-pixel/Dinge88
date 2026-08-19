#!/bin/sh
#
# SecureGuard Enterprise - self-bootstrapping Gradle wrapper.
#
# This wrapper does NOT require a committed gradle-wrapper.jar. It reads the
# distributionUrl from gradle/wrapper/gradle-wrapper.properties, downloads the
# distribution on first use (cached under $HOME/.gradle), and then runs Gradle.
#
# Normally you would generate the official wrapper with:  gradle wrapper
# but this version keeps the repository fully self-contained and CI-friendly.
#
# SPDX-License-Identifier: Apache-2.0

set -e

# Resolve APP_HOME (directory of this script).
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# Locate a Java runtime.
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_EXE=java
else
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  echo "Please install a JDK (17) and set JAVA_HOME, or add java to your PATH." >&2
  exit 1
fi
"$JAVA_EXE" -version >/dev/null 2>&1 || {
  echo "ERROR: Java not runnable: $JAVA_EXE" >&2
  exit 1
}

WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "ERROR: Missing $WRAPPER_PROPS" >&2
  exit 1
fi

DIST_URL=`sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | tr -d '\r\n'`
if [ -z "$DIST_URL" ]; then
  echo "ERROR: distributionUrl not found in $WRAPPER_PROPS" >&2
  exit 1
fi

# Extract version like 8.5 from .../gradle-8.5-bin.zip
GRADLE_VERSION=`echo "$DIST_URL" | sed -E 's/.*gradle-([0-9][0-9.]*)-(bin|all)\.zip.*/\1/'`
DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION from $DIST_URL ..."
    if command -v curl >/dev/null 2>&1; then
      curl -fSL -o "$ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$ZIP" "$DIST_URL"
    else
      echo "ERROR: Neither curl nor wget is available." >&2
      exit 1
    fi
  fi
  echo "Extracting Gradle $GRADLE_VERSION ..."
  (cd "$DIST_DIR" && unzip -q -o "$ZIP")
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
