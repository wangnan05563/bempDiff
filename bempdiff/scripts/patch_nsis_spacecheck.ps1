# 
# patch_nsis_spacecheck.ps1
# 
# Persist the NSIS "disk-space precheck" patch into electron-builder's templates so the
# packaging flow can re-apply it idempotently. On some hosts (NTFS quota, compressed
# folders, AV filter drivers, virtualization redirect) GetDiskFreeSpaceEx returns free~=0,
# causing electron-builder / NSIS to report "insufficient disk space" even when the disk is
# actually fine. This script makes those false positives go away, reproducibly.
# 
# It does two things:
#   1) Overwrite the electron-builder node_modules template
#      (node_modules/app-builder-lib/templates/nsis/common.nsh) with our locked template
#      (scripts/nsis-tpl/common.nsh) that forces the required NSIS space to 1 KiB so the
#      built-in SectionSetSize precheck always passes. Install-time decompression still
#      writes real data unaffected.
#   2) Verify the in-repo source build/installer.nsh carries the fix marker (ensureDiskSpace
#      disabled entirely via an immediate Return). This preInit path runs EARLIER than
#      SectionSetSize, so it is the one that normally trips in bad environments; it is the
#      source of truth to guard. Full disable is required because NSIS IntCmp is a 32-bit
#      signed compare while free bytes are 64-bit: with >2GB free the low 32 bits wrap
#      negative and ANY threshold mis-fires as "insufficient space" on healthy disks.
# 
# Why a marker check instead of writing the source file: build/installer.nsh is a git-tracked
# project file (never reset by npm install), so we only validate it and refuse to package if
# someone reverted the fix. We do NOT overwrite it to avoid dirtying the git working tree.
# 
# Encoding note: this script MUST stay pure ASCII (CRLF). The Chinese marker string is built
# from Unicode code points below so the file carries no multi-byte bytes; that way cmd/PSGBK
# parsing can never corrupt it regardless of BOM presence. Keep every non-escape char 7-bit.
# 
# To restore real NSIS disk validation: delete the SectionSetSize line in
# scripts/nsis-tpl/common.nsh, restore build/installer.nsh's original ensureDiskSpace, and
# drop this script's overrides/checks.
# 

[CmdletBinding()]
param(
  # When $false, a missing fix marker only warns and lets the build continue (for CI that
  # must not hard-fail on unrelated hosts). Missing required INPUT files still hard-fail:
  # there is nothing to patch without them. Production should keep the default $true.
  [bool]$Strict = $true
)
$ErrorActionPreference = 'Stop'
# Parent cmd has already done chcp 65001, but PowerShell 5.1 defaults Console.OutputEncoding
# to the system OEM code page (936 on Chinese Windows). Lock it to UTF-8 so our Chinese
# Write-Host / Write-Warning lines reach the parent correctly instead of becoming mojibake.
# NOTE: InputEncoding / $OutputEncoding are not strictly needed here (we read only local files
# and spawn no children), but set them so downstream use stays consistent.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8

$ScriptDir     = Split-Path -Parent $MyInvocation.MyCommand.Path
$Proto         = Split-Path -Parent $ScriptDir                      # bempdiff/
$Template      = Join-Path $ScriptDir 'nsis-tpl\common.nsh'         # repo-locked template
$Target        = Join-Path $Proto 'node_modules\app-builder-lib\templates\nsis\common.nsh'
$InstallerNsh  = Join-Path $Proto 'build\installer.nsh'             # project source, preInit gate
# Chinese marker "[磁盘空间预检禁用]", built from code points so this file can be pure ASCII
# (no BOM dependency). Kept in sync with the marker comment in build/installer.nsh.
$Marker        = -join (0x5B,0x78C1,0x76D8,0x7A7A,0x95F4,0x9884,0x68C0,0x7981,0x7528,0x5D | ForEach-Object { [char]$_ })

# A helper to fail (strict) or warn-pass (lenient) on an integrity check.
function Test-Marker([string]$Name, [string]$Content) {
  if ($Content -notmatch [regex]::Escape($Marker)) {
    $msg = "Patch not applied: $Name is missing marker [$Marker]; the resulting installer may still mis-report disk space."
    if ($Strict) { throw $msg }
    Write-Warning $msg
    return $false
  }
  return $true
}

# Validate required inputs EXIST regardless of Strict: we cannot patch what is absent.
# (These are fail-fast by design, not covered by the Strict "warn" option.)
foreach ($req in @(
    @{ Label = 'repo template';     Path = $Template },
    @{ Label = 'node_modules target'; Path = $Target },
    @{ Label = 'project installer.nsh'; Path = $InstallerNsh }
  )) {
  if (-not (Test-Path $req.Path)) {
    throw "Missing required $($req.Label): $($req.Path)"
  }
}

# 1) Overwrite the node_modules template (NSIS built-in SectionSetSize precheck).
Copy-Item -Force $Template $Target
if (-not (Test-Marker -Name $Target -Content (Get-Content -Raw $Target -Encoding UTF8))) {
  if ($Strict) { throw 'Template marker check failed.' }  # unreachable: Test-Marker already threw in strict
}
Write-Host "NSIS disk-space precheck patch applied (node_modules template): $Target" -ForegroundColor Green

# 2) Validate the project source build/installer.nsh carries the fix marker (preInit gate).
$instContent = Get-Content -Raw $InstallerNsh -Encoding UTF8
if (-not (Test-Marker -Name $InstallerNsh -Content $instContent)) {
  if ($Strict) { throw 'installer.nsh marker check failed.' }  # unreachable in strict
}
Write-Host "NSIS disk-space precheck patch applied (project source): $InstallerNsh" -ForegroundColor Green

exit 0