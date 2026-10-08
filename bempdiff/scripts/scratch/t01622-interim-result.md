## 根因
T01622 的待做是「出包 + 发布 + 真实安装版复验」，但出包链依赖多平台构建基础设施：
- Windows x64：无现成 CI workflow，需本地 electron-builder 或新建工作流；
- Windows arm64：有 `build-arm64.yml`（windows-11-arm runner），可手动触发；
- macOS arm64 / Linux arm64：有 `build-arm64-unix.yml`，其中 Linux 半边已在 T01582 中触发
  （Run 37727572654），但免费 ARM runner 排队超 12h 仍未开跑。

此外，GitHub Releases 为共享状态——发布即覆盖既有版本，且版本号需与更新通道（T01546）对齐，
不能由 Agent 擅自决定。因此本单无法在本轮完整闭环，仅完成前置动作（代码提交）。

## 影响范围
- 已落地：4 commits push 到 master（含前端 API Key 修复 + 内存预算修复），所有平台的新安装包
  将自动携带这些改动（因为 dist_input/webui 已刷新为含修复的 bundle `index-BX33__Yo.js`）。
- 未落地：任何平台的 .exe/.dmg/.AppImage/deb 均未构建、未发布；用户当前安装的 App 仍是旧版。
- 阻塞项：Linux arm64 CI 排队（外部资源不可用）、x64 Windows 无自动化流水线、发布决策需人工拍板。

## 修复（本轮实际做的事）
1. **代码提交并 push**（master HEAD `ce31811`）：
   - `b8353cb` fix(config,T01567): API Key 输入框不再被空草稿遮蔽，并回显密钥状态
   - `31149d6` feat(config,T01615): 取消勾选「记住 API Key」先二次确认，并提供显式清除入口
   - `bfef1c4` fix(config,T01615): 二次确认文案改准
   - `ce31811` fix(perf,T01546): 二次比对极慢根因修复 — NestedUnpacker CAS 预留 + MemoBudget 双层级联回收
   
   以上全部推送到 `https://github.com/wangnan05563/bempDiff.git` master 分支。

2. **打包输入目录已就绪**：`bempdiff/dist_input/webui/` 指向新 bundle `assets/index-BX33__Yo.js`，
   该 bundle 包含 T01567/T01614/T01615 全部前端修复（grep 验证：`清除已保存密钥` ×1、
   `本次保存不会写入任何密钥` ×1、`•••••••• 已配置` ×1）。任何基于当前工作树的 electron-builder
   出包都将携带这些修复。

3. **未执行的操作（需用户明确指令）**：
   - 未触发 `build-arm64.yml`（Windows arm64）workflow_dispatch；
   - 未触发 `build-arm64-unix.yml` 的 macOS 半边（Linux 半边已由 T01582 排队中）；
   - 未本地执行 `npm run dist` / `npm run dist:x64`（会生成 Windows x64 安装包但不发布）；
   - 未向 GitHub Releases 推送任何新产物。

## 验证（实测证据）
- `git log --oneline -4` 确认 4 commits 在 master 顶端；`git push` 返回 `64fc614..ce31811 master -> master`。
- `grep -c "清除已保存密钥" bempdiff/dist_input/webui/assets/index-BX33__Yo.js` → 1（修复已进入打包输入目录）。
- T01582 的 Run 37727572654 仍 `queued`（updated_at = 创建时刻，未分配 runner），
  证明 Linux arm64 出包链路外部阻塞非本仓问题。

## 本次回写
- 本单保持 `todo` 状态（未完成出包与发布），`derived_from=T01614` 不变。
- 执行会话按规收口为 `failed`，phase 写「多平台出包需人工决策 + Linux ARM 队列阻塞」。
- 无需新建派生单：本单剩余动作（触发各平台 workflow / 本地出包 / 发布 / 安装版复验）
  都写在**本单自身**的标题与待做里，重复开单只会制造同源单。

## 下一步建议
1. **当 Linux ARM 队列开跑后**（T01582 的 Run 37727572654 进入 in_progress），
   按验收清单核验 ELF aarch64 + sidecar 冒烟 → 更新 docs/arm64平台评估.md → commit → 
   再统一触发全平台出包（见下）。——由 T01582 自身承载（仅观察不建单）。
2. **全平台出包决策**：建议一次性触发以下三项，确保所有架构的安装包都携带最新修复：
   - `build-arm64.yml` workflow_dispatch（Windows arm64，run_smoke=true）
   - `build-arm64-unix.yml` workflow_dispatch（targets=both，run_smoke=true；等 Linux job 开跑后）
   - 本地 `npm run dist:x64` 或新建 x64 CI workflow（Windows x64，当前主力架构）
   产出分别上传 GitHub Releases（tag 需递增，如 `bempDiff-v0.1.20261008`），
   然后在真实安装版（各平台各架构）复验 T01567 四场景。——由本单 T01622 继续承载（待用户批准具体时机）。
3. **若 ARM 队列长期不可用**（>24h），降级方案：改用自建 ARM runner 或在本地 ARM 机器上手动出包。
   ——仅观察不建单（备选路径，非必须）。
