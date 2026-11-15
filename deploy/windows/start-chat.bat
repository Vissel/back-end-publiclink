@echo off
REM ---------------------------------------------------------------------------
REM Run chat-app (port 9090) on the Windows QA machine.
REM Artifact: chat-app/target/chat-exec.jar
REM           built on the Mac with: mvn -Pqa -pl chat-app -am package -DskipTests
REM The qa profile is baked into the artifact at build time (@activeProfile@), so
REM no SPRING_PROFILES_ACTIVE is needed here; the shared variables (RabbitMQ,
REM service URLs) come from qa-env.bat next to this script.
REM ---------------------------------------------------------------------------
setlocal
call "%~dp0qa-env.bat"
java -jar "%~dp0chat-exec.jar"
