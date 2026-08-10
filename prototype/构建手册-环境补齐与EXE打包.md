# 构建手册：环境补齐（JDK21/JavaFX）→ 编译 → 运行 → 打包自包含 EXE

> 适用：在一台**只有 JDK8** 的 Windows 机器上，把 `prototype/` 下的 BEMP 差异比对工具（core + JavaFX UI）
> 编译成可双击运行的 exe。所有命令均已在本项目实测通过。
> 踩坑点与"为什么"都写在末尾 FAQ，照做可避开。

---

## 1. 前置与结论

- 工具本体是纯 Java；**core 层**用 JDK8 就能编译验证，**JavaFX UI + exe 打包必须用 JDK21**。
- JavaFX 21 的类文件是 Java 21 字节码，只有 JDK21 的 `javac` 能读取其 jar；JDK8 编译会报
  `unsupported class file major version`。所以**必须拿到 JDK21**。
- 产出：`dist_exe/BempDiff/BempDiff.exe` —— 内嵌 JRE + JavaFX，**双击即用、无需安装 Java、无需联网**。

---

## 2. 获取 JDK21（本机无 JDK21 时）

> Adoptium API 重定向到 GitHub、清华镜像在本环境被拦；**Azul API 直链可达**，用其返回的 `download_url`。

```bash
# 2.1 查最新 Zulu JDK21 windows x64 zip 的下载地址（返回 JSON，取 download_url）
curl -sSL "https://api.azul.com/metadata/v1/zulu/packages/?java_version=21&os=windows&arch=x64&archive_type=zip&java_package_type=jdk&release_status=ga&latest=true&distro_version=21"

# 2.2 下载（示例 URL，以 2.1 实际返回为准；约 200MB）
curl -sSL -o toolchain/jdk21.zip "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-win_x64.zip" --max-time 480

# 2.3 解压（用 Python 可靠解压，避免 unzip 在某些 shell 的异常）
python -c "import zipfile; zipfile.ZipFile('toolchain/jdk21.zip').extractall('toolchain')"
```

解压后得到 `toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/`，其 `bin/java -version` 应为 `21.x`。

---

## 3. 获取 JavaFX 21（Windows 运行时 jar）

从 Maven Central 拉四个平台 jar（base/controls/fxml/graphics，均为 `*-win.jar`）：

```bash
cd toolchain
for art in base controls fxml graphics; do
  curl -sSL -o "javafx-$art-21-win.jar" \
    "https://repo1.maven.org/maven2/org/openjfx/javafx-$art/21/javafx-$art-21-win.jar" --max-time 120
done

# 开源 UI / 主题 / 图标库（MIT / Apache-2.0）：BootstrapFX 主题 + Ikonli 图标
for j in \
  "org/kordamp/bootstrapfx/bootstrapfx-core/0.4.0/bootstrapfx-core-0.4.0.jar" \
  "org/kordamp/ikonli/ikonli-core/12.3.1/ikonli-core-12.3.1.jar" \
  "org/kordamp/ikonli/ikonli-javafx/12.3.1/ikonli-javafx-12.3.1.jar" \
  "org/kordamp/ikonli/ikonli-bootstrapicons-pack/12.3.1/ikonli-bootstrapicons-pack-12.3.1.jar" ; do
  fn=$(basename "$j")
  curl -sSL -o "$fn" "https://repo1.maven.org/maven2/$j" --max-time 120
done
```

---

## 4. 编译（core + UI，JDK21）

```bash
cd prototype
JAVAC21="toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/javac"
# Windows 上模块路径分隔符是分号 ';'（不是冒号 ':'）
MP="toolchain/javafx-base-21-win.jar;toolchain/javafx-controls-21-win.jar;toolchain/javafx-fxml-21-win.jar;toolchain/javafx-graphics-21-win.jar;toolchain/bootstrapfx-core-0.4.0.jar;toolchain/ikonli-core-12.3.1.jar;toolchain/ikonli-javafx-12.3.1.jar;toolchain/ikonli-bootstrapicons-pack-12.3.1.jar"

rm -rf javafx_ui/out && mkdir -p javafx_ui/out
"$JAVAC21" --module-path "$MP" --add-modules javafx.controls,javafx.fxml,org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons \
  -classpath cfr.jar \
  -encoding UTF-8 -d javafx_ui/out $(find java_core/src javafx_ui/src -name "*.java")
```

> ⚠️ **`cfr.jar` 必须进 `-classpath`**：`cfr.jar` 在 `prototype/` 根目录（`cd prototype` 后相对路径即 `cfr.jar`），它是非模块 classpath jar，`Decompiler.java` 直接 `import org.benf.cfr.reader.api.*`，不进 classpath 会编译失败。运行时 cfr 类由第 6 步合并进 `app.jar` 提供（见下方 P0-1 说明）。

成功标志：`javafx_ui/out/com/bempdiff/ui/` 下生成 `App.class` 等，无报错。

---

## 5. 运行（源码方式）

```bash
# Git Bash
bash javafx_ui/run_ui.sh
# 或 Windows 双击 javafx_ui/run_ui.bat
```

脚本已内置 JDK21 与 JavaFX 路径；首次运行确保 `javafx_ui/out` 已编译（见第 4 步）。

---

## 6. 打包自包含 EXE（jpackage，无需 Java/安装）

```bash
cd prototype
JAR21="toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/jar"
JP21="toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/jpackage"

mkdir -p dist_input
cp toolchain/javafx-*-21-win.jar toolchain/bootstrapfx-core-0.4.0.jar toolchain/ikonli-*.jar dist_input/
"$JAR21" --create --main-class com.bempdiff.ui.App -f dist_input/app.jar -C javafx_ui/out .

# P0-1 落地：把 cfr.jar 的类合并进 app.jar（cfr 是非模块 classpath jar，无签名 / 无 META-INF services，合并安全）。
# 排除其 META-INF（避免覆盖 app.jar 主清单）。合并后运行时自带 cfr 类，Decompiler 走**进程内 CFR API**，
# 默认即真源码反编译，无需 --cfr。cfr.jar 在 prototype/ 根目录（dist_input 的 ../..）。
mkdir -p dist_input/cfr_extract && ( cd dist_input/cfr_extract && "$JAR21" xf ../../cfr.jar && rm -rf META-INF )
"$JAR21" uf dist_input/app.jar -C dist_input/cfr_extract .
rm -rf dist_input/cfr_extract

"$JP21" --type app-image --name BempDiff --input dist_input --main-jar app.jar \
  --module-path dist_input --add-modules javafx.controls,javafx.fxml,org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons \
  --dest dist_exe --java-options "-Dfile.encoding=UTF-8"
```

产出：`dist_exe/BempDiff/BempDiff.exe`（含 `runtime/` 内嵌 JRE 与 `app/` 下 javafx 模块）。
双击即用。**`--type app-image` 不需要 Wix**（仅 `.msi`/安装包类型才需要）。

### 6.1 双模：GUI 双击 + 命令行 headless

`App.main` 已支持**双模**：
- **无参双击** → 启动 JavaFX GUI（需桌面显示）。
- **带子命令参数** → 直接跑核心比对逻辑并退出，**不初始化 JavaFX（无需显示设备）**，适合命令行/CI 与无显示环境验证。

```bash
# 子命令与 com.bempdiff.Main 完全一致：
BempDiff.exe compare  <old> <new> [--expand-all]
BempDiff.exe report   <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <md>]
BempDiff.exe export   <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <dir>]
BempDiff.exe decompile/inspect/ai ...   # 同 Main 用法
```

> ⚠️ 默认 `app-image` 启动器是 **GUI 子系统**，headless 模式输出需用 `> out.txt 2>&1` 重定向到文件才能看到（直接双击控制台看不到）。
> 若想要"命令行里直接看到输出"的 exe，打包时加 `--win-console`：

```bash
"$JP21" --type app-image --name BempDiff --input dist_input --main-jar app.jar \
  --module-path dist_input --add-modules javafx.controls,javafx.fxml,org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons \
  --dest dist_exe --java-options "-Dfile.encoding=UTF-8" --win-console
```

（`--win-console` 会让 GUI 模式也弹出一个控制台窗口，属可接受取舍；换来 headless 输出直通终端。）

### 6.2 仅换 app.jar（免重打包）

若只改了 `App`/core 源码、JRE 与 javafx 不变，不必整包重打：重打 `app.jar` 后**直接覆盖** exe 内的 `app/app.jar` 即可（写文件，不被 safe-delete 拦截）：

```bash
"$JAR21" --create --main-class com.bempdiff.ui.App -f dist_input/app.jar -C javafx_ui/out .
cp dist_input/app.jar dist_exe/BempDiff/app/app.jar
```

---

## 7. 验证清单

- [ ] `java -version` 指向 JDK21（21.x）。
- [ ] `javafx_ui/out` 编译无错。
- [ ] `bash javafx_ui/run_ui.sh` 启动无模块/链接报错（沙箱无显示会常驻等待窗口，属正常）。
- [ ] `dist_exe/BempDiff/BempDiff.exe` 存在；结构含 `runtime/` 与 `app/javafx-*-21-win.jar`。
- [ ] **交互实测在你 Windows 桌面做**：选两个 war/jar → 开始比对 → 选中类看双栏 diff → ⚙设置填 AI → AI 分析 → 导出报告/资产。

---

## 8. FAQ / 踩坑

**Q1. 为什么不用 Adoptium / GitHub？**
A. 本环境 Adoptium API 返回 307 重定向到 GitHub，GitHub 被拦（连接重置）；清华 Adoptium 镜像路径 404。Azul API 直链可达，优先用它。

**Q2. `找不到模块: javafx.controls`？**
A. 模块路径分隔符在 Windows 上是 `;`，不是 `:`。把 `--module-path` 的值用 `;` 连接（见第 4 步 `MP`）。

**Q3. `TreeItem` 找不到 `getUserData()`？**
A. `javafx.scene.control.TreeItem` **没有** userData 方法（那是 `Node` 的）。需用 `TreeItem.setValue(...)` 承载数据（本工具用完整 key 作 value，配 `statusMap`/`layerMap` 回查）。

**Q4. `ParseConfig.setExpandLib` / `AiConfig.setHttpProxy` 找不到？**
A. 方法名以 core 源码为准：`ParseConfig` 用 `setExpandInternalLib` / `setExpandAllForPlainJar`；`AiConfig` 的代理字段是后来补的 `setHttpProxy/setHttpsProxy`。改 UI 时以 `java_core/src/com/bempdiff/config/` 下的真实签名为准。

**Q5. `timeout` 命令报错 "无效语法"？**
A. 本环境 `timeout` 被解析为 Windows `TIMEOUT.EXE`（语法不同）。长任务用 Bash 工具自带的 `timeout` 参数兜底，命令里不要裸用 `timeout`。

**Q6. jpackage 要装 Wix 吗？**
A. 不需要。`--type app-image` 直接产应用镜像文件夹（含 `.exe` 启动器），满足"双击即用"。要 `.msi` 安装包才需 Wix。

**Q7. 真实 AI 调用怎么配？**
A. 桌面打开工具 → 设置（bi-gear 齿轮图标）→ AI 服务页：厂商选 openai/azure/ollama，填 BaseURL + API Key（掩码保存），点"连接测试"。无 Key 时自动走离线 Mock，仍能完成基础比对与源码 diff。

**Q8. 真实 war 双版本比对？**
A. 等 BEMP 第二个版本 war 下发，UI 里选"老包/新包"两个 war 即可；CLI 则 `java -cp out com.bempdiff.Main compare <老.war> <新.war> --expand-all`。
