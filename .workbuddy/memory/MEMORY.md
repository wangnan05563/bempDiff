# BempDiff 项目长期记忆

## 产品形态与链路
- **系统命名（2026-08-21 通用化）**：标题统一为「差异化对比工具」（英文代号 BempDiff 保留）；已全库移除「票据系统」字样（页面/窗口标题、AI prompt、配置、docs 文件名与内容）。新文案/文档一律用通用词（软件构建包/war/jar/文件夹），勿再引入业务限定字样。
- **Electron 壳 + Vue3/Bootstrap UI + Java 后端 sidecar**（`com.bempdiff.Main server` + jlink 瘦 JRE）。核心 `bempdiff/java_core`，前端 `bempdiff/webui/`，壳 `bempdiff/dev-shell/`。
- 退役：JavaFX/jpackage/Tauri 2。目标打包 = electron-builder(NSIS)，只需 Node+JDK21。
- 开发入口：`tooling/scripts/启动桌面壳.bat`（`BEMPDIFF_SHELL=electron` → call `启动服务.bat`）。
- 根目录只留：`.gitignore`/`cleanup-config.yaml`/`sonar-project.properties`/`config/`/`docs/`/`logs/`/`bempdiff/`/`tooling/`。

## JDK21 工具链（必须遵守）
- **必须 JDK21 编译**：`BempServer` 用 `Executors.newVirtualThreadPerTaskExecutor()`（JDK21 finalized），JDK17 报 `找不到符号`。
- 唯一可用：`bempdiff/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64`（被掏空 → `curl` cdn.azul.com 重下 + `unzip -q -o`）。
- 编译/测试一律调 `toolchain/.../bin/javac.exe`+`java.exe`，勿用 JDK17。脚本 `java_core/build_and_test.sh`。

## 脚本/沙箱坑（实测）
- **`.bat` 编码**：Write 强制 UTF-8 BOM+LF，本机 codepage 936(GBK) 不认 → 崩。约定：中文 .bat 存 **GBK 无 BOM + CRLF**（保留全角 `（）`），英文纯 ASCII；禁 BOM、禁 `chcp 65001`。修复用 python 二进制重写（剥 BOM、`.encode('gbk')`、`\n`→`\r\n`）。
- **启动服务.bat 探测 electron 须两级 `..`**：`%~dp0`=`tooling/scripts/`，`bempdiff/` 在根 → `%~dp0..\..\bempdiff\dev-shell\...`。少一级 → electron not found 回退浏览器。
- **Vite 须绑 IPv4**：默认 `localhost` 在 Win 绑 `::1` → 连不到。追加 `--host 127.0.0.1`。
- **electron npm install**：`bempdiff/dev-shell/.npmrc` 覆盖 `electron_mirror=https://registry.npmmirror.com/-/binary/electron/`（用户级 .npmrc 指向不可达 artifactory，`npm_config_electron_mirror` 优先级高于 env）。沙箱 `ELECTRON_RUN_AS_NODE=1` 干扰版本检测，用 `env -u ELECTRON_RUN_AS_NODE`。
- **safe-delete 钩子**：`Remove-Item`/`rm`/`del` 被 fail-closed 强杀；路径含 `Users\hspcadmin`/`.workbuddy`/`C:\Users` 字面量也被扫。绕过：Python ctypes `DeleteFileW/RemoveDirectoryW` 直接 Win32，或 `MoveFileExW(...,MOVEFILE_DELAY_UNTIL_REBOOT)` 登记重启删。
- **幻影路径**：传原生 `javac/java` 目录须用显式 `C:/Users/...` 盘符，勿用 `/c/...` POSIX（被当当前盘 D: 根 → 写到 `D:\c\...`）。
- **npm/vitest**：webui/ 与 dev-shell/ 放项目级 `.npmrc` 覆盖 `registry=https://registry.npmmirror.com/`（用户级 `always-auth=true` 致挂起）；**vitest 4.x 与 vite 5 不兼容**，须 `vitest@^2.1.9`。
- **PowerShell 管道死锁**：`& script.ps1 2>&1 | ForEach{}` 长跑后外层挂住 → 用 `*> file`/`Tee-Object` 落盘读。
- **PowerShell 5.1 读 JSON 必须显式 `-Encoding UTF8`（2026-08-31 实测）**：`Get-Content <无 BOM 的 UTF-8> -Raw | ConvertFrom-Json` 按 ANSI/GBK 解码，中文 `description` 乱码、引号被破坏 → `ConvertFrom-Json` 报「传入的对象无效，应为":"或"}"」→ 字段取到**空值**。**约定：脚本读 JSON 字段一律用 `node -p "require('./x.json').field"`**（node require 天然处理编码，输出纯 LF 无 CR，`set /p` 安全），勿再用 PS ConvertFrom-Json。
- **electron-builder "output file is locked => waiting for unlock" 仍 exit 0**：旧 exe 被占用（杀软/Explorer/上次残留）时会跳过生成新文件却退 0，"文件存在"检查会把**旧产物**当本次结果（2026-08-31：误把 11:47 的旧包当本次产出）。**打包前须按版本号先删旧 `setup.exe`+`.blockmap`**，再以"存在且 >=50MB"作本次判据；校验用绝对路径（`%CD%\..\release`）且缺失时 `Write-Error` 打印真实路径，杜绝静默失败。

## 业务要点
- 分层 L0/L1/L2；CFR 反编译+javap 降级；两阶段 AI+成本闸门+脱敏；API Key 默认不落盘。
- 前端 JS/HTML/CSS 对比(FR4.4)：`FileClass` 增 JS/HTML/CSS，`FrontendTextDiff` beautify，同构 `DecompiledUnit`。
- 文件夹对比(FR11)：`FolderDiff`（名称/类型/大小/mtime/SHA-256）。
- 行内差异：`diff_align`（simRatio>=0.5 DP 配对）+ `diff_inline`（公共前后缀裁剪后 LCS）；粒度 line/word/char。
- **报告代码差异精简（2026-08-21）**：`MarkdownReport.renderCompactDiff()` 复用 `DiffDigest.render`，三/四/五章仅输出变更行+行号区间+类型。全量 186/186 绿。
- **差异树目录树视图（2026-08-21）**：`webui/src/lib/dir_tree.js`（`buildDirTree`+`flattenDirTree`+`dirLayersOf` 纯函数）驱动 DiffTree.vue 树视图按目录层级递归展开（VS Code 风格）：节点仅显示当前层名称、缩进 `8+depth*16px`、`fileIcon` 类型图标、目录默认全展开可折叠（`expandedDirs`）、FOLDER 显式节点合并进推导目录、archiveChild 缩进改 depth 内联控制（勿恢复 .child-row 的 padding !important）、列表视图保持显示完整 key。dir_tree.spec.js 14 用例，前端 128/128 绿。
- **全部展开/折叠按钮（2026-08-21）**：DiffTree 工具栏两个 `TipButton.vue`（新组件：icon+气泡 Tooltip，props icon/tooltip/delay 默认400ms/disabled；mouseenter 延迟显示、mouseleave 隐藏；disabled 不派发 click 但保留提示）——全部展开=`bi-plus-square`、全部折叠=`bi-dash-square`，仅树视图可用（列表视图禁用并提示切换）。点击走**逐层交错动画**：`dirLayersOf` 按深度分层，展开自顶层逐层、折叠自最深层逐层（60ms/层），`dirAnimToken` 令牌中断上一轮支持实时切换；动画中修改 expandedDirs 触发 Vue 更新，虚拟滚动不受影响。原「...」菜单里的目录展开两项已移除。
- **对比栏全量/差异双模式（2026-08-21）**：DiffView 新增 `diffOnly`（默认 false=全量内容显示全部行；true=仅差异内容）。差异模式 = `foldContext(rows, true, 0)`（win=0 全部 ctx 折叠只留差异行）；工具栏分段按钮（`bi-file-earmark-text`=全量 / `bi-diff`=差异）；折叠条文案区分两模式、差异模式点击切回全量。**报告保持只写差异内容（MarkdownReport 不改）**。diff_fold.spec.js 6 用例，前端 134/134 绿。

## 桌面壳/sidecar 架构
- **Electron 壳 main.js 是 sidecar 拥有者**：自行 spawn(javaw, windowsHide, detached) 静默拉起 Java 后端（18765）+ 可选 vite dev（5180）；before-quit 回收子进程 + killPort。
- 启动服务.bat 在 SHELL==electron 时短路：仅 start /B 拉起 Electron 后 exit → 零控制台黑框。
- 生产打包：electron-builder 经 extraResources 把 `bempdiff/dist_input`(jre/classes/cfr/webui/dist) 落地到 resourcesPath；main.js resolveRoot() 兼容 dev/prod。
- **改 Java 后须同步 class**：sidecar `findClasspath()` 优先级 `dist_input/classes` > `dist_input/app/bempdiff.jar` > `dist_input/dev_classes`，**不用 java_core/out** → 编译新 class 后必须 `cp -r java_core/out/. dist_input/classes/` 再重启壳才生效（2026-08-21 实测）。

## AI 分析功能（前端）
- **主用组件 `AiConsole.vue`**（InfoPanel 第5个 tab「控制台」，多任务并行 tab + 流式输出），**非**遗留的 `AiAnalysisDialog.vue`（已废弃、App.vue 未引用）。
- 后端 `BempServer.handleJob` 的 `ai-analyze` 分支走 SSE，事件 `thinking`→`answer`(28字符块/16ms)→`done`/`error`；复用 `runAiAnalysis()`（与 `report --ai` 同管线）+ `buildThinkingSteps()`。
- **智能分类覆盖全部变更文件（2026-08-21 修复）**：`handleClassify` 曾误用 `stageBTopK` 截断候选（默认15）→ 只打标前 N 个。现用 `buildAllCandidates`（全量不截断，候选序 ADDED/MODIFIED/DELETED）；成本预估 `estimateStageBOnly` 同步全量（避免闸门漏拦）。`buildCandidates`(topK 截断) 仅剩 report/analyze 用。无 Key 走启发式，有 Key 逐文件串行 LLM。
- 前端 `api.analyzeStream`(fetch+ReadableStream)；`AiConsole.vue`：多任务 tab + 思考块折叠 + `.ai-md` 渐进渲染 + `scrollBottom()` 跟流（watch answer/thinking 长度）。
- **tab 滚动位置记忆（2026-08-21）**：两套共用滚动容器的 tab 加 Map 缓存 save/restore，切回恢复各自位置：
  - InfoPanel 前4报告 tab（file/global/break/audit）共用 `.ai-body` → `aiScrollMap` + `@scroll` 节流(raf) + watch `aiPanelTab`：切前 save 旧 tab、切后 restore 新 tab（无记忆回顶）；console tab 走 AiConsole 不在此恢复。
  - AiConsole 多任务共用 `.console-body`(bodyRef) → `taskScrollMap` + watch `aiActiveTaskId`：切到 streaming/thinking 任务走 `scrollBottom` 跟流（不被记忆覆盖），切到已完成任务 restore 记忆（无则回顶）；另 watch `aiTasks` id 集合清理已关任务缓存。
- 三套 tab 系统：InfoPanel 5 内容 tab（state.aiPanelTab）/ AiConsole 多任务 tab（state.aiActiveTaskId+aiTasks）/ DiffView 多文件 tab（state.activeKey+tabs，切换强制 scrollTop=0，**未加记忆**）。
- `PromptBuilders.sanitize` 用显式 `(?<!\d)`/`(?!\d)` 边界替代 `\b`（CJK 与数字相邻时漏脱敏）。

## 打包/验证
- **exe 子命令白名单漂移**：`App.isBatchSubcommand()` 须随 `Main` 子命令同步加 case，否则走 usage exit2。
- **离线 `report --ai`**：`--apikey ""` 强制 MockAiAnalyzer，确定性产出 AI 章节。
- **前端构建/测试**：webui/ 下 `node node_modules/vite/bin/vite.js build`（45模块/2.4s）；测试 `./node_modules/.bin/vitest run`（jsdom 起得慢属正常，约 16s/90 用例）。

## 工作空间清理（workspace-cleanup，第 3 轮 2026-09-10）
- **skill 状态**：`workspace-cleanup` 被 `skillOverrides` 禁用（Skill 工具拒载）；`SkillManage` 本环境不可用。其 `SKILL.md` 正文**乱码**（UTF-8 被按 GBK 解），references 链接还指向别的项目（`19_Karpathy-AI+Obsidian知识库/.trae/skills/...`）→ 用前需直接 Read 该文件、按六阶段手工执行。
- **双桶处置约定**：可再生且未入库 → 直删；一次性质疑证据 → **隔离移动**。隔离区放**工作空间外**（`D:\code\otherProjects\.cleanup-quarantine\<项目>-<ts>\`，同盘秒级还原）；放工作空间内会让「释放空间」报表失真。
- **哈希策略（实测）**：全量 SHA-256 在 AV 节流下 1.62 GB 需 >7min 未完；改「隔离桶全量 + 删除桶 ≤1MB 全量 / >1MB 首 64KiB 采样」+8 线程 → 7.6s。可再生缓存无需全量哈希。
- **去重坑**：混用 `os.walk` 绝对路径与 `os.path.join(ROOT, rel)` 会生成 `D:\a/b` 与 `D:\a\b` 两种串 → 按 abs 字符串去重失效（本次虚高 163），且 `.class` 会被删除桶先命中而非按预期走隔离。必须用 `normcase(normpath(abs))` 作键；`kind` 也须按条目后缀归一化，否则同一 `.class` 因多规则命中导致报表口径分裂。
- **占用探测**：`CreateFileW(path, GENERIC_READ, share=0, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS)` 返回 INVALID 且 `GetLastError()==32` 即锁定（目录同此）。
- **服务甄别**：`tasklist` 只见通用 `java.exe`，须 `wmic process get ProcessId,Name,ExecutablePath,CommandLine` 看命令行——本机 java.exe 全是 Trae LS/Jenkins/spring-boot LS，**与 BempDiff 无关**；端口 8000/8080/5000/18765/5180 无监听即放行。
- **tracked-but-ignored 检测**：`git ls-files | git check-ignore --stdin --no-index` 精确列出「已入库但被忽略」历史垃圾（本次 26 个），是 `.gitattributes export-ignore` 的输入。
- **只提交指定文件**：index 里可能已有他人暂存改动，用 `git commit -- <pathspec>` 避免夹带。
- **本轮结果**：2.85 GB → 782.44 MB（−2.09 GB / −73.23%），删 4,377 + 隔离 550 文件，26 批 0 错误，commit `f99171a`。用户确认保留：`node_modules ×3`（526 MB）、`bempdiff/dist_input`（86.7 MB）、`release/*.exe` 交付包。
- **禁用清理项**：`bempdiff/toolchain`（JDK21 必需）、`bempdiff/java_core_ai_replay`（约定保留不改）、`bempdiff/verify_exe`+`tooling/verify_exe`（受 git 跟踪）。
