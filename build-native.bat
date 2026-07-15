@echo off
setlocal

pushd "%~dp0"
if errorlevel 1 exit /b %errorlevel%

call :main %*
set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%

:main

set "AUTH_BASE_URL=%~1"
if not defined AUTH_BASE_URL set "AUTH_BASE_URL=%YOZAKURA_AUTH_BASE_URL%"
if not defined AUTH_BASE_URL set "AUTH_BASE_URL=https://auth.yozakura.wtf/"

echo Authentication endpoint: %AUTH_BASE_URL%

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

echo Refreshing build\libs\Yozakura.jar...
call "%~dp0gradlew.bat" syncRuntimeJar "-Pyozakura_auth_base_url=%AUTH_BASE_URL%"
if errorlevel 1 exit /b %errorlevel%

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

call :build_one x64 "%VS_BUILD%\vcvars64.bat" "build\libs\YozakuraLoader-x64.dll"
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

if not exist "%VCVARS%" (
  echo Visual Studio environment not found: %VCVARS%
  exit /b 1
)

echo Building %ARCH% DLL...
call "%VCVARS%" >nul
if errorlevel 1 exit /b %errorlevel%

rc /nologo /fo "build\native\yozakura_loader_%ARCH%.res" "native\yozakura_loader.rc"
if errorlevel 1 exit /b %errorlevel%

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

link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF ^
  "build\native\%ARCH%_loader.obj" ^
  "build\native\%ARCH%_auth.obj" ^
  "build\native\yozakura_loader_%ARCH%.res" ^
  winhttp.lib ^
  /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib" /PDB:"build\native\%ARCH%.pdb"

exit /b %errorlevel%

:select_java8_from
if not exist "%~1\release" exit /b 0
for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"JAVA_VERSION=" "%~1\release"') do set "CANDIDATE_JAVA_VERSION=%%~B"
if "%CANDIDATE_JAVA_VERSION:~0,4%"=="1.8." set "JAVA8_HOME=%~1"
exit /b 0
