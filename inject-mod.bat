@echo off
setlocal

set "DLL=%~1"
set "PID=%~2"
set "TARGET=%~3"

if "%DLL%"=="" set "DLL=%~dp0build\libs\VapuLiteLoader-x64.dll"
if "%TARGET%"=="" set "TARGET=Auto"

if "%PID%"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\InjectMod.ps1" -Dll "%DLL%" -Target "%TARGET%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\InjectMod.ps1" -Dll "%DLL%" -ProcessId %PID% -Target "%TARGET%"
)

exit /b %errorlevel%
