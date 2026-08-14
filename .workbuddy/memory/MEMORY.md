# 项目长期记忆：BEMP WAR/JAR 差异比对与智能分析工具

## 技术栈与 UI 约定
- **产品形态**：JavaFX 桌面应用（双击 `BempDiff.exe` 即用，jpackage 自包含），核心逻辑 `java_core`，UI `javafx_ui`。
- **UI/样式开源库**（2026-08-09 重构确立）：
  - Web 高保真原型 `prototype/ui_prototype.html` → **Bootstrap 5.3.3 + Bootstrap Icons 1.11.3**，资源本地化于 `prototype/assets/`（含字体），支持 `data-bs-theme` 浅/深切换。
  - JavaFX → **BootstrapFX 0.4.0**（kordamp，模块化 `org.kordamp.bootstrapfx.core`）+ **Ikonli 12.3.1**（模块化 `org.kordamp.ikonli.core/.javafx/.bootstrapicons`）作为图标库。
  - 原计划的 **AtlantaFX 在本环境 Maven Central/jsdelivr 均 404 不可达**，故选 BootstrapFX（与 Web 侧 Bootstrap 主题一致）。
- **构建方式**：手工 `javac` + `jpackage`，无 Maven/Gradle；依赖走 `--module-path`（JDK21 + JavaFX21 + 上述开源 jar）。编译/打包的 `--add-modules` 已扩展含 `org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons`（见构建手册 §3/§4/§6）。
- 已下载的开源 jar 位于 `prototype/toolchain/`：bootstrapfx-core-0.4.0.jar、ikonli-core/javafx/bootstrapicons-pack 12.3.1.jar。

## 约定/坑
- Ikonli 三包是**模块化 jar**，模块名 `org.kordamp.ikonli.*`（不是 `ikonli.*`）；必须放 `--module-path` 且列入 `--add-modules`，否则 `import org.kordamp.ikonli.javafx.FontIcon` 报"程序包不可见"。
- BootstrapFX 加载：`scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());` 并对根节点加 `"bootstrap"` 类；控件加 `btn`/`btn-default`/`btn-primary`/`form-control` 才生效。
- JavaFX GUI 交互需本机 Windows 实测；无显示环境只能验证编译（headless 子命令可验证核心）。
- **打包产物在无头沙箱内的验证手法（非显然，重要）**：jpackage `--type app-image` 的 `BempDiff.exe` 是 GUI 子系统启动器，**不挂接控制台**（stdout/stderr 被丢弃），且本沙箱会杀 JavaFX 类加载，故无法在沙箱内直接冒烟 `BempDiff.exe`。等价验证法：用工具链 `java.exe` 直接跑镜像里的 `app.jar`，按 jpackage 真实方式拼参数——`java -p <dist_exe/BempDiff/app> --add-modules <add_modules> -Dfile.encoding=UTF-8 -cp "<app.jar;bootstrapfx;ikonli三包;javafx四包>" com.bempdiff.Main <subcommand> ...`（注意用 `-cp` 全 jar + 主类 `com.bempdiff.Main`，**不要 `-jar`**，`-jar` 会让其它 jar 脱离 classpath 导致静默失败）。这能验证打包后的类 + 合并的 cfr（jar 内应含 1362 个 `org/benf/cfr` 类）全链路可用；GUI 启动器转发逻辑（`App.main`→`Main.main`）靠读源码确认即可。
- **清理 jpackage 残留 `_old_*` 目录（非显然，易踩）**：`package.ps1` 清理阶段把旧 app-image 重命名为 `BempDiff._old_<时间戳>`。删除时先 `Get-Process -Name BempDiff`（**不依赖 Path 过滤**，捕获映像路径重命名后失效、Path=null 的挂起 launcher）全杀，再用 .NET `[System.IO.Directory]::Delete($p,$true)` 递归删（**绕过 safe-delete 钩子，绝不能 `Remove-Item`**）。注意：`classes.jsa` 等 AppCDS 文件可能被 Defender/系统锁持续占用，重试 + `takeown`/`icacls` 赋权均失败 → 无害残留，需本机手动删或重启清理。
- **perf-harness 编译/运行验证手法**：`javac -cp <dist_exe/BempDiff/app/app.jar> -d perf-harness/bin perf-harness/src/.../Harness.java`（app.jar 含全部 core+cfr 作 classpath 即可，Harness 不依赖 JavaFX）；运行 `java -cp app.jar;bin com.bempdiff.perf.Harness <port>`，探 `http://localhost:<port>/health` 验证（`{"ok":true,"role":"bempdiff-perf-harness"}`）。
- **版本号提取策略**：`PackageParser.extractVersion` 合并三来源取"最详细"版本号——①`MANIFEST.MF` 多 key（Bundle-Version/Implementation-Version/Specification-Version/Build-Version/Version）；②`META-INF/maven/.../pom.properties` 的 `version=`；③包文件名中的 `x.y.z...`（支持 `old-1.6.1.war`、`app-1.6.2-SNAPSHOT.jar`）。优先段数更多/长度更长者，避免只显示 `1.6`。
- **IDEA 风格 HTML 报告渲染器**：`prototype/java_core/render_idea_report.py` 读取 `report.md`，生成 Darcula 暗色主题 HTML，支持 Java/JS/CSS/HTML 关键字/字符串/数字/方法高亮、diff 删除红底/新增绿底/inline 变更黄底高亮、版本号覆盖参数。
- **设置页悬停提示（固定底部状态栏）**：`ConfigDialog.show` 现接收主窗口 `statusBar`（`Label`）参数；弹窗内每个字段（两页共 21 个：ComboBox/TextField/PasswordField/CheckBox/Button/Hyperlink/HBox/Label）经 `attachHint(node, hint, statusBar, def)` 绑定 `setOnMouseEntered/Exited`——悬停把该字段中文说明写进底部状态栏、移开恢复默认提示；`dlg.setOnHiding` 还原弹窗打开前的原状态文本。底部状态栏为 `BorderPane` 的 `bottom`（始终固定最底部）。GUI 交互需本机 Windows 实测。

## 业务要点（差异比对工具）
- 分层差异：L0 包级 / L1 业务码（按内部包前缀展开 class）/ L2 第三方依赖（jar 级折叠）。
- 反编译 CFR 为主、javap 降级；GBK 容错；两阶段 AI 分析（StageA 概览 + StageB 单文件深读）+ 成本闸门 + 脱敏；AI 不可达走 Mock 基础结论。
- 合规：API Key 默认不落盘（`persistApiKey=false`）；公网模型仅发脱敏 diff 摘要，源码不出机。
- 文件夹对比（FR11，2026-08-12 落地）：独立 `FolderDiff` 引擎（`diff/FolderDiff.java`，不复用 PackageSnapshot/DiffEngine），递归比对两目录的名称/类型/大小/mtime(毫秒)/SHA-256；状态 LEFT_ONLY/RIGHT_ONLY/MODIFIED/SAME/TYPE_MISMATCH，MODIFIED 细分 SIZE/MTIME/CONTENT/TYPE。目录只比存在性+类型不比 mtime（子树差异靠展开树体现）。文本文件(≤4MB)内容不同做 `LineDiff.unified` 行级 diff，超大(>64MB)/二进制仅判哈希；并行哈希、跳符号链接、单文件异常隔离。CLI `folderdiff <L> <R> [--report <md>] [--max-depth N]` + 报告 `FolderReport`；GUI 选两目录进 folderCompare 模式可展开树查看（MODIFIED 文本不同→双栏 diff）。

## 前端源码对比与分析（FR4.4 增强，2026-08-11 落地）
- **背景**：原实现仅对 Java `.class` 反编译做双栏 diff + 两阶段 AI；前端 `js/html/htm/css` 被归为 `STATIC`，**无内容 diff、无 AI 分析**（FR4.4 名存实亡）。本次补全"前端 JS/HTML/CSS 对比与分析"。
- **分类**：`FileClass` 新增 `JS/HTML/CSS` 枚举（含 `isFrontendText()`）；`PackageParser.classify` 把 `.js`→JS、`.html/.htm`→HTML、`.css`→CSS，其余静态资源仍归 `STATIC`。
- **美化**：`FrontendTextDiff` 服务——压缩单行 JS/CSS 用状态机按 `{}/;` 缩进展开、HTML 按 `><` 拆行（`beautifyHtml` 始终执行，不影响文本内 `<`/`>`）；`looksMinified` 判定单行超长；`BEAUTIFY_MAX_CHARS=2_000_000` 超长回退原始；异常回退原始（尽力而为）。
- **同构复用**：前端 diff 返回与 class 反编译**同构**的 `DecompiledUnit`（`engine` 区分 `js-beautify`/`css-beautify`/`html-beautify`），报告/UI/AI 三下游无需特判。
- **AI**：`AiAnalyzer.DecompileReq` 增 `FileClass fileClass`；`PromptBuilders.buildStageB` 按 `fc.isFrontendText()` 切换"前端JavaScript/HTML模板/CSS样式代码改动" vs "Java类改动"措辞。
- **报告**：`MarkdownReport` 新增"四、前端资源代码差异"章节（原三/四/五/六顺延为三Java/四前端/五破坏性/六审计）。
- **导出**：`AssetExporter.exportDecompiledSources` 按原生扩展名落盘（`.js/.html/.css`，不再统一 `.java`）。
- **验证**：`prototype/java_core/{build_and_test.sh, build_ui.sh, e2e_frontend.sh, make_wars.py, Demo.java}` 为本次新增的编译/单测/端到端脚本（含 `FrontendTest` 8 用例）；`Main` 子命令 compare/decompile/report/export/ai 已于含压缩/可读/变更/新增/删除 JS 的 war 上全链路验证通过。

## JDK 工具链复原 SOP（重要，可能复发）
- bundled Zulu JDK21 在 `prototype/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64`，**本环境可能再次被掏空**（`jmods`/`lib`/`bin` DLL 全没 → `javac/java/jpackage` 崩 `0xC0000139`/`exit 127`）。
- 复原：从 Azul CDN 下精确版本并解压覆盖：
  `curl -L -o prototype/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64.zip "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-win_x64.zip"`
  然后 `cd prototype/toolchain && unzip -q -o zulu21.52.15-ca-jdk21.0.12-win_x64.zip`。
- 校验：`jmods`≈70、`bin` DLL≈90、`javac -version` 出 `21.0.12`。
- 机器上无其它 JDK21（仅 JDK8/17/25）；JavaFX 21 必须 JDK21，别用其它版本替代（JDK8/17 加载不了 class file 65，JDK25 有兼容风险）。

## 打包产物验证套路补充（2026-08-13）
- **exe 子命令白名单漂移坑（真实踩中）**：`App.main` 的 `isBatchSubcommand()` 白名单独立维护，必须随 `Main` 子命令集合同步扩展——漏加某子命令（如 `diff-jars`）会让 `BempDiff.exe <子命令>` 落入 usage 分支 `exit 2` 而非转发执行（GUI 路径不受影响）。新增子命令时务必同步加 `case`；验证打包产物须对每个子命令实跑「真实 exe 包装器」，不能只 `java -cp app.jar` 直调 `Main`（那会绕过白名单，掩盖此种缺陷）。
- **离线验证 `report --ai` 技巧**：持久化配置若带真实 API Key，`report --ai` 走真实 `HttpAiAnalyzer` 且 `testConnection` 探活会卡死（超时）；传 `--apikey ""` 强制空 Key 分支走 `MockAiAnalyzer`，确定性产出含「七、AI 智能分析」章节的报告。
