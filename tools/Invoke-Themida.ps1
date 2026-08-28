param(
    [Parameter(Mandatory = $true)]
    [string]$Project,

    [Parameter(Mandatory = $true)]
    [string]$InputDll,

    [Parameter(Mandatory = $true)]
    [string]$OutputDll
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$themidaHome = if ($env:YOZAKURA_THEMIDA_HOME) {
    $env:YOZAKURA_THEMIDA_HOME
} else {
    "D:\obf\Themida v3.1.8.0"
}
$themidaExecutable = Join-Path $themidaHome "Themida64.exe"

foreach ($required in @(
    @($themidaExecutable, "Themida x64 executable"),
    @($Project, "Themida project"),
    @($InputDll, "Themida marker input DLL")
)) {
    if (-not (Test-Path -LiteralPath $required[0] -PathType Leaf)) {
        throw "$($required[1]) was not found: $($required[0])"
    }
}

$resolvedProject = (Resolve-Path -LiteralPath $Project).Path
$resolvedInput = (Resolve-Path -LiteralPath $InputDll).Path
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDll)
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
if (Test-Path -LiteralPath $resolvedOutput -PathType Leaf) {
    Remove-Item -LiteralPath $resolvedOutput -Force
}

Push-Location $themidaHome
try {
    $arguments = @(
        '/protect', $resolvedProject,
        '/inputfile', $resolvedInput,
        '/outputfile', $resolvedOutput,
        '/q'
    )
    $process = Start-Process -FilePath $themidaExecutable -ArgumentList $arguments `
        -WorkingDirectory $themidaHome -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Themida failed with exit code $($process.ExitCode)."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $resolvedOutput -PathType Leaf)) {
    throw "Themida did not create the protected output DLL: $resolvedOutput"
}
