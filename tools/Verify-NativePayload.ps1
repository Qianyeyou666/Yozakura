param(
    [Parameter(Mandatory = $true)]
    [string]$Dll,

    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [string]$EmbeddedJarSource,

    [string]$Report
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw "Native payload verification failed: $Message"
}

$Dll = (Resolve-Path -LiteralPath $Dll).Path
$Jar = (Resolve-Path -LiteralPath $Jar).Path
if ($EmbeddedJarSource) {
    $EmbeddedJarSource = (Resolve-Path -LiteralPath $EmbeddedJarSource).Path
}

$dllBytes = [System.IO.File]::ReadAllBytes($Dll)
$dllAscii = [System.Text.Encoding]::ASCII.GetString($dllBytes)
$forbiddenNativeStrings = @(
    "api/v2/verify/challenge"
    "api/v2/verify/login"
    "api/v2/verify/heartbeat"
    "api/v2/verify/introspect"
    "api/v2/verify/logout"
    "auth.yozakura.wtf"
    "49.235.166.227"
    "gq.yozakura.k.A"
    "login0"
    "logout0"
    "JarToDllLoader.log"
    "YozakuraNativeAuth"
    "gq.yozakura.YozakuraBootstrap"
    "gq.yozakura.bridge.IsolatedClientClassLoader"
    "net.minecraft.client.Minecraft"
    "login accepted and heartbeat started"
    "native authentication bridge registration failed"
    "JarToDllInject"
    "YozakuraInject"
)
foreach ($value in $forbiddenNativeStrings) {
    if ($dllAscii.Contains($value)) {
        Fail "protected native string is present in plaintext: $value"
    }
}

$reader = [System.IO.BinaryReader]::new([System.IO.File]::OpenRead($Dll))
try {
    if ($reader.ReadUInt16() -ne 0x5A4D) {
        Fail "the loader is not a PE file: $Dll"
    }
    $reader.BaseStream.Position = 0x3C
    $peOffset = $reader.ReadUInt32()
    $reader.BaseStream.Position = $peOffset
    if ($reader.ReadUInt32() -ne 0x00004550) {
        Fail "the loader has an invalid PE signature: $Dll"
    }
    $machine = $reader.ReadUInt16()
    $expectedMachine = if ([System.IO.Path]::GetFileName($Dll) -match '-x86(?:-|\.)') { 0x014C } else { 0x8664 }
    $expectedName = if ($expectedMachine -eq 0x014C) { 'I386' } else { 'AMD64' }
    if ($machine -ne $expectedMachine) {
        Fail ("the loader machine is 0x{0:X4}; expected {1} (0x{2:X4})" -f `
                $machine, $expectedName, $expectedMachine)
    }
} finally {
    $reader.Dispose()
}

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class YozakuraResourceReader
{
    private const uint LOAD_LIBRARY_AS_DATAFILE = 0x00000002;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadLibraryEx(string fileName, IntPtr file, uint flags);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr FindResource(IntPtr module, IntPtr name, IntPtr type);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr LoadResource(IntPtr module, IntPtr resource);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr LockResource(IntPtr resourceData);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint SizeofResource(IntPtr module, IntPtr resource);

    [DllImport("kernel32.dll")]
    private static extern bool FreeLibrary(IntPtr module);

    public static byte[] Read(string dllPath, int resourceId, int resourceType)
    {
        IntPtr module = LoadLibraryEx(dllPath, IntPtr.Zero, LOAD_LIBRARY_AS_DATAFILE);
        if (module == IntPtr.Zero)
            throw new InvalidOperationException("LoadLibraryEx failed: " + Marshal.GetLastWin32Error());
        try
        {
            IntPtr resource = FindResource(module, new IntPtr(resourceId), new IntPtr(resourceType));
            if (resource == IntPtr.Zero)
                throw new InvalidOperationException("FindResource failed: " + Marshal.GetLastWin32Error());
            uint size = SizeofResource(module, resource);
            if (size == 0)
                throw new InvalidOperationException("The embedded resource is empty");
            IntPtr loaded = LoadResource(module, resource);
            IntPtr pointer = LockResource(loaded);
            if (pointer == IntPtr.Zero)
                throw new InvalidOperationException("LockResource failed: " + Marshal.GetLastWin32Error());
            int length = checked((int)size);
            byte[] bytes = new byte[length];
            Marshal.Copy(pointer, bytes, 0, length);
            return bytes;
        }
        finally
        {
            FreeLibrary(module);
        }
    }
}
'@

$embedded = [YozakuraResourceReader]::Read($Dll, 101, 10)
$jarBytes = [System.IO.File]::ReadAllBytes($Jar)
$runtimeJarBytes = if ($EmbeddedJarSource) {
    [System.IO.File]::ReadAllBytes($EmbeddedJarSource)
} else {
    $jarBytes
}
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $embeddedHash = ([BitConverter]::ToString($sha256.ComputeHash($embedded))).Replace('-', '').ToLowerInvariant()
    $jarHash = ([BitConverter]::ToString($sha256.ComputeHash($jarBytes))).Replace('-', '').ToLowerInvariant()
    $runtimeJarHash = ([BitConverter]::ToString($sha256.ComputeHash($runtimeJarBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
if ($jarHash -ne $runtimeJarHash) {
    Fail "named release JAR does not match the runtime JAR used by the resource compiler (release $jarHash, runtime $runtimeJarHash)"
}
if ($embeddedHash -ne $jarHash) {
    Fail "embedded RCDATA 101 does not match the verified named release JAR (embedded $embeddedHash, release $jarHash)"
}

$lines = @(
    "Yozakura native payload verification: PASS"
    "Loader: $Dll"
    "Named release JAR: $Jar"
    "Runtime JAR source: $(if ($EmbeddedJarSource) { $EmbeddedJarSource } else { $Jar })"
    "Embedded JAR bytes: $($embedded.Length)"
    "Embedded JAR SHA-256: $embeddedHash"
    "Named release JAR SHA-256: $jarHash"
    "Runtime JAR SHA-256: $runtimeJarHash"
    ("PE machine: {0} (0x{1:X4})" -f $expectedName, $machine)
)
$lines | ForEach-Object { Write-Host $_ }
if ($Report) {
    $reportDir = Split-Path -Parent $Report
    if ($reportDir) {
        New-Item -ItemType Directory -Force $reportDir | Out-Null
    }
    $lines | Set-Content -Encoding UTF8 $Report
}
