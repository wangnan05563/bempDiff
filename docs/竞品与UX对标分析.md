# BempDiff 竞品与 UX 对标分析（合并集）

> 合并自四份分析与盘点文档，保留全部独有结论：
> - 第一部分：功能梳理 · 竞品对比 · AI 方向优化（原《BempDiff_AI优化与竞品分析报告.md》，2026-08-17）
> - 第二部分：同类产品可借鉴功能续析（第二批）（原《BempDiff同类产品可借鉴功能续析-第二批.md》，2026-08-18）
> - 第三部分：架构与 UX 对标评估（原《BempDiff架构与UX对标评估.md》，2026-08-14）
> - 第四部分：UX 优化需求盘点（原《UX优化需求盘点-2026-08-17.md》，2026-08-17）

## 第一部分：功能梳理 · 竞品对比 · AI 方向优化
> 文档定位：基于代码库实勘（非规划臆测）梳理 BempDiff 当前能力基线，横向对比同类竞品核心能力与差异化特征，并围绕**人工智能方向**提出可落地优化功能点，覆盖智能推荐、自动化处理、自然语言交互、客户体验四大场景。每点均给出对应的体验改进与落地优先级（P0/P1/P2）。
>
> 实勘基线：`java_core/`（Java 后端 sidecar，端口 18765）、`bempdiff/webui/src/`（Vue3/Bootstrap 前端）、`bempdiff/dev-shell/`（Electron 壳）。实勘时间：2026-08-17。

---

#### 一、当前功能清单（按模块）

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

#### 二、竞品核心能力与差异化对比

##### 2.1 竞品能力矩阵

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

##### 2.2 BempDiff 定位与差异化结论

**卡位一句话**：面向企业**发布验证 / 合规审计**的、可**离线私有化**、针对**编译产物（jar/war）反编译源码级差异 + AI 风险影响分析**的垂直工具。

- **相对 BC/WinMerge（传统对比工具）**：对比"广度"是弱项（无三向合并、无文件夹同步、无二进制/图片对比），但拥有它们完全没有的**「无源码、比构建产物、私有化合规」**护城河——这正是金融/政企客户在"上线前比对两个生产构建到底改了什么"场景下的刚需。
- **相对 AI 评审工具（CodeRabbit/Qodo/Copilot）**：它们强在 AST 代码图、跨文件依赖、自动修复、从历史学习，但**必须基于源码 PR 且多依赖上云**；BempDiff 的差异化是"拿不到源码也能比、且数据不出内网"，代价是 AI 深度（代码图/自动修复/学习）目前较弱。
- **机会窗口**：把"AI 评审工具的能力"嫁接到"无源码 + 私有化"的场景上——即**在反编译后的可比源码上做 AST/跨文件分析、自动建议、对话式追问**，这是竞品都够不到的交叉地带。

---

#### 三、AI 方向可落地优化功能点

> 设计原则：复用现有两阶段 AI 框架（`AiAnalyzer` / `PromptBuilders` / SSE 流式）、现有脱敏与成本闸门，尽量"低侵入、高复用"。优先级 P0（护城河 + 高频痛点）→ P1（差异化增强）→ P2（广度补齐）。

##### A. 智能推荐（Intelligent Recommendation）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| A1 | **智能审阅优先级推荐** | AI 对全部差异按「风险 × 影响」打分，给出"建议优先审阅 Top N" | 审计人员先聚焦关键变更，**信噪比↑、平均审阅时长↓**；不再从头翻到尾 | P0 · ✅ 已实现 |
| A2 | **影响面 / 回归范围推荐** | 结合历史 release 数据 + 调用链，推荐"本次变更需回归的模块/接口" | 测试点从"通用模板"升级为"精准清单"，**漏测率↓** | P0 |
| A3 | **反编译 / 忽略规则智能推荐** | 首轮噪声自动诊断，推荐最优 ignore 组合（屏蔽 L2 整包、屏蔽生成代码） | **首轮对比信噪比↑、误报↓**，减少人工调规则 | P1 |
| A4 | **个性化默认推荐** | 记忆用户历史偏好（忽略注释、常收起分析栏、常用引擎），新任务自动套用 | **零配置上手、重复操作↓** | P1 |

##### B. 自动化处理（Automation）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| B1 | **GUI 批量对比编排 + 汇总报告** | 可视化配置多组"基线 vs 目标"，一键批量跑并出总报告 | 多环境/多版本并行验证，**一次点击出全局视图**（把 BC 脚本能力补到 GUI） | P1 |
| B2 | **差异自动分类打标** | AI 逐处打标（新增接口 / 删除字段 / SQL 变更 / 配置变更 / 依赖升级 / 安全相关），可筛选 | **审计清单自动生成，按类秒级定位** | P0 · ✅ 已实现 |
| B3 | **测试点 → 可执行用例生成与导出** | AI 测试点直接产出 JUnit / Postman 骨架，一键导出 | 从"建议"到"可执行"，**测试准备闭环** | P1 |
| B4 | **自动生成发布说明 / 变更摘要** | 基于 diff + AI 分析产出 changelog / 发版说明（含风险等级） | **发版文档自动化，人工整理工时↓** | P1 |
| B5 | **三方合并建议（补齐 BC 3-way）** | 对"共同祖先 vs 两份构建"给出配置/资源文件自动合并建议（仅建议不自动改） | **覆盖多分支发布，减少手动合并冲突** | P2 |

##### C. 自然语言交互（NL Interaction）

| # | 功能点 | 场景 | 对应体验改进 | 优先级 |
|---|---|---|---|---|
| C1 | **NL 差异检索** | "显示所有删除的 public 方法""找出涉及金额计算的修改" → 自然语言转结构化查询 | **非技术干系人（测试/PM/合规）也能精准检索**，无需懂结构 | P1 |
| C2 | **对话式分析面板** | 分析栏内嵌对话，针对当前 diff 追问"为什么有风险""给个回滚建议" | 从"静态报告"升级为**可追问的分析伙伴** | P1 |
| C3 | **NL 指令执行** | "把分析栏收起""导出高风险为报告" | **操作便捷，减少菜单寻找** | P2 |
| C4 | **高管语义摘要** | 整包变更 NL 生成"这次发布主要改了什么、为什么、风险几何" | **面向决策层可读性，汇报效率↑** | P2 |

##### D. 客户体验（操作便捷 / 响应效率 / 个性化 / 界面友好）

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

#### 四、落地优先级与里程碑建议

##### P0（护城河强化 + 高频痛点，建议首批）
1. **D4 真正虚拟化滚动** —— 性能刚需，当前大包有卡顿风险。
2. **A1 智能审阅优先级 + B2 差异自动分类打标** —— 直接复用现有两阶段 AI，AI 价值最显性、ROI 最高。**✅ 已实现（2026-08-18）：新增 `ai-classify` 端点（BempServer）+ 前端工具栏「智能分类」按钮 + 差异树风险点/类别徽章 + 按风险排序/过滤；后端 95/95 测试通过、Vite 33 模块构建通过、活体冒烟验证分类正确（InvoiceService→服务/MEDIUM）。**
3. **D1 拖拽即比 + 右键 Shell 集成** —— 进入成本，决定"用不用得起来"。
4. **D5 跨会话持久化缓存** —— 响应效率，低成本高感知。**✅ 已实现（2026-08-17）：`Decompiler.java` 叠加内容寻址磁盘层 + `DecompileTest` 新增跨实例回归用例，全量 95/95 测试通过。**
5. **成本闸门升级**（基于实勘：当前 warn-only）→ 改为"超阈值强制确认 + 预估展示"，契合金融合规。**✅ 已实现（2026-08-18）：后端新增 `ai-estimate` 端点（`BempServer.handleAiEstimate`，复用 `MockAiAnalyzer.buildStageA/B`+`estimateTokens`，per-job 缓存 key 含阈值以便配置热更新失效）；前端 `store.ensureAiBudget()` 在三大 AI 入口前置拦截，超阈值弹 `CostGateDialog` 强制确认（含预估 token/近似字符数/金融合规提示，ratio≥3 升级危险样式）；`report` 的 silent 自动报告超阈值则跳过并 toast。Java 93/95（2 例预存 AiTest/ContextAiTest plain-text 路径 `UnsupportedOperationException`，与本功能无关）+ Vite 35 模块构建通过，活体冒烟验证阈值随 `costGateWarnTokens` 热更新生效（8000→100→50，超阈值触发拦截）。**

##### 已落地清单（截至 2026-08-18）
- **D5 跨会话持久化反编译缓存**（2026-08-17）：`Decompiler.java` 内容寻址磁盘层 + `DecompileTest` 跨实例回归，全量 95/95 通过。
- **A1 智能审阅优先级 + B2 差异自动分类打标**（2026-08-18）：后端 `ai-classify` 端点复用两阶段 stageB（同受成本闸门/脱敏约束），前端「智能分类」按钮 + 差异树风险等级（高/中/低）+ 类别徽章（接口/服务/数据/配置/前端脚本/前端模板/前端样式/页面/业务代码）+ 按风险排序与过滤；无 API Key 时 risk 走确定性启发式、category 始终启发式，离线可验证。Java 95/95 + Vite 33 模块构建通过，活体冒烟确认分类正确（InvoiceService→服务/MEDIUM）。
- **成本闸门升级（P0 #5）**（2026-08-18）：后端 `ai-estimate` 端点精确预估三大 AI 入口 token 消耗（报告/流式分析 = stageA + stageB，智能分类 = 仅 stageB），per-job 缓存（key 含阈值）；前端 `ensureAiBudget()` 在 AI 报告（含 silent 自动报告，超阈值跳过并提示）、流式分析、智能分类三入口前置，超阈值弹 `CostGateDialog` 强制确认（展示预估 token / 近似字符数 / 金融合规提示，ratio≥3 升级为危险样式）。阈值随配置热更新即时生效。

- **AI 分析 UX 重构 · 非阻塞并行控制台（D6，2026-08-18）**：取消阻塞式 `AiAnalysisDialog` 全屏遮罩（原"正在调用AI分析"遮罩卡死 UI），改为 `AiConsole.vue` 常驻智能分析栏下方。五大子需求全部落地：① 控制台输出框 SSE 实时流式（思维链可折叠 + Markdown 逐字渲染）；② 中断/重新分析按钮（各自 AbortController，置于 `reactive` 外 `aiControllers` Map）；③ 分析期间 ToolBar 对比类型按钮、双路径 input、浏览按钮、对比按钮自动 `:disabled`（`aiBusy` 计算）；④ 分析完成「预览报告」复用 `ReportPreview` 弹层（`state.previewMd/previewOpen`）；⑤ 多类别并行任务（risk/breaking/impact/testpoints/custom）独立标签页各自控制台，后端 `Executors.newVirtualThreadPerTaskExecutor()` 原生并行。后端新增 `category`/`prompt` 路由（`BempServer.handleAiAnalyze` 解析 + `runAiAnalysis` 透传 `buildFocus` 聚焦指令，`AiAnalyzer`/`PromptBuilders`/`MockAiAnalyzer`/`HttpAiAnalyzer` 加 focus-aware 默认方法），不同类别产出差异化内容。`AiAnalysisDialog.vue` 已废弃（不再被 App 引用）。**验证：Java 107/107 测试通过（core/test/testrunner 均 exit 0）；Vite 35 模块构建通过。**

> 复用与一致性：A1+B2 与 `ai-analyze` 共用 `AiAnalyzer`/成本闸门/脱敏管线；差异树打标结果可一键按风险重排，直接支撑 A1「优先审阅 Top N」。

##### P1（差异化增强，第二批）
- C1 NL 差异检索 + C2 对话式分析面板（自然语言交互核心）
- B3 测试点→用例导出 + B4 自动发布说明（自动化闭环）
- D7 角色视图 + D8 历史档案（个性化）
- D9 minimap + D10 进度阶段可视化（界面友好）
- B1 GUI 批量编排、A3/A4 智能规则与个性化推荐、D2 书签、D6 后台预分析

##### P2（广度补齐，第三批）
- B5 三方合并建议（BC 强项补齐）
- 文件夹同步（复制/删除）、二进制/图片/十六进制对比（非 Java 场景扩展）
- C3 NL 指令执行、C4 高管摘要（锦上添花）

---

#### 五、结论

BempDiff 已具备**「反编译源码级差异 + 两阶段 AI 风险影响分析 + 私有化合规」**三位一体的独特定位，在传统对比工具（BC/WinMerge）与 AI 评审工具（CodeRabbit/Qodo）之间卡住了一个两者都够不到的交叉地带：**无源码、比构建产物、数据不出内网**。

当前最值得投入的方向不是去和 BC 拼"对比广度"（三向合并/二进制/云同步），而是把 **AI 评审工具的能力移植到反编译后的可比源码上**——智能优先级、自动分类打标、自然语言检索与对话式分析、测试点闭环，这既能放大既有护城河，又能以最低侵入复用现有 AI 框架。配套把性能（虚拟化、持久化缓存）与进入成本（拖拽、Shell 集成）补齐，即可在"企业发布验证 / 合规审计"垂直场景形成明显差异化优势。


## 第二部分：同类产品可借鉴功能续析（第二批）
> 文档性质：竞品对标补遗 / 优化需求池（第二批）
> 日期：2026-08-18
> 关联文档：
> - `竞品与UX对标分析.md（第三部分）`（第一批：BC / Recaf / jar-analyzer / japi / GitHub·JetBrains diff）
> - `竞品与UX对标分析.md（第一部分）`（第一批 AI 方向：BC5 / WinMerge·Meld·KDiff3 / Copilot·CodeRabbit·Qodo）
> - `需求规格说明书-客户体验提升对标BeyondCompare.md`（FR-CX 系列）
> - `竞品与UX对标分析.md（第四部分）`（B-/F-/E- 缺陷与 QW-/P-/S- 清单）

---

#### 0. 为什么需要"第二批"

首批对标已覆盖 **7 类竞品**（Beyond Compare、Recaf、jar-analyzer、japi-compliance-checker/revapi、GitHub/JetBrains diff、WinMerge·Meld·KDiff3、AI 评审 CodeRabbit·Qodo·Copilot），并形成 QW / P0-P2 / S / FR-CX / A-B-C-D / B-F-E 多套编号体系。

但 BempDiff 的垂直定位是 **"Java 构建产物（WAR/JAR/EAR）差异比对 + 反编译 + AI 风险/影响分析 + 私有化合规审计"**，从这一垂直定位看，仍有 **5 类高度相关、却未被现有文档系统覆盖** 的竞品能力类别，且它们恰好对应 BempDiff 当前的几处真实短板：

| 短板（来自既有文档） | 缺失能力 | 对应新类别 |
|---|---|---|
| B-8 AI 风险未结构化（仅为文本结论） | 确定性安全规则、可机读风险 | SAST 安全规则、SBOM/漏洞 |
| 反编译仅 CFR 一款，可读性与边界 case 有限 | 多反编译器对照、字节码视图 | 多反编译器/字节码视图 |
| AI 分析为单文件深读（StageB），缺全仓上下文 | 跨文件代码图推理 | AI 代码评审（Greptile 式） |
| 比对结果含大量格式/import 噪声 | 语义化（AST）比对降噪 | 语义化 diff |
| 无依赖升级/漏洞视角 | SBOM + CVE 可达性 | SBOM/依赖漏洞扫描 |

为避免与既有编号冲突，本批统一使用 **`NX-` 系列**（Next / 续析）编号。

---

#### 1. 五类新竞品能力核验（截至 2026-08-18）

##### 1.1 语义化 diff（比对降噪）
- **diffsitter**：基于 tree-sitter 的语法树 diff，按语言语法而非纯文本行比对，天然忽略空白、注释、import 重排等格式化噪声；支持 Java 等 30+ 语言。
- **difftastic**：结构化 diff，识别表达式/语句级的结构变化，能呈现"代码被移动但未实质改动"，显著减少伪变更。
- **可借鉴点**：在 L0/L1/L2 文本 diff 之外，提供 **L1.5 语义层**——把方法体解析为 AST，按结构比对，仅当语义真变化才标红。这对"同一逻辑换了写法"的 WAR 重打包场景尤其有用。

##### 1.2 SBOM / 依赖漏洞扫描（依赖升级风险评估）
- **Syft**：生成 SBOM（CycloneDX/SPDX），原生支持 Java（Maven/Gradle 元数据、嵌套 jar、WAR/WEB-INF/lib）。
- **Grype / Trivy / Snyk**：基于 SBOM 匹配 CVE 数据库，输出 CVSS 评分、受影响版本区间、固定版本建议；Trivy/Snyk 支持 **可达性（reachability）** 与 **实际使用** 优先级排序，避免告警轰炸。
- **可借鉴点**：把"版本号变了"升级为"这个依赖变更是否在 CVE 影响范围内、是否真的被调用"。这是 BempDiff 现有 AI 风险之外的 **确定性安全视角**，且天然适配金融/政企客户的合规审计诉求。

##### 1.3 AI 代码评审（AI 分析深度）
- **CodeRabbit**：行内评论 + **Learnings**（从用户采纳/驳回历史沉淀团队规则）、自动总结 PR 变更。
- **Greptile**：构建 **全仓代码图**，支持跨文件/跨仓推理，能回答"这个改动影响了哪些调用方"。
- **Qodo（原 Codium）**：多智能体 + **测试生成**，针对变更类自动产出单测骨架。
- **DeepSource**：静态分析 + AI 修复建议，**自动开 fix PR**。
- **可借鉴点**：BempDiff 的 StageB 是"单文件深读"，缺 **跨文件影响面** 与 **从历史反馈学习** 两件事；且当前 AI 仅产出文本结论，无"建议修复 patch / 可采纳的 diff"。

##### 1.4 多反编译器 / 字节码视图（反编译质量与可比对性）
- **Bytecode Viewer**：一站式集成 **6 款反编译器**（CFR、Procyon、Fernflower、JD-GUI、JADX、Krakatau），多视图并列对比、字节码搜索、混淆定位；插件体系支持恶意代码/危险调用扫描。
- **可借鉴点**：BempDiff 当前反编译链路为"进程内 CFR → 子进程 CFR → javap 降级"（见 `decompile.py` / `diff_engine.py`）。多反编译器对照可 **自动选最优可读输出**、在 CFR 翻车的方法上 fallback 到 Procyon/Fernflower；字节码视图能在反编译失败时仍给出可用比对。

##### 1.5 SAST 安全规则（安全审计护城河）
- **Semgrep**：YAML 规则，单文件 pattern + Pro 版跨文件 taint 追踪；规则生态丰富、可业务方自定义。
- **CodeQL**：深层数据流/污点分析，适合复杂安全场景。
- **SpotBugs + FindSecBugs**：**130+ 字节码级安全检查**（命令注入、不安全的反序列化、JNDI 注入、XXE、硬编码密钥、Log4Shell 等），直接作用于 `.class`/jar，**无需源码**。
- **可借鉴点**：对 BempDiff 是 **差异化护城河**——现有 AI 风险是"启发式/概率性"，而 FindSecBugs 类字节码规则是"确定性/可审计"，二者互补：AI 负责"影响与可读性解释"，规则引擎负责"硬性安全结论"。且字节码分析无需源码，完美契合"只有 WAR/JAR、没有源码"的现场。

---

#### 2. NX- 需求清单

> 优先级：P0（护城河/强痛点，优先落地）｜P1（明显增益）｜P2（锦上添花）
> 成本：L 轻（<1 周）｜M 中（1–4 周）｜R 重（>1 月 / 需架构改造或外部依赖集成）

| 编号 | 类别 | 需求 | 优先级 | 成本 | 价值要点 |
|---|---|---|---|---|---|
| **NX-01** | 语义化 diff | 引入 tree-sitter AST 比对层（忽略格式/import 噪声） | P1 | R | 比对降噪，减少伪变更干扰 |
| **NX-02** | 语义化 diff | 移动/重命名/块搬移识别（合并拆分方法归类） | P1 | R | 把"搬家"与"真改"区分开 |
| **NX-03** | SBOM/漏洞 | JAR/WAR/EAR 依赖清单提取 + SBOM 生成（含嵌套 jar） | P1 | M | 把"版本变了"结构化 |
| **NX-04** | SBOM/漏洞 | CVE/漏洞匹配（集成 Grype/Trivy 规则库，CVSS + 修复建议） | **P0** | M | 新增确定性安全视角，强合规卖点 |
| **NX-05** | SBOM/漏洞 | 依赖可达性/实际使用优先级排序 | P2 | M | 避免漏洞告警轰炸 |
| **NX-06** | AI 评审 | 代码图/跨文件推理（识别变更影响面与调用方） | P1 | R | 弥补 StageB 单文件局限 |
| **NX-07** | AI 评审 | 从历史反馈学习（Learnings：采纳/驳回沉淀规则） | P2 | M | 越用越准，降低重复误报 |
| **NX-08** | AI 评审 | 自动修复建议（AI 生成 patch/diff，人工确认应用） | P2 | R | 从"告知风险"到"给解法" |
| **NX-09** | AI 评审 | 变更类测试生成（单测骨架） | P2 | R | 辅助回归，金融客户看重 |
| **NX-10** | 多反编译器 | 多反编译器并行比对（CFR/Procyon/Fernflower/JD-GUI 对照选优） | **P0** | M | 反编译质量兜底，提升可读性 |
| **NX-11** | 多反编译器 | 字节码视图 + 字节码搜索（method/const 检索、混淆定位） | P1 | M | 反编译失败仍有可用比对 |
| **NX-12** | 多反编译器 | 危险调用/恶意代码扫描插件（危险 API、硬编码密钥） | P1 | M | 安全线索前置 |
| **NX-13** | SAST | 字节码级安全规则引擎（FindSecBugs 式 130+ 检查） | **P0** | M | 确定性安全护城河，无需源码 |
| **NX-14** | SAST | 轻量自定义规则（Semgrep 式 YAML + 跨文件 taint） | P1 | R | 业务方可自定义合规规则 |
| **NX-15** | SAST | 合规基线/白黑名单（等保/行业基线 + 一键合规报告） | P2 | L | 交付物即合规证据 |

---

#### 3. 与现有需求 / 护城河的协同

| NX 需求 | 衔接的既有项 | 协同说明 |
|---|---|---|
| NX-04、NX-13 | B-8（AI 风险未结构化）、FR-CX-01（智能化差异说明） | 把"AI 文本风险"升级为"**确定性规则结论 + AI 解释**"双轨，风险可机读、可审计 |
| NX-10、NX-11、NX-12 | `decompile.py` / `diff_engine.py`（CFR 单链路） | 多反编译器对照直接修复"CFR 翻车方法"的可读性缺口 |
| NX-01、NX-02 | FR-CX-01、L0/L1/L2 分层 diff | 在文本层之上补"语义层"，是比对降噪的下一阶段 |
| NX-04、NX-13、NX-15 | AI 风险 / 私有化合规 | 三者在"**无需源码、本地私有化、可审计**"上完全一致，构成 BempDiff 对 Beyond Compare / Recaf 的差异化护城河 |
| NX-06、NX-07 | StageA/StageB 两阶段 AI | 补足"全仓上下文"与"从历史学习"，是 AI 方向的纵向深化 |

**护城河定位重申**：BempDiff 不应与 BC/Recaf 在"通用文件 diff / 通用反编译"上正面竞争，而应锁定 **"Java 构建产物 → 私有化 → 安全合规可审计"** 这一垂直切片。本批 NX-04 / NX-13 / NX-15 正是把这一切片做深做厚的关键。

---

#### 4. 落地路线建议

- **V6.1（护城河优先，1–2 月）**：NX-10（多反编译器对照）→ NX-13（字节码安全规则）→ NX-04（CVE 匹配）。三者皆为 **P0 且成本 M**，且能直接产出"安全审计报告"这一高价值交付物。
- **V6.2（体验深化，2–3 月）**：NX-03（SBOM）、NX-11（字节码视图）、NX-12（危险调用扫描）、NX-14（自定义规则）。
- **V6.3（AI 纵向、长期）**：NX-01/NX-02（语义化 diff）、NX-06（跨文件推理）、NX-07/NX-08/NX-09（学习/修复/测试生成）。

> 说明：R 成本项（语义化 diff、跨文件推理、自动修复、测试生成）均依赖外部成熟库（tree-sitter、代码图索引、Agent 编排），建议以"集成而非自研"的思路推进，控制投入。

---

#### 5. 待确认 / 下一步

1. 是否将 NX-04 / NX-10 / NX-13 三个 P0 直接并入 V6.1 迭代计划？
2. NX-13 字节码规则引擎建议优先评估 **FindSecBugs（开源、零源码、130+ 检查）** 作为首版底座，是否同意？
3. SBOM/CVE（NX-03/NX-04）是否接入本地化漏洞库（离线 CVE 镜像）以满足私有化部署？

（以上为建议，待你拍板后进入 WBS 拆解。）


## 第三部分：架构与 UX 对标评估
> 评估对象：`bempdiff/`（Tauri 2 桌面壳 + 内嵌 jlinked JRE + `java_core` 纯 Java CLI + Vue3/Bootstrap Web UI + AI 风险分析）
> 对标基准：Recaf、jar-analyzer、japi-compliance-checker / revapi、Beyond Compare（既定 UX 基线），以及现代 diff 视图（GitHub / react-diff-viewer / JetBrains Diff Viewer）的最佳实践
> 评估维度：整体架构合理性 + 用户体验（UX）短板与可提升空间
> 日期：2026-08-14

---

#### 1. 评估范围与方法

- **架构复核**：实读 `src-tauri/src/lib.rs`、`tauri.conf.json`、`webui/src/**`、`java_core/src/com/bempdiff/**` 确认当前真实状态。
- **竞品调研**：检索 GitHub 上同类开源/商业产品与其公开的 UX 能力，提取可借鉴项。
- **差距分析**：以"审计人员比对两个 WAR/JAR 版本、判断改了什么/是否有风险"为主线任务，逐项对标。

---

#### 2. 当前架构概览与评价

##### 2.1 架构（确认态）

```
┌─────────────────────────────────────────────────────────────┐
│ Tauri 2 桌面壳 (Rust, lib.rs)                                  │
│  - 进程管理：拉起 内嵌 JRE → java -cp bempdiff.jar;cfr.jar     │
│    com.bempdiff.Main server --webroot <webui> --port <随机>    │
│  - TCP 探活 → 建 WebView 窗口指向 http://127.0.0.1:<port>     │
│  - 退出时 kill Java 子进程（无孤儿）                            │
└───────────────────────────┬─────────────────────────────────┘
                            │ 同源托管 (base='./', /api 同域免 CORS)
┌───────────────────────────┴─────────────────────────────────┐
│ Java HttpServer (java_core)  +  Vue3/Bootstrap SPA (webui)     │
│  - 比对引擎：L0 包级 / L1 业务码 / L2 三方依赖                  │
│  - 反编译：CFR 为主、javap 降级；前端 JS/HTML/CSS 美化 diff     │
│  - 文件夹比对 (FolderDiff)、两阶段 AI 风险/影响/测试点分析      │
│  - 报告：MarkdownReport；导出：md + diff 文件                   │
└─────────────────────────────────────────────────────────────┘
```

##### 2.2 架构优势（值得保留）

| 优势 | 说明 |
|---|---|
| **零安装分发** | 内嵌 jlinked 瘦 JRE（48MB）+ bempdiff.jar 随包分发，双击 EXE 即用，无需本机 Java——优于 Recaf / jar-analyzer（需 JRE，尽管后者也带打包 JRE） |
| **关注点分离干净** | Rust 只做进程管理 + 原生窗口 + 原生对话框；业务/反编译全在 `java_core`，前端纯展示。无 JavaFX 依赖，可维护性好 |
| **隐私友好** | 原生对话框直接拿绝对路径，**不上传**；AI 仅发脱敏 diff 摘要、源码不出机（合规到位） |
| **分层差异** | L0/L1/L2 分层比"扁平文件树"更利于审计聚焦（业务码 vs 三方依赖） |
| **差异化能力** | 两阶段 AI 风险/影响评估 + 测试点生成是 Recaf/jar-analyzer/BC 都不具备的独有价值 |
| **前端文本比对** | JS/HTML/CSS 美化 + 同构 DecompiledUnit，竞品普遍只做 Java |

##### 2.3 架构风险（仅提示，非 UX 主线）

- **随机端口 + 每次启动新 JRE 进程**：多开实例会各自启 Java 服务（资源翻倍），无单实例约束；目前可接受。
- **dev/prod 路径分叉**：`tauri dev` 不拉 JRE，需手动起 `server --port 18765`——本机联调有心智负担（已在 `WebUI迁移方案.md` 标注）。
- **前端全量渲染 diff 行**：`DiffView.vue` 用单个 grid 渲染整文件所有行，超大反编译类（数千行）可能卡顿，缺虚拟滚动。
- **比对为只读**：与 Recaf/jar-analyzer 的"可编辑/可导出反编译源码"定位不同，BempDiff 定位是"审计比对"，因此 3-way merge 等编辑能力优先级低（见 §5 P2）。

---

#### 3. 对标对象与各自 UX 标杆能力

| 产品 | 类型 | 标杆 UX 能力（摘取） |
|---|---|---|
| **Beyond Compare** (基线) | 商业文件/文件夹比对 | 三路合并、忽略不重要差异（空白/注释/正则）、会话保存复用(.bcf)、文件夹同步预览、按规则过滤(node_modules/构建产物)、字符级高亮 |
| **Recaf** | 开源 Java 字节码编辑/反编译 | 深色多面板布局、类树 + 多标签代码区 + 右侧成员/继承关系面板、语法高亮、全局搜索、**注释/书签注入反编译结果**、附加到运行进程 |
| **jar-analyzer** (~2k★) | 开源 JAR 分析/审计 | 方法调用图(DFS/污点)、字符串/全局搜索(Lucene)、Spring 路由分析、10 套 UI 主题、前进/后退导航、备忘录/书签、黑白名单过滤、实时 CPU/内存图 |
| **japi-compliance-checker / revapi** | 开源 API 兼容性 | **HTML 报告按严重性红/橙/黄/绿着色**、可折叠分区、兼容性结论徽章、问题分类(方法级/类型级)、签名语法高亮 |
| **GitHub / react-diff-viewer / JetBrains Diff** | diff 视图事实标准 | 分栏(split) ↔ 统一(unified) 切换、**词/字符级行内高亮**、忽略空白、折叠未变区域、上一处/下一处差异跳转(Nav)、同步滚动 |

---

#### 4. BempDiff 现有能力 × 标杆 能力矩阵

| 能力 | BempDiff 现状 | 标杆水平 | 差距 |
|---|---|---|---|
| 分栏双栏 diff | ✅ DiffView 4 列网格 | ✅ | 持平 |
| 行号对照 | ✅ | ✅ | 持平 |
| 状态过滤/搜索树 | ✅ DiffTree：状态按钮+正则搜索+未变开关 | ⚠️ 仅树 key 搜索 | 缺"跨文件内容搜索" |
| 行内 word/char 高亮 | ❌ 仅整行红/绿 | ✅ 词级 | **明显短板** |
| 统一/分栏切换 | ❌ 仅分栏(+换行切换) | ✅ | 短板 |
| 忽略不重要差异 | ❌ 无 | ✅(BC 杀手锏) | **明显短板（审计降噪）** |
| 上/下一项跳转 + 折叠未变 | ❌ 无 | ✅ | 短板（大文件导航） |
| 严重性着色报告 | ⚠️ 有章节但无分级色 | ✅(japi) | 短板 |
| 会话/最近比对保存 | ❌ 无 | ✅(BC) | 短板（重复性审计） |
| 跨文件内容搜索/调用图 | ❌ 无 | ✅(jar-analyzer) | 中短板 |
| 类成员/继承右栏 | ⚠️ InfoPanel 存在，需确认深度 | ✅(Recaf) | 待增强 |
| 多主题 | ⚠️ Bootstrap data-bs-theme 明暗 | ✅(jar-analyzer 10 套) | 轻短板 |
| 可携带 HTML 报告 | ⚠️ md + diff 文件 | ✅(japi HTML) | 待增强 |
| 注释/书签留痕 | ❌ 无 | ✅(Recaf/jar-analyzer) | 中短板（审计留痕） |
| 虚拟滚动(大文件) | ❌ 全量渲染 | ⚠️ | 性能短板 |

---

#### 5. UX 提升机会（按优先级）

> 标注：位置 = 主要改动文件；成本 = 前端为主(L) / 前后端(M) / 重(R)；价值 = 对审计 UX 的提升。

##### P0（高价值、低成本，直接强化审计核心体验）

**P0-1 行内 word/char 级差异高亮**
- 对标：GitHub / react-diff-viewer / JetBrains（"Highlight words"模式）
- 现状：`DiffView.vue` 只把整行标为 add/del，改一个变量名也整行高亮，审计需肉眼找差异。
- 建议：对相邻 add/del 行对做词/字符级 diff（前端即可算，无需改后端），在 `.row.add/.del .code` 内嵌 `<mark>` 高亮变更片段；提供"整行 / 词 / 字符"三档粒度切换（对齐 DataSpell 的 Highlighting mode）。
- 位置：`webui/src/components/DiffView.vue` + 一个小型 intra-line diff 工具函数。
- 成本：L｜价值：高

**P0-2 忽略不重要差异（审计降噪开关）**
- 对标：Beyond Compare「Ignore unimportant differences」（空白/注释/正则忽略行）——BC 的核心卖点。
- 现状：无。WAR/JAR 里大量自动生成代码、时间戳、构建元数据差异会淹没真实逻辑变更。
- 建议：在 `ConfigDialog`/工具栏加"比较规则"：①忽略空白(trim/ignore) ②忽略注释行 ③正则忽略行（如版本号、`Generated.*`、`@Generated`）。规则下发到 `DiffEngine`/`LineDiff`（已有 `LineDiff.unified`，可加规则参数）。
- 位置：`CompareOptions.java` + `LineDiff.java` + `ConfigDialog.vue` + `ToolBar.vue`。
- 成本：M｜价值：高（最贴合审计场景）

**P0-3 上一项/下一项跳转 + 折叠未变区域**
- 对标：JetBrains `F7/Shift+F7 Next/Prev Difference` + `Collapse Unchanged Fragments`。
- 现状：大文件 diff 一长串上下文，无快速跳到下一处变更、无折叠未变。
- 建议：DiffView 顶部加"上一处/下一处"按钮（基于 `rows` 中 add/del 索引滚动定位）+ "折叠未变"开关（隐藏 ctx 行、仅留变更上下文 N 行）。
- 位置：`DiffView.vue`。
- 成本：L｜价值：高（大文件可读性的关键）

**P0-4 AI 报告严重性分级着色 + 兼容性结论徽章**
- 对标：japi-compliance-checker 的 红/橙/黄/绿 严重性 + 兼容性结论 + 可折叠分区。
- 现状：`MarkdownReport` 已有"破坏性变更/审计"等章节，但报告渲染（`ReportPreview.vue` / `render_idea_report.py`）无严重性颜色，章节不可折叠。
- 建议：AI 风险等级(高/中/低)映射到色块徽章；报告顶部加"总体风险结论"汇总条；破坏性变更章节默认展开、其余可折叠。复用现有 `FileRisk`/`StageASummary` 的风险字段。
- 位置：`report/MarkdownReport.java`(加 risk 标记) + `webui/src/components/ReportPreview.vue`(渲染着色) + `render_idea_report.py`(IDEA 报告同步)。
- 成本：M｜价值：高（放大 BempDiff 的 AI 差异化优势）

##### P1（显著增强，中等投入）

**P1-1 分栏 ↔ 统一视图切换**
- 对标：react-diff-viewer `splitView`、JetBrains side-by-side/unified。
- 建议：DiffView 增加视图模式切换（分栏默认、统一可选），统一视图复用同一 `rows` 数据。
- 位置：`DiffView.vue`。
- 成本：L｜价值：中

**P1-2 会话/最近比对保存 + 比对配置档案**
- 对标：Beyond Compare 保存会话(.bcf)、jar-analyzer 启动配置。
- 建议：记录"最近比对"（左/右路径 + 时间）；允许保存"比对档案"（路径对 + 过滤规则 + 比较规则），下次一键复跑——对"每周发版比对同一应用"的重复审计极有价值。
- 位置：`server/JobStore.java` 或新增 `HistoryStore` + `store.js` + 启动页/下拉。
- 成本：M｜价值：中高

**P1-3 跨文件内容搜索 / 调用关系索引**
- 对标：jar-analyzer 全局搜索(Lucene)、方法调用图、Spring 路由分析。
- 建议：在已完成反编译的结果上建轻量索引，支持"在两份反编译代码里搜字符串/方法名/注解"，结果可跳转对应 diff 文件——把 BempDiff 从"两版本差异"扩展到"版本内可检索"，贴合审计人员"找危险调用"的刚需。调用图/污点分析为进阶（R，可后续）。
- 位置：新增 `search/` 模块 + 前端搜索面板。
- 成本：R｜价值：高（审计差异化）

**P1-4 选中类成员/继承/Spring 映射右栏**
- 对标：Recaf 右侧 fields/methods + 继承关系图。
- 建议：强化 `InfoPanel.vue`：对选中 class 展示 成员列表、注解、父类/接口、若含 `@Controller/@RequestMapping` 则提取路由——让"看了 diff 想知道这个类干嘛的"一步到位。
- 位置：`InfoPanel.vue` + `PackageParser`/`Decompiler` 补充抽取成员信息。
- 成本：M｜价值：中

**P1-5 可携带独立 HTML 报告导出**
- 对标：japi 的 self-contained HTML 报告（内联 CSS/JS，可邮件/归档）。
- 建议：在现有 md 报告外，增加"导出单文件 HTML"——内联样式、严重性着色、可离线打开，便于跨团队传阅审计结论。
- 位置：`report/` 新增 `HtmlReport` + 前端导出按钮。
- 成本：M｜价值：中

##### P2（锦上添花 / 偏离只读审计定位）

- **P2-1 多主题**：暗色为默认 + 2~3 套可选（对标 jar-analyzer 10 套）；当前 Bootstrap `data-bs-theme` 已支持明暗，扩展成本低。
- **P2-2 大文件虚拟滚动**：`DiffView` 对 >2000 行启用虚拟列表（对标 react-diff-viewer 虚拟滚动）；避免卡顿。
- **P2-3 差异文件注释/书签留痕**：对某个 diff 文件加审计备注（"此变更因 X 需求预期内"），随报告导出（对标 Recaf 注释注入 / jar-analyzer 备忘录）。
- **P2-4 拖拽比对 / 文件管理器右键**：窗口支持拖入两个文件即比对；低优先级。
- **P2-5 三路合并(3-way merge)**：BCompare 杀手锏，但偏离 BempDiff"只读审计"定位，**不建议**做（投入大、与产品定位不符）。

---

#### 6. 架构层改进建议（支撑上述 UX）

| 建议 | 说明 | 关联 UX |
|---|---|---|
| 比对结果索引化 | 反编译完成后建字符串/符号倒排索引，支撑 P1-3 跨文件搜索 | P1-3 |
| diff 计算支持"规则参数" | `DiffEngine`/`LineDiff` 接收忽略空白/注释/正则规则，供 P0-2 复用 | P0-2 |
| 报告模型携带 risk 字段 | `MarkdownReport` 产出的结构同时给前端着色用，避免前端再解析 markdown | P0-4 |
| 前端 diff 虚拟化 | 大文件按需渲染，避免整文件 grid | P2-2 |
| 历史/档案持久化 | 轻量 JSON 存最近比对与档案，随包 `resources` 或用户目录 | P1-2 |

---

#### 7. 建议落地路线（与现有 P0–P5 节奏一致）

1. **第一轮（P0，建议作为 P6）**：P0-1 行内高亮 + P0-2 忽略不重要差异 + P0-3 跳转/折叠 + P0-4 报告着色。四项均为"审计体验立竿见影"项，且 P0-1/3 纯前端、P0-2/4 前后端小改。
2. **第二轮（P1）**：P1-1 视图切换 → P1-2 会话保存 → P1-4 右栏增强 → P1-5 HTML 报告 → P1-3 跨文件搜索（重者殿后）。
3. **第三轮（P2）**：主题、虚拟滚动、注释留痕。

---

#### 8. 结论

- **架构层面**：BempDiff 当前架构（Tauri 壳 + 内嵌 JRE + 纯 Java CLI + Web UI）**合理且领先于多数同类桌面工具**（零安装、隐私好、关注点分离干净），无需重构；仅需为 UX 增强补少量索引/规则/持久化能力。
- **UX 最大短板**集中在"差异可读性"与"审计降噪"：① 缺行内词级高亮；② 缺"忽略不重要差异"；③ 缺大文件跳转/折叠；④ AI 报告缺严重性着色。这四项（P0）直接对标 Beyond Compare / JetBrains / japi，且成本低、收益高，应优先于任何新功能。
- **差异化护城河**（AI 风险/影响分析、分层 diff、零安装）应作为 UX 重点放大，而非去追 Recaf/jar-analyzer 的"编辑/调用图"能力——后者投入大且与"只读审计"定位不符。


## 第四部分：UX 优化需求盘点（2026-08-17）
> 审查日期：2026-08-17
> 审查范围：`bempdiff/webui/src`（Vue3 + Bootstrap 前端）、`bempdiff/java_core/src`（纯 Java 后端 + 内嵌 HttpServer）、`bempdiff/dev-shell/main.js`（Electron 桌面壳）、`tooling/scripts`（启动/停止脚本）
> 方法：逐文件实读源码 + 交叉验证（grep 复验关键结论）。本文聚焦"用户体验"，但凡后端稳定性/安全缺陷**直接影响体验**的，一并纳入。
> 前置文档：`竞品与UX对标分析.md（第三部分）`（2026-08-14，基于 Tauri）、`WebUI迁移方案.md`。本文为**当前（Electron 壳）真实代码态**的复核与更新。

---

#### 0. 架构现状（与旧文档的差异）

| 项 | 旧文档所述 | 当前真实态 |
|---|---|---|
| 桌面壳 | Tauri 2（Rust + WebView2） | **Electron**（`dev-shell/main.js`，复用同一套前端/后端，仅外壳不同） |
| 前端 | 已交付三栏 Web UI（P3 完成） | 同左，`webui/src` 19 个源文件，三栏布局 + 报告预览 + 原生对话框封装 |
| 后端 | `server` 子命令端到端 | 同左，差异引擎/反编译/AI/报告齐备 |
| 关键发现 | — | **三处高价值 UX 特性已写好却未接线（死代码）**；**后端单线程同步阻塞**；**忽略规则后端已支持、前端未暴露** |

> 结论先行：架构合理、无需重构；当前最大问题不是"缺功能"，而是"**已写好的能力没接上** + "**后端长任务无异步/无进度**"。二者叠加，是本轮最该优先处理的两类。

---

#### 1. 代码逻辑审查

##### 1.1 后端（java_core）— 稳健项

- ✅ **错误处理友好**：异常被 `catch` 后 `LOG.warning` + 返回 500 中文消息（`BempServer.java:236-239 / 355-358 / 373-376 / 423-426`），不吞异常、前端可提示，无白屏。
- ✅ **反编译三级降级且可见**：CFR 进程内 → CFR 子进程 → javap，降级源码以 `// [降级] javap ...` 标记，`engine` 字段置 `javap` 并回传前端（`Decompiler.java:118/154/177/233`、`BempServer.java:414-422`），用户能区分质量。
- ✅ **忽略规则已实现**：`DiffRules`（忽略空白/注释/正则，`DiffRules.java:14-73`）经 `CompareOptions.toDiffRules()` 透传到比对与前端文本 diff，**能力已具备**。

##### 1.2 后端 — 缺陷（直接影响体验/稳定性）

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| B-1 | **单线程调度**：`HttpServer.create(...)` 未 `setExecutor` | `BempServer.java:67` | 默认单线程串行处理所有请求；报告/导出/反编译都是重活，**互相排队且阻塞整个服务**，大包比对期间 UI 完全卡死 |
| B-2 | **全同步、无进度/无取消**：`Job.status` 恒为 `"DONE"`（`Job.java:16`），比对在请求内完成 | `BempServer.java:187-240` | 大包/AI 分析干等、易 HTTP 超时；用户看不到进度、无法取消 |
| B-3 | **子进程无超时**：CFR/javap 子进程未设超时 | `Decompiler.java:154/180` | 若 CFR 卡死，请求永久挂起 |
| B-4 | **JobStore 永不清理**：`ConcurrentHashMap` 无上限/过期 | `JobStore.java:9` | 每个 Job 持有完整 `PackageSnapshot`（含全量 sha），长运行服务**内存泄漏** |
| B-5 | **大文件 OOM 风险**：`int[n+1][m+1]` 二维数组 | `LineDiff.java:47`、`Decompiler.java:297` | 超大文件 O(n·m) 内存；`handleStatic` 整文件读内存（`BempServer.java:501`） |
| B-6 | **路径穿越**：`webroot.resolve(path.substring(1))` 无规范化/越界校验 | `BempServer.java:499` | 潜在目录穿越点（虽 webroot 常为空，仍应堵） |
| B-7 | **CORS `*`** | `BempServer.java:100-104` | 仅绑 127.0.0.1 但开放任意源，存在 CSRF 面 |
| B-8 | **AI 风险未结构化暴露**：`/report` 仅返回 `text/markdown`，`overallRisk/FileRisk.risk` 字段未给前端 JSON | `BempServer.java:354`、`MarkdownReport.java:115/126/136` | 前端无法据数据着色，只能解析 Markdown |
| B-9 | **无单文件 HTML 报告导出**：`MarkdownReport` 仅生成 `.md`，导出仅 zip 源码 | `MarkdownReport.java:384-429` | 审计结论不便跨团队离线传阅 |
| B-10 | **重复 diff 实现**：`LineDiff.unified` 与 `Decompiler.simpleDiff` 两份并行 LCS | `LineDiff.java:39`、`Decompiler.java:290` | 维护隐患、易漂移 |
| B-11 | **硬编码业务默认值**：`internalPrefixes` 默认 `"com.hundsun"` | `ServerConfig.java:32`、`CompareOptions.java:10` | 非通用开箱体验（换项目需改配置） |

##### 1.3 前端（webui/src）— 稳健项

- ✅ **反馈机制完备**：全屏 busy 遮罩（`CompareOverlay.vue`）、tab spinner、反编译中/失败/不可反编译/空态、全局 toast（`App.vue`、`store.js` 各动作均 `toast('danger',…)`）。
- ✅ **上/下一处差异跳转已支持**：`DiffView.vue:120-127`（按钮）+ `L78-84`（Ctrl/Alt+↑/↓）+ 计数 `curIdx+1/diffCount` + 高亮动画。
- ✅ **主题明暗已支持**（`TopBar.vue`），错误提示中文化（`api/client.js:17-24`）。
- ✅ **原生文件对话框封装**（`lib/tauri.js`）在 Electron/桌面壳下走原生 `pickPath`、浏览器态优雅退化提示手输。

##### 1.4 前端 — 缺陷与缺口

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| F-1 | **行内词/字符级高亮 = 死代码**：`lib/diff_inline.js` 已写好，**全仓零引用** | ✅ 2026-08-20 已接线（见 QW-1）；另修复根因：旧 `lcsAlign` 只认整行相同，修改行全落 del/add、永远不产 rep，行内高亮形同虚设 → 新增相似度配对 | 改一个变量名即同行左右对照 + 差异部分红/绿高亮 |
| F-2 | **未变区段折叠 = 死代码**：`lib/diff_fold.js` 已写好，未 import、UI 无开关 | 同上 | 大文件一长串上下文，可读性差 |
| F-3 | **AI 风险严重性配色 = 死代码**：`lib/severity.js` 已写好，未 import、无对应 CSS | 同上 | 报告无分级色（对标 japi 红/橙/黄/绿） |
| F-4 | **忽略规则前端未暴露**：后端 `DiffRules` 已支持，前端 `buildOptions` 只发 `expandAll/topK/internalPrefixes/cfrJar`，从不通透 `ignoreWhitespace/ignoreComments/ignoreRegex` | `ToolBar.vue:41-49`、grep 复验零引用 | "忽略不重要差异"（BC 杀手锏）能力已具备却用不上 |
| F-5 | **无分栏↔统一视图切换**：固定双栏 4 列网格 | `DiffView.vue:201-207` | 缺统一视图选项 |
| F-6 | **无跨文件内容搜索**：仅 `DiffTree.vue` 文件名模糊/正则搜索 | `DiffTree.vue` | 审计"找危险调用"刚需未满足 |
| F-7 | **无"最近比对/比对档案"持久化**：`runCompare` 每次清空 `tabs/activeKey/reportMd`，仅内存持有当前 `state.job` | `store.js:100-104` | 重复审计（每周发版比对同应用）无法一键复跑 |
| F-8 | **大文件无虚拟滚动**：`rows` 全量 `v-for` 渲染进单滚动容器 | `DiffView.vue:203` | 数千行反编译类性能隐患 |
| F-9 | **过滤入口行为割裂**：`DiffTree.vue:71` 实时改 `state.config.filterSearch`；`ConfigDialog.vue:185` 改副本需"保存"才生效 | 两处 | 同一配置两套管行为，用户困惑 |
| F-10 | **重新比对无确认**：静默清空已开 tab 与报告 | `store.js` | 误触丢失当前审计上下文 |
| F-11 | **无顶层 error boundary**：渲染异常仍可能白屏 | `App.vue`/`main.js` | 单点故障黑屏 |
| F-12 | **默认路径硬编码** `sample_v1.war/sample_v2.war` | `ToolBar.vue:12-13` | 首次使用略显随意；应空态引导 |
| F-13 | **可访问性弱**：diff 行无 aria、toast 无 `aria-live`、跳转仅快捷键无可视入口提示 | 多处 | 无障碍不达标 |
| F-14 | **`rows` 计算属性每次重算 `split('\n')`** 无 memo | `DiffView.vue:34-56` | 频繁重渲染轻微浪费 |

##### 1.5 桌面壳（dev-shell/main.js）— 审查

整体稳健（自适应窗口、静默 sidecar、F12 DevTools、外部链接走系统浏览器）。缺陷：

| # | 问题 | 位置 | 影响 |
|---|
| E-1 | **`setWindowOpenHandler` 变量 bug**：`new URL(url)` 而参数解构为 `{url: u}`，`url` 未定义 → 抛错 → `allowed=''`，内部新窗口也被 `deny` + 推到外部浏览器 | `main.js:271` | 应用内任何 `window.open` 都被误拒/外抛（SPA 多走客户端路由，暂少触发，但属真实 bug） |
| E-2 | **detached 子进程回收不彻底**：`c.kill('SIGTERM')` 对 Windows detached 的 Java 进程树未必传播 | `main.js:283-289` | 退出可能留孤儿 `java`/`vite` 进程，占端口 18765/5180，下次启动需手动清理 |
| E-3 | **无启动 loading 指示**：后端就绪前窗口显示空白 `#app` | `index.html:2` + `main.js` | 冷启动数秒白屏，无反馈 |
| E-4 | **dev 模式窗口闪切**：先开后端(dist)地址，vite 就绪后再 `loadURL` 切到 5180 | `main.js:317-324` | 开发态偶发一次整页 reload 闪烁 |

---

#### 2. 用户体验优化需求清单（按优先级）

> 成本：L=纯前端｜M=前后端小改｜R=重投入。价值：对审计 UX 的提升。

##### 🟢 快速见效（已写代码 / 后端已具备，仅"接线"或"暴露开关"）

| ID | 需求 | 现状 | 做法 | 成本 |
|---|---|---|---|---|
| QW-1 | 行内词/字符级高亮 | ✅ 2026-08-20 完成：`diff_align.js` 变更块相似行配对成 rep（修改对）→ `diff_inline.js` 字符/词级 LCS 细分（公共前后缀裁剪防碎片）→ `DiffView.vue` 渲染 + 粒度切换（整行/词/字符）+ 高饱和内描边 | 用法：DiffView 工具栏"字符级"图标循环切换粒度 | L |
| QW-2 | 未变区段折叠 | `diff_fold.js` 死代码 | 接线 + 加"折叠未变"开关（保留变更上下文 N 行） | L |
| QW-3 | AI 风险严重性配色 | `severity.js` 死代码 + 缺 CSS | 接线 + 配色 + 报告顶部"总体风险结论"汇总条 | L |
| QW-4 | 忽略不重要差异开关 | 后端 `DiffRules` 已支持，前端未暴露 | `ToolBar`/`ConfigDialog` 加"忽略空白/注释/正则"并下发 `options` | M |

> 这四项几乎零新算法，是把"已存在的资产"接通，**应作为第一批，立竿见影**。

##### 🔴 P0（高价值，应先于任何新功能）

- **P0-1 后端异步化 + 进度反馈**（对应 B-1/B-2）
  - 问题：单线程同步、大包/AI 阻塞、无进度、易超时。
  - 做法：`BempServer` `setExecutor`（固定线程池或虚拟线程）；长任务（比对/反编译/AI/报告）提交后台线程，立即返回 `jobId`；补 `/api/job/:id/status` 轮询或 SSE；`Job.status` 真实推进（PENDING→DECOMPILING→AI→DONE）+ 可取消。
  - 成本：R｜价值：高（解锁大包可用性，是其余一切体验的地基）
- **P0-2 重新比对确认 + 不静默清空**（F-10）：`runCompare` 前弹确认（若有已开 tab/报告）；或改为"新开会话"而非覆盖。成本：L｜价值：中高。
- **P0-3 顶层 error boundary**（F-11）：`App.vue` 包 `onErrorCaptured` 兜底，渲染异常显示错误卡而非白屏。成本：L｜价值：中。

##### 🟡 P1（显著增强）

- **P1-1 分栏↔统一视图切换**（F-5）：复用同一 `rows` 数据切 split/unified。成本：L｜价值：中。
- **P1-2 最近比对 + 比对档案持久化**（F-7）：轻量 JSON 存"最近 N 条"与"命名档案"（路径对+过滤/比较规则），启动页/下拉一键复跑。成本：M｜价值：中高（重复审计刚需）。
- **P1-3 跨文件内容搜索**（F-6）：在已反编译结果上建轻量索引，支持字符串/方法名/注解检索并跳转对应 diff 文件。成本：R｜价值：高（审计差异化）。
- **P1-4 信息右栏增强**（对标 Recaf）：选中 class 展示成员/注解/父类接口，抽取 `@Controller/@RequestMapping` 路由。成本：M｜价值：中。
- **P1-5 单文件 HTML 报告导出**（B-9）：内联 CSS/JS 的可离线 HTML（含严重性着色），便于邮件/归档。成本：M｜价值：中。
- **P1-6 AI 风险结构化 JSON 出口**（B-8）：`/report` 额外返回 `{risk,summary,fileAnalysis[]}`，前端据数据着色（替代解析 Markdown）。成本：M｜价值：中高。

##### 🔵 P2（锦上添花 / 偏离只读审计定位的低优先）

- **P2-1 多套主题**（F-9 延伸）：暗色默认 + 2~3 套可选。成本：L｜价值：低。
- **P2-2 大文件虚拟滚动**（F-8）：>2000 行启用 windowing。成本：M｜价值：中。
- **P2-3 审计注释/书签留痕**：对 diff 文件加备注，随报告导出（对标 Recaf/jar-analyzer）。成本：M｜价值：中。
- **P2-4 拖拽比对**：窗口拖入两文件即比对。成本：L｜价值：低。
- **P2-5 三路合并**：**不建议**（偏离"只读审计"定位，投入大）。

##### 🛡️ 稳定性/安全（非纯 UX，但直接影响可用性，建议随 P0 一并处理）

- **S-1 子进程超时**（B-3）：CFR/javap 加超时，超时返回降级/错误而非永久挂起。
- **S-2 JobStore 清理**（B-4）：LRU/过期上限，防内存泄漏。
- **S-3 大文件 OOM 缓解**（B-5）：改用差分算法或设行数上限 + 流式。
- **S-4 路径穿越封堵**（B-6）：`normalize()` + 越界校验。
- **S-5 CORS 收紧**（B-7）：同源即可，移除 `*`。
- **S-6 桌面壳进程树回收**（E-2）：退出用 `taskkill /T /F` 或进程组杀 Java/vite，避免孤儿占端口。
- **S-7 Electron 链接 bug**（E-1）：`new URL(u)` 修正变量。
- **S-8 通用默认**（B-11）：`internalPrefixes` 改为空或通用占位，引导用户配置。
- **S-9 合并重复 diff 实现**（B-10）：统一 `LineDiff`，移除 `Decompiler.simpleDiff`。

---

#### 3. 建议落地路线

1. **第一批（1~2 天，纯接线，零算法）**：QW-1/2/3/4（行内高亮、折叠、风险着色、忽略规则开关）+ S-7（Electron bug 小修）。这是"把已写好的能力接通"，收益最高、风险最低。
2. **第二批（地基，约 1 周）**：P0-1 后端异步+进度 + S-1/2/3/4/5（稳定性）+ P0-2/3（确认/错误边界）。**没有 P0-1，大包比对仍不可用**；其余 UX 再好也建立在不稳的地基上。
3. **第三批（增强）**：P1-1→P1-2→P1-6→P1-4→P1-5→P1-3（重者殿后）。
4. **第四批（打磨）**：P2-1/2/3/4 + S-6/8/9。

> 与旧《UX对标评估》的差异：旧 P0（行内高亮/忽略差异/跳转折叠/报告着色）中，**跳转/折叠/行内高亮/报告着色的前端代码现已存在只是没接**——故本轮把"接线"单列快速见效档，并把"后端异步+进度"升为真正的技术地基 P0-1（旧文档未充分强调同步阻塞问题）。

---

#### 4. 结论

- **架构无需重构**，关注点分离干净、隐私合规到位、差异化能力（AI 风险/分层 diff/零安装）应继续放大。
- **当前体验最大瓶颈 = 已写能力未接通 + 后端长任务同步阻塞**。前者靠 QW 批快速解决（零新算法），后者靠 P0-1 打地基。
- **差异化护城河**（AI 分析、`--apikey ""` 离线 Mock、分层 diff）应作为 UX 重点放大，而非去追 Recaf/jar-analyzer 的"编辑/调用图"能力。

