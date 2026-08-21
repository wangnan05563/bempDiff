# BempDiff 功能梳理 · 竞品对比 · AI 方向优化报告

> 文档定位：基于代码库实勘（非规划臆测）梳理 BempDiff 当前能力基线，横向对比同类竞品核心能力与差异化特征，并围绕**人工智能方向**提出可落地优化功能点，覆盖智能推荐、自动化处理、自然语言交互、客户体验四大场景。每点均给出对应的体验改进与落地优先级（P0/P1/P2）。
>
> 实勘基线：`java_core/`（Java 后端 sidecar，端口 18765）、`bempdiff/webui/src/`（Vue3/Bootstrap 前端）、`bempdiff/dev-shell/`（Electron 壳）。实勘时间：2026-08-17。

---

## 一、当前功能清单（按模块）

下表仅收录**代码已证实实现**的能力；未实现或仅部分实现的标注"部分/缺失"。

| 模块 | 功能点 | 状态 | 关键实现 / 证据 |
|---|---|---|---|
| **差异对比引擎** | 包 vs 包（文件级）对比 | ✅ 已实现 | `DiffEngine.compute()`：状态 ADDED/DELETED/MODIFIED/UNCHANGED，MODIFIED 以 **SHA-256 不等**判定（`DiffEngine.java:38`） |
| | 文件夹 vs 文件夹对比 | ✅ 已实现 | `FolderDiff.compare()`：按 name/type/size/mtime/流 SHA-256 区分 LEFT_ONLY/RIGHT_ONLY/MODIFIED/SAME/TYPE_MISMATCH（`FolderDiff.java:229`） |
| | 分层对比 L0/L1/L2 | ✅ 已实现 | `Layer` 枚举：L0 包结构 / L1 业务类 / L2 三方依赖；`DiffEngine.collectL1ClassCandidates()` |
| | 差异算法 | ✅ 已实现 | 自研 **LCS 行级 DP**（非 Myers）；`DiffRules` 忽略规则：空白/注释/正则（`DiffRules.java:48`） |
| | 行内差异高亮 | ✅ 已实现 | 行/词/字符三级内联高亮（`DiffView.vue`） |
| **反编译引擎** | CFR 反编译 | ✅ 已实现 | `Decompiler.cfrInProcess()` 进程内零冷启动；失败降级 CLI / `javap`（`Decompiler.java:118/177`） |
| | 产物解析 | ✅ 已实现 | `.jar/.war` → 内嵌 `.class` 反编译为可比文本 `DecompiledUnit`（`Decompiler.java:242`） |
| | 反编译缓存 | ✅ 已实现 | SHA-256 LRU 缓存（max 256）（`Decompiler.java:55`） |
| **前端代码对比** | JS/HTML/CSS/JSP/CONFIG 对比 | ✅ 已实现 | `FrontendTextDiff.diff()` + `beautify()`，统一为 `DecompiledUnit`（`FrontendTextDiff.java:26/72`） |
| **Web 界面** | DiffView 主对比区 | ✅ 已实现 | 双栏网格、专注模式+Esc、换行切换、折叠未变、上/下一处差异（Ctrl/Alt+↑↓）、多标签页 |
| | DiffTree 文件树 | ✅ 已实现 | 模糊+正则搜索过滤、状态着色（`:73/:77`） |
| | 智能分析栏 InfoPanel | ✅ 已实现 | 单文件/全局/破坏性/审计 多 Tab；收起 + 响应式避让（本次 task #33 已强化） |
| | AiConsole（原 AiAnalysisDialog） | ✅ 已实现 | **非阻塞**并行 AI 控制台：智能分析栏下方常驻输出框，SSE 实时流式（思维链可折叠 + Markdown 答案逐字渲染）；多类别并行任务独立标签页 + 各自 AbortController；中断/重新分析/预览报告按钮；分析期间 ToolBar 对比按钮与路径输入框自动禁用（`store.aiBusy`）；点击「预览报告」复用 `ReportPreview` 弹层（`AiConsole.vue`/`store.js`/`ToolBar.vue`） |
| | ToolBar / StatusBar / ReportPreview | ✅ 已实现 | 对比类型、报告/AI/导出菜单、主题切换、全屏报告预览 |
| **AI 能力** | 两阶段 AI 分析 | ✅ 已实现 | StageA 总览风险/影响/测试主题 + StageB 单文件深读（`AiAnalyzer`） |
| | 分析器可插拔 | ✅ 已实现 | `MockAiAnalyzer`（离线重放）/ `HttpAiAnalyzer`（OpenAI/Azure/Ollama 兼容）互换 |
| | 成本闸门 | ✅ 已实现（2026-08-18） | 后端 `ai-estimate` 精确预估（复用 `PromptBuilders`+`estimateTokens`，per-job + 阈值缓存）+ 前端超阈值**强制确认弹窗**（`CostGateDialog`），覆盖三大 AI 入口（AI 报告/流式分析/智能分类），契合金融合规 |
| | 脱敏 | ✅ 已实现 | `PromptBuilders.sanitize()` 清洗身份证/密钥/手机号，本地/ollama 跳过（`PromptBuilders.java:165`） |
| | API Key 默认不落盘 | ✅ 已实现 | `persistApiKey=false`，UI 开关"记住 API Key（默认关闭）" |
| | 网络安全加固 | ✅ 已实现 | SSRF 防护、TLS 降级阶梯、代理、重试（`HttpAiAnalyzer`） |
| **报告与导出** | Markdown 报告 | ✅ 已实现 | 一~七章（含依赖 JAR 差异、破坏性变更、AI 分析）（`MarkdownReport.java`） |
| | 导出 | ✅ 已实现 | 反编译源码 zip + diff 类/jar（`AssetExporter`） |
| | 离线 mock 模式 | ✅ 已实现 | `--apikey ""` 强制 `MockAiAnalyzer` |
| **桌面壳与启动** | Electron + Java sidecar | ✅ 已实现 | `main.js` 拉起 `com.bempdiff.Main server --port 18765`，进程回收、原生文件对话框 |
| | NSIS 打包 | ✅ 已实现 | electron-builder（`package.json`：`win.target:"nsis"`） |
| **批处理 / CLI** | 子命令 | ✅ 已实现 | `inspect / compare / compare-folders / folderdiff / decompile / report / export / diff-jars / ai / server`（`Main.main()` 内联 switch） |

**实勘更正（相对历史记忆）**：
- `DiffView` **并非真正虚拟化滚动**——整 `.diff-area` 容器一次性渲染所有行，大差异包存在性能风险（"虚拟化"为未证实项）。
- 成本闸门**仅告警、未强制拦截**。
- 历史记忆中的 `App.isBatchSubcommand()` **不存在**，实际为 `Main` 内联 `switch`。

---

## 二、竞品核心能力与差异化对比

### 2.1 竞品能力矩阵

| 能力维度 | Beyond Compare 5 | WinMerge / Meld / KDiff3 | GitHub Copilot / CodeRabbit / Qodo | **BempDiff 现状** |
|---|---|---|---|---|
| 对比对象广度 | 文本/二进制/图片/表格/十六进制/注册表/MP3 | 文本/文件夹（WinMerge 新增图片/表格） | 源码 PR/提交（需源码） | 包/文件夹/反编译源码/前端代码 |
| 三向合并 | ✅ | ✅（WinMerge/KDiff3） | ❌（评审不合并） | ❌ 缺失 |
| 文件夹同步（复制/删除） | ✅ | ✅（WinMerge/Araxis） | ❌ | ❌ 仅对比 |
| VCS / 云 / FTP 集成 | ✅ | ✅（Git/SVN 外部工具） | ✅（原生 PR 流） | ❌ 缺失（非核心定位） |
| 压缩包直读 | ✅（ZIP/7z/ISO） | ✅（WinMerge 7-Zip） | ❌ | ✅ `.jar/.war` 直读（仅 Java 生态） |
| **反编译源码级 diff** | ❌（需外部工具且拿不到源码） | ❌ | ❌（需源码 PR） | ✅ **独有护城河** |
| 二进制/图片/十六进制对比 | ✅ | 部分 | ❌ | ❌ 缺失 |
| AI 分析 | ❌ 无原生 AI | ❌ | ✅ AST 代码图 / 跨文件依赖 / 自动修复 / 从历史学习 | ⚠️ 两阶段风险/影响/测试点 + 脱敏/成本闸门（缺代码图/自动修复/学习） |
| 自动化 / 脚本 | ✅ 脚本 + CI | ⚠️ 有限 | ✅ PR 自动评审 / 后台 agent | ⚠️ CLI 子命令（缺 GUI 批量编排） |
| **离线 / 私有化** | ✅ 纯本地 | ✅ 纯本地 | ❌ 多依赖上云 SaaS | ✅ **本地 sidecar + 可本地 ollama（合规强项）** |
| 合规（Key 不落盘/脱敏/闸门） | ❌ | ❌ | ⚠️ 依赖厂商 | ✅ **强项** |

### 2.2 BempDiff 定位与差异化结论

**卡位一句话**：面向企业**发布验证 / 合规审计**的、可**离线私有化**、针对**编译产物（jar/war）反编译源码级差异 + AI 风险影响分析**的垂直工具。

- **相对 BC/WinMerge（传统对比工具）**：对比"广度"是弱项（无三向合并、无文件夹同步、无二进制/图片对比），但拥有它们完全没有的**「无源码、比构建产物、私有化合规」**护城河——这正是金融/政企客户在"上线前比对两个生产构建到底改了什么"场景下的刚需。
- **相对 AI 评审工具（CodeRabbit/Qodo/Copilot）**：它们强在 AST 代码图、跨文件依赖、自动修复、从历史学习，但**必须基于源码 PR 且多依赖上云**；BempDiff 的差异化是"拿不到源码也能比、且数据不出内网"，代价是 AI 深度（代码图/自动修复/学习）目前较弱。
- **机会窗口**：把"AI 评审工具的能力"嫁接到"无源码 + 私有化"的场景上——即**在反编译后的可比源码上做 AST/跨文件分析、自动建议、对话式追问**，这是竞品都够不到的交叉地带。

---

## 三、AI 方向可落地优化功能点

> 设计原则：复用现有两阶段 AI 框架（`AiAnalyzer` / `PromptBuilders` / SSE 流式）、现有脱敏与成本闸门，尽量"低侵入、高复用"。优先级 P0（护城河 + 高频痛点）→ P1（差异化增强）→ P2（广度补齐）。

### A. 智能推荐（Intelligent Recommendation）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| A1 | **智能审阅优先级推荐** | AI 对全部差异按「风险 × 影响」打分，给出"建议优先审阅 Top N" | 审计人员先聚焦关键变更，**信噪比↑、平均审阅时长↓**；不再从头翻到尾 | P0 · ✅ 已实现 |
| A2 | **影响面 / 回归范围推荐** | 结合历史 release 数据 + 调用链，推荐"本次变更需回归的模块/接口" | 测试点从"通用模板"升级为"精准清单"，**漏测率↓** | P0 |
| A3 | **反编译 / 忽略规则智能推荐** | 首轮噪声自动诊断，推荐最优 ignore 组合（屏蔽 L2 整包、屏蔽生成代码） | **首轮对比信噪比↑、误报↓**，减少人工调规则 | P1 |
| A4 | **个性化默认推荐** | 记忆用户历史偏好（忽略注释、常收起分析栏、常用引擎），新任务自动套用 | **零配置上手、重复操作↓** | P1 |

### B. 自动化处理（Automation）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| B1 | **GUI 批量对比编排 + 汇总报告** | 可视化配置多组"基线 vs 目标"，一键批量跑并出总报告 | 多环境/多版本并行验证，**一次点击出全局视图**（把 BC 脚本能力补到 GUI） | P1 |
| B2 | **差异自动分类打标** | AI 逐处打标（新增接口 / 删除字段 / SQL 变更 / 配置变更 / 依赖升级 / 安全相关），可筛选 | **审计清单自动生成，按类秒级定位** | P0 · ✅ 已实现 |
| B3 | **测试点 → 可执行用例生成与导出** | AI 测试点直接产出 JUnit / Postman 骨架，一键导出 | 从"建议"到"可执行"，**测试准备闭环** | P1 |
| B4 | **自动生成发布说明 / 变更摘要** | 基于 diff + AI 分析产出 changelog / 发版说明（含风险等级） | **发版文档自动化，人工整理工时↓** | P1 |
| B5 | **三方合并建议（补齐 BC 3-way）** | 对"共同祖先 vs 两份构建"给出配置/资源文件自动合并建议（仅建议不自动改） | **覆盖多分支发布，减少手动合并冲突** | P2 |

### C. 自然语言交互（NL Interaction）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| C1 | **NL 差异检索** | "显示所有删除的 public 方法""找出涉及金额计算的修改" → 自然语言转结构化查询 | **非技术干系人（测试/PM/合规）也能精准检索**，无需懂结构 | P1 |
| C2 | **对话式分析面板** | 分析栏内嵌对话，针对当前 diff 追问"为什么有风险""给个回滚建议" | 从"静态报告"升级为**可追问的分析伙伴** | P1 |
| C3 | **NL 指令执行** | "把分析栏收起""导出高风险为报告" | **操作便捷，减少菜单寻找** | P2 |
| C4 | **高管语义摘要** | 整包变更 NL 生成"这次发布主要改了什么、为什么、风险几何" | **面向决策层可读性，汇报效率↑** | P2 |

### D. 客户体验（操作便捷 / 响应效率 / 个性化 / 界面友好）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| D1 | **拖拽即比 + 右键 Shell 集成** | 拖两个 jar 到窗口直接对比；资源管理器右键"用 BempDiff 打开" | **进入成本↓**，贴合用户既有习惯 | P0 |
| D2 | **差异书签 / 锚点** | 对关键差异打书签，跨会话保留 | **审计连续性↑**，断点可续 | P1 |
| D3 | **快捷键可视化 + 可自定义** | 设置面板展示并允许用户重映射 | **高级用户效率↑** | P2 |
| D4 | **真正虚拟化滚动（大包不卡）** | 当前 DiffView 整容器渲染；改为窗口化渲染 | **大差异包流畅**，消除卡顿焦虑 | P0 |
| D5 | **跨会话持久化反编译缓存** | 强化现有 LRU（max 256，内存级）→ 落盘持久化 | **二次对比秒开，响应效率↑** | P0 |
| D6 | **AI 后台预分析（不阻塞 UI）** | 打开即后台开始分析，SSE 流式（已有）叠加"预分析" | **长任务无阻塞感** | P1 · ✅ 已实现（2026-08-18）：取消阻塞式 `AiAnalysisDialog` 全屏遮罩，改为 `AiConsole` 常驻非阻塞控制台；支持多类别并行任务（risk/breaking/impact/testpoints/custom）独立标签页、各自 AbortController 中断、思维链折叠、Markdown 逐字渲染、分析完成点击预览报告；分析期间自动禁用 ToolBar 对比操作防干扰 |
| D7 | **角色视图（开发/测试/合规/PM）** | 不同角色默认视图与摘要粒度不同 | **各角色开箱即得所需**，个性化 | P1 |
| D8 | **历史对比档案 / 纵向趋势** | 同基线多次对比，展示"哪些模块持续在变" | **趋势洞察，辅助技术债判断** | P1 |
| D9 | **差异导航地图 minimap** | 对标 BC location pane，大文件快速定位 | **大文件导航效率↑** | P1 |
| D10 | **空状态 / 首启引导 + 进度阶段可视化** | 长任务分阶段（反编译→对比→AI）进度展示 | **新手友好，长任务焦虑↓** | P1 |

---

## 四、落地优先级与里程碑建议

### P0（护城河强化 + 高频痛点，建议首批）
1. **D4 真正虚拟化滚动** —— 性能刚需，当前大包有卡顿风险。
2. **A1 智能审阅优先级 + B2 差异自动分类打标** —— 直接复用现有两阶段 AI，AI 价值最显性、ROI 最高。**✅ 已实现（2026-08-18）：新增 `ai-classify` 端点（BempServer）+ 前端工具栏「智能分类」按钮 + 差异树风险点/类别徽章 + 按风险排序/过滤；后端 95/95 测试通过、Vite 33 模块构建通过、活体冒烟验证分类正确（InvoiceService→服务/MEDIUM）。**
3. **D1 拖拽即比 + 右键 Shell 集成** —— 进入成本，决定"用不用得起来"。
4. **D5 跨会话持久化缓存** —— 响应效率，低成本高感知。**✅ 已实现（2026-08-17）：`Decompiler.java` 叠加内容寻址磁盘层 + `DecompileTest` 新增跨实例回归用例，全量 95/95 测试通过。**
5. **成本闸门升级**（基于实勘：当前 warn-only）→ 改为"超阈值强制确认 + 预估展示"，契合金融合规。**✅ 已实现（2026-08-18）：后端新增 `ai-estimate` 端点（`BempServer.handleAiEstimate`，复用 `MockAiAnalyzer.buildStageA/B`+`estimateTokens`，per-job 缓存 key 含阈值以便配置热更新失效）；前端 `store.ensureAiBudget()` 在三大 AI 入口前置拦截，超阈值弹 `CostGateDialog` 强制确认（含预估 token/近似字符数/金融合规提示，ratio≥3 升级危险样式）；`report` 的 silent 自动报告超阈值则跳过并 toast。Java 93/95（2 例预存 AiTest/ContextAiTest plain-text 路径 `UnsupportedOperationException`，与本功能无关）+ Vite 35 模块构建通过，活体冒烟验证阈值随 `costGateWarnTokens` 热更新生效（8000→100→50，超阈值触发拦截）。**

### 已落地清单（截至 2026-08-18）
- **D5 跨会话持久化反编译缓存**（2026-08-17）：`Decompiler.java` 内容寻址磁盘层 + `DecompileTest` 跨实例回归，全量 95/95 通过。
- **A1 智能审阅优先级 + B2 差异自动分类打标**（2026-08-18）：后端 `ai-classify` 端点复用两阶段 stageB（同受成本闸门/脱敏约束），前端「智能分类」按钮 + 差异树风险等级（高/中/低）+ 类别徽章（接口/服务/数据/配置/前端脚本/前端模板/前端样式/页面/业务代码）+ 按风险排序与过滤；无 API Key 时 risk 走确定性启发式、category 始终启发式，离线可验证。Java 95/95 + Vite 33 模块构建通过，活体冒烟确认分类正确（InvoiceService→服务/MEDIUM）。
- **成本闸门升级（P0 #5）**（2026-08-18）：后端 `ai-estimate` 端点精确预估三大 AI 入口 token 消耗（报告/流式分析 = stageA + stageB，智能分类 = 仅 stageB），per-job 缓存（key 含阈值）；前端 `ensureAiBudget()` 在 AI 报告（含 silent 自动报告，超阈值跳过并提示）、流式分析、智能分类三入口前置，超阈值弹 `CostGateDialog` 强制确认（展示预估 token / 近似字符数 / 金融合规提示，ratio≥3 升级为危险样式）。阈值随配置热更新即时生效。

- **AI 分析 UX 重构 · 非阻塞并行控制台（D6，2026-08-18）**：取消阻塞式 `AiAnalysisDialog` 全屏遮罩（原"正在调用AI分析"遮罩卡死 UI），改为 `AiConsole.vue` 常驻智能分析栏下方。五大子需求全部落地：① 控制台输出框 SSE 实时流式（思维链可折叠 + Markdown 逐字渲染）；② 中断/重新分析按钮（各自 AbortController，置于 `reactive` 外 `aiControllers` Map）；③ 分析期间 ToolBar 对比类型按钮、双路径 input、浏览按钮、对比按钮自动 `:disabled`（`aiBusy` 计算）；④ 分析完成「预览报告」复用 `ReportPreview` 弹层（`state.previewMd/previewOpen`）；⑤ 多类别并行任务（risk/breaking/impact/testpoints/custom）独立标签页各自控制台，后端 `Executors.newVirtualThreadPerTaskExecutor()` 原生并行。后端新增 `category`/`prompt` 路由（`BempServer.handleAiAnalyze` 解析 + `runAiAnalysis` 透传 `buildFocus` 聚焦指令，`AiAnalyzer`/`PromptBuilders`/`MockAiAnalyzer`/`HttpAiAnalyzer` 加 focus-aware 默认方法），不同类别产出差异化内容。`AiAnalysisDialog.vue` 已废弃（不再被 App 引用）。**验证：Java 107/107 测试通过（core/test/testrunner 均 exit 0）；Vite 35 模块构建通过。**

> 复用与一致性：A1+B2 与 `ai-analyze` 共用 `AiAnalyzer`/成本闸门/脱敏管线；差异树打标结果可一键按风险重排，直接支撑 A1「优先审阅 Top N」。

### P1（差异化增强，第二批）
- C1 NL 差异检索 + C2 对话式分析面板（自然语言交互核心）
- B3 测试点→用例导出 + B4 自动发布说明（自动化闭环）
- D7 角色视图 + D8 历史档案（个性化）
- D9 minimap + D10 进度阶段可视化（界面友好）
- B1 GUI 批量编排、A3/A4 智能规则与个性化推荐、D2 书签、D6 后台预分析

### P2（广度补齐，第三批）
- B5 三方合并建议（BC 强项补齐）
- 文件夹同步（复制/删除）、二进制/图片/十六进制对比（非 Java 场景扩展）
- C3 NL 指令执行、C4 高管摘要（锦上添花）

---

## 五、结论

BempDiff 已具备**「反编译源码级差异 + 两阶段 AI 风险影响分析 + 私有化合规」**三位一体的独特定位，在传统对比工具（BC/WinMerge）与 AI 评审工具（CodeRabbit/Qodo）之间卡住了一个两者都够不到的交叉地带：**无源码、比构建产物、数据不出内网**。

当前最值得投入的方向不是去和 BC 拼"对比广度"（三向合并/二进制/云同步），而是把 **AI 评审工具的能力移植到反编译后的可比源码上**——智能优先级、自动分类打标、自然语言检索与对话式分析、测试点闭环，这既能放大既有护城河，又能以最低侵入复用现有 AI 框架。配套把性能（虚拟化、持久化缓存）与进入成本（拖拽、Shell 集成）补齐，即可在"企业发布验证 / 合规审计"垂直场景形成明显差异化优势。
