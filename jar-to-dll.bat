@echo off
setlocal

set "JAR=%~1"
set "ENTRY=%~2"
set "NAME=%~3"
set "ARCH=%~4"

if "%JAR%"=="" set "JAR=build\libs\Yozakura.jar"
if "%ENTRY%"=="" set "ENTRY=gq.yozakura.YozakuraBootstrap"
if "%NAME%"=="" set "NAME=YozakuraLoader"
if "%ARCH%"=="" set "ARCH=x64"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\JarToDll.ps1" -Jar "%JAR%" -EntryClass "%ENTRY%" -Name "%NAME%" -Arch "%ARCH%"
exit /b %errorlevel%
