param(
    [Parameter(Mandatory = $true)]
    [string]$InputJar,

    [Parameter(Mandatory = $true)]
    [string]$OutputJar,

    [Parameter(Mandatory = $true)]
    [string]$ZkmJar,

    [Parameter(Mandatory = $true)]
    [string]$JnicJar,

    [Parameter(Mandatory = $true)]
    [string]$ZkmLibs,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome,

    [Parameter(Mandatory = $true)]
    [string]$ZkmJavaHome
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$workDir = Join-Path $repoRoot "build\obfuscation\work"
$zkmDir = Split-Path -Parent $ZkmJar
$jnicDir = Split-Path -Parent $JnicJar
$java = Join-Path $JavaHome "bin\java.exe"
$zkmJava = Join-Path $ZkmJavaHome "bin\java.exe"
$template = Join-Path $repoRoot "obfuscation\zkm-release.zkm.template"
$jnicConfig = Join-Path $repoRoot "obfuscation\jnic-release.xml"
$verifier = Join-Path $PSScriptRoot "Verify-ObfuscatedJar.ps1"

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path $Path -PathType Leaf)) {
        throw "$Description was not found: $Path"
    }
}

function To-ZkmPath([string]$Path) {
    return $Path.Replace('\', '/').Replace('"', '\"')
}

function Assert-ZkmResolutionLog([string]$Path) {
    $unresolved = @(Get-Content $Path | Where-Object {
        $_ -match "WARNING:\s+(Class|Field|Method)\s+'.+'\s+not found"
    })
    if ($unresolved.Count -ne 0) {
        $preview = ($unresolved | Select-Object -First 10) -join [Environment]::NewLine
        throw "ZKM reported unresolved bytecode contracts. Fix the dependency classpath or source references before release:`n$preview"
    }
}

Require-File $InputJar "Input JAR"
Require-File $java "JDK Java launcher"
Require-File $zkmJava "ZKM Java 8 launcher"
Require-File $ZkmJar "Zelix KlassMaster JAR"
Require-File $JnicJar "JNIC-compatible JAR"
Require-File $template "ZKM release template"
Require-File $jnicConfig "JNIC release configuration"
Require-File $verifier "JAR verifier"
# Test-compatible implementations may use nonstandard branding or filenames.
# Tool hashes are recorded below so each build remains reproducible and auditable.
if (-not (Test-Path $ZkmLibs -PathType Container)) {
    throw "ZKM dependency directory was not found: $ZkmLibs"
}
if (-not (Get-ChildItem $ZkmLibs -Filter *.jar -File | Select-Object -First 1)) {
    throw "ZKM dependency directory contains no JAR files: $ZkmLibs"
}
if (-not (Get-ChildItem $jnicDir -Filter zig.exe -File -Recurse | Select-Object -First 1)) {
    throw "JNIC requires Zig extracted below $jnicDir. Place a JNIC-compatible Windows Zig release beside the JNIC JAR."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$inputArchive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $InputJar).Path)
try {
    $inputEntries = @($inputArchive.Entries | ForEach-Object { $_.FullName })
} finally {
    $inputArchive.Dispose()
}
if (@($inputEntries | Where-Object { $_ -match '^(myj2c|dev/jnic)/' }).Count -ne 0) {
    throw "Input JAR is already Java-to-native transformed. Provide the fresh Gradle runtime JAR."
}

if (Test-Path $workDir) {
    Remove-Item $workDir -Recurse -Force
}
New-Item -ItemType Directory -Force $workDir | Out-Null
$stagedInput = Join-Path $workDir "Yozakura-input.jar"
$zkmOutput = Join-Path $workDir "Yozakura-zkm.jar"
$jnicOutput = Join-Path $workDir "Yozakura-jnic.jar"
$zkmScript = Join-Path $workDir "release.zkm"
$zkmLog = Join-Path $workDir "zkm.log"
$jnicLog = Join-Path $workDir "jnic.log"
$toolReport = Join-Path $workDir "tool-hashes.txt"
Copy-Item $InputJar $stagedInput -Force

@(
    "ZKM JAR: $ZkmJar",
    "ZKM SHA-256: $((Get-FileHash -Algorithm SHA256 $ZkmJar).Hash.ToLowerInvariant())",
    "JNIC JAR: $JnicJar",
    "JNIC SHA-256: $((Get-FileHash -Algorithm SHA256 $JnicJar).Hash.ToLowerInvariant())"
) | Set-Content -Encoding UTF8 $toolReport

$script = Get-Content $template -Raw
$script = $script.Replace('@ZKM_CLASSPATH@', (To-ZkmPath (Join-Path $ZkmLibs "*.jar")))
$script = $script.Replace('@INPUT_JAR@', (To-ZkmPath $stagedInput))
$script = $script.Replace('@OUTPUT_JAR@', (To-ZkmPath $zkmOutput))
$dynamicPrefixes = @(
    'gq/yozakura/bridge/forge/',
    'gq/yozakura/event/bridge/',
    'gq/yozakura/core/modern/ModernForgeEventBridge'
)
$dynamicClasses = @($inputEntries | Where-Object {
    $entry = $_
    $entry.EndsWith('.class') -and @($dynamicPrefixes | Where-Object { $entry.StartsWith($_) }).Count -ne 0
} | Sort-Object | ForEach-Object {
    $name = $_.Substring(0, $_.Length - 6).Replace('/', '.')
    $separator = $name.LastIndexOf('.')
    'exclude class ' + $name.Substring(0, $separator) + '(' + $name.Substring($separator + 1) + ');'
})
$externalClasses = @($inputEntries | Where-Object {
    $_.EndsWith('.class') -and -not $_.StartsWith('gq/yozakura/')
} | Sort-Object | ForEach-Object {
    $_.Substring(0, $_.Length - 6).Replace('/', '.')
})
$dynamicPackages = @(($dynamicClasses + $externalClasses) | ForEach-Object {
    $name = $_
    if ($name.StartsWith('exclude class ')) {
        $name = $name.Substring(14)
        $marker = $name.IndexOf('!(')
        if ($marker -gt 0) { $name = $name.Substring(0, $marker) }
    } else {
        $separator = $name.LastIndexOf('.')
        if ($separator -gt 0) { $name = $name.Substring(0, $separator) }
    }
    $name
} | Sort-Object -Unique | ForEach-Object {
    $separator = $_.LastIndexOf('.')
    'exclude package ' + $_ + ';'
})
$dynamicClasses = $dynamicPackages + $dynamicClasses
foreach ($externalClass in $externalClasses) {
    $separator = $externalClass.LastIndexOf('.')
    $dynamicClasses += 'exclude class ' + $externalClass.Substring(0, $separator) + '(' + $externalClass.Substring($separator + 1) + ');'
    $dynamicClasses += 'exclude ' + $externalClass + '^*(*);'
}
$script = $script.Replace('@DYNAMIC_EXCLUDES@', ($dynamicClasses -join [Environment]::NewLine))
$script | Set-Content -Encoding ASCII $zkmScript

Write-Host "[1/3] Running Zelix KlassMaster..."
Push-Location $zkmDir
try {
    & $zkmJava -jar $ZkmJar -p $zkmScript
    if ($LASTEXITCODE -ne 0) {
        throw "ZKM rejected the release script with exit code $LASTEXITCODE"
    }
    & $zkmJava -jar $ZkmJar -v -l $zkmLog $zkmScript
    if ($LASTEXITCODE -ne 0) {
        throw "ZKM failed with exit code $LASTEXITCODE. See $zkmLog"
    }
} finally {
    Pop-Location
}
Require-File $zkmOutput "ZKM output JAR"
Require-File $zkmLog "ZKM audit log"
Assert-ZkmResolutionLog $zkmLog

Write-Host "[2/3] Translating the authentication gate with JNIC..."
Push-Location $jnicDir
try {
    & $java -jar $JnicJar $zkmOutput $jnicOutput $jnicConfig 2>&1 | Tee-Object -FilePath $jnicLog
    if ($LASTEXITCODE -ne 0) {
        throw "JNIC failed with exit code $LASTEXITCODE. See $jnicLog"
    }
} finally {
    Pop-Location
}
Require-File $jnicOutput "JNIC output JAR"

Write-Host "[3/3] Verifying transformed JAR contracts..."
& $verifier -InputJar $stagedInput -ZkmJar $zkmOutput -Jar $jnicOutput -JavaHome $JavaHome -Report (Join-Path $workDir "verification.txt")
if ($LASTEXITCODE -ne 0) {
    throw "Final obfuscation verification failed with exit code $LASTEXITCODE"
}

$outputDir = Split-Path -Parent $OutputJar
if ($outputDir) {
    New-Item -ItemType Directory -Force $outputDir | Out-Null
}
Copy-Item $jnicOutput $OutputJar -Force
Write-Host "Obfuscated runtime JAR: $OutputJar"
