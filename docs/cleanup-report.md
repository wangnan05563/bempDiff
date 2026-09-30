# 工作空间清理报告

> workspace-cleanup 技能归档（配置：根目录 `cleanup-config.yaml`）

## 2026-09-30 第 4 轮：散乱文件归位整理

**背景**：用户要求把根目录散乱文件整理到合适文件夹统一管理。侦察发现项目根本身仅 4~5 个文件（均在 allowlist），真正的散乱在 `bempdiff/` 原型根层。

**动作**：

| 文件 | 处置 | 目标 |
|---|---|---|
| `sonar-issues.json`（根，263KB 无引用） | `git mv` 保留跟踪 | `tooling/quality/` |
| `bempdiff/decompiled_src_v1/v2.java`、`decompiled_diff.txt`、`diff_result.json`、`real_diff.json`、`bemp_real_class.java`（6 个脚本运行产物） | 物理移入 + `git rm --cached` 出库 + ignore | `bempdiff/artifacts/` |
| `bempdiff/build_native.log`（未跟踪构建日志） | 删除（可再生，配置 `/bempdiff/*.log` 命中） | — |

**防复发**：
- `.gitignore` 新增 `bempdiff/artifacts/`（已验证 `git check-ignore` 命中）
- `decompile.py` / `e2e_real.py` 输出路径改为落 `artifacts/`，重跑不再散落根层

**保持原位的强引用文件（不可移动）**：
- `bempdiff/cfr.jar`：`sonar-project.properties`、`config/project_config.json`、`build_and_test.ps1`、原型脚本按此路径引用
- `bempdiff/bempdiff-logo.png`：`gen_social_preview.py` / 图标生成脚本硬编码
- `decompile.py` / `diff_engine.py` / `e2e_real.py`：原型工具链（配置 `script_exclude_dirs` 规定保留）

**验证**：py_compile PASS；`verification.required_present` 6/6 存在；稳定复查无日志复发；项目根 5→4 文件，`bempdiff/` 根层 16→10 文件。

**备注**：本轮未执行 git commit（变更留在暂存区/工作区，由用户决定提交时机）。
