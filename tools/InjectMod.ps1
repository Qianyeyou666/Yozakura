param(
    [string]$Dll = "build\libs\VapuLiteReobf-x64.dll",
    [int]$ProcessId = 0
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    return (Resolve-Path -LiteralPath (Join-Path $repoRoot $Path)).Path
}

function Find-MinecraftProcess {
    $processes = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -match '^(java|javaw)\.exe$' } |
        ForEach-Object {
            $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
            [pscustomobject]@{
                Id = $_.ProcessId
                Name = $_.Name
                Path = $_.ExecutablePath
                Title = if ($p) { $p.MainWindowTitle } else { "" }
                CommandLine = $_.CommandLine
            }
        }

    $candidates = $processes | Where-Object {
        $_.Title -like 'Minecraft*' -or
        $_.CommandLine -match 'net\.minecraft\.launchwrapper\.Launch' -or
        $_.CommandLine -match '\\.minecraft'
    }

    $preferred = $candidates |
        Sort-Object @{ Expression = { if ($_.Title -like 'Minecraft 1.8.9*') { 0 } elseif ($_.Title -like 'Minecraft*') { 1 } else { 2 } } }, Id |
        Select-Object -First 1

    if (!$preferred) {
        throw "No Minecraft Java process found. Start Minecraft first, or pass a PID: inject-mod.bat <dll> <pid>"
    }

    return $preferred
}

function Test-ModuleLoaded([int]$ProcessId, [string]$DllPath) {
    $target = [IO.Path]::GetFullPath($DllPath)
    $process = Get-Process -Id $ProcessId -ErrorAction Stop
    try {
        foreach ($module in $process.Modules) {
            if ($module.FileName -ieq $target) {
                return $true
            }
        }
    } catch {
        return $false
    }
    return $false
}

$source = @'
using System;
using System.Runtime.InteropServices;

public static class OneClickRemoteDllInjector {
    [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr VirtualAllocEx(IntPtr process, IntPtr address, UIntPtr size, uint allocationType, uint protect);
    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool WriteProcessMemory(IntPtr process, IntPtr address, string buffer, UIntPtr size, out UIntPtr written);
    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern IntPtr GetModuleHandle(string moduleName);
    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Ansi)] public static extern IntPtr GetProcAddress(IntPtr module, string procName);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr CreateRemoteThread(IntPtr process, IntPtr attrs, uint stackSize, IntPtr start, IntPtr param, uint flags, IntPtr threadId);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool GetExitCodeThread(IntPtr thread, out uint exitCode);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool VirtualFreeEx(IntPtr process, IntPtr address, UIntPtr size, uint freeType);
}
'@

Add-Type -TypeDefinition $source

$dllPath = Resolve-RepoPath $Dll
$targetProcess = if ($ProcessId -gt 0) {
    $wmi = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
    if (!$wmi) { throw "Process not found: $ProcessId" }
    $p = Get-Process -Id $ProcessId -ErrorAction Stop
    [pscustomobject]@{
        Id = $ProcessId
        Name = $wmi.Name
        Path = $wmi.ExecutablePath
        Title = $p.MainWindowTitle
        CommandLine = $wmi.CommandLine
    }
} else {
    Find-MinecraftProcess
}

Write-Host "Target PID: $($targetProcess.Id)"
Write-Host "Target: $($targetProcess.Title)"
Write-Host "DLL: $dllPath"

if (Test-ModuleLoaded $targetProcess.Id $dllPath) {
    Write-Host "DLL is already loaded in target process."
    exit 0
}

$PROCESS_ACCESS = 0x0002 -bor 0x0400 -bor 0x0008 -bor 0x0020 -bor 0x0010
$MEM_COMMIT = 0x1000
$MEM_RESERVE = 0x2000
$PAGE_READWRITE = 0x04
$MEM_RELEASE = 0x8000

$process = [OneClickRemoteDllInjector]::OpenProcess($PROCESS_ACCESS, $false, [uint32]$targetProcess.Id)
if ($process -eq [IntPtr]::Zero) {
    throw "OpenProcess failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}

try {
    $bytes = [uint32](($dllPath.Length + 1) * 2)
    $remotePath = [OneClickRemoteDllInjector]::VirtualAllocEx($process, [IntPtr]::Zero, [UIntPtr]$bytes, $MEM_COMMIT -bor $MEM_RESERVE, $PAGE_READWRITE)
    if ($remotePath -eq [IntPtr]::Zero) {
        throw "VirtualAllocEx failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
    }

    try {
        [UIntPtr]$written = [UIntPtr]::Zero
        if (-not [OneClickRemoteDllInjector]::WriteProcessMemory($process, $remotePath, $dllPath, [UIntPtr]$bytes, [ref]$written)) {
            throw "WriteProcessMemory failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
        }

        $kernel32 = [OneClickRemoteDllInjector]::GetModuleHandle("kernel32.dll")
        $loadLibrary = [OneClickRemoteDllInjector]::GetProcAddress($kernel32, "LoadLibraryW")
        if ($loadLibrary -eq [IntPtr]::Zero) {
            throw "GetProcAddress(LoadLibraryW) failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
        }

        $thread = [OneClickRemoteDllInjector]::CreateRemoteThread($process, [IntPtr]::Zero, 0, $loadLibrary, $remotePath, 0, [IntPtr]::Zero)
        if ($thread -eq [IntPtr]::Zero) {
            throw "CreateRemoteThread failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
        }

        try {
            [void][OneClickRemoteDllInjector]::WaitForSingleObject($thread, 10000)
            [uint32]$exitCode = 0
            [void][OneClickRemoteDllInjector]::GetExitCodeThread($thread, [ref]$exitCode)
            if ($exitCode -eq 0) {
                throw "LoadLibraryW returned NULL in target process."
            }
            Write-Host "Injected. LoadLibrary handle: $exitCode"
        } finally {
            [void][OneClickRemoteDllInjector]::CloseHandle($thread)
        }
    } finally {
        if ($remotePath -ne [IntPtr]::Zero) {
            [void][OneClickRemoteDllInjector]::VirtualFreeEx($process, $remotePath, [UIntPtr]::Zero, $MEM_RELEASE)
        }
    }
} finally {
    [void][OneClickRemoteDllInjector]::CloseHandle($process)
}

Start-Sleep -Seconds 2

$logCandidates = @(
    "JarToDllLoader.log",
    "VapuLiteLoader.log",
    "VapuLiteLoader-Loader.log",
    "VapuLite-Loader.log"
) | ForEach-Object { Join-Path $env:TEMP $_ }

foreach ($log in $logCandidates) {
    if (Test-Path -LiteralPath $log) {
        Write-Host ""
        Write-Host "Log: $log"
        Get-Content -LiteralPath $log -Tail 80
        break
    }
}
