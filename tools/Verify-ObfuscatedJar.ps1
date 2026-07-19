param(
    [Parameter(Mandatory = $true)]
    [string]$InputJar,

    [Parameter(Mandatory = $true)]
    [string]$ZkmJar,

    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome,

    [string]$Report
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw "Obfuscation verification failed: $Message"
}

function Get-JarEntries([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        return @($archive.Entries | ForEach-Object { $_.FullName })
    } finally {
        $archive.Dispose()
    }
}

function Get-JarEntryText([string]$Path, [string]$EntryName) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $archive.GetEntry($EntryName)
        if ($null -eq $entry) {
            Fail "required text entry is missing: $EntryName"
        }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

function Get-ClassMajorVersions([string]$Path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $versions = @{}
        foreach ($entry in $archive.Entries) {
            if (-not $entry.FullName.EndsWith('.class', [StringComparison]::Ordinal)) {
                continue
            }
            $stream = $entry.Open()
            try {
                $header = New-Object byte[] 8
                if ($stream.Read($header, 0, $header.Length) -ne $header.Length -or
                        $header[0] -ne 0xCA -or $header[1] -ne 0xFE -or
                        $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
                    Fail "invalid class header: $($entry.FullName)"
                }
                $versions[$entry.FullName] = ([int]$header[6] -shl 8) -bor [int]$header[7]
            } finally {
                $stream.Dispose()
            }
        }
        return $versions
    } finally {
        $archive.Dispose()
    }
}

function Test-JarContainsEntryContent([string]$SourceJar, [string]$EntryName, [string]$TargetJar) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($SourceJar)
        try {
            $sourceEntry = $sourceArchive.GetEntry($EntryName)
            $sourceStream = $sourceEntry.Open()
            try {
                $expected = [Convert]::ToBase64String($sha256.ComputeHash($sourceStream))
            } finally {
                $sourceStream.Dispose()
            }
        } finally {
            $sourceArchive.Dispose()
        }
        $targetArchive = [System.IO.Compression.ZipFile]::OpenRead($TargetJar)
        try {
            foreach ($entry in $targetArchive.Entries) {
                if ($entry.FullName.EndsWith('/') -or $entry.FullName.EndsWith('.class')) {
                    continue
                }
                $stream = $entry.Open()
                try {
                    if ([Convert]::ToBase64String($sha256.ComputeHash($stream)) -eq $expected) {
                        return $true
                    }
                } finally {
                    $stream.Dispose()
                }
            }
        } finally {
            $targetArchive.Dispose()
        }
        return $false
    } finally {
        $sha256.Dispose()
    }
}

$InputJar = (Resolve-Path $InputJar).Path
$ZkmJar = (Resolve-Path $ZkmJar).Path
$Jar = (Resolve-Path $Jar).Path
$javap = Join-Path $JavaHome "bin\javap.exe"
if (-not (Test-Path $javap -PathType Leaf)) {
    Fail "javap.exe was not found under $JavaHome"
}

$inputHash = (Get-FileHash -Algorithm SHA256 $InputJar).Hash.ToLowerInvariant()
$outputHash = (Get-FileHash -Algorithm SHA256 $Jar).Hash.ToLowerInvariant()
if ($inputHash -eq $outputHash) {
    Fail "the final JAR is byte-for-byte identical to the input"
}

$entries = Get-JarEntries $Jar
$zkmEntries = Get-JarEntries $ZkmJar
$inputEntries = Get-JarEntries $InputJar
$seenEntries = New-Object 'System.Collections.Generic.HashSet[string]' -ArgumentList ([System.StringComparer]::Ordinal)
$duplicateEntry = $null
foreach ($entry in $entries) {
    if (-not $seenEntries.Add($entry)) {
        $duplicateEntry = $entry
        break
    }
}
if ($null -ne $duplicateEntry) {
    Fail "the final JAR contains a duplicate ZIP entry: $duplicateEntry"
}
$required = @(
    "META-INF/MANIFEST.MF",
    "META-INF/yozakura_at.cfg",
    "mcmod.info",
    "gq/yozakura/YozakuraAttachPoint.class",
    "gq/yozakura/YozakuraBootstrap.class",
    "gq/yozakura/Yozakuraloader.class",
    "gq/yozakura/auth/NativeAuthBridge.class",
    "gq/yozakura/auth/YozakuraAuthGate.class",
    "gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.class",
    "gq/yozakura/core/Client.class",
    "gq/yozakura/core/StandaloneClient.class",
    "gq/yozakura/core/ModernForgeClient.class",
    "gq/yozakura/module/Module.class",
    "gq/yozakura/event/bus/EventManager.class",
    "gq/yozakura/event/api/EventManager.class",
    "gq/yozakura/bridge/MovementInputBridge.class",
    "gq/yozakura/ui/click/yozakura/YozakuraClickGui.class"
)
foreach ($entry in $required) {
    if ($entries -cnotcontains $entry) {
        Fail "required entry is missing: $entry"
    }
    if ($zkmEntries -cnotcontains $entry) {
        Fail "ZKM renamed or removed a required contract entry: $entry"
    }
}

$inputResources = @($inputEntries | Where-Object {
    -not $_.EndsWith('/') -and -not $_.EndsWith('.class') -and
    $_ -notmatch '(?i)^META-INF/.+\.(SF|RSA|DSA)$'
})
$missingResources = @($inputResources | Where-Object {
    $entries -cnotcontains $_ -and -not (Test-JarContainsEntryContent $InputJar $_ $Jar)
})
if ($missingResources.Count -ne 0) {
    Fail "a non-class resource and its content were lost during transformation: $($missingResources[0])"
}

$manifestText = Get-JarEntryText $Jar "META-INF/MANIFEST.MF"
if ($manifestText -notmatch '(?m)^Agent-Class:\s*gq\.yozakura\.YozakuraAttachPoint\s*$') {
    Fail "the Agent-Class manifest contract is missing or renamed"
}
if ($manifestText -notmatch '(?m)^FMLAT:\s*yozakura_at\.cfg\s*$') {
    Fail "the Forge access-transformer manifest contract is missing"
}
$contractClasses = @(
    "gq/yozakura/YozakuraAttachPoint.class",
    "gq/yozakura/YozakuraBootstrap.class",
    "gq/yozakura/Yozakuraloader.class",
    "gq/yozakura/auth/NativeAuthBridge.class",
    "gq/yozakura/auth/YozakuraAuthGate.class",
    "gq/yozakura/auth/vendor/tech/skidonion/obfuscator/inline/Wrapper.class",
    "gq/yozakura/core/Client.class",
    "gq/yozakura/core/StandaloneClient.class",
    "gq/yozakura/core/ModernForgeClient.class",
    "gq/yozakura/module/Module.class",
    "gq/yozakura/event/bus/EventManager.class",
    "gq/yozakura/event/api/EventManager.class",
    "gq/yozakura/bridge/MovementInputBridge.class",
    "gq/yozakura/ui/click/yozakura/YozakuraClickGui.class"
)
$renameCandidates = @($inputEntries | Where-Object {
    $_ -match '^gq/yozakura/.+\.class$' -and $contractClasses -cnotcontains $_
})
$renamedCount = @($renameCandidates | Where-Object { $zkmEntries -cnotcontains $_ }).Count
if ($renamedCount -lt 10) {
    Fail "ZKM rename evidence is too weak: only $renamedCount original Yozakura class names disappeared"
}

if (@($entries | Where-Object { $_ -match '^myj2c/' }).Count -ne 0) {
    Fail "a MyJ2C payload remains in the JNIC release"
}
if (@($entries | Where-Object { $_ -match '^dev/jnic/' }).Count -eq 0) {
    Fail "the JNIC runtime classes are missing"
}
if (@($zkmEntries | Where-Object { $_ -match '^dev/jnic/' }).Count -ne 0) {
    Fail "the ZKM intermediate unexpectedly contains a JNIC payload"
}
if (@($entries | Where-Object { $_ -match '^dev/jnic/lib/.+\.dat$' }).Count -eq 0) {
    Fail "the JNIC Windows native payload is missing"
}
if (@($entries | Where-Object { $_ -match '(?i)^META-INF/.+\.(SF|RSA|DSA)$' }).Count -ne 0) {
    Fail "stale JAR signature files remain after transformation"
}

$zkmGate = & $javap -classpath $ZkmJar -p gq.yozakura.auth.YozakuraAuthGate 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "javap could not inspect the ZKM YozakuraAuthGate: $($zkmGate -join ' ')"
}
if (@($zkmGate | Where-Object { $_ -match '\bnative\b' }).Count -ne 0) {
    Fail "YozakuraAuthGate was already native before JNIC; the pipeline input is not clean"
}

$manifest = & $javap -classpath $Jar -p gq.yozakura.auth.YozakuraAuthGate 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "javap could not inspect YozakuraAuthGate: $($manifest -join ' ')"
}
$nativeCount = @($manifest | Where-Object { $_ -match '\bnative\b' }).Count
if ($nativeCount -lt 3) {
    Fail "YozakuraAuthGate exposes only $nativeCount native methods; expected at least 3"
}
foreach ($method in @("getVerifiedUsername", "allowRuntime", "requireRuntime")) {
    if (-not ($manifest -match "native .+\b$method\(")) {
        Fail "JNIC did not translate the authentication method: $method"
    }
}
foreach ($method in @("verifyOrThrow", "showVerification")) {
    if ($manifest -match "native .+\b$method\(") {
        Fail "JNIC translated the UI authentication method and would prevent the verification window: $method"
    }
}

$bridge = & $javap -classpath $Jar -p gq.yozakura.auth.NativeAuthBridge 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "javap could not inspect NativeAuthBridge: $($bridge -join ' ')"
}
foreach ($method in @("q0", "q1", "login0", "redeemLicense0", "logout0", "username0", "role0", "expiry0")) {
    if (-not ($bridge -match "\b$method\(")) {
        Fail "JNI registration contract was renamed or removed: $method"
    }
}

$verbose = & $javap -classpath $Jar -verbose gq.yozakura.auth.YozakuraAuthGate 2>&1
if ($LASTEXITCODE -ne 0 -or -not ($verbose -match 'major version:\s+52')) {
    Fail "YozakuraAuthGate is not Java 8 bytecode (major version 52)"
}

$majorVersions = Get-ClassMajorVersions $Jar
$newerClass = $majorVersions.GetEnumerator() | Where-Object { $_.Value -gt 52 } | Select-Object -First 1
if ($null -ne $newerClass) {
    Fail "class $($newerClass.Key) requires bytecode major $($newerClass.Value), newer than Java 8"
}

$lines = @(
    "Yozakura obfuscation verification: PASS",
    "Input SHA-256:  $inputHash",
    "Output SHA-256: $outputHash",
    "JNIC native guard methods in YozakuraAuthGate: $nativeCount",
    "Yozakura classes renamed by ZKM: $renamedCount",
    "JAR entries: $($entries.Count)"
    "Non-class resources retained: $($inputResources.Count)"
    "Maximum class major version: $((($majorVersions.Values | Measure-Object -Maximum).Maximum))"
)
$lines | ForEach-Object { Write-Host $_ }
if ($Report) {
    $reportDir = Split-Path -Parent $Report
    if ($reportDir) {
        New-Item -ItemType Directory -Force $reportDir | Out-Null
    }
    $lines | Set-Content -Encoding UTF8 $Report
}
