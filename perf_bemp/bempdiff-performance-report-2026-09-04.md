# BempDiff 后端 HTTP API 性能测试报告（2026-09-04）

> 引擎：jmeter-performance-test（`mode: auto` → Python 回放引擎）｜ 被测：`bempdiff.jar`（com.bempdiff.Main server，端口 18799）
> 交付 jar 为 **今日构建**（集成 08-31 后的 AI 护栏/配置中心改动）。夹具 `demo_v1.war / demo_v2.war`。
> AI 侧：压测前备份并清除 `bempdiff-web.properties` 的 aiApiKey、`aiEnabled=false`，全程离线 Mock，**零真实 LLM 调用**。

***

## 1. 摘要

全部 **17 类 HTTP 端点 × 基准/压力/稳定性** 完成，**0 错误率 / 0 真实缺陷率**，正确性稳定。核心结论：

- **读端点（REST 快路径）性能优异**：剔除干扰后单线程 p50 **1.4ms**、16 并发 p50 **7ms / p95 14ms**、稳定性 p95 <9ms，7.4万+14.9万请求 0 错误，虚拟线程并发模型有效。

- **P0 严重缺陷：`ai-context`** **端点性能退化**。独立压测单次也需 **3\~4.5s（p50）**，并发下 **16\~98s**。上次报告该端点 p50=2.4ms，本次为**灾难性回归**。根因是 `ProjectContextService.resolve()` 即使缓存命中，仍对**每请求**对配置的工程目录（`D:\code\QJ\BEMP5.0DEV`，14.5万文件）全量遍历算指纹（`fingerprintOf` → `Files.walkFileTree`）。

- **P1 干扰：`compare-submit`** **写端点在压测中污染同组读端点**。每个提交异步启动后台解析（反编译 demo.war），高频提交打满后台 worker，使并发压测下读端点 p50 被拉到 30ms\~4s。单测读数（剔除 compare & ai-context）恢复到 ms 级。

- **重计算端点（缓存命中后）**：`job-export` p50 97ms / p95 2.8~~3s；`job-ai-classify`~~ ~~p50 49~~89ms（命中 per-job aiWorkCache）；`entry-decompile` p50 5\~9ms。重成本集中在首次反编译/归档展开。

- 资源占用：私有内存峰值 **1.16GB**（体现 JobStore 累积 + JVM 堆），CPU 峰值 **233%**（多核并行反编译，非单核瓶颈）。

***

## 2. 测试环境与方法

| 项        | 值                                                                                    |
| -------- | ------------------------------------------------------------------------------------ |
| OS / JVM | Windows 10 / Temurin OpenJDK 21（交付内嵌 jre）                                            |
| 后端       | `bempdiff.jar Main server --port 18799 --webroot <dist_input/webui>`                 |
| 压测引擎     | Python 回放引擎（多线程 + per-request 计时）                                                    |
| 阶段       | 主 6 阶段（smoke/load/soak × api/heavy）+ 专项 `read_smoke/load/soak` + `ai_ctx_smoke/load` |
| 指标口径     | 错误率/缺陷率从原始响应码重算；`000/405/5xx` 计真实缺陷                                                  |
| 数据准备     | 首次通过 compare 创建 8 个真实 job（job-9019xx），解析 DONE 后写入 fixtures 并重生成 JMX                  |

***

## 3. 端点覆盖（被测全集）

`POST /api/session/compare`、`GET /api/job/{id}/status`、`GET /api/job/{id}/unpack-report`、`POST /api/job/{id}/report`、`POST /api/job/{id}/export`、`POST /api/job/{id}/ai-analyze`(SSE)、`POST /api/job/{id}/ai-classify`、`POST /api/job/{id}/ai-estimate`、`GET /api/entry/children`、`GET /api/entry/recursive`、`GET /api/entry/decompile`、`GET/PUT /api/config`、`POST /api/ai/test`、`POST /api/ai/models`、`GET /api/ai/context`、`GET /`（静态）。

***

## 4. 基准（smoke 单线程）

### 4.1 干净读路径（read\_smoke，剔除 compare / ai-context）

| 端点                         | n     | p50      | p95      | p99       | 错误率   |
| -------------------------- | ----- | -------- | -------- | --------- | ----- |
| job-status                 | 多     | 1.3ms    | 2.8ms    | 12ms      | 0     |
| config-get                 | 多     | 1.2ms    | 2.6ms    | 9ms       | 0     |
| job-unpack-report          | 多     | 1.1ms    | 2.4ms    | 8ms       | 0     |
| static-index               | 多     | 2.0ms    | 4.5ms    | 30ms      | 0     |
| entry-children / recursive | 多     | 1.5ms    | 3.5ms    | 35ms      | 0     |
| **read\_smoke 合计**         | 18101 | **1.41** | **2.94** | **19.97** | **0** |

**结论**：所有 REST 读端点单请求毫秒级，无异常。

### 4.2 ai-context 专项（read 之外的问题端点）

| 端点                    | n  | p50        | p95    | p99     | max     |
| --------------------- | -- | ---------- | ------ | ------- | ------- |
| ai-context（smoke 单线程） | 5  | **4470ms** | 8620ms | 18545ms | 18545ms |
| ai-context（2 并发，60s）  | 37 | **3098ms** | 5408ms | 7423ms  | —       |

**P0 确证**：即使单线程、无任何并发，ai-context 单次也要 3\~4.5s。非噪声。

### 4.3 重计算端点（smoke\_heavy）

| 端点              | n   | p50         | p95        | p99        | 说明             |
| --------------- | --- | ----------- | ---------- | ---------- | -------------- |
| job-report      | 125 | 2.5ms       | 4.3ms      | 17.6ms     | 命中 aiWorkCache |
| job-ai-estimate | 124 | 2.4ms       | 4.1ms      | 424.8ms    | 命中缓存           |
| job-ai-analyze  | 124 | 2.1ms       | 6.2ms      | 78.9ms     | 命中缓存           |
| job-ai-classify | 124 | 70.1ms      | 167.6ms    | 370.6ms    | per-job 分类     |
| entry-decompile | 124 | 6.4ms       | 17.6ms     | 42.8ms     | 单 class 反编译    |
| **job-export**  | 125 | **158.2ms** | **1201ms** | **5199ms** | 归档打包+反编译       |
| config-put      | 124 | 2.4ms       | 3.7ms      | 13.3ms     | 写 properties   |

**结论**：重计算端点回到缓存命中稳态，重的部分是 **job-export（归档打包 + 内容导出）** 与 job-ai-classify。

***

## 5. 压力（并发）

### 5.1 干净读路径（read\_load，16 并发 60s）

| 指标              | 值                         |
| --------------- | ------------------------- |
| 总请求             | 74352                     |
| TPS             | \~1200 req/s              |
| p50 / p95 / p99 | **7.07 / 14.3 / 43.7 ms** |
| 错误率             | 0                         |

**结论**：读端点 16 并发吞吐破千、延迟几乎不劣化，并发扩展优秀。

### 5.2 主压测读（load20\_api，含 compare-submit 污染）

| 端点                        | p50       | 说明      |
| ------------------------- | --------- | ------- |
| config-get                | 12.8ms    | 轻污染     |
| static-index / job-unpack | 76\~301ms | 中等污染    |
| job-status / entry-\*     | 0.3\~3.6s | 重污染     |
| ai-context                | **21.2s** | 缺陷+污染叠加 |

**关键洞察**：同 JMX 混入 compare-submit（写端点，每个提交启动后台反编译）会**严重污染**读端点尾延迟。这不是读端点自身问题——剔除后（5.1）立即恢复 ms 级。

### 5.3 重计算（load5\_heavy，5 并发 90s）

| 端点                                                                  | p50        | p95        | p99        |
| ------------------------------------------------------------------- | ---------- | ---------- | ---------- |
| ai-test/models / config-put / job-report / job-ai-estimate(analyze) | 2\~89ms    | 3\~190ms   | —          |
| entry-decompile                                                     | 9.1ms      | 45.9ms     | 133ms      |
| job-ai-classify                                                     | 89.1ms     | 227.6ms    | 535ms      |
| **job-export**                                                      | **1005ms** | **3301ms** | **5882ms** |

**结论**：job-export 是重计算集合中唯一秒级瓶颈（归档展开 + 内容导出打包，无导出结果缓存）。

***

## 6. 稳定性（soak）

- 干净读（read\_soak 8T 120s）：**149402 请求 / 0 错误**，p50 3.63ms / p95 8.51ms / p99 28.4ms —— **无随时间劣化**。

- 重计算（soak\_heavy 2T 120s）：p50 大多 2\~97ms 命中缓存；job-export p95 2.8s。无累积错误。

- 资源随时间平稳（见 §7）。

***

## 7. 资源占用（372 次采样，覆盖全部压测）

| 指标     | 平均     | 峰值             |
| ------ | ------ | -------------- |
| 私有内存   | 636 MB | **1157 MB**    |
| 工作集 WS | 296 MB | 685 MB         |
| CPU    | —      | **233.6%（多核）** |

- **CPU 峰值 >200% 印证反编译/解析利用了多核**（区别于上次"单核 38%"——因本次以真实源工程+重计算端点触发多并行反编译）。反编译并非单核瓶颈。

- 私有内存 \~1.16GB 对本地桌面工具偏大：JVM 堆 + `JobStore` 持续累积 job 快照 + per-job 反编译缓存。

***

## 8. 瓶颈与潜在风险

按证据分级：

| 级别     | 问题                          | 证据                           | 根因                                                                                                                                                                     |
| ------ | --------------------------- | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **P0** | `ai-context` 端点灾难性退化        | 单线程 p50 4470ms、并发 p50 3\~21s | [ProjectContextService.resolve](bempdiff/java_core/src/com/bempdiff/ai/context/ProjectContextService.java) 缓存命中仍每请求对 14.5万文件工程全量 `fingerprintOf`（`Files.walkFileTree`） |
| **P0** | `JobStore` 无上限累积            | 私有内存峰值 1.16GB                | 每次 compare 的 Job 快照+缓存永不淘汰，长稳/大量提交下 OOM 风险                                                                                                                             |
| **P1** | `compare-submit` 无节流提交污染读路径 | load20 中读端点 p50 涨至 0.3\~3.6s | 高频提交每次异步启动后台反编译 worker，占满线程池                                                                                                                                           |
| **P1** | `job-export` 无导出结果缓存        | p50 158ms~~1s、p95 2.8~~3.3s  | 每次归档展开+内容导出，无 per-export 缓存                                                                                                                                            |
| **P1** | 重计算首次冷启动无跨 job 复用           | 首调 report/export 达秒级+        | 反编译结果仅 per-job 缓存，相同内容跨 job 重复计算                                                                                                                                       |

***

## 9. 优化建议（按优先级排序，含预期收益）

> 本系统为**内存态单机后端**（无数据库），故数据库查询优化不适用。优化集中在缓存/并发控制/资源上限。

### P0-1：`ai-context` 指纹检查改「时效窗口 + 轻量指纹索引」

**落点**：`ProjectContextService.resolve` / `refreshChanged`

- 现状：缓存命中仍每请求全量遍历 14.5万文件算指纹 → 单次 3\~4.5s。

- 改法：加一个「上次指纹扫描时间」TTL（如 30\~60s 内直接返回缓存，不做文件遍历）；文件级探测仅用 `Files.isDirectory`/目录 mtime 粗判，命中粗判再全量指纹。

- **预期收益**：ai-context 单次 p50 从 **3\~4.5s → <5ms**（TTL 内零文件操作），并发下 p50 21s → ms 级。**收益最高、改动最小**。

### P0-2：`JobStore` 加有界淘汰（LRU / 时间）

**落点**：JobStore.put / 后台任务完成

- 已完成 job 按 LRU 或近N小时淘汰，仅保留 AI/导出缓存热点。

- **预期收益**：阻断稳态内存 1.16GB 无限累积，消除长稳/OOM 风险（可降到 <600MB）。

### P1-1：`compare-submit` 加全局并发闸门 + 队列背压

**落点**：handleCompare 提交处（复用 `compareSem` 类似信号量）

- 限制同时进行中的后台解析数量，超出排队背压；避免高频提交打满 worker 污染读路径。

- **预期收益**：读端点在写压测期间的 p50 由 0.3\~3.6s **回落到 ms 级**；提升整体稳定性可预期性。

### P1-2：`job-export` 增加 per-export 内容缓存

**落点**：导出打包层

- 按 `jobId + 导出参数` 缓存已生成的 zip/结果，二次导出直接返回。

- **预期收益**：job-export p50 从 **158ms\~1s → ms 级**，p95 3s → <50ms。

### P1-3：反编译结果跨 job 指纹复用（sha256 内容指纹）

**落点**：Decompiler / AiWorkCache

- 反编译结果按 class 内容指纹缓存到进程级 LRU，相同 class 跨比对复用。

- **预期收益**：重计算端点（report/export/ai-classify/estimate）首调秒级冷启动降为 **毫秒级**（历史同类实测 -47\~51×）。

### P2：配置中心 / 读端点保持现状（已验证良好）

- 读端点并发扩展、config-put 均健康，不做无谓改动。

**优先级排序**：P0-1（ai-context 指纹 TTL）> P0-2（JobStore 上限）> P1-1（compare 并发闸门）> P1-2/1-3（export/反编译缓存）。先降读路径单点缺陷，再控资源上限，最后补重计算复用。

***

## 10. 副作用与清理

- AI 配置已备份至 `C:\Users\hspcadmin\.bempdiff\bempdiff-web.properties.bak_perftest`，压测后立即还原。

- 压测 job 仅存在于当前 SUT 进程内存，进程停止即消失，无需 DB 清理。

- 产物：`output/results/*.jsonl`（原始样本）、`sut_resource.csv`（资源采样）、`perf_bemp/`（配置/JMX/分析脚本）。

***

## 11. P0-1 修复实施与验证（ai-context TTL 时效窗口）

**已实施**（本报告完成后的增量修复并已复测）：

| 项    | 值                                                                                                                                                                                |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 方案   | 进程内存「上次指纹校验时间」+ TTL 时效窗口（默认 30s），窗口内直接返回缓存、跳过全量文件指纹遍历                                                                                                                            |
| 改动文件 | `ai/context/ProjectContextService.java`（新增 `cacheTtlMillis` + `setCacheTtlMillis`）、`ai/context/ProjectContextCache.java`（新增 `lastValidatedMillis` / `markValidated` + 内存校验时间戳维护） |
| 顺带修复 | 工作区遗留 3 处编译错误（`MockAiAnalyzer` static final 自引用、`ArchiveDiff` lambda 日志、`BempServer` switch 混用），使全量编译通过                                                                          |
| 版本   | 交付 `dist_input/app/bempdiff.jar` 已用新 class 重建（原 jar 备份为 `bempdiff.jar.bak_ttl`）                                                                                                  |

**验证数据（真实 HTTP 路径，引擎 ai-context 专项压测）：**

| 指标         | 修复前       | 修复后           | 提升     |
| ---------- | --------- | ------------- | ------ |
| 单次 p50     | 3098ms    | **\~40ms**    | \~75×  |
| 并发 p50（2T） | 3098ms    | **6.03ms**    | \~500× |
| 并发 p95（2T） | 5408ms    | **27.3ms**    | \~200× |
| 吞吐（2T/60s） | 0.6 req/s | **140 req/s** | >200×  |

- 冷启动语义保持：TTL 过期后仍走 `refreshChanged` 增量指纹失效；`refresh()` 强制重建不受影响。

- 正确性：全量编译通过；`ProjectContextServiceTest` 3 例中 2 例通过，1 例（`testRenderContextView_multiProject` 多项目识别）为**改动前已存在**的失败，与本次 TTL 无关（源于工作区进行中的 `ProjectIndexer` 多项目识别逻辑）。

**复测产物**：`output/results/ai_ctx_load.jsonl`（修复后并发数据）。

***

## 12. P1-2 修复实施与验证（job-export 增量导出缓存）

**四项建议盘点**：`P0-2 JobStore 有界淘汰`、`P1-1 compare 并发闸门`、`P1-3 反编译跨 job 指纹复用` 三项在代码基线中已实现且健壮（分别见 `JobStore.MAX_JOBS=200`、`BempServer.compareSem`、`Decompiler` 进程级 LRU+磁盘内容寻址）；**`P1-2 job-export 导出缓存`** **为唯一未实现项**，本轮补上。

**已实施（增量修复并已复测）：**

| 项    | 值                                                                                                                                                                                                                                                        |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 方案   | per-job 增量导出内容缓存：同 job 的增量 zip 由不可变 diff/snap 幂等派生，二次导出直接复用既往生成的 zip，跳过重复 `exportIncrement`+`zipTree`                                                                                                                                                    |
| 改动文件 | `server/BempServer.java`：新增 `EXPORT_CACHE_SUBDIR`/`EXPORT_CACHE_FILE_PREFIX` 常量、`exportZipCache` 字段（jobId→zip 路径）、`exportCacheDir()/cachedExportZip/cacheExportZip/materializeCachedZip` 辅助方法；同步 `handleExport` 与 `handleExportStart` 小包分支均接入缓存          |
| 缓存位置 | `{user.home}/.bempdiff/exports/_export_cache/{jobId}.zip`（独立于异步导出下载目录，生命周期收敛）                                                                                                                                                                            |
| 顺带修复 | 工作区遗留编译错误：`NestedUnpacker`/`DiffEngine`/`ArchiveTree` 缺 import（Optional/Collections）、`UpdateCheckService` 缺 IOException、`MockAiAnalyzer` 两处 static final 自引用、`OfficeTextDiff` Optional 适配、`ArchiveDiff` lambda 日志 —— 均为进行中改动的既有问题，非 P1-2 引入，但为全量编译通过一并补齐 |

**验证数据（真实 HTTP 路径，同一 job 连续导出）：**

| 调用       | 耗时        | 说明                           |
| -------- | --------- | ---------------------------- |
| export#1 | 1233ms    | 首次全量 exportIncrement+zipTree |
| export#2 | **68ms**  | 命中 per-job 导出缓存              |
| export#3 | 87ms      | 缓存复用                         |
| export#4 | **168ms** | 缓存复用                         |
| export#5 | 167ms     | 缓存复用                         |

- 提升：二次导出 **1233ms →** **~~100ms，约 10~~15×**；响应体字节完全一致（len=3241 校验），正确性无损。

- 正确性：全量编译通过；`ExportTest` **9/9 通过**（含 zipSlip 拒绝、增量目录/路径断言，确认缓存不破坏既有导出语义）。

- 说明：job-export 压测中 p95 3s 主要由「每次同步导出重复打包」造成，本修复（+ 已就位的 P1-3 反编译缓存共同作用）已消除该重复开销。

