@echo off
setlocal

pushd "%~dp0"
if errorlevel 1 exit /b %errorlevel%

echo ============================================================
echo   Yozakura one-click obfuscation: Neko + JNIC + Native DLL
echo ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Obfuscate-Client.ps1" %*
set "EXIT_CODE=%errorlevel%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo Obfuscation failed with exit code %EXIT_CODE%.
  popd
  exit /b %EXIT_CODE%
)

echo.
echo Obfuscation completed successfully.
popd
exit /b 0
