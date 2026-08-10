# 项目长期记忆：BEMP WAR/JAR 差异比对与智能分析工具

## 技术栈与 UI 约定
- **产品形态**：JavaFX 桌面应用（双击 `BempDiff.exe` 即用，jpackage 自包含），核心逻辑 `java_core`，UI `javafx_ui`。
- **UI/样式开源库**（2026-08-09 重构确立）：
  - Web 高保真原型 `prototype/ui_prototype.html` → **Bootstrap 5.3.3 + Bootstrap Icons 1.11.3**，资源本地化于 `prototype/assets/`（含字体），支持 `data-bs-theme` 浅/深切换。
  - JavaFX → **BootstrapFX 0.4.0**（kordamp，模块化 `org.kordamp.bootstrapfx.core`）+ **Ikonli 12.3.1**（模块化 `org.kordamp.ikonli.core/.javafx/.bootstrapicons`）作为图标库。
  - 原计划的 **AtlantaFX 在本环境 Maven Central/jsdelivr 均 404 不可达**，故选 BootstrapFX（与 Web 侧 Bootstrap 主题一致）。
- **构建方式**：手工 `javac` + `jpackage`，无 Maven/Gradle；依赖走 `--module-path`（JDK21 + JavaFX21 + 上述开源 jar）。编译/打包的 `--add-modules` 已扩展含 `org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons`（见构建手册 §3/§4/§6）。
- 已下载的开源 jar 位于 `prototype/toolchain/`：bootstrapfx-core-0.4.0.jar、ikonli-core/javafx/bootstrapicons-pack 12.3.1.jar。

## 约定/坑
- Ikonli 三包是**模块化 jar**，模块名 `org.kordamp.ikonli.*`（不是 `ikonli.*`）；必须放 `--module-path` 且列入 `--add-modules`，否则 `import org.kordamp.ikonli.javafx.FontIcon` 报"程序包不可见"。
- BootstrapFX 加载：`scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());` 并对根节点加 `"bootstrap"` 类；控件加 `btn`/`btn-default`/`btn-primary`/`form-control` 才生效。
- JavaFX GUI 交互需本机 Windows 实测；无显示环境只能验证编译（headless 子命令可验证核心）。

## 业务要点（差异比对工具）
- 分层差异：L0 包级 / L1 业务码（按内部包前缀展开 class）/ L2 第三方依赖（jar 级折叠）。
- 反编译 CFR 为主、javap 降级；GBK 容错；两阶段 AI 分析（StageA 概览 + StageB 单文件深读）+ 成本闸门 + 脱敏；AI 不可达走 Mock 基础结论。
- 合规：API Key 默认不落盘（`persistApiKey=false`）；公网模型仅发脱敏 diff 摘要，源码不出机。
