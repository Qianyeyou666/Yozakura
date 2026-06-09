@echo off
setlocal

set "JAR=%~1"
set "ENTRY=%~2"
set "NAME=%~3"
set "ARCH=%~4"

if "%JAR%"=="" set "JAR=build\libs\VapuLite.jar"
if "%ENTRY%"=="" set "ENTRY=gq.vapulite.Vapu.Client"
if "%NAME%"=="" set "NAME=VapuLiteLoader"
if "%ARCH%"=="" set "ARCH=x64"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\JarToDll.ps1" -Jar "%JAR%" -EntryClass "%ENTRY%" -Name "%NAME%" -Arch "%ARCH%"
exit /b %errorlevel%
