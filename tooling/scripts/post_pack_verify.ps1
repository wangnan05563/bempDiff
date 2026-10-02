# post_pack_verify.ps1 - post-pack verification for BempDiff NSIS builds.
# Pure ASCII on purpose: PS 5.1 decodes BOM-less non-ASCII scripts via ANSI codepage.
#
# What it does (T01507):
#   1. Locate the canonical installer for $Version across release root AND legacy _bk-* dirs
#      ("exists and >= 50MB" rule). Warns loudly when the canonical copy is NOT in the root
#      (stale-root scenario: users picking from \release\ would get an old build).
#   2. Verify packaged webui bundle freshness: the index.html inside the packaged
#      win-unpacked tree must reference the SAME hashed bundles as the source-side
#      dist_input\webui\index.html (guards "packaged OK but ships an old frontend").
# Exit 0 = pass, 1 = fail. Prints CANONICAL-INSTALLER line for pickup automation.

param(
  [Parameter(Mandatory = $true)][string]$ReleaseDir,
  [Parameter(Mandatory = $true)][string]$Version,
  [Parameter(Mandatory = $true)][string]$SourceWebuiIndex
)
$ErrorActionPreference = 'Stop'
$MinInstallerBytes = 52428800  # 50MB stub guard

if (-not (Test-Path $ReleaseDir)) { Write-Error ("ReleaseDir not found: " + $ReleaseDir); exit 1 }

# ---- 1. candidate dirs: root first, then every _bk-* ----
$cands = New-Object System.Collections.Generic.List[string]
$cands.Add($ReleaseDir)
Get-ChildItem -LiteralPath $ReleaseDir -Directory -Filter '_bk-*' -ErrorAction SilentlyContinue |
  ForEach-Object { $cands.Add($_.FullName) }

$valid = New-Object System.Collections.Generic.List[string]
foreach ($d in $cands) {
  $f = Join-Path $d ("BempDiff-{0}-setup.exe" -f $Version)
  if (Test-Path -LiteralPath $f) {
    if ((Get-Item -LiteralPath $f).Length -ge $MinInstallerBytes) { $valid.Add($f) }
    else { Write-Host ("[WARN] stub installer (<50MB), ignored: " + $f) }
  }
}
if ($valid.Count -eq 0) {
  Write-Error ("no valid installer (>=50MB) for version " + $Version + " under " + $ReleaseDir + " (root + _bk-*)")
  exit 1
}

$canonical = $null
foreach ($f in $valid) {
  if ($f.StartsWith($ReleaseDir, [System.StringComparison]::OrdinalIgnoreCase) -and
      -not ($f.Substring($ReleaseDir.Length).StartsWith('\_bk-'))) { $canonical = $f; break }
}
if (-not $canonical) {
  $canonical = $valid | Sort-Object { (Get-Item -LiteralPath $_).LastWriteTimeUtc } -Descending | Select-Object -First 1
  Write-Host "[WARN] canonical installer is NOT in release root - stale-root scenario. Promote it or pick from the path below."
}
Write-Host ("CANONICAL-INSTALLER: " + $canonical)

# ---- 2. packaged webui bundle freshness ----
$bundleRe = 'index-[A-Za-z0-9_-]+\.js'
function Get-Bundles([string]$idxPath) {
  if (-not (Test-Path -LiteralPath $idxPath)) { return $null }
  $html = [System.IO.File]::ReadAllText($idxPath)
  return @([regex]::Matches($html, $bundleRe) | ForEach-Object { $_.Value } | Sort-Object -Unique)
}
$srcBundles = Get-Bundles $SourceWebuiIndex
if (-not $srcBundles -or $srcBundles.Count -eq 0) {
  Write-Error ("source webui index has no hashed bundle refs (or file missing): " + $SourceWebuiIndex)
  exit 1
}

$packagedIndex = $null
foreach ($d in $cands) {
  $p = Join-Path $d 'win-unpacked\resources\bempdiff\dist_input\webui\index.html'
  if (Test-Path -LiteralPath $p) {
    if ($null -eq $packagedIndex) { $packagedIndex = $p }
    else {
      $a = Get-Item -LiteralPath $packagedIndex; $b = Get-Item -LiteralPath $p
      if ($b.LastWriteTimeUtc -gt $a.LastWriteTimeUtc) { $packagedIndex = $p }
    }
  }
}
if (-not $packagedIndex) {
  Write-Host "[WARN] no packaged win-unpacked webui index found - bundle freshness check skipped"
  exit 0
}
$pkgBundles = Get-Bundles $packagedIndex
$diff = Compare-Object -ReferenceObject $srcBundles -DifferenceObject $pkgBundles -ErrorAction SilentlyContinue
if ($diff) {
  Write-Host ("[ERROR] packaged webui is STALE vs source:")
  Write-Host ("        packaged : " + (($pkgBundles | Select-Object -First 6) -join ', '))
  Write-Host ("        source   : " + (($srcBundles | Select-Object -First 6) -join ', '))
  Write-Host ("        packaged index: " + $packagedIndex)
  exit 1
}
Write-Host ("[OK] packaged webui bundles match source (" + ($pkgBundles.Count) + " bundles): " + $packagedIndex)
Write-Host "[OK] post-pack verify passed."
exit 0
