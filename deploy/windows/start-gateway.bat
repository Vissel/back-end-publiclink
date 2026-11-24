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
if not exist "%~dp0qa-env.bat" (
    echo ERROR: qa-env.bat was not found next to this script.
    echo Copy qa-env.bat.example to qa-env.bat and fill it in first.
    pause
    exit /b 1
)
call "%~dp0qa-env.bat"

REM -- Preflight ---------------------------------------------------------------
REM The gateway routes /publiclink/** to PUBLICLINK_SERVICE_URL. Reactor Netty
REM does its OWN DNS lookup and does not use mDNS/Bonjour: ping can succeed while
REM the gateway still gets NXDOMAIN. Check the hosts file, which Netty does read,
REM then test the TCP port. A warning only: the gateway itself can boot before
REM the Mac app comes up.
powershell -NoProfile -Command "$u=[Uri]'%PUBLICLINK_SERVICE_URL%'; $h=$u.Host; $hosts=$env:SystemRoot+'\System32\drivers\etc\hosts'; $entry=$false; if(Test-Path $hosts){$entry=[bool](Select-String -Path $hosts -Pattern ('(?m)^\s*\d+\.\d+\.\d+\.\d+\s+'+[regex]::Escape($h)+'(\s|$)') -Quiet)}; if($h.EndsWith('.local') -and -not $entry){Write-Host ('WARNING: Reactor Netty does not use mDNS. Ping may work but '+$h+' will still fail with NXDOMAIN. Add an entry to '+$hosts) -ForegroundColor Red; Write-Host ('Example: 192.168.1.13   '+$h+' - edit as Administrator, run ipconfig /flushdns, then restart.') -ForegroundColor Yellow}else{$r=Test-NetConnection -ComputerName $h -Port $u.Port -WarningAction SilentlyContinue; if($r.TcpTestSucceeded){Write-Host ('publiclink-app reachable: '+$h+':'+$u.Port) -ForegroundColor Green}else{Write-Host ('WARNING: publiclink-app NOT reachable: '+$h+':'+$u.Port+' - check the hosts mapping and that the Mac app is running.') -ForegroundColor Yellow}}"

java -jar "%~dp0apiGateway.war" --spring.webflux.base-path=/apiGateway
