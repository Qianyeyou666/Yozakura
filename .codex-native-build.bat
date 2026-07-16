@echo off
setlocal
if not exist "build\native" mkdir "build\native"
call :build_one x64 "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" "build\libs\YozakuraLoader-x64.dll"
if errorlevel 1 exit /b %errorlevel%
call :build_one x86 "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars32.bat" "build\libs\YozakuraLoader-x86.dll"
if errorlevel 1 exit /b %errorlevel%
copy /Y "build\libs\YozakuraLoader-x64.dll" "build\libs\YozakuraLoader.dll" >nul
exit /b %errorlevel%

:build_one
call "%~2" >nul
if errorlevel 1 exit /b %errorlevel%
rc /nologo /fo "build\native\yozakura_loader_%~1.res" "native\yozakura_loader.rc"
if errorlevel 1 exit /b %errorlevel%
cl /nologo /EHsc /c /O2 /GL /I"C:\Users\Administrator\.jdks\corretto-1.8.0_492\include" /I"C:\Users\Administrator\.jdks\corretto-1.8.0_492\include\win32" /Fo"build\native\%~1_loader.obj" "native\yozakura_loader.cpp"
if errorlevel 1 exit /b %errorlevel%
cl /nologo /EHsc /c /O2 /GL /I"C:\Users\Administrator\.jdks\corretto-1.8.0_492\include" /I"C:\Users\Administrator\.jdks\corretto-1.8.0_492\include\win32" /Fo"build\native\%~1_auth.obj" "native\yozakura_native_auth.cpp"
if errorlevel 1 exit /b %errorlevel%
link /NOLOGO /DLL /LTCG /OPT:REF /OPT:ICF "build\native\%~1_loader.obj" "build\native\%~1_auth.obj" "build\native\yozakura_loader_%~1.res" winhttp.lib /OUT:"%~3" /IMPLIB:"build\native\%~1.lib" /PDB:"build\native\%~1.pdb"
exit /b %errorlevel%
