#!/bin/sh
# Gradle startup script for UN*X
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
APP_NAME="Gradle"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="java"
fi
exec "$JAVA" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
