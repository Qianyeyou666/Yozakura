param(
    [switch]$NoBuild,
    [switch]$JarOnly,
    [switch]$IdeOnly,
    [string]$BuildId = "local-20260615-anti-patch-1"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$tmp = Join-Path $env:TEMP "yozakura-fingerprint-tool"

function Resolve-JavaTool([string]$name) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (![string]::IsNullOrWhiteSpace($env:JAVA8_HOME)) {
        $candidates.Add((Join-Path $env:JAVA8_HOME "bin\$name.exe")) | Out-Null
    }
    if (![string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add((Join-Path $env:JAVA_HOME "bin\$name.exe")) | Out-Null
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($command -ne $null) {
        return $command.Source
    }

    throw "Cannot find $name. Set JAVA8_HOME or JAVA_HOME to a JDK path."
}

function Get-ClientJar {
    $candidates = @(
        (Join-Path $repoRoot "build\libs\Yozakura.jar"),
        (Join-Path $repoRoot "build\libs\Yozakura-1.5.0.jar")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    return $null
}

function Get-IdeClasspath {
    $entries = New-Object System.Collections.Generic.List[string]
    $candidates = @(
        (Join-Path $repoRoot "build\classes\java\main"),
        (Join-Path $repoRoot "build\resources\main"),
        (Join-Path $repoRoot "out\production\VapuLite-main"),
        (Join-Path $repoRoot "out\production\resources")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            $entries.Add((Resolve-Path -LiteralPath $candidate).Path) | Out-Null
        }
    }

    if ($entries.Count -eq 0) {
        return $null
    }

    $wrapperClass = "gq\yozakura\auth\vendor\tech\skidonion\obfuscator\inline\C.class"
    foreach ($entry in $entries) {
        if (Test-Path -LiteralPath (Join-Path $entry $wrapperClass)) {
            return ($entries -join ";")
        }
    }

    return $null
}

function Get-Fingerprint([string]$displayName, [string]$classPath, [string]$java, [string]$javac) {
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null

    $sourcePath = Join-Path $tmp "PrintYozakuraFingerprint.java"
    $source = @'
import java.lang.reflect.Method;
import gq.yozakura.k.vendor.tech.skidonion.obfuscator.inline.C;

public class PrintYozakuraFingerprint {
    public static void main(String[] args) throws Exception {
        Method method = C.class.getDeclaredMethod("clientFingerprint");
        method.setAccessible(true);
        System.out.println(method.invoke(null));
    }
}
'@
    [System.IO.File]::WriteAllText($sourcePath, $source, (New-Object System.Text.UTF8Encoding($false)))

    & $javac -encoding UTF-8 -cp $classPath -d $tmp $sourcePath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to compile fingerprint helper for $displayName."
    }

    $fingerprint = & $java -cp "$tmp;$classPath" PrintYozakuraFingerprint
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($fingerprint)) {
        throw "Failed to compute fingerprint for $displayName."
    }

    return ($fingerprint | Select-Object -Last 1).Trim()
}

Push-Location $repoRoot
try {
    if (!$NoBuild) {
        Write-Host "Building latest client jar..."
        & (Join-Path $repoRoot "gradlew.bat") build --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed."
        }
    }

    $java = Resolve-JavaTool "java"
    $javac = Resolve-JavaTool "javac"
    $entries = New-Object System.Collections.Generic.List[string]

    if (!$IdeOnly) {
        $jar = Get-ClientJar
        if ($jar -ne $null) {
            $jarFingerprint = Get-Fingerprint "jar" $jar $java $javac
            $entries.Add("$BuildId`:$jarFingerprint") | Out-Null
            Write-Host ""
            Write-Host "Jar: $jar"
            Write-Host "Jar fingerprint: $jarFingerprint"
            Write-Host "Allowed jar entry:"
            Write-Host "$BuildId`:$jarFingerprint"
        } elseif ($JarOnly) {
            throw "Cannot find Yozakura jar. Run build first."
        }
    }

    if (!$JarOnly) {
        $ideClasspath = Get-IdeClasspath
        if ($ideClasspath -ne $null) {
            $ideFingerprint = Get-Fingerprint "IDE classes" $ideClasspath $java $javac
            $entries.Add("$BuildId`:$ideFingerprint") | Out-Null
            Write-Host ""
            Write-Host "IDE classpath: $ideClasspath"
            Write-Host "IDE fingerprint: $ideFingerprint"
            Write-Host "Allowed IDE entry:"
            Write-Host "$BuildId`:$ideFingerprint"
        } elseif ($IdeOnly) {
            throw "Cannot find compiled IDE classes. Run build first."
        }
    }

    if ($entries.Count -eq 0) {
        throw "No fingerprint source found."
    }

    $allowedClients = ($entries | Select-Object -Unique) -join ";"
    Write-Host ""
    Write-Host "PHANTOMSHIELD_ALLOWED_CLIENTS="
    Write-Host $allowedClients
} finally {
    Pop-Location
}
