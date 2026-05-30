#!/usr/bin/env sh
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME=`dirname "$0"`
APP_HOME=`cd "$APP_HOME" && pwd`

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVA_OPTS=""
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

exec java $DEFAULT_JVM_OPTS $JAVA_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
