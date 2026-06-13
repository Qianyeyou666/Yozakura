param(
    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [string]$EntryClass = "gq.vapulite.core.Client",

    [string]$Name,

    [ValidateSet("x64", "x86", "all")]
    [string]$Arch = "x64",

    [string]$JavaHome = "C:\Users\qiany\.jdks\corretto-1.8.0_492",

    [string]$VsBuild = "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build"
)

$ErrorActionPreference = "Stop"

function Convert-ToCDefineString([string]$Value) {
    return '\"' + ($Value -replace '\\', '\\' -replace '"', '\"') + '\"'
}

function Convert-ToCWideDefineString([string]$Value) {
    return 'L' + (Convert-ToCDefineString $Value)
}

function Invoke-NativeBuild([string]$TargetArch, [string]$VcVars, [string]$OutDll, [string]$RcFile, [string]$ObjPrefix, [string]$BuildDir, [string]$EntryClass, [string]$LogName, [string]$TempPrefix, [string]$JavaHome) {
    if (!(Test-Path -LiteralPath $VcVars)) {
        throw "Visual Studio environment not found: $VcVars"
    }

    $entryDefine = Convert-ToCDefineString $EntryClass
    $logDefine = Convert-ToCDefineString $LogName
    $tempDefine = Convert-ToCWideDefineString $TempPrefix

    $cmd = @"
@echo off
call "$VcVars" >nul
if errorlevel 1 exit /b %errorlevel%
rc /nologo /fo "$BuildDir\payload_$TargetArch.res" "$RcFile"
if errorlevel 1 exit /b %errorlevel%
cl /nologo /EHsc /LD /O2 /DJAR_TO_DLL_CLIENT_CLASS=$entryDefine /DJAR_TO_DLL_LOG_NAME=$logDefine /DJAR_TO_DLL_TEMP_PREFIX=$tempDefine /I"$JavaHome\include" /I"$JavaHome\include\win32" /Fo"$ObjPrefix" "native\vapulite_loader.cpp" "$BuildDir\payload_$TargetArch.res" /link /NOLOGO /OUT:"$OutDll" /IMPLIB:"$BuildDir\$TargetArch.lib" /PDB:"$BuildDir\$TargetArch.pdb"
"@

    Write-Host "Building $TargetArch -> $OutDll"
    $batPath = Join-Path $BuildDir "build_$TargetArch.bat"
    Set-Content -LiteralPath $batPath -Value $cmd -Encoding ASCII
    cmd /c "`"$batPath`""
    if ($LASTEXITCODE -ne 0) {
        throw "Native build failed for $TargetArch"
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$jarPath = (Resolve-Path -LiteralPath $Jar).Path
if (!(Test-Path -LiteralPath "$JavaHome\include\jni.h")) {
    throw "JDK JNI headers not found: $JavaHome"
}

if ([string]::IsNullOrWhiteSpace($Name)) {
    $Name = [IO.Path]::GetFileNameWithoutExtension($jarPath)
}

$safeName = $Name -replace '[^A-Za-z0-9_.-]', '_'
$buildDir = Join-Path $repoRoot "build\jar-to-dll\$safeName"
$outDir = Join-Path $repoRoot "build\libs"
New-Item -ItemType Directory -Force -Path $buildDir, $outDir | Out-Null

$payloadJar = Join-Path $buildDir "payload.jar"
Copy-Item -LiteralPath $jarPath -Destination $payloadJar -Force

$rcPath = Join-Path $buildDir "payload.rc"
$rcJarPath = ($payloadJar -replace '\\', '/')
@"
#define IDR_VAPULITE_JAR 101
IDR_VAPULITE_JAR RCDATA "$rcJarPath"
"@ | Set-Content -LiteralPath $rcPath -Encoding ASCII

$logName = "$safeName-Loader.log"
$tempPrefix = $safeName
$built = @()

if ($Arch -eq "x64" -or $Arch -eq "all") {
    $outDll = Join-Path $outDir "$safeName-x64.dll"
    Invoke-NativeBuild "x64" (Join-Path $VsBuild "vcvars64.bat") $outDll $rcPath (Join-Path $buildDir "x64_") $buildDir $EntryClass $logName $tempPrefix $JavaHome
    $built += $outDll
}

if ($Arch -eq "x86" -or $Arch -eq "all") {
    $outDll = Join-Path $outDir "$safeName-x86.dll"
    Invoke-NativeBuild "x86" (Join-Path $VsBuild "vcvars32.bat") $outDll $rcPath (Join-Path $buildDir "x86_") $buildDir $EntryClass $logName $tempPrefix $JavaHome
    $built += $outDll
}

if ($Arch -ne "all") {
    $singleName = Join-Path $outDir "$safeName.dll"
    Copy-Item -LiteralPath $built[0] -Destination $singleName -Force
    $built += $singleName
}

Write-Host ""
Write-Host "Built DLL:"
$built | ForEach-Object { Write-Host "  $_" }
Write-Host "Log file after injection:"
Write-Host "  %TEMP%\$logName"
