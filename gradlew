#!/bin/sh
# Minimal gradlew — descarga el wrapper jar si no existe
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
    GRADLE_VERSION=$(grep distributionUrl "$DIR/gradle/wrapper/gradle-wrapper.properties" | sed 's/.*gradle-\(.*\)-bin.zip/\1/')
    mkdir -p "$(dirname "$JAR")"
    echo "Descargando gradle-wrapper.jar para Gradle $GRADLE_VERSION..."
    curl -fL "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar" \
         -o "$JAR" 2>/dev/null || \
    curl -fL "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" \
         -o "$JAR" 2>/dev/null
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
