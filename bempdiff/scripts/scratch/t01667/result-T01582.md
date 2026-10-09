# T01582 处理结果（2026-10-10 01:3x，Agent 沙箱实测推进）

## 根因 / 背景

- linux arm64（ubuntu-24-04-arm 免费队列）长期排队：旧 run 37727572654 queued 26h+ 后被 concurrency 取消，linux arm64 从未有成功实测记录。

## 修复（本次实际完成项）

1. **重派已执行**：`build-arm64-unix.yml` workflow_dispatch，`targets=both`、`run_smoke=true` → run **37954555988**（https://github.com/wangnan05563/bempDiff/actions/runs/37954555988，head=8af752b）。
   - 该 run 与 T01667 的 macOS 半边共用（一次 both，两单共享）。
   - 触发前已确认无进行中 run（旧 run cancelled，无 concurrency 掐单风险）。
2. **凭证能力核实**：沙箱通过 GitHub PAT 可 dispatch/查询 Actions API（此前认为必须用户本机 gh，实际 curl + PAT 即可）；`git push` 仍被沙箱阻断（github.com:443 不通），但 8af752b 已在远端，无需 push。

## 验证（实测证据）

- dispatch 响应 HTTP 204；run 列表确认 37954555988 状态 `queued`（创建于 2026-10-09T15:49:14Z）。
- 截至本轮结束（2026-10-10 01:3x）仍 `queued`——ubuntu-24-04-arm 免费队列调度慢属已知风险（曾 26h+）。

## 成功判据（不变）

- linux-arm64 job `completed/success`；
- AppImage/deb 产物有效 + 包内 ELF machine = **0xB7 (aarch64)**（对应 0xAA64 的 ARM64 统一口径：ELF e_machine 0xB7）；
- sidecar 启动冒烟 18765 监听。

## 本次回写

- 无代码改动。

## 下一步建议

- run 37954555988 排队中，**不要重复 dispatch**（concurrency cancel-in-progress 会掐掉排队 job）；耐心轮询即可（Agent 沙箱下轮可代查状态）。
- 若 24h+ 仍未调度：可先 `-f targets=macos` 单独收 mac（T01667 口径），linux 改自建 runner 或择时再派。
- 成功后更新 `docs/arm64平台评估.md` 的 linux 结论（此为本单验收末条）。
- 本单维持 todo。

---

## 进展（2026-10-09 15:1x，Agent 沙箱核实）【保留原文】

- run 37727572654 已 **cancelled**（曾 queued 26h+，基于旧 commit 64fc6143，作废合理）。
- linux arm64 尚无任何成功实测记录 → 本单维持 todo。
- ⚠ ubuntu-24-04-arm 免费队列曾 26h+ 未调度；若再排队过久可先 `-f targets=macos` 单独收 mac，linux 另行安排（自建 runner / 择时再派）。
- 成功判据不变：linux-arm64 job success + deb/AppImage 内 ELF aarch64 + sidecar 冒烟 18765 监听。
