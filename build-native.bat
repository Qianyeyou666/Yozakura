@echo off
setlocal

pushd "%~dp0"
if errorlevel 1 exit /b %errorlevel%

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
  copy /Y "%YOZAKURA_RUNTIME_JAR%" "build\libs\Yozakura.jar" >nul
  if errorlevel 1 exit /b %errorlevel%
  if defined YOZAKURA_OBFUSCATION_INPUT_JAR if defined YOZAKURA_INTERMEDIATE_JAR (
    echo Verifying protected runtime JAR...
    powershell -ExecutionPolicy Bypass -File "tools\Verify-ObfuscatedJar.ps1" ^
      -InputJar "%YOZAKURA_OBFUSCATION_INPUT_JAR%" ^
      -ZkmJar "%YOZAKURA_INTERMEDIATE_JAR%" ^
      -Jar "build\libs\Yozakura.jar" ^
      -JavaHome "%JAVA8_HOME%"
    if errorlevel 1 exit /b %errorlevel%
  ) else (
    echo Verifying runtime JAR ZIP integrity...
    "%JAVA8_HOME%\bin\jar.exe" tf "build\libs\Yozakura.jar" >nul
    if errorlevel 1 exit /b %errorlevel%
  )
) else (
  echo Refreshing build\libs\Yozakura.jar...
  call "%~dp0gradlew.bat" syncRuntimeJar
  if errorlevel 1 exit /b %errorlevel%
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
  call :build_one x64 "%VS_BUILD%\vcvars64.bat" "build\libs\YozakuraLoader-x64-themida-input.dll"
  if errorlevel 1 exit /b %errorlevel%
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\Verify-NativePayload.ps1" -Dll "build\libs\YozakuraLoader-x64-themida-input.dll" -Jar "build\libs\Yozakura.jar"
  if errorlevel 1 exit /b %errorlevel%
  echo Built Themida input:
  echo   build\libs\YozakuraLoader-x64-themida-input.dll
  exit /b 0
)

call :build_one x64 "%VS_BUILD%\vcvars64.bat" "build\libs\YozakuraLoader-x64.dll"
if errorlevel 1 exit /b %errorlevel%
powerShell -NoProfile -ExecutionPolicy Bypass -File "tools\Verify-NativePayload.ps1" -Dll "build\libs\YozakuraLoader-x64.dll" -Jar "build\libs\Yozakura.jar"
if errorlevel 1 exit /b %errorlevel%

call :build_one x86 "%VS_BUILD%\vcvars32.bat" "build\libs\YozakuraLoader-x86.dll"
if errorlevel 1 exit /b %errorlevel%

copy /Y "build\libs\YozakuraLoader-x64.dll" "build\libs\YozakuraLoader.dll" >nul
if errorlevel 1 (
  echo Failed to refresh build\libs\YozakuraLoader.dll. Close any process using the old loader and rebuild.
  exit /b 1
)

echo Built:
echo   build\libs\YozakuraLoader.dll
echo   build\libs\YozakuraLoader-x64.dll
echo   build\libs\YozakuraLoader-x86.dll
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
if errorlevel 1 exit /b %errorlevel%

rc /nologo /fo "build\native\yozakura_loader_%ARCH%.res" "native\yozakura_loader.rc"
if errorlevel 1 exit /b %errorlevel%

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
if errorlevel 1 exit /b %errorlevel%

if /I "%ARCH%"=="x64" if /I not "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  cl /nologo /EHsc /O2 ^
    "native\tests\yozakura_themida_guard_test.cpp" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    /Fe"build\native\yozakura_themida_guard_test.exe"
  if errorlevel 1 exit /b %errorlevel%
  "build\native\yozakura_themida_guard_test.exe"
  if errorlevel 1 exit /b %errorlevel%
)

cl /nologo /EHsc /c /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /Fo"build\native\%ARCH%_loader.obj" ^
  "native\yozakura_loader.cpp"
if errorlevel 1 exit /b %errorlevel%

cl /nologo /EHsc /c /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /Fo"build\native\%ARCH%_auth.obj" ^
  "native\yozakura_native_auth.cpp"
if errorlevel 1 exit /b %errorlevel%

if /I "%YOZAKURA_THEMIDA_MARKERS%"=="1" (
  link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF /INCREMENTAL:NO /RELEASE ^
    "build\native\%ARCH%_loader.obj" ^
    "build\native\%ARCH%_auth.obj" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    "build\native\yozakura_loader_%ARCH%.res" ^
    "%YOZAKURA_THEMIDA_SDK%\Lib\COFF\SecureEngineSDK64.lib" ^
    winhttp.lib ^
    crypt32.lib ^
    advapi32.lib ^
    /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib"
) else (
  link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF /INCREMENTAL:NO /RELEASE ^
    "build\native\%ARCH%_loader.obj" ^
    "build\native\%ARCH%_auth.obj" ^
    "build\native\%ARCH%_themida_guard.obj" ^
    "build\native\yozakura_loader_%ARCH%.res" ^
    winhttp.lib ^
    crypt32.lib ^
    advapi32.lib ^
    /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib"
)

exit /b %errorlevel%

:select_java8_from
if not exist "%~1\release" exit /b 0
for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"JAVA_VERSION=" "%~1\release"') do set "CANDIDATE_JAVA_VERSION=%%~B"
if "%CANDIDATE_JAVA_VERSION:~0,4%"=="1.8." set "JAVA8_HOME=%~1"
exit /b 0
