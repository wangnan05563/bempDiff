# T01726 处理结果：关于页「立即更新」按钮降级缺陷修复

## 根因（与派生单定位一致）

`webui/src/components/About.vue` L185：`<button v-if="result.latest.downloadUrl && canOneClick()">` —— 显隐只看「最新 Release 是否有下载 URL + 桌面壳能力」，未参考 `result.upToDate`。已最新用户点「立即更新」会把旧版安装回本机（降级）。

## 修复（提交本批）

`About.vue` 条件改为：

```
v-if="result.upToDate === false && result.latest.downloadUrl && canOneClick()"
```

仅**确有新版**（`upToDate === false`）时显示一键更新；`upToDate=true`（已最新）与 `upToDate=null`（版本未知）均不显示。「前往下载页」链接保留不动。

## 验证（三层，全绿）

1. **单测**：`about.spec.js` 新增 3 条护栏用例（有新版显示 / 已最新隐藏 / 未知隐藏且保留下载页链接）；About 共 12/12，**全量 vitest 44 文件 366/366 通过**。
2. **构建**：`vite build` 成功（bundle `index-B36DV2xP.js`）；`test_report_button.mjs` 9/9 PASS。
3. **真机 CDP 复验（win-unpacked 实例 + 路由拦截 mock /api/update/check）**：
   - upToDate=true：显示「当前已是最新版本」横幅、「立即更新」按钮 **0 个** ✅
   - upToDate=false：显示「发现新版本」横幅、「立即更新」按钮 **1 个** ✅
   - 验证脚本：`bempdiff/scripts/scratch/t01727/verify-t01726.mjs`

## 环境说明

- win-unpacked 的 `resources/bempdiff/dist_input/webui` 快照已替换为本次新构建（验证用，win-unpacked 可由 `npm run dist` 再生；已安装版不受影响，须重新出包安装才吃到修复）。

## 回写

缺陷已修复并真机验证，本单可置 verified。原观察来源 T01542（T00877 复验）/ T01506（R9）。
