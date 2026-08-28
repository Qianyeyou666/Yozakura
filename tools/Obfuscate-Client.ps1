param(
    [switch]$SkipGradle,
    [switch]$SkipNative,
    [switch]$PreflightOnly,
    [switch]$NoThemida
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$profileName = if ($NoThemida) { 'neko-eskid-jnic-no-themida' } else { 'neko-eskid-jnic-release' }
$skipThemida = $NoThemida
$workDir = Join-Path $repoRoot ("build\obfuscation\" + $profileName)
$inputJar = Join-Path $repoRoot "build\obfuscation\input\Yozakura-unobfuscated.jar"
$dependencyLibsDir = Join-Path $repoRoot "build\obfuscation\zkm-libs"
$srgLibsDir = Join-Path $repoRoot "build\obfuscation\srg-libs"
$nekoInputJar = Join-Path $workDir "Yozakura-neko-input.jar"
$nekoJar = Join-Path $workDir "Yozakura-neko.jar"
$nekoMapping = $nekoJar + ".map"
$nekoMergedJar = Join-Path $workDir "Yozakura-neko-merged.jar"
$eskidBoundaryInput = Join-Path $workDir "Yozakura-eskid-application-boundary.jar"
$eskidSupportJar = Join-Path $workDir "Yozakura-eskid-stable-abi-support.jar"
$eskidBoundaryOutput = Join-Path $workDir "Yozakura-eskid-application-boundary-obfuscated.jar"
$eskidJar = Join-Path $workDir "Yozakura-neko-eskid.jar"
$jnicInputJar = Join-Path $workDir "Yozakura-neko-eskid-jnic-input.jar"
$jnicRawOutput = Join-Path $workDir "Yozakura-neko-eskid-jnic-raw.jar"
$jnicJarOutput = Join-Path $workDir "Yozakura-neko-eskid-jnic.jar"
$nekoConfig = Join-Path $workDir "neko-release.yml"
$eskidConfig = Join-Path $workDir "eskid-release.json"
$nekoLog = Join-Path $workDir "neko.log"
$eskidLog = Join-Path $workDir "eskid.log"
$jnicLog = Join-Path $workDir "jnic.log"
$toolHashes = Join-Path $workDir "tool-hashes.txt"
$releaseJarName = if ($NoThemida) {
    "build\libs\Yozakura-1.5.0-neko-eskid-jnic-no-themida.jar"
} else {
    "build\libs\Yozakura-1.5.0-neko-eskid-jnic.jar"
}
$releaseJar = Join-Path $repoRoot $releaseJarName
$nekoRules = Join-Path $repoRoot "obfuscation\neko-release-rules.yml"
$eskidTemplate = Join-Path $repoRoot "obfuscation\eskid-release.json"
$jnicConfig = Join-Path $repoRoot "obfuscation\jnic-auth-release.xml"
$themidaInput = Join-Path $repoRoot "build\libs\YozakuraLoader-x64-themida-input.dll"
$themidaOutput = Join-Path $repoRoot "build\libs\YozakuraLoader-x64.dll"
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
    $expanded = [System.Collections.Generic.List[string]]::new()
    foreach ($candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { $expanded.Add($candidate) }
    }
    $jdksRoot = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path -LiteralPath $jdksRoot -PathType Container) {
        foreach ($directory in @(Get-ChildItem -LiteralPath $jdksRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending)) {
            $expanded.Add($directory.FullName)
        }
    }
    foreach ($candidate in $expanded) {
        $java = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
        $release = Join-Path $candidate "release"
        if (Test-Path -LiteralPath $release -PathType Leaf) {
            $line = Get-Content -LiteralPath $release | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
            if ($Major -eq 8 -and $line -notmatch 'JAVA_VERSION="1\.8\.') { continue }
            if ($Major -ne 8 -and $line -notmatch ('JAVA_VERSION="' + $Major + '\.')) { continue }
        }
        return (Resolve-Path -LiteralPath $candidate).Path
    }
    throw "JDK $Major was not found. Set JAVA${Major}_HOME to its installation directory."
}

function ConvertTo-YamlPath([string]$Path) {
    return $Path.Replace('\', '/').Replace("'", "''")
}

function Test-StableThirdPartyEntry([string]$Name) {
    return $Name.StartsWith('com/') -or $Name.StartsWith('io/') -or
        $Name.StartsWith('javax/') -or $Name.StartsWith('javazoom/') -or
        $Name.StartsWith('org/')
}

function Test-EskidApplicationBoundaryEntry([string]$Name) {
    return $Name -eq 'META-INF/MANIFEST.MF' -or
        $Name.StartsWith('n/', [System.StringComparison]::Ordinal)
}

function Test-JnicAuthenticationBoundaryEntry([string]$Name) {
    return $Name -eq 'META-INF/MANIFEST.MF' -or
        $Name -eq 'gq/yozakura/k/A.class' -or
        $Name.StartsWith('gq/yozakura/k/A$', [System.StringComparison]::Ordinal) -or
        $Name -eq 'gq/yozakura/k/B.class' -or
        $Name.StartsWith('gq/yozakura/k/B$', [System.StringComparison]::Ordinal)
}

function Copy-ZipEntry($SourceEntry, $DestinationArchive) {
    $target = $DestinationArchive.CreateEntry($SourceEntry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
    $target.LastWriteTime = $SourceEntry.LastWriteTime
    $input = $SourceEntry.Open()
    $output = $target.Open()
    try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
}

function New-NekoInputJar([string]$Source, [string]$Destination) {
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or (Test-StableThirdPartyEntry $entry.FullName)) { continue }
            if ($entry.FullName -match '^(dev/jnic|myj2c)/') {
                throw "Fresh Gradle input contains a stale Java-to-native payload: $($entry.FullName)"
            }
            if ($entry.FullName.StartsWith('gq/yozakura/auth/', [System.StringComparison]::Ordinal)) {
                throw "Fresh Gradle input contains the retired authentication namespace: $($entry.FullName)"
            }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $sourceArchive.Dispose()
    }
}

function Merge-NekoOutputWithStableThirdParty([string]$Base, [string]$Source, [string]$Destination) {
    $baseArchive = [System.IO.Compression.ZipFile]::OpenRead($Base)
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $written = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    try {
        foreach ($entry in $baseArchive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate Neko entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or -not (Test-StableThirdPartyEntry $entry.FullName)) { continue }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate third-party overlay entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $sourceArchive.Dispose(); $baseArchive.Dispose()
    }
}

function New-EskidApplicationBoundaryJar([string]$Source, [string]$Destination) {
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $copiedClasses = 0
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or
                    -not (Test-EskidApplicationBoundaryEntry $entry.FullName)) { continue }
            Copy-ZipEntry $entry $destinationArchive
            if ($entry.FullName.StartsWith('n/', [System.StringComparison]::Ordinal) -and
                    $entry.FullName.EndsWith('.class', [System.StringComparison]::Ordinal)) {
                $copiedClasses++
            }
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $sourceArchive.Dispose()
    }
    if ($copiedClasses -lt 50) {
        throw "The Eskid application boundary is too small: copied $copiedClasses n/** classes."
    }
}

function New-EskidStableAbiSupportJar([string]$Source, [string]$Destination) {
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $copiedClasses = 0
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or
                    $entry.FullName.StartsWith('n/', [System.StringComparison]::Ordinal) -or
                    -not $entry.FullName.EndsWith('.class', [System.StringComparison]::Ordinal)) { continue }
            Copy-ZipEntry $entry $destinationArchive
            $copiedClasses++
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $sourceArchive.Dispose()
    }
    if ($copiedClasses -lt 10) {
        throw "The Eskid stable ABI support library is incomplete: copied $copiedClasses classes."
    }
}

function Merge-EskidApplicationBoundary([string]$Base, [string]$EskidBoundary, [string]$Destination) {
    $baseArchive = [System.IO.Compression.ZipFile]::OpenRead($Base)
    $eskidArchive = [System.IO.Compression.ZipFile]::OpenRead($EskidBoundary)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $written = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    try {
        foreach ($entry in $baseArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or (Test-EskidApplicationBoundaryEntry $entry.FullName)) { continue }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate full JAR entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
        foreach ($entry in $eskidArchive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if (-not (Test-EskidApplicationBoundaryEntry $entry.FullName)) {
                throw "Unexpected Eskid boundary output entry: $($entry.FullName)"
            }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate Eskid merge entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $eskidArchive.Dispose(); $baseArchive.Dispose()
    }
}

function New-JnicAuthenticationBoundaryJar([string]$Source, [string]$Destination) {
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $copied = 0
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or
                    -not (Test-JnicAuthenticationBoundaryEntry $entry.FullName)) { continue }
            Copy-ZipEntry $entry $destinationArchive
            $copied++
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $sourceArchive.Dispose()
    }
    if ($copied -lt 7) {
        throw "The JNIC authentication boundary is incomplete: copied $copied entries."
    }
}

function Merge-JnicAuthenticationBoundary([string]$Base, [string]$JnicBoundary, [string]$Destination) {
    $baseArchive = [System.IO.Compression.ZipFile]::OpenRead($Base)
    $jnicArchive = [System.IO.Compression.ZipFile]::OpenRead($JnicBoundary)
    $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $destinationArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    $written = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    try {
        foreach ($entry in $baseArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or
                    (Test-JnicAuthenticationBoundaryEntry $entry.FullName)) { continue }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate full JAR entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
        foreach ($entry in $jnicArchive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if (-not (Test-JnicAuthenticationBoundaryEntry $entry.FullName) -and
                    -not $entry.FullName.StartsWith('dev/jnic/', [System.StringComparison]::Ordinal)) {
                throw "Unexpected JNIC boundary output entry: $($entry.FullName)"
            }
            if (-not $written.Add($entry.FullName)) { throw "Duplicate JNIC merge entry: $($entry.FullName)" }
            Copy-ZipEntry $entry $destinationArchive
        }
    } finally {
        $destinationArchive.Dispose(); $outputStream.Dispose(); $jnicArchive.Dispose(); $baseArchive.Dispose()
    }
}

function Remove-StaleReleaseArtifacts([string[]]$Artifacts) {
    foreach ($artifact in $Artifacts) {
        if (Test-Path -LiteralPath $artifact -PathType Leaf) {
            Remove-Item -LiteralPath $artifact -Force
        }
    }
}

function New-ReleaseThemidaProject([string]$Source, [string]$Destination) {
    Require-File $Source "Themida project"
    $projectText = Get-Content -LiteralPath $Source -Raw -Encoding UTF8
    if ($projectText -notmatch '(?m)^[ \t]*OPTION_VIRTUAL_MACHINE_NUMBER[ \t]*=[ \t]*[1-9][0-9]*[ \t]*\r?$') {
        throw "Themida project must define at least one VM profile: $Source"
    }
    $compatibilitySettings = @(
        'OPTION_COMPRESSION_COMPRESS_RESOURCES=false',
        'OPTION_COMPRESSION_COMPRESS_APPLICATION=false',
        'OPTION_PROTECTION_IS_ANTIDEBUG=false',
        'OPTION_PROTECTION_IS_API_WRAPPER_ENABLED=false',
        'OPTION_PROTECTION_IS_FILE_REGISTRY_MONITORS=false',
        'OPTION_PROTECTION_IS_VMWARE_SUPPORT=false',
        'OPTION_MACROS_INTEGRITY_CHECKS=false'
    )
    foreach ($replacement in $compatibilitySettings) {
        $name = $replacement.Substring(0, $replacement.IndexOf('='))
        $pattern = '(?m)^' + [regex]::Escape($name) + '=.*$'
        if ($projectText -match $pattern) {
            $projectText = [regex]::Replace($projectText, $pattern, $replacement)
        } else {
            $projectText = $projectText.TrimEnd("`r", "`n") + [Environment]::NewLine + $replacement + [Environment]::NewLine
        }
    }
    [System.IO.File]::WriteAllText($Destination, $projectText, [System.Text.UTF8Encoding]::new($false))
    return $Destination
}

function Invoke-ThemidaProtection([string]$Protector, [string]$Project, [string]$InputDll, [string]$OutputDll) {
    Require-File $InputDll "Themida marker input DLL"
    if (Test-Path -LiteralPath $OutputDll -PathType Leaf) { Remove-Item -LiteralPath $OutputDll -Force }
    & $Protector $Project $InputDll $OutputDll
    if ($LASTEXITCODE -ne 0) { throw "Themida protector failed with exit code $LASTEXITCODE." }
    Require-File $OutputDll "Themida protected output DLL"
    if ((Get-FileHash -LiteralPath $InputDll -Algorithm SHA256).Hash -eq
            (Get-FileHash -LiteralPath $OutputDll -Algorithm SHA256).Hash) {
        throw "Themida output is byte-identical to its input."
    }
}

$java8Home = Resolve-JavaHome 8 @($env:JAVA8_HOME, (Join-Path $env:USERPROFILE ".jdks\corretto-1.8.0_502"), $env:JAVA_HOME)
$javaToolHome = Resolve-JavaHome 21 @($env:JAVA21_HOME, (Join-Path $env:USERPROFILE ".jdks\ms-21.0.12"), $env:JAVA_HOME)
$javaTool = Join-Path $javaToolHome "bin\java.exe"
$nekoHome = if ($env:YOZAKURA_NEKO_HOME) { $env:YOZAKURA_NEKO_HOME } else { "D:\obf\neko-obfuscator-main" }
$nekoCli = Join-Path $nekoHome "neko-cli\build\install\neko-cli\bin\neko-cli.bat"
$nekoJvmOpts = if ($env:YOZAKURA_NEKO_JVM_OPTS) { $env:YOZAKURA_NEKO_JVM_OPTS } else { '-Xmx10240m' }
$eskidTool = if ($env:YOZAKURA_ESKID_JAR) { $env:YOZAKURA_ESKID_JAR } else { "D:\obf\Eskid\build\libs\Eskid-0.42.jar" }

$jnicTool = if ($env:YOZAKURA_JNIC_JAR) { $env:YOZAKURA_JNIC_JAR } else { "D:\obf\jnic\jnic-3.7.0.jar" }
$themidaHome = if ($env:YOZAKURA_THEMIDA_HOME) { $env:YOZAKURA_THEMIDA_HOME } else { "D:\obf\Themida v3.1.8.0" }
$themidaProtector = if ($env:YOZAKURA_THEMIDA_PROTECTOR) { $env:YOZAKURA_THEMIDA_PROTECTOR } else { Join-Path $PSScriptRoot "Invoke-Themida.ps1" }
$themidaProject = if ($env:YOZAKURA_THEMIDA_PROJECT) { $env:YOZAKURA_THEMIDA_PROJECT } else { Join-Path $themidaHome "1.tmd" }

foreach ($required in @(
    @($nekoCli, 'Neko CLI'),
    @($eskidTool, 'Eskid tool'),
    @($jnicTool, 'JNIC tool'),
    @($nekoRules, 'Neko release rules'),
    @($eskidTemplate, 'Eskid release configuration'),
    @($jnicConfig, 'JNIC authentication configuration'),
    @((Join-Path $repoRoot 'gradlew.bat'), 'Gradle wrapper'),
    @((Join-Path $repoRoot 'build-native.bat'), 'native build script')
)) { Require-File $required[0] $required[1] }
if (-not (Get-ChildItem -LiteralPath (Split-Path -Parent $jnicTool) -Filter zig.exe -File -Recurse | Select-Object -First 1)) {
    throw "JNIC requires Zig below $(Split-Path -Parent $jnicTool)."
}
if (-not $SkipNative -and -not $skipThemida) {
    Require-File $themidaProtector "Themida protector wrapper"
    Require-File $themidaProject "Themida project"
    Require-File (Join-Path $themidaHome "Themida64.exe") "Themida x64 executable"
}

if ($PreflightOnly) {
    if (-not $SkipNative -and -not $skipThemida) {
        $preflightProject = [System.IO.Path]::GetTempFileName()
        try { $null = New-ReleaseThemidaProject $themidaProject $preflightProject }
        finally { Remove-Item -LiteralPath $preflightProject -Force -ErrorAction SilentlyContinue }
    }
    Write-Host "Yozakura Neko + Eskid + JNIC + x64 DLL + Themida release preflight passed."
    Write-Host "  Java 8:   $java8Home"
    Write-Host "  Tool JDK: $javaToolHome"
    Write-Host "  Neko CLI: $nekoCli"
    Write-Host "  Eskid:    $eskidTool"
    Write-Host "  JNIC:     $jnicTool"
    if (-not $SkipNative -and -not $skipThemida) {
        Write-Host "  Themida:  $(Join-Path $themidaHome 'Themida64.exe')"
        Write-Host "  Project:  $themidaProject"
    }
    Write-Host "  Profile:  $profileName"
    exit 0
}

Remove-StaleReleaseArtifacts @($releaseJar, $themidaInput, $themidaOutput)
if (-not $SkipGradle) {
    Write-Host "[1/9] Building a fresh Java 8 input and obfuscation classpath..."
    Push-Location $repoRoot
    $savedJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $javaToolHome
        & (Join-Path $repoRoot "gradlew.bat") --no-daemon --offline clean prepareObfuscation
        if ($LASTEXITCODE -ne 0) { throw "Gradle clean prepareObfuscation failed with exit code $LASTEXITCODE." }
    } finally { $env:JAVA_HOME = $savedJavaHome; Pop-Location }
} else {
    Write-Host "[1/9] Reusing the existing fresh Gradle input."
}
Require-File $inputJar "fresh Gradle obfuscation input"
Require-Directory $dependencyLibsDir "obfuscation dependency classpath"
Require-Directory $srgLibsDir "SRG Minecraft classpath"

if (Test-Path -LiteralPath $workDir) { Remove-Item -LiteralPath $workDir -Recurse -Force }
New-Item -ItemType Directory -Path $workDir -Force | Out-Null
$themidaBuildProject = $null
if (-not $SkipNative -and -not $skipThemida) {
    $themidaBuildProject = New-ReleaseThemidaProject $themidaProject (Join-Path $workDir "yozakura-release.tmd")
}

Write-Host "[2/9] Preparing clean Neko input and SRG-aligned classpath..."
New-NekoInputJar $inputJar $nekoInputJar
$java8LibDir = Join-Path $java8Home "jre\lib"
$classpath = @(Get-ChildItem -LiteralPath $java8LibDir -File -Filter '*.jar' |
    Sort-Object Name | ForEach-Object { $_.FullName })
$classpath += @(Get-ChildItem -LiteralPath (Join-Path $java8LibDir "ext") -File -Filter '*.jar' |
    Sort-Object Name | ForEach-Object { $_.FullName })
$classpath += @(Get-ChildItem -LiteralPath $srgLibsDir -File -Filter '*.jar' |
    Sort-Object Name | ForEach-Object { $_.FullName })
$classpath += @(Get-ChildItem -LiteralPath $dependencyLibsDir -File -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '(?i)(?:minecraft|forge).*mcp' } |
    Sort-Object Name | ForEach-Object { $_.FullName })
$configHeader = @(
    "input: '$(ConvertTo-YamlPath $nekoInputJar)'",
    "output: '$(ConvertTo-YamlPath $nekoJar)'",
    'classpath:'
)
$configHeader += ($classpath | Select-Object -Unique) | ForEach-Object { "  - '$(ConvertTo-YamlPath $_)'" }
$configText = ($configHeader -join [Environment]::NewLine) + [Environment]::NewLine + [Environment]::NewLine +
    (Get-Content -LiteralPath $nekoRules -Raw -Encoding UTF8)
[System.IO.File]::WriteAllText($nekoConfig, $configText, [System.Text.UTF8Encoding]::new($false))
@(
    "Neko CLI: $nekoCli",
    "Neko CLI SHA-256: $((Get-FileHash -LiteralPath $nekoCli -Algorithm SHA256).Hash.ToLowerInvariant())",
    "Eskid JAR: $eskidTool",
    "Eskid SHA-256: $((Get-FileHash -LiteralPath $eskidTool -Algorithm SHA256).Hash.ToLowerInvariant())",
    "JNIC JAR: $jnicTool",
    "JNIC SHA-256: $((Get-FileHash -LiteralPath $jnicTool -Algorithm SHA256).Hash.ToLowerInvariant())"
) | Set-Content -LiteralPath $toolHashes -Encoding UTF8

Write-Host "[3/9] Running Neko class/member renaming with CFF disabled..."
$savedJavaHome = $env:JAVA_HOME
$savedNekoOpts = $env:NEKO_CLI_OPTS
try {
    $env:JAVA_HOME = $javaToolHome
    $env:NEKO_CLI_OPTS = $nekoJvmOpts
    & $nekoCli obfuscate --config $nekoConfig --input $nekoInputJar --output $nekoJar 2>&1 |
        Tee-Object -FilePath $nekoLog
    if ($LASTEXITCODE -ne 0) { throw "Neko CLI failed with exit code $LASTEXITCODE. See $nekoLog" }
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:NEKO_CLI_OPTS = $savedNekoOpts
}
Require-File $nekoJar "Neko output JAR"
Require-File $nekoMapping "Neko mapping"
if ((Get-Item -LiteralPath $nekoMapping).Length -eq 0) { throw "Neko mapping is empty: $nekoMapping" }
Merge-NekoOutputWithStableThirdParty $nekoJar $inputJar $nekoMergedJar

Write-Host "[4/9] Running Eskid string/number hardening on the renamed application boundary (flow disabled for Forge compatibility)..."
New-EskidApplicationBoundaryJar $nekoMergedJar $eskidBoundaryInput
New-EskidStableAbiSupportJar $nekoMergedJar $eskidSupportJar
$eskidClasspath = @($eskidSupportJar) + @($classpath | Select-Object -Unique)
$eskidText = Get-Content -LiteralPath $eskidTemplate -Raw -Encoding UTF8
$eskidText = $eskidText.Replace('__INPUT__', $eskidBoundaryInput.Replace('\', '\\'))
$eskidText = $eskidText.Replace('__OUTPUT__', $eskidBoundaryOutput.Replace('\', '\\'))
$eskidText = $eskidText.Replace('"__LIBRARIES__"', (($eskidClasspath | ForEach-Object {
    '"' + $_.Replace('\', '\\') + '"'
}) -join ','))
[System.IO.File]::WriteAllText($eskidConfig, $eskidText, [System.Text.UTF8Encoding]::new($false))
if (Test-Path -LiteralPath $eskidBoundaryOutput -PathType Leaf) { Remove-Item -LiteralPath $eskidBoundaryOutput -Force }
& $javaTool -Xmx4096m -jar $eskidTool $eskidConfig 2>&1 | Tee-Object -FilePath $eskidLog
if ($LASTEXITCODE -ne 0) { throw "Eskid failed with exit code $LASTEXITCODE. See $eskidLog" }
Require-File $eskidBoundaryOutput "Eskid application boundary output"
if ((Get-FileHash -LiteralPath $eskidBoundaryInput -Algorithm SHA256).Hash -eq
        (Get-FileHash -LiteralPath $eskidBoundaryOutput -Algorithm SHA256).Hash) {
    throw "Eskid output is byte-identical to its input."
}
$eskidLogText = Get-Content -LiteralPath $eskidLog -Raw -ErrorAction SilentlyContinue
if ($eskidLogText -notmatch 'Encrypted\s+[1-9][0-9]*\s+strings' -or
        $eskidLogText -notmatch 'Obfuscated\s+[1-9][0-9]*\s+numbers') {
    throw "Eskid did not report effective string and number transformations. See $eskidLog"
}
if ($eskidLogText -match 'Warning:\s+Missing class') {
    $missingPreview = @([regex]::Matches($eskidLogText, 'Warning:\s+Missing class[^\r\n]*') |
        ForEach-Object { $_.Value } | Select-Object -Unique -First 10) -join [Environment]::NewLine
    throw "Eskid reported unresolved application hierarchy contracts:`n$missingPreview"
}
Merge-EskidApplicationBoundary $nekoMergedJar $eskidBoundaryOutput $eskidJar
Copy-Item -LiteralPath $eskidJar -Destination $jnicInputJar -Force

Write-Host "[5/9] Running JNIC for the expanded authentication boundary..."
$jnicBoundaryInput = Join-Path $workDir "Yozakura-jnic-auth-boundary.jar"
New-JnicAuthenticationBoundaryJar $jnicInputJar $jnicBoundaryInput
Push-Location (Split-Path -Parent $jnicTool)
try {
    & $javaTool -jar $jnicTool $jnicBoundaryInput $jnicRawOutput $jnicConfig 2>&1 | Tee-Object -FilePath $jnicLog
    if ($LASTEXITCODE -ne 0) { throw "JNIC failed with exit code $LASTEXITCODE. See $jnicLog" }
} finally { Pop-Location }
Require-File $jnicRawOutput "JNIC raw output"
Merge-JnicAuthenticationBoundary $jnicInputJar $jnicRawOutput $jnicJarOutput
& (Join-Path $PSScriptRoot "Verify-ObfuscatedJar.ps1") -InputJar $inputJar -NekoJar $nekoJar `
    -EskidJar $eskidJar -Jar $jnicJarOutput -JavaHome $java8Home -NekoMapping $nekoMapping `
    -EskidLog $eskidLog -Report (Join-Path $workDir "verification.txt")
if ($LASTEXITCODE -ne 0) { throw "Neko + Eskid + JNIC JAR verification failed." }
New-Item -ItemType Directory -Path (Split-Path -Parent $releaseJar) -Force | Out-Null
Copy-Item -LiteralPath $jnicJarOutput -Destination $releaseJar -Force

if (-not $SkipNative) {
    Write-Host "[6/9] Building the x64 loader DLL with the protected JAR embedded..."
    $saved = @{
        Runtime = $env:YOZAKURA_RUNTIME_JAR
        Input = $env:YOZAKURA_OBFUSCATION_INPUT_JAR
        Intermediate = $env:YOZAKURA_INTERMEDIATE_JAR
        EskidIntermediate = $env:YOZAKURA_ESKID_INTERMEDIATE_JAR
        EskidLog = $env:YOZAKURA_ESKID_LOG
        Mapping = $env:YOZAKURA_NEKO_MAPPING
        Java8 = $env:JAVA8_HOME
        Markers = $env:YOZAKURA_THEMIDA_MARKERS
    }
    try {
        $env:YOZAKURA_RUNTIME_JAR = $releaseJar
        $env:YOZAKURA_OBFUSCATION_INPUT_JAR = $inputJar
        $env:YOZAKURA_INTERMEDIATE_JAR = $nekoJar
        $env:YOZAKURA_ESKID_INTERMEDIATE_JAR = $eskidJar
        $env:YOZAKURA_ESKID_LOG = $eskidLog
        $env:YOZAKURA_NEKO_MAPPING = $nekoMapping
        $env:JAVA8_HOME = $java8Home
        if ($skipThemida) { Remove-Item Env:YOZAKURA_THEMIDA_MARKERS -ErrorAction SilentlyContinue }
        else { $env:YOZAKURA_THEMIDA_MARKERS = '1' }
        & (Join-Path $repoRoot "build-native.bat")
        if ($LASTEXITCODE -ne 0) { throw "build-native.bat failed with exit code $LASTEXITCODE." }
        Write-Host "[7/9] Re-verifying the final named release JAR after native asset packaging..."
        & (Join-Path $PSScriptRoot "Verify-ObfuscatedJar.ps1") -InputJar $inputJar -NekoJar $nekoJar `
            -EskidJar $eskidJar -Jar $releaseJar -JavaHome $java8Home -NekoMapping $nekoMapping -EskidLog $eskidLog
        if ($LASTEXITCODE -ne 0) { throw "Final named release JAR verification failed." }
    } finally {
        $env:YOZAKURA_RUNTIME_JAR = $saved.Runtime
        $env:YOZAKURA_OBFUSCATION_INPUT_JAR = $saved.Input
        $env:YOZAKURA_INTERMEDIATE_JAR = $saved.Intermediate
        $env:YOZAKURA_ESKID_INTERMEDIATE_JAR = $saved.EskidIntermediate
        $env:YOZAKURA_ESKID_LOG = $saved.EskidLog
        $env:YOZAKURA_NEKO_MAPPING = $saved.Mapping
        $env:JAVA8_HOME = $saved.Java8
        $env:YOZAKURA_THEMIDA_MARKERS = $saved.Markers
    }
    if (-not $skipThemida) {
        Write-Host "[8/9] Protecting the x64 loader with Themida..."
        Invoke-ThemidaProtection $themidaProtector $themidaBuildProject $themidaInput $themidaOutput
    } else {
        Write-Host "[8/9] Themida skipped by explicit no-themida mode."
    }
    Write-Host "[9/9] Verifying the embedded x64 native payload..."
    $embeddedJar = Join-Path $repoRoot "build\libs\Yozakura.jar"
    & (Join-Path $PSScriptRoot "Verify-NativePayload.ps1") -Dll $themidaOutput -Jar $releaseJar `
        -EmbeddedJarSource $embeddedJar -Report $verificationReport
    if ($LASTEXITCODE -ne 0) { throw "Native payload verification failed." }
} else {
    Write-Host "[6/9] Native DLL build skipped by request."
    Write-Host "[7/9] Final post-native JAR verification skipped."
    Write-Host "[8/9] Themida protection skipped with the native build."
    Write-Host "[9/9] Native payload verification skipped."
}

Write-Host ""
Write-Host "Release artifacts:"
foreach ($artifact in @($releaseJar, (Join-Path $repoRoot "build\libs\YozakuraLoader-x64.dll"))) {
    if (Test-Path -LiteralPath $artifact -PathType Leaf) {
        $item = Get-Item -LiteralPath $artifact
        $hash = Get-FileHash -LiteralPath $artifact -Algorithm SHA256
        Write-Host "  $artifact ($($item.Length) bytes)"
        Write-Host "  SHA-256: $($hash.Hash.ToLowerInvariant())"
    }
}
exit 0
