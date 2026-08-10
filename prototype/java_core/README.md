# BEMP WAR/JAR 差异比对与智能分析工具 · Java 核心端口（原型验证）

> 本目录是《需求规格说明书 v1.1》中 `core.parse / core.diff / core.decompile / core.report / core.export / core.ai`
> 六个核心模块的 **Java 移植端口（参考实现）**。所有模块均已在本机**真实包**上编译、运行并验证通过，
> 与 `../diff_engine.py`、`../decompile.py` 两套 Python 原型行为一致，可作为量产版（JavaFX + jpackage）的**冻结基线**。

## 运行环境

- JDK 8+（本机实测 `JAVA_HOME` 指向 JDK 8，`javac -encoding UTF-8` 编译）
- 反编译可选：把 `cfr.jar`（https://www.benf.org/other/cfr/）放到本目录，用 `--cfr` 指定
- 无网络 / 无 API Key 也能跑通除真实 AI 调用外的全部流程

## 编译

```bash
cd prototype/java_core
rm -rf out && mkdir -p out
$JAVA_HOME/bin/javac -encoding UTF-8 -d out $(find src -name "*.java")
```

## 一键运行（真实包验证脚本）

```bash
bash run_demo.sh          # Git Bash / WSL / macOS；Windows 用 Git Bash 打开
```

脚本默认对 `../lib_v1.jar` ↔ `../lib_v2.jar`（真实 commons-lang3 3.12.0 ↔ 3.14.0）跑完：
`inspect / compare / decompile / report / export / ai(离线回放)`。

## 手动子命令

```bash
# 1) 单包检视（自动提取版本、分层统计）
java -cp out com.bempdiff.Main inspect <pkg.war|jar>

# 2) 全量差异（L0/L1/L2 分层 + 命名空间）
java -cp out com.bempdiff.Main compare <old> <new> [--expand-all]

# 3) 反编译 + 源码级 diff（Top-K 防爆炸）
java -cp out com.bempdiff.Main decompile <old> <new> --expand-all --top-k 15 --cfr ../cfr.jar

# 4) 导出 Markdown 报告（差异统计 + 全量树 + Top-K 源码 diff + 破坏性变更清单）
java -cp out com.bempdiff.Main report <old> <new> --expand-all --top-k 15 --cfr ../cfr.jar --out report.md

# 5) 导出差异资产（差异 class / 差异 jar / 反编译源码 zip + MANIFEST）
java -cp out com.bempdiff.Main export <old> <new> --expand-all --top-k 15 --cfr ../cfr.jar --out export_dir

# 6) 两阶段 AI 分析
#   默认离线回放（无 Key）：prompt 落地 + 解析回放响应
java -cp out com.bempdiff.Main ai <old> <new> --expand-all --top-k 15 --cfr ../cfr.jar --replay ./ai_replay
#   接真实 API：给 --apikey，连接失败会优雅降级为基础结论（不崩溃）
java -cp out com.bempdiff.Main ai <old> <new> --apikey $KEY --baseurl https://xxx/v1 --provider openai
```

## 已验证的能力（真实包）

| 模块 | 真实证据 | 结论 |
|---|---|---|
| `core.parse` | 真实 BEMP `bemp-adapter.war`（30,877 条目） | `L0=4 / L1=30710 / L2=163`，业务码与第三方依赖正确分层、不爆炸 |
| `core.parse` | 真实 `commons-lang3 3.12.0 ↔ 3.14.0` | 自动提取版本、统计 `新增63/删除4/修改345/未变1` |
| `core.decompile` | 真实 `cfr.jar 0.152` | CFR 主 + javap 降级，中文常量 GBK 容错解码，输出真实源码级 diff |
| `core.report` | 真实双版本 jar | 导出 `report.md`（统计 + 全量树 + Top-K 完整 diff + 破坏性变更） |
| `core.export` | 真实双版本 jar | `diff-classes/` 含 412 真实 `.class` + 状态侧车；`decompiled-sources.zip` 含 15 源码 |
| `core.ai` | 真实双版本 jar（离线回放） | 阶段A 解析 `overallRisk=HIGH`；成本闸门 `tokens≈19246>8000` 触发；阶段B 深读 Top-K |

## 架构（对应详细设计 §4）

```
com.bempdiff
├── model      逻辑条目 / 包快照 / 反编译产物 / 数据类
├── config     ParseConfig（分层策略）/ AiConfig（AI 两阶段 + 成本闸门）
├── parse      PackageParser（包类型识别 + 路径映射 + 分层 + 版本提取 + 按需取字节）
├── diff       DiffEngine / DiffResult / DiffStats（全量差异 + 删除类 + 非文本边界）
├── decompile  Decompiler（CFR 主 + javap 降级 + GBK 容错 + LCS 行级 diff）
├── report     MarkdownReport（FR7）
├── export     AssetExporter（FR6：差异 class / jar / 源码 zip）
└── ai         AiAnalyzer 接口 + PromptBuilders（脱敏）+ MockAiAnalyzer（离线回放）+ HttpAiAnalyzer（真实调用）
```

**设计要点（量产必看）**
- `LogicalEntry` 只存 `sha256 + size + EntrySource` 定位，**不缓存整条目字节** → 流式防 OOM，3 万级 war 不爆内存。
- 公网模型模式 prompt 经 `PromptBuilders.sanitize` 脱敏（身份证/密钥/手机号正则）；本地 ollama 不脱敏、代码不出机（金融合规 §5.9.1）。
- 成本闸门：阶段A prompt tokens 超阈值即告警，阶段B 仅深读 `stageBTopK` 个文件。

## 已知限制 / 下一步

- 当前为 **CLI 验证端口**，UI（JavaFX 三区 + 配置中心）需 JDK 21，本机 JDK 8 不支持新 JavaFX。
- `HttpAiAnalyzer` 为骨架：用 JDK8 `HttpURLConnection` 调 OpenAI 兼容 `/v1/chat/completions`；响应解析为轻量实现，量产建议换 JSON 库。
- 打包为双击 exe（`jpackage` + 内嵌 JRE）是 M1/M3 收尾项，尚未做。
- 待 BEMP 第二个版本 war 下发后，可直接用本端口跑真实 war 双版本 diff（替换 `<old>/<new>` 路径即可）。

## UI 与可双击 exe（已完成）

- **JavaFX 三区 UI + 配置中心**：见 `../javafx_ui/`（App / ConfigDialog / UiConfig / DiffTree），集成本 core 层。
- **自包含 exe**：`../dist_exe/BempDiff/BempDiff.exe` 已由 `jpackage --type app-image` 产出，内嵌 JRE + JavaFX，**双击即用、无需安装 Java**。
- 编译/运行需 JDK21 + JavaFX21（已随原型放在 `../toolchain/`）：见 `../javafx_ui/README.md`。
- 验证：JDK21 全量编译通过；`App` 与打包 exe 均干净启动（无模块/链接错误）；交互式点击测试需在你的 Windows 桌面进行（沙箱无显示设备）。
