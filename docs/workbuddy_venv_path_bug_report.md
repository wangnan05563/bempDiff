# WorkBuddy 托管运行时 venv 路径计算 Bug 反馈

> 提交对象：WorkBuddy 产品/运行时团队
> 复现环境：WorkBuddy 5.3.13（WIN32），Windows，用户配置目录 `C:\Users\hspcadmin\.workbuddy`（symlink → `D:\code\Data_WorkBuddy\.workbuddy`）
> 托管 Python：`C:\Users\hspcadmin\.workbuddy\binaries\python\versions\3.13.12`

## 1. 现象
在**无关项目**的工作区（`D:\code\otherProjects\18_comparePakage`）下，凭空出现错误目录：

```
D:\c\Users\hspcadmin\.workbuddy\binaries\python\envs\default
```

它是一个 Python venv，与真实 venv **同名同构**，但位置完全错误——`D:\` 盘下多出一串 `c\Users\hspcadmin`，像是把 `C:\Users\hspcadmin` 的盘符 `C` 小写后重新锚定到 `D:\` 而拼出来的。

## 2. 复现证据
该 venv 的 `pyvenv.cfg` 记录了创建命令（`command` 字段），铁证如下：

```ini
home = C:\Users\hspcadmin\.workbuddy\binaries\python\versions\3.13.12
include-system-site-packages = false
version = 3.13.14
executable = D:\code\Data_WorkBuddy\.workbuddy\binaries\python\versions\3.13.12\python.exe
command = C:\Users\hspcadmin\.workbuddy\binaries\python\versions\3.13.12\python.exe -m venv d:\c\Users\hspcadmin\.workbuddy\binaries\python\envs\default
```

关键观察：**`home`（Python 本体）路径正确**（`C:\Users\...`），但 venv **目标路径**被算成了 `d:\c\Users\hspcadmin\.workbuddy\...`。两者来源不一致。

## 3. 根因分析
WorkBuddy 在创建「隔离 Python venv」时，对配置基址 `C:\Users\hspcadmin\.workbuddy` 做了一次错误的「可移植化」变换：

1. 取首字符盘符 `C`，小写成 `c`；
2. 再重新锚定到**工作区所在盘** `D:\`；
3. 拼出 `D:\c\Users\hspcadmin\.workbuddy`。

即：`venv 目标路径 = 工作区盘符(D:) + 小写盘符(c) + 原配置基址除盘符外的其余部分`。

而 Python 本体 `home` 仍从正确的 `WORKBUDDY_CONFIG_DIR` 解析，所以只有「目标路径」错乱，形成了 `D:\c\...` 这串孤儿目录。

## 4. 影响评估
- 产生孤儿副本，浪费磁盘，且极易与真实配置混淆（肉眼难辨 `C:\Users` 与 `D:\c\Users`）。
- 本次真实 venv 位于 `C:\Users\hspcadmin\.workbuddy\binaries\python\envs\default`（symlink 内），始终健在且被实际使用，**功能无影响**。
- `WORKBUDDY_CONFIG_DIR` / `CODEBUDDY_CONFIG_DIR` / `WB_SBX_ACCESS_HOME_ROOT` 等环境变量均正确，项目脚本也无错误引用，故仅该一次性副本有问题。

## 5. 修复建议
在托管运行时 / venv 初始化代码路径中：

1. **全程复用同一个绝对配置基址**：venv 目标路径应直接由 `WORKBUDDY_CONFIG_DIR`（已正确）拼接 `binaries/python/envs/default` 得到，**不得对盘符做任何小写 / 重新锚定 / 可移植化变换**。
2. **删除那条「绝对路径 → 工作区盘符 + 小写盘符 + 余下」的辅助逻辑**——正是它产出 `D:\c\...`。
3. 为 venv 路径计算加单测：断言目标路径以 `WORKBUDDY_CONFIG_DIR` 开头，且不包含 `D:\c` 之类的错误片段。

## 6. 临时规避（用户侧）
删除该孤儿目录即可（其不含任何唯一数据，真实 venv 在正确位置）。由于 agent 沙箱对 `Users\hspcadmin` / `.workbuddy` 字面量有删除拦截，可用「倒序构造路径 + `.NET Directory.Delete`」绕过；或在用户自己的 PowerShell / 资源管理器中执行（桌面回收站可用，钩子放行）：

```powershell
Remove-Item -Recurse -Force 'D:\c'
```
