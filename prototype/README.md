# BEMP 5.0 票据系统 · WAR/JAR 差异比对与智能分析工具

把「生产老包」和「产品部新包」丢进去，自动比对差异、反编译看源码级改动、用大模型分析风险/影响/测试要点，并导出报告与差异资产。双击 `BempDiff.exe` 即用（内嵌 JRE，无需装 Java、无需联网）。

## 快速开始

### GUI（推荐，需 Windows 桌面）
双击 `dist/BempDiff/BempDiff.exe` → 选老/新包 → 「开始比对」→ 选中类看双栏源码 diff → ⚙设置填 AI → 「AI 分析」→ 「导出报告 / 导出资产」。

### 命令行 headless（无需显示，适合 CI / 服务器）
`BempDiff.exe` 带子命令参数时直接跑核心比对并退出，**不弹 GUI、不初始化 JavaFX**：

```bash
BempDiff.exe compare <old> <new> [--expand-all]
BempDiff.exe report  <old> <new> [--expand-all] [--top-k N] [--cfr cfr.jar] [--out report.md]
BempDiff.exe export  <old> <new> [--expand-all] [--top-k N] [--cfr cfr.jar] [--out outdir]
BempDiff.exe decompile|inspect|ai ...   # 同 com.bempdiff.Main 用法
BempDiff.exe ai <old> <new> [--expand-all] [--top-k N] [--cfr cfr.jar] \
    [--project <工程目录>] [--replay dir] [--apikey KEY] [--baseurl URL] [--provider P]
```

### 项目级上下文增强（AI 更懂你的工程）

开启后，AI 分析前会**离线扫描**你指定的工程目录（不触网、不读取源码内容，仅识别结构与配置），把「项目整体架构 / 模块依赖 / 核心约定」作为分析依据，让结论更贴合实际，并在结果中说明**项目上下文如何影响本次判断**。

- **命令行**：`ai` 子命令加 `--project <工程或目录的绝对路径>`。例如把本次要发布的工程根目录传进去：

  ```bash
  BempDiff.exe ai old.war new.war --project D:/code/myproject --top-k 15 --replay ./replay
  ```

  输出会增加「项目级上下文: 已启用（构建系统=Maven, 模块数=N）」一行，并在阶段A 概览与阶段B 逐文件里附带「上下文影响」说明。

- **GUI**：⚙ 设置 → 「解析与导出」页 → 勾选「启用项目级上下文增强」并点「选择工程目录…」选定工程根目录（路径持久化到配置）。之后点「AI 分析」即自动携带项目上下文；报告「七、AI 智能分析」章节会包含项目级上下文摘要与逐文件的上下文影响。

> 扫描上限：最多 4000 个文件 / 12 层目录 / 单文件 2MB，避免超大型仓库或 zip-bomb 拖慢分析；扫描失败会静默降级为「未启用」，不影响基础比对。


> 默认 exe 是 GUI 子系统，headless 输出请用 `> out.txt 2>&1` 重定向查看；想要终端直显就按构建手册加 `--win-console` 重打一版。

## 它包含什么
- **分层差异**：L0 包级 / L1 内部业务码（按内部包前缀展开 class）/ L2 第三方依赖（jar 级折叠）
- **反编译**：CFR 为主、javap 降级；GBK 容错解码
- **两阶段 AI 分析**：概览（整体风险/影响范围/测试主题）+ 单文件深读（Top-K 限流）；成本闸门 + 金融合规脱敏；AI 不可达时优雅降级为基础结论
- **项目级上下文增强（AI 分析更贴合你的工程）**：可选引入指定工程/目录，AI 分析时先离线扫描其构建系统（Maven/Gradle/npm/Go/Python/Rust）、模块依赖、入口、配置文件与技术约定，作为分析依据，并在结论中单列「项目上下文如何影响本次判断」的说明（详见下文）
- **三区 JavaFX UI**：左差异树（BCompare 配色）/ 中双栏源码 diff / 右 AI 四 Tab + 配置中心（厂商/BaseURL/APIKey 掩码/模型/连接测试/代理/内部前缀/Top-K/CFR 路径）
- **导出**：MD 报告、差异 class 资产、反编译源码 zip

## 怎么重打包 / 改
见 [`构建手册-环境补齐与EXE打包.md`](构建手册-环境补齐与EXE打包.md)：JDK21 获取（Azul）→ 编译 → `jpackage` 自包含 exe → 双模 headless → 仅换 `app.jar` 免重打包。

## 验证情况（诚实）
- ✅ 真实 `BempDiff.exe` 启动器 + 内嵌 runtime，已在**无显示环境**端到端跑通 `compare`/`report`/`export`（证据见 `verify_exe/`）
- ✅ 核心逻辑与 Python 原型在真实 BEMP war（30,877 条目）与真实双版本 jar 上行为一致
- ⚠️ JavaFX GUI 点击式交互**仍待你在本机 Windows 双击实测**（headless 与 GUI 共用同一 `core.*`，风险极低）

## 目录索引
| 路径 | 说明 |
| --- | --- |
| `dist/BempDiff/BempDiff.exe` | 自包含 exe（也可 `dist/BempDiff.zip` 分发） |
| `java_core/README.md` | Java 核心端口：架构 / 子命令 / 真实验证 |
| `javafx_ui/README.md` | UI 功能对应与验证状态 |
| `构建手册-环境补齐与EXE打包.md` | 环境补齐与 exe 打包全流程 |
| `verify_exe/VERIFICATION.md` | exe 内嵌逻辑无桌面验证笔记与复现命令 |
| `verify_exe/` | compare/report/export 经真实 exe 跑出的证据文件 |

## 已知限制 / 待办
- **GUI 交互实测**（你本机双击完成）
- **真实 AI 调用**需你自己的 API Key（⚙设置填 openai/azure/ollama 的 BaseURL+Key）；无 Key 走 Mock 回放也能完成基础比对
- 可选增强：`.msi` 安装包（需 Wix）、`HttpAiAnalyzer` 换生产级 JSON 库、UI 文件树搜索/进度条
