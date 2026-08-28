@echo off
setlocal EnableExtensions DisableDelayedExpansion

pushd "%~dp0" >nul
if errorlevel 1 (
  echo [ERROR] Cannot open the project directory.
  exit /b 1
)

rem Some host environments inject a value large enough to exceed the Windows child-process limit.
set "ACC_PRODUCT_CONFIG_V3="

set "DLL=build\libs\YozakuraLoader-x64.dll"
set "JAR=build\libs\Yozakura.jar"

echo [INFO] Building the runtime JAR and native loader DLLs...
call "build-native.bat"
if errorlevel 1 goto :build_failed

if not exist "%DLL%" (
  echo [ERROR] Missing "%CD%\%DLL%" after build.
  goto :fail
)
if not exist "%JAR%" (
  echo [ERROR] Missing "%CD%\%JAR%" after build.
  goto :fail
)

echo [INFO] Rechecking the x64 native payload...
powershell -NoProfile -ExecutionPolicy Bypass -File "tools\Verify-NativePayload.ps1" -Dll "%DLL%" -Jar "%JAR%"
if errorlevel 1 goto :fail

for %%I in ("%DLL%") do echo [OK] DLL: "%%~fI" ^(%%~zI bytes^)
set "DLL_SHA256="
for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%CD%\%DLL%').Hash.ToLowerInvariant()"`) do if not defined DLL_SHA256 set "DLL_SHA256=%%H"
if not defined DLL_SHA256 (
  echo [ERROR] Unable to calculate the DLL SHA-256 digest.
  goto :fail
)
echo [OK] SHA-256: %DLL_SHA256%

popd >nul
exit /b 0

:build_failed
echo [ERROR] Native DLL build failed. Close any process using the old loader DLL and retry.
:fail
popd >nul
exit /b 1
