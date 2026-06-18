@echo off
setlocal

if not defined JAVA8_HOME if defined JAVA_HOME set "JAVA8_HOME=%JAVA_HOME%"
if not defined JAVA8_HOME set "JAVA8_HOME=C:\Users\shiranaidk\jdk-8\jdk8u492-b09"
if not defined VS_BUILD set "VS_BUILD=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"

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
  echo Warning: build\libs\YozakuraLoader.dll is locked. Use the arch-specific DLLs below.
) else (
  echo   build\libs\YozakuraLoader.dll
)

echo Built:
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

cl /nologo /EHsc /LD /O2 /GL ^
  /I"%JAVA8_HOME%\include" ^
  /I"%JAVA8_HOME%\include\win32" ^
  /Fo"build\native\%ARCH%_" ^
  "native\yozakura_loader.cpp" ^
  "build\native\yozakura_loader_%ARCH%.res" ^
  /link /NOLOGO /LTCG /OPT:REF /OPT:ICF /OUT:"%OUTDLL%" /IMPLIB:"build\native\%ARCH%.lib" /PDB:"build\native\%ARCH%.pdb"

exit /b %errorlevel%
