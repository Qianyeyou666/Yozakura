@echo off
setlocal

if not defined VS_BUILD set "VS_BUILD=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"

if not exist "%VS_BUILD%\vcvars64.bat" (
  echo Visual Studio x64 environment not found: %VS_BUILD%\vcvars64.bat
  exit /b 1
)

if not exist "native\vapulite_injector_ui.cpp" (
  echo Missing native\vapulite_injector_ui.cpp
  exit /b 1
)

if not exist "build\libs" mkdir "build\libs"
if not exist "build\native" mkdir "build\native"

call "%VS_BUILD%\vcvars64.bat" >nul
if errorlevel 1 exit /b %errorlevel%

echo Building VapuLiteInjector.exe...
cl /nologo /EHsc /O2 /DUNICODE /D_UNICODE ^
  /Fo"build\native\injector_ui_" ^
  "native\vapulite_injector_ui.cpp" ^
  /link /NOLOGO /SUBSYSTEM:WINDOWS /OUT:"build\libs\VapuLiteInjector.exe" ^
  user32.lib gdi32.lib shlwapi.lib shell32.lib

if errorlevel 1 exit /b %errorlevel%

echo Built:
echo   build\libs\VapuLiteInjector.exe
exit /b 0
