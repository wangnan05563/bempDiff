## 根因
T01567 的修复只改了前端源码（`bempdiff/webui/src/components/ConfigDialog.vue`、`src/lib/i18n.js`，commit b8353cb），
而用户实际使用的是打包安装的桌面 App：安装包/打包输入目录 `bempdiff/dist_input/webui/` 里是**历史构建产物**，
`index.html` 仍指向旧 bundle `assets/index-Ccxs_3qY.js`。源码修复不重建、不刷新 dist_input、不出包，就不会进入桌面 App。
本单不含新缺陷修复，属于「产物新鲜度」闭环。

## 影响范围
- 交付物：`bempdiff/webui/dist/`（构建输出，gitignored）与 `bempdiff/dist_input/webui/`（electron-builder/Tauri 打包输入，gitignored）——
  两者均无版本控制痕迹，因此本单工作全部为**本地重建 + 实测复核**，无源码 commit。
- 影响用户：任何使用已安装 App（非 vite dev）的用户，在下次出包发布前仍看不到 API Key 回显/状态行/空 Key 告警。
- 不影响：后端 `ServerConfig` 持久化与回显语义（T01567 已证明链路透传正确，本次未触碰）。

## 修复（绝对路径与改动点）
1. 重建 webui：`npm run build --prefix bempdiff/webui`
   - 产物：`D:\code\otherProjects\18_comparePakage\bempdiff\webui\dist\assets\index-C9SGtf1_.js`（439.29 kB）
     与 `index-B9ixpr2u.css`；`dist/index.html` 引用更新为新哈希。
2. 刷新打包输入目录：`D:\code\otherProjects\18_comparePakage\bempdiff\dist_input\webui\`
   - 先把旧目录整体改名保留（`webui.old-20261008`，随后移到
     `bempdiff/scripts/scratch/t01614-old-bundle-backup/` 以免干扰出包扫描），
     再 `cp -a ../webui/dist webui` 全量替换，与既有出包脚本的 `rm -rf dist_input/webui && cp -a webui/dist dist_input/webui` 同构。
   - 替换后核对文件清单：新旧仅差 `index-C9SGtf1_.js` / `index-B9ixpr2u.css` 两个新增项，
     `splash.html`、`splash-logo.png`、`vendor/`（来自 `webui/public/`）全部保留，未丢资源。
3. 产物内容核验（确认修复真的进入了打包产物，而不只是哈希变了）：
   - 新 bundle 命中 `已配置，输入新值可覆盖` ×1、`本次保存不会写入任何密钥` ×1；
   - 旧 bundle（`index-Ccxs_3qY.js`）对同一文案命中 **0** 次 —— 说明旧安装包确实不含 T01567 修复，本单必要性得证。
4. 未做（按规矩需用户批准，已建派生单承接）：未执行 electron-builder 出包、未发布安装包、未改动用户已装 App。
   → 见派生单 **T01622**「打包发布含 API Key 修复（T01567/T01614）的桌面安装包，并在真实安装版复验四场景」。

## 验证（实测证据）
验证形态刻意选「打包后 SPA + 后端同源托管」而非 vite dev：用生产 dist_input 起 sidecar，
`com.bempdiff.Main server --webroot bempdiff/dist_input/webui --port <隔离端口>`（`-Duser.home` 指向临时目录，不触碰用户真实 `~/.bempdiff`）。
浏览器实测加载的确实是新 bundle（`document` 内 script src = `/assets/index-C9SGtf1_.js`），四场景复验：

| 场景（T01567） | 前置构造 | 实测结果 |
|---|---|---|
| ① 空草稿遮蔽持久化密钥 | 磁盘 `aiApiKey=sk-PRODCHECK-…` + `persistApiKey=true`；先向 `localStorage['bempdiff:config:draft']` 注入 `{"aiApiKey":""}` 再重开页面 | 输入框 value = `sk-PRODCHECK-0123456789`（修复前会被空草稿遮蔽成空），状态行不显示（表单有值时正确留空）✅ |
| ② 后端有 Key 但未落盘（仅进程内） | 磁盘 `aiApiKey=sk-MEMONLY-…` + `persistApiKey=false`；`/api/config` 返回 `hasApiKey=true, persistApiKey=false, aiApiKey=None` | 输入框空、placeholder = `•••••••• 已配置，输入新值可覆盖`，状态行 = `后端已持有密钥（未勾选「记住」，仅存于当前进程，重启后失效；输入新值可覆盖）`，「记住 API Key」复选框未勾 ✅ |
| ③ 完全未配置密钥 | 磁盘仅有 `persistApiKey=true`、无 `aiApiKey` 行；`/api/config` 返回 `hasApiKey=false` | 状态行 = `未配置密钥`，placeholder 回到 `本地模型可留空` ✅ |
| ④ 勾选记住却空 Key 保存 | 同③状态下点配置中心「保存」 | toast（`.toast-container`）= `已勾选「记住 API Key」，但密钥为空——本次保存不会写入任何密钥`，弹窗按设计保持打开 ✅ |

测试完成后已收口：隔离 sidecar 进程（PID 定向 taskkill，端口 18992/18993/18991）全部终止，
临时 `user.home` 目录（含两处**假**密钥 `sk-PRODCHECK-…` / `sk-MEMONLY-…`）与 server.log 已删除，
用户真实 `~/.bempdiff/bempdiff-web.properties` 全程只读未写。

补充：`bempdiff/webui/vite.config.js:28` 有 `emptyOutDir: false`（本机回收站钩子 fail-closed 的既有取舍），
故 `webui/dist/assets` 会累积历史哈希 bundle；本次已用 `index.html` 引用关系确认生效的是新 bundle，
残留文件对运行无影响（仅体积）。

## 本次回写
- 新建派生单 **T01622**（来源 T01614，项目 compare二期，标题带「AI回写待审核」前缀待批准）：
  承接「出包 + 发布 + 真实安装版复验」——这是本单唯一剩余动作，需用户确认后才执行
  （出包链会编译当前工作树，须先决定未提交的内存预算修复是否一并提交）。
- 旧打包产物备份移至 `bempdiff/scripts/scratch/t01614-old-bundle-backup/`，如需回退直接复制回去即可。
- 本单已 done 但 `verified=false`：验收口径写的是「真实安装版」，实测形态是「生产 bundle + sidecar 同源托管」，
  与安装版仅差 electron 外壳与发布环节；待 T01622 出包复验通过后再据实升为已验证。

## 下一步建议
1. 批准并执行 T01622（出包 → 发布 → 安装版复验四场景）——已建派生单 T01622。
2. 出包前先决定 `NestedUnpacker.java` / `BempServer.java` 内存预算修复（当前未提交）是否随包发布，
   以及本地 commit b8353cb 是否 push——已建派生单 T01622（同一单的第 1 条待做，不重复开单）。
