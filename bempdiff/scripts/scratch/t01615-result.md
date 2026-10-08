## 根因
后端语义本身是有意设计，但前端把它当成了「一个无害的复选框」：`ServerConfig.toProperties()` 全量重建
properties，仅在 `persistApiKey==true && 密钥非空` 时写 `aiApiKey` 行——所以「取消勾选记住 + 保存」
= **删除磁盘密钥**。而默认安全模式下 GET 只在 `persistApiKey==true` 时回显明文，删完框里就空了，
既看不到旧值也没有任何提示，只能凭记忆重填（T01567 用户现场丢 Key 正是这条路径：磁盘残留
`persistApiKey=true` 而 `aiApiKey` 行缺失）。缺口有三：无二次确认、无显式清除入口、后果提示只有一条会被
覆盖的 toast。

## 影响范围
- 前端配置中心（API Key 行 + 「记住 API Key」复选框 + footer 保存）：新增拦截与常驻提示，**不改后端语义**。
- 后端 `ServerConfig` 行为零改动（本次只补测试固化既有语义），不影响比对/AI 链路。
- 交互口径变化：以前「取消记住 + 保存」一次点击即生效，现在需要两步（确认条）；「清除已保存密钥」成为显式入口。
- 交付新鲜度：改动全部在前端源码，需随下次安装包发布才进入桌面 App（与 T01614 同一条链，已由 T01622 承载）。

## 修复（绝对路径与改动点）
1. `D:\code\otherProjects\18_comparePakage\bempdiff\webui\src\components\ConfigDialog.vue`
   - 新增密钥删除风险判定：`savedKeyOnDisk`（`state.config.hasApiKey && persistApiKey===true`）、
     `explicitClear`、`clearKeyPending`、`wipeRisk = form.persistApiKey===false && (savedKeyOnDisk || explicitClear)`。
   - `onSave()`：命中 `wipeRisk` 且未确认 → 置 `clearKeyPending=true` + warning toast 后**直接 return，不发 PUT**；
     用户在确认条点「确认清除并保存」后同一 PUT 才落地，保存成功即复位两个标志位。
   - `apiKeyNoWriteHint` 常驻状态行（`data-testid="apikey-nowrite"`）：风险态显示「将删除已落盘的 API Key…」，
     「勾选记住但框空且后端无 Key」显示「本次保存不会写入任何密钥」——补齐 toast 被覆盖后无人看见的盲区。
   - API Key 行新增显式入口 `data-testid="clear-key-btn"`「清除已保存密钥」（仅 `hasApiKey` 时出现）：
     置 `aiApiKey='' / persistApiKey=false` 并直接拉起确认条，替代原来「取消勾选=隐式删除」的语义。
   - 确认条 `data-testid="clear-key-confirm"` 放在 footer 上方（跨 Tab 常驻可见），含
     `clear-key-ok` / `clear-key-cancel`；「取消」`cancelClearKey()` 把复选框回滚为记住态且不发 PUT。
   - 弹窗打开时复位 `clearKeyPending/explicitClear`；`watch(form.persistApiKey)` 重新勾选即撤销待确认态。
2. `D:\code\otherProjects\18_comparePakage\bempdiff\webui\src\lib\i18n.js`
   - 中英双写新增：`cfg.apiKey.state.willWipe`、`cfg.apiKey.clear`、`cfg.apiKey.clear.hint`、
     `cfg.apiKey.confirm.wipe`、`cfg.apiKey.confirm.ok`（复用既有 `common.cancel`）。
3. `D:\code\otherProjects\18_comparePakage\bempdiff\java_core\test\com\bempdiff\test\ServerConfigTest.java`
   - 新增 `testPersistOnWithEmptyKeyKeepsDiskApiKeyLine`：勾选记住 + `aiApiKey=""` 的 PUT 之后，
     重启读盘必须 `hasApiKey=true`、`toJson().aiApiKey` 仍为原值、properties 里 `aiApiKey=` 行仍在
     （该语义此前只由 `applyAiFields` 忽略空串隐式保证）。
4. `D:\code\otherProjects\18_comparePakage\bempdiff\webui\src\__tests__\config_clear_key.spec.js`（新增 6 例）
   - 覆盖：入口按钮出现 / 未确认不发 PUT、确认后 PUT 带 `persistApiKey:false` / 取消回滚勾选且不发 PUT /
     显式清除入口 → 确认后 PUT `persistApiKey:false` 且 `aiApiKey:''` / 空框勾选记住时常驻提示 /
     后端本无落盘密钥时**不误报**（直接保存）。
5. 提交：`31149d6`（实现）+ `bfef1c4`（确认文案改准——原文案称「磁盘/进程内都删且无法找回」，
   实际此刻内存密钥仍在，只有 properties 的 `aiApiKey` 行会消失）。本地 master 未 push（等用户决定）。

## 验证（实测证据）
- Java 全量：`bash bempdiff/java_core/build_and_test.sh` → **总计 456 通过 456 失败 0**（原 455，+1 即新用例）；
  单跑 `TestRunner com.bempdiff.test.ServerConfigTest` → 8 用例全通过，用例名 `testPersistOnWithEmptyKeyKeepsDiskApiKeyLine` 在列。
- webui 全量 vitest：**44 files / 362 tests 全通过**（原 43/356，+1 file +6 tests）；两份 API Key 相关 spec 单独复跑 10/10。
- **生产 bundle 真机 E2E**（不是 vite dev）：`npm run build` → 刷新 `bempdiff/dist_input/webui`
  （`index.html` 指向 `assets/index-BX33__Yo.js`）→ 隔离 sidecar `-Duser.home=<临时目录>`
  + `Main server --webroot bempdiff/dist_input/webui --port 18995`，浏览器逐步操作并读盘核验：

  | 步骤 | 实测 |
  |---|---|
  | 载入确认 | `script[src]` = `index-BX33__Yo.js`（确实是打包产物）；API Key 行出现「清除已保存密钥」按钮 |
  | 取消勾选「记住」 | 常驻黄字出现：`将删除已落盘的 API Key：取消「记住」后本次保存不写入密钥，properties 中的密钥行会被移除，误删只能凭记忆重填` |
  | 再勾选 | 黄字消失；再取消又出现（状态随模型走，无残留） |
  | 点「保存」（未确认） | 确认条出现（`确认清除 API Key？保存后 properties 中的密钥行会被删除（仅勾选「记住」且密钥非空才会写入）…`）；**PUT 未发出**：磁盘仍 `aiApiKey=` 行 1 条、`/api/config` 的 `persistApiKey` 仍为 `True` |
  | 点「取消」 | 复选框回到勾选态、确认条与黄字消失、磁盘密钥行仍 1 条（零副作用） |
  | 点「确认清除并保存」 | toast「配置已保存」；等异步合并落盘后读盘：`aiApiKey` 行数 **0**、`persistApiKey=false`；`/api/config` = `hasApiKey True / persist False / 不回显`（内存仍持有，重启即失效——与 `toProperties` 语义一致） |

- 卫生收口：隔离 sidecar（端口 18994/18995，PID 定向 taskkill）全部终止，临时 `user.home`（内含**假**密钥
  `sk-PRODCHECK-0123456789`）与 scratch 脚本/日志已删除；用户真实 `~/.bempdiff/bempdiff-web.properties` 全程未写。

## 本次回写
- 回传即由服务端自动整合回原单 **T01567**（复核 T01567 `handle_result` 已含本节，无重复）。
- 本单四项待做全部落地：①二次确认 ✅ ②状态行常驻「本次不写入/将删除」✅ ③显式「清除已保存密钥」入口（评估结论＝做，
  已实现并接入同一确认条）✅ ④后端断言 ✅。
- 验收第二条 `build_and_test.sh` 全绿 ✅；验收第一条「真实 UI 走完取消勾选→保存能看到确认框与后果说明」以**打包产物**实测达成 ✅。
- 唯一剩余动作仍是交付：新 bundle（`index-BX33__Yo.js`）已刷新进 `dist_input/webui`，但尚未出包发布——
  **不重复开单**，由 T01614 派生的 **T01622**（打包发布 + 真实安装版复验四场景）一并承载。

## 下一步建议
1. 批准并执行 T01622：出包（Windows + mac/linux arm64）→ 发布 → 真实安装版复验含本次确认条在内的交互——已建派生单 T01622。
2. 决定本地三个 commit（`b8353cb` / `31149d6` / `bfef1c4`）与未提交的内存预算修复是否 push / 随包发布——已建派生单 T01622（其待做第 1 条）。
