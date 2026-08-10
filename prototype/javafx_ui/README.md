# JavaFX 三区 UI 与可双击 exe（M2/M3 收尾项）

> 在已验证的 `core.*` 服务层之上，补齐需求要求的 **JavaFX 桌面 UI**（左差异树 / 中双栏源码 diff / 右 AI 四 Tab）+ **配置中心弹窗**，并用 `jpackage` 产出**自包含、双击即用的 exe**。

## 目录

```
javafx_ui/
├── src/com/bempdiff/ui/
│   ├── App.java          # 主应用：三区布局 + 工具栏 + 后台任务（不卡 UI）
│   ├── ConfigDialog.java # 设置弹窗（FR9：AI 服务 / 解析与导出 全部 UI 入口）
│   ├── UiConfig.java     # 配置持久化（properties，~/.bempdiff/ui-config.properties）
│   └── DiffTree.java     # 由 DiffResult 构建差异文件树（BCompare 配色 + 层级标签）
├── run_ui.sh / run_ui.bat# 从源码运行
└── README.md
prototype/
├── toolchain/            # JDK21 (Zulu) + JavaFX 21 win jars（编译/运行/打包用）
├── dist_exe/BempDiff/    # ✅ 已打包的 exe（自包含，双击即用）
└── java_core/            # 已验证的 core.* 服务层
```

## 运行（从源码，需 JDK21 + JavaFX）

已随原型附带 `toolchain/` 下的 JDK21 与 JavaFX 21 jars，无需另行安装：

```bash
bash javafx_ui/run_ui.sh        # Git Bash
rem 或双击 javafx_ui/run_ui.bat  # Windows
```

## 编译

```bash
cd prototype
JAVAC21=toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/javac
MP="toolchain/javafx-base-21-win.jar;toolchain/javafx-controls-21-win.jar;toolchain/javafx-fxml-21-win.jar;toolchain/javafx-graphics-21-win.jar"
"$JAVAC21" --module-path "$MP" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d javafx_ui/out $(find java_core/src javafx_ui/src -name "*.java")
```

## 重新打包 exe（jpackage，自包含、无需 Java）

```bash
cd prototype
JAR21=toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/jar
JP21=toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/jpackage
mkdir -p dist_input
cp toolchain/javafx-*-21-win.jar dist_input/
"$JAR21" --create --main-class com.bempdiff.ui.App -f dist_input/app.jar -C javafx_ui/out .
"$JP21" --type app-image --name BempDiff --input dist_input --main-jar app.jar \
  --module-path dist_input --add-modules javafx.controls,javafx.fxml \
  --dest dist_exe --java-options "-Dfile.encoding=UTF-8"
```

产出 `dist_exe/BempDiff/BempDiff.exe`：内嵌 runtime（JRE）+ `app/` 下的 javafx 模块，双击即起。**无需安装 Java、无需联网**（AI 分析在离线 Mock 模式下也能跑基础比对）。

## 功能对应

| UI 区域 | 对应需求 | 集成点 |
|---|---|---|
| 工具栏：老/新包选择 + 开始比对/AI 分析/导出报告/导出资产/⚙设置 | FR1/FR2/FR9 | PackageParser / DiffEngine / MarkdownReport / AssetExporter / ConfigDialog |
| 左：差异文件树（BCompare 配色：新增蓝/删除红/修改黄/未变灰，L0/L1/L2 标签） | FR3/FR4 | DiffTree + DiffResult |
| 中：双栏源码 diff（选中类实时反编译 old/new） | FR4/T07/T08 | Decompiler（CFR/javap） |
| 右 Tab1 全局汇总：整体风险/影响/测试主题 | FR5 | AiAnalyzer.stageA |
| 右 Tab2 单文件分析：每文件风险/改动意图 | FR5 | AiAnalyzer.stageB |
| 右 Tab3 破坏性变更：删除的 L1 class 单列 | FR4.7/FR6.4 | DiffTree.destructiveChanges |
| 右 Tab4 审计日志：谁/何时/比对了什么/是否用 AI | FR9.13/AC9 | in-memory audit list |
| ⚙设置：厂商预设/BaseURL/APIKey(掩码)/模型/连接测试/代理/内部前缀/Top-K/CFR | FR9 + 评审点#8 | UiConfig + HttpAiAnalyzer（代理已接入） |

## 验证状态

- ✅ **编译**：JDK21 + JavaFX 21 全量编译通过（`javafx_ui/out` 生成全部 class）。
- ✅ **启动**：`java -cp ... com.bempdiff.ui.App` 与打包后的 `BempDiff.exe` 均无模块/链接错误，干净初始化（被工具超时 kill 仅因无桌面显示、GUI 等待窗口）。
- ✅ **打包**：`jpackage --type app-image` 产出自包含 exe，结构正确（runtime/ + app/ 含 javafx 模块）。
- ⚠️ **交互测试**：本沙箱无桌面显示设备，**未做真人点击式 UI 交互验证**。请在你的 Windows 桌面双击 `dist_exe/BempDiff/BempDiff.exe` 实测：选两个 war/jar → 开始比对 → 选中类看双栏 diff → ⚙设置填 AI → AI 分析 → 导出报告/资产。
- ⚠️ **真实 AI 调用**：需你自己的 API Key（设置弹窗填 openai/azure/ollama 的 BaseURL+Key）；离线无 Key 时走 Mock 回放，仍能完成基础比对与文件树/源码 diff。

## 已知限制 / 后续

- 当前为**参考实现**：UI 纯代码构建（未用 FXML/SceneBuilder），样式为最小可用；量产能加 CSS 主题、文件树搜索过滤、进度条。
- `HttpAiAnalyzer` 响应解析为轻量实现，量产建议换 JSON 库；连接测试已接代理。
- 打包为 `.msi`/安装包（需 Windows Wix）是可选增强，`app-image` 已满足"双击即用"。
- 待 BEMP 第二个版本 war 下发后，直接用 UI 选两个 war 即可跑真实双版本 diff。
