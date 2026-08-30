# BempDiff 真实后端 HTTP API 性能测试报告

> 生成时间：2026-08-28 ｜ 引擎：jmeter-performance-test（`mode: auto` → 回退 Python 回放引擎）
> 被测对象：`com.bempdiff.Main server`（生产交付 `bempdiff.jar`，JDK21 内置 `com.sun.net.httpserver`），端口 18799
> 夹具：`demo_v1.war` / `demo_v2.war`（代表性 WAR 对比对）
> 副产物：`output/results/*.jsonl`（原始样本）、`regression_report.md`（引擎报告）、`sut_resource.csv`（资源采样）

---

## 1. 摘要

对 BempDiff 真实后端暴露的 **全部 HTTP API 端点** 完成了基准(smoke)、压力(load)、稳定性(soak) 三档测试，覆盖 17 类端点。**六阶段全量 0 错误率、0 真实缺陷率**，正确性稳定。按负载特征可清晰分为两类：

- **轻 / 读 / REST 端点**：`compare-submit`、`job-status`、`job-unpack-report`、`config-get`、`ai-context` 等单请求毫秒级，20 并发下总吞吐约 **78 req/s**，`p95 < 30ms`，具备良好的并发扩展性（虚拟线程模型有效）。
- **重计算端点**：`job-ai-classify`、`job-export`、`entry-decompile`、`entry-children/recursive` 单请求 **0.3s ~ 8s**（含反编译/归档展开/导出），并发下 `p50 3–6s、p95 6–7s`。这是本系统最主要的性能短板，瓶颈不在 CPU（峰值仅约 38% 单核），而在**每请求重复反编译与归档解包的 IO/子进程开销、且无跨 job 内容复用**。

资源占用温和：常驻私有内存 **~1.17GB**、工作集 **~719MB**、CPU 峰值 **~38%**，无短期内存泄漏迹象；但 `JobStore` 无上限，长稳 soak 有累积风险。

---

## 2. 测试环境与方法

| 项 | 值 |
|---|---|
| OS / JVM | Windows 10 / Temurin OpenJDK 21 |
| 后端 | `bempdiff.jar`（`Main server --port 18799 --webroot <webui dist>`） |
| 压测引擎 | Python 3.14 回放引擎（无 JMeter，`auto` 自动回退；多线程 + per-request 计时） |
| 负载模型 | 快端点 1/20/8 线程；重端点 1/5/2 线程（重计算并发度受单请求时长约束，按技能 §3.2 估计） |
| 阶段 | 基准=smoke(1T)、压力=load(20T/5T)、稳定性=soak(8T/2T) |
| 指标口径 | 错误率/缺陷率从原始响应码重算；`000/405/5xx` 计真实缺陷 |
| 出站依赖隔离 | 本地环境含真实 SiliconFlow API Key → 压测前**备份并清除** `bempdiff-web.properties` 的 `aiApiKey`，`aiEnabled=false`、`projectContextEnabled=false`。**AI 端点全程走离线 MockAiAnalyzer，未产生任何真实 LLM 调用/成本**。压测后已还原原配置。 |

> 说明：`job-report`、`job-ai-analyze`、`job-ai-estimate` 在同一 job 二次调用时命中服务端 per-job 缓存（`aiWorkCache`/`aiEstimateCache`），呈现毫秒级；**首次调用**（新 job 首次反编译）才是其真实延迟，报告中以 `job-ai-classify`/`job-export`（每次都触发反编译）为基准代表真实重计算成本。

---

## 3. 端点覆盖（被测全集）

`POST /api/session/compare`、`GET /api/job/{id}/status`、`GET /api/job/{id}/unpack-report`、`POST /api/job/{id}/report`、`POST /api/job/{id}/export`、`POST /api/job/{id}/ai-analyze`(SSE)、`POST /api/job/{id}/ai-classify`、`POST /api/job/{id}/ai-estimate`、`GET /api/entry/children`、`GET /api/entry/recursive`、`GET /api/entry/decompile`、`GET/PUT /api/config`、`POST /api/ai/test`、`POST /api/ai/models`、`GET /api/ai/context`、`GET /`（静态托管）。（`/api/file` 为包对比模式返回 400 的业务预期，不在压力路径压测。）

---

## 4. 基准（单用户 smoke）—— 稳态单请求延迟

| 端点 | p50(ms) | p95(ms) | 说明 |
|---|---|---|---|
| ai-context | 2.4 | 25.9 | 读内存上下文索引（小目录） |
| compare-submit | 3.1 | 819.0 | 提交即返回 jobId，p95 个别因后台 parse 争用上扬 |
| config-get | 2.7 | 26.9 | 内存读 |
| **entry-children** | 637 | 3342 | 归档展开，每次请求重新读 zip 解包，无缓存 |
| **entry-recursive** | 639 | 3089 | 递归解包，同理较慢 |
| job-status | 3.0 | 21.6 | 内存读耗时快（未 DONE 时更轻；DONE 返回全树） |
| job-unpack-report | 2.8 | 6.0 | 读报告 |
| static-index | 57.8 | 285.6 | 读入磁盘文件副本再回传，不入内存缓存 |
| ai-test/models(offline) | 2.7–2.9 | 4–21 | 快速失败路径 |
| config-put | 178.5 | 244.2 | 全量重写 properties 落盘 |
| entry-decompile | 397.6 | 646.6 | 单 class 反编译（CFR 子进程启动 + 计算） |
| job-ai-analyze(SSE) | 2.1 | 2.7 | 命中 aiWorkCache |
| job-ai-estimate | 2.5 | 26.1 | 命中 aiEstimateCache（首次>30s 冷启动已消除） |
| job-report | 3.1 | 26.5 | 命中缓存 |
| **job-ai-classify** | 6034 | 9638 | 全量反编译全部变更文件 + AI stageB |
| **job-export** | 6450 | 7521 | 反编译 + 打包导出 zip |

**结论**：轻端点单请求毫秒级；重端点基准即达 **6s+**（真实反编译成本）。

---

## 5. 压力（并发）

### 5.1 轻/读端点（20 并发，60s）

| 端点 | 请求 | TPS/s | p50 | p95 | p99 | 错误率 |
|---|---|---|---|---|---|---|
| ai-context | 600 | 9.9 | 2.4 | 21.8 | 39.5 | 0 |
| compare-submit | 600 | 9.9 | 2.9 | 27.1 | 164.4 | 0 |
| config-get | 600 | 9.9 | 2.6 | 23.3 | 44.7 | 0 |
| entry-children | 600 | 9.9 | 868 | 1512 | 1760 | 0 |
| entry-recursive | 582 | 9.6 | 884 | 1523 | 1702 | 0 |
| job-status | 600 | 9.9 | 2.6 | 23.9 | 67.5 | 0 |
| job-unpack-report | 600 | 9.9 | 2.5 | 20.5 | 30.8 | 0 |
| static-index | 580 | 9.5 | 97.8 | 246.7 | 412.7 | 0 |
| **合计** | **4762** | **78** | 4.2(总) | 1112(总) | 1540(总) | **0** |

- 轻端点延迟随并发**几乎不劣化**（p95 与基准持平），虚拟线程 + 无锁内存 job 模型并发扩展良好。
- 唯一明显劣化点是 `entry-children/recursive`：并发下 p50 稳定在 ~870–890ms（受归档 IO 约束），是轻端点中唯一的 IO 型慢点。

### 5.2 重计算端点（5 并发，90s）

| 端点 | 请求 | TPS/s | p50 | p95 | p99 | 错误率 |
|---|---|---|---|---|---|---|
| ai-test/models(offline) | 60 | 0.6 | 2.3–2.5 | 6–26 | — | 0 |
| config-put | 60 | 0.6 | 92.5 | 261 | 383 | 0 |
| entry-decompile | 61 | 0.6 | 298 | 518 | 616 | 0 |
| job-ai-analyze(SSE) | 61 | 0.6 | 2.0 | 22.1 | 38.3 | 0 |
| job-ai-estimate | 61 | 0.6 | 2.3 | 19.7 | 1629 | 0 |
| job-report | 65 | 0.7 | 2.4 | 946 | 1627 | 0 |
| **job-ai-classify** | 65 | 0.7 | **3044** | **6283** | **7964** | 0 |
| **job-export** | 65 | 0.7 | **3035** | **5842** | **6249** | 0 |
| **合计** | **558** | **6.2** | 11(总) | 4227(总) | 6219(总) | **0** |

- 重端点吞吐被单请求长延时天然压制（重计算本质，非并发缺陷）：5 线程下每端点仅 ~0.7/s。
- **并发下无明显竞态/错误**：`config-put`（并发写盘）60 次全部成功无互锁异常。

---

## 6. 稳定性（持续负载 soak）

- 轻端点 `soak_api`（8T，120s）4194 请求 / 0 错误：p50 3.4ms，p95 1.09s，p99 1.36s，与 load 档基本持平——**无随时间劣化**。
- 重端点 `soak_heavy`（2T，120s）220 请求 / 0 错误：p50 7.3ms（大部分命中缓存），重端点 `job-ai-classify`/`job-export` p95 6.2–7.9s。
- 资源随时间平稳：全程工作集 WS 稳定在 **712–719MB**，私有内存 **1171–1176MB**，无短期泄漏（见 §7）。

---

## 7. 资源占用（重计算高峰采样后端进程）

| 指标 | 平均 | 峰值 |
|---|---|---|
| 工作集 WS | 712 MB | 719 MB |
| 私有内存 | 1171 MB | 1176 MB |
| CPU | — | **38.2%（单核）** |
| 线程/句柄 | — | 未见异常增长 |

- **CPU 峰值仅 ~38% 单核**：反编译等重计算期间 CPU 未饱和，印证瓶颈在 **CFR 子进程启动 + 磁盘 IO 串行**，而非计算密集。
- 常驻内存 ~1.17GB 对本地工具偏大：包含 JVM 堆 + 所有累积 job 快照 + per-job 反编译缓存。

---

## 8. 瓶颈与潜在风险

1. **重计算端点无跨 job 内容复用（P0，最大收益）**：`job-ai-classify` / `job-export` / 新 job 首调 report/analyze 每次都全量反编译全部变更 class 并展开归档，无按**内容指纹(sha256)**的进程级缓存。即使两次提交同一包对上，仍重复计算。→ 实测 p50 3–6s、p95 6–7s 的根因。
2. **`entry-children` / `recursive` 无结果缓存（P0）**：每次请求重新读归档 zip、解包、计算子项，并发下 p50 ~870ms、p95 ~1.5s。同一归档反复展开，重复 IO。
3. **静态资源入响应不缓存（P1）**：`static-index` 每次 `readAll` 整文件再回传（p50 ~98ms、p95 ~247ms），未利用磁盘不可变资产的内存缓存与浏览器缓存头。
4. **反编译无并发闸门/有界池（P1）**：真实 `BempServer` 未像 perf-harness 那样用全局有界反编译池 + 背压；高并发下每个请求独立起 CFR 子进程串行反编译，子进程数可能随请求数膨胀，放大内存峰值与 IO。
5. **`JobStore` 无上限（P1，稳定性风险）**：`compare` 提交的 job 对象（含两侧快照 + 缓存）永不淘汰，长时 soak/大量提交下堆内存无限增长（本次 1.17GB）；`aiWorkCache` 虽有 16 上限但 `job` 本体无清理。
6. **`config-put` 全量重写落盘（P2）**：每次 PUT 序列化整份配置并重写文件（p50 92–178ms），高并发写存在重复 IO。
7. **AI 首次冷启动**：新 job 首次 report/analyze/estimate 因全量反编译出现 >10s（estimate 曾实测 >30s），`aiEstimateCache` 首次未命中尤其明显。

> 注：并发下未观察到 5xx / 锁冲突 / 内存溢出——**无竞态正确性 bug**；上述均为容量/成本类优化空间。

---

## 9. 优化建议（按优先级排序 + 预期收益）

> 就用户点名的五类手段逐项给结论（本系统特殊性：内存态、无数据库、单机）：

**（一）数据库查询优化 — 不适用**
BempDiff 后端为纯内存态（`JobStore` 用 `ConcurrentHashMap`，job 与比对结果不落库、报表无 DB），**无数据库查询可优化**。等价风险落在「JobStore 内存累积」（见建议 D）。

**（二）缓存策略调整 — P0，收益最大**
| 建议 | 落点 | 预期收益 |
|---|---|---|
| A. **按内容指纹(sha256)的反编译缓存**：新 job 提交包对时对两侧包生成指纹，反编译结果按 `sha256(class字节+配对)` 缓存 → 相同内容跨 job 复用 | `Decompiler` 层 + 进程级 LRU | 重计算端点延迟 **-5×~-50×**（历史同类实测 47–51×）；`job-ai-classify`/`export` 从秒级降到缓存命中毫秒级 |
| B. **归档展开结果缓存**：按 `jobId+归档key+忽略规则` 缓存 `entry-children/recursive` 结果，job 内幂等 | `ArchiveTree.computeChildren/recursiveUnpack` per-job | `entry` 端点 p95 **~1.5s → 毫秒级** |
| C. **静态资源内存缓存 + Cache-Control**：不可变 `assets/vendor` 物化到内存 map + 304 协商 | `sendBytesInline` / `mimeOf` | `static-index` p50 **~98ms → <5ms**，重复加载不再读盘 |

**（三）连接池 / 并发模型 — P1，吞吐与稳定性**
| 建议 | 落点 | 预期收益 |
|---|---|---|
| D. **反编译有界池 + 全局并发闸门**：借鉴 perf-harness 的 `WORK_POOL`(有界) + `PARSE_GATE`(信令量背压)，把「CFR 子进程数」钳制在 CPU/IO 可承受范围 | `BempServer` 反编译/解析入口 | 防高并发子进程爆炸、内存峰值回落；高并发尾延迟可降（历史同类 100 线程 -25% 尾延迟） |
| E. **`JobStore` 淘汰上限**（按 LRU/时间淘汰已完成 job，AI 缓存上限已有 16 可沿用该思想） | `JobStore` | 阻断长 soak 下私有内存 1.17GB 持续累积，**消除稳定性/OOM 风险** |
| F. **`config-put` 异步合并写 + 就地更新** | `handleConfig` | `config-put` p50 **~90–178ms → 个位数 ms**；避免重复全量重写 |
| G. HTTP 层保持虚拟线程（已验证良好），无需改 | `BempServer.setExecutor` | —（经验证并发扩展良好，不作变更） |

**（四）代码逻辑精简 — P2，可及收益**
- `buildClassMap`/`buildTextMap`/`getAiWork` 三入口重复 build 反编译集，已用 per-job `aiWorkCache` 合并一次；进一步**与建议 A 的进程级指纹缓存叠加**，彻底消除同内容跨 job 的重复反编译。
- `finalize_url`/`pickTextOrArray` 等既有正确性约束保持不动（勿回退）。

**（五）负载均衡 / 横向扩展 — 本机场景有限适用**
单用户本地桌面后端 + 虚拟线程单进程模型，**横向负载均衡不适用**（无多实例需求）。若未来作为局域网多人服务部署，才需：多实例 + 会话亲和（`JobStore` 现为内存态，需外置到共享存储）+ 前置均衡。此处仅记录，不作为本期改动。

**优先级排序结论**：**P0 = A、B、C**（缓存，点降低最大、纯增量）；**P1 = D、E、F**（并发闸门 + 内存上限 + 写优化）；**P2 = G 验证 + 代码精简**。先 P0 缓存、再 P1 闸门/上限，符合「先降重复计算、再防资源膨胀」的最优次序。预期整体：重计算端点延迟下降一个数量级、内存有界、静态/条目读取近常量时间。

---

## 10. 副作用与清理

- 压测前后备份/还原了 `~/.bempdiff/bempdiff-web.properties`（含原真实 API Key，压测期已清除并置离线 Mock，**未产生真实 LLM 调用**）。
- 测试期间累积的 job 仅存在于压测用后端进程内存，后端已停，无需清理 DB。
- 产物：`output/results/`（jsonl + csv + 两份报告）、`perf_bemp/`（配置、JMX、采样脚本）供复测复现。