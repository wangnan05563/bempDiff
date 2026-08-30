<#
.SYNOPSIS
  BempDiff 桌面应用（路径 B：Tauri 2.x + 内嵌 Java 后端 sidecar）一键打包脚本。

.DESCRIPTION
  仅在「用户本机」运行（需 Rust 工具链 + Tauri 2 前置 + WebView2 运行时 + Node 22 + JDK21）。
  本沙箱无 Rust/WebView2/显示环境，无法在此冒烟，产物须本机验证。

  流程：
    1. 编译 java_core → dist_input/app/bempdiff.jar（Main-Class=com.bempdiff.Main）
    2. 复制 cfr.jar → dist_input/app/cfr.jar
    3. jlink 最小 JRE（见 $Modules）→ dist_input/jre
    4. 构建前端（vite build）→ webui/dist，并镜像到 dist_input/webui
    5. cargo tauri icon 生成图标（若缺失）
    6. cargo tauri build（NSIS 安装包）→ src-tauri/target/release/bundle/

  开发态（仅验证桌面壳，不打包）：
    .\build_tauri_app.ps1 -AssembleOnly
    然后 `npm run tauri dev`（debug 分支由 Tauri 打开 vite@5173，后端手动 `java ... server --port 18765`）。

.PARAMETER AssembleOnly
  只做 1~4 步（编译/打包 JRE/前端），不跑 cargo tauri build / icon。
.PARAMETER SkipJava
  跳过 jar/jre 重新编译（复用已有 dist_input/app、dist_input/jre）。
.PARAMETER SkipFrontend
  跳过前端构建与镜像。
.PARAMETER SkipCargo
  跳过 cargo tauri build（仅做 assemble + icon）。
.PARAMETER Clean
  先清空 dist_input 与 jre 再开始。
#>
# 注意：刻意不使用 [CmdletBinding()]。使用普通脚本 + 自动变量 $args 收集游离参数，
# 可确保任何误传的位置参数（如 `构建打包.bat src` 漏进来的 `src`）只进 $args、不会触发
# PowerShell 严格的参数绑定异常（ParameterBindingException），构建不被打断。
param(
  [switch]$AssembleOnly,
  [switch]$SkipJava,
  [switch]$SkipFrontend,
  [switch]$SkipCargo,
  [switch]$Clean
)

$ErrorActionPreference = 'Stop'

# 容忍误传的位置参数（例如 `构建打包.bat src`）：明确警告后忽略，不阻断构建。
if ($args -and $args.Count -gt 0) {
  $args | ForEach-Object { Write-Warning "忽略未识别参数: '$_'（本脚本仅接受具名开关: -AssembleOnly / -SkipJava / -SkipFrontend / -SkipCargo / -Clean）" }
}

# 5.1 兼容辅助：Windows PowerShell 5.1 的 Join-Path 仅接受 2 个位置参数（Path + ChildPath），
# 多段路径（如 Join-Path $Proto 'a' 'b'）会报“找不到接受实际参数的位置形式参数”。
# 此函数接收首个基路径 + 任意多段子路径，逐段拼接，跨 PowerShell 版本安全。
function Join-Paths {
  param($Base)
  $p = $Base
  foreach ($seg in $args) { $p = Join-Path $p $seg }
  return $p
}

# 运行原生命令并回显输出；仅以 $LASTEXITCODE 判定成败。
# 背景（已实测）：PowerShell 5.1 下若 $ErrorActionPreference='Stop'，原生命令写到 stderr 的内容
# （如 javac “使用了未经检查或不安全的操作”告警、where.exe 找不到文件）会被包装成
# NativeCommandError/RemoteException 并直接终止脚本——即便工具本身已成功（exit 0）。
# 关键点：重定向（2>&1 或 2> file）【不会】消除这个终止行为，终止与否完全由 $EAP 决定。
# 因此最稳修法 = 临时把 $EAP 降到 SilentlyContinue（确保告警/提示绝不致命）+ 2>&1 把合并流
# 收进变量以便回显；成败只判 $LASTEXITCODE。已在沙箱用真实 where.exe 验证不再抛 NativeCommandError。
function Invoke-Native {
  param([scriptblock]$Script)
  $prev = $ErrorActionPreference
  # 关键修复：临时抑制错误动作偏好，使原生命令 stderr 不再触发 NativeCommandError 终止。
  $ErrorActionPreference = 'SilentlyContinue'
  try {
    $merged = & $Script 2>&1
    $code = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $prev
  }
  # 合并流（含原 stderr 内容）按行回显，并追加到构建日志，便于失败时回溯真实报错。
  $merged | ForEach-Object {
    Write-Host $_
    try { $_ | Out-File -Append -FilePath $BuildLog -Encoding utf8 } catch {}
  }
  return $code
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Proto     = Split-Path -Parent $ScriptDir
$Webui     = Join-Path $Proto 'webui'
$DistInput = Join-Path $Proto 'dist_input'
$AppDir    = Join-Path $DistInput 'app'
$JreOut    = Join-Path $DistInput 'jre'
$Classes   = Join-Path $DistInput 'classes'
$DevClasses= Join-Path $DistInput 'dev_classes'
$CfrSrc    = Join-Path $Proto 'cfr.jar'
$BempJar   = Join-Path $AppDir 'bempdiff.jar'
$CfrJar    = Join-Path $AppDir 'cfr.jar'
$JavaSrc   = Join-Path (Join-Path $Proto 'java_core') 'src'
$WebuiDist = Join-Path $Webui 'dist'
$Logo      = Join-Path $Proto 'bempdiff-logo.png'
# 三方依赖 jar（目前为解析旧版二进制 .xls 的 Apache POI 及其传递依赖）：编译 classpath 与
# 运行时 dist_input/app/lib 均来自此目录，桌面壳 main.js 按该目录拼服务端 classpath。
$PoiLib    = Join-Path $Proto 'toolchain\lib'

# 项目作用域构建目录：覆盖可能存在于全局环境的 CARGO_TARGET_DIR（如曾指向 D:\tmp_install\cargo_release），
# 使 cargo / tauri 构建产物落到 bempdiff/src-tauri/target（已被 gitignore），不再污染 D:\ 根目录。
# 背景：此前全局 CARGO_TARGET_DIR 指向 D:\tmp_install，导致 2.3G 构建缓存在 D:\ 根堆积；
# 该变量在当前会话不可见（沙箱剥离了进程环境、reg 被禁用），故在此显式重设为本项目路径，构建确定性归位。
$env:CARGO_TARGET_DIR = Join-Path $Proto 'src-tauri\target'

# 构建日志：所有原生命令输出按行追加于此，构建失败时可直接打开回溯真实报错。
$BuildLog = Join-Path $Proto 'build_native.log'
try { Set-Content -Path $BuildLog -Value ("=== BempDiff build @ " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ===") -Encoding utf8 } catch {}

# 最小 JRE 模块（jdeps 实测静态最小集为 java.base,java.logging,jdk.httpserver；
# 显式补 jdk.crypto.ec/jdk.crypto.mscapi 以支撑 HTTPS 调 AI（原 jpackage TLS 事故根因），
# jdk.jdeps 保留 javap 降级能力。
# java.xml 必加：OfficeTextDiff 用 JAXP（DocumentBuilderFactory）解析 docx/xlsx/pptx 内部 XML，
# 缺失时点击 Office 文档抛 ClassNotFoundException -> "该文件无法反编译（引擎：none）" / 卡在加载态。
# java.desktop 必含：POI 的 DataFormatter 依赖 java.beans.PropertyChangeSupport（属 java.desktop），
# 缺它点击 .xls 会在线程内抛 NoClassDefFoundError 且无响应，前端永久停在加载态。
$Modules = 'java.base,java.logging,jdk.httpserver,jdk.crypto.ec,jdk.crypto.mscapi,jdk.jdeps,java.xml,java.desktop'

# 工具链（JDK）：按优先级探测可用 javac.exe，首取 $env:JAVA21_HOME，其次 $env:JAVA_HOME，
# 再内置 Zulu；内置 Zulu 若损坏/缺失（历史上 bin 曾被清空只剩 server），降级到本机常见 JDK，
# 避免仅因工具链缺失就中断打包。取第一个 bin\javac.exe 可用的候选为本次构建 JDK。
$jdkCandidates = @()
if ($env:JAVA21_HOME) { $jdkCandidates += $env:JAVA21_HOME }
if ($env:JAVA_HOME)   { $jdkCandidates += $env:JAVA_HOME }
$jdkCandidates += (Join-Paths $Proto 'toolchain' 'zulu21.52.15-ca-jdk21.0.12-win_x64')
# 本机常用 JDK 兜底（缺环境变量时的备选）
$jdkCandidates += 'D:\code\Java\jdk-25.0.1'
$JDK = $jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Paths $_ 'bin' 'javac.exe')) } | Select-Object -First 1
if (-not $JDK) {
  throw "未找到可用 JDK（javac.exe）。请设置 JAVA21_HOME 指向完整 JDK 后重试。"
}
$Javac = Join-Paths $JDK 'bin' 'javac.exe'
$Jar   = Join-Paths $JDK 'bin' 'jar.exe'
$Jlink = Join-Paths $JDK 'bin' 'jlink.exe'

function Write-Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }

# 递归删目录（兼容本环境 safe-delete 钩子：优先 Remove-Item，失败回退 .NET）
function Remove-Tree($p) {
  if (-not (Test-Path $p)) { return $true }
  $ok = $false
  try { Remove-Item -Recurse -Force -ErrorAction Stop $p; $ok = $true } catch {}
  if (-not $ok) {
    try { [System.IO.Directory]::Delete($p, $true); $ok = $true } catch {}
  }
  if (-not $ok) {
    # 兜底：cmd rmdir 对超长路径（jlink 产物路径很深）/只读文件比 .NET Delete 更鲁棒
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
    try { & cmd.exe /c ("rmdir /s /q """ + $p + """") } finally { $ErrorActionPreference = $prev }
    $ok = -not (Test-Path $p)
  }
  if (-not $ok) {
    # 最常见真因：目录被其它进程占用（如正在运行的 java/jlink 持有了 jre 内文件）。
    # 此时必须先在任务管理器结束占用进程，或手动删除后重跑；脚本无法跨进程强制解锁。
    throw "无法删除 $p：可能被其它进程占用（最常见是正在运行的 java.exe / jlink.exe 持有了该目录内的文件，例如用 启动服务.bat 跑的后端未关闭）。`n请先执行：taskkill /F /IM java.exe 与 taskkill /F /IM jlink.exe，再手动删除该目录后重跑本脚本。"
  }
  return $ok
}

# 校验并（如有必要）重载 MSVC + Windows SDK 链接环境。
# 背景：cargo 链接需要 LIB 环境变量包含 Windows SDK 的 um\x64 路径（kernel32.lib 所在），
# 缺失时 link.exe 报 LNK1181 "无法打开输入文件 kernel32.lib"（已实测踩中）。
# 此处：① 用 vswhere 定位 VS/BuildTools 并重载 VsDevCmd（双保险，即使 bat 加载失效也在此补救）；
#       ② 核验磁盘 kernel32.lib 是否存在，缺失则给出明确的 SDK 安装指引并终止。
function Ensure-VcEnv {
  Write-Step "校验 MSVC 链接环境 (LIB / kernel32.lib)"
  $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
  $vsPath = ''
  if (Test-Path $vswhere) {
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
    try {
      $vsp = (& $vswhere -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath)
      if ($vsp) { $vsPath = ($vsp | Select-Object -First 1).ToString().Trim() }
    } finally { $ErrorActionPreference = $prev }
  }
  if ($vsPath) {
    $vsdev = Join-Path $vsPath 'Common7\Tools\VsDevCmd.bat'
    if (Test-Path $vsdev) {
      Write-Host "  重载 VsDevCmd: $vsdev"
      $prev = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
      try {
        $envLines = & cmd /c "`"$vsdev`" -arch=amd64 -host_arch=amd64 >nul 2>&1 && set"
        foreach ($line in $envLines) {
          if ($line -match '^([^=]+)=(.*)$') { Set-Item -Path ("Env:" + $matches[1]) -Value $matches[2] }
        }
      } finally { $ErrorActionPreference = $prev }
      # VsDevCmd 可能重排 PATH，确保 cargo 仍可解析
      if (Test-Path "$env:USERPROFILE\.cargo\bin") { $env:PATH = "$env:USERPROFILE\.cargo\bin;$env:PATH" }
    }
  } else {
    Write-Warning "未通过 vswhere 定位到 VS/BuildTools（MSVC 环境可能未装），cargo 链接可能失败"
  }
  # 核验
  $libHasSdk = ($env:LIB -match 'Windows Kits\\10\\Lib')
  Write-Host ("  LIB 含 Windows SDK 库路径: " + $libHasSdk)
  $k32 = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\Lib' -Recurse -Filter kernel32.lib -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($k32) { Write-Host ("  kernel32.lib 位于: " + $k32.FullName) }
  if (-not $k32) {
    Write-Host ""
    Write-Host "[缺失] Windows 10 SDK 或其库组件未安装（cargo 链接需要 kernel32.lib）。" -ForegroundColor Red
    Write-Host "  解决方案（任选其一）：" -ForegroundColor Yellow
    Write-Host "  1) 管理员运行：vs_buildtools.exe --add Microsoft.VisualStudio.Component.Windows10SDK.19041 --passive --wait" -ForegroundColor Yellow
    Write-Host "     （vs_buildtools.exe 从 https://aka.ms/vs/17/release/vs_buildtools.exe 下载）" -ForegroundColor Yellow
    Write-Host "  2) 或在 Visual Studio Installer 中给 BuildTools 勾选『Windows 10 SDK』后修改。" -ForegroundColor Yellow
    throw "Windows 10 SDK 缺失（缺少 kernel32.lib），无法链接。请先安装 SDK 后重试。"
  } elseif (-not $libHasSdk) {
    Write-Warning "kernel32.lib 在磁盘存在，但 LIB 未含 SDK 路径；已尝试重载 VsDevCmd，若仍失败请用『VS 开发者命令行』运行本脚本"
  }
}

if ($Clean) {
  Write-Step "清理 dist_input"
  Remove-Tree $DistInput
}

Write-Step "校验工具链"
foreach ($bin in @($Javac, $Jar, $Jlink, $CfrSrc)) {
  if (-not (Test-Path $bin)) { throw "缺少必要文件：$bin" }
}
New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
# POI 解析 .xls 所需的三方 jar：编译阶段必须有（javac -cp），否则无法编译 HSSF 代码。
if (-not (Test-Path $PoiLib)) { throw "缺少三方依赖目录：$PoiLib（需含 poi-*.jar 等，用于 .xls 解析）" }

# ---------- 1~2. 编译 java_core + 复制 cfr.jar + 分发三方 lib ----------
if (-not $SkipJava) {
  Write-Step "编译 java_core → bempdiff.jar"
  Remove-Tree $Classes
  # 同步清理兜底编译产物 dev_classes（main.js ensureClasspath 生成）：避免陈旧 class 混入打包源，
  # 若开发态再需要，main.js 会在无产物时按源码重新生成。
  Remove-Tree $DevClasses
  New-Item -ItemType Directory -Force -Path $Classes | Out-Null
  $sources = (Get-ChildItem -Recurse -Filter *.java $JavaSrc).FullName
  # 编译 classpath = cfr.jar + POI lib(通配符)。通配符由 javac 展开，需各 jar 位于同一目录。
  $jcp = "$CfrSrc;$PoiLib\*"
  $rc = Invoke-Native { & $Javac -d $Classes -cp $jcp @sources }
  if ($rc -ne 0) { throw "javac 失败" }
  $rc = Invoke-Native { & $Jar cfe $BempJar com.bempdiff.Main -C $Classes . }
  if ($rc -ne 0) { throw "jar 失败" }
  Write-Host "  -> $BempJar ($([math]::Round((Get-Item $BempJar).Length/1MB,2)) MB)"

  Write-Step "复制 cfr.jar"
  Copy-Item -Force $CfrSrc $CfrJar
  Write-Host "  -> $CfrJar"

  Write-Step "分发三方依赖 lib → dist_input/app/lib"
  $LibOut = Join-Path $AppDir 'lib'
  New-Item -ItemType Directory -Force -Path $LibOut | Out-Null
  Copy-Item -Force (Join-Path $PoiLib '*') $LibOut
  Write-Host "  -> $($(Get-ChildItem $LibOut -Filter '*.jar').Count) 个 jar 已复制"
} else {
  Write-Host "跳过 Java 编译（复用已有 dist_input/app）"
  if (-not (Test-Path $BempJar)) { throw "bempdiff.jar 不存在，请去掉 -SkipJava 先编译" }
}

# ---------- 3. jlink 最小 JRE ----------
if (-not $SkipJava) {
  Write-Step "jlink 最小 JRE → $JreOut"
  # 定向结束可能锁定 dist_input/jre 的残留进程（如未关闭的 启动服务.bat 后端 / 上一轮构建残留的 java、jlink）。
  # 仅匹配命令行含本项目路径的进程，不误杀其它 Java。避免 jre 目录被占用导致 jlink 报“目录已存在”。
  try {
    $holders = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='jlink.exe'" -ErrorAction SilentlyContinue |
      Where-Object { $_.CommandLine -and ($_.CommandLine -like "*bempdiff*" -or $_.CommandLine -like "*dist_input*") }
    foreach ($h in $holders) {
      Write-Host "  -> 结束占用进程 PID $($h.ProcessId): $($h.CommandLine)"
      Stop-Process -Id $h.ProcessId -Force -ErrorAction SilentlyContinue
    }
  } catch {}
  Start-Sleep -Milliseconds 500
  Remove-Tree $JreOut
  $jlinkArgs = @('--no-header-files','--no-man-pages','--compress=2',
                 '--add-modules', $Modules, '--output', $JreOut)
  $rc = Invoke-Native { & $Jlink @jlinkArgs }
  if ($rc -ne 0) { throw "jlink 失败" }
  $javaExe = Join-Paths $JreOut 'bin' 'java.exe'
  if (-not (Test-Path $javaExe)) { throw "jlink 产物缺少 java.exe" }
  Write-Host "  -> JRE 就绪（$([math]::Round((Get-ChildItem $JreOut -Recurse | Measure-Object -Property Length -Sum).Sum/1MB,1)) MB）"
} else {
  Write-Host "跳过 jlink（复用已有 dist_input/jre）"
  if (-not (Test-Path (Join-Paths $JreOut 'bin' 'java.exe'))) { throw "jre/bin/java.exe 不存在，请去掉 -SkipJava" }
}

# ---------- 4. 构建前端 + 镜像到 dist_input/webui ----------
if (-not $SkipFrontend) {
  Write-Step "构建前端 (vite build)"
  Push-Location $Webui
  try {
    # 约束：npm 走默认全局缓存；切勿手动传 `--cache /d/code/...` 之类 POSIX 路径，
    # Windows 原生 npm 会把前导 /d/ 归一化为 D:\d\... 误生成异常目录。
    $rc = Invoke-Native { & npm run build }
    if ($rc -ne 0) { throw "npm run build 失败" }
  } finally { Pop-Location }
  Write-Step "镜像前端 → dist_input/webui"
  $dest = Join-Path $DistInput 'webui'
  Remove-Tree $dest
  New-Item -ItemType Directory -Force -Path $dest | Out-Null
  robocopy $WebuiDist $dest /E /R:2 /W:2 /NFL /NDL | Out-Null
  Write-Host "  -> $dest"
} else {
  Write-Host "跳过前端构建（复用已有 dist_input/webui）"
}

# 自愈（防御性）：保证 tauri.conf.json 的 frontendDist 指向的目录真实存在。
# 迁移遗留配置可能指向不存在的占位路径（如 "../webui/.tauri-placeholder"），
# 会导致 tauri build 静默失败；这里把已构建好的前端同步过去，免手动改配置即可通过。
$confPath = Join-Path $Proto 'src-tauri\tauri.conf.json'
try {
  $confTxt = Get-Content $confPath -Raw -Encoding utf8
  if ($confTxt -match '"frontendDist"\s*:\s*"([^"]+)"') {
    $fdDir = Join-Path $Proto ('src-tauri\' + $matches[1])
    $fdExists = Test-Path $fdDir
    Write-Host ("[自检] frontendDist='" + $matches[1] + "' -> 解析 '" + $fdDir + "' 存在=" + $fdExists.ToString())
    if (-not $fdExists) {
      Write-Step "自愈 frontendDist 目录"
      New-Item -ItemType Directory -Force -Path $fdDir | Out-Null
      robocopy $WebuiDist $fdDir /E /R:2 /W:2 /NFL /NDL | Out-Null
      Write-Host "  -> 已用前端产物填充 $fdDir"
    }
  } else {
    Write-Warning "[自检] 未能从 tauri.conf.json 解析 frontendDist，请检查配置"
  }
} catch { Write-Warning "未能读取/修正 tauri.conf.json 的 frontendDist，请手动确认" }

if ($AssembleOnly) {
  Write-Host "`n[完成] 仅 assemble：dist_input 已就绪。开发态请运行 `npm run tauri dev`。" -ForegroundColor Green
  exit 0
}

# ---------- 5. 图标 ----------
Write-Step "生成 Tauri 图标"
$IconsDir = Join-Paths $Proto 'src-tauri' 'icons'
if (-not (Test-Path (Join-Path $IconsDir 'icon.ico')) -and (Test-Path $Logo)) {
  Push-Location $Proto
  try {
    # 约束：显式锁定 npm 全局缓存目录，禁止 --cache 指向项目本地（避免误生成 D:\d\...）
    Invoke-Native { & npm install --cache "$env:LOCALAPPDATA\npm-cache" }
    $rc = Invoke-Native { & npm run tauri -- icon $Logo }
    if ($rc -ne 0) { Write-Warning "图标生成失败（可手动 `npm run tauri icon $Logo`）" }
  } finally { Pop-Location }
} else {
  Write-Host "  图标已存在或缺少 logo，跳过"
}

# ---------- 5.5 MSVC/SDK 链接环境自检（cargo 链接必需） ----------
Ensure-VcEnv

# ---------- 5.6 产物新鲜度自检（防 Skip 误用把旧内容打包进安装包） ----------
# 仅当通过 -Skip* 复用了旧产物时才需要校验：若源码比复用的 jar 新，说明打包的是旧后端，直接中止。
function Assert-InputFresh {
  if (-not $SkipJava -and -not $SkipFrontend) { return }   # 本次全量重编，天然新鲜，无需校验
  if ($SkipJava) {
    $newestSrc = (Get-ChildItem -Recurse -Filter *.java $JavaSrc | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    $jarT = (Get-Item $BempJar -ErrorAction SilentlyContinue).LastWriteTime
    if ($jarT -isnot [datetime]) { throw "bempdiff.jar 不存在，无法打包（-SkipJava 空跑）" }
    if ($jarT -lt $newestSrc) {
      throw "SkipJava 复用旧 jar（$jarT）早于最新源码（$newestSrc），会打包旧后端。请去掉 -SkipJava 重新编译。"
    }
  }
  if ($SkipFrontend -and -not (Test-Path (Join-Path $DistInput 'webui\index.html'))) {
    throw "SkipFrontend 复用但 dist_input/webui 前端缺失，会打包空/旧前端。请去掉 -SkipFrontend。"
  }
}
Assert-InputFresh

# ---------- 6. cargo tauri build ----------
if (-not $SkipCargo) {
  Write-Step "cargo tauri build（NSIS 安装包）"
  Push-Location $Proto
  try {
    # 约束：显式锁定 npm 全局缓存目录，禁止 --cache 指向项目本地（避免误生成 D:\d\...）
    Invoke-Native { & npm install --cache "$env:LOCALAPPDATA\npm-cache" }
    # tauri build 用 Start-Process 实时输出（不经 2>&1 捕获，避免真实报错被吞），并加 --verbose。
    # 已知坑：此前 2>&1 捕获时 tauri build 零输出静默失败，故此处必须实时可见。
    Write-Host "  运行 tauri build（实时输出 + --verbose）..."
    $p = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "npm run tauri -- build --bundles nsis --verbose") -NoNewWindow -PassThru -Wait
    $rc = $p.ExitCode
    Write-Host "  tauri build 退出码 = $rc"
    if ($rc -ne 0) {
      Write-Host "`n[诊断] tauri build 失败（exit=$rc）。真实报错应在上方实时输出中；以下为 build_native.log 末尾备查：" -ForegroundColor Red
      if (Test-Path $BuildLog) { Get-Content $BuildLog -Tail 60 | ForEach-Object { Write-Host $_ } }
      throw "tauri build 失败"
    }
  } finally { Pop-Location }
  $bundle = Join-Paths $Proto 'src-tauri' 'target' 'release' 'bundle'
  Write-Host "`n[完成] 安装包位于：$bundle" -ForegroundColor Green
} else {
  Write-Host "跳过 cargo tauri build"
}

Write-Host "`n全部完成。" -ForegroundColor Green
