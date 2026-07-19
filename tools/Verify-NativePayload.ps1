param(
    [Parameter(Mandatory = $true)]
    [string]$Dll,

    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [string]$Report
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw "Native payload verification failed: $Message"
}

$Dll = (Resolve-Path -LiteralPath $Dll).Path
$Jar = (Resolve-Path -LiteralPath $Jar).Path

$dllBytes = [System.IO.File]::ReadAllBytes($Dll)
$dllAscii = [System.Text.Encoding]::ASCII.GetString($dllBytes)
$forbiddenNativeStrings = @(
    "api/v2/verify/login"
    "api/v2/verify/heartbeat"
    "api/v2/verify/logout"
    "49.235.166.227"
    "gq.yozakura.auth.NativeAuthBridge"
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
    if ($machine -ne 0x8664) {
        Fail ("the loader machine is 0x{0:X4}; expected AMD64 (0x8664)" -f $machine)
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
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $embeddedHash = ([BitConverter]::ToString($sha256.ComputeHash($embedded))).Replace('-', '').ToLowerInvariant()
    $jarHash = ([BitConverter]::ToString($sha256.ComputeHash($jarBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
if ($embeddedHash -ne $jarHash) {
    Fail "embedded RCDATA 101 does not match the verified JAR (embedded $embeddedHash, JAR $jarHash)"
}

$lines = @(
    "Yozakura native payload verification: PASS"
    "Loader: $Dll"
    "Embedded JAR bytes: $($embedded.Length)"
    "Embedded JAR SHA-256: $embeddedHash"
    "PE machine: AMD64 (0x8664)"
)
$lines | ForEach-Object { Write-Host $_ }
if ($Report) {
    $reportDir = Split-Path -Parent $Report
    if ($reportDir) {
        New-Item -ItemType Directory -Force $reportDir | Out-Null
    }
    $lines | Set-Content -Encoding UTF8 $Report
}
