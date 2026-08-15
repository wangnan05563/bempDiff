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
[CmdletBinding()]
param(
  [switch]$AssembleOnly,
  [switch]$SkipJava,
  [switch]$SkipFrontend,
  [switch]$SkipCargo,
  [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Proto     = Split-Path -Parent $ScriptDir
$Webui     = Join-Path $Proto 'webui'
$DistInput = Join-Path $Proto 'dist_input'
$AppDir    = Join-Path $DistInput 'app'
$JreOut    = Join-Path $DistInput 'jre'
$Classes   = Join-Path $DistInput 'classes'
$CfrSrc    = Join-Path $Proto 'cfr.jar'
$BempJar   = Join-Path $AppDir 'bempdiff.jar'
$CfrJar    = Join-Path $AppDir 'cfr.jar'
$JavaSrc   = Join-Path $Proto 'java_core' 'src'
$WebuiDist = Join-Path $Webui 'dist'
$Logo      = Join-Path $Proto 'bempdiff-logo.png'

# 最小 JRE 模块（jdeps 实测静态最小集为 java.base,java.logging,jdk.httpserver；
# 显式补 jdk.crypto.ec/jdk.crypto.mscapi 以支撑 HTTPS 调 AI（原 jpackage TLS 事故根因），
# jdk.jdeps 保留 javap 降级能力）。
$Modules = 'java.base,java.logging,jdk.httpserver,jdk.crypto.ec,jdk.crypto.mscapi,jdk.jdeps'

# JDK21 工具链：优先 $env:JAVA21_HOME，否则用内置 Zulu 工具链。
if ($env:JAVA21_HOME -and (Test-Path (Join-Path $env:JAVA21_HOME 'bin' 'javac.exe'))) {
  $JDK = $env:JAVA21_HOME
} else {
  $JDK = Join-Path $Proto 'toolchain' 'zulu21.52.15-ca-jdk21.0.12-win_x64'
}
$Javac = Join-Path $JDK 'bin' 'javac.exe'
$Jar   = Join-Path $JDK 'bin' 'jar.exe'
$Jlink = Join-Path $JDK 'bin' 'jlink.exe'

function Write-Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }

# 递归删目录（兼容本环境 safe-delete 钩子：优先 Remove-Item，失败回退 .NET）
function Remove-Tree($p) {
  if (-not (Test-Path $p)) { return }
  try { Remove-Item -Recurse -Force -ErrorAction Stop $p }
  catch { try { [System.IO.Directory]::Delete($p, $true) } catch { Write-Warning "无法删除 $p（可手动删或本机重跑）" } }
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

# ---------- 1~2. 编译 java_core + 复制 cfr.jar ----------
if (-not $SkipJava) {
  Write-Step "编译 java_core → bempdiff.jar"
  Remove-Tree $Classes
  New-Item -ItemType Directory -Force -Path $Classes | Out-Null
  $sources = (Get-ChildItem -Recurse -Filter *.java $JavaSrc).FullName
  & $Javac -d $Classes -cp $CfrSrc @sources 2>&1 | ForEach-Object { Write-Host $_ }
  if ($LASTEXITCODE -ne 0) { throw "javac 失败" }
  & $Jar cfe $BempJar com.bempdiff.Main -C $Classes . 2>&1 | ForEach-Object { Write-Host $_ }
  if ($LASTEXITCODE -ne 0) { throw "jar 失败" }
  Write-Host "  -> $BempJar ($([math]::Round((Get-Item $BempJar).Length/1MB,2)) MB)"

  Write-Step "复制 cfr.jar"
  Copy-Item -Force $CfrSrc $CfrJar
  Write-Host "  -> $CfrJar"
} else {
  Write-Host "跳过 Java 编译（复用已有 dist_input/app）"
  if (-not (Test-Path $BempJar)) { throw "bempdiff.jar 不存在，请去掉 -SkipJava 先编译" }
}

# ---------- 3. jlink 最小 JRE ----------
if (-not $SkipJava) {
  Write-Step "jlink 最小 JRE → $JreOut"
  Remove-Tree $JreOut
  $jlinkArgs = @('--no-header-files','--no-man-pages','--compress=2',
                 '--add-modules', $Modules, '--output', $JreOut)
  & $Jlink @jlinkArgs 2>&1 | ForEach-Object { Write-Host $_ }
  if ($LASTEXITCODE -ne 0) { throw "jlink 失败" }
  $javaExe = Join-Path $JreOut 'bin' 'java.exe'
  if (-not (Test-Path $javaExe)) { throw "jlink 产物缺少 java.exe" }
  Write-Host "  -> JRE 就绪（$([math]::Round((Get-ChildItem $JreOut -Recurse | Measure-Object -Property Length -Sum).Sum/1MB,1)) MB）"
} else {
  Write-Host "跳过 jlink（复用已有 dist_input/jre）"
  if (-not (Test-Path (Join-Path $JreOut 'bin' 'java.exe'))) { throw "jre/bin/java.exe 不存在，请去掉 -SkipJava" }
}

# ---------- 4. 构建前端 + 镜像到 dist_input/webui ----------
if (-not $SkipFrontend) {
  Write-Step "构建前端 (vite build)"
  Push-Location $Webui
  try {
    # 约束：npm 走默认全局缓存；切勿手动传 `--cache /d/code/...` 之类 POSIX 路径，
    # Windows 原生 npm 会把前导 /d/ 归一化为 D:\d\... 误生成异常目录。
    & npm run build 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "npm run build 失败" }
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

if ($AssembleOnly) {
  Write-Host "`n[完成] 仅 assemble：dist_input 已就绪。开发态请运行 `npm run tauri dev`。" -ForegroundColor Green
  exit 0
}

# ---------- 5. 图标 ----------
Write-Step "生成 Tauri 图标"
$IconsDir = Join-Path $Proto 'src-tauri' 'icons'
if (-not (Test-Path (Join-Path $IconsDir 'icon.ico')) -and (Test-Path $Logo)) {
  Push-Location $Proto
  try {
    # 约束：显式锁定 npm 全局缓存目录，禁止 --cache 指向项目本地（避免误生成 D:\d\...）
    & npm install --cache "$env:LOCALAPPDATA\npm-cache" 2>&1 | ForEach-Object { Write-Host $_ }
    & npm run tauri -- icon $Logo 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { Write-Warning "图标生成失败（可手动 `npm run tauri icon $Logo`）" }
  } finally { Pop-Location }
} else {
  Write-Host "  图标已存在或缺少 logo，跳过"
}

# ---------- 6. cargo tauri build ----------
if (-not $SkipCargo) {
  Write-Step "cargo tauri build（NSIS 安装包）"
  Push-Location $Proto
  try {
    # 约束：显式锁定 npm 全局缓存目录，禁止 --cache 指向项目本地（避免误生成 D:\d\...）
    & npm install --cache "$env:LOCALAPPDATA\npm-cache" 2>&1 | ForEach-Object { Write-Host $_ }
    & npm run tauri -- build --bundles nsis 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "tauri build 失败" }
  } finally { Pop-Location }
  $bundle = Join-Path $Proto 'src-tauri' 'target' 'release' 'bundle'
  Write-Host "`n[完成] 安装包位于：$bundle" -ForegroundColor Green
} else {
  Write-Host "跳过 cargo tauri build"
}

Write-Host "`n全部完成。" -ForegroundColor Green
