@echo off
setlocal EnableExtensions DisableDelayedExpansion

pushd "%~dp0" >nul
if errorlevel 1 (
  echo [ERROR] Cannot open the project directory.
  exit /b 1
)

set "ACC_PRODUCT_CONFIG_V3="
set "PACKAGE_NAME=YozakuraInjector-Package"
set "PACKAGE_DIR=outputs\%PACKAGE_NAME%"
set "PACKAGE_ZIP=outputs\%PACKAGE_NAME%.zip"

echo [1/5] Building the loader DLL...
call "build-dll-release.bat"
if errorlevel 1 goto :fail

echo [2/5] Building the graphical injector...
call "build-injector-ui.bat"
if errorlevel 1 goto :fail

call :require_file "build\libs\YozakuraLoader-x64.dll" || goto :fail
call :require_file "build\libs\YozakuraInjector.exe" || goto :fail
call :require_file "build\libs\JetBrainsMono.ttf" || goto :fail
call :require_file "build\libs\LICENSE-JetBrainsMono.txt" || goto :fail

powershell -NoProfile -Command "if (-not (Select-String -Quiet -SimpleMatch 'YozakuraLoader-x64.dll' 'native\yozakura_injector_ui.cpp')) { exit 1 }"
if errorlevel 1 (
  echo [ERROR] Injector no longer searches for YozakuraLoader-x64.dll beside its executable.
  goto :fail
)

echo [3/5] Creating a clean same-directory release package...
if exist "%PACKAGE_DIR%" rmdir /S /Q "%PACKAGE_DIR%"
if errorlevel 1 goto :fail
if not exist "outputs" mkdir "outputs"
if errorlevel 1 goto :fail
mkdir "%PACKAGE_DIR%"
if errorlevel 1 goto :fail
copy /Y "build\libs\YozakuraLoader-x64.dll" "%PACKAGE_DIR%\YozakuraLoader-x64.dll" >nul || goto :fail
copy /Y "build\libs\YozakuraInjector.exe" "%PACKAGE_DIR%\YozakuraInjector.exe" >nul || goto :fail
copy /Y "build\libs\JetBrainsMono.ttf" "%PACKAGE_DIR%\JetBrainsMono.ttf" >nul || goto :fail
copy /Y "build\libs\LICENSE-JetBrainsMono.txt" "%PACKAGE_DIR%\LICENSE-JetBrainsMono.txt" >nul || goto :fail
if exist "build\native\injector-ui\YozakuraInjectorCli.exe" copy /Y "build\native\injector-ui\YozakuraInjectorCli.exe" "%PACKAGE_DIR%\YozakuraInjectorCli.exe" >nul

call :require_file "%PACKAGE_DIR%\YozakuraLoader-x64.dll" || goto :fail
call :require_file "%PACKAGE_DIR%\YozakuraInjector.exe" || goto :fail

powershell -NoProfile -Command "$p='%CD%\%PACKAGE_DIR%\YozakuraLoader-x64.dll'; $b=[IO.File]::ReadAllBytes($p); $o=[BitConverter]::ToInt32($b,60); $m=[BitConverter]::ToUInt16($b,$o+4); if($m -ne 0x8664){Write-Error ('Expected AMD64 PE machine 0x8664, got 0x{0:X4}' -f $m); exit 1}"
if errorlevel 1 goto :fail

echo [4/5] Writing SHA-256 manifest...
powershell -NoProfile -Command "$d='%CD%\%PACKAGE_DIR%'; Get-ChildItem -LiteralPath $d -File | Where-Object Name -ne 'SHA256SUMS.txt' | Sort-Object Name | ForEach-Object { '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant(), $_.Name } | Set-Content -Encoding ASCII -LiteralPath (Join-Path $d 'SHA256SUMS.txt')"
if errorlevel 1 goto :fail

echo [5/5] Creating ZIP archive...
if exist "%PACKAGE_ZIP%" del /Q "%PACKAGE_ZIP%"
if errorlevel 1 goto :fail
powershell -NoProfile -Command "Compress-Archive -Path '%CD%\%PACKAGE_DIR%\*' -DestinationPath '%CD%\%PACKAGE_ZIP%' -Force"
if errorlevel 1 goto :fail
if not exist "%PACKAGE_ZIP%" goto :fail

for %%I in ("%PACKAGE_DIR%\YozakuraInjector.exe") do echo [OK] Injector: "%%~fI" ^(%%~zI bytes^)
for %%I in ("%PACKAGE_DIR%\YozakuraLoader-x64.dll") do echo [OK] Loader:   "%%~fI" ^(%%~zI bytes^)
for %%I in ("%PACKAGE_ZIP%") do echo [OK] Package:  "%%~fI" ^(%%~zI bytes^)
echo [OK] The injector and YozakuraLoader-x64.dll are in the same directory.

popd >nul
exit /b 0

:require_file
if exist "%~1" exit /b 0
echo [ERROR] Missing "%CD%\%~1".
exit /b 1

:fail
echo [ERROR] Injector release packaging failed.
popd >nul
exit /b 1
