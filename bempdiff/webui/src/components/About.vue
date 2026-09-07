<script setup>
// 配置中心「关于」tab：展示产品版本 + 连接 GitHub 检查最新 Release 并提供更新指引。
//
// 数据流（后端 UpdateCheckService，零依赖仅 JDK 标准库）：
//   api.checkUpdate({ current }) → POST /api/update/check
//   → GET https://api.github.com/repos/{owner}/{repo}/releases/latest（HTTPS 强制）
//   → { ok, current, upToDate(Boolean|null), latest:{tag,name,url,publishedAt,body}|null,
//       repo, message, lastError }
//
// 兼容与安全约定：
//  - 仓库地址不视为凭证，沿用后端内置默认 repo，可用环境变量 BEMPDIFF_GITHUB_REPO 覆盖，
//    前端不提供「改仓库」入口，避免凭证/地址误写；最终生效仓库以后端返回的 resp.repo 为准。
//  - 访问令牌从环境变量 GITHUB_TOKEN 读取，不外泄到前端。
//  - 检查失败永远优雅降级：展示后端 message（含限流/令牌/网络排查提示），不影响其他功能。
import { ref, onMounted } from 'vue'
import { resolveAppVersion } from '../lib/helpContent'
import { api } from '../api/client'
import { state, toast } from '../store'

const currentVersion = resolveAppVersion()
const checking = ref(false)      // 检查请求进行中（true 时按钮转圈并禁用，防重复请求）
const result = ref(null)         // 后端统一响应；null=未检查
const showGuide = ref(false)     // 「手动手工更新指引」折叠面板开关

// ---- GitHub 访问令牌配置（可选）----
// 落库 /api/config 的 githubToken 与 persistGithubToken。安全口径对齐 API Key：
// 仅在勾选「记住令牌」时明文落盘；默认不落盘，仅存活于后端进程内存（重启后需重填）。
const tokenInput = ref('')       // 令牌输入框（未持久化时留空，内存态密钥不回显）
const tokenVisible = ref(false)  // 明文/掩码切换
const persistToken = ref(false)  // 是否明文落盘
const savingToken = ref(false)   // 保存中（禁用按钮防重复提交）
const tokenMsg = ref('')         // 保存结果提示
// 会话内是否已有令牌（后端 GET 返回 hasGithubToken）
const hasToken = () => !!(state.config && (state.config.hasGithubToken || state.config.githubToken))

// 打开时预填：仅当已「记住」时后端才回显明文令牌，可预填进输入框；未记住则留空（不泄露内存态密钥）。
function seedToken() {
  const cfg = state.config || {}
  persistToken.value = !!cfg.persistGithubToken
  tokenInput.value = cfg.persistGithubToken && cfg.githubToken ? String(cfg.githubToken) : ''
}

// 保存令牌：仅当用户输入了非空新值才提交 githubToken（空值视为不修改，避免把会话内内存令牌误清空）；
// persistGithubToken 勾选状态始终提交。保存成功给后端已持有令牌，旧缓存按键差异自动失效。
async function onSaveToken() {
  if (savingToken.value) return
  savingToken.value = true
  tokenMsg.value = ''
  const tok = tokenInput.value.trim()
  const patch = { ...(state.config || {}), persistGithubToken: persistToken.value }
  if (tok) patch.githubToken = tok
  try {
    const resp = await api.putConfig(patch)
    state.config = resp
    tokenMsg.value = 'GitHub 令牌已保存；此后检查更新将携带该令牌（提升限流额度）。'
    toast('success', 'GitHub 令牌已保存')
  } catch (e) {
    tokenMsg.value = '保存失败：' + ((e && e.message) || String(e))
    toast('danger', tokenMsg.value)
  } finally {
    savingToken.value = false
  }
}

// GitHub 仓库 URL（后端返回的 owner/repo）。后端不可达时给一个可读的占位，避免空串。
const repoUrl = () => {
  const r = result.value && result.value.repo
  return r ? `https://github.com/${r}/releases` : 'https://github.com'
}

// GMT/UTC 时间串 → 本地可读日期（ISO 8601；解析失败原样返回，避免界面报错）
function fmtDate(s) {
  if (!s) return '—'
  const d = new Date(s)
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString()
}

// 执行检查：失败、成功都用后端 message 展示状态；因后端 message 已含排查提示，这里只轻提示一次 toast。
async function doCheck() {
  if (checking.value) return
  checking.value = true
  try {
    const r = await api.checkUpdate({ current: currentVersion })
    result.value = r
    toast('info', (r && r.message) || '检查完成')
  } catch (e) {
    // 网络层/后端 500 兜底：展示通用失败，不抛出打断交互
    result.value = { ok: false, message: '检查更新失败：' + ((e && e.message) || String(e)) }
    toast('danger', result.value.message)
  } finally {
    checking.value = false
  }
}

// 打开「关于」tab：预填令牌配置，然后自动检测一次（覆盖「自动检测」机制），随后可点按钮手动再查。
onMounted(() => { seedToken(); doCheck() })
</script>

<template>
  <div class="about">
    <!-- 顶部：产品名 + 当前版本号显示区域（与 GitHub 比较的基准） -->
    <div class="d-flex align-items-center gap-2 flex-wrap mb-2">
      <i class="bi bi-info-circle-fill text-primary"></i>
      <span class="fw-semibold">关于</span>
      <span class="badge text-bg-light border" title="当前产品版本">当前版本 v{{ currentVersion }}</span>
      <span class="text-secondary" style="font-size:.75rem">BempDiff 包/目录差异对比工具</span>
    </div>

    <!-- 检查更新：自动检测 + 手动触发按钮 -->
    <div class="card card-body py-2 mb-2" style="font-size:.78rem">
      <div class="d-flex align-items-center gap-2 flex-wrap">
        <span class="fw-semibold"><i class="bi bi-arrow-repeat"></i> 检查更新</span>
        <span class="text-secondary">连接 GitHub 获取最新 Release 版本并与之对比（HTTPS）</span>
        <button class="btn btn-primary btn-sm ms-auto" type="button" :disabled="checking" @click="doCheck"
                title="立即连接 GitHub 查询最新版本并对比；失败可重试">
          <i class="bi" :class="checking ? 'bi-arrow-repeat spinning' : 'bi-search'"></i>
          {{ checking ? '检查中…' : '检查更新' }}
        </button>
      </div>

      <!-- 检查结果区：错误 / 无 Release / 已最新 / 有新版 -->
      <template v-if="result">
        <!-- 失败 -->
        <div v-if="result.ok === false" class="alert alert-danger py-2 mt-2 mb-0 about-result" role="alert">
          <i class="bi bi-exclamation-triangle-fill me-1"></i>{{ result.message }}
        </div>
        <!-- 成功但仓库无 Release -->
        <div v-else-if="!result.latest" class="alert alert-secondary py-2 mt-2 mb-0 about-result" role="alert">
          <i class="bi bi-dash-circle me-1"></i>{{ result.message }}
        </div>
        <!-- 有最新版本时：版本对比区 + 下载/说明 -->
        <template v-else>
          <!-- 既有新版（需更新） -->
          <div v-if="result.upToDate === false"
               class="alert alert-warning py-2 mt-2 mb-2 about-result" role="alert">
            <i class="bi bi-exclamation-triangle-fill me-1"></i>发现新版本，建议立即更新
          </div>
          <!-- 已最新 / 当前未知 -->
          <div v-else class="alert alert-success py-2 mt-2 mb-2 about-result" role="alert">
            <i class="bi bi-check-circle-fill me-1"></i>当前已是最新版本，无需更新
          </div>

          <!-- 版本对比：当前 vs 最新 -->
          <div class="d-flex align-items-center gap-2 flex-wrap mb-2 about-compare" aria-label="版本对比">
            <span class="badge text-bg-light border about-ver">当前 v{{ currentVersion }}</span>
            <i class="bi bi-arrow-right text-secondary"></i>
            <span class="badge text-bg-primary border about-ver">{{ result.latest.tag || '最新版本' }}</span>
            <a v-if="result.latest.url" class="btn btn-outline-primary btn-sm" :href="result.latest.url"
               target="_blank" rel="noopener"
               title="前往 GitHub Release 页查看/下载安装包">
              <i class="bi bi-box-arrow-up-right"></i> 前往下载页
            </a>
          </div>
          <div v-if="result.latest.name || result.latest.publishedAt" class="text-secondary mb-1"
               style="font-size:.74rem">
            {{ result.latest.name }} · 发布于 {{ fmtDate(result.latest.publishedAt) }}
          </div>
          <!-- Release 说明（更新说明）预览 -->
          <div v-if="result.latest.body" class="border rounded p-2 mb-0 about-body" style="max-height:9rem;overflow:auto">
            <div class="text-secondary mb-1" style="font-size:.72rem">发布说明：</div>
            <pre class="mb-0" style="font-size:.74rem;white-space:pre-wrap;word-break:break-word">{{ result.latest.body }}</pre>
          </div>
        </template>
      </template>
      <!-- 进程内短缓存命中提示：避免反复检查触达 GitHub 匿名限流 -->
      <div v-if="result && result.cached" class="mt-2 text-secondary" style="font-size:.72rem">
        <i class="bi bi-database me-1"></i>最近 5 分钟内已检查过，本次为缓存结果（点「检查更新」可强制刷新）
      </div>
    </div>

    <!-- GitHub 访问令牌（配置中心可填）：规避匿名限流 / 支持私有仓库 -->
    <div class="card card-body py-2 mb-2" style="font-size:.78rem">
      <div class="d-flex align-items-center gap-2 flex-wrap">
        <span class="fw-semibold"><i class="bi bi-key"></i> GitHub 访问令牌</span>
        <span class="text-secondary">
          <template v-if="hasToken()">已配置</template>
          <template v-else>未配置（匿名，60 次/小时限流）</template>
        </span>
        <button class="btn btn-outline-secondary btn-sm ms-auto" type="button"
                :disabled="savingToken" @click="onSaveToken"
                title="保存后检查更新将携带该令牌（提升限流额度）；私有仓库必须配置只读令牌">
          <i class="bi" :class="savingToken ? 'bi-arrow-repeat spinning' : 'bi-check2'"></i>
          {{ savingToken ? '保存中…' : '保存' }}
        </button>
      </div>
      <div class="input-group input-group-sm mt-2">
        <input class="form-control" :type="tokenVisible ? 'text' : 'password'" v-model="tokenInput"
               placeholder="留空则不修改；本次会话已配置的令牌默认不回显" title="GitHub Personal Access Token（只读范围即可）">
        <button class="btn btn-outline-secondary" type="button" @click="tokenVisible = !tokenVisible"
                title="显示/隐藏令牌">
          <i class="bi" :class="tokenVisible ? 'bi-eye-slash' : 'bi-eye'"></i>
        </button>
      </div>
      <div class="form-check form-check-inline mt-2 mb-0">
        <input class="form-check-input" type="checkbox" id="aboutPersistToken" v-model="persistToken"
               title="勾选后令牌明文写入本地配置（重启后仍保留）；默认不落盘，仅存后端进程内存">
        <label class="form-check-label" for="aboutPersistToken">记住令牌（明文落盘，默认不选）</label>
      </div>
      <div v-if="tokenMsg" class="mt-2 mb-0" style="font-size:.72rem"
           :class="tokenMsg.startsWith('保存失败') ? 'text-danger' : 'text-success'">
        <i class="bi" :class="tokenMsg.startsWith('保存失败') ? 'bi-exclamation-triangle' : 'bi-check-circle'"></i> {{ tokenMsg }}
      </div>
    </div>

    <!-- 手动更新提示区域：GitHub 仓库地址 + 人工设置操作指引 -->
    <div class="card card-body py-2" style="font-size:.78rem">
      <div class="d-flex align-items-center gap-2 flex-wrap">
        <span class="fw-semibold"><i class="bi bi-github"></i> 手动更新</span>
        <span class="text-secondary ms-auto">GitHub 仓库：<a :href="repoUrl()" target="_blank" rel="noopener">{{ result && result.repo ? result.repo : '设置中…' }}</a></span>
        <button class="btn btn-outline-secondary btn-sm" type="button" @click="showGuide = !showGuide"
                :title="showGuide ? '收起操作指引' : '展开 GitHub 人工设置操作指引'">
          <i class="bi" :class="showGuide ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
          {{ showGuide ? '收起指引' : '人工设置指引' }}
        </button>
      </div>

      <template v-if="showGuide">
        <hr class="my-2">
        <ol class="mb-2 about-guide">
          <li>在 GitHub 上创建 <code>Settings → Releases</code> 发布记录（tag 建议形如 <code>v1.2.3</code>），本工具通过 Releases API 读取最新一条。</li>
          <li>仓库地址默认内置，可用环境变量 <code>BEMPDIFF_GITHUB_REPO</code>（格式 <code>owner/repo</code>）覆盖，前端无需改动。</li>
          <!-- 令牌配置：可在本页「GitHub 访问令牌」内填写保存（建议勾选「记住令牌」以便重启后保留），
               也可用环境变量 GITHUB_TOKEN；私有仓库必须配置只读令牌，令牌仅后端内存使用、默认不落盘。 -->
          <li>私有仓库或规避匿名限流：在本页「GitHub 访问令牌」填写保存，或用环境变量 <code>GITHUB_TOKEN</code>（只读范围）；令牌仅后端内存使用。</li>
          <li>版本对比采用同一语义：去掉 tag 前导 <code>v/</code> 后按语义化数字比较，当前版本由安装包 <code>appVersion</code> 提供。</li>
          <li>更新安装包：请在 Releases 页下载对应平台的安装包并覆盖安装，本工具不自动下载/执行安装程序。</li>
        </ol>
        <div class="alert alert-info py-2 mb-0"><i class="bi bi-info-circle-fill me-1"></i>完整的仓库权限 / Webhook / 版本校验与故障排查步骤，见随包附带的《GitHub 更新-人工操作手册》。</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.about { display: flex; flex-direction: column; gap: .5rem; min-height: 420px; }
/* 转圈（检查中） */
.spinning { animation: about-rotate 1s linear infinite; display: inline-block; }
@keyframes about-rotate { to { transform: rotate(360deg); } }
/* 版本徽标统一宽度，让对比行对齐 */
.about-ver { white-space: nowrap; }
.about-result { font-size: .78rem; }
.about-compare { gap: .5rem; }
.about-body pre { font-family: inherit; }
.about-guide { padding-left: 1.1rem; }
</style>