<#
.SYNOPSIS
  BempDiff 临时文件 / 磁盘缓存回收脚本（Windows PowerShell 5.1 与 pwsh 跨平台通用）。

.DESCRIPTION
  背景：BempDiff 后端会在系统临时目录生成解压/抽取/反编译临时文件（bempdiff-*），在
  {user.home}/.bempdiff/runtime 生成逐层解包原子文件，并在本地缓存反编译源码
  （%LOCALAPPDATA%\bempdiff\decompile-cache 或 ~/.bempdiff/decompile-cache）。
  这些文件大多只靠「进程退出的 deleteOnExit」兜底——进程被强杀/崩溃或长时间运行不重启时，
  进程内清理永不触发，磁盘临时文件持续累积。

  本脚本按「修改时间早于 N 天」的安全阈值批量回收，天然避开正在进行的解压任务
  （单个解压任务的原子文件生命周期为分钟级，远小于 N 天），因此不会中断运行中的任务。

  设计原则：
   - 只匹配 BempDiff 已知前缀/目录，绝不误删无关文件。
   - 单文件删除失败（被占用/权限）静默跳过，绝不因个别失败中断整体。
   - 默认保留至少 N（默认 3）天历史，供问题复现与验证。
   - 支持 -WhatIf 演练，先预览后实删。
   - 建议在业务低峰期（如每日 02:00）由任务计划程序定时调用。

.EXAMPLE
  # 演练（只预览，不删除）
  powershell -ExecutionPolicy Bypass -File .\cleanup_temp.ps1 -Preflight

  # 实删：回收超过 3 天的临时文件
  powershell -ExecutionPolicy Bypass -File .\cleanup_temp.ps1

  # 实删 + 附带按容量的 decompile-cache 回收（超 512MB 淘汰最旧条目）
  powershell -ExecutionPolicy Bypass -File .\cleanup_temp.ps1 -PurgeCache -DecompileCacheMaxMB 512

.PARAMETER Days
  保留历史天数（早于此阈值的文件/目录才会被删）。默认 3。
  IMPORTANT: 不得小于 1，避免误删仍在解压的任务临时文件。

.PARAMETER PurgeCache
  附带回收 decompile-cache：按 -DecompileCacheMaxMB 总量上限淘汰最旧 .dec，并回收超过 -Days 天的 .dec。

.PARAMETER DecompileCacheMaxMB
  decompile-cache 总量软上限（MB）。默认 512。

.PARAMETER Preflight
  只扫描并输出将删除的条目与预计回收量，不真正删除（等价于 -WhatIf）。
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
  [ValidateRange(1, 365)]
  [int]$Days = 3,
  [switch]$PurgeCache,
  [int]$DecompileCacheMaxMB = 512,
  [switch]$Preflight
)

$ErrorActionPreference = 'SilentlyContinue'

# 结果统计
$stat = @{ Dirs = 0; Files = 0; BytesFreed = [long]0; CacheFreedMB = [double]0 }

# ---------- 目录定位（跨平台） ----------
$OsTemp = if ($env:TEMP) { $env:TEMP } elseif ($test = Get-ChildItem Env:TMPDIR -EA SilentlyContinue) { "$test" } else { '/tmp' }
$HomeDot = if ($env:USERPROFILE) { Join-Path $env:USERPROFILE '.bempdiff' } else { Join-Path $HOME '.bempdiff' }
$LocalData = if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'bempdiff' } else { $HomeDot }
$RunRoot  = Join-Path $HomeDot 'runtime'
$LogRoot  = Join-Path $HomeDot 'logs'
$CacheDir = Join-Path $LocalData 'decompile-cache'

$cut = (Get-Date).AddDays(-([double]$Days))

function Add-Stat([long]$bytes, [int]$f = 0, [int]$d = 0) {
  $script:stat.Files += $f
  $script:stat.Dirs += $d
  $script:stat.BytesFreed += $bytes
}

function Remove-If([string]$path, [bool]$isDir = $false) {
  if (-not $Preflight -and $PSCmdlet.ShouldProcess($path, 'Remove')) {
    try { if ($isDir) { Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop } else { Remove-Item -LiteralPath $path -Force -ErrorAction Stop } }
    catch { return $false }   # 被占用/权限：静默跳过，不中断
  }
  return $true
}

# ---------- 1) 系统临时目录：BempDiff 前缀文件（按天） ----------
Write-Host "= [1/4] 系统临时文件 (dir=$OsTemp, older than $Days 天) =" -ForegroundColor Cyan
if (Test-Path $OsTemp) {
  $patterns = @('bempdiff-*.class', 'bempdiff-*.bin', 'bempdiff-*.jar', 'bempdiff-*.zip')
  foreach ($pat in $patterns) {
    Get-ChildItem -LiteralPath $OsTemp -Filter $pat -File -ErrorAction SilentlyContinue | ForEach-Object {
      if ($_.LastWriteTime -lt $cut) {
        Add-Stat -bytes ([long]$_.Length) -f 1
        Remove-If $_.FullName
        Write-Verbose "  rm $($_.Name)"
      }
    }
  }
  # 残留解包目录（异常路径兜底产物）
  Get-ChildItem -LiteralPath $OsTemp -Directory -Filter 'bempdiff-unpack-*' -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.LastWriteTime -lt $cut) {
      $sz = (Get-ChildItem -LiteralPath $_.FullName -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum
      Add-Stat -bytes $sz -d 1
      Write-Verbose "  rmdir $($_.Name)"
      Remove-If $_.FullName $true
    }
  }
}

# ---------- 2) 解包运行时目录 {home}/.bempdiff/runtime/<jobId>（按天整目录回收） ----------
Write-Host "= [2/4] 作业解包运行时目录 (dir=$RunRoot) ="
if (Test-Path $RunRoot) {
  Get-ChildItem -LiteralPath $RunRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.LastWriteTime -lt $cut) {
      $sz = (Get-ChildItem -LiteralPath $_.FullName -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum
      Add-Stat -bytes $sz -d 1
      Remove-If $_.FullName $true
    }
  }
}

# ---------- 3) 本地反编译缓存 cache（可选：按总量上限 + 按天） ----------
if ($PurgeCache) {
  Write-Host "= [3/4] decompile-cache 回收 (dir=$CacheDir, capMB=$DecompileCacheMaxMB) ="
  if (Test-Path $CacheDir) {
    $frags = Get-ChildItem -LiteralPath $CacheDir -Filter '*.dec' -File -EA SilentlyContinue
    $total = ($frags | Measure-Object Length -Sum).Sum
    if ($total -gt ($DecompileCacheMaxMB * 1MB)) {
      # 超上限：按修改时间升序淘汰，直到回到上限
      foreach ($f in ($frags | Sort-Object LastWriteTime)) {
        if ((Get-ChildItem -LiteralPath $CacheDir -Filter '*.dec' -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum -le ($DecompileCacheMaxMB * 1MB)) { break }
        if (Remove-If $f.FullName) { $script:stat.CacheFreedMB += ($f.Length / 1MB) }
      }
    }
    # 按天：过老的缓存条目也回收
    foreach ($f in $frags) {
      if ($f.LastWriteTime -lt $cut) {
        if (Remove-If $f.FullName) { $script:stat.CacheFreedMB += ($f.Length / 1MB) }
      }
    }
  }
}

# ---------- 4) 运行日志 logs/（按天） ----------
Write-Host "= [4/4] 运行日志 (dir=$LogRoot) ="
if (Test-Path $LogRoot) {
  Get-ChildItem -LiteralPath $LogRoot -File -Recurse -EA SilentlyContinue | ForEach-Object {
    if ($_.LastWriteTime -lt $cut) {
      Add-Stat -bytes ([long]$_.Length) -f 1
      Remove-If $_.FullName
    }
  }
}

# ---------- 汇总 ----------
Write-Host ""
Write-Host ([string]::Format('完成。目录 {0} 个(递归仅算顶层)/文件 {1} 个，回收约 {2} MB；cache 另回收 {3} MB。',
  $script:stat.Dirs, $script:stat.Files,
  [math]::Round($script:stat.BytesFreed / 1MB, 2),
  [math]::Round($script:stat.CacheFreedMB, 2))) -ForegroundColor Green
if ($Preflight) { Write-Host "(演练模式：未真正删除任何文件)" -ForegroundColor Yellow }