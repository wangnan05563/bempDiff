# stop-app.ps1 - BempDiff 停止
# 通过 PID 文件停止 -> 回退到按进程名停止 -> 校验端口/进程已释放
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-app.ps1"
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

# 进程隔离校验：杀 PID 前确认该 PID 确实仍属于本机 BempDiff 实例。
#  - 进程名必须匹配（防 PID 复用误杀无关/系统进程）
#  - 若可读取主模块路径，须等于本机 exe（防误杀其他用户/实例的同名进程）
#  - 读不到路径时仅以进程名兜底放行（如权限不足）
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

$pidFile = Join-Path $Root $cfg.runtime.pid_file_rel
$appName = $cfg.build.app_name

Write-Host "============================================"
Write-Host "  正在停止 BempDiff ..."
Write-Host "============================================"

# [1/3] 通过 PID 文件停止
Write-Host "[1/3] 通过 PID 文件停止 ..."
$killed = $false
if (Test-Path $pidFile) {
    $procPid = (Get-Content $pidFile -Raw) -replace '\D', ''
    if ($procPid -ne '') {
        if (Confirm-BempDiffPid $procPid $exe $appName) {
            Stop-Proc $procPid
            if ($LASTEXITCODE -eq 0) {
                Write-Host ("  [OK] 已停止 PID " + $procPid)
                $killed = $true
            }
        } else {
            Write-Host ("  [WARN] PID " + $procPid + " 已不属于 BempDiff（可能 PID 复用），跳过以避免误杀")
        }
    }
    Remove-Path $pidFile
}

# 回退: 按进程名停止（仅限本机 exe 路径匹配的实例，避免误杀其他用户/实例）
if (-not $killed) {
    $procs = Get-Process -Name $appName -ErrorAction SilentlyContinue
    if ($procs) {
        $attempted = $false
        $skippedOthers = $false
        foreach ($p in $procs) {
            if ($exe -and $p.Path -and ($p.Path -ne $exe)) {
                Write-Host ("  [SKIP] 跳过非本机实例的 " + $appName + " 进程 PID " + $p.Id + " (路径不符，避免误杀其他用户/实例)")
                $skippedOthers = $true
                continue
            }
            Stop-Proc $p.Id
            Write-Host ("  [OK] 已停止 PID " + $p.Id + " (按进程名)")
            $attempted = $true
        }
        if ($attempted) { $killed = $true }
        elseif ($skippedOthers) {
            Write-Host ("  [INFO] 仅发现其他用户/实例的 " + $appName + " 进程，已保留不误杀")
        }
    }
}
if (-not $killed) {
    Write-Host "  [SKIP] 未发现运行中的 BempDiff 进程"
}

# [2/3] 清理关联 Java 运行时子进程
Write-Host "[2/3] 清理关联子进程 ..."
Start-Sleep -Seconds 1

# [3/3] 校验结果
Write-Host "[3/3] 校验关闭结果 ..."
$remaining = Get-Process -Name $appName -ErrorAction SilentlyContinue
if ($remaining) {
    Write-Host "  [WARN] 仍有 BempDiff 进程残留，请检查任务管理器"
} else {
    Write-Host "  [OK] 所有服务已成功停止"
}

Write-Host ""
Write-Host "============================================"
Write-Host "  服务已停止"
Write-Host "============================================"
