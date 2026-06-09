param(
  [string]$IndexPath = "$env:USERPROFILE\.gradle\caches\minecraft\assets\indexes\1.8.json",
  [string]$ObjectsDir = "$env:USERPROFILE\.gradle\caches\minecraft\assets\objects"
)

$ErrorActionPreference = 'Stop'

if (!(Test-Path -LiteralPath $IndexPath)) {
  throw "Asset index not found: $IndexPath"
}

if (!(Test-Path -LiteralPath $ObjectsDir)) {
  New-Item -ItemType Directory -Path $ObjectsDir | Out-Null
}

$index = Get-Content -Raw -LiteralPath $IndexPath | ConvertFrom-Json
$seen = @{}
$assets = New-Object System.Collections.Generic.List[object]

foreach ($prop in $index.objects.PSObject.Properties) {
  $hash = [string]$prop.Value.hash
  if (!$seen.ContainsKey($hash)) {
    $seen[$hash] = $true
    $assets.Add([pscustomobject]@{
      Name = $prop.Name
      Hash = $hash
      Size = [int64]$prop.Value.size
      Prefix = $hash.Substring(0, 2)
    })
  }
}

$missing = New-Object System.Collections.Generic.List[object]
foreach ($asset in $assets) {
  $target = Join-Path (Join-Path $ObjectsDir $asset.Prefix) $asset.Hash
  if (!(Test-Path -LiteralPath $target)) {
    $missing.Add($asset)
    continue
  }

  $item = Get-Item -LiteralPath $target
  if ($item.Length -ne $asset.Size) {
    $missing.Add($asset)
  }
}

Write-Host "Assets in index: $($assets.Count)"
Write-Host "Missing or invalid: $($missing.Count)"

$i = 0
foreach ($asset in $missing) {
  $i++
  $dir = Join-Path $ObjectsDir $asset.Prefix
  $target = Join-Path $dir $asset.Hash
  $tmp = "$target.tmp"
  $url = "https://resources.download.minecraft.net/$($asset.Prefix)/$($asset.Hash)"

  if (!(Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
  }

  Write-Host "[$i/$($missing.Count)] $($asset.Name)"
  Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing

  $item = Get-Item -LiteralPath $tmp
  if ($item.Length -ne $asset.Size) {
    Remove-Item -LiteralPath $tmp -Force
    throw "Size mismatch for $($asset.Name): expected $($asset.Size), got $($item.Length)"
  }

  $sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $tmp).Hash.ToLowerInvariant()
  if ($sha1 -ne $asset.Hash) {
    Remove-Item -LiteralPath $tmp -Force
    throw "SHA1 mismatch for $($asset.Name): expected $($asset.Hash), got $sha1"
  }

  Move-Item -LiteralPath $tmp -Destination $target -Force
}

Write-Host "Asset cache is complete."
