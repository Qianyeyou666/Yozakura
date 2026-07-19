param(
    [switch]$SkipGradle,
    [switch]$SkipNative
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$workDir = Join-Path $repoRoot "build\obfuscation\release-neko-jnic"
$inputJar = Join-Path $repoRoot "build\obfuscation\input\Yozakura-unobfuscated.jar"
$libsDir = Join-Path $repoRoot "build\obfuscation\zkm-libs"
$selectiveJar = Join-Path $workDir "Yozakura-neko-input.jar"
$nekoJar = Join-Path $workDir "Yozakura-neko.jar"
$mergedJar = Join-Path $workDir "Yozakura-neko-merged.jar"
$jnicJarOutput = Join-Path $workDir "Yozakura-neko-jnic.jar"
$nekoConfig = Join-Path $workDir "neko-release.yml"
$nekoRules = Join-Path $repoRoot "obfuscation\neko-release-rules.yml"
$jnicConfig = Join-Path $repoRoot "obfuscation\jnic-auth-release.xml"
$releaseJar = Join-Path $repoRoot "build\libs\Yozakura-1.5.0-neko-jnic-cff.jar"
$verificationReport = Join-Path $workDir "verification-native-payload.txt"

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description was not found: $Path"
    }
}

function Require-Directory([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Description was not found: $Path"
    }
}

function Resolve-JavaHome([int]$Major, [string[]]$Candidates) {
    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $java = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
            continue
        }
        $release = Join-Path $candidate "release"
        if (Test-Path -LiteralPath $release -PathType Leaf) {
            $versionLine = Get-Content -LiteralPath $release | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
            if ($Major -eq 8 -and $versionLine -notmatch 'JAVA_VERSION="1\.8\.') {
                continue
            }
            if ($Major -ne 8 -and $versionLine -notmatch ('JAVA_VERSION="' + $Major + '\.')) {
                continue
            }
        }
        return (Resolve-Path -LiteralPath $candidate).Path
    }
    throw "JDK $Major was not found. Set JAVA${Major}_HOME to its installation directory."
}

function ConvertTo-YamlPath([string]$Path) {
    return $Path.Replace('\', '/').Replace("'", "''")
}

function Test-SelectiveExclusion([string]$Name) {
    if ($Name -match '^(dev/jnic|myj2c)/') {
        return $true
    }
    if ($Name.EndsWith('.class') -and
            ($Name.StartsWith('com/google/zxing/') -or $Name.StartsWith('javazoom/jl/'))) {
        return $true
    }
    return $false
}

function Test-OverlayEntry([string]$Name) {
    if ($Name.EndsWith('.class') -and
            ($Name.StartsWith('com/google/zxing/') -or
             $Name.StartsWith('javazoom/jl/'))) {
        return $true
    }
    return $false
}

function Copy-ZipEntry($SourceEntry, $DestinationArchive) {
    $target = $DestinationArchive.CreateEntry($SourceEntry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
    $target.LastWriteTime = $SourceEntry.LastWriteTime
    $input = $SourceEntry.Open()
    $output = $target.Open()
    try {
        $input.CopyTo($output)
    } finally {
        $output.Dispose()
        $input.Dispose()
    }
}

function New-SelectiveJar([string]$Source, [string]$Destination) {
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or (Test-SelectiveExclusion $entry.FullName)) {
                continue
            }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose()
        $outputStream.Dispose()
        $sourceArchive.Dispose()
    }
}

function Merge-StablePackages([string]$Base, [string]$Source, [string]$Destination) {
    $baseArchive = [System.IO.Compression.ZipFile]::OpenRead($Base)
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $written = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    try {
        foreach ($entry in $baseArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or (Test-OverlayEntry $entry.FullName)) {
                continue
            }
            if (-not $written.Add($entry.FullName)) {
                throw "Duplicate Neko entry: $($entry.FullName)"
            }
            Copy-ZipEntry $entry $destinationArchive
        }
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or -not (Test-OverlayEntry $entry.FullName)) {
                continue
            }
            if (-not $written.Add($entry.FullName)) {
                throw "Duplicate overlay entry: $($entry.FullName)"
            }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose()
        $outputStream.Dispose()
        $sourceArchive.Dispose()
    }
}

function Assert-Jar([string]$Jar, [string]$Javap) {
    Require-File $Jar "Protected JAR"
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $names = @($archive.Entries | Where-Object { -not $_.FullName.EndsWith('/') } | ForEach-Object { $_.FullName })
    } finally {
        $archive.Dispose()
    }
    $seenNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $duplicateName = $null
    foreach ($name in $names) {
        if (-not $seenNames.Add($name)) {
            $duplicateName = $name
            break
        }
    }
    if ($null -ne $duplicateName) {
        throw "Protected JAR contains an exact duplicate entry: $duplicateName"
    }
    $required = @(
        'gq/yozakura/auth/NativeAuthBridge.class',
        'gq/yozakura/auth/NativeAuthBridge$1.class',
        'gq/yozakura/auth/YozakuraAuthGate.class',
        'gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.class',
        'gq/yozakura/core/Client.class',
        'gq/yozakura/core/StandaloneClient.class',
        'gq/yozakura/core/ModernForgeClient.class',
        'gq/yozakura/module/Module.class',
        'gq/yozakura/event/bus/EventManager.class',
        'gq/yozakura/event/api/EventManager.class',
        'gq/yozakura/bridge/MovementInputBridge.class',
        'gq/yozakura/bridge/forge/MouseEvent.class',
        'gq/yozakura/bridge/forge/TickEvent$ClientTickEvent.class',
        'gq/yozakura/bridge/forge/RenderWorldLastEvent.class',
        'gq/yozakura/auth/token/TokenAuthGuiHandler.class',
        'gq/yozakura/auth/token/TokenAuthSessionManager.class',
        'gq/yozakura/auth/token/TokenAuthStandaloneBridge.class',
        'gq/yozakura/auth/token/TokenAuthSessionGui.class',
        'gq/yozakura/ui/click/material/MaterialClickGui.class',
        'gq/yozakura/ui/click/sakura/SakuraClickGui.class',
        'gq/yozakura/ui/click/yozakura/YozakuraClickGui.class'
    )
    foreach ($entry in $required) {
        if ($names -notcontains $entry) {
            throw "Protected JAR is missing required ABI entry: $entry"
        }
    }
    $bridgeAbi = & $Javap -classpath $Jar -p -s gq.yozakura.auth.NativeAuthBridge 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "javap could not inspect NativeAuthBridge."
    }
    if ($bridgeAbi -notmatch 'native void logout0\(\);') {
        throw "NativeAuthBridge.logout0()V is missing after obfuscation."
    }
    if ($bridgeAbi -notmatch 'native long q0\(long\);') {
        throw "NativeAuthBridge.q0(J)J is missing after obfuscation."
    }
    if ($bridgeAbi -notmatch 'native long q1\(int, long\);') {
        throw "NativeAuthBridge.q1(IJ)J is missing after obfuscation."
    }
    $movementBridge = & $Javap -classpath $Jar -p -c -verbose gq.yozakura.bridge.MovementInputBridge 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "javap could not inspect MovementInputBridge."
    }
    if ($movementBridge -match 'gq/yozakura/manager/RotationState') {
        throw "MovementInputBridge contains a stale pre-rename RotationState reference."
    }
    foreach ($guiClass in @(
        'gq.yozakura.ui.click.material.MaterialClickGui',
        'gq.yozakura.ui.click.sakura.SakuraClickGui',
        'gq.yozakura.ui.click.yozakura.YozakuraClickGui'
    )) {
        $guiAbi = & $Javap -classpath $Jar -p -s $guiClass 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) {
            throw "javap could not inspect GUI callback ABI: $guiClass"
        }
        if ($guiAbi -notmatch 'void func_73866_w_\(\);' -or
                $guiAbi -notmatch 'void func_73863_a\(int, int, float\);') {
            throw "Minecraft GUI callbacks were renamed in $guiClass."
        }
    }
    $tokenAuthGuiClass = 'gq.yozakura.auth.token.TokenAuthSessionGui'
    $tokenAuthGuiAbi = & $Javap -classpath $Jar -p -s $tokenAuthGuiClass 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "javap could not inspect TokenAuth GUI callback ABI: $tokenAuthGuiClass"
    }
    foreach ($callback in @(
        'func_73866_w_()',
        'func_146281_b()',
        'func_73876_c()',
        'func_73863_a(int, int, float)',
        'func_146284_a(net.minecraft.client.gui.GuiButton)',
        'func_73869_a(char, int)',
        'func_73864_a(int, int, int)',
        'func_73868_f()'
    )) {
        if ($tokenAuthGuiAbi -notmatch [regex]::Escape($callback)) {
            throw "Minecraft GUI callback $callback was renamed in $tokenAuthGuiClass."
        }
    }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$java8Home = Resolve-JavaHome 8 @(
    $env:JAVA8_HOME,
    $env:JAVA_HOME,
    (Join-Path $env:USERPROFILE ".jdks\corretto-1.8.0_492"),
    (Join-Path $env:USERPROFILE ".jdks\temurin-8")
)
$java17Home = Resolve-JavaHome 17 @(
    $env:JAVA17_HOME,
    (Join-Path $env:USERPROFILE ".jdks\microsoft-17-portable"),
    (Join-Path $env:USERPROFILE ".jdks\temurin-17-portable"),
    @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE ".jdks\microsoft-17-portable") -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }),
    @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE ".jdks\temurin-17-portable") -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName })
)
$java8 = Join-Path $java8Home "bin\java.exe"
$javap8 = Join-Path $java8Home "bin\javap.exe"
$java17 = Join-Path $java17Home "bin\java.exe"

$nekoHome = if ($env:YOZAKURA_NEKO_HOME) { $env:YOZAKURA_NEKO_HOME } else { "D:\obf\neko-obfuscator-main" }
$nekoCli = Join-Path $nekoHome "neko-cli\build\install\neko-cli\bin\neko-cli.bat"
$jnicTool = if ($env:YOZAKURA_JNIC_JAR) { $env:YOZAKURA_JNIC_JAR } else { "D:\obf\jnic\jnic-3.7.0.jar" }

Require-File $nekoRules "Neko release rules"
Require-File $jnicConfig "JNIC authentication configuration"
Require-File $jnicTool "JNIC tool"

if (-not $SkipGradle) {
    Write-Host "[1/7] Building a fresh unobfuscated client JAR and classpath..."
    Push-Location $repoRoot
    $savedJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $java17Home
        $gradleArgs = @('prepareObfuscation')
        & (Join-Path $repoRoot "gradlew.bat") @gradleArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle prepareObfuscation failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:JAVA_HOME = $savedJavaHome
        Pop-Location
    }
} else {
    Write-Host "[1/7] Reusing the existing unobfuscated client JAR."
}

Require-File $inputJar "Fresh Gradle obfuscation input"
Require-Directory $libsDir "Obfuscation classpath"

$resolvedWork = [System.IO.Path]::GetFullPath($workDir)
$resolvedBuild = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "build\obfuscation"))
if (-not $resolvedWork.StartsWith($resolvedBuild, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean work directory outside build\obfuscation: $resolvedWork"
}
if (Test-Path -LiteralPath $workDir) {
    Remove-Item -LiteralPath $workDir -Recurse -Force
}
New-Item -ItemType Directory -Path $workDir -Force | Out-Null

if (-not (Test-Path -LiteralPath $nekoCli -PathType Leaf)) {
    Write-Host "Building the Neko CLI distribution..."
    $savedJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $java17Home
        & (Join-Path $nekoHome "gradlew.bat") :neko-cli:installDist
        if ($LASTEXITCODE -ne 0) {
            throw "Neko CLI build failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:JAVA_HOME = $savedJavaHome
    }
}
Require-File $nekoCli "Neko CLI"

Write-Host "[2/7] Preparing a clean Neko input (no stale JNIC payloads)..."
New-SelectiveJar $inputJar $selectiveJar

$classpath = @((Join-Path $java8Home "jre\lib\rt.jar"), $inputJar)
$classpath += @(Get-ChildItem -LiteralPath $libsDir -File -Filter "*.jar" | Sort-Object Name | ForEach-Object { $_.FullName })
$configHeader = @(
    "input: '$(ConvertTo-YamlPath $selectiveJar)'",
    "output: '$(ConvertTo-YamlPath $nekoJar)'",
    'classpath:'
)
$configHeader += $classpath | Select-Object -Unique | ForEach-Object { "  - '$(ConvertTo-YamlPath $_)'" }
$config = ($configHeader -join [Environment]::NewLine) + [Environment]::NewLine + [Environment]::NewLine +
    (Get-Content -LiteralPath $nekoRules -Raw -Encoding UTF8)
[System.IO.File]::WriteAllText($nekoConfig, $config, [System.Text.UTF8Encoding]::new($false))

Write-Host "[3/7] Running Neko rename and low-coverage CFF..."
$savedJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $java17Home
    & $nekoCli obfuscate --config $nekoConfig --input $selectiveJar --output $nekoJar
    if ($LASTEXITCODE -ne 0) {
        throw "Neko failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:JAVA_HOME = $savedJavaHome
}
Require-File $nekoJar "Neko output"

Write-Host "[4/7] Restoring isolated ZXing and JLayer classes..."
Merge-StablePackages $nekoJar $inputJar $mergedJar
Assert-Jar $mergedJar $javap8

Write-Host "[5/7] Running JNIC for authentication core and secondary core..."
Push-Location (Split-Path -Parent $jnicTool)
try {
    & $java17 -jar $jnicTool $mergedJar $jnicJarOutput $jnicConfig
    if ($LASTEXITCODE -ne 0) {
        throw "JNIC failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
Assert-Jar $jnicJarOutput $javap8
New-Item -ItemType Directory -Path (Split-Path -Parent $releaseJar) -Force | Out-Null
Copy-Item -LiteralPath $jnicJarOutput -Destination $releaseJar -Force

if (-not $SkipNative) {
    Write-Host "[6/7] Building loader DLLs with the protected JAR embedded..."
    $savedRuntimeJar = $env:YOZAKURA_RUNTIME_JAR
    $savedJava8Home = $env:JAVA8_HOME
    try {
        $env:YOZAKURA_RUNTIME_JAR = $releaseJar
        $env:JAVA8_HOME = $java8Home
        & (Join-Path $repoRoot "build-native.bat")
        if ($LASTEXITCODE -ne 0) {
            throw "build-native.bat failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:YOZAKURA_RUNTIME_JAR = $savedRuntimeJar
        $env:JAVA8_HOME = $savedJava8Home
    }

    Write-Host "[7/7] Verifying the embedded x64 native payload..."
    $loader = Join-Path $repoRoot "build\libs\YozakuraLoader-x64.dll"
    & (Join-Path $PSScriptRoot "Verify-NativePayload.ps1") -Dll $loader -Jar $releaseJar -Report $verificationReport
    if ($LASTEXITCODE -ne 0) {
        throw "Native payload verification failed with exit code $LASTEXITCODE."
    }
} else {
    Write-Host "[6/7] Native DLL build skipped by request."
    Write-Host "[7/7] Native payload verification skipped by request."
}

Write-Host ""
Write-Host "Release artifacts:"
foreach ($artifact in @($releaseJar, (Join-Path $repoRoot "build\libs\YozakuraLoader-x64.dll"))) {
    if (Test-Path -LiteralPath $artifact -PathType Leaf) {
        $hash = Get-FileHash -LiteralPath $artifact -Algorithm SHA256
        Write-Host "  $artifact"
        Write-Host "  SHA-256: $($hash.Hash)"
    }
}
