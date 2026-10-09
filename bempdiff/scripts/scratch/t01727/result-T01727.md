# T01727 调研报告：GitHub 同款产品梳理与 BempDiff 优化需求全景

> 调研时间：2026-10-10。数据来源：GitHub API 实时查询（star 数 / 最近推送均为当日实测）、Beyond Compare 5.2（2026-05 发布）官方资料。
> 对象：BempDiff（war/jar/zip/文件夹差异化对比 + AI 分析桌面工具）。

## 一、同款/近邻产品矩阵

### 1.1 桌面 GUI diff 工具（直接竞品）

| 产品 | 平台/形态 | 热度 | 核心能力 | 可借鉴点 |
|---|---|---|---|---|
| **WinMerge** | Windows 开源 | ★9,248（活跃） | 文件夹/文本/图片对比、3 路合并、插件体系、归档直比 | 图片对比插件化、归档直比、7 脚本 |
| **Meld** | Linux/macOS（GTK） | ★1,318（GitLab 主仓，GitHub 只读镜像） | 文件夹对比、VCS 集成、2/3 路合并 | VCS 工具链嵌入习惯 |
| **Beyond Compare 5.2** | 跨平台商业（$70） | 行业标杆（百万级用户） | 文本/表格/图片/十六进制/注册表/MP3/版本信息七类专用查看器、3 路文件+文件夹合并、FTP/SFTP/S3/WebDAV 虚拟文件系统、脚本自动化、会话管理 | 专用查看器矩阵、Session 概念、脚本自动化、免解压直比压缩包 |

### 1.2 Java 归档/API diff（细分赛道直接同款）

| 项目 | 热度 | 定位 | 可借鉴点 |
|---|---|---|---|
| **java-diff-utils** | ★1,549 | Java diff 算法库 | 行内字符级算法参考 |
| **japicmp** | ★781 | 两个 jar 版本对比，**API 兼容性**语义分析 | 新增/移除/签名变更/二进制不兼容**分级标注**（MAJOR/MINOR/PATCH） |
| **japi-compliance-checker** | ★380 | Java 库向后兼容性检查 | 兼容性报告格式 |
| **revapi** | ★223 | API 变更跟踪框架（扩展插件制） | 插件化 diff 管线 |
| **scala-garden/jardiff** | ★191 | **反编译后**对比 jar（含方法体） | 与 BempDiff CFR 路线同源，验证反编译对比是主流做法 |
| **pkgdiff** | ★258 | Linux 软件包变更可视化 | 包级 diff 报告呈现 |

### 1.3 语法感知 diff（新一代方向，热度最高）

| 项目 | 热度 | 要点 |
|---|---|---|
| **difftastic** | ★25,992 | 基于 tree-sitter 的**结构化 diff**：按 AST 对齐而非行对齐，格式化/重排不产生噪声 |
| **delta** | ★32,464 | 语法高亮 diff pager、side-by-side、行内高亮、gutter 快捷操作 |
| **tree-sitter** | ★27,145 | 增量解析底座，Java grammar 现成 |

### 1.4 Web diff UI 组件（前端实现参照）

| 项目 | 热度 | 要点 |
|---|---|---|
| **monaco-editor** | ★46,853 | VS Code 同源：diff 编辑器、gutter 折叠未变更区、字符级高亮、主题系统 |
| **shiki** | ★13,858 | 语法高亮：可直接复用 VS Code TextMate 主题（github-dark/one-dark 等）做主题包 |
| **diff2html** | ★3,417 | pretty diff：字符级行内高亮、折叠、文件导航栏 |
| **react-diff-view** | ★1,016 | git diff 组件：gutter 标记、评注位、选区 |
| **CodeMirror 6** | ★7,813 | 轻量替代 monaco 的候选 |

### 1.5 AI 分析参照

| 项目 | 热度 | 要点 |
|---|---|---|
| **qodo-ai/pr-agent** | ★13,322 | 开源 AI 代码审查：影响分析、置信度、增量评论 |
| jadx（★50,793）/ CFR（★2,713） | — | 反编译链（CFR 已在用；jadx 支持更多格式） |

## 二、优化需求梳理

### A. 客户体验提升

| # | 需求 | 依据 | 优先级 |
|---|---|---|---|
| A1 | **修复「已是最新仍显示立即更新」缺陷**（About.vue L185 未判 `result.upToDate`，点击会降级安装） | 本项目已立派生单 T01726 | **P0** |
| A2 | 更新链路完善：更新日志展示、增量/静默更新选项 | BC/WinMerge 均带 changelog 呈现 | P1 |
| A3 | 命令行/CI 模式 + 脚本自动化（`bempdiff-cli --left a --right b --report out.html`） | BC script processor、japicmp 全靠 CLI/CI 场景立足；桌面 diff 工具进入流水线是明确趋势 | **P1** |
| A4 | 对比会话保存/最近记录/命名任务（Session 概念） | BC Session 体系 | P1 |
| A5 | 错误信息可读化 + 一键导出脱敏诊断包 | 现场排障体验 | P1 |
| A6 | 大包感知性能：可取消进度、树懒加载、比对中可浏览已完成部分 | big500 SLA 已达标，感知层补齐 | P2 |
| A7 | Windows 11 右键菜单/「发送到」快捷比对入口 | BC v5 专属特性，桌面工具获客入口 | P2 |
| A8 | 安装体积优化：JRE jlink 模块再裁剪、Electron 资源压缩（当前 NSIS 包 113–195MB） | BC 仅 60MB 磁盘占用 | P2 |

### B. 功能扩展

| # | 需求 | 依据 | 优先级 |
|---|---|---|---|
| B1 | **语法感知 diff（AST 对齐）**：tree-sitter Java，消除格式化/重排/导入顺序噪声 | difftastic ★26k 验证了需求强度；对「反编译代码对比」场景噪声消除价值极大 | **P1（高价值）** |
| B2 | **API 兼容性语义分级**：新增/移除/签名变更/二进制不兼容（借鉴 japicmp），叠加到 L2 层 | japicmp 赛道验证；BempDiff 已有反编译+分层，是天然差异化 | **P1（差异化）** |
| B3 | 用户可配置忽略规则集（正则/路径/键模式），支持导入导出；现有构建元数据噪声归一化升级为规则体系 | BC 智能忽略规则 | P1 |
| B4 | HTML/Markdown/JSON 报告导出（可附 AI 结论），供评审归档 | japicmp/pkgdiff 均以报告为核心输出 | P1 |
| B5 | 归档格式扩展：RAR/7z/ISO/磁盘镜像免解压直比 | BC 免解压直比 ZIP/RAR/7z/TAR/ISO | P2 |
| B6 | 远程对比源：Maven Central 直接拉 artifact 对比、FTP/SFTP/S3 | BC VFS；Maven 场景与目标用户重合 | P2 |
| B7 | 依赖树/POM diff（Maven/Gradle 依赖变更可视化） | jar 对比用户的下游诉求 | P2 |
| B8 | 3 路合并（文件级先行）：共同祖先 + 双版本 → 输出窗格 | BC Pro 核心卖点；只读对比向生产合并演进 | P2（远期） |
| B9 | 专用查看器矩阵：十六进制/图片/表格（CSV/Excel）对比 | BC 七类查看器 | P2（按需取舍） |
| B10 | 反编译格式扩展：Kotlin（jadx 路线）/字节码控制流图 | jadx ★50k 的多语言覆盖 | P2 |

### C. UI 升级

| # | 需求 | 依据 | 优先级 |
|---|---|---|---|
| C1 | **字符级行内高亮**（word-diff）：同一条变更行内再细分改动片段 | diff2html/react-diff-view/delta 标配 | **P0/P1** |
| C2 | **折叠未变更区**（gutter 折叠条 + 「展开 N 行」），大文件导航效率核心 | monaco/diff2html 标配 | **P0/P1** |
| C3 | 目录树筛选增强：只看差异/按类型/正则过滤/列排序/状态图例 | BC 文件夹过滤器 | P1 |
| C4 | gutter 快捷操作：复制到对侧、标记已审阅、快捷键流（j/k/n/p） | delta/BC gutter 按钮 + 热键 | P1 |
| C5 | 跨文件搜索 + 差异计数导航增强（在 T00876 下一处差异基础上加文件间跳转/总览条） | 已有基础增强 | P2 |
| C6 | 虚拟滚动大文件（12000 行夹具已备，纳入回归） | difftastic 大仓性能口碑 | P2 |
| C7 | 多显示器 DPI 专项验证（BC v5 为此专门修，Electron 同样有坑） | BC v5 特性清单 | P2 |

### D. 主题升级

| # | 需求 | 依据 | 优先级 |
|---|---|---|---|
| D1 | **亮/暗/跟随系统三态主题**（含差异配色双套） | BC v5 才补齐暗色=市场确认的标配 | **P0** |
| D2 | 语法高亮主题包：接 shiki/TextMate 主题（github-dark、one-dark 等即插即用） | shiki 主题体系 | P1 |
| D3 | 差异配色语义化 + 色盲友好方案（增/删/改/移动/噪声五类可调，WCAG AA 对比度） | delta/BC 无障碍实践 | P1 |
| D4 | 自定义主题 API（CSS 变量 + 主题包加载） | monaco 主题注册模式 | P2 |

### E. AI 能力升级（差异化护城河）

| # | 需求 | 依据 | 优先级 |
|---|---|---|---|
| E1 | **变更影响分析**：结合调用图/包结构给出风险等级与受影响面 | pr-agent 影响分析思路；BempDiff 已有分层与反编译 AST，具备做静态分析的条件 | **P1** |
| E2 | 成本透明化：token/费用预估面板 + 分析预算上限 UI 化（成本闸门已有 → 可视化） | 企业落地信任感 | P1 |
| E3 | 自然语言查询 diff（「列出所有涉及鉴权的变更」） | AI 交互新范式 | P2 |
| E4 | AI 结论置信度分级 + 增量分析（仅分析新增变更，控成本） | pr-agent 增量评论 | P1 |
| E5 | AI 修复建议/迁移提示（针对 API 不兼容项给出升级代码片段） | 与 B2 联动的独特卖点 | P2 |

## 三、优先级总览（建议路线图）

- **P0（修复与标配补齐）**：A1 更新按钮缺陷（T01726）、C1 字符级行内高亮、C2 折叠未变更区、D1 暗色/跟随系统主题
- **P1（竞争力建设）**：A3 CLI/CI 模式、A4 会话管理、A5 诊断包、B1 语法感知 diff、B2 API 兼容性分级、B3 忽略规则体系、B4 报告导出、C3/C4、D2/D3、E1/E2/E4
- **P2（远期演进）**：A6–A8、B5–B10（3 路合并、远程源、专用查看器等）、C5–C7、D4、E3/E5

**差异化主线建议**：BempDiff 相对通用 diff 工具（WinMerge/BC）的不可替代位 = 「Java 归档 + 反编译 + 语义分层 + AI」组合拳。P1 投入优先砸向 B1（语法感知）+ B2（兼容性分级）+ E1（影响分析），形成 difftastic/japicmp/pr-agent 三者能力的垂直整合，这是市场上尚无开源产品覆盖的空位。

## 四、数据附录

GitHub API 实测（2026-10-09/10）：WinMerge/winmerge ★9248；java-diff-utils ★1549；siom79/japicmp ★781；revapi ★223；Wilfred/difftastic ★25992；dandavison/delta ★32464；rtfpessoa/diff2html ★3417；otakustay/react-diff-view ★1016；microsoft/monaco-editor ★46853；skylot/jadx ★50793；leibnitz27/cfr ★2713；qodo-ai/pr-agent ★13322；tree-sitter ★27145；pkgdiff ★258；GNOME/meld ★1318（镜像）；lvc/japi-compliance-checker ★380；scala-garden/jardiff ★191；shikijs/shiki ★13858；codemirror/codemirror.next ★7813。

商业对标：Beyond Compare v5.2.2（2026-05-20 发布）：系统级暗色、文本对比自动换行、表格对比升级、Windows 11 上下文菜单、Apple Silicon 原生、per-monitor DPI、Linux Qt6、200+ 语言语法高亮、FTP/SFTP/WebDAV/S3/Dropbox 虚拟文件系统、3 路合并（Pro）、脚本自动化。定价 $70/用户。
