@echo off
REM ---------------------------------------------------------------------------
REM Run apiGateway standalone (port 8080) on the Windows QA machine.
REM Artifact: apiGateway/target/apiGateway.war
REM           built on the Mac with: mvn -pl apiGateway -am package -DskipTests
REM
REM The WAR runs standalone (java -jar), NOT inside Tomcat. Spring Cloud
REM Gateway is a WebFlux app, and Spring Boot's WAR initializer only creates a
REM servlet application context - deployed to Tomcat, no reactive request
REM mapping loads and every URL answers 404. The executable WAR carries its
REM own server jars (WEB-INF/lib-provided), so java -jar boots it fully.
REM
REM --spring.webflux.base-path=/apiGateway keeps the /apiGateway URL prefix,
REM so SECURITY_SERVICE_URL on the Mac and the frontend base URL stay unchanged.
REM ---------------------------------------------------------------------------
setlocal
call "%~dp0qa-env.bat"

REM -- Preflight ---------------------------------------------------------------
REM The gateway routes /publiclink/** to PUBLICLINK_SERVICE_URL. If that host
REM does not resolve (a .local name without Bonjour or a hosts entry) or the
REM Mac app is down, EVERY /publiclink/** call fails while /api/v1/auth/** keeps
REM working - which reads like an auth bug but is a routing one. Report it here
REM at startup instead of leaving it to be discovered through 500s.
REM A warning only: the gateway itself is fine before the Mac app comes up.
powershell -NoProfile -Command "$u=[Uri]'%PUBLICLINK_SERVICE_URL%'; $ok=(Test-NetConnection -ComputerName $u.Host -Port $u.Port -WarningAction SilentlyContinue).TcpTestSucceeded; if($ok){Write-Host ('publiclink-app reachable: '+$u.Host+':'+$u.Port) -ForegroundColor Green}else{Write-Host ('WARNING: publiclink-app NOT reachable: '+$u.Host+':'+$u.Port) -ForegroundColor Yellow; Write-Host 'Every /publiclink/** route will fail until it is. Check the Mac app is running and that this machine resolves the name (ping it). For a .local name install Bonjour for Windows or add a hosts entry.' -ForegroundColor Yellow}"

java -jar "%~dp0apiGateway.war" --spring.webflux.base-path=/apiGateway
