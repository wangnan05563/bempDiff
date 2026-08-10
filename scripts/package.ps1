# package.ps1 - BempDiff 构建打包
# 流程: 解析 JDK21 + JavaFX -> 编译 core+UI -> 打 app.jar -> jpackage 自包含 exe
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1"
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
Write-Host "  BempDiff 构建打包 (Build + Package)"
Write-Host "============================================"
Write-Host ("项目根目录: " + $Root)

$jdk = Find-Jdk $Root $cfg
if (-not $jdk) {
    Write-Host "[ERROR] 未找到 JDK21！"
    Write-Host "  请确认 prototype\toolchain 下存在 Zulu JDK21，或将 JDK21 加入 PATH"
    exit 1
}
$javac    = Join-Path $jdk 'bin\javac.exe'
$jar      = Join-Path $jdk 'bin\jar.exe'
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
$mp = Get-RuntimeModulePath $Root $cfg
$cfrJar = Join-Path $Root $cfg.toolchain.cfr_jar_rel
if (-not (Test-Path $cfrJar)) { throw ("缺少 cfr.jar: " + $cfrJar) }
Write-Host ("JDK: " + $jdk)

$outDir     = Join-Path $Root $cfg.build.out_dir_rel
$distInput  = Join-Path $Root $cfg.build.dist_input_rel
$distExeDir = Join-Path $Root $cfg.build.dist_exe_dir_rel
$appName    = $cfg.build.app_name
$appJar     = Join-Path $distInput 'app.jar'
$mainClass  = $cfg.project.main_class

# [1/5] 清理旧产物
Write-Host "[1/5] 清理旧编译产物 ..."
Remove-Path $outDir
Remove-Path $distInput
New-Item -ItemType Directory -Force -Path $outDir    | Out-Null
New-Item -ItemType Directory -Force -Path $distInput | Out-Null

# [2/5] 编译 core + UI
$javaCoreSrc = Join-Path $Root $cfg.build.java_core_src_rel
$javaFxSrc   = Join-Path $Root $cfg.build.javafx_ui_src_rel
$srcFiles = @(Get-ChildItem -Path $javaCoreSrc, $javaFxSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($srcFiles.Count -eq 0) { throw "未找到任何 .java 源文件，请检查 prototype\java_core\src 与 prototype\javafx_ui\src" }
Write-Host ("[2/5] 编译 " + $srcFiles.Count + " 个源文件 (javac + JavaFX) ...")
$addModules = $cfg.build.add_modules
# cfr.jar 是非模块 classpath jar（Decompiler 直接 import org.benf.cfr.reader.api.*），
# 必须进 -classpath，否则新 Decompiler 编不过；运行时 cfr 类由 [4.5/5] 合并进 app.jar 提供。
$javacArgs = @('-J-Dstdout.encoding=UTF-8', '-J-Dstderr.encoding=UTF-8', '--module-path', $mp, '--add-modules', $addModules, '-classpath', $cfrJar, '-encoding', 'UTF-8', '-Xlint:-unchecked', '-d', $outDir)
$javacArgs += $srcFiles
Invoke-NativeCmd { & $javac @javacArgs }
if ($LASTEXITCODE -ne 0) { throw "javac 编译失败 (exit $LASTEXITCODE)" }
Write-Host "  编译完成"

# [3/5] 复制运行时模块化 jar（JavaFX + BootstrapFX + Ikonli）到打包输入目录
Write-Host "[3/5] 复制运行时模块化 jar 到 dist_input ..."
$jarDir = Join-Path $Root $cfg.toolchain.runtime_jar_dir_rel
$cfg.toolchain.runtime_modular_jars | ForEach-Object {
    $src = Join-Path $jarDir $_
    if (-not (Test-Path $src)) { throw ("缺少运行时 jar: " + $src) }
    Copy-Item -Path $src -Destination $distInput -Force
}

# [4/5] 打 app.jar
Write-Host "[4/5] 打包 app.jar ..."
Invoke-NativeCmd { & $jar --create --main-class $mainClass -f $appJar -C $outDir . }
if ($LASTEXITCODE -ne 0) { throw "jar 打包失败 (exit $LASTEXITCODE)" }

# [4.5/5] 合并 cfr.jar 类进 app.jar（P0-1 进程内 CFR 落地）
# cfr 是非模块 classpath jar，无签名 / 无 META-INF services，合并安全。
# 排除其 META-INF（含 MANIFEST.MF，会覆盖 app.jar 主清单导致 Main-Class 丢失）。
# 合并后运行时 classpath 自带 cfr 类，Decompiler.inProcessCfrAvailable=true，
# 默认走进程内 CFR（真源码反编译），无需 --cfr 指定外部 jar。
Write-Host "[4.5/5] 合并 cfr.jar 类进 app.jar (P0-1 进程内 CFR) ..."
$cfrExtract = Join-Path $distInput 'cfr_extract'
New-Item -ItemType Directory -Force -Path $cfrExtract | Out-Null
Push-Location $cfrExtract
try {
    & $jar xf $cfrJar >$null 2>&1
} finally {
    Pop-Location
}
Remove-Path (Join-Path $cfrExtract 'META-INF')
Invoke-NativeCmd { & $jar uf $appJar -C $cfrExtract . }
if ($LASTEXITCODE -ne 0) { throw "合并 cfr 进 app.jar 失败 (exit $LASTEXITCODE)" }
Remove-Path $cfrExtract

# [5/5] jpackage 自包含 exe
# 先结束可能占用 app-image 的旧 BempDiff 进程(重打包时常见)，释放文件锁；
# 再用镜像名结束整棵进程树，并兜底结束命令行含 app 名的残留 JVM。
# 同时清理上一次被中断留下的 _fresh 暂存目录。进程释放句柄有短暂延迟，
# 删除失败则重试数次；若仍被占用(常见于资源管理器打开了该文件夹)，给出明确指引。
Write-Host "[5/5] 释放旧进程占用 ..."
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    taskkill /F /T /IM ($appName + ".exe") >$null 2>&1
} finally {
    $ErrorActionPreference = $prevEAP
}
Start-Sleep -Seconds 2
$appImage  = Join-Path $distExeDir $appName
$appFresh = Join-Path $distExeDir "_fresh"
Remove-Path $appFresh
$removed = $false
for ($i = 0; $i -lt 8; $i++) {
    Remove-Path $appImage
    if (-not (Test-Path $appImage)) { $removed = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $removed) {
    throw ("无法删除旧 app-image（仍被占用）: " + $appImage + "`n  常见原因：资源管理器正打开该文件夹(含预览窗格/缩略图)。`n  请关闭该文件夹窗口后重新执行打包。")
}
Write-Host "[5/5] jpackage 打包自包含 exe (预计 10-30 秒) ..."
Invoke-NativeCmd { & $jpackage --type app-image --name $appName --input $distInput --main-jar app.jar --module-path $distInput --add-modules $addModules --dest $distExeDir --java-options $cfg.build.jvm_options }
if ($LASTEXITCODE -ne 0) { throw "jpackage 打包失败 (exit $LASTEXITCODE)" }

# 校验产物
$exe = Join-Path $distExeDir (Join-Path $cfg.build.exe_subdir ($appName + '.exe'))
if (-not (Test-Path $exe)) { throw ("未生成 exe: " + $exe) }

Write-Host ""
Write-Host "============================================"
Write-Host "  构建完成!"
Write-Host "============================================"
Write-Host ("EXE 路径: " + $exe)
Write-Host "  双击即用 (内嵌 JRE + JavaFX，无需安装 Java)"
Write-Host ""
Write-Host "  后续:"
Write-Host "  - 启动 GUI : 双击 scripts\启动服务.bat"
Write-Host "  - 命令行   : BempDiff.exe compare/report/export <old> <new>"
Write-Host "============================================"
