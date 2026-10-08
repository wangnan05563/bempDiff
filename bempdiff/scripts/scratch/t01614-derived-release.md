来源：T01614

## 背景
T01614 已完成前端产物侧的工作：`npm run build` 重建 webui（新 bundle `assets/index-C9SGtf1_.js`，
旧安装包内为 `assets/index-Ccxs_3qY.js`），并刷新打包输入目录 `bempdiff/dist_input/webui/`；
用 `Main server --webroot dist_input/webui` 以「打包后 SPA + 同源 sidecar」形态实测了
T01567 的四个场景全部通过（见 T01614 回传）。

尚缺的是**交付环节**：新产物还在打包输入目录里，没有构建成 .exe/.dmg/.AppImage，
也没有发布，用户当前安装的 App 仍是旧 bundle（看不到密钥回显/状态行/空 Key 告警）。

## 待做
1. 经用户确认后执行完整出包链（Windows + mac/linux arm64）：
   注意 `dist_input` 组装会编译当前工作树——需先决定未提交的内存预算修复
   （`NestedUnpacker.java` / `BempServer.java` + 相关测试）是否一并提交，避免把 WIP 烘进发布包。
2. 发布安装包（GitHub Releases / 既有分发渠道），确认版本号与更新通道（T01546 更新链路）。
3. 在真实安装版（非 vite dev、非 `server --webroot`）配置中心复验四场景并留证：
   草稿不遮蔽持久化密钥、已配置占位（••••••••）、未配置/仅当前进程状态文案、
   勾选记住但空 Key 保存时的告警条。

## 验收
安装版 App 行为与源码一致；复验记录（截图或步骤）回填本单。
