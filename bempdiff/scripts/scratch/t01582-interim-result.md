## 阶段结论（本轮未收口，保持待办）
本单唯一动作是「等 GitHub 免费 ARM runner 队列开跑后做实测核验」。
本轮（2026-10-08）12:26 触发 `build-arm64-unix.yml`（Run **37727572654**，`targets=linux`、`run_smoke=true`、
`head_sha=64fc614`），API 接受（HTTP 204）；此后 **排队约 8 小时仍未分配到 runner**：
`status=queued`、`conclusion=null`、`run_started_at` 与 `updated_at` 始终停在创建时刻 `2026-10-08T04:27:30Z`（UTC），
且 `?status=queued` 查询显示全仓只有这一条在排队——即积压来自 GitHub 侧免费 ARM 池全局拥堵，不是本仓并发/配置问题。
因此 linux arm64 至今**仍无一次成功实测**，本单不能标 done，也不应标 verified。

## 影响范围
- 零代码改动：本轮不含任何 src/ workflow 修改，无 commit。
- 受影响结论：`docs/arm64平台评估.md` 的 §5 linux 半边**继续留白**（不写未实测的成功结论）；
  §2.3「Linux arm64 风险低/优先级最低」的评估判断不变。
- 上游关系：T01478（arm64 平台评估）的 mac 半边已闭环并归档 T01546；linux 半边由本单继续挂账。

## 修复（本轮实际做的事 = 触发与取证，非改码）
1. 触发：`workflow_dispatch` → `https://api.github.com/repos/wangnan05563/bempDiff/actions/workflows/build-arm64-unix.yml/dispatches`
   （`ref=master`、`inputs={targets:'linux', run_smoke:'true'}`；token 经 `git credential fill` 临时取用，不落盘）。
2. **刻意不重触发**：workflow 的 `concurrency` 组带 `cancel-in-progress: true`，
   重触发会直接掐掉已在排队的 job，把等待时间清零（Run#1 37578153410 就是这么丢的）——
   所以正确做法只有轮询等待，不是反复 dispatch。
3. 轮询取证：`bempdiff/scripts/scratch/t01582-poll.sh`（75s 间隔 × 130 轮，每轮对空响应重试 3 次；
   `api.github.com` 常整段重置返回空体，单次空响应不能判失败）。
   本轮完整跑满 130 轮（15:25→19:08 全部 `queued`）后 GIVEUP，随即另起一轮继续后台轮询。
4. 待开跑后必须核验的验收清单（已在 workflow 内实现，逐条看 job 日志/产物即可，勿凭「绿了」放行）：
   - `linux-arm64` job（`ubuntu-24-04-arm`）架构自证步输出 `aarch64`；
   - `fetch-jre.mjs --os linux --arch arm64` 后 `file` 输出含 `ARM aarch64`；
   - 产物校验步对 `/opt/BempDiff/bempdiff` 与 `dist_input/jre/bin/java` 两处 ELF 均 grep 到 `aarch64`（machine 0xAA64）；
   - 冒烟步 `xvfb-run /opt/BempDiff/bempdiff --no-sandbox` 后 `lsof -nP -iTCP:18765 -sTCP:LISTEN` 有监听（sidecar 起得来）；
   - `upload-artifact` 产出 `BempDiff-linux-arm64`（AppImage + deb + blockmap）。
   以上全绿后才更新 `docs/arm64平台评估.md` 的 linux 结论、commit、再回传并把本单归并回 T01478。

## 验证（实测证据）
- `GET /repos/wangnan05563/bempDiff/actions/runs/37727572654` 于 19:0x 与 20:3x 两次采样均为
  `queued None`，`run_started_at=2026-10-08T04:27:30Z`、`updated_at=2026-10-08T04:27:30Z`（未被调度即不更新）。
- `GET .../actions/runs?status=queued` → `total_count=1`（仅本 run），排除「本仓把队列占满」的可能。
- 轮询日志留存：130 行全部 `queued None`，无 `failed`/`cancelled`，即排队未被 concurrency 抢占。
- 未达成项：linux 包内 ELF 校验与 18765 冒烟**本轮没有实测数据**，故不写入任何成功结论。

## 本次回写
- 本单状态保持 `todo`（未 done、未 verified），`derived_from=T01478` 不变；待实测通过后再走
  回传 → 归并回 T01478 → 归档的闭环。
- 执行会话按规收口为 `failed`，phase 写「免费 ARM runner 排队 ~8h 未开跑，本轮无法收口」——
  属外部资源不可用，非平台/代码报错。
- 无新增派生单：本单剩余动作（等开跑 + 按清单核验 + 更新 docs）与备选路径（自建 ARM runner）
  都已写在**本单自身**的标题与待做里，重复开单只会制造同源单。
- 观察项（仅观察不建单）：GitHub 免费 ARM 队列今天的等待量级已从「可超 1h」恶化到「8h+ 未开跑」，
  后续排期评估（T01478 的 arm64 优先级判断）宜按「ARM 实测不可当天承诺」这一新事实来定。
