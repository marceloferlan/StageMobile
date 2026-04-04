#!/bin/bash
# Script de build para StageMobile (Contorna erros de permissão e JDK)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
echo "Usando JAVA_HOME: $JAVA_HOME"
echo "Diretório de cache do Gradle: ./.gradle-home"
chmod +x ./gradlew
./gradlew -g ./.gradle-home "$@"
