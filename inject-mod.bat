@echo off
setlocal

set "DLL=%~1"
set "PID=%~2"

if "%DLL%"=="" set "DLL=%~dp0build\libs\VapuLiteReobf-x64.dll"

if "%PID%"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\InjectMod.ps1" -Dll "%DLL%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\InjectMod.ps1" -Dll "%DLL%" -ProcessId %PID%
)

exit /b %errorlevel%
