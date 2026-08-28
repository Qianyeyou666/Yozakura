@echo off
setlocal EnableExtensions DisableDelayedExpansion

pushd "%~dp0" >nul
if errorlevel 1 (
  echo [ERROR] Cannot open the project directory.
  exit /b 1
)

rem Some host environments inject a value large enough to exceed the Windows child-process limit.
set "ACC_PRODUCT_CONFIG_V3="

if not "%~2"=="" (
  echo [ERROR] Unexpected argument: %~2
  goto :usage_error
)

if /I "%~1"=="help" goto :usage
if /I "%~1"=="--help" goto :usage
if /I "%~1"=="-h" goto :usage
if /I "%~1"=="preflight" goto :preflight
if /I "%~1"=="jar-only" goto :jar_only
if /I "%~1"=="no-themida" goto :no_themida
if not "%~1"=="" (
  echo [ERROR] Unknown mode: %~1
  goto :usage_error
)

echo ============================================================
echo   Yozakura one-click release obfuscation
echo   Fresh JAR -^> Neko rename ^(CFF off^) -^> Eskid hardening -^> JNIC -^> x64 DLL -^> Themida -^> Verify
echo ============================================================
echo.
echo [INFO] This command rebuilds all protected release artifacts.
call :run_pipeline
if errorlevel 1 goto :fail

echo.
echo [OK] Obfuscation and native payload verification completed.
call :print_artifacts
popd >nul
exit /b 0

:preflight
echo [INFO] Checking JDK, Neko CLI, Eskid, self-owned JNIC, Themida x64 and project configuration...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Obfuscate-Client.ps1" -PreflightOnly
if errorlevel 1 goto :fail
popd >nul
exit /b 0

:jar_only
echo [INFO] Building the protected JAR without rebuilding native DLLs...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Obfuscate-Client.ps1" -SkipNative
if errorlevel 1 goto :fail
call :print_artifacts
popd >nul
exit /b 0

:no_themida
echo [INFO] Building the Neko + Eskid + JNIC profile without Themida for isolation...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Obfuscate-Client.ps1" -NoThemida
if errorlevel 1 goto :fail
call :print_file "build\libs\Yozakura-1.5.0-neko-eskid-jnic-no-themida.jar"
call :print_file "build\libs\YozakuraLoader-x64.dll"
popd >nul
exit /b 0

:run_pipeline
set "YOZAKURA_THEMIDA_MARKERS=1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Obfuscate-Client.ps1"
set "PIPELINE_EXIT=%errorlevel%"
exit /b %PIPELINE_EXIT%

:print_artifacts
call :print_file "build\libs\Yozakura-1.5.0-neko-eskid-jnic.jar"
call :print_file "build\libs\YozakuraLoader-x64.dll"
exit /b 0

:print_file
if not exist "%~1" exit /b 0
for %%I in ("%~1") do echo [OK] %%~fI ^(%%~zI bytes^)
powershell -NoProfile -Command "$h=(Get-FileHash -Algorithm SHA256 -LiteralPath '%CD%\%~1').Hash.ToLowerInvariant(); Write-Host ('     SHA-256: ' + $h)"
exit /b %errorlevel%

:usage
echo Usage:
echo   obfuscate-client.bat             Full build: Neko + Eskid + JNIC + x64 DLL + Themida
echo   obfuscate-client.bat jar-only    Build only the Neko + Eskid + JNIC protected JAR
echo   obfuscate-client.bat no-themida  Build Neko + Eskid + JNIC and x64 DLL without Themida
echo   obfuscate-client.bat preflight   Check JDK, obfuscators, self-owned JNIC and Themida project
echo   obfuscate-client.bat help        Show this help
popd >nul
exit /b 0

:usage_error
echo.
echo Run "obfuscate-client.bat help" for supported modes.
popd >nul
exit /b 2

:fail
echo.
echo [ERROR] Yozakura obfuscation failed. No unprotected fallback was packaged.
popd >nul
exit /b 1
