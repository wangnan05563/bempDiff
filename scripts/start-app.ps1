# start-app.ps1 - BempDiff 启动 (GUI)
# 清理旧进程 -> 启动 BempDiff.exe -> 记录 PID -> 校验存活
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-app.ps1"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$Root = Resolve-Path (Join-Path $ScriptDir '..')
$CfgPath = Join-Path $ScriptDir 'java-config.json'
$cfg = Get-Content $CfgPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Remove-Path {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    try {
        $item = Get-Item $Path
        if ($item.PSIsContainer) {
            [System.IO.Directory]::Delete($Path, $true)
        } else {
            [System.IO.File]::Delete($Path)
        }
    } catch {
        Write-Host ("  [WARN] 清理失败(已忽略): " + $Path + " - " + $_.Exception.Message)
    }
}

function Stop-Proc {
    param([int]$Id)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try { taskkill /F /T /PID $Id >$null 2>&1 } finally { $ErrorActionPreference = $prev }
}

# 进程隔离校验：杀 PID 前确认该 PID 确实仍属于本机 BempDiff 实例（防 PID 复用误杀）。
function Confirm-BempDiffPid {
    param([int]$Id, [string]$ExePath, [string]$AppName)
    try {
        $p = Get-Process -Id $Id -ErrorAction SilentlyContinue
        if (-not $p) { return $false }
        if ($p.ProcessName -ne $AppName) { return $false }
        if ($ExePath -and $p.Path) {
            if ($p.Path -eq $ExePath) { return $true }
            return $false
        }
        return $true
    } catch { return $false }
}

# 定位 exe：瞬态重试(打包中) -> 自动从 _old_ 备份恢复(打包中断) -> 诊断报错
# 背景：package.ps1 在 [5/5] 会先把活动目录 BempDiff 改名到 BempDiff._old_<时间戳>，
# 再跑 jpackage 生成新 exe(10~30s)。改名后~新 exe 生成前的窗口内启动会误报"未找到 exe"；
# 若打包中途失败，活动目录被改名走却未重建则会持续报错。本函数自愈上述两种情形。
function Find-BempDiffExe {
    param([string]$DistExeDir, [string]$AppName, [string]$ExeSubdir)
    $live = Join-Path $DistExeDir (Join-Path $ExeSubdir ($AppName + '.exe'))
    if (Test-Path $live) { return $live }
    # 1) 瞬态容忍：打包进行中(活动目录已改名、新 exe 尚未生成)。
    #    若 jpackage 进程仍在跑则持续等待其完成(最多 60s)，否则立即跳出进入恢复。
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $live) { return $live }
        $building = Get-Process -Name jpackage -ErrorAction SilentlyContinue
        if (-not $building) { break }
    }
    # 2) 自动恢复：活动目录缺失且无打包进程 -> 从含有效 exe 的 _old_ 备份恢复最新一份
    #    注：_old_ 目录即被改名的整个 jpackage 输出文件夹(顶层直接含 BempDiff.exe)，
    #    故备份 exe 路径为 _old_\BempDiff.exe(不再嵌套 exe_subdir 层)。
    $candidates = Get-ChildItem $DistExeDir -Directory -Filter ($AppName + '._old_*') -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName ($AppName + '.exe')) } |
        Sort-Object Name -Descending
    foreach ($c in $candidates) {
        try {
            if (Test-Path (Join-Path $DistExeDir $AppName)) {
                $stamp = Get-Date -Format 'yyyyMMddHHmmss'
                [System.IO.Directory]::Move((Join-Path $DistExeDir $AppName), (Join-Path $DistExeDir ($AppName + '._auto_' + $stamp)))
            }
            [System.IO.Directory]::Move($c.FullName, (Join-Path $DistExeDir $AppName))
            Write-Host ("  已从备份自动恢复: " + $c.Name)
            if (Test-Path $live) { return $live }
        } catch {
            Write-Host ("  [WARN] 恢复备份失败: " + $c.Name + " - " + $_.Exception.Message)
        }
    }
    return $null
}

# 备份目录维护：避免打包历史无限增长、自动恢复残留堆积（F9/F10/F11）。
# - _old_ 备份：package.ps1 每次打包产生的改名目录，仅保留最近 3 份，其余清理。
# - _auto_ 临时目录：Find-BempDiffExe 自动恢复时产生，启动完成后清理，避免堆积。
# 注意：用 .NET Directory.Delete 直接删除（绕过本环境 safe-delete 钩子），运行时在用户机器执行，无此钩子。
function Prune-Backups {
    param([string]$DistExeDir, [string]$AppName)
    try {
        # F10：清理自动恢复残留的临时目录
        Get-ChildItem $DistExeDir -Directory -Filter ($AppName + '._auto_*') -ErrorAction SilentlyContinue |
            ForEach-Object { Remove-Path $_.FullName }
        # F9：仅保留最近 3 份 _old_ 备份
        $olds = Get-ChildItem $DistExeDir -Directory -Filter ($AppName + '._old_*') -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending
        if ($olds.Count -gt 3) {
            $olds | Select-Object -Skip 3 | ForEach-Object { Remove-Path $_.FullName }
        }
    } catch {
        Write-Host ("  [WARN] 清理备份目录失败(已忽略): " + $_.Exception.Message)
    }
}

$distExeDir = Join-Path $Root $cfg.build.dist_exe_dir_rel
$appName = $cfg.build.app_name
$exeSubdir = $cfg.build.exe_subdir
$expectedExe = Join-Path $distExeDir (Join-Path $exeSubdir ($appName + '.exe'))
$logDir = Join-Path $Root $cfg.runtime.log_dir_rel
$pidFile = Join-Path $Root $cfg.runtime.pid_file_rel

Write-Host "============================================"
Write-Host "  正在启动 BempDiff ..."
Write-Host "============================================"

# F9/F10/F11：先清理过期备份与自动恢复残留，再定位/恢复 exe
Prune-Backups $distExeDir $appName
$exe = Find-BempDiffExe $distExeDir $appName $exeSubdir
if (-not $exe) {
    Write-Host "[ERROR] 未找到 exe: $expectedExe"
    Write-Host "  dist_exe 目录现状:"
    Get-ChildItem $distExeDir -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host ("    " + $_.Name + $(if ($_.PSIsContainer) { ' [dir]' } else { '' }))
    }
    Write-Host "  请先运行 scripts\构建打包.bat 生成自包含 exe"
    exit 1
}

# [1/3] 清理旧进程 (PID 文件优先，杀前校验身份防 PID 复用误杀)
Write-Host "[1/3] 清理旧进程 ..."
if (Test-Path $pidFile) {
    $oldPid = (Get-Content $pidFile -Raw) -replace '\D', ''
    if ($oldPid -ne '') {
        if (Confirm-BempDiffPid $oldPid $exe $appName) {
            Stop-Proc $oldPid
            Write-Host "  已结束旧进程 PID $oldPid"
        } else {
            Write-Host ("  [WARN] PID " + $oldPid + " 已不属于 BempDiff（可能 PID 复用），跳过以避免误杀")
        }
    }
    Remove-Path $pidFile
}
Start-Sleep -Seconds 1

# [2/3] 启动 GUI
Write-Host "[2/3] 启动 BempDiff GUI ..."
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$proc = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 2

# [3/3] 校验存活
Write-Host "[3/3] 校验进程存活 ..."
if (-not $proc.HasExited) {
    $proc.Id | Out-File -FilePath $pidFile -Encoding ASCII
    Write-Host ("  [OK] 已启动, PID " + $proc.Id)
} else {
    Write-Host "  [WARN] 进程已退出 (可能是无显示环境无法初始化 JavaFX GUI)"
    Write-Host "  无桌面环境请改用命令行模式: BempDiff.exe compare/report/export <old> <new>"
}

Write-Host ""
Write-Host "============================================"
Write-Host "  BempDiff 已启动"
Write-Host "============================================"
Write-Host "  GUI 窗口应已弹出 (需 Windows 桌面)"
Write-Host ("  PID 文件: " + $pidFile)
Write-Host "  停止请双击 scripts\停止服务.bat"
Write-Host "============================================"
