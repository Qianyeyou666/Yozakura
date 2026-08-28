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
if errorlevel 1 exit /b 1

echo Building YozakuraInjector.exe...
cl /nologo /EHsc /std:c++17 /O2 /GL /DUNICODE /D_UNICODE ^
  /I"third_party\imgui" ^
  /I"third_party\imgui\backends" ^
  /Fo"build\native\injector-ui\\" ^
  "native\injector_core.cpp" ^
  "native\yozakura_injector_ui_state.cpp" ^
  "native\yozakura_injector_ui_design.cpp" ^
  "native\yozakura_injector_ui_views.cpp" ^
  "native\yozakura_injector_ui_entry.cpp" ^
  "native\yozakura_injector_ui.cpp" ^
  "third_party\imgui\imgui.cpp" ^
  "third_party\imgui\imgui_draw.cpp" ^
  "third_party\imgui\imgui_tables.cpp" ^
  "third_party\imgui\imgui_widgets.cpp" ^
  "third_party\imgui\backends\imgui_impl_win32.cpp" ^
  "third_party\imgui\backends\imgui_impl_dx11.cpp" ^
  /link /NOLOGO /LTCG /OPT:REF /OPT:ICF /SUBSYSTEM:WINDOWS /OUT:"build\libs\YozakuraInjector.exe" ^
  user32.lib gdi32.lib shlwapi.lib shell32.lib dwmapi.lib d3d11.lib dxgi.lib

if errorlevel 1 exit /b 1

echo Building YozakuraInjectorCli.exe...
cl /nologo /EHsc /std:c++17 /O2 /GL /DUNICODE /D_UNICODE ^
  /Fo"build\native\injector-ui\\" ^
  "native\injector.cpp" ^
  "native\injector_core.cpp" ^
  /link /NOLOGO /LTCG /OPT:REF /OPT:ICF /SUBSYSTEM:CONSOLE /OUT:"build\native\injector-ui\YozakuraInjectorCli.exe" user32.lib
if errorlevel 1 exit /b 1

echo Building injector core tests...
cl /nologo /EHsc /std:c++17 /O2 /I"native" ^
  /Fo"build\native\injector-ui\\" ^
  "native\tests\injector_core_test.cpp" ^
  "native\injector_core.cpp" ^
  /link /NOLOGO /OUT:"build\native\injector-ui\injector_core_test.exe" user32.lib
if errorlevel 1 exit /b 1
"build\native\injector-ui\injector_core_test.exe"
if errorlevel 1 exit /b 1

echo Building injector UI state tests...
cl /nologo /EHsc /std:c++17 /O2 /I"native" ^
  /Fo"build\native\injector-ui\\" ^
  "native\tests\injector_ui_state_test.cpp" ^
  "native\yozakura_injector_ui_state.cpp" ^
  /link /NOLOGO /OUT:"build\native\injector-ui\injector_ui_state_test.exe"
if errorlevel 1 exit /b 1
"build\native\injector-ui\injector_ui_state_test.exe"
if errorlevel 1 exit /b 1

echo Building injector UI design tests...
cl /nologo /EHsc /std:c++17 /O2 /I"native" ^
  /Fo"build\native\injector-ui\\" ^
  "native\tests\injector_ui_design_test.cpp" ^
  "native\yozakura_injector_ui_design.cpp" ^
  "native\yozakura_injector_ui_state.cpp" ^
  /link /NOLOGO /OUT:"build\native\injector-ui\injector_ui_design_test.exe"
if errorlevel 1 exit /b 1
"build\native\injector-ui\injector_ui_design_test.exe"
if errorlevel 1 exit /b 1

copy /Y "native\assets\JetBrainsMono.ttf" "build\libs\JetBrainsMono.ttf" >nul
if errorlevel 1 exit /b 1
copy /Y "native\assets\LICENSE-JetBrainsMono.txt" "build\libs\LICENSE-JetBrainsMono.txt" >nul
if errorlevel 1 exit /b 1

echo Built:
echo   build\libs\YozakuraInjector.exe
exit /b 0
