# build-ui.ps1 - BempDiff 前端(JavaFX UI)构建
# 仅编译 core+UI 到 javafx_ui/out，不打包 exe。用于快速改 UI 后从源码运行验证。
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-ui.ps1"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$Root = Resolve-Path (Join-Path $ScriptDir '..')
$CfgPath = Join-Path $ScriptDir 'java-config.json'
$cfg = Get-Content $CfgPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Find-Jdk {
    param($root, $cfg)
    $candidates = @()
    $candidates += Join-Path $root $cfg.toolchain.bundled_jdk_rel
    foreach ($p in $cfg.toolchain.jdk_search_paths) {
        $exp = [System.Environment]::ExpandEnvironmentVariables($p)
        if ($exp -notmatch '^[A-Za-z]:\\' -and $exp -notmatch '^\\\\') {
            $exp = Join-Path $root $exp
        }
        $candidates += $exp
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $candidates += (Split-Path (Split-Path $cmd.Source -Parent) -Parent)
    }
    foreach ($c in $candidates) {
        $javac = Join-Path $c 'bin\javac.exe'
        if (Test-Path $javac) { return $c }
    }
    return $null
}

function Get-RuntimeModulePath {
    param($root, $cfg)
    $dir = Join-Path $root $cfg.toolchain.runtime_jar_dir_rel
    $jars = $cfg.toolchain.runtime_modular_jars | ForEach-Object { Join-Path $dir $_ }
    $missing = $jars | Where-Object { -not (Test-Path $_) }
    if ($missing) { throw ("缺少运行时模块化 jar: " + ($missing -join ', ')) }
    return $jars -join ';'
}

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
        Write-Host ("  已清理: " + $Path)
    } catch {
        Write-Host ("  [WARN] 清理失败(已忽略): " + $Path + " - " + $_.Exception.Message)
    }
}

function Invoke-NativeCmd {
    param([ScriptBlock]$Script)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Script 2>&1 | ForEach-Object {
            $s = $_.ToString()
            # 过滤该 Zulu JDK 上始终输出的无害 NOTE（未经检查或不安全的操作）；
            # 仅跳过这两行特定文本，真实错误/警告照常透传。
            if ($s -match '使用了未经检查或不安全的操作' -or $s -match '请使用 -Xlint:unchecked 重新编译') {
                return
            }
            Write-Host $s
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

Write-Host "============================================"
Write-Host "  BempDiff 前端(JavaFX UI)构建"
Write-Host "============================================"

$jdk = Find-Jdk $Root $cfg
if (-not $jdk) {
    Write-Host "[ERROR] 未找到 JDK21！请确认 prototype\toolchain 下存在 Zulu JDK21"
    exit 1
}
$javac = Join-Path $jdk 'bin\javac.exe'
$mp = Get-RuntimeModulePath $Root $cfg
$cfrJar = Join-Path $Root $cfg.toolchain.cfr_jar_rel
if (-not (Test-Path $cfrJar)) { throw ("缺少 cfr.jar: " + $cfrJar) }
Write-Host ("JDK: " + $jdk)

$outDir      = Join-Path $Root $cfg.build.out_dir_rel
$javaCoreSrc = Join-Path $Root $cfg.build.java_core_src_rel
$javaFxSrc   = Join-Path $Root $cfg.build.javafx_ui_src_rel

Write-Host "[1/2] 清理旧前端产物 ..."
Remove-Path $outDir
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "[2/2] 编译 core + UI (javac + JavaFX) ..."
$srcFiles = @(Get-ChildItem -Path $javaCoreSrc, $javaFxSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($srcFiles.Count -eq 0) { throw "未找到任何 .java 源文件" }
$javacArgs = @('-J-Dstdout.encoding=UTF-8', '-J-Dstderr.encoding=UTF-8', '--module-path', $mp, '--add-modules', $cfg.build.add_modules, '-classpath', $cfrJar, '-encoding', 'UTF-8', '-Xlint:-unchecked', '-d', $outDir)
$javacArgs += $srcFiles
Invoke-NativeCmd { & $javac @javacArgs }
if ($LASTEXITCODE -ne 0) { throw "javac 编译失败 (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "============================================"
Write-Host "  前端构建完成!"
Write-Host "============================================"
Write-Host ("产物目录: " + $outDir)
Write-Host "  运行方式:"
Write-Host "  - 源码运行 : 双击 prototype\javafx_ui\run_ui.bat"
Write-Host "  - 打包 exe : 双击 scripts\构建打包.bat (全量重打)"
Write-Host "============================================"
