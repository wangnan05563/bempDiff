# SEC 证据目录清点（`bempdiff/verify_exe/sec/`）

> 本文件用于给 `docs/SECURITY-权限隔离评审.md` 的证据链做**可核对台账**。
> 最后核对：2026-09-10。

## 一、这是什么

2026-08 的 BempDiff 权限隔离评审中，用**真实构造的恶意包**对 P0（任意文件写）与
P1（SSRF 不全、解压炸弹）三类缺陷做的实跑验证残留。评审结论见
`docs/SECURITY-权限隔离评审.md`。

## 二、现存证据（✅ 均在库）

| 文件 | 大小 | 说明 |
|---|---|---|
| `report.md` | ~260 KB | 评审当时的报告快照，与 `../report.md`、`../regress/report.md` 内容**各不相同**（四次独立验证），不可相互替代 |
| `make_evil.py` | 674 B | 恶意包构造脚本 —— **可重放**：跑一次即可重新生成攻击样本，是证据链的关键可复现件 |
| `evil_export2/decompiled-sources.zip` | 207 B | 越界写被拦截后实际只产出了落于 `diff-classes/` 内的文件，说明「跳单条目」语义成立 |
| `export/decompiled-sources.zip` | 24 KB | 正常路径导出产物 |

## 三、已遗失的证据（⚠️ 不可恢复）

`evil_compare.log`、`evil_export2.log`、`ssrf.log`、`ssrf_local.log`

**根因**：仓库根 `.gitignore` 第 6 行有全局规则 `*.log`，而这些实跑证据当时以 `.log`
形式留在工作区 → **从初始提交 `4983e12` 起就未被纳入版本控制**。

取证依据：

- `git log --all --name-only --diff-filter=A | grep -i 'verify_exe.*\.log'` → **空**
  （整个仓库历史上从未入库任何 `verify_exe` 下的 `.log`）
- 初始提交 `4983e12` 的 `prototype/verify_exe/` 只有 16 个非 log 文件，
  与今日 `bempdiff/verify_exe/` 的 16 个完全一致
- 全盘检索 `D:\code` 与历次隔离区，无任何副本

**影响评估**：结论**不依赖**这 4 个 log。`report.md` + `make_evil.py` + `evil_export2/`
已能支撑「越界写被拦截」这一核心论断；如需完整复现过程日志，用 `make_evil.py`
重新生成恶意包跑一遍即可。

## 四、防复发（已封堵，2026-09-10）

1. 根 `.gitignore` 增加例外：`!bempdiff/verify_exe/`、`!bempdiff/verify_exe/**`
   —— 该目录下一切内容均为可追溯证据，永不按「运行时日志」处置。
2. `cleanup-config.yaml → preserve_paths` 登记 `bempdiff/verify_exe/`，自动清理不得命中。
3. 约定：**新证据不要用 `.log` 扩展名**（会被全局规则吞掉），用 `.txt` / `.out`
   或直接放本目录并在此台账登记。

## 五、维护约定

- 新增证据 → 在本文件第二节追加一行（文件 / 大小 / 说明）。
- 证据被撤销或迁移 → 移到第三节并写清原因，**不要直接删行**。
