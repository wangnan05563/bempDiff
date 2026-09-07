# BempDiff 更新功能 · 技术说明

本文档说明「关于 → 检查更新」所依赖的组件、接口契约与认证方式，供后续维护与排查。

---

## 1. 依赖项

| 依赖 | 说明 |
| --- | --- |
| JDK 标准库（`java.net` / `java.nio`） | 后端 `UpdateCheckService.java`，零第三方依赖 |
| GitHub API（REST，HTTPS 强制） | `https://api.github.com/repos/{owner}/{repo}/releases/latest` |
| 前端通信 | Web UI 通过 `api.checkUpdate()` 调后端 `/api/update/check`（同源，无需额外依赖） |

> 数据链路全程 **HTTPS**：`api.github.com`、后端到前端均走同一域下的接口（无明文传输）。

---

## 2. 认证方式

**强制约定：不硬编码任何凭证。令牌可从两个来源注入（优先级：显式配置 → 环境变量）。**

| 来源 | 说明 | 是否必填 |
| --- | --- | --- |
| 前端配置（推荐） | 应用「关于」页「GitHub 访问令牌」填写并保存到后端配置（`/api/config` 的 `githubToken`）；勾选「记住令牌」(`persistGithubToken=true`) 才明文落盘，默认不落盘仅存进程内存 | 否 |
| 环境变量 `GITHUB_TOKEN` | 显式令牌为空时的回落来源 | 否 |
| 环境变量 `BEMPDIFF_GITHUB_REPO` | 覆盖仓库地址，格式 `owner/repo`；非法格式则回落内置默认 | 否 |

- 令牌仅在 `UpdateCheckService` 请求头 `Authorization: Bearer <token>` 中使用，**不写日志、不在未开启「记住」时回显给前端**。
- 未设令牌走匿名访问：限 60 次/小时/IP。
- 请求携带 `User-Agent`、`Accept: application/vnd.github+json`、`X-GitHub-Api-Version: 2022-11-28`（GitHub 对缺失 User-Agent 返回 403）。

**请求超时**：连接 5s / 读取 8s。GitHub 不可达时优雅降级为 `ok=false + message`，不影响比对等其他功能。

**进程内短缓存（防限流）**：对同一 `repo + token` 的「latest release 拉取结果」缓存 5 分钟（键不同不命中；失败不缓存，可立即重试；`clearLatestCache()` 可强制清空）。开启缓存不被视为新请求，显著降低反复点检查/重开关于页触达限流的概率。缓存命中的响应 `cached=true`。

---

## 3. 接口契约

### `POST /api/update/check`

请求体（可选）：
```json
{ "current": "0.1.2026083102" }
```

响应体（HTTP 恒为 200，以 `ok` 字段区分成功/失败）：
```json
{
  "ok": true | false,
  "current": "当前版本号",
  "upToDate": true | false | null,
  "latest": { "tag": "v1.2.3", "name": "...", "url": "...", "publishedAt": "ISO8601", "body": "..." } | null,
  "repo": "owner/repo",
  "message": "用户可读提示",
  "cached": "是否命中进程内缓存",
  "lastError": "失败时的底层错误信息（仅失败时出现）"
}
```

字段语义：
- `upToDate = true`：当前 ≥ 最新；`false`：存在新版本；`null`：未比较（当前版本为空或仓库无 Release）。
- `latest = null` 且 `ok=true`：仓库尚无任何 Release。
- `cached = true`：本次结果来自 5 分钟进程内缓存（未发起新 API 请求）。
- `ok=false`：网络 / 限流 / 令牌 / 404 等问题，`message` 附带排查建议。

前端组件：`webui/src/components/About.vue`；接口封装：`webui/src/api/client.js` 的 `checkUpdate`；后端：`java_core/.../server/UpdateCheckService.java`、`BempServer.handleUpdateCheck`。

---

## 4. 版本比较逻辑

- 版本号来自安装包 `appVersion`（桌面壳注入 `window.bempdiff.appVersion`），缺失时回落前端 `APP_VERSION`。
- 对 tag 剥离前导 `v/ /V` 后，复用后端 `PackageVersion.compare`（数字分段语义化比较：`1.10 > 1.9`）。
- 判定：`compare(stripV(current), stripV(tag)) >= 0` → 已最新；否则存在新版本。

---

## 5. 前端行为

- 打开「关于」tab 自动检查一次（自动检测），并提供「检查更新」按钮手动重查。
- 状态展示：检查中（按钮转圈禁用，防重复请求）→ 成功/失败均以后端 `message` 为准，「有更新」时展示 当前 vs 最新 版本对比与下载链接及发布说明。
- 单测：`webui/src/__tests__/about.spec.js`（mock `api.checkUpdate`，覆盖自动/手动/有更新/失败降级/指引展开）。

---

## 6. 维护提示

- 变更「检查更新」后端时，保持响应字段结构稳定（前端按 `ok/upToDate/latest/repo/message` 消费）。
- 若要支持超过 60 次/小时的匿名查询，务必配置 `GITHUB_TOKEN`。
- 契约有后端单测守护：`UpdateCheckServiceContractTest`（`build_and_test.ps1` 全量套件）。它通过反射把 `UpdateCheckService.API_BASE` 指向进程内假 GitHub 服务，覆盖有新版/已最新/无 Release/HTTP 500/限流 403 等场景，**不依赖外网**；因此 `API_BASE` 被设计为 `volatile` 可覆盖（生产恒为 `https://api.github.com/repos/`）。改动契约时需同步更新该测试。