@echo off
setlocal

if not defined VS_BUILD set "VS_BUILD=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"

if not exist "%VS_BUILD%\vcvars64.bat" (
  echo Visual Studio x64 environment not found: %VS_BUILD%\vcvars64.bat
  exit /b 1
)

if not exist "native\yozakura_injector_ui.cpp" (
  echo Missing native\yozakura_injector_ui.cpp
  exit /b 1
)

if not exist "build\libs" mkdir "build\libs"
if not exist "build\native" mkdir "build\native"
if not exist "build\native\injector-ui" mkdir "build\native\injector-ui"

call "%VS_BUILD%\vcvars64.bat" >nul
if errorlevel 1 exit /b %errorlevel%

echo Building YozakuraInjector.exe...
cl /nologo /EHsc /std:c++17 /O2 /GL /DUNICODE /D_UNICODE ^
  /I"third_party\imgui" ^
  /I"third_party\imgui\backends" ^
  /Fo"build\native\injector-ui\\" ^
  "native\yozakura_injector_ui.cpp" ^
  "third_party\imgui\imgui.cpp" ^
  "third_party\imgui\imgui_draw.cpp" ^
  "third_party\imgui\imgui_tables.cpp" ^
  "third_party\imgui\imgui_widgets.cpp" ^
  "third_party\imgui\backends\imgui_impl_win32.cpp" ^
  "third_party\imgui\backends\imgui_impl_dx11.cpp" ^
  /link /NOLOGO /LTCG /OPT:REF /OPT:ICF /SUBSYSTEM:WINDOWS /OUT:"build\libs\YozakuraInjector.exe" ^
  user32.lib gdi32.lib shlwapi.lib shell32.lib dwmapi.lib ole32.lib windowscodecs.lib d3d11.lib d3dcompiler.lib dxgi.lib

if errorlevel 1 exit /b %errorlevel%

copy /Y "native\assets\yozakura_logo.png" "build\libs\yozakura_logo.png" >nul
if errorlevel 1 exit /b %errorlevel%
copy /Y "native\assets\minecraft_grass_block.png" "build\libs\minecraft_grass_block.png" >nul
if errorlevel 1 exit /b %errorlevel%
copy /Y "native\assets\minecraft_furnace_block.png" "build\libs\minecraft_furnace_block.png" >nul
if errorlevel 1 exit /b %errorlevel%
copy /Y "native\assets\minecraft_cherry_block.png" "build\libs\minecraft_cherry_block.png" >nul
if errorlevel 1 exit /b %errorlevel%

echo Built:
echo   build\libs\YozakuraInjector.exe
exit /b 0
