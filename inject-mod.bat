@echo off
setlocal

set "DLL=%~1"
set "PID=%~2"
set "TARGET=%~3"
set "INJECTOR=%~dp0InjectMod.ps1"
if not exist "%INJECTOR%" set "INJECTOR=%~dp0tools\InjectMod.ps1"

if "%DLL%"=="" (
  set "DLL=%~dp0YozakuraLoader.dll"
  if not exist "%~dp0YozakuraLoader.dll" if exist "%~dp0YozakuraLoader-x64.dll" set "DLL=%~dp0YozakuraLoader-x64.dll"
)
if "%TARGET%"=="" set "TARGET=Auto"

if not exist "%DLL%" (
  echo DLL not found: "%DLL%"
  exit /b 1
)

if not exist "%INJECTOR%" (
  echo Injector script not found: "%INJECTOR%"
  exit /b 1
)

if "%PID%"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%INJECTOR%" -Dll "%DLL%" -Target "%TARGET%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%INJECTOR%" -Dll "%DLL%" -ProcessId %PID% -Target "%TARGET%"
)

exit /b %errorlevel%
