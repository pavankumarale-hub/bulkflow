#!/bin/sh
# Maven Wrapper — resolves Maven 3.9.6 from the local wrapper cache or downloads it.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"

if [ -z "$JAVA_HOME" ] && [ -x "/usr/libexec/java_home" ]; then
  JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
  export JAVA_HOME
fi

DIST_URL=$(grep "^distributionUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
DIST_FILENAME=$(basename "$DIST_URL" .zip)

# Find an already-extracted Maven installation
MVN_HOME=$(find "$MAVEN_USER_HOME/wrapper/dists/$DIST_FILENAME" -maxdepth 4 \
  -name "mvn" -type f 2>/dev/null | head -1 | xargs -I{} sh -c 'dirname $(dirname {})')

if [ -z "$MVN_HOME" ]; then
  echo "Downloading Maven distribution: $DIST_URL ..."
  EXTRACT_DIR="$MAVEN_USER_HOME/wrapper/dists/$DIST_FILENAME/extracted"
  mkdir -p "$EXTRACT_DIR"
  curl -sL "$DIST_URL" -o "/tmp/${DIST_FILENAME}.zip"
  unzip -q "/tmp/${DIST_FILENAME}.zip" -d "$EXTRACT_DIR"
  rm -f "/tmp/${DIST_FILENAME}.zip"
  MVN_HOME=$(find "$EXTRACT_DIR" -maxdepth 3 -name "mvn" -type f | head -1 | \
    xargs -I{} sh -c 'dirname $(dirname {})')
fi

if [ -z "$MVN_HOME" ]; then
  echo "ERROR: Could not locate Maven. Install Maven 3.9+ and add it to PATH." >&2
  exit 1
fi

exec "$MVN_HOME/bin/mvn" "$@"
