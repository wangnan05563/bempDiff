#
# bump_patch.ps1
#
# Before packaging, write a UNIQUE patch version into both package.json files:
#   - <root>/package.json         (root project metadata)
#   - <root>/dev-shell/package.json  (REAL version source read by electron-builder,
#                                     because build.directories.app = "dev-shell")
# patch = yyyyMMdd + daily-increment seq (zero-padded 2 digits), e.g. 0.1.2026082601.
#
# Why BOTH files: electron-builder derives the installer name ($version) from the
# app directory's package.json (dev-shell), NOT the root one. Without syncing both,
# the bump appears in root but the setup.exe still comes out as 0.1.0-setup.exe.
#
# Why date+seq: distinguishes multiple builds within one day (same day +1) and
# across days (new date => new number), so installers never share a version and
# never overwrite each other in cache/upload/rollback.
#
# NOTE: pure ASCII + CRLF required. The default Windows PowerShell console code
# page is GBK; any non-ASCII comment would corrupt parsing. Keep everything 7-bit.
#

param(
    # Root project folder (contains package.json). Passed explicitly by the caller
    # because $PSScriptRoot / $MyInvocation are empty in some execution hosts.
    [string]$Root
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Root)) { throw 'Missing -Root parameter (bempdiff root directory).' }
$root    = $Root.TrimEnd('\', '/')
$pkgFiles = @(
    (Join-Path $root 'package.json'),
    (Join-Path $root 'dev-shell\package.json')
)
# Last build's { date, seq } record (kept inside project so the seq persists across days)
$seqFile = Join-Path $root 'scripts\.patch-seq.json'

$today = (Get-Date).ToString('yyyyMMdd')

# Read the last record (missing/corrupt => treat as never built)
$lastDate = ''
$lastSeq  = 0
if (Test-Path $seqFile) {
  try {
    $rec = Get-Content $seqFile -Raw | ConvertFrom-Json
    $lastDate = [string]$rec.date
    $lastSeq  = [int]$rec.seq
  } catch { $lastDate = ''; $lastSeq = 0 }
}

# Sequence: reset to 1 on a new day, +1 within the same day
$seq  = if ($lastDate -eq $today) { $lastSeq + 1 } else { 1 }
$patch = $today + $seq.ToString('D2')

# Replace only the patch, keep original major.minor (e.g. 0.1); leave the rest of
# each package.json bytes untouched. Written back as UTF-8 WITHOUT BOM so Node's
# require()/JSON parsing stays intact.
function Update-PkgVersion([string]$file) {
  if (-not (Test-Path $file)) { throw "package.json not found: $file" }
  $content = [System.IO.File]::ReadAllText($file)
  $m = [regex]::Match($content, '"version"\s*:\s*"(\d+)\.(\d+)\.')
  if (-not $m.Success) { throw "package.json does not match expected version format (major.minor.patch): $file" }
  $ver = '{0}.{1}.{2}' -f $m.Groups[1].Value, $m.Groups[2].Value, $patch
  $updated = [regex]::Replace($content, '"version"\s*:\s*"[^"]*"', ('"version": "' + $ver + '"'))
  $enc = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($file, $updated, $enc)
  return $ver
}

$firstVer = $null
foreach ($f in $pkgFiles) {
  $v = Update-PkgVersion $f
  if (-not $firstVer) { $firstVer = $v }
}

# Persist this seq for the next build
@{ date = $today; seq = $seq } | ConvertTo-Json | Set-Content -Path $seqFile -Encoding utf8

Write-Host "bump patch -> $firstVer (root + dev-shell)"
