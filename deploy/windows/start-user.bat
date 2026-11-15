@echo off
REM ---------------------------------------------------------------------------
REM Run the user service (qa profile, port 8082) on the Windows QA machine.
REM Artifact: user/target/user-0.0.1-SNAPSHOT.jar
REM           built on the Mac with: mvn -pl user -am package -DskipTests
REM The qa profile itself is activated by SPRING_PROFILES_ACTIVE below; the
REM shared variables come from qa-env.bat next to this script.
REM ---------------------------------------------------------------------------
setlocal
call "%~dp0qa-env.bat"
set SPRING_PROFILES_ACTIVE=qa
java -jar "%~dp0user-0.0.1-SNAPSHOT.jar"
