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

$distExeDir = Join-Path $Root $cfg.build.dist_exe_dir_rel
$exe = Join-Path $distExeDir (Join-Path $cfg.build.exe_subdir ($cfg.build.app_name + '.exe'))
$appName = $cfg.build.app_name
$logDir = Join-Path $Root $cfg.runtime.log_dir_rel
$pidFile = Join-Path $Root $cfg.runtime.pid_file_rel

Write-Host "============================================"
Write-Host "  正在启动 BempDiff ..."
Write-Host "============================================"

if (-not (Test-Path $exe)) {
    Write-Host "[ERROR] 未找到 exe: $exe"
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
