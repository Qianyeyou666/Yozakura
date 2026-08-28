@echo off
setlocal

pushd "%~dp0"
if errorlevel 1 exit /b 1

call :main %*
set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%

:main

if not defined JAVA8_HOME if defined JAVA_HOME call :select_java8_from "%JAVA_HOME%"
if not defined JAVA8_HOME for /d %%D in ("%USERPROFILE%\.jdks\corretto-1.8*") do if not defined JAVA8_HOME set "JAVA8_HOME=%%~fD"
if not defined JAVA8_HOME for /d %%D in ("%USERPROFILE%\.jdks\temurin-8*") do if not defined JAVA8_HOME set "JAVA8_HOME=%%~fD"
if not defined JAVA8_HOME for /d %%D in ("%USERPROFILE%\.jdks\jdk8*") do if not defined JAVA8_HOME set "JAVA8_HOME=%%~fD"
if not defined JAVA8_HOME (
  echo Java 8 was not found. Set JAVA8_HOME to a JDK 8 installation.
  exit /b 1
)
set "JAVA_HOME=%JAVA8_HOME%"
if not defined VS_BUILD set "VS_BUILD=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"
if not defined YOZAKURA_THEMIDA_SDK set "YOZAKURA_THEMIDA_SDK=D:\obf\Themida v3.1.8.0\ThemidaSDK"
if /I "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  if not exist "%YOZAKURA_THEMIDA_SDK%\Include\C\ThemidaSDK.h" (
    echo Themida SDK headers were not found: %YOZAKURA_THEMIDA_SDK%
    exit /b 1
  )
  if not exist "%YOZAKURA_THEMIDA_SDK%\Lib\COFF\SecureEngineSDK64.lib" (
    echo Themida x64 SDK library was not found: %YOZAKURA_THEMIDA_SDK%
    exit /b 1
  )
)

if defined YOZAKURA_RUNTIME_JAR (
  if not exist "%YOZAKURA_RUNTIME_JAR%" (
    echo YOZAKURA_RUNTIME_JAR does not exist: %YOZAKURA_RUNTIME_JAR%
    exit /b 1
  )
  if not exist "build\libs" mkdir "build\libs"
  if exist "build\libs\Yozakura.jar" del /q "build\libs\Yozakura.jar" 2>nul
  copy /Y "%YOZAKURA_RUNTIME_JAR%" "build\libs\Yozakura.jar" >nul
  if errorlevel 1 (
    echo Failed to copy protected runtime JAR into build\libs\Yozakura.jar.
    exit /b 1
  )
  powershell -NoProfile -Command "$src=(Get-FileHash -Algorithm SHA256 -LiteralPath '%YOZAKURA_RUNTIME_JAR%').Hash; $dst=(Get-FileHash -Algorithm SHA256 -LiteralPath 'build\libs\Yozakura.jar').Hash; if ($src -ne $dst) { Write-Host ('Source:  ' + $src); Write-Host ('Copied:  ' + $dst); exit 1 }"
  if errorlevel 1 (
    echo The copied Yozakura.jar does not match the source runtime JAR.
    exit /b 1
  )
  if defined YOZAKURA_OBFUSCATION_INPUT_JAR if defined YOZAKURA_INTERMEDIATE_JAR if defined YOZAKURA_ESKID_INTERMEDIATE_JAR if defined YOZAKURA_ESKID_LOG (
    echo Verifying protected runtime JAR...
    powershell -ExecutionPolicy Bypass -File "tools\Verify-ObfuscatedJar.ps1" ^
      -InputJar "%YOZAKURA_OBFUSCATION_INPUT_JAR%" ^
      -NekoJar "%YOZAKURA_INTERMEDIATE_JAR%" ^
      -EskidJar "%YOZAKURA_ESKID_INTERMEDIATE_JAR%" ^
      -Jar "build\libs\Yozakura.jar" ^
      -JavaHome "%JAVA8_HOME%" ^
      -NekoMapping "%YOZAKURA_NEKO_MAPPING%" ^
      -EskidLog "%YOZAKURA_ESKID_LOG%"
    if errorlevel 1 exit /b 1
  ) else (
    echo Verifying runtime JAR ZIP integrity...
    "%JAVA8_HOME%\bin\jar.exe" tf "build\libs\Yozakura.jar" >nul
    if errorlevel 1 exit /b 1
  )
) else (
  echo Refreshing build\libs\Yozakura.jar...
  call "%~dp0gradlew.bat" syncRuntimeJar
  if errorlevel 1 exit /b 1
)

if not exist "%JAVA8_HOME%\include\jni.h" (
  echo JDK 8 JNI headers not found: %JAVA8_HOME%
  exit /b 1
)

if not exist "build\libs\Yozakura.jar" (
  echo Missing build\libs\Yozakura.jar. Run gradlew jar first.
  exit /b 1
)

if not exist "build\native" mkdir "build\native"
if not exist "build\libs" mkdir "build\libs"

if /I "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  call :prepare_webview2 x64 "%VS_BUILD%\vcvars64.bat"
  if errorlevel 1 exit /b 1
  call :synchronize_final_runtime_jar
  if errorlevel 1 exit /b 1
  call :build_one x64 "%VS_BUILD%\vcvars64.bat" "build\libs\YozakuraLoader-x64-themida-input.dll"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\Verify-NativePayload.ps1" -Dll "build\libs\YozakuraLoader-x64-themida-input.dll" -Jar "build\libs\Yozakura.jar"
  if errorlevel 1 exit /b 1
  echo Built Themida input:
  echo   build\libs\YozakuraLoader-x64-themida-input.dll
  exit /b 0
)

call :prepare_webview2 x64 "%VS_BUILD%\vcvars64.bat"
if errorlevel 1 exit /b 1
call :synchronize_final_runtime_jar
if errorlevel 1 exit /b 1

call :build_one x64 "%VS_BUILD%\vcvars64.bat" "build\libs\YozakuraLoader-x64.dll"
if errorlevel 1 exit /b 1
powerShell -NoProfile -ExecutionPolicy Bypass -File "tools\Verify-NativePayload.ps1" -Dll "build\libs\YozakuraLoader-x64.dll" -Jar "build\libs\Yozakura.jar"
if errorlevel 1 exit /b 1

copy /Y "build\libs\YozakuraLoader-x64.dll" "build\libs\YozakuraLoader.dll" >nul
if errorlevel 1 (
  echo Failed to refresh build\libs\YozakuraLoader.dll. Close any process using the old loader and rebuild.
  exit /b 1
)

echo Built:
echo   build\libs\YozakuraLoader.dll
echo   build\libs\YozakuraLoader-x64.dll
exit /b 0

:build_one
set "ARCH=%~1"
set "VCVARS=%~2"
set "OUTDLL=%~3"

del /q "build\native\%ARCH%.pdb" 2>nul

if not exist "%VCVARS%" (
  echo Visual Studio environment not found: %VCVARS%
  exit /b 1
)

echo Building %ARCH% DLL...
call "%VCVARS%" >nul
if errorlevel 1 exit /b 1

if /I "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  cl /nologo /EHsc /c /O2 ^
    /DYOZAKURA_THEMIDA_MARKERS=1 ^
    /I"%YOZAKURA_THEMIDA_SDK%\Include\C" ^
    /Fo"build\native\%ARCH%_themida_guard.obj" ^
    "native\yozakura_themida_guard.cpp"
) else (
  cl /nologo /EHsc /c /O2 ^
    /Fo"build\native\%ARCH%_themida_guard.obj" ^
    "native\yozakura_themida_guard.cpp"
)
if errorlevel 1 exit /b 1

if /I "%ARCH%"=="x64" if /I not "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  cl /nologo /EHsc /O2 ^
    "native\tests\yozakura_themida_guard_test.cpp" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    /Fe"build\native\yozakura_themida_guard_test.exe"
  if errorlevel 1 exit /b 1
  "build\native\yozakura_themida_guard_test.exe"
  if errorlevel 1 exit /b 1
)

cl /nologo /EHsc /c /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /Fo"build\native\%ARCH%_loader.obj" ^
  "native\yozakura_loader.cpp"
if errorlevel 1 exit /b 1

rc /nologo /fo "build\native\yozakura_loader_%ARCH%.res" "native\yozakura_loader.rc"
if errorlevel 1 exit /b 1

cl /nologo /EHsc /c /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /Fo"build\native\%ARCH%_auth.obj" ^
  "native\yozakura_native_auth.cpp"
if errorlevel 1 exit /b 1

if /I "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF /INCREMENTAL:NO /RELEASE ^
    "build\native\%ARCH%_loader.obj" ^
    "build\native\%ARCH%_auth.obj" ^
    "build\native\%ARCH%_webview2.obj" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    "build\native\yozakura_loader_%ARCH%.res" ^
    "%YOZAKURA_THEMIDA_SDK%\Lib\COFF\SecureEngineSDK64.lib" ^
    winhttp.lib ^
    crypt32.lib ^
    advapi32.lib ^
    ncrypt.lib ^
    bcrypt.lib ^
    ole32.lib user32.lib gdi32.lib version.lib ^
    "third_party\webview2\build\native\%ARCH%\WebView2LoaderStatic.lib" ^
    /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib"
) else (
  link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF /INCREMENTAL:NO /RELEASE ^
    "build\native\%ARCH%_loader.obj" ^
    "build\native\%ARCH%_auth.obj" ^
    "build\native\%ARCH%_webview2.obj" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    "build\native\yozakura_loader_%ARCH%.res" ^
    winhttp.lib ^
    crypt32.lib ^
    advapi32.lib ^
    ncrypt.lib ^
    bcrypt.lib ^
    ole32.lib user32.lib gdi32.lib version.lib ^
    "third_party\webview2\build\native\%ARCH%\WebView2LoaderStatic.lib" ^
    /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib"
)

exit /b %errorlevel%

:prepare_webview2
set "ARCH=%~1"
set "VCVARS=%~2"

if not exist "%VCVARS%" (
  echo Visual Studio environment not found: %VCVARS%
  exit /b 1
)

call "%VCVARS%" >nul
if errorlevel 1 exit /b 1

cl /nologo /EHsc /c /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /I"third_party\webview2\build\native\include" ^
  /Fo"build\native\%ARCH%_webview2.obj" ^
  "native\yozakura_webview2.cpp"
if errorlevel 1 exit /b 1

link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF /INCREMENTAL:NO /RELEASE ^
  "build\native\%ARCH%_webview2.obj" ^
  ole32.lib user32.lib gdi32.lib version.lib ^
  "third_party\webview2\build\native\%ARCH%\WebView2Loader.dll.lib" ^
  /OUT:"build\native\YozakuraWebView2Bridge-%ARCH%-package.dll" ^
  /IMPLIB:"build\native\%ARCH%_webview2_bridge.lib"
if errorlevel 1 exit /b 1

copy /Y "third_party\webview2\build\native\%ARCH%\WebView2Loader.dll" "build\libs\WebView2Loader-%ARCH%.dll" >nul
if errorlevel 1 exit /b 1

call :package_webview2 "%ARCH%"
exit /b %errorlevel%

:package_webview2
set "PACKAGE_ARCH=%~1"
set "PACKAGE_DIR=build\native\jar-native\assets\yozakura\native\%PACKAGE_ARCH%"
if not exist "%PACKAGE_DIR%" mkdir "%PACKAGE_DIR%"
copy /Y "build\native\YozakuraWebView2Bridge-%PACKAGE_ARCH%-package.dll" "%PACKAGE_DIR%\YozakuraWebView2Bridge.dll" >nul
if errorlevel 1 exit /b 1
copy /Y "build\libs\WebView2Loader-%PACKAGE_ARCH%.dll" "%PACKAGE_DIR%\WebView2Loader.dll" >nul
if errorlevel 1 exit /b 1
set "DEV_RESOURCE_DIR=build\resources\main\assets\yozakura\native\%PACKAGE_ARCH%"
if not exist "%DEV_RESOURCE_DIR%" mkdir "%DEV_RESOURCE_DIR%"
copy /Y "build\native\YozakuraWebView2Bridge-%PACKAGE_ARCH%-package.dll" "%DEV_RESOURCE_DIR%\YozakuraWebView2Bridge.dll" >nul
if errorlevel 1 exit /b 1
copy /Y "build\libs\WebView2Loader-%PACKAGE_ARCH%.dll" "%DEV_RESOURCE_DIR%\WebView2Loader.dll" >nul
if errorlevel 1 exit /b 1
"%JAVA8_HOME%\bin\jar.exe" uf "build\libs\Yozakura.jar" -C "build\native\jar-native" assets
exit /b %errorlevel%

:synchronize_final_runtime_jar
if not defined YOZAKURA_RUNTIME_JAR exit /b 0
copy /Y "build\libs\Yozakura.jar" "%YOZAKURA_RUNTIME_JAR%" >nul
if errorlevel 1 (
  echo Failed to synchronize the packaged native assets back to the named release JAR.
  exit /b 1
)
powershell -NoProfile -Command "$release=(Get-FileHash -Algorithm SHA256 -LiteralPath '%YOZAKURA_RUNTIME_JAR%').Hash; $runtime=(Get-FileHash -Algorithm SHA256 -LiteralPath 'build\libs\Yozakura.jar').Hash; if ($release -ne $runtime) { Write-Host ('Release: ' + $release); Write-Host ('Runtime: ' + $runtime); exit 1 }"
if errorlevel 1 (
  echo Final named release JAR does not match build\libs\Yozakura.jar.
  exit /b 1
)
exit /b 0

:select_java8_from
if not exist "%~1\release" exit /b 0
for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"JAVA_VERSION=" "%~1\release"') do set "CANDIDATE_JAVA_VERSION=%%~B"
if "%CANDIDATE_JAVA_VERSION:~0,4%"=="1.8." set "JAVA8_HOME=%~1"
exit /b 0
