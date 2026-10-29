# BempDiff

[![许可证](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![最新版本](https://img.shields.io/github/v/release/wangnan05563/bempDiff.svg)](https://github.com/wangnan05563/bempDiff/releases/latest)
[![支持平台](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20macOS-blue)](https://github.com/wangnan05563/bempDiff/releases)

![BempDiff — Java 交付物（war / jar / zip / 文件夹）差异化对比与 AI 分析桌面工具](bempdiff/assets/social-preview-1280x640.png)

> *Package diff & AI analysis for Java delivery artifacts.*

面向 Java 交付物（**war / jar / zip / 文件夹**）的桌面**差异化对比与智能分析**工具：把两个构建版本丢进来，得到目录树结构的差异、反编译后的源码级改动，以及 AI 风险分析——全程无需另装 Java。

> **为什么做 BempDiff？** 「生产版本 vs 下发版本」的发布前比对至今仍靠人工。BempDiff 把它变成可复用的流程：递归解包 → 分层结构化差异 → CFR 反编译 → AST 归一化 → 可选大模型分析（带成本闸门与脱敏），并提供可用退出码卡住流水线的 `ci` 入口。

模块级中文说明（特性清单、目录结构、开发上手）见 [`bempdiff/README.md`](bempdiff/README.md)。

---

## 核心能力

### 比对内核
- **分层差异视图**：`L0` 包级清单 → `L1` 内部业务类 → `L2` 第三方 jar，噪声默认折叠。
- **递归解包**：嵌套 `war → jar → zip` 逐层展开为原子条目。单条目 ≤64 KB 驻留内存，每个比对作业独享 256 MB 内存预算、全局 3× 硬上限，超额自动落盘。
- **源码级反编译**：CFR 为主、`javap` 降级，GBK 容错解码，只展示变更行 + 行号区间。
- **语法感知归一化**（FR-006）：反编译源码经 JDK 内置 `com.sun.source`（javac）AST 归一化，格式化调整与成员重排不再报成实质变更；解析失败自动降级为行级 diff。
- **API 兼容性分级**（FR-007）：新增 / 移除 / 签名变更 / 行为变更四类，不兼容项醒目标注，并写入报告与 CI JSON。
- **不只看包**：JS / HTML / CSS 文本差异（美化后对比）、目录 vs 目录对比（名称 / 类型 / 大小 / mtime / SHA-256）、zip/war/ear 归档清单级直比、docx/xlsx/pptx 抽取正文文本对比。
- **忽略规则**（FR-008）：引擎侧忽略空白 / 注释 / 自定义正则，前端侧按精确路径、前缀、文件名、扩展名管理忽略规则（差异树右键即加，本机持久化）。

### 审阅体验
- **BCompare 风格外壳**：目录树折叠 / 全部展开、路径导航历史（后退 / 前进 / 向上一层）、拖拽即比、文件与目录同一对话框自动识别。
- **字符级行内高亮**（FR-002）与**未变更区折叠**（FR-003，上下文行数可配，另有「仅差异内容」视图）。
- **主题**（FR-004 / FR-014）：亮 / 暗 / 跟随系统三态，多套强调色，语法高亮主题包可切换。
- **中英双语界面**，运行时切换。
- **gutter 快捷操作**（FR-015）：复制到对侧、标记已审阅，`j`/`k` 跳下一处 / 上一处差异，`n`/`p` 跳查找结果。
- **对比会话**（FR-010）：最近比对历史可一键重放；大包导出交给下载管理器异步处理。

### AI 分析
- **两阶段流水线**（FR-012）：整包风险概览 + Top-K 单文件深读，底层基于反编译调用关系做影响面分析。
- **成本透明**（FR-013）：调用前预估 token/费用并按阈值拦截，调用后展示输入 / 输出实际消耗。
- **项目级上下文增强**：分析前离线扫描指定工程目录（构建系统、模块依赖、技术约定），结论贴合真实工程。
- **优雅降级**：AI 不可达或未配 Key 时，比对 / 反编译 / 报告全链照常工作，AI 环节走 Mock；`--apikey ""` 可强制本地 Mock。

### 报告与打包
- **导出套件**（FR-009 / FR-011）：Markdown 报告、自包含 HTML 报告（内联 CSS、打印友好）、CI JSON 摘要、差异 class 资产 zip、反编译源码包、一键脱敏诊断包。
- **跨平台安装包**：electron-builder 产出 Windows x64/arm64（NSIS）、Linux arm64（AppImage + deb）、macOS arm64（dmg，未签名），内嵌瘦身 JRE。

---

## 技术栈

| 层次 | 技术 |
|---|---|
| 桌面壳 | Electron 31 |
| 前端 | Vue 3 + Bootstrap 5 + Vite 5，单测 Vitest |
| 比对引擎 / AI sidecar | Java 21，内嵌 HTTP 服务监听 `127.0.0.1:18765` |
| 反编译 | CFR（主）+ `javap`（降级） |
| 语法归一化 | JDK `com.sun.source` javac AST API（不引入额外原生依赖） |
| 归档 / Office 解析 | JDK `java.util.zip` + Apache POI |
| AI 服务商 | OpenAI 兼容接口（OpenAI、Azure、Ollama、通义等） |
| 打包 / CI | electron-builder（NSIS / AppImage / deb / dmg）、GitHub Actions（含 ARM runner）、SonarQube + JaCoCo |

---

## 安装

到 [Releases](https://github.com/wangnan05563/bempDiff/releases) 下载对应平台安装包。

| 平台 | 产物 | 状态 |
|---|---|---|
| Windows x64 | `BempDiff-<version>-setup.exe` | 每次发布随版本产出 |
| Windows arm64 | `BempDiff-<version>-arm64-setup.exe` | 由 [`build-arm64.yml`](.github/workflows/build-arm64.yml) 按需构建（`workflow_dispatch`） |
| Linux arm64 | `BempDiff-<version>-arm64.AppImage` / `.deb` | 由 [`build-arm64-unix.yml`](.github/workflows/build-arm64-unix.yml) 按需构建（`workflow_dispatch`） |
| macOS arm64 | `BempDiff-<version>-arm64.dmg` | 同一工作流构建，**未签名**——首次打开需右键 → 打开 |

安装包内嵌瘦身 JRE，无需额外安装 Java。构建细节见 [`docs/构建手册-环境补齐与EXE打包.md`](docs/构建手册-环境补齐与EXE打包.md)。

---

## 快速开始

### GUI

1. 启动 BempDiff。
2. 浏览或拖入**老包（生产）**与**新包（下发候选）**——文件与目录共用一个对话框，类型自动识别。
3. 点击**开始比对**，等待解包与差异树生成。
4. 左侧差异树点选条目 → 中间双栏源码 diff（全量 / 仅差异，unified / split / wrap）。
5. 需要 AI 分析时，打开**设置 → AI** 填服务商 Base URL + API Key，再用工具栏**智能分析**发起概览与逐文件深读。
6. **导出**菜单产出 Markdown / HTML 报告、差异资产包与脱敏诊断包。

### 命令行 / CI

带子命令参数时，可执行文件直接跑核心比对并退出，不弹窗口——同一个内核，没有界面依赖：

```text
inspect         <pkg>
compare         <old> <new> [--expand-all]
compare-folders <leftDir> <rightDir>                                  （文件夹对比，基础版）
folderdiff      <leftDir> <rightDir> [--report <md>] [--max-depth N]
decompile       <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>]
report          <old> <new> [--expand-all] [--top-k N] [--cfr <jar>] [--out <md>]
                             [--ai] [--apikey KEY] [--baseurl URL] [--provider P] [--model M]
                             [--aiconfig <path>] [--project <dir>] [--replay <dir>]
export          <old> <new> [--expand-all] [--top-k N] [--cfr <jar>] [--out <dir>]
diff-jars       <old.jar> <new.jar> [--out <md>]                       （差异 lib jar 的 class 源码对比）
ai              <old> <new> [--expand-all] [--top-k N] [--replay <dir>] [--apikey KEY] [--baseurl URL]
server          [--port 18765] [--webroot <dir>]                       （Web UI + REST 后端）
ci              --left <old> --right <new> [--report <md>] [--json <out.json>]
                [--fail-on-diff] [--ai] [--top-k N] [--cfr <jar>] [--expand-all]
```

`ci`（FR-005）是流水线门禁：复用完整报告链路、额外产出机器可读 JSON 摘要，并把结论写进退出码——`0` 无差异、`1` 有差异（仅在带 `--fail-on-diff` 时）、`2` 参数或运行错误。

```yaml
- name: 下发候选与生产版本比对门禁
  run: BempDiff.exe ci --left prod.war --right rc.war --json ci.json --report ci.md --fail-on-diff
```

也可以直接调用 Java sidecar：

```bash
java -cp dist_input/classes com.bempdiff.Main <子命令> ...
```

---

## 配置

服务端配置持久化在 `~/.bempdiff/bempdiff-web.properties`，在应用内**设置**即可编辑：

- **比对**：内部包前缀（L1 展开规则）、忽略规则、嵌套解包深度与线程数、空白 / 注释归一化。
- **AI**：服务商、Base URL、模型、API Key（掩码显示，默认不落盘）、代理、成本闸门阈值、单次输入 / 输出 token 上限、连接测试。
- **界面**：明暗主题、强调色、语法高亮主题、语言（中 / 英）、差异树过滤、启动图。
- **更新检查**：**关于 → 检查更新**读取 GitHub 最新 Release（默认仓库 `wangnan05563/bempDiff`，可用 `BEMPDIFF_GITHUB_REPO` 覆盖；私有仓库或触发匿名限流时，可在关于页填 GitHub 令牌或设 `GITHUB_TOKEN`），详见 [`docs/GitHub更新-人工操作手册.md`](docs/GitHub更新-人工操作手册.md)。

客户端偏好（忽略规则、语言、已审阅标记）存在浏览器 `localStorage`，不出本机。

---

## 架构

```text
┌──────────────────────────────────────────────────────────┐
│  Electron 桌面壳          bempdiff/dev-shell/             │
│  └── Vue 3 SPA            bempdiff/webui/                 │
│      差异树 · diff 视图 · AI 控制台 · 报告预览             │
├──────────────────────────────────────────────────────────┤
│  Java 21 sidecar          bempdiff/java_core/             │
│  parse/ unpack/  →  diff/（引擎、AST、API 兼容性分级）      │
│  decompile/（CFR + javap） · ai/（两阶段 + 项目上下文）     │
│  report/ export/ · server/（BempServer：HTTP + REST）      │
└──────────────────────────────────────────────────────────┘
```

sidecar 监听 `127.0.0.1:18765`，同时托管构建后的 SPA 与桌面壳调用的 REST 接口：

`/api/session/compare` · `/api/entry` · `/api/file` · `/api/job` · `/api/export` · `/api/config` · `/api/ai/context` · `/api/ai/models` · `/api/ai/test` · `/api/tasks/running` · `/api/update/check` · `/api/admin/diag-bundle` · `/api/admin/cleanup`

设计文档（接口契约、分层方案、UX 对标、性能报告）见 [`docs/`](docs/)。

---

## 开发

```bash
# 前端（Vite 开发服务器 + Vitest）
cd bempdiff/webui
npm install
npm run dev            # Vite 开发服务器
npm run test:unit      # vitest run
npm run build          # 生产构建 → dist/

# 桌面壳（开发态拉起 Java sidecar + Electron 原生窗口）
tooling/scripts/启动服务.bat      # 停止：tooling/scripts/停止服务.bat

# 安装包构建（编译 Java + jlink 瘦 JRE + 构建前端 + electron-builder）
tooling/scripts/构建打包.bat

# 一键「构建 + 发布 GitHub Release」（设 GH_DRY_RUN=1 为演练，不真正发布）
tooling/scripts/一键发布.bat
```

`bempdiff/package.json` 里另有 `dist`、`dist:x64`、`dist:arm64`、`jre:fetch`、`jre:status` 等脚本。

Java 测试走仓库内置的轻量 TestRunner，classpath 需要 `cfr.jar`：

```bash
javac -cp "dist_input/classes;toolchain/lib/*" -d out_test test/...
java  -cp "out_test;toolchain/lib/*;dist_input/classes;cfr.jar" \
      com.bempdiff.test.TestRunner com.bempdiff.test.XxxTest
```

静态扫描配置见 [`sonar-project.properties`](sonar-project.properties)（project key `bempdiff`，JaCoCo 覆盖率由 `tooling/quality/jacoco/` 生成）。

### 版本与发布约定

版本号为 `0.1.<yyyyMMdd><序号>`（日期 + 当日序号），由构建脚本自动写入 `package.json`；GitHub Release 的 tag 用**裸版本号**，与「检查更新」的比较口径一致。

---

## 安全与隐私

- **API Key** 默认不落盘，仅存在于后端进程内存，勾选「记住」才持久化。
- **AI 分析**在数据出机前套用金融与个人信息脱敏规则，成本闸门会拦截超阈值请求。
- **本地优先**：解包、比对、反编译全程不联网；唯一的外呼是你自行配置的 AI 服务商与 GitHub 发布检查。
- 用 `--apikey ""` 可强制 Mock 路径，完全离线。
- 权限与网络隔离评审记录：[`docs/SECURITY-权限隔离评审.md`](docs/SECURITY-权限隔离评审.md)。

---

## 路线图

已交付（优化 PRD 的 M1–M3）：行内字符级高亮、未变更区折叠、三态主题、CLI/CI 模式、AST 对齐 diff、API 兼容性分级、可配置忽略规则、HTML/MD/JSON 导出、会话重放、脱敏诊断包、影响面分析、成本透明、语法高亮主题包、gutter 操作。

下一批候选（P2 规划池，详见 [`docs/PRD-优化需求全景-基于GitHub同款调研.md`](docs/PRD-优化需求全景-基于GitHub同款调研.md)）：

1. 归档格式扩展——RAR / 7z / ISO 与磁盘镜像免解压直比。
2. 远程对比源——Maven Central 直接拉 artifact、FTP/SFTP/S3。
3. Maven / Gradle 依赖树与 POM 变更可视化。
4. 超大文件虚拟滚动渲染，以及多显示器 / 高分屏专项验证。
5. 反编译格式扩展（Kotlin 走 jadx 路线）与 AI 迁移建议。

---

## 贡献指南

欢迎提 Issue 与 Pull Request。

- 提交保持单一职责，不要把无关的半成品裹进同一个 PR。
- 改前端先跑 `npm run test:unit`，改 `java_core` 先跑 TestRunner 用例。
- 批处理脚本必须保持纯 ASCII（见 [`.gitattributes`](.gitattributes)）；中文文件名是刻意的，文件内容不是。
- 架构沿革与长期笔记见 [`docs/`](docs/) 及 `.workbuddy/memory/MEMORY.md`。

---

## 许可证

[MIT](LICENSE)

---

## 致谢

- [CFR](https://www.benf.org/other/cfr/) — Java 反编译器
- [Apache POI](https://poi.apache.org/) — Office 文档正文抽取
- [Electron](https://www.electronjs.org/)、[Vue 3](https://vuejs.org/)、[Bootstrap 5](https://getbootstrap.com/)、[Vite](https://vitejs.dev/)
