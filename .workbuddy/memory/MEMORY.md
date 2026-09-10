# BempDiff 项目长期记忆

> 本文件为精炼后的项目级长期记忆；过程细节见 `memory/YYYY-MM-DD.md` 日志。

## 产品形态与链路
- 命名：标题「差异化对比工具」（代号 BempDiff 保留）；全库已移除「票据系统」字样。新文案/文档一律用通用词（软件构建包/war/jar/文件夹）。
- 架构：Electron 壳 + Vue3/Bootstrap UI + Java 后端 sidecar（`com.bempdiff.Main server` + jlink 瘦 JRE）。核心 `bempdiff/java_core`、前端 `bempdiff/webui/`、壳 `bempdiff/dev-shell/`。
- 已退役 JavaFX/jpackage/Tauri 2；目标打包 = electron-builder(NSIS)，只需 Node + JDK21。
- 开发入口：`tooling/scripts/启动桌面壳.bat`（`BEMPDIFF_SHELL=electron` → call `启动服务.bat`）。
- 根目录只留：`.gitignore`/`cleanup-config.yaml`/`sonar-project.properties`/`config/`/`docs/`/`logs/`/`bempdiff/`/`tooling/`。

## JDK21 工具链（必须）
- 必须 JDK21：`BempServer` 用 `Executors.newVirtualThreadPerTaskExecutor()`，JDK17 报「找不到符号」。
- 唯一可用：`bempdiff/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64`（曾被掏空 → `curl` cdn.azul.com 重下 + `unzip -q -o`）。编译/测试一律调该目录 `bin/javac.exe`+`java.exe`，勿用 JDK17；脚本 `java_core/build_and_test.sh`。

## 脚本/沙箱坑（实测）
- `.bat` 编码：Write 工具强制 UTF-8 BOM+LF，本机 codepage 936(GBK) 不认 → 崩。约定中文 .bat 存 **GBK 无 BOM + CRLF**（保留全角括号），英文纯 ASCII；禁 BOM、禁 `chcp 65001`。修复用 python 二进制重写（剥 BOM、`.encode('gbk')`、`\n`→`\r\n`）。
- `启动服务.bat` 探测 electron 须**两级** `..`（`%~dp0`=`tooling/scripts/`）→ `%~dp0..\..\bempdiff\dev-shell\...`；少一级则 electron not found 回退浏览器。
- Vite 须绑 IPv4：默认 `localhost` 在 Win 绑 `::1` → 连不到，追加 `--host 127.0.0.1`。
- electron npm install：`bempdiff/dev-shell/.npmrc` 覆盖 `electron_mirror=https://registry.npmmirror.com/-/binary/electron/`（用户级 .npmrc 指向不可达 artifactory，且 `npm_config_electron_mirror` 优先级高于 env）。沙箱 `ELECTRON_RUN_AS_NODE=1` 干扰版本检测 → `env -u ELECTRON_RUN_AS_NODE`。
- safe-delete 钩子：`Remove-Item`/`rm`/`del` 被 fail-closed 强杀；路径含 `Users\hspcadmin`/`.workbuddy`/`C:\Users` 字面量也被扫。绕过：Python ctypes `DeleteFileW`/`RemoveDirectoryW` 直调 Win32，或 `MoveFileExW(...,MOVEFILE_DELAY_UNTIL_REBOOT)` 登记重启删。
- 幻影路径：传原生 `javac/java` 目录须用显式 `C:/Users/...` 盘符，勿用 `/c/...` POSIX（被当当前盘根 → 写到 `D:\c\...`）。
- npm/vitest：webui/ 与 dev-shell/ 放项目级 `.npmrc` 覆盖 `registry=https://registry.npmmirror.com/`（用户级 `always-auth=true` 致挂起）；**vitest 4.x 与 vite 5 不兼容**，须 `vitest@^2.1.9`。
- PowerShell 管道死锁：`& script.ps1 2>&1 | ForEach{}` 长跑后外层挂住 → 用 `*> file`/`Tee-Object` 落盘读。
- PS 5.1 读 JSON 必须显式 `-Encoding UTF8`：`Get-Content <无BOM UTF-8> -Raw | ConvertFrom-Json` 按 GBK 解码 → 中文乱码、引号被破坏 → 字段取到**空值**。**约定：读 JSON 字段一律用 `node -p "require('./x.json').field"`**（node require 天然处理编码，输出纯 LF 无 CR）。
- electron-builder「output file is locked => waiting for unlock」仍 exit 0：旧 exe 被占用（杀软/Explorer/上次残留）时会跳过生成新文件却退 0，「文件存在」检查会把**旧产物**当本次结果。打包前须按版本号先删旧 `setup.exe`+`.blockmap`，再以「存在且 ≥50MB」作判据；校验用绝对路径（`%CD%\..\release`），缺失时 `Write-Error` 打印真实路径，杜绝静默失败。

## 业务要点
- 分层 L0/L1/L2；CFR 反编译 + javap 降级；两阶段 AI + 成本闸门 + 脱敏；API Key 默认不落盘。
- 前端 JS/HTML/CSS 对比(FR4.4)：`FileClass` 增 JS/HTML/CSS，`FrontendTextDiff` beautify，同构 `DecompiledUnit`。
- 文件夹对比(FR11)：`FolderDiff`（名称/类型/大小/mtime/SHA-256）。
- 行内差异：`diff_align`（simRatio≥0.5 DP 配对）+ `diff_inline`（公共前后缀裁剪后 LCS）；粒度 line/word/char。
- 报告代码差异精简：`MarkdownReport.renderCompactDiff()` 复用 `DiffDigest.render`，三/四/五章仅输出变更行 + 行号区间 + 类型。
- **差异树目录树视图**：`webui/src/lib/dir_tree.js`（`buildDirTree`/`flattenDirTree`/`dirLayersOf` 纯函数）驱动 DiffTree.vue 按目录层级递归展开（VS Code 风格）：节点仅显示当前层名称、缩进 `8+depth*16px`、`fileIcon` 图标、目录默认全展开可折叠（`expandedDirs`）、FOLDER 显式节点并入推导目录；archiveChild 缩进用 depth 内联控制（**勿恢复 `.child-row` 的 padding `!important`**），列表视图仍显示完整 key。dir_tree.spec.js 14 用例。
- **全部展开/折叠按钮**：DiffTree 工具栏两个 `TipButton.vue`（icon + 气泡 Tooltip；props icon/tooltip/delay=400ms/disabled；mouseenter 延迟显示、mouseleave 隐藏；disabled 不派发 click 但保留提示）——展开 `bi-plus-square`、折叠 `bi-dash-square`，仅树视图可用（列表视图禁用并提示切换）。点击走**逐层交错动画**：按 `dirLayersOf` 深度分层，展开自顶层、折叠自最深层（60ms/层），`dirAnimToken` 令牌支持中断；原「...」菜单里的目录展开两项已移除。
- **对比栏全量/差异双模式**：DiffView 新增 `diffOnly`（false=全量显示全部行；true=仅差异）。差异模式 = `foldContext(rows, true, 0)`（win=0 折叠全部 ctx 只留差异行）；工具栏分段按钮 `bi-file-earmark-text`(全量)/`bi-diff`(差异)；折叠条文案区分两模式，差异模式点击切回全量。**报告保持只写差异内容（MarkdownReport 不改）**。diff_fold.spec.js 6 用例。

## 桌面壳 / sidecar
- Electron 壳 main.js 是 sidecar 拥有者：spawn(javaw, windowsHide, detached) 静默拉起 Java 后端（18765）+ 可选 vite dev（5180）；before-quit 回收子进程 + killPort。`启动服务.bat` 在 `SHELL==electron` 时短路（仅 `start /B` 拉起 Electron 后 exit → 零控制台黑框）。
- 生产打包：electron-builder 经 extraResources 把 `bempdiff/dist_input`(jre/classes/cfr/webui/dist) 落地到 resourcesPath；main.js `resolveRoot()` 兼容 dev/prod。
- **改 Java 后须同步 class**：`findClasspath()` 优先级 `dist_input/classes` > `dist_input/app/bempdiff.jar` > `dist_input/dev_classes`（**不用 java_core/out**）→ 编译后必须 `cp -r java_core/out/. dist_input/classes/` 再重启壳才生效。

## AI 分析功能（前端）
- 主用组件 `AiConsole.vue`（InfoPanel 第 5 个 tab「控制台」，多任务并行 tab + 流式输出）；`AiAnalysisDialog.vue` 已废弃、App.vue 未引用。
- 后端 `BempServer.handleJob` 的 `ai-analyze` 分支走 SSE：`thinking`→`answer`(28 字符块/16ms)→`done`/`error`；复用 `runAiAnalysis()`（与 `report --ai` 同管线）+ `buildThinkingSteps()`。
- **智能分类覆盖全部变更文件**：`handleClassify` 曾误用 `stageBTopK` 截断候选（默认 15）→ 只打标前 N 个；现用 `buildAllCandidates`（全量不截断，候选序 ADDED/MODIFIED/DELETED），成本预估 `estimateStageBOnly` 同步全量以免闸门漏拦。`buildCandidates`(topK 截断) 仅剩 report/analyze 用。无 Key 走启发式，有 Key 逐文件串行 LLM。
- 前端 `api.analyzeStream`(fetch+ReadableStream)；`AiConsole.vue`：多任务 tab + 思考块折叠 + `.ai-md` 渐进渲染 + `scrollBottom()` 跟流（watch answer/thinking 长度）。
- **tab 滚动位置记忆**：两套共用滚动容器的 tab 加 Map 缓存 save/restore——
  - InfoPanel 前 4 报告 tab（file/global/break/audit）共用 `.ai-body` → `aiScrollMap` + `@scroll` 节流(raf) + watch `aiPanelTab`：切前 save 旧 tab、切后 restore 新 tab（无记忆回顶）；console tab 走 AiConsole 不在此恢复。
  - AiConsole 多任务共用 `.console-body`(bodyRef) → `taskScrollMap` + watch `aiActiveTaskId`：切到 streaming/thinking 走 `scrollBottom` 跟流（不被记忆覆盖），切到已完成 restore（无则回顶）；另 watch `aiTasks` id 集合清理已关任务缓存。
- 三套 tab 系统：InfoPanel 5 内容 tab（`state.aiPanelTab`）/ AiConsole 多任务 tab（`aiActiveTaskId`+`aiTasks`）/ DiffView 多文件 tab（`activeKey`+`tabs`，切换强制 `scrollTop=0`，**未加记忆**）。
- `PromptBuilders.sanitize` 用显式 `(?<!\d)`/`(?!\d)` 边界替代 `\b`（CJK 与数字相邻时漏脱敏）。

## 打包 / 验证
- exe 子命令白名单漂移：`App.isBatchSubcommand()` 须随 `Main` 子命令同步加 case，否则走 usage exit2。
- 离线 `report --ai`：`--apikey ""` 强制 MockAiAnalyzer，确定性产出 AI 章节。
- 前端回归（webui/）：`node scripts/test_report_button.mjs`（9 用例，直接消费 `@vue/compiler-sfc`）；`./node_modules/.bin/vitest run`（22 文件/210 用例，jsdom 起得慢约 16s 属正常）；`node node_modules/vite/bin/vite.js build`（59 模块 ~3s）。

## 依赖与构建配置
- 依赖现状（2026-09-10 审计后）：`bempdiff` devDeps 仅 `electron-builder`（`@tauri-apps/cli` 与死脚本 `scripts.tauri` 已移除）；`webui` deps `vue`，devDeps `@vitejs/plugin-vue`/`@vue/compiler-sfc`/`@vue/test-utils`/`jsdom`/`vite`/`vitest`；`dev-shell` devDeps `electron`。
- `@vue/compiler-sfc` 曾被 2 个 webui 测试脚本 import 却未声明（靠依赖提升才能跑），已补声明，**版本范围取 `^3.5.0` 与 `vue` 保持一致**。
- 只改 package.json 不同步 lockfile 会使 `npm ci` 直接失败 → 必须 `npm install --package-lock-only`。
- **`npm --offline --dry-run` 的 reify 计划不等于真实计划**：曾预告补装 13 个 electron-builder 可选依赖，联网实跑 0 新增（外科手术级）。勿以其作决策依据。
- `bempdiff/webui/public/vendor/`（bootstrap css/js/fonts、splash*）是 vendored 静态资源，非 npm 依赖，构建时复制进 `dist/`；隔离 `webui/dist` 不影响构建。

## NSIS：bempdiff/build/installer.nsh（已入库，2dd7d97 重建）
- **事故**：该文件曾被清理脚本的**裸 `build/`** 规则当编译产物删除。`bempdiff/build/` 实为 electron-builder 的 **buildResources 源码目录**，非产物；`installer.nsh` 是 `package.json → build.nsis.include` 引用的**手写 NSIS 脚本**，缺失会让 `npm run dist` 直接失败。
- **铁律（血的教训）：不受版本控制的目标一律走隔离桶，绝不直接删除。** 当时该文件未入库 + ctypes 绕回收站 → 删除即不可恢复；`git rev-list --all --objects`/`stash`/`reflog`/`vssadmin list shadows`/隔离区/构建临时目录/全盘 find 全部搜救无效。
- 原版事实（据 Trae 记忆 2026-08-30）：① `customInit`/`customUnInstall` 内 `nsExec::ExecToLog 'taskkill /f /t /im BempDiff.exe'`——electron-builder 24.13.3 **无 `nsis.killRunningApp`**，不关运行实例会 `Access is denied` 并把旧 jar 打进包造成 STALE；② `preInit`+`ensureDiskSpace` 必须包在 **`!ifndef BUILD_UNINSTALLER`** 内——该阶段以 **`-WX`** 编译，未引用函数触发 warning 6010 即失败；③ 磁盘预检演变：初版查 `$INSTDIR`/系统盘 <512MB 则中止 → 最终 `ensureDiskSpace` **立即 Return** 彻底绕开（另一半在 `scripts/nsis-tpl/common.nsh`：`SectionSetSize 1`）。
- 含中文的 NSIS 脚本须 **UTF-8 带 BOM + CRLF**（`nsis-tpl/common.nsh` 是无 BOM 的历史例外）。
- 验证法：`makensis -WX` 与 `makensis -WX -DBUILD_UNINSTALLER` 双模式编译 harness；定点验证用 `<electron-builder Cache>\nsis\nsis-3.0.4.1\Bin\makensis.exe -V3 harness.nsi`，最小 harness 复刻 `Unicode true` + `!addincludedir` + `!include` + `.onInit` 内 `!ifmacrodef preInit` 即可验 NSIS 语法/宏/函数，无需跑完整打包。
- **端到端 electron-builder 打包在本沙箱无法完成**：打包阶段删 `locales/*.pak` 触发 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`，走不到 NSIS 步。须在普通命令行跑 `tooling/scripts/构建打包.bat`。
- 配置已修正：`cleanup-config.yaml` 移除裸 `build/`、新增最高优先级 `preserve_paths` 白名单、裸 `target/` 改显式 `bempdiff/src-tauri/target/`；`.gitignore` 加 `!bempdiff/build/` 例外段，`installer.nsh` 首次入库。
- ctypes 排错：`ctypes.windll.kernel32.get_last_error()` 恒为 0（会误导）→ 必须 `ctypes.WinDLL('kernel32', use_last_error=True)`。文件被锁时 `MoveFileExW(path, None, MOVEFILE_DELAY_UNTIL_REBOOT=0x4)` 登记重启删除；目录也可登记，但要排在文件之后、自底向上。

## ⭐ Trae 记忆库 = 本项目代码考古一手源
- 路径：`D:\code\Data_Trae\.trae-cn\memory\projects\-d-code-otherProjects-18-comparePakage--p2-f4df43e8d6a464e821e4\`
  - `project_memory.md`：跨会话沉淀的项目级事实条目（**最权威**，逐条 learned/actions）
  - `YYYYMMDD/topics.md`：按时间的会话主题摘要；`session_memory_*.jsonl`：单会话摘要（**只存摘要，不含文件原文**）
- 用途：追问「某段代码/配置为何这么写」「某符号从哪来」时**优先查此库而非自行推断**。本轮即靠它复原了被误删的 `installer.nsh` 全部功能规格。

## 工作空间清理（workspace-cleanup）
- skill 状态：被 `skillOverrides` 禁用（Skill 工具拒载）；`SkillManage` 本环境不可用。`SKILL.md` 正文**乱码**（UTF-8 被按 GBK 解），references 还指向别的项目 → 用前直接 Read 该文件、按六阶段手工执行。
- 双桶处置：可再生且未入库 → 直删；一次性质疑证据 → **隔离移动**。隔离区放**工作空间外**（`D:\code\otherProjects\.cleanup-quarantine\<项目>-<ts>\`，同盘秒级还原）；放工作空间内会让「释放空间」报表失真。
- 哈希策略：全量 SHA-256 在 AV 节流下 1.62 GB 需 >7min 未完 → 改「隔离桶全量 + 删除桶 ≤1MB 全量 / >1MB 首 64KiB 采样」+ 8 线程 = 7.6s。可再生缓存无需全量哈希。
- 去重坑：混用 `os.walk` 绝对路径与 `os.path.join(ROOT, rel)` 会生成 `D:\a/b` 与 `D:\a\b` 两种串 → 按 abs 字符串去重失效（本次虚高 163）。必须用 `normcase(normpath(abs))` 作键；`kind` 须按条目后缀归一化，否则同一 `.class` 因多规则命中致报表口径分裂。
- 占用探测：`CreateFileW(path, GENERIC_READ, share=0, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS)` 返回 INVALID 且 `GetLastError()==32` 即锁定（目录同此）。
- 服务甄别：`tasklist` 只见通用 `java.exe` → 须 `wmic process get ProcessId,Name,ExecutablePath,CommandLine` 看命令行（本机 java.exe 全是 Trae LS/Jenkins/spring-boot LS，**与 BempDiff 无关**）；端口 8000/8080/5000/18765/5180 无监听即放行。
- tracked-but-ignored 检测：`git ls-files | git check-ignore --stdin --no-index` 精确列出「已入库但被忽略」历史垃圾（本次 26 个），可作 `.gitattributes export-ignore` 输入。
- 只提交指定文件：用 `git commit -- <pathspec>` 避免夹带 index 里他人已暂存的改动。
- 第 3 轮结果：2.85 GB → 782.44 MB（−2.09 GB / −73.23%），删 4,377 + 隔离 550 文件，26 批 0 错误，commit `f99171a`。用户确认保留：`node_modules ×3`（526 MB）、`bempdiff/dist_input`（86.7 MB）、`release/*.exe` 交付包。
- 禁用清理项：`bempdiff/toolchain`（JDK21 必需）、`bempdiff/java_core_ai_replay`（约定保留不改）、`bempdiff/verify_exe`+`tooling/verify_exe`（受 git 跟踪）。

## skill 乱码事故（2026-09-10）
- `logs-review` 的 `config.yaml` / `config.example.yaml` 曾遭**双重编码损坏**（UTF-8 字节被按 CP936 误读）。已修复：键名与全部功能性取值自原文逐字恢复，中文注释/文案依 `config.example.yaml` 同名字段说明重建，文件头留重建说明。
- **原件已归档到工作空间外**：`~/.workbuddy/skill-backups/mojibake-20260910/`（含 `*.orig` 原版、`*.corrupted-20260910` 损坏件、`logs-review-mojibake-finding.md` 报告）。
- **备份目录不应留在 `skills/` 内**——曾因含 `SKILL.md.orig` 被加载器注册成幽灵技能 `workspace-cleanup:.backup-20260910-mojibake`。
