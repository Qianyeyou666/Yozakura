param(
    [Parameter(Mandatory = $true)]
    [string]$InputJar,

    [Parameter(Mandatory = $true)]
    [string]$NekoJar,

    [Parameter(Mandatory = $true)]
    [string]$EskidJar,

    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome,

    [Parameter(Mandatory = $true)]
    [string]$NekoMapping,

    [string]$EskidLog,

    [string]$Report
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Fail([string]$Message) {
    throw "Obfuscation verification failed: $Message"
}

function Require-File([string]$Path, [string]$Description) {
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "$Description was not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Get-JarEntries([string]$Path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try { return @($archive.Entries | ForEach-Object { $_.FullName }) }
    finally { $archive.Dispose() }
}

function Get-JarEntryText([string]$Path, [string]$EntryName) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $archive.GetEntry($EntryName)
        if ($null -eq $entry) { Fail "required text entry is missing: $EntryName" }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}

function Get-ClassMajorVersions([string]$Path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $versions = @{}
        foreach ($entry in $archive.Entries) {
            if (-not $entry.FullName.EndsWith('.class', [StringComparison]::Ordinal)) { continue }
            $stream = $entry.Open()
            try {
                $header = New-Object byte[] 8
                if ($stream.Read($header, 0, $header.Length) -ne $header.Length -or
                        $header[0] -ne 0xCA -or $header[1] -ne 0xFE -or
                        $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
                    Fail "invalid class header: $($entry.FullName)"
                }
                $versions[$entry.FullName] = ([int]$header[6] -shl 8) -bor [int]$header[7]
            } finally { $stream.Dispose() }
        }
        return $versions
    } finally { $archive.Dispose() }
}

function Test-JarContainsEntryContent([string]$SourceJar, [string]$EntryName, [string]$TargetJar) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($SourceJar)
        try {
            $sourceEntry = $sourceArchive.GetEntry($EntryName)
            if ($null -eq $sourceEntry) { return $false }
            $sourceStream = $sourceEntry.Open()
            try { $expected = [Convert]::ToBase64String($sha256.ComputeHash($sourceStream)) }
            finally { $sourceStream.Dispose() }
        } finally { $sourceArchive.Dispose() }
        $targetArchive = [System.IO.Compression.ZipFile]::OpenRead($TargetJar)
        try {
            foreach ($entry in $targetArchive.Entries) {
                if ($entry.FullName.EndsWith('/') -or $entry.FullName.EndsWith('.class')) { continue }
                $stream = $entry.Open()
                try {
                    if ([Convert]::ToBase64String($sha256.ComputeHash($stream)) -eq $expected) { return $true }
                } finally { $stream.Dispose() }
            }
        } finally { $targetArchive.Dispose() }
        return $false
    } finally { $sha256.Dispose() }
}

$InputJar = Require-File $InputJar "fresh Gradle input JAR"
$NekoJar = Require-File $NekoJar "Neko intermediate JAR"
$EskidJar = Require-File $EskidJar "Eskid intermediate JAR"
$Jar = Require-File $Jar "final protected JAR"
$NekoMapping = Require-File $NekoMapping "Neko mapping"
if ((Get-Item -LiteralPath $NekoMapping).Length -eq 0) { Fail "Neko mapping is empty" }
$javap = Join-Path $JavaHome "bin\javap.exe"
if (-not (Test-Path -LiteralPath $javap -PathType Leaf)) { Fail "javap.exe was not found under $JavaHome" }

$inputHash = (Get-FileHash -LiteralPath $InputJar -Algorithm SHA256).Hash.ToLowerInvariant()
$nekoHash = (Get-FileHash -LiteralPath $NekoJar -Algorithm SHA256).Hash.ToLowerInvariant()
$eskidHash = (Get-FileHash -LiteralPath $EskidJar -Algorithm SHA256).Hash.ToLowerInvariant()
$outputHash = (Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($inputHash -eq $nekoHash) { Fail "the Neko intermediate is byte-for-byte identical to the clean input" }
if ($nekoHash -eq $eskidHash) { Fail "the Eskid intermediate is byte-for-byte identical to the Neko intermediate" }
if ($inputHash -eq $outputHash) { Fail "the final JAR is byte-for-byte identical to the clean input" }

$inputEntries = Get-JarEntries $InputJar
$nekoEntries = Get-JarEntries $NekoJar
$eskidEntries = Get-JarEntries $EskidJar
$entries = Get-JarEntries $Jar
foreach ($artifact in @(
    @{ Name = 'Neko intermediate'; Entries = $nekoEntries },
    @{ Name = 'Eskid intermediate'; Entries = $eskidEntries },
    @{ Name = 'final JAR'; Entries = $entries }
)) {
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' -ArgumentList ([System.StringComparer]::Ordinal)
    foreach ($entry in $artifact.Entries) {
        if (-not $seen.Add($entry)) { Fail "$($artifact.Name) contains a duplicate ZIP entry: $entry" }
    }
}

$required = @(
    'META-INF/MANIFEST.MF',
    'META-INF/yozakura_at.cfg',
    'mcmod.info',
    'gq/yozakura/YozakuraAttachPoint.class',
    'gq/yozakura/YozakuraBootstrap.class',
    'gq/yozakura/Yozakuraloader.class',
    'gq/yozakura/k/A.class',
    'gq/yozakura/k/A$1.class',
    'gq/yozakura/k/B.class',
    'gq/yozakura/k/B$1.class',
    'gq/yozakura/k/B$1$1.class',
    'gq/yozakura/k/B$2.class',
    'gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.class',
    'gq/yozakura/core/Client.class',
    'gq/yozakura/core/StandaloneClient.class',
    'gq/yozakura/core/ModernForgeClient.class',
    'gq/yozakura/module/Module.class',
    'gq/yozakura/event/bus/EventManager.class',
    'gq/yozakura/bridge/MovementInputBridge.class',
    'gq/yozakura/ui/click/web/WebView2Bridge.class',
    'gq/yozakura/ui/click/yozakura/PanelClickGuiWindowsAlphaCursor.class',
    'gq/yozakura/ui/click/material/MaterialClickGui.class',
    'gq/yozakura/ui/click/sakura/SakuraClickGui.class',
    'gq/yozakura/ui/click/yozakura/YozakuraClickGui.class'
)
foreach ($entry in $required) {
    if ($nekoEntries -cnotcontains $entry) { Fail "Neko renamed or removed a required ABI entry: $entry" }
    if ($eskidEntries -cnotcontains $entry) { Fail "Eskid changed or removed a required ABI entry: $entry" }
    if ($entries -cnotcontains $entry) { Fail "the final JAR is missing required ABI entry: $entry" }
}
if ($entries -ccontains 'gq/yozakura/event/api/EventManager.class' -or
        $eskidEntries -ccontains 'gq/yozakura/event/api/EventManager.class' -or
        $nekoEntries -ccontains 'gq/yozakura/event/api/EventManager.class') {
    Fail "the retired duplicate event manager is present"
}

$mappingLines = @(Get-Content -LiteralPath $NekoMapping | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$classMappings = @($mappingLines | Where-Object { $_.StartsWith('CLASS ') })
$methodMappings = @($mappingLines | Where-Object { $_.StartsWith('METHOD ') })
if ($classMappings.Count -lt 50) { Fail "Neko mapping contains only $($classMappings.Count) CLASS entries" }
if ($methodMappings.Count -lt 1) { Fail "Neko mapping contains no METHOD entries" }
foreach ($requiredMapping in @(
    'gq/yozakura/k/t/F',
    'gq/yozakura/k/t/G',
    'gq/yozakura/k/t/H',
    'gq/yozakura/k/t/I',
    'gq/yozakura/k/vendor/skidonion/sWdSl/D',
    'gq/yozakura/k/vendor/skidonion/sWdSl/E',
    'gq/yozakura/module/ModuleType'
)) {
    if (-not ($classMappings | Where-Object { $_ -like "CLASS $requiredMapping -> *" } | Select-Object -First 1)) {
        Fail "Neko mapping is missing the required class rename: $requiredMapping"
    }
}

$protectedNamespaceEntries = @($nekoEntries | Where-Object { $_ -match '^n/.+\.class$' })
if ($protectedNamespaceEntries.Count -lt 50) {
    Fail "the Neko intermediate contains only $($protectedNamespaceEntries.Count) classes under n/**"
}
$contractClasses = @($required | Where-Object { $_.EndsWith('.class') })
$renameCandidates = @($inputEntries | Where-Object {
    $_ -match '^gq/yozakura/.+\.class$' -and $contractClasses -cnotcontains $_
})
$renamedCount = @($renameCandidates | Where-Object { $nekoEntries -cnotcontains $_ }).Count
if ($renamedCount -lt 50) {
    Fail "Neko rename evidence is too weak: only $renamedCount original Yozakura class names disappeared"
}

$inputResources = @($inputEntries | Where-Object {
    -not $_.EndsWith('/') -and -not $_.EndsWith('.class') -and
    $_ -notmatch '(?i)^META-INF/.+\.(SF|RSA|DSA)$'
})
$missingResources = @($inputResources | Where-Object {
    $entries -cnotcontains $_ -and -not (Test-JarContainsEntryContent $InputJar $_ $Jar)
})
if ($missingResources.Count -ne 0) { Fail "a non-class resource and its content were lost: $($missingResources[0])" }

$manifestText = Get-JarEntryText $Jar 'META-INF/MANIFEST.MF'
if ($manifestText -notmatch '(?m)^Agent-Class:\s*gq\.yozakura\.YozakuraAttachPoint\s*$') {
    Fail "the Agent-Class manifest contract is missing or renamed"
}
if ($manifestText -notmatch '(?m)^FMLAT:\s*yozakura_at\.cfg\s*$') {
    Fail "the Forge access-transformer manifest contract is missing"
}

if (@($entries | Where-Object { $_ -match '^myj2c/' }).Count -ne 0) { Fail "a MyJ2C payload remains in the final release" }
if (@($nekoEntries | Where-Object { $_ -match '^dev/jnic/' }).Count -ne 0) {
    Fail "the Neko intermediate unexpectedly contains a JNIC payload"
}
if (@($eskidEntries | Where-Object { $_ -match '^dev/jnic/' }).Count -ne 0) {
    Fail "the Eskid intermediate unexpectedly contains a JNIC payload"
}
if (-not [string]::IsNullOrWhiteSpace($EskidLog)) {
    $EskidLog = Require-File $EskidLog "Eskid log"
    $eskidLogText = Get-Content -LiteralPath $EskidLog -Raw
    if ($eskidLogText -notmatch 'Encrypted\s+([1-9][0-9]*)\s+strings') {
        Fail "Eskid did not report encrypted strings"
    }
    if ($eskidLogText -notmatch 'Obfuscated\s+([1-9][0-9]*)\s+numbers') {
        Fail "Eskid did not report obfuscated numbers"
    }
    if ($eskidLogText -match 'Warning:\s+Missing class') {
        Fail "Eskid reported unresolved application hierarchy contracts"
    }
}
if (@($entries | Where-Object { $_ -match '^dev/jnic/' }).Count -eq 0 -or
        @($entries | Where-Object { $_ -match '^dev/jnic/lib/.+\.dat$' }).Count -eq 0) {
    Fail "the JNIC Windows runtime or native payload is missing"
}
if (@($entries | Where-Object { $_ -match '(?i)^META-INF/.+\.(SF|RSA|DSA)$' }).Count -ne 0) {
    Fail "stale JAR signature files remain after transformation"
}

$nekoGate = & $javap -classpath $NekoJar -p gq.yozakura.k.B 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $nekoGate -match '\bnative\b') {
    Fail "B is missing from the clean Neko boundary or was already native before JNIC"
}
$eskidGate = & $javap -classpath $EskidJar -p gq.yozakura.k.B 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $eskidGate -match '\bnative\b') {
    Fail "B is missing from the Eskid intermediate or was already native before JNIC"
}
$gate = & $javap -classpath $Jar -p gq.yozakura.k.B 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Fail "javap could not inspect B" }
foreach ($method in @("verifyOrThrow", "getVerifiedUsername", "getVerifiedSessionProof")) {
    if ($gate -notmatch "native .+\b$method\(") { Fail "JNIC did not translate B.$method" }
}
foreach ($method in @(
    'permitModuleActivation', 'permitTickDispatch', 'permitRenderDispatch',
    'permitInputDispatch', 'permitPacketDispatch', 'permitEventDispatch',
    'permitMovementDispatch'
)) {
    if ($gate -match "native .+\b$method\(") { Fail "JNIC transformed the hot runtime permit wrapper: $method" }
}

$bridge = & $javap -classpath $Jar -p gq.yozakura.k.A 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Fail "javap could not inspect A" }
foreach ($method in @('q0', 'q1', 'login0', 'redeemLicense0', 'logout0', 'username0', 'role0', 'expiry0', 'sessionProof0')) {
    if ($bridge -notmatch "\b$method\(") { Fail "JNI registration contract was renamed or removed: $method" }
}
foreach ($method in @(
    "login", "redeemLicense", "getVerifiedUsername", "getVerifiedRole",
    "getVerifiedExpiry", "getVerifiedSessionProof"
)) {
    if ($bridge -notmatch "native .+\b$method\(") { Fail "JNIC did not translate A.$method" }
}

foreach ($guiClass in @(
    'gq.yozakura.ui.click.material.MaterialClickGui',
    'gq.yozakura.ui.click.sakura.SakuraClickGui',
    'gq.yozakura.ui.click.yozakura.YozakuraClickGui'
)) {
    $guiAbi = & $javap -classpath $Jar -p -s $guiClass 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or
            $guiAbi -notmatch 'void func_73866_w_\(\);' -or
            $guiAbi -notmatch 'void func_73863_a\(int, int, float\);') {
        Fail "Minecraft GUI callbacks were renamed or removed in $guiClass"
    }
}

$majorVersions = Get-ClassMajorVersions $Jar
$newerClass = $majorVersions.GetEnumerator() | Where-Object { $_.Value -gt 52 } | Select-Object -First 1
if ($null -ne $newerClass) {
    Fail "class $($newerClass.Key) requires bytecode major $($newerClass.Value), newer than Java 8"
}
$maximumMajor = (($majorVersions.Values | Measure-Object -Maximum).Maximum)

$lines = @(
    'Yozakura Neko + Eskid + JNIC verification: PASS',
    "Input SHA-256:  $inputHash",
    "Neko SHA-256:   $nekoHash",
    "Eskid SHA-256:  $eskidHash",
    "Output SHA-256: $outputHash",
    "Neko class mappings: $($classMappings.Count)",
    "Neko method mappings: $($methodMappings.Count)",
    "Yozakura classes renamed by Neko: $renamedCount",
    "Classes under protected n namespace: $($protectedNamespaceEntries.Count)",
    "JAR entries: $($entries.Count)",
    "Non-class resources retained: $($inputResources.Count)",
    "Maximum class major version: $maximumMajor"
)
$lines | ForEach-Object { Write-Host $_ }
if (-not [string]::IsNullOrWhiteSpace($Report)) {
    $reportDir = Split-Path -Parent $Report
    if ($reportDir) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
    $lines | Set-Content -LiteralPath $Report -Encoding UTF8
}
