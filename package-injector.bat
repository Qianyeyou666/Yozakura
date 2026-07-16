@echo off
setlocal EnableExtensions DisableDelayedExpansion

pushd "%~dp0" >nul
if errorlevel 1 (
  echo [ERROR] Cannot open the project directory.
  exit /b 1
)

set "SOURCE_DIR=%CD%\build\libs"
set "BUILD_DIR=%CD%\build\native\standalone"
set "OUTPUT_EXE=%SOURCE_DIR%\YozakuraStandalone.exe"
set "TEMP_EXE=%BUILD_DIR%\YozakuraStandalone.exe"
set "RESOURCE_FILE=%BUILD_DIR%\payload.rc"
set "RESOURCE_OBJECT=%BUILD_DIR%\payload.res"
set "RC_SOURCE_DIR=%SOURCE_DIR:\=/%"

if not defined VS_BUILD set "VS_BUILD=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"
if not exist "%VS_BUILD%\vcvars64.bat" (
  echo [ERROR] Visual Studio x64 environment not found:
  echo         "%VS_BUILD%\vcvars64.bat"
  echo         Set VS_BUILD to the Visual Studio VC\Auxiliary\Build directory.
  goto :fail
)

if not exist "native\yozakura_standalone.cpp" (
  echo [ERROR] Missing "%CD%\native\yozakura_standalone.cpp".
  goto :fail
)
call :require_file "YozakuraLoader.dll" || goto :fail
call :require_file "YozakuraInjector.exe" || goto :fail
call :require_file "minecraft_cherry_block.png" || goto :fail
call :require_file "minecraft_furnace_block.png" || goto :fail
call :require_file "minecraft_grass_block.png" || goto :fail
call :require_file "yozakura_logo.png" || goto :fail

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if errorlevel 1 (
  echo [ERROR] Cannot create "%BUILD_DIR%".
  goto :fail
)

>"%RESOURCE_FILE%" echo 101 RCDATA "%RC_SOURCE_DIR%/YozakuraLoader.dll"
>>"%RESOURCE_FILE%" echo 102 RCDATA "%RC_SOURCE_DIR%/YozakuraInjector.exe"
>>"%RESOURCE_FILE%" echo 103 RCDATA "%RC_SOURCE_DIR%/minecraft_cherry_block.png"
>>"%RESOURCE_FILE%" echo 104 RCDATA "%RC_SOURCE_DIR%/minecraft_furnace_block.png"
>>"%RESOURCE_FILE%" echo 105 RCDATA "%RC_SOURCE_DIR%/minecraft_grass_block.png"
>>"%RESOURCE_FILE%" echo 106 RCDATA "%RC_SOURCE_DIR%/yozakura_logo.png"
if errorlevel 1 (
  echo [ERROR] Cannot create "%RESOURCE_FILE%".
  goto :fail
)

call "%VS_BUILD%\vcvars64.bat" >nul
if errorlevel 1 (
  echo [ERROR] Failed to initialize the Visual Studio x64 toolchain.
  goto :fail
)

echo [INFO] Embedding the loader, injector, and UI images...
rc.exe /nologo /fo "%RESOURCE_OBJECT%" "%RESOURCE_FILE%"
if errorlevel 1 (
  echo [ERROR] Resource compilation failed.
  goto :fail
)

cl.exe /nologo /EHsc /std:c++17 /utf-8 /O2 /GL /MT /DUNICODE /D_UNICODE ^
  /Fo"%BUILD_DIR%\yozakura_standalone.obj" "native\yozakura_standalone.cpp" "%RESOURCE_OBJECT%" ^
  /link /NOLOGO /LTCG /OPT:REF /OPT:ICF /SUBSYSTEM:WINDOWS ^
  /OUT:"%TEMP_EXE%" user32.lib shell32.lib
if errorlevel 1 (
  echo [ERROR] Standalone launcher compilation failed.
  goto :fail
)

copy /Y "%TEMP_EXE%" "%OUTPUT_EXE%" >nul
if errorlevel 1 (
  echo [ERROR] Cannot replace "%OUTPUT_EXE%". Close it and try again.
  goto :fail
)

for %%I in ("%OUTPUT_EXE%") do echo [OK] Created "%%~fI" ^(%%~zI bytes^).
popd >nul
exit /b 0

:require_file
if exist "%SOURCE_DIR%\%~1" exit /b 0
echo [ERROR] Missing "%SOURCE_DIR%\%~1".
exit /b 1

:fail
popd >nul
exit /b 1
