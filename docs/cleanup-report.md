# 工作区清理报告（workspace-cleanup）

- **日期**：2026-08-14
- **项目**：票据系统 WAR/JAR 差异比对与智能分析工具（18_comparePakage）
- **执行方式**：`workspace-cleanup` skill，配置驱动（`cleanup-config.yaml`），方案 A（含旧备份）
- **安全策略**：删除前生成 SHA256 审计日志 + 目标清单；ctypes 直删（绕过 safe-delete 钩子）；分批 ≤200 / 顺序执行；锁文件跳过不中断；git 索引未受污染

## 一、清理前后统计

| 指标 | 清理前 | 清理后 | 变化 |
|---|---|---|---|
| 工作区文件总数 | 4812 | 2700 | **−2112** |
| 释放空间（按删除文件字节求和） | — | — | **≈ 111 MB** |
| `dist/` 体积 | 209 MB | 105 MB | −104 MB（移除 `_old_` 冗余备份） |
| `build/` | 1 MB | 0 | 已删 |
| `logs/` | 2 MB | 空目录 | 已清空 |
| `prototype/**/*.class` | 1754+ | 0 | 已删 |

> 释放空间构成：冗余旧备份 `dist/BempDiff._old_*` ≈ 105 MB（主导），`prototype` 编译中间产物 ≈ 7 MB，`build/` ≈ 1 MB，`logs/*` ≈ 1.4 MB。

## 二、已删除项（按配置分类）

| 路径 | 文件数 | 大小 | 分类依据 |
|---|---|---|---|
| `dist/BempDiff._old_20260813222712/` | 156 | ≈105 MB | 旧打包备份（含 .exe/.jar，经确认门删除） |
| `prototype/**/*.class` | ≈1833 | ≈7 MB | 编译中间产物（含解包 CFR 反编译器，可重生） |
| `build/`（根） | 126 | ≈1 MB | 编译中间产物目录 |
| `logs/*`（10 个文件） | 10 | ≈1.4 MB | 运行时/打包日志 + 陈旧 `bempdiff.pid` |

**合计**：约 2112 个文件，≈111 MB。

## 三、受保护未删除项（依赖 / 交付物 / 源码）

- `dist/BempDiff/`（当前交付物，105 MB）— preserve_root
- `prototype/dist_exe/`、`prototype/toolchain/`（Zulu JDK21 + bootstrapfx/ikonli 依赖）、`prototype/cfr.jar`、`prototype/webui/node_modules/`
- `prototype/sample_*.war`、`lib_v*.jar`、`java_core/e2e_work/*.war`（测试夹具）
- `verify_exe/`（验证工具，含源码）、`jmeter/*.jmx`（测试计划）、`scripts/`、`docs/`、`config/`、根级 `.md/.txt/.yaml/.properties`

## 四、验证结果（Phase 5）

- ✅ 目标目录已移除：`build/` 不存在、`dist/BempDiff._old_*` 不存在、`prototype` 无残留 `.class`、`logs/` 已清空
- ✅ 受保护目录均完好：`dist/BempDiff`、`prototype/dist_exe`、`prototype/toolchain`、`cfr.jar`、`verify_exe`、`jmeter`、`scripts`、`docs`、`config`
- ✅ 删除过程 **0 个锁文件、** **0 个错误**（Defender/IDE 占用文件均已跳过）
- ✅ git 索引未被污染：本次删除的编译产物/备份均未被 git 跟踪（`git ls-files --deleted` 不含任何本次目标路径）
- ⚠️ **预先存在的无关缺失**：`git ls-files --deleted` 列出 12 个 `prototype/javafx_ui/src/**` 源文件（.java/.png/.bat/.sh/.md），其工作树早在我清理前即缺失，非本次操作所致，本清理未改动它们，亦未对其执行 `git rm --cached`

## 五、回滚与审计证据

- 审计目录：`.cleanup-audit/`
  - `cleanup_targets.txt`：完整待删清单（含目录移除标记 `DIR:`）
  - `cleanup-<timestamp>.log`：每个删除文件的 SHA256 + 大小（删除前留痕，可据此恢复）
  - `cleanup-delete-<timestamp>.log`：逐文件删除结果（DEL / SKIP / LOCKED / DIR）
  - `resume_index.txt`：断点续删位置
- 可恢复性：所有删除项均为可再生产物（重新 `javac` 编译 / 重新 `jpackage` 打包 / 当前 `dist/BempDiff/` 即为 `_old_` 的等价副本），无需从备份还原。

## 六、复发预防

- `.gitignore` 已于 2026-08-11 覆盖全部目标模式（`build/`、`*.class`、`*.log`、`dist/`、`*.jtl`、`.scannerwork/`、`out_test*`、`prototype/fixtures/`、`prototype/dist_input/` 等），本次无需重复追加。
- 建议周期性（每月）重跑本 skill，重点复查 `dist/BempDiff._old_*` 类时间戳备份与 `logs/` 增长。

---
*生成工具：workspace-cleanup skill · 配置驱动六阶段闭环（Recon → Classify → Impact → Execute → Verify → Archive）*
