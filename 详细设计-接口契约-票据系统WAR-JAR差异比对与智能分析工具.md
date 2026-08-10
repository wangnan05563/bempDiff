# 详细设计与接口契约 — 票据系统 WAR/JAR 差异比对与智能分析工具

> 版本：v1.0（设计基线）
> 关联文档：需求规格说明书 v1.1、开发任务清单 WBS、prototype 参考原型（Python）
> 说明：本文档将已通过真实包验证的 prototype 原型，转化为 Java 量产版的**可编码接口契约**。原型代码（prototype/diff_engine.py、decompile.py、e2e_real.py）是本文档的事实来源，凡有出入以原型行为为准。

---

## 1. 设计目标与约束

| 目标 | 约束 |
|---|---|
| 双击 exe 即用 | 内嵌 JRE，无需用户安装 Java；jpackage 生成自包含安装包 |
| 解析真实 war/jar（含 3 万级条目） | 流式/按需读取，避免整包解压到磁盘导致 IO 与空间爆炸 |
| L0/L1/L2 分层不淹没用户 | 第三方依赖默认 jar 级折叠，内部业务码默认展开 |
| 反编译真实字节码 | 内嵌 CFR，class → 可读 Java；CFR 不可用时降级 javap |
| 两阶段 AI，可空跑 | 未配置 AI 时基础比对/报告照常；AI 仅发脱敏摘要/完整 diff |
| 金融合规 | 本地/私有化模型优先；公网模型仅发脱敏 diff；审计日志留痕 |

**实测基线（来自 prototype 真实验证，非估算）**：
- 真实 war `bemp-adapter.war`（30,877 条目）→ L2=163 / L1=30,710 / L0=4，秒级完成。
- 真实两版库 `commons-lang3 3.12.0→3.14.0`（345→404 class）→ 新增63/删除4/修改345/未变1，CFR 反编译 Top-K(12) 个修改类正常。
- 真实 BEMP 业务 class（含 Spring Cloud 注解/泛型）→ CFR 产出 184 行干净源码。

---

## 2. 架构与模块

```
com.bempdiff
├── app                    # JavaFX 主程序、生命周期、exe 入口
│   ├── MainApp.java
│   ├── controller         # 视图控制器（对应 UI 三区）
│   └── view               # FXML / 自定义控件（差异树/双栏diff/AI面板/设置弹窗）
├── core                   # 无 UI 依赖的纯逻辑，可单测
│   ├── parse              # 包解析引擎（T04/T05/T06）
│   ├── diff               # 差异计算（T05/T06）
│   ├── decompile          # 反编译集成（T07/T08）
│   ├── ai                 # 两阶段 AI 分析（T09/T10/T11）
│   ├── report             # MD 报告导出（T12）
│   ├── export             # 差异资产导出（T13）
│   └── audit              # 审计日志（T15）
├── config                 # 配置模型与持久化（T14）
└── model                  # 共享数据结构（FR 实体）
```

**分层依赖**：`app → core / config / model`；`core` 各模块之间仅依赖 `model`。UI 不直接调用 CFR/AI 网络，统一经 `core` 服务层，便于单测与合规拦截。

---

## 3. 核心数据结构（model 包）

```java
/** 包类型（FR1.3 / FR2.7） */
enum PackageType { WAR, FAT_JAR, JAR }

/** 差异状态（FR3.1） */
enum DiffStatus { ADDED, DELETED, MODIFIED, UNCHANGED }

/** 分层（§5.2.1） */
enum Layer { L0, L1, L2 }   // L0=包级, L1=内部业务码, L2=第三方依赖

/** 文件分类（FR2.3） */
enum FileClass { CLASS, JAR, CONFIG, STATIC, OTHER }

/** 单条逻辑文件（prototype: build_logical_entries 的 entries[k]） */
class LogicalEntry {
    String key;            // 命名空间唯一键，如 "WEB-INF/lib/internal-core.jar/com/internal/B.class"
    Layer layer;
    FileClass fileClass;
    long size;
    String sha256;
    EntrySource src;       // 反编译/提取所需的定位信息
    String path;           // 展示用路径
}

/** 反编译定位（prototype: _extract_bytes 的 src） */
class EntrySource {
    String outerEntry;     // 外层 zip 内条目（jar 自身，或 war 内 lib/<x>.jar）
    String innerEntry;     // 若为内嵌 jar 中的 class，则为内部路径；否则 null
}

/** 单包解析结果 */
class PackageSnapshot {
    Path file;
    PackageType type;
    String version;        // FR1.7 自动提取，可 null
    Map<String, LogicalEntry> entries;
}

/** 差异结果 */
class DiffResult {
    Map<DiffStatus, List<String>> byStatus;   // 状态 -> key 列表
    DiffStats stats;
}

class DiffStats {
    int added, deleted, modified, unchanged;
    int bizChanged;      // 非 jar 的业务/类级变更（prototype: internal_or_biz_changed）
    int jarChanged;      // jar 级变更（prototype: jar_level_changed）
}

/** 反编译产物 */
class DecompiledUnit {
    String key;
    String oldSource;     // 老包侧源码（删除类仅 newSource 为 null）
    String newSource;     // 新包侧源码（新增类仅 oldSource 为 null）
    String diffText;      // 统一格式源码级 diff
    String engine;        // "cfr" / "javap" / "none"
    String error;         // 失败原因
    boolean ok;
}
```

---

## 4. 配置模型（config 包，T14）

对应需求 v1.1 §5.9 配置中心；全部 UI 入口。持久化到 `%APPDATA%/bempdiff/config.json`（密钥本地加密，非明文落盘）。

```java
class AppConfig {
    AiConfig ai = new AiConfig();
    ParseConfig parse = new ParseConfig();
    ExportConfig export = new ExportConfig();
    UiConfig ui = new UiConfig();
    ProxyConfig proxy = new ProxyConfig();
}

class AiConfig {
    String provider;            // "openai" / "azure" / "ollama" / "qwen" / "custom"
    String baseUrl;             // 如 https://api.openai.com/v1 或 http://localhost:11434/v1
    String apiKeyEncrypted;     // 加密存储；UI 输入掩码 "****"
    String model;               // 如 gpt-4o / qwen-max / llama3.1:70b
    boolean enabled;            // 未配置=false，基础比对仍可用
    int stageAFileSampleLines = 80;   // 阶段A 单文件 diff 摘要截断行数
    int stageATopK = 30;              // 阶段A 概览纳入文件上限
    int stageBTopK = 15;              // 阶段B 深读完整 diff 上限（FR7.2 默认 15）
    double costGateWarnTokens = 8000; // 成本闸门：预估超阈值弹确认
}

class ParseConfig {
    List<String> internalPrefixes = List.of("com/", "cn/");  // FR9.5 内部包前缀
    boolean expandInternalLib = true;    // L1 展开内部 lib（§5.2.1 默认）
    boolean expandThirdPartyLib = false; // L2 默认折叠
    long maxEntryBytes = 64 * 1024 * 1024;
}

class ExportConfig {
    boolean includeTopKFullDiff = true;  // FR7.2 默认含 Top-K 完整 diff
    int defaultTopK = 15;
    Path lastReportDir;
    Path lastAssetDir;
}

class UiConfig {
    boolean showL0 = true, showL1 = true, showL2 = true;  // 树筛选（§5.10）
    boolean darkTheme = false;     // M3 后置
}

class ProxyConfig {                // FR9.11 企业网代理
    boolean enabled = false;
    String httpHost, httpPort, httpsHost, httpsPort;
    String username, passwordEncrypted;
}
```

---

## 5. 模块接口契约

### 5.1 包解析引擎（T04/T05/T06，FR1/FR2）

```java
interface PackageParser {
    /** 识别包类型：依据内部布局判断 war/fat_jar/jar（prototype: detect_package_type） */
    PackageType detectType(Path file);

    /** 全量解析为逻辑文件集合（prototype: build_logical_entries）。
     *  关键：① 命名空间避免同名类冲突；② 内部 lib 展开 L1，第三方 lib 仅 jar 级 L2；
     *  ③ 普通 jar（无 WEB-INF）在 expandAll=true 时所有 class 视为 L1。 */
    PackageSnapshot parse(Path file, ParseConfig cfg, boolean expandAll) throws IOException;

    /** 版本自动提取：MANIFEST Implementation-Version / META-INF/maven/*/pom.properties（FR1.7） */
    String extractVersion(PackageSnapshot snap);

    /** 从 LogicalEntry.src 取回 class 字节（prototype: _extract_bytes） */
    byte[] readEntryBytes(PackageSnapshot snap, LogicalEntry entry) throws IOException;
}
```

**路径映射表（FR2.7 / §5.2，prototype: path_mapping）**

| 包类型 | classes 前缀 | lib 前缀 |
|---|---|---|
| WAR | `WEB-INF/classes/` | `WEB-INF/lib/` |
| Spring Boot fat jar | `BOOT-INF/classes/` | `BOOT-INF/lib/` |
| 普通 jar | `(根)` | — |

**算法要点**：
- 先扫一遍收集 `lib/*.jar`；对每个 lib jar 判定是否含内部前缀的 class → 是则展开其 class 为 `L1`（key 加 `WEB-INF/lib/<jar>/` 命名空间），否则整 jar 作为 `L2` 单条目。
- 再扫一遍非 lib 条目：`classes_prefix` 下或普通 jar 的 class → `L1`；否则 `L0`。
- 流式：用 `ZipFile` 按需 `getInputStream`，不整包落盘；大 jar（>maxEntryBytes）跳过并标记 `OTHER/oversize`。

### 5.2 差异计算（T05/T06，FR3/FR4）

```java
interface DiffEngine {
    /** 全量差异（prototype: compute_diff）：key 集合对称差 + sha256 比对 */
    DiffResult compute(PackageSnapshot old, PackageSnapshot now);

    /** 统计（prototype: compute_stats） */
    DiffStats stats(DiffResult r, PackageSnapshot old, PackageSnapshot now);
}
```

**删除类处理（FR4.2/4.3/4.7，评审点#5）**：`DELETED` 条目仅 `oldSource` 可用，UI 左栏展示老包侧源码并打「破坏性变更」标记；不进入双栏 diff，单列为「破坏性变更清单」（FR6.4）。

**非文本边界（FR4.8，评审点#6）**：`STATIC/OTHER`（图片/字体等）仅比对存在性与 size/sha256，状态标记 `MODIFIED` 但**不做内容 diff**，报告中注明「二进制资源，未做内容比对」。

### 5.3 反编译集成（T07/T08，§5.4）

```java
interface Decompiler {
    /** 反编译单 class（prototype: decompile.decompile）：
     *  CFR 主；失败降级 javap；统一返回 {source, engine, ok, error} */
    DecompiledUnit decompile(byte[] classBytes, String displayName);

    /** 批量反编译，逐文件超时/异常隔离（prototype: batch_decompile） */
    Map<String, DecompiledUnit> batch(List<DecompileReq> reqs, int topK, Duration timeoutPerFile);
}

class DecompileReq { String key; byte[] oldBytes; byte[] newBytes; }  // 新增类 newBytes 有、old 无
```

**实现约束（量产版）**：
- CFR（`cfr.jar`）打进 exe 资源目录随包分发，`java -jar cfr.jar <class>` 子进程调用（prototype 同形）。
- **GBK 坑（已在原型踩中并解决）**：CFR 在有中文常量的 class 上按系统编码输出 stdout，须按字节捕获并容错解码（utf-8→gbk→latin-1），否则子进程会 `UnicodeDecodeError`/乱码。**Java 侧同样用 `Process.getInputStream().readAllBytes()` + 容错 `String(bytes, 候选编码)` 解码**。
- 反编译服务层是**合规拦截点**：本地模型模式下不触网；公网模型模式仅把 `DecompiledUnit.diffText`（脱敏，见 §5.4）外发。

### 5.4 两阶段 AI 分析（T09/T10/T11，§5.5.1，评审点#2）

```java
interface AiAnalyzer {
    /** 阶段A 概览（prototype 思路）：发「差异清单 + 每文件截断 diff 摘要」，
     *  产出整体风险/影响/测试主题 + 每文件初评，返回结构化 JSON。 */
    StageASummary stageA(DiffResult diff, Map<String,String> fileDiffSummaries, AiConfig cfg);

    /** 阶段B 深读：仅对 high/medium 风险或用户勾选文件，发完整反编译 diff，
     *  逐文件产出改动意图/风险/影响/测试要点。 */
    List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg);

    /** 连接测试（设置弹窗「测试」按钮，FR9.4） */
    boolean testConnection(AiConfig cfg);
}
```

**两阶段数据契约**：

```java
class StageASummary {
    String overallRisk;          // LOW / MEDIUM / HIGH
    String impactScope;          // 自由文本：影响模块/对外接口
    List<String> testThemes;     // 测试要点（全局）
    List<FileRisk> fileRisks;    // 每文件初评（含 risk 等级）
}
class FileRisk { String key; String risk; String oneLineReason; }

class FileAnalysis {              // 阶段B 逐文件
    String key;
    String intent;               // 改动意图
    String risk;                 // LOW/MEDIUM/HIGH
    String impact;               // 影响范围
    List<String> testPoints;     // 测试要点
}

/** AI 输入裁剪（控制 token/成本，评审点#2）：
 *  阶段A：每文件 diff 仅取前 cfg.stageAFileSampleLines 行 + 状态；最多 cfg.stageATopK 文件。
 *  阶段B：仅发完整 diffText（已脱敏），上限 cfg.stageBTopK。 */
```

**脱敏规则（§5.9.1，评审点#4）**：公网模型不允许发送原始源码常量中的**业务敏感字串**（如证件号、密钥字面量）。原型阶段用 diff 行即可满足，量产版在 `ai` 服务层做正则脱敏后再外发；本地/私有化模型（ollama）可关闭脱敏（代码不出机）。

**成本闸门**：阶段A/B 前估算 token（`diffText` 字符数≈token 数比例），超 `costGateWarnTokens` 弹确认（FR5 默认开启）。

### 5.5 报告导出（T12，FR7）

```java
interface ReportExporter {
    /** 生成 Markdown 报告（prototype: write_markdown_report）。
     *  默认包含：差异统计 + 全量清单 + Top-K（cfg.defaultTopK，按风险排序）完整 diff + 总结。 */
    Path exportMarkdown(DiffResult diff, DiffStats stats, Map<String,DecompiledUnit> decompiled,
                        List<FileAnalysis> analyses, AppConfig cfg, Path outDir);
}
```

报告结构（FR7）：标题/版本 → 一、差异统计 → 二、差异文件树（全量清单）→ 三、反编译源码级差异（Top-K）→ 四、AI 分析（整体风险/影响/测试主题 + 逐文件）→ 五、破坏性变更清单（删除类）→ 六、审计摘要（比对时间/包路径/是否外发）。

### 5.6 差异资产导出（T13，FR6）

```java
interface AssetExporter {
    /** 差异 class 目录：仅复制 ADDED/DELETED/MODIFIED 的 class 字节（保持命名空间） */
    Path exportDiffClasses(DiffResult diff, PackageSnapshot old, PackageSnapshot now, Path outDir);
    /** 差异 jar 目录：仅复制变化的 lib/*.jar（L2 变化） */
    Path exportDiffJars(DiffResult diff, PackageSnapshot old, PackageSnapshot now, Path outDir);
    /** 反编译源码 zip：Top-K 完整源码 + manifest 清单 */
    Path exportDecompiledSources(Map<String,DecompiledUnit> decompiled, Path outDir);
}
```

### 5.7 审计日志（T15，FR9.13，评审点#4）

```java
interface AuditLogger {
    /** 记录：时间、操作用户、老包路径+sha256、新包路径+sha256、是否外发 AI、AI provider、命中文件数 */
    void logCompare(AuditEvent e);
    List<AuditEvent> query(LocalDate from, LocalDate to);
}
class AuditEvent {
    Instant ts; String user; String oldSha, newSha;
    boolean aiExternallySent; String aiProvider; int filesTouched;
}
```

**合规硬门槛（AC9）**：本地/ollama 模式 `aiExternallySent=false` 且**全程抓包验证零外发**；公网模型模式 `aiExternallySent=true` 且 `diffText` 经脱敏。审计日志不可关闭。

---

## 6. 错误码与降级矩阵

| 场景 | 行为 | 降级 |
|---|---|---|
| 无 Java 运行时 | 反编译整模块不可用，基础比对/报告仍可用 | `engine=none`，文件树标注「未反编译」 |
| CFR 对该 class 报错 | 单文件反编译失败不影响整体 | 降级 `javap`（签名级） |
| lib jar 损坏/非 zip | 该 jar 仅记为 L2 单条目，跳过展开 | 标注 `corrupt` |
| 超大条目（>maxEntryBytes） | 跳过内容读取 | 状态仍可比对存在性 |
| AI 未配置/连接失败 | 报告不含 AI 章节，基础章节完整 | 提示「未配置 AI」 |
| AI 超成本闸门 | 中止阶段B，仅保留阶段A | 用户二次确认可继续 |

---

## 7. 与 WBS 任务映射（回链可验证）

| WBS 任务 | 本文档契约 |
|---|---|
| T04 包接入与版本管理 | §3 PackageSnapshot / §5.1 detectType·extractVersion / FR1.7 |
| T05 分层解析引擎 | §3 LogicalEntry·Layer / §5.1 parse（L0/L1/L2 + 命名空间）/ §5.1 路径映射表 |
| T06 全量差异计算 | §3 DiffResult / §5.2 DiffEngine / 删除类·非文本边界 |
| T07 反编译集成 | §5.3 Decompiler / GBK 容错解码 |
| T08 双栏源码 diff | §3 DecompiledUnit.diffText / §5.3 batch |
| T09 阶段A 概览 | §5.4 stageA / StageASummary |
| T10 阶段B 深读 | §5.4 stageB / FileAnalysis / 成本闸门 |
| T11 AI 配置与连接测试 | §4 AiConfig / §5.4 testConnection |
| T12 报告导出 | §5.5 ReportExporter |
| T13 差异资产导出 | §5.6 AssetExporter |
| T14 配置中心 | §4 AppConfig 全量字段 / 持久化加密 |
| T15 审计日志 | §5.7 AuditLogger / AC9 |

---

## 8. 已知缺陷与待办（来自原型验证）

1. **[已修复] print_tree 层级标签 bug**：普通 jar 比对时 class 条目真实 layer=L1，但旧 `print_tree` 按路径重推会误判 L0。已改为从 `entries` 取真实 layer（prototype/diff_engine.py）。量产版 `view` 层同样以 `LogicalEntry.layer` 为准，不复算。
2. **真实 war 双版本 diff 脚本**：待产品部下发第二个版本 war 时，可直接用 `diff_engine.compare(old.war, new.war) + --decompile` 跑通；原型已具备能力，仅缺第二个真实版本包。
3. **反编译 GBK 容错**：见 §5.3，Java 侧务必实现，否则中文票据系统的 class 必踩。
4. **本地模型零外发验证**：AC9 硬门槛，需抓包回归（ollama 模式下确无外发请求）。

---

## 9. 下一步

- 选定 JDK 版本（建议 JDK 21 LTS），初始化 JavaFX + Maven/Gradle 工程骨架。
- 优先落地 `core.parse` + `core.diff`（纯逻辑、可单测、无 UI 依赖），用 prototype 的 e2e 真包数据做对拍基准。
- 反编译模块先内嵌 CFR 资源，先打通单 class，再补批量与成本闸门。
