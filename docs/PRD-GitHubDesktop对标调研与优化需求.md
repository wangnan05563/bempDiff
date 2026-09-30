# PRD · GitHub PC 端对标调研与 BempDiff 桌面端优化需求

> 任务来源：MTask T01448。信息截止：2026-09-30。对标基线：GitHub Desktop 3.5.x（3.5.0 GA 于 2025-06-24，3.5.3 升级 Electron 38.2.0）。
> 目标产品：BempDiff（war/jar/zip/文件夹差异化对比桌面工具，Electron 壳 + Vue 3 前端 + Java 21 sidecar）。

---

## 1. 文档概述

### 1.1 背景

BempDiff 是面向「生产版本 vs 下发版本」发布前比对场景的桌面工具：自动逐层解包、分层差异树（L0 包级 / L1 内部业务码 / L2 第三方依赖）、CFR 反编译源码级 diff、两阶段 AI 风险分析、Markdown 报告与差异资产导出。

GitHub Desktop 是 GitHub 官方 PC 客户端（Windows / macOS），与本产品同为 **Electron 技术栈**，在「变更列表 → 双栏 diff → 协作交付」的核心工作流上具有直接的可对标性；其多年的交互打磨（过滤、行级暂存、无障碍、AI 提交摘要）与大仓库性能教训（Electron 应用在超大仓库上的公认短板）对本产品均有直接的借鉴价值。

### 1.2 目标

1. 完成 GitHub Desktop 的功能 / 体验 / 技术三维深度调研，事实均有来源可查。
2. 提炼对 BempDiff 桌面端可落地的优化需求，按 P0 / P1 / P2 分级并给出验收标准。
3. 输出可直接用于研发排期与设计评审的实施路线图。

### 1.3 范围

- **覆盖**：BempDiff 桌面 GUI（webui + dev-shell 交互层）的产品体验优化需求。
- **不覆盖**：headless CLI 子命令、Java 核心比对算法（DiffEngine）内部实现、AI 厂商接入协议变更。

### 1.4 读者

研发、设计、产品、测试。

### 1.5 对标版本基线

| 项目 | 基线 |
| --- | --- |
| GitHub Desktop | 3.5.x（3.5.0，2025-06-18 发布；3.5.3，2025-09 升级 Electron v38.2.0）[S1][S2] |
| 内置 Git | 2.47.3（macOS）/ 2.47.3.windows.1（Windows）[S2] |
| 安装包形态 | Windows x64 + **arm64**（exe/msi/nupkg），macOS x64 + arm64 [S1] |
| 参考竞品 | GitKraken、Sourcetree、Fork、Tower、SmartGit、Sublime Merge [S4][S5][S6] |

---

## 2. 竞品分析

### 2.1 GitHub Desktop 产品概况

- **定位**：极简主义的 Git/GitHub 可视化客户端，聚焦「clone → commit → branch → push → PR」主循环，刻意不暴露高级 Git 操作 [S3][S7]。
- **技术栈**：Electron（3.5.3 已升级至 v38.2.0 [S2]），内置 Git，无需单独安装 [S7]；开源免费（MIT）[S4]。
- **平台**：官方仅 Windows / macOS，Linux 无官方版（社区 fork）[S4][S5]。

### 2.2 核心功能矩阵（GitHub Desktop 实测功能清单，均含版本来源）

| 能力 | 说明 | 来源 |
| --- | --- | --- |
| 仓库管理 | 克隆 / 添加本地仓库 / 新建；非 Git 目录可一键初始化 [S7] | [S7] |
| 变更列表过滤 | Changed files 列表支持关键字即时过滤（3.4.19，#20220） | [S1] |
| 行级暂存 | 逐行选择纳入 commit（可视化 staging 的标杆交互） | [S3][S7] |
| 分支管理 | 切换 / 新建 / ahead-behind 状态可视化 | [S3] |
| PR 工作流 | Preview Pull Request 预览变更再创建 PR（仅 GitHub 生态） | [S1][S3] |
| 合并冲突 | 冲突文件列表 + 保留当前 / 保留传入 / 外部编辑器三选一 | [S7] |
| 图片 diff | 图像差异 slider / 分页对比视图（含无障碍改造 #20637） | [S2] |
| AI 提交摘要 | Copilot 一键生成 commit message，3.5.0 GA（#17439），组织可经策略开关 | [S2][S8] |
| 多账号 / 多域 | 3.4.19 支持多企业账号（#20227）；3.5 支持 GitHub.com / GHES / 其他域统一认证 | [S1][S8] |
| 安全提示 | Secret Scanning push protection 拦截时给出友好对话框 + 受控绕过（3.4.21，#20386） | [S2] |
| 无障碍 | 长期持续投入：读屏标签、4.5:1 对比度、焦点顺序、图像 diff 读屏（多个 issue） | [S1][S2] |
| 主题 | 明 / 暗主题，自定义能力有限（不支持插件 / 面板定制） | [S6] |
| 离线 | 本地 commit / 分支操作完全离线可用，仅同步需联网 | [S6] |

### 2.3 与 BempDiff 的功能维度逐项对比

| 维度 | GitHub Desktop | BempDiff 现状 | 借鉴点 |
| --- | --- | --- | --- |
| 任务入口 | 仓库列表 + 最近仓库快速切换 | 单次比对会话（左右入口 + 开始比对） | **比对会话历史列表**（R5） |
| 变更列表 | 变更文件列表 + 关键字过滤（3.4.19+） | 分层差异树（L0/L1/L2），有树过滤入口 | 列表级即时过滤 + 过滤开关记忆（R1） |
| Diff 视图 | split/unified、行级暂存、图片 diff、whitespace 提示 | 双栏 diff（全量/仅差异、unified/split/wrap） | 行级复制/选择、二进制条目占位视图（R2） |
| AI 能力 | Copilot 一键 commit message（GA） | 两阶段 AI 分析 + 成本闸门 + 多任务控制台 | **一键整体摘要**入口（R4），对齐「一键生成」心智 |
| 交付/导出 | PR 创建交付上游 | Markdown 报告 + 差异资产 zip + 下载管理 | 报告模板与 HTML 导出（R11） |
| 配置/账号 | 多企业账号、组织策略（Copilot policy） | 配置中心（BaseURL/API Key 掩码不落盘/连接测试） | 错误友好化与受控降级提示（R8） |
| 更新 | 自动静默更新（nupkg 增量 delta） | NSIS 手动覆盖安装 | 版本检查 + 下载引导（R9） |
| 无障碍 | 持续专项（读屏/对比度/焦点） | 未系统开展 | 对比度与焦点顺序专项（R7） |

### 2.4 体验维度对比

- **布局**：GitHub Desktop 为「仓库/分支侧栏 + 变更列表 + diff 主区」三区结构；BempDiff 为「差异树 + 双栏 diff + AI 控制台」，结构同源，信息密度更高。
- **操作效率**：GitHub Desktop 的右键上下文菜单保持选中项（3.4.19 #20319）、Ctrl+A 语义修正（#20049）等细节体现「列表操作不打断」原则；BempDiff 已有拖拽即比、路径导航历史，列表右键与快捷键体系尚不完整。
- **响应速度**：GitHub Desktop 在超大仓库（数 GB .git、数千变更文件）下渲染延迟明显，被多个评测列为首要短板 [S3][S6][S9]；BempDiff 的「数万条目包」场景与之同构，必须前置防御（R3）。
- **可访问性**：GitHub Desktop 在 2025 年的多个版本中持续修复读屏与对比度问题（如 #20902 对比度 4.5:1、#20804 列表压缩提示）[S2]，可作为 BempDiff 无障碍工作的操作清单样本。
- **多标签/多窗口**：GitHub Desktop 单仓库单窗口；Fork 以「仓库管理器 + 快速切换」见长 [S6]——对应 BempDiff 的多比对会话并行需求（R5）。

### 2.5 技术维度对比

| 维度 | GitHub Desktop | BempDiff | 结论 |
| --- | --- | --- | --- |
| 壳技术 | Electron（v38） | Electron | 同源；内存占用大是共性风险，需进程内治理 |
| 引擎 | 内置 Git 2.47 | Java 21 sidecar（自研 DiffEngine + CFR/javap） | BempDiff 引擎离线自足，无外部依赖，是优势 |
| 搜索 | 仓库内 Find 对话框（#20049 修复 Ctrl+A） | 差异树过滤 | diff 内容内搜索为缺口（R2 带出） |
| 离线支持 | 本地操作离线可用，同步需联网 | **全离线可用**（AI 为可选增强，不可达时 Mock 降级） | 保持优势，AI 降级路径不可回退 |
| 更新通道 | 正式版 + beta 双通道（desktop.githubusercontent.com releases） | 仅正式安装包 | 参考 beta 通道做法（P2 观察项） |

### 2.6 GitHub Desktop 优势与不足总结

**优势**（可借鉴）：上手门槛极低、GitHub 生态无缝、行级暂存标杆交互、PR 工作流、无障碍长期投入、开源免费 [S3][S4][S6]。

**不足**（引以为戒，均有来源）：

1. 超大仓库性能差（GUI 渲染延迟、分支切换慢）[S3][S6][S9]。
2. 高级 Git 操作缺失：interactive rebase、cherry-pick、submodule、worktree 均未暴露 [S7]。
3. PR 集成仅限 GitHub 生态，GitLab/Bitbucket 体验不完整 [S7]。
4. 多账号长期需登出切换，3.4.19 起多企业账号才改善 [S1][S7]。
5. Electron 内存与磁盘占用高于原生客户端（Fork/Sublime Merge 为反例）[S6][S7]。
6. 冲突解决界面过于简化，可能掩盖语义合并风险 [S9]。
7. 自定义能力有限（仅明暗主题）[S6]。
8. 无官方 Linux 版 [S4][S5]。

### 2.7 其他竞品速览（供差异化定位参考）[S4][S5][S6]

| 工具 | 价格 | 平台 | 一句话定位 | 对 BempDiff 的启示 |
| --- | --- | --- | --- | --- |
| GitKraken | 免费(公共仓)/$4.95+/mo | Win/Mac/Linux | 可视化 commit graph + 冲突编辑器最强 | 图形化历史/关系视图的呈现方式 |
| Sourcetree | 免费 | Win/Mac | Atlassian 生态免费全能，UI 陈旧、大仓卡顿 | 免费 + 全功能仍有市场；UI 迭代不能停 |
| Fork | $50 买断 | Win/Mac | 原生性能、速度快 | 「快」本身是核心卖点 |
| Tower | $69/年 | Win/Mac | 专业打磨、Undo 安全网、冲突顾问 | 「可撤销」的安全感设计 |
| Sublime Merge | $99 买断 | Win/Mac/Linux | 大仓库不卡 | 性能即口碑 |
| SmartGit | $89-139/用户 | 三平台 | 跨平台一致性 | Java 技术栈跨平台可行性参照 |

**差异化结论**：BempDiff 不做 Git 客户端，而做「构建包差异审计」——对标的是上述工具在 *diff 呈现、过滤检索、性能、安全感（可撤销/可预估）* 上的共性标准，而非 Git 操作覆盖面。

---

## 3. 用户画像与使用场景

| 画像 | 场景 | 关键诉求 |
| --- | --- | --- |
| 发布/运维工程师 | 上线前比对生产包与下发包，确认变更范围 | 快速过滤出关注模块；一键整体摘要向上汇报 |
| QA/测试负责人 | 依据差异定回归测试范围 | 风险分级着色、报告导出、差异资产导出 |
| 安全审计人员 | 审计反编译源码级改动 | 源码 diff 精确、可搜索、脱敏可控 |
| 研发负责人 | 评审 AI 风险分析结论 | 多任务并行、流式输出、成本可控 |

共性诉求（与竞品结论互证）：**找得快（过滤/搜索）、看得清（diff 呈现）、等得起（性能可预期）、说得明（AI 摘要 + 报告）**。

---

## 4. 功能需求详述

> 优先级定义：P0 核心需求（下一版本必须）、P1 重要需求（1-2 个版本内）、P2 增强需求（排期弹性）。每条含用户价值与验收标准（可测试）。

### P0 核心需求

#### R1 变更列表即时过滤（对标 GitHub Desktop 3.4.19 Filter changes [S1]）

- **描述**：差异树/文件列表顶部提供过滤输入框，支持按路径片段、变更类别（新增/修改/删除）、风险等级（AI 打标结果）组合过滤；View 菜单可开关过滤栏并记忆状态 [S1]。
- **用户价值**：数万条目中快速定位关注文件，缩短审计定位时间。
- **验收标准**：
  1. 输入关键字后 ≤200ms 完成列表过滤（1 万条目样本实测）；
  2. 支持清空一键还原；过滤状态在切换文件/展开折叠后保持；
  3. 过滤栏可通过菜单关闭，重启后记忆上一次开关状态；
  4. 过滤命中数实时显示（如「128 / 9,432」）。

#### R2 Diff 视图增强（对标行级交互与图片 diff 占位 [S3][S2]）

- **描述**：① diff 内容支持行级选中与复制（含行号开关）；② 增加差异内容内查找（Find）对话框，Ctrl+A 语义为全选查找结果而非破坏 diff 选择（规避 GitHub Desktop #20049 同类问题）；③ 二进制/不可文本化条目提供结构化占位视图（大小/哈希/类型变化摘要），替代空白。
- **用户价值**：审计人员可直接摘录证据行；二进制变更不再「黑盒」。
- **验收标准**：
  1. 任意 diff 行可拖选复制，复制内容含可配置的行号前缀；
  2. Find 支持「下一个/上一个/高亮全部」，万行文件查找 ≤300ms；
  3. 二进制条目展示摘要卡（类型、双端大小、SHA-256 是否变化）；
  4. vitest 覆盖过滤与复制逻辑核心用例。

#### R3 大包性能防御（吸取 GitHub Desktop 大仓教训 [S3][S6][S9]）

- **描述**：差异树虚拟滚动渲染；解包/比对阶段分阶段进度（已有基础）细化到「解包 → 比对 → 反编译」三段百分比；首次进入仅渲染可视区。
- **用户价值**：超大包（数万条目）不出现界面假死，等待可预期。
- **验收标准**：
  1. 5 万条目差异树首屏渲染 ≤3s；滚动帧率 ≥55fps（Performance 面板实测）；
  2. 内存驻留不超过既有 256MB 软上限（回归 MemoryReleaseTest 3 用例）；
  3. 阶段进度三段百分比与实际阶段一致（手动核对 3 组样本）。

#### R4 一键整体摘要（对标 Copilot 一键 commit message [S2][S8]）

- **描述**：在智能分析入口增加「一键摘要」：单次调用生成整体变更概览（变更规模、主要模块、风险等级分布），流式输出、可一键复制进报告。
- **用户价值**：对齐「一键生成」的低门槛心智；向非技术干系人汇报的最小可用产物。
- **验收标准**：
  1. 无 Key/不可达时优雅降级提示（不中断比对主流程，走既有 Mock 路径）；
  2. 发起前展示成本预估（复用既有成本闸门），超阈值需确认；
  3. 摘要输出流式渲染，完成后可一键复制为 Markdown。

### P1 重要需求

#### R5 比对会话历史与快速切换（对标多仓库管理 [S6]）

- **描述**：记录近期比对会话（左右路径、时间、差异统计缩略），支持一键恢复查看与删除记录。
- **验收标准**：历史列表 ≥20 条滚动存储；恢复会话后差异树与选中文件还原；删除有确认；不含 AI 分析结果落盘（延续脱敏与 Key 不落盘约束）。

#### R6 键盘快捷键体系与帮助面板

- **描述**：补齐核心操作快捷键（过滤定位、树导航、diff 视图切换、开始比对），`?` 或菜单唤出快捷键速查面板。
- **验收标准**：全部快捷键可在面板查看且实际生效；与输入框编辑快捷键不冲突（焦点感知）。

#### R7 无障碍与对比度专项（对标 GitHub Desktop 持续投入 [S1][S2]）

- **描述**：全界面文本对比度 ≥4.5:1；列表/树项具备可读屏的名称与状态；焦点顺序符合视觉顺序。
- **验收标准**：以 GitHub Desktop 2025 年修复清单（#20902、#20804、#20621 等）为检查单逐项核对；axe 或等价工具扫描 0 critical。

#### R8 配置与错误友好化（对标 Secret Scanning 友好对话框 [S2]）

- **描述**：AI 连接测试失败时给出结构化原因（网络/鉴权/模型名）与建议动作；破坏性操作（清空历史、覆盖导出）使用统一确认对话框并说明后果与绕过条件。
- **验收标准**：三类失败原因可区分并给出对应建议；确认对话框文案包含后果与默认安全选项。

#### R9 版本检查与更新引导（对标自动更新 [S2]）

- **描述**：启动后异步检查新版本（可配置关闭），发现新版在界面角落提示并跳转下载页；不自动安装。
- **验收标准**：检查失败静默（不留错误弹窗）；提示可关闭且当日不再重复；关闭开关持久化。

### P2 增强需求

#### R10 主题完善：在明暗主题之上支持强调色自定义（GitHub Desktop 仅明暗 [S6]，做出差异）。
#### R11 报告导出增强：HTML 版报告与自定义报告模板（标题/落款/风险口径）。
#### R12 i18n：界面文案中英双语，术语首次出现附英文对照（与本 PRD 术语规范一致）。

---

## 5. 非功能性需求

| 类别 | 需求 | 指标/标准 |
| --- | --- | --- |
| 性能 | 大包比对全链路可预期 | 5 万条目首屏 ≤3s；过滤 ≤200ms；内存 ≤256MB 软上限 |
| 兼容性 | Windows 10/11 x64（NSIS）；评估 arm64 产物 | GitHub Desktop 已提供 x64+arm64 双通道 [S1]，作为形态参照 |
| 安全性 | API Key 默认不落盘；金融合规脱敏；AI 降级 | 沿用既有约束；安装包评估代码签名（消除 SmartScreen 告警） |
| 可访问性 | 对比度 ≥4.5:1、读屏可用 | 见 R7 |
| 可靠性 | AI 不可达优雅降级；导出任务异步可恢复 | Mock 降级路径回归通过；下载管理断点行为不回退 |
| 可维护性 | 前端回归三件套全绿 | vitest 216+ 用例、report_button 9 用例、vite build 通过 |

---

## 6. 实施路线图

| 阶段 | 内容 | 出口标准（里程碑） |
| --- | --- | --- |
| M1（P0，约 4-6 周） | R1 过滤、R2 Diff 增强、R3 性能防御、R4 一键摘要 | 四项验收全部实测通过；回归三件套 + MemoryReleaseTest 全绿 |
| M2（P1，约 4 周） | R5 会话历史、R6 快捷键、R7 无障碍、R8 错误友好化、R9 更新引导 | 逐项验收通过；axe 扫描 0 critical |
| M3（P2，弹性） | R10 主题、R11 报告增强、R12 i18n、arm64 评估 | 按单项独立排期与验收 |

依赖说明：R4 依赖既有两阶段 AI 管线与成本闸门（已完成）；R7 与 R10 有样式层耦合，建议 M2 先行；R3 与既有解包/内存治理（LruMap、sweepSupersededJobs）直接衔接。

---

## 7. 附录

### 7.1 参考资料

- [S1] GitHub Desktop Releases（3.4.19/3.4.21/3.5.3 changelog）：https://desktop.githubusercontent.com
- [S2] GitHub Desktop 3.4.x changelog 汇总（Softpedia）：https://mac.softpedia.com/progChangelog/GitHub-Changelog-100845.html
- [S3] GitHub Desktop 官方博客 · 3.5 GA（Copilot commit message / 多域认证 / 过滤）：https://github.blog/changelog/2025-06-24-github-desktop-3-5-github-copilot-commit-message-generation-now-generally-available
- [S4] Best Git GUI Clients in 2025（dev.to，GitKraken/SourceTree/Fork/GitHub Desktop/Tower/SmartGit 对比与定价）：https://dev.to/_d7eb1c1703182e3ce1782/best-git-gui-clients-in-2025-gitkraken-sourcetree-fork-and-more-compared-4gjd
- [S5] Top 10 Git Clients（ScmGalaxy，评分与选型建议）：https://www.scmgalaxy.com/tutorials?p=16700
- [S6] Git Visual Tools 四工具功能对比（procopan 博客）：https://blog.procopan.md/2025/04/07/Git-visual-tools.html
- [S7] GitHub Desktop 功能与局限评述（techbloat，含高级操作缺失/资源占用/大仓性能）：https://www.techbloat.com/github-desktop-electron-based-github-app.html
- [S8] GitHub Desktop 评测（The CTO Club，多账号/主题/企业适用性）：https://thectoclub.com/tools/github-desktop-review
- [S9] GitHub Desktop 极简主义评析（TrueSight，大仓性能/冲突解决简化风险）：https://tsight.io/articles/13486869
- [S10] GitHub Desktop 官方文档：https://docs.github.com/en/desktop

> 注：第三方定价信息随厂商调整变动频繁，引用时以官方页面为准 [S4][S5]。

### 7.2 术语表

| 术语 | 英文 | 释义 |
| --- | --- | --- |
| 差异树 | Diff Tree | BempDiff 按目录层级呈现两包差异的树形视图 |
| 分层差异 | Layered Diff | L0 包级清单 / L1 内部业务码 / L2 第三方依赖的三层组织口径 |
| 反编译 | Decompile | 将 class 字节码还原为近似源码（CFR 为主、javap 降级） |
| 统一/分栏视图 | Unified / Split View | diff 的单栏合并 / 双栏对照两种呈现 |
| 行级暂存 | Line-level Staging | 按行选择纳入提交的交互（GitHub Desktop 标杆能力） |
| 逐字差异 | Word-level Diff（word diff） | 行内更细粒度的差异高亮 |
| 无障碍 | Accessibility (a11y) | 读屏兼容、对比度、焦点管理等可用性能力 |
| Sidecar | Sidecar Process | 随主程序生命周期启动/退出的子进程（此处指 Java 比对引擎） |
| 成本闸门 | Cost Gate | AI 调用前按预估 token 费用拦截/确认的机制 |
| 推送保护 | Push Protection | GitHub 对疑似密钥推送的拦截机制 [S2] |
