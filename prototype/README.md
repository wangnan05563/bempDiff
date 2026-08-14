# BEMP 5.0 票据系统 · WAR/JAR 差异比对与智能分析工具

把「生产老包」和「产品部新包」丢进去，自动比对差异、反编译看源码级改动、用大模型分析风险/影响/测试要点，并导出报告与差异资产。双击 `BempDiff.exe` 即用（Tauri 桌面壳 + 内嵌瘦 JRE，无需装 Java、无需联网）。

## 快速开始

### GUI（推荐，需 Windows 桌面）
双击 `BempDiff.exe`（Tauri 桌面壳，内嵌瘦 JRE 自动起本地服务）→ 浏览器式 Web UI 选老/新包 → 「开始比对」→ 选中类看双栏源码 diff → ⚙设置填 AI → 「AI 分析」→ 「导出报告 / 导出资产」。原生文件/文件夹对话框直接选本机路径，无需上传。

### 命令行 headless（无需显示，适合 CI / 服务器）
`BempDiff.exe` 带子命令参数时直接跑核心比对并退出，**不弹 GUI 窗口**：

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

- **Web UI**：⚙ 设置 → 「解析与导出」页 → 勾选「启用项目级上下文增强」并点「选择工程目录…」选定工程根目录（路径持久化到配置）。之后点「AI 分析」即自动携带项目上下文；报告「七、AI 智能分析」章节会包含项目级上下文摘要与逐文件的上下文影响。

> 扫描上限：最多 4000 个文件 / 12 层目录 / 单文件 2MB，避免超大型仓库或 zip-bomb 拖慢分析；扫描失败会静默降级为「未启用」，不影响基础比对。


> 默认 exe 是 GUI 子系统（Tauri 窗口），headless 输出请用 `> out.txt 2>&1` 重定向查看；想要终端直显就按构建手册加 `--win-console` 重打一版。

## 它包含什么
- **分层差异**：L0 包级 / L1 内部业务码（按内部包前缀展开 class）/ L2 第三方依赖（jar 级折叠）
- **反编译**：CFR 为主、javap 降级；GBK 容错解码
- **两阶段 AI 分析**：概览（整体风险/影响范围/测试主题）+ 单文件深读（Top-K 限流）；成本闸门 + 金融合规脱敏；AI 不可达时优雅降级为基础结论
- **项目级上下文增强（AI 分析更贴合你的工程）**：可选引入指定工程/目录，AI 分析时先离线扫描其构建系统（Maven/Gradle/npm/Go/Python/Rust）、模块依赖、入口、配置文件与技术约定，作为分析依据，并在结论中单列「项目上下文如何影响本次判断」的说明（详见下文）
- **Web UI（Vue 3 + Bootstrap，由 Tauri 桌面壳承载）**：左差异树（BCompare 配色）/ 中双栏源码 diff / 右 AI 四 Tab + 配置中心（厂商/BaseURL/APIKey 掩码/模型/连接测试/代理/内部前缀/Top-K/CFR 路径）
- **导出**：MD 报告、差异 class 资产、反编译源码 zip

## 怎么重打包 / 改
**新版打包（推荐）**：见 [`scripts/build_tauri_app.ps1`](scripts/build_tauri_app.ps1) + [`WebUI迁移方案.md`](WebUI迁移方案.md) §9 —— Tauri 2 桌面壳 + jlink 瘦 JRE + Vite 前端，`cargo tauri build` 出 NSIS 安装包。
> 旧 JavaFX jpackage 链路见 [`构建手册-环境补齐与EXE打包.md`](构建手册-环境补齐与EXE打包.md)（**已归档**，仅作历史参考）。

## 验证情况（诚实）
- ✅ 真实 `BempDiff.exe` 启动器 + 内嵌 runtime，已在**无显示环境**端到端跑通 `compare`/`report`/`export`（证据见 `verify_exe/`）
- ✅ 核心逻辑与 Python 原型在真实 BEMP war（30,877 条目）与真实双版本 jar 上行为一致
- ✅ P5 回归：`java_core` 零 `javafx` 导入，重编译 + headless `compare` 在样本 war 上产出正确差异（stats + 树），核心 CLI 完全独立于已退役的 `javafx_ui`
- ⚠️ Web UI（Tauri 桌面壳）点击式交互**仍待你在本机 Windows 双击实测**（`server` 子命令与 Web 前端已端到端联调，`vite build` 通过；桌面壳须本机 `cargo tauri build` 实跑）

## 目录索引
| 路径 | 说明 |
| --- | --- |
| `src-tauri/target/release/bundle/nsis/*.exe` | Tauri 桌面壳安装包（自包含，内嵌瘦 JRE） |
| `java_core/README.md` | Java 核心端口：架构 / 子命令 / 真实验证 |
| `webui/` | Vue 3 + Bootstrap 前端（Web UI） |
| `src-tauri/` | Tauri 2 桌面壳（Rust 启动器 + 托盘 + 原生对话框） |
| `scripts/build_tauri_app.ps1` | 一键打包编排（jlink JRE + vite + cargo tauri build） |
| `WebUI迁移方案.md` | WebUI 迁移方案（含 P0–P5 与 Tauri 打包决策） |
| `javafx_ui/`（已退役） | 旧 JavaFX UI，迁移后不再使用，待清理（见 P5-3） |
| `构建手册-环境补齐与EXE打包.md`（已归档） | 旧 JavaFX jpackage 链路，仅历史参考 |
| `verify_exe/VERIFICATION.md` | exe 内嵌逻辑无桌面验证笔记与复现命令 |
| `verify_exe/` | compare/report/export 经真实 exe 跑出的证据文件 |

## 已知限制 / 待办
- **Web UI / Tauri 桌面壳交互实测**（你本机 `cargo tauri build` 后双击完成）
- **真实 AI 调用**需你自己的 API Key（⚙设置填 openai/azure/ollama 的 BaseURL+Key）；无 Key 走 Mock 回放也能完成基础比对
- 可选增强：`.msi` 安装包（需 Wix）、`HttpAiAnalyzer` 换生产级 JSON 库、UI 文件树搜索/进度条
