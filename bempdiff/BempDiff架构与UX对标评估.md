# BempDiff 架构与 UX 对标评估报告

> 评估对象：`bempdiff/`（Tauri 2 桌面壳 + 内嵌 jlinked JRE + `java_core` 纯 Java CLI + Vue3/Bootstrap Web UI + AI 风险分析）
> 对标基准：Recaf、jar-analyzer、japi-compliance-checker / revapi、Beyond Compare（既定 UX 基线），以及现代 diff 视图（GitHub / react-diff-viewer / JetBrains Diff Viewer）的最佳实践
> 评估维度：整体架构合理性 + 用户体验（UX）短板与可提升空间
> 日期：2026-08-14

---

## 1. 评估范围与方法

- **架构复核**：实读 `src-tauri/src/lib.rs`、`tauri.conf.json`、`webui/src/**`、`java_core/src/com/bempdiff/**` 确认当前真实状态。
- **竞品调研**：检索 GitHub 上同类开源/商业产品与其公开的 UX 能力，提取可借鉴项。
- **差距分析**：以"审计人员比对两个 WAR/JAR 版本、判断改了什么/是否有风险"为主线任务，逐项对标。

---

## 2. 当前架构概览与评价

### 2.1 架构（确认态）

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

### 2.2 架构优势（值得保留）

| 优势 | 说明 |
|---|---|
| **零安装分发** | 内嵌 jlinked 瘦 JRE（48MB）+ bempdiff.jar 随包分发，双击 EXE 即用，无需本机 Java——优于 Recaf / jar-analyzer（需 JRE，尽管后者也带打包 JRE） |
| **关注点分离干净** | Rust 只做进程管理 + 原生窗口 + 原生对话框；业务/反编译全在 `java_core`，前端纯展示。无 JavaFX 依赖，可维护性好 |
| **隐私友好** | 原生对话框直接拿绝对路径，**不上传**；AI 仅发脱敏 diff 摘要、源码不出机（合规到位） |
| **分层差异** | L0/L1/L2 分层比"扁平文件树"更利于审计聚焦（业务码 vs 三方依赖） |
| **差异化能力** | 两阶段 AI 风险/影响评估 + 测试点生成是 Recaf/jar-analyzer/BC 都不具备的独有价值 |
| **前端文本比对** | JS/HTML/CSS 美化 + 同构 DecompiledUnit，竞品普遍只做 Java |

### 2.3 架构风险（仅提示，非 UX 主线）

- **随机端口 + 每次启动新 JRE 进程**：多开实例会各自启 Java 服务（资源翻倍），无单实例约束；目前可接受。
- **dev/prod 路径分叉**：`tauri dev` 不拉 JRE，需手动起 `server --port 18765`——本机联调有心智负担（已在 `本机实测清单.md` 标注）。
- **前端全量渲染 diff 行**：`DiffView.vue` 用单个 grid 渲染整文件所有行，超大反编译类（数千行）可能卡顿，缺虚拟滚动。
- **比对为只读**：与 Recaf/jar-analyzer 的"可编辑/可导出反编译源码"定位不同，BempDiff 定位是"审计比对"，因此 3-way merge 等编辑能力优先级低（见 §5 P2）。

---

## 3. 对标对象与各自 UX 标杆能力

| 产品 | 类型 | 标杆 UX 能力（摘取） |
|---|---|---|
| **Beyond Compare** (基线) | 商业文件/文件夹比对 | 三路合并、忽略不重要差异（空白/注释/正则）、会话保存复用(.bcf)、文件夹同步预览、按规则过滤(node_modules/构建产物)、字符级高亮 |
| **Recaf** | 开源 Java 字节码编辑/反编译 | 深色多面板布局、类树 + 多标签代码区 + 右侧成员/继承关系面板、语法高亮、全局搜索、**注释/书签注入反编译结果**、附加到运行进程 |
| **jar-analyzer** (~2k★) | 开源 JAR 分析/审计 | 方法调用图(DFS/污点)、字符串/全局搜索(Lucene)、Spring 路由分析、10 套 UI 主题、前进/后退导航、备忘录/书签、黑白名单过滤、实时 CPU/内存图 |
| **japi-compliance-checker / revapi** | 开源 API 兼容性 | **HTML 报告按严重性红/橙/黄/绿着色**、可折叠分区、兼容性结论徽章、问题分类(方法级/类型级)、签名语法高亮 |
| **GitHub / react-diff-viewer / JetBrains Diff** | diff 视图事实标准 | 分栏(split) ↔ 统一(unified) 切换、**词/字符级行内高亮**、忽略空白、折叠未变区域、上一处/下一处差异跳转(Nav)、同步滚动 |

---

## 4. BempDiff 现有能力 × 标杆 能力矩阵

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

## 5. UX 提升机会（按优先级）

> 标注：位置 = 主要改动文件；成本 = 前端为主(L) / 前后端(M) / 重(R)；价值 = 对审计 UX 的提升。

### P0（高价值、低成本，直接强化审计核心体验）

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

### P1（显著增强，中等投入）

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

### P2（锦上添花 / 偏离只读审计定位）

- **P2-1 多主题**：暗色为默认 + 2~3 套可选（对标 jar-analyzer 10 套）；当前 Bootstrap `data-bs-theme` 已支持明暗，扩展成本低。
- **P2-2 大文件虚拟滚动**：`DiffView` 对 >2000 行启用虚拟列表（对标 react-diff-viewer 虚拟滚动）；避免卡顿。
- **P2-3 差异文件注释/书签留痕**：对某个 diff 文件加审计备注（"此变更因 X 需求预期内"），随报告导出（对标 Recaf 注释注入 / jar-analyzer 备忘录）。
- **P2-4 拖拽比对 / 文件管理器右键**：窗口支持拖入两个文件即比对；低优先级。
- **P2-5 三路合并(3-way merge)**：BCompare 杀手锏，但偏离 BempDiff"只读审计"定位，**不建议**做（投入大、与产品定位不符）。

---

## 6. 架构层改进建议（支撑上述 UX）

| 建议 | 说明 | 关联 UX |
|---|---|---|
| 比对结果索引化 | 反编译完成后建字符串/符号倒排索引，支撑 P1-3 跨文件搜索 | P1-3 |
| diff 计算支持"规则参数" | `DiffEngine`/`LineDiff` 接收忽略空白/注释/正则规则，供 P0-2 复用 | P0-2 |
| 报告模型携带 risk 字段 | `MarkdownReport` 产出的结构同时给前端着色用，避免前端再解析 markdown | P0-4 |
| 前端 diff 虚拟化 | 大文件按需渲染，避免整文件 grid | P2-2 |
| 历史/档案持久化 | 轻量 JSON 存最近比对与档案，随包 `resources` 或用户目录 | P1-2 |

---

## 7. 建议落地路线（与现有 P0–P5 节奏一致）

1. **第一轮（P0，建议作为 P6）**：P0-1 行内高亮 + P0-2 忽略不重要差异 + P0-3 跳转/折叠 + P0-4 报告着色。四项均为"审计体验立竿见影"项，且 P0-1/3 纯前端、P0-2/4 前后端小改。
2. **第二轮（P1）**：P1-1 视图切换 → P1-2 会话保存 → P1-4 右栏增强 → P1-5 HTML 报告 → P1-3 跨文件搜索（重者殿后）。
3. **第三轮（P2）**：主题、虚拟滚动、注释留痕。

---

## 8. 结论

- **架构层面**：BempDiff 当前架构（Tauri 壳 + 内嵌 JRE + 纯 Java CLI + Web UI）**合理且领先于多数同类桌面工具**（零安装、隐私好、关注点分离干净），无需重构；仅需为 UX 增强补少量索引/规则/持久化能力。
- **UX 最大短板**集中在"差异可读性"与"审计降噪"：① 缺行内词级高亮；② 缺"忽略不重要差异"；③ 缺大文件跳转/折叠；④ AI 报告缺严重性着色。这四项（P0）直接对标 Beyond Compare / JetBrains / japi，且成本低、收益高，应优先于任何新功能。
- **差异化护城河**（AI 风险/影响分析、分层 diff、零安装）应作为 UX 重点放大，而非去追 Recaf/jar-analyzer 的"编辑/调用图"能力——后者投入大且与"只读审计"定位不符。
