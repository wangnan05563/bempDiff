# BempDiff SonarQube 扫描修复报告（T01365）

- 日期：2026-10-07
- 服务器：本地 SonarQube 26.1.0（http://localhost:9000）
- 项目 Key：`bempdiff`（工单所述 `sqa_fe4b…` 在本服务器不存在，实测数据挂在 `bempdiff`）
- 扫描范围：`bempdiff/java_core/src`（66 个 Java 文件，`sonar-project.properties`）
- 扫描器：sonar-scanner 8.0.1.6346（token 经环境变量注入，未落盘）

## 一、修复前基线

服务器历史累计 OPEN 830 条，其中绝大部分对应已删除/重构的旧代码（陈旧基线）。
本次对**当前代码**重扫（revision f46a250）后真实 OPEN = **18**：

| 严重级 | 数量 | 规则分布 |
|---|---|---|
| CRITICAL | 6 | S3776 认知复杂度 ×4、S1192 重复字面量 ×2 |
| MAJOR | 6 | S107 参数过多 ×4、S1168 返回 null ×2 |
| MINOR | 5 | S135 多重 break/continue ×3、S1659 多变量声明 ×1、S1133 ×1（INFO 级） |
| BUG / VULNERABILITY / BLOCKER | 0 | — |

## 二、修复清单（18/18）

| 文件 | 规则 | 处理 |
|---|---|---|
| ArchiveTree.java `listArchive` | S3776(37)+S135 | 拆分为 `readEntrySizes` + `upgradeFakeDirEntries`，多 continue 归一为单条件 |
| ArchiveTree.java `buildChildNode` | S3776(19) | 提取 `putBinaryMeta`（R2 摘要卡元数据段） |
| DiffEngine.java `findUniqueCompat` | S3776(18)+S135 | 抽 `collectCompatMatches`（命中上限 2 提前停），strict/loose 两段合一 |
| DiffEngine.java `tryAlignArchive`/`alignSubFiles` | S107(9 参 ×2) | `old/now` 由 `os/ns.getEntries()` 内部派生，9→7 参 |
| HttpAiAnalyzer.java `handleCallFailure` | S3776(17) | 抽 `degradedMaxTokens`（max_tokens 降级判定） |
| PromptBuilders.java `appendGroupSummary` | S107(8)+S1659+S135 | 新增 `GroupAllocation` 参数组；声明拆行；for+双 break 改 while 单守卫 |
| PackageParser.java `processNonLibEntries` | S107(8) | 改传 `Prefixes`，8→7 参 |
| PackageParser.java `handleLibJar` 内层循环 | S135 | 双 continue 合并为单 if 守卫 |
| BempServer.java `handleRunningTasks` | S1192 ×2 | 改用既有常量 `KEY_PHASE`/`KEY_MESSAGE` |
| BempServer.java `readEntrySafe` | S1168 ×2 | **保留 null**：null 是刻意的「缺侧」语义（空数组会误判为空文件），NOSONAR 附因注释 |
| ParseConfig.java `isIgnoredKey` | S1133 | 全仓零调用方，直接删除废弃方法 |

## 三、修复后（重扫收敛）

- OPEN = **0**（bugs 0 / vulnerabilities 0 / code_smells 0 / sqale 0）
- 质量门条件 `new_violations = 0` → OK
- Quality Gate 整体仍为 ERROR，两条与本次代码无关的环境级条件：
  1. `new_coverage = 0`（<80）：项目用自研 TestRunner（237 用例），未接 JaCoCo 覆盖率上报；
  2. `new_security_hotspots_reviewed = 0`（<100）：服务器存量 54 个安全热点未复核，且当前 token 无 `hotspots/search` 检索权限，无法在命令行侧逐条判定——需在管理界面用管理员账号处理。

## 四、回归验证

- java_core：`build_and_test.sh` 编译 + TestRunner **237/237 通过**（修复前后各一次）
- webui：vitest **42 文件 / 352 用例全通过**；vite build 产出新哈希 bundle
- 生产构建（本环境全链实测）：`build_tauri_app.ps1 -AssembleOnly` 重组 dist_input（jar 含本次改动）→ `patch_nsis_spacecheck.ps1` → `electron-builder` NSIS 完整出包 **rc=0**：
  - `release/_bk-sonar-smoke-20261007/BempDiff-0.1.2026093001-setup.exe`（128,067,326 B ≥50MB）+ `.blockmap`（134,851 B）
  - `post_pack_verify.ps1`：deliverable family intact + packaged webui bundles match source → **PASS**
  - 冒烟包未走版本号日 bump、未晋升 release 根目录，仅作构建验证；正式发版仍应跑 `构建打包.bat` 全流程。

## 五、提交

- `670b9b7` chore(sonar): T01365 SonarQube 存量 18 项清零（7 个源文件，+144/−105）
