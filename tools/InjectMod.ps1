param(
    [string]$Dll = "build\libs\YozakuraLoader-x64.dll",
    [int]$ProcessId = 0,
    [ValidateSet("Auto", "Forge", "Forge1201", "Vanilla", "Lunar")]
    [string]$Target = "Auto",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    return (Resolve-Path -LiteralPath (Join-Path $repoRoot $Path)).Path
}

function Test-Contains([string]$Text, [string]$Needle) {
    if (!$Text) { return $false }
    return $Text.IndexOf($Needle, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Get-MinecraftTargetScore($Process, [string]$Target) {
    $title = if ($Process.Title) { $Process.Title } else { "" }
    $cmd = if ($Process.CommandLine) { $Process.CommandLine } else { "" }

    if ((Test-Contains $title "Home - Lunar Client") -or
        (Test-Contains $title "Hello Minecraft! Launcher") -or
        (Test-Contains $title "Badlion Chat") -or
        (Test-Contains $cmd "org.gradle.launcher.daemon") -or
        (Test-Contains $cmd "-jar `"HMCL") -or
        (Test-Contains $cmd "-jar HMCL")) {
        return 1000
    }

    $titleMinecraft = Test-Contains $title "Minecraft"
    $titleVersion189 = Test-Contains $title "1.8.9"
    $commandVersion189 = (Test-Contains $cmd "--version 1.8.9") -or
        (Test-Contains $cmd "versions\1.8.9") -or
        (Test-Contains $cmd "versions/1.8.9")
    $version189 = $titleVersion189 -or $commandVersion189
    $titleVersion1201 = Test-Contains $title "1.20.1"
    $commandVersion1201 = (Test-Contains $cmd "--version 1.20.1") -or
        (Test-Contains $cmd "versions\1.20.1") -or
        (Test-Contains $cmd "versions/1.20.1")
    $version1201 = $titleVersion1201 -or $commandVersion1201
    $lunar = (Test-Contains $title "Lunar") -or
        (Test-Contains $cmd "Lunar") -or
        (Test-Contains $cmd ".lunarclient") -or
        (Test-Contains $cmd "moonsworth") -or
        (Test-Contains $cmd "com.moonsworth.lunar.genesis") -or
        (Test-Contains $cmd "ichor.")
    $badlion = (Test-Contains $title "Badlion") -or (Test-Contains $cmd "Badlion")
    $forge = (Test-Contains $cmd "net.minecraft.launchwrapper.Launch") -or
        (Test-Contains $cmd "net.minecraftforge") -or
        (Test-Contains $cmd "--tweakClass cpw.mods.fml") -or
        (Test-Contains $cmd "--tweakClass net.minecraftforge") -or
        (Test-Contains $cmd "FMLTweaker") -or
        (Test-Contains $cmd "cpw.mods.bootstraplauncher") -or
        (Test-Contains $cmd "modlauncher") -or
        (Test-Contains $cmd "forgeclient")
    $vanillaMain = Test-Contains $cmd "net.minecraft.client.main.Main"

    switch ($Target) {
        "Lunar" {
            if (!$lunar -or $badlion) { return 1000 }
            if ($titleMinecraft -and $version189) { return 0 }
            if ($version189) { return 1 }
            return 2
        }
        "Vanilla" {
            if ($lunar -or $badlion -or $forge) { return 1000 }
            if ($vanillaMain -and $version189) { return 0 }
            if ($vanillaMain) { return 1 }
            if ($titleMinecraft -and $version189 -and !$cmd) { return 3 }
            if ($titleMinecraft -and !$cmd) { return 5 }
            return 1000
        }
        "Forge" {
            if ($lunar -or $badlion) { return 1000 }
            if ($forge -and $version189) { return 0 }
            if ($forge) { return 1 }
            if ($titleMinecraft -and $version189 -and !$vanillaMain) { return 4 }
            if ($titleMinecraft -and !$cmd) { return 6 }
            return 1000
        }
        "Forge1201" {
            if ($lunar -or $badlion) { return 1000 }
            if ($forge -and $version1201) { return 0 }
            if ($forge -and (Test-Contains $cmd "1.20.1")) { return 1 }
            if ($titleMinecraft -and $version1201 -and !$vanillaMain) { return 4 }
            return 1000
        }
        default {
            if ($lunar -and $version189) { return 2 }
            if ($forge -and $version189) { return 0 }
            if ($vanillaMain -and $version189) { return 1 }
            if ($titleMinecraft -and $version189) { return 3 }
            if ($lunar -or $forge -or $vanillaMain -or $titleMinecraft) { return 5 }
            return 1000
        }
    }
}

function Find-MinecraftProcess([string]$Target) {
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

    $candidates = $processes | ForEach-Object {
        $score = Get-MinecraftTargetScore $_ $Target
        if ($score -lt 1000) {
            $_ | Add-Member -NotePropertyName Score -NotePropertyValue $score -Force
            $_
        }
    }

    $preferred = $candidates |
        Sort-Object Score, Id |
        Select-Object -First 1

    if (!$preferred) {
        throw "No $Target Minecraft Java process found. Start the selected client first, or pass -ProcessId <pid>."
    }

    return $preferred
}

function Get-LoadedYozakuraModule([int]$ProcessId, [string]$DllPath) {
    $target = [IO.Path]::GetFullPath($DllPath)
    $targetName = [IO.Path]::GetFileName($target)
    $process = Get-Process -Id $ProcessId -ErrorAction Stop
    try {
        foreach ($module in $process.Modules) {
            $moduleName = [IO.Path]::GetFileName($module.FileName)
            $knownLoaderName = ($moduleName.StartsWith("YozakuraLoader", [StringComparison]::OrdinalIgnoreCase) -or
                $moduleName.StartsWith("YozakuraReobf", [StringComparison]::OrdinalIgnoreCase)) -and
                $moduleName.EndsWith(".dll", [StringComparison]::OrdinalIgnoreCase)
            if ($module.FileName -ieq $target -or $moduleName -ieq $targetName -or $knownLoaderName) {
                return $module.FileName
            }
        }
    } catch {
        throw "Unable to inspect modules in target PID ${ProcessId}; refusing injection to avoid loading Yozakura twice. $($_.Exception.Message)"
    }
    return $null
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
    Find-MinecraftProcess $Target
}

Write-Host "Target PID: $($targetProcess.Id)"
Write-Host "Target: $($targetProcess.Title)"
Write-Host "Profile: $Target"
Write-Host "DLL: $dllPath"

if ($DryRun) {
    Write-Host "Dry run: target resolved; injection skipped."
    exit 0
}

$loadedModule = Get-LoadedYozakuraModule $targetProcess.Id $dllPath
if ($loadedModule) {
    throw "Yozakura is already injected into target PID $($targetProcess.Id) ($loadedModule). Restart Minecraft before injecting again; another loader would create a second isolated classloader."
}

$PROCESS_ACCESS = 0x0002 -bor 0x0400 -bor 0x0008 -bor 0x0020 -bor 0x0010
$MEM_COMMIT = 0x1000
$MEM_RESERVE = 0x2000
$PAGE_READWRITE = 0x04
$MEM_RELEASE = 0x8000
$WAIT_OBJECT_0 = [uint32]0
$WAIT_TIMEOUT = [uint32]258
$WAIT_FAILED = [uint32]::MaxValue

$process = [OneClickRemoteDllInjector]::OpenProcess($PROCESS_ACCESS, $false, [uint32]$targetProcess.Id)
if ($process -eq [IntPtr]::Zero) {
    throw "OpenProcess failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}

try {
    $bytes = [uint32](($dllPath.Length + 1) * 2)
    $remotePath = [OneClickRemoteDllInjector]::VirtualAllocEx($process, [IntPtr]::Zero, [UIntPtr]$bytes, $MEM_COMMIT -bor $MEM_RESERVE, $PAGE_READWRITE)
    $releaseRemotePath = $true
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
            [uint32]$waitResult = [OneClickRemoteDllInjector]::WaitForSingleObject($thread, 10000)
            if ($waitResult -ne $WAIT_OBJECT_0) {
                $releaseRemotePath = $false
                if ($waitResult -eq $WAIT_TIMEOUT) {
                    throw "Timed out waiting for LoadLibraryW; remote path memory was retained for the active thread."
                }
                if ($waitResult -eq $WAIT_FAILED) {
                    throw "WaitForSingleObject failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
                }
                throw "Unexpected LoadLibraryW wait result: $waitResult"
            }
            [uint32]$exitCode = 0
            if (-not [OneClickRemoteDllInjector]::GetExitCodeThread($thread, [ref]$exitCode)) {
                throw "GetExitCodeThread failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
            }
            if ($exitCode -eq 0) {
                throw "LoadLibraryW returned NULL in target process."
            }
            Write-Host "Injected. LoadLibrary handle: $exitCode"
        } finally {
            [void][OneClickRemoteDllInjector]::CloseHandle($thread)
        }
    } finally {
        if ($remotePath -ne [IntPtr]::Zero -and $releaseRemotePath) {
            [void][OneClickRemoteDllInjector]::VirtualFreeEx($process, $remotePath, [UIntPtr]::Zero, $MEM_RELEASE)
        }
    }
} finally {
    [void][OneClickRemoteDllInjector]::CloseHandle($process)
}

Start-Sleep -Seconds 2

$logCandidates = @(
    "JarToDllLoader.log",
    "YozakuraLoader.log",
    "YozakuraLoader-Loader.log",
    "Yozakura-Loader.log"
) | ForEach-Object { Join-Path $env:TEMP $_ }

foreach ($log in $logCandidates) {
    if (Test-Path -LiteralPath $log) {
        Write-Host ""
        Write-Host "Log: $log"
        Get-Content -LiteralPath $log -Tail 80
        break
    }
}
