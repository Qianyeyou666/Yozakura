$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "build\libs"
$package = Join-Path $sourceDir "YozakuraStandalone.exe"
$testRoot = Join-Path $repoRoot "build\native\standalone-test"
$testDir = Join-Path $testRoot ([Guid]::NewGuid().ToString("N"))
$payloadNames = @(
    "YozakuraLoader.dll",
    "YozakuraInjector.exe",
    "minecraft_cherry_block.png",
    "minecraft_furnace_block.png",
    "minecraft_grass_block.png",
    "yozakura_logo.png"
)

if (-not (Test-Path -LiteralPath $package -PathType Leaf)) {
    throw "Standalone package does not exist: $package"
}

New-Item -ItemType Directory -Path $testDir -Force | Out-Null
try {
    $process = Start-Process -FilePath $package `
        -ArgumentList @("--extract-only", "`"$testDir`"") `
        -WindowStyle Hidden `
        -Wait `
        -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Standalone extraction returned exit code $($process.ExitCode)."
    }

    foreach ($name in $payloadNames) {
        $source = Join-Path $sourceDir $name
        $extracted = Join-Path $testDir $name
        if (-not (Test-Path -LiteralPath $extracted -PathType Leaf)) {
            throw "Missing extracted payload: $name"
        }

        $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash
        $extractedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $extracted).Hash
        if ($sourceHash -ne $extractedHash) {
            throw "Extracted payload does not match its source: $name"
        }
    }

    $secondExtraction = Start-Process -FilePath $package `
        -ArgumentList @("--extract-only", "`"$testDir`"") `
        -WindowStyle Hidden `
        -Wait `
        -PassThru
    if ($secondExtraction.ExitCode -eq 0) {
        throw "Standalone extraction overwrote an existing payload directory."
    }

    Write-Host "[OK] Embedded payloads match byte-for-byte and existing files are not overwritten."
}
finally {
    if (Test-Path -LiteralPath $testDir) {
        Remove-Item -LiteralPath $testDir -Recurse -Force
    }
}
