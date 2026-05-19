#!/bin/sh
set -e
GRADLE_WRAPPER_DIR="$HOME/.gradle/wrapper/dists/gradle-8.4-bin"
if [ ! -d "$GRADLE_WRAPPER_DIR" ]; then
  mkdir -p "$GRADLE_WRAPPER_DIR"
  curl -L https://services.gradle.org/distributions/gradle-8.4-bin.zip -o /tmp/gradle-8.4-bin.zip
  unzip -q /tmp/gradle-8.4-bin.zip -d "$GRADLE_WRAPPER_DIR/"
fi
exec "$GRADLE_WRAPPER_DIR/gradle-8.4/bin/gradle" "$@"
