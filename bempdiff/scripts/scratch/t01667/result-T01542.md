# T01542 处理结果：一期遗留 GUI 任务真机复验（2026-10-10，Agent 沙箱 CDP 驱动）

## 复验方式（与「需真机 GUI、沙箱不可替代」预设的偏差说明）

- 被测对象：`release/win-unpacked/`（BempDiff **0.1.2026100902 解包版**，与安装版同源同结构、免安装运行；代码比 0901 安装版更新，含 T01670 构建修复）。
- 驱动方式：Electron `--remote-debugging-port=9222` + Playwright `connectOverCDP`，全部判定基于**真实运行的应用**（真实渲染、真实 sidecar、真实磁盘配置 `~/.bempdiff/bempdiff-web.properties`），非 mock、非源码静态检查。
- 脚本与截图证据：`bempdiff/scripts/scratch/t01667/s-*.mjs` + `out/`。

## 复验结果逐项

| 原单 | 判据 | 结果 | 证据要点 |
|---|---|---|---|
| **T00843 断点续录** | 改字段不保存关闭→重开恢复；保存后重开无旧草稿遮蔽 | **PASS** | 草稿写入 localStorage `bempdiff:config:draft`（仅未保存字段）；重开恢复临时值 ✓；保存后服务端 aiBaseUrl 变更 ✓ 且草稿清空 ✓；全程配置逐字节还原（API diff={} 复核） |
| **T00844 导出/导入** | 导出合法；非法文件报错；导入恢复+表单同步；未记住 Key 不含明文 | **PASS** | 导出 `app=bempdiff, kind=config, 33 键` ✓；`{hello:world}` →「不是合法的 BempDiff 配置导出文件」✓；乱文本 → JSON 解析报错 ✓；导入 topK=33 → 服务端生效 ✓；取消「记住」+假 Key 保存 → 导出 `aiApiKey:""` 不含明文 ✓。附带**磁盘级**验证 T01615：取消记住+二次确认保存后 `bempdiff-web.properties` 中 `aiApiKey` 行被删除 |
| **T00862 向导引导** | 默认无按钮；开始引导→播放+按钮；关闭→消失；重启保持 | **PASS** | 默认 key='off' 且左下角无按钮 ✓；「开始引导」→ 浮层播放（可跳过）+ key='on' ✓；再点开关 → key='off' ✓；**真实重启应用后** key='on' 且左下角出现「打开功能引导」按钮 ✓ |
| **T00876 跳转下一处差异** | 大文件反复单击即精确跳下一差异行 | **PASS** | 造 12000 行/全文件差异夹具（counter 23702 行）；连点 12 次 counter **12/12 次单击 +1**、`.diff-pane` 滚动 **12/12 跟随推进**；当前行带 `current flash` 反馈（viewport y=236/632 居中带上部）；初始 0→10 次亦逐次 +1（首屏内不需滚动属正常） |
| **T00926 关闭前任务检测** | RUNNING 关窗弹确认；取消→窗口保持比对继续；强制→关闭无二次弹窗；无任务直接关 | **PASS** | 50000 条目比对 RUNNING（phase=diffing）时 WM_CLOSE → 原生框「比对仍在进行」出现（含「取消关闭/强制关闭」按钮）✓；对话框 WM_CLOSE（=取消关闭）→ 进程存活、任务推进至 diffing ✓；「强制关闭」BM_CLICK → 进程/侧车清零、端口释放、**无二次弹窗** ✓；无任务时关窗直接放行 ✓（沙箱实测） |
| **T00638 老包识别** | 单文件包可正常识别；「下拉形态」问题不复现 | **PASS** | 包模式（war/jar 双按钮并列，无下拉形态）下 sample war 与 big zip 均正常识别出包并完成比对；工具栏为 T00406 定型的「包/目录」两按钮形态 |
| **T00845 全屏/编辑** | DiffView 全屏查看/编辑入口可用 | **PASS** | 「专注对比」进入（树面板隐藏）→ Esc 退出（树面板恢复）✓；编辑器式光标定位（点击代码行 → `code-caret` 落下）✓ |
| **T00877 一键更新** | 带 .exe Release 显示「立即更新」；无 .exe 不显示 | **部分通过** | 「立即更新」按钮在最新 Release（0901，含 .exe）存在时正常显示 ✓。**未测**：无 .exe 资产的 Release 是否隐藏按钮（需发布一个无 .exe 的 Release 造负样本）。另观察项：当前版本(0902)>最新 Release(0901) 时绿色「已是最新」横幅与「立即更新」按钮**同时显示**（点击会装回旧版）——见派生单 T01675 |

## 已置 verified 的原单

T00843 / T00844 / T00862 / T00876 / T00926 / T00638 / T00845 → verified:true（本单回传后逐单置位）。
T00877 维持 done 未验证（负样本分支未测）。

## 本次回写

- 派生单 T01675【AI回写待审核】关于页「已是最新」状态仍显示「立即更新」（点击会降级安装旧版），来源 T01542/T00877。
- 复验脚本入库：`bempdiff/scripts/scratch/t01667/`（CDP 驱动族 s-*.mjs、helpers.mjs、drive_dialog.py、config 快照）。

## 下一步建议

- T00877 收口：发布一个仅含 blockmap（无 .exe）的测试 Release 验证按钮隐藏后即可置 verified（未建单，属 T00877 自身待办，见上）。
- （仅观察不建单）沙箱内 CDP 复验手法已可复用：`win-unpacked` + `--remote-debugging-port=9222` + Playwright connectOverCDP；原生 MessageBox 用 ctypes WM_CLOSE/BM_CLICK 驱动。注意渲染层 `window.close()` 会绕过 BrowserWindow 'close' 守卫（CDP 伪象，非产品缺陷）。
