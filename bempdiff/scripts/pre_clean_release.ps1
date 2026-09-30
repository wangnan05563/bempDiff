<#
.SYNOPSIS
  electron-builder 打包前预清理 release\win-unpacked，失败时自动诊断锁持有进程。

.DESCRIPTION
  背景（2026-09-11 实测踩坑）：electron-builder 打包第一步 EnsureEmptyDir 要删除
  release\win-unpacked\resources\app.asar；若该目录被残留进程占用（本次是 WorkBuddy
  桌面应用的文件索引守护进程持有了泄漏句柄），electron-builder 直接报
  ERR_ELECTRON_BUILDER_CANNOT_EXECUTE，且报错栈看不出锁持有者是谁。

  本脚本：
    1. 递归删除 release\win-unpacked（最多 3 轮重试，覆盖 AV/索引器瞬时占用）；
    2. 仍删不掉时，用 Windows Restart Manager API 精确找出占用文件的进程
       （PID + 进程名 + 命令行），给出明确处置提示后以非零码退出，让打包提前失败
       而不是在 electron-builder 深处炸出一段难懂的反编译栈。

  兼容性：Windows PowerShell 5.1；$ErrorActionPreference='Stop' 下原生命令 stderr
  会终止脚本，故全脚本只用 .NET/cmdlet，不裸调原生命令。
#>

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Proto     = Split-Path -Parent $ScriptDir          # bempdiff/
$Release   = Join-Path (Split-Path -Parent $Proto) 'release'
$Target    = Join-Path $Release 'win-unpacked'

function Test-DirEmpty($p) {
  -not (Test-Path $p) -or -not (@(Get-ChildItem -LiteralPath $p -Force -ErrorAction SilentlyContinue).Count)
}

# 递归删除（Remove-Item -> .NET -> cmd rmdir 三级回退，jlink 产物/asar 深路径均覆盖）
function Remove-TreeHard($p) {
  if (-not (Test-Path $p)) { return $true }
  $ok = $false
  try { Remove-Item -Recurse -Force -ErrorAction Stop $p; $ok = $true } catch {}
  if (-not $ok) { try { [System.IO.Directory]::Delete($p, $true); $ok = $true } catch {} }
  if (-not $ok) {
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
    try { & cmd.exe /c ("rmdir /s /q """ + $p + """") } finally { $ErrorActionPreference = $prev }
    $ok = -not (Test-Path $p)
  }
  return $ok
}

# 结束“自己人”进程：可执行文件位于待删目录内的残留进程（上一轮打包后没退出的 app /
# 手工启动的 win-unpacked\BempDiff.exe）。它们的句柄必然锁住 resources\app.asar，
# 且属于本项目自己产生的进程，可以安全结束。外部进程（索引器/杀软）绝不在此处理。
function Stop-OwnAppProcesses {
  $norm = $Target.TrimEnd('\') + '\'
  $killed = @()
  $procs = @()
  try { $procs = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue) } catch {}
  foreach ($p in $procs) {
    if (-not $p.ExecutablePath) { continue }
    if (-not $p.ExecutablePath.StartsWith($norm, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
    try {
      Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
      $killed += ($p.Name + " (PID " + $p.ProcessId + ")")
    } catch {}
  }
  return $killed
}

# Restart Manager：找出锁定指定文件的进程（PID + 进程名）
function Find-FileLockers([string]$path) {
  $src = @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class RmProbe {
  [StructLayout(LayoutKind.Sequential)]
  struct RM_UNIQUE_PROCESS { public int dwProcessId; public System.Runtime.InteropServices.ComTypes.FILETIME ProcessStartTime; }
  const int CCH_RM_MAX_APP_NAME = 255; const int CCH_RM_MAX_SVC_NAME = 63;
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  struct RM_PROCESS_INFO {
    public RM_UNIQUE_PROCESS Process;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCH_RM_MAX_APP_NAME + 1)] public string strAppName;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCH_RM_MAX_SVC_NAME + 1)] public string strServiceShortName;
    public int ApplicationType; public uint AppStatus; public uint TSSessionId;
    [MarshalAs(UnmanagedType.Bool)] public bool bRestartable;
  }
  [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
  static extern int RmRegisterResources(uint h, uint nFiles, string[] files, uint nApps, RM_UNIQUE_PROCESS[] apps, uint nSvcs, string[] svcs);
  [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
  static extern int RmStartSession(out uint h, int flags, string key);
  [DllImport("rstrtmgr.dll")]
  static extern int RmEndSession(uint h);
  [DllImport("rstrtmgr.dll")]
  static extern int RmGetList(uint h, out uint needed, ref uint count, [In, Out] RM_PROCESS_INFO[] info, ref uint reasons);
  public static List<string> Find(string path) {
    var result = new List<string>(); uint h; string key = Guid.NewGuid().ToString();
    if (RmStartSession(out h, 0, key) != 0) return result;
    try {
      if (RmRegisterResources(h, 1, new[] { path }, 0, null, 0, null) != 0) return result;
      uint needed = 0, count = 0, reasons = 0;
      int res = RmGetList(h, out needed, ref count, null, ref reasons);
      if (res == 234) {
        var buf = new RM_PROCESS_INFO[needed]; count = needed;
        if (RmGetList(h, out needed, ref count, buf, ref reasons) == 0)
          for (int i = 0; i < count; i++) result.Add(buf[i].Process.dwProcessId + "\t" + buf[i].strAppName);
      }
    } finally { RmEndSession(h); }
    return result;
  }
}
'@
  try { Add-Type -TypeDefinition $src } catch { }   # 已编译过则忽略（同进程内重复 Add-Type）
  return [RmProbe]::Find($path)
}

if (Test-DirEmpty $Target) {
  Write-Host "[OK] win-unpacked 干净（不存在或已空）"
  exit 0
}

Write-Host "[STEP] 预清理 win-unpacked ..."

# Step 0：先结束本项目自己残留的进程（它们的句柄必须先释放，后面删才可能成功）
$killed = Stop-OwnAppProcesses
if ($killed.Count) {
  Write-Host ("  -> 已结束残留进程：" + ($killed -join ", "))
  Start-Sleep -Seconds 1
}

# 多轮重试：AV / 索引器的瞬时占用通常 1~2 秒内自行释放；遇到杀软全量扫描则需更久，
# 故用递增退避（1/2/3/5/8 秒）覆盖 ~20 秒窗口。
$waits = @(1, 2, 3, 5, 8)
for ($i = 0; $i -lt $waits.Count; $i++) {
  if (Remove-TreeHard $Target) {
    Write-Host ("  -> 已删除 $Target（第 " + ($i + 1) + " 轮）")
    exit 0
  }
  Start-Sleep -Seconds $waits[$i]
}

# 重试仍失败：诊断锁持有者，给出可执行结论
Write-Host ("[ERROR] 无法删除 $Target（" + $waits.Count + " 轮退避重试后仍被占用）") -ForegroundColor Red
$left = @(Get-ChildItem -LiteralPath $Target -Recurse -File -Force -ErrorAction SilentlyContinue)
Write-Host ("  残留文件数: " + $left.Count)
$probeFile = Join-Path $Target 'resources\app.asar'
if (-not (Test-Path $probeFile)) {
  $probeFile = if ($left.Count) { $left[0].FullName } else { '' }
}
if ($probeFile) {
  Write-Host ("  探测文件: " + $probeFile)
  # Add-Type 在受限 shell（如禁止运行时编译的沙箱）会直接失败；必须兜住，
  # 否则整段诊断被终止、只剩一句无信息量的报错（2026-09-16 实测）。
  $lockers = @()
  try { $lockers = @(Find-FileLockers $probeFile) } catch { Write-Host ("  诊断不可用：" + $_.Exception.Message) }
  if ($lockers) {
    Write-Host "  锁持有者：" -ForegroundColor Yellow
    foreach ($l in $lockers) {
      # NOTE: $pid is a READ-ONLY automatic variable (current process id) - assigning to it
      # throws "Cannot overwrite variable PID" under $ErrorActionPreference='Stop' (hit 2026-09-16,
      # which masked the real holder and made the build fail with no actionable info).
      $procPid = [int]($l.Split("`t")[0])
      $pname   = $l.Split("`t")[1]
      $cmd = ''
      $owner = ''
      try {
        $proc = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $procPid) -ErrorAction Stop
        $cmd = $proc.CommandLine
        if ($proc.ExecutablePath) { $owner = $proc.ExecutablePath }
      } catch {}
      Write-Host ("    PID " + $procPid + "  " + $pname)
      if ($owner) { Write-Host ("      exe: " + $owner) }
      if ($cmd)   { Write-Host ("      cmd: " + $cmd) }
      # 2026-09-16 实测：持有者常常是 IDE/助手类应用的后台守护进程（本次为 WorkBuddy.exe
      # 的 daemon-app-server），它们索引整个工作区时会把 asar 一起打开且不放删除共享。
      if ($pname -match 'WorkBuddy|Code|devenv|idea|SearchIndexer|SearchProtocol') {
        Write-Host "      ^^ 后台索引类进程：退出/重启该应用即可释放；若不想动它，改用备用输出目录打包。"
      }
    }
  } elseif ($lockers.Count -eq 0) {
    Write-Host "  Restart Manager 未报告持有者：多为杀毒/搜索索引的内核句柄（RM 看不到），"
    Write-Host "  典型如 MsMpEng.exe（Defender）扫描大体积 asar 时会长时间持有且不共享删除权限。"
  }
}
Write-Host "  处置优先级：" -ForegroundColor Yellow
Write-Host "    1) 结束上方列出的进程（taskkill /F /T /PID <pid>）；看不到 PID 时多为杀软，"
Write-Host "       把本项目目录加入杀软排除项后重跑（Defender 排除项需管理员 PowerShell："
Write-Host "       Add-MpPreference -ExclusionPath '<项目根>'）。"
Write-Host "    2) 退而求其次：重启一次，句柄必然释放，之后再跑打包。"
exit 1
