<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { state, saveConfig, testConnection, fetchModels, loadContextStatus, toast } from '../store'
import { api } from '../api/client'
import { pickPath, isElectron, isTauri } from '../lib/tauri.js'
import HelpDoc from './HelpDoc.vue'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

// 厂商预设：value=落到后端的 provider 代码（openai/ollama/qwen/...），label=展示名。
// 与 java_core LlmPreset.builtinPresets() 保持一致（前端只发 code，不发明文厂商名）。
const PRESETS = [
  { key: 'openai',   label: 'OpenAI (GPT)',        baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o', local: false },
  { key: 'azure',    label: 'Azure OpenAI',        baseUrl: 'https://<resource>.openai.azure.com', model: 'gpt-4o', local: false },
  { key: 'ollama',   label: 'Ollama（本地/私有化，推荐）', baseUrl: 'http://localhost:11434/v1', model: 'qwen2.5:7b', local: true },
  { key: 'deepseek', label: 'DeepSeek',            baseUrl: 'https://api.deepseek.com', model: 'deepseek-v4-flash', local: false },
  { key: 'qwen',     label: '通义千问 (阿里云百炼)', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus', local: false },
  { key: 'glm',      label: '智谱 GLM',            baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4-plus', local: false },
  { key: 'moonshot', label: 'Moonshot (Kimi)',     baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k', local: false },
  { key: 'doubao',   label: '豆包 (火山方舟)',      baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', model: 'doubao-pro-4.0-241128', local: false },
  { key: 'custom',   label: '自定义 OpenAI 兼容',   baseUrl: '', model: '', local: false }
]

// 比对级忽略的常用文件类型（多选）。value 存点号前缀的小写扩展名，与后端 CompareOptions.ignoreExtensions 对齐。
// 运行时按需增删；勾选后对比会忽略这些类型的条目（日志、临时文件、锁文件、压缩包、图片等常见噪声）。
const IGNORE_EXT_PRESETS = [
  { value: '.log',        label: '日志 .log',        hint: '运行日志、控制台输出' },
  { value: '.tmp',        label: '临时 .tmp',        hint: '临时文件' },
  { value: '.swp',        label: '交换 .swp',        hint: 'vi/vim 交换文件' },
  { value: '.bak',        label: '备份 .bak',        hint: '备份副本' },
  { value: '.class',      label: '字节码 .class',    hint: '编译产物（忽略则只看源码不改）' },
  { value: '.jar',        label: '归档 .jar',        hint: '第三方 Jar（忽略则跳过整个依赖包）' },
  { value: '.zip',        label: '压缩 .zip',        hint: 'zip 归档' },
  { value: '.war',        label: '压缩 .war',        hint: 'war 归档' },
  { value: '.png',        label: '图片 .png',        hint: '位图资源' },
  { value: '.jpg',        label: '图片 .jpg/.jpeg',  hint: '位图资源' },
  { value: '.gif',        label: '图片 .gif',        hint: '动图资源' },
  { value: '.svg',        label: '矢量 .svg',        hint: '矢量图资源' },
  { value: '.ico',        label: '图标 .ico',        hint: '站点/应用图标' },
  { value: '.db',         label: '数据库 .db',       hint: 'SQLite 等本地库文件' },
  { value: '.lock',       label: '锁 .lock',         hint: '依赖锁/进程锁（忽略可避免伪造差异）' },
  { value: '.map',        label: '源码映射 .map',    hint: '前端 sourcemap' },
  { value: '.min.js',     label: '压缩JS .min.js',   hint: '前端压缩产物' },
  { value: '.txt',        label: '纯文本 .txt',      hint: '说明/README（按需）' }
]

// 勾选响应：把「是否已勾选」的布尔映射转回扩展名数组（value 即点号扩展名，直接存储）。
function midToggle(ev, id) {
  const checked = !!ev && !!ev.target && ev.target.checked
  const cur = Array.isArray(form.ignoreExtensions) ? form.ignoreExtensions.slice() : []
  if (!checked) form.ignoreExtensions = cur.filter(v => v !== id)
  else if (!cur.includes(id)) form.ignoreExtensions = [...cur, id]
}

// 自定义后缀：输入框内容（如 ".MF" / "properties"，允许带或不带点，允许逗号分隔多个）
const customExt = ref('')
// 归一化单个输入为点号小写扩展名；非法（空/含路径分隔符/端点）返回 null
function normalizeExt(raw) {
  let v = String(raw || '').trim()
  if (!v) return null
  const segs = v.split(/[,;\s]+/).map(s => s.trim()).filter(Boolean)
  const out = []
  for (let s of segs) {
    if (!s.startsWith('.')) s = '.' + s
    if (s === '.') continue
    if (s.includes('/') || s.includes('\\')) continue
    out.push(s.toLowerCase())
  }
  return out
}
// 添加自定义后缀到忽略列表（立即生效，保存时随 form 一并提交）
function addCustomExt() {
  const list = normalizeExt(customExt.value)
  if (!list || !list.length) { customExt.value = ''; return }
  const cur = Array.isArray(form.ignoreExtensions) ? form.ignoreExtensions.slice() : []
  for (const v of list) { if (!cur.includes(v)) cur.push(v) }
  form.ignoreExtensions = cur
  customExt.value = ''
}
// 从忽略列表移除某扩展名
function removeCustomExt(v) {
  if (!Array.isArray(form.ignoreExtensions)) return
  form.ignoreExtensions = form.ignoreExtensions.filter(x => x !== v)
}

const cfgTab = ref('ai')
const form = reactive({})
const showKey = ref(false)
const testing = ref(false)

let prevProviderKey = null
watch(() => props.visible, (v) => {
  if (v) {
    Object.assign(form, JSON.parse(JSON.stringify(state.config || {})))
    prevProviderKey = form.aiProvider // 记录当前厂商，供首次切换时判断「是否未改过默认值」
    // 打开配置中心即查询上下文索引状态（读缓存秒回；让用户一眼看到「是否已生效」）
    if (form.projectContextEnabled && form.projectContextDir) loadContextStatus()
    else state.aiContext = { loading: false, data: null, error: '' }
  }
})

// ---- 项目级上下文状态（配置中心可视化：加载中/已加载/失败 + 项目清单 + 重新扫描） ----
const ctxLoading = computed(() => state.aiContext.loading)
const ctxError = computed(() => state.aiContext.error)
const ctxData = computed(() => state.aiContext.data)
function fmtTime(ms) {
  if (!ms) return '—'
  const d = new Date(ms)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
async function onRefreshContext() {
  await loadContextStatus(true)
}

function close() { emit('close') }

// 切换厂商时，若当前 Base URL 仍等于「上一厂商的预设默认值」或为空（即用户未手动改过），
// 则自动填充新厂商的 baseUrl/model；用户已自定义则保留其填写（不覆盖）。
// 用上一厂商的预设 URL 作基准（而非模块级 prevBaseUrl），使「打开后第一次切换」也能正确填充。
function onProviderChange() {
  const newP = PRESETS.find(x => x.key === form.aiProvider)
  if (!newP || newP.key === 'custom') { prevProviderKey = form.aiProvider; return }
  const oldP = PRESETS.find(x => x.key === prevProviderKey)
  const untouched = !form.aiBaseUrl || (oldP && form.aiBaseUrl === oldP.baseUrl)
  if (untouched) {
    form.aiBaseUrl = newP.baseUrl
    form.aiModel = newP.model
  }
  prevProviderKey = newP.key
}

const canPick = computed(() => isElectron() || isTauri())
const pickTitle = computed(() => canPick.value
  ? '选择本地文件夹作为项目级上下文目录'
  : '仅在桌面壳（Electron/Tauri）中可用，浏览器模式请手动输入服务器本机绝对路径')

async function pickContextDir() {
  const p = await pickPath({ directory: true })
  if (p) form.projectContextDir = p
}

// 按当前 API Base URL + Key 拉取可用模型列表，写入 state.aiModels 供下方 datalist 补全。
const fetchingModels = ref(false)
async function onFetchModels() {
  fetchingModels.value = true
  await fetchModels({
    provider: form.aiProvider, baseUrl: form.aiBaseUrl, apiKey: form.aiApiKey,
    httpProxy: form.httpProxy, httpsProxy: form.httpsProxy,
    blockPrivateEndpoints: !!form.blockPrivateEndpoints
  })
  fetchingModels.value = false
}

async function onTest() {
  testing.value = true
  await testConnection({
    provider: form.aiProvider, baseUrl: form.aiBaseUrl, apiKey: form.aiApiKey,
    model: form.aiModel, httpProxy: form.httpProxy, httpsProxy: form.httpsProxy,
    blockPrivateEndpoints: !!form.blockPrivateEndpoints
  })
  testing.value = false
}

// 保存：仅持久化配置，不关闭窗口（用户可能要切换到别的 Tab 继续填其他信息）。
// 关闭窗口由 footer 的「关闭」按钮负责（emit close）。store.saveConfig 已弹「配置已保存」toast。
async function onSave() {
  const out = JSON.parse(JSON.stringify(form))
  if (out.topK !== undefined && out.topK !== null && out.topK !== '') out.topK = Number(out.topK)
  if (out.stageBTopK !== undefined && out.stageBTopK !== null && out.stageBTopK !== '') out.stageBTopK = Number(out.stageBTopK)
  if (out.stageATopK !== undefined && out.stageATopK !== null && out.stageATopK !== '') out.stageATopK = Number(out.stageATopK)
  if (out.stageAFileSampleLines !== undefined && out.stageAFileSampleLines !== null && out.stageAFileSampleLines !== '') out.stageAFileSampleLines = Number(out.stageAFileSampleLines)
  if (out.maxPromptTokens !== undefined && out.maxPromptTokens !== null && out.maxPromptTokens !== '') out.maxPromptTokens = Number(out.maxPromptTokens)
  if (out.costGateWarnTokens !== undefined && out.costGateWarnTokens !== null && out.costGateWarnTokens !== '') out.costGateWarnTokens = Number(out.costGateWarnTokens)
  await saveConfig(out)
  // 保存后立即重扫上下文目录（新目录/刚开启都立即生效并展示加载态）
  if (out.projectContextEnabled && out.projectContextDir) await loadContextStatus(true)
}

// 手动清理临时文件：调后端 /api/admin/cleanup-temp，释放解压残留，避免磁盘爆满。
const cleaning = ref(false)
const cleanupMsg = ref('')
async function onCleanupTemp() {
  cleaning.value = true
  cleanupMsg.value = ''
  try {
    const r = await api.cleanupTemp()
    const mb = (r.freedBytes / (1024 * 1024)).toFixed(2)
    cleanupMsg.value = `已清理 ${r.files} 个文件 / ${r.dirs} 个目录，释放约 ${mb} MB`
    toast('success', `临时文件清理完成，释放约 ${mb} MB`)
  } catch (e) {
    const m = (e && e.message) || String(e)
    cleanupMsg.value = '清理失败：' + m
    toast('danger', '临时文件清理失败：' + m)
  } finally {
    cleaning.value = false
  }
}
</script>

<template>
  <div class="modal-backdrop" v-if="visible" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header py-2 px-4">
          <h6 class="modal-title mb-0"><i class="bi bi-sliders"></i> 配置中心
            <small class="fw-normal text-secondary ms-2" style="font-size:.75rem">所有配置均在界面完成，无需改文件</small>
          </h6>
          <button type="button" class="btn-close" @click="close"></button>
        </div>

        <div class="alert alert-warning d-flex gap-2 align-items-start mb-2 py-2" role="alert" style="font-size:.8rem">
          <i class="bi bi-shield-lock fs-6"></i>
          <div>金融合规：默认优先使用本地/私有化模型；选择公网模型时仅发送脱敏后的 diff 摘要，原始源码不出机。默认不记住 API Key（落盘关闭）。</div>
        </div>

        <div class="modal-body">
          <ul class="nav nav-tabs mb-3">
            <li class="nav-item"><button class="nav-link py-1" :class="{active: cfgTab==='ai'}" @click="cfgTab='ai'">AI 服务</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{active: cfgTab==='parse'}" @click="cfgTab='parse'">解析与导出</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{active: cfgTab==='filter'}" @click="cfgTab='filter'">差异树过滤</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{active: cfgTab==='ui'}" @click="cfgTab='ui'">界面与高级</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{active: cfgTab==='help'}" @click="cfgTab='help'">帮助文档</button></li>
          </ul>

          <!-- AI 服务 -->
          <div v-show="cfgTab==='ai'">
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="选择 LLM 服务厂商；切换时会自动填充默认 Base URL 与模型名称">模型厂商</label>
              <div class="col-sm-9">
                <select class="form-select form-select-sm" v-model="form.aiProvider" @change="onProviderChange" title="选择 LLM 服务厂商；切换时会自动填充默认 Base URL 与模型名称">
                  <option v-for="p in PRESETS" :key="p.key" :value="p.key">{{ p.label }}</option>
                </select>
                <div class="form-text mb-0" style="font-size:.72rem" v-if="PRESETS.find(x=>x.key===form.aiProvider)?.local">
                  本地模型：默认无需 API Key，且 blockPrivateEndpoints 应关闭以允许访问回环地址。
                </div>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="OpenAI 兼容格式的聊天补全接口地址，例如 https://api.openai.com/v1">Base URL</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.aiBaseUrl" title="OpenAI 兼容格式的聊天补全接口地址，例如 https://api.openai.com/v1"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="模型服务提供的访问密钥；本地/私有化模型通常可留空">API Key</label>
              <div class="col-sm-9 input-group input-group-sm">
                <input class="form-control" :type="showKey ? 'text' : 'password'" v-model="form.aiApiKey" placeholder="本地模型可留空" title="模型服务提供的访问密钥；本地/私有化模型通常可留空">
                <button class="btn btn-outline-secondary" type="button" @click="showKey = !showKey" title="显示/隐藏 API Key">
                  <i class="bi" :class="showKey ? 'bi-eye-slash' : 'bi-eye'"></i>
                </button>
                <button class="btn btn-outline-secondary" type="button" :disabled="testing" @click="onTest" title="用当前配置测试与模型服务的连通性">
                  <i class="bi bi-plug"></i> {{ testing ? '测试中…' : '连接测试' }}
                </button>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="实际请求的模型 ID，例如 gpt-4o、deepseek-v4-flash、qwen-plus；可点「获取模型列表」自动拉取">模型名称</label>
              <div class="col-sm-9">
                <div class="input-group input-group-sm">
                  <input class="form-control" list="aiModelList" v-model="form.aiModel" placeholder="可输入或点右侧按钮拉取" title="实际请求的模型 ID，例如 gpt-4o、deepseek-v4-flash、qwen-plus；可点「获取模型列表」自动拉取">
                  <datalist id="aiModelList">
                    <option v-for="m in state.aiModels" :key="m" :value="m"></option>
                  </datalist>
                  <button class="btn btn-outline-secondary" type="button" :disabled="fetchingModels" @click="onFetchModels" title="按当前 API Base URL + Key 自动获取可用模型列表">
                    <i class="bi" :class="fetchingModels ? 'bi-arrow-repeat' : 'bi-list-ul'"></i> {{ fetchingModels ? '获取中…' : '获取模型列表' }}
                  </button>
                </div>
                <div class="form-text mb-0" style="font-size:.72rem" v-if="state.aiModels.length">已拉取 {{ state.aiModels.length }} 个可用模型，可在输入框中下拉选择。</div>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="访问公网模型时经过的 HTTP 代理，格式如 http://proxy.example.com:8080">HTTP 代理</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.httpProxy" placeholder="企业网访问公网模型时使用" title="访问公网模型时经过的 HTTP 代理，格式如 http://proxy.example.com:8080"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="访问公网模型时经过的 HTTPS 代理；留空则复用 HTTP 代理">HTTPS 代理</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.httpsProxy" placeholder="可选" title="访问公网模型时经过的 HTTPS 代理；留空则复用 HTTP 代理"></div>
            </div>
            <div class="form-check form-check-inline">
              <input class="form-check-input" type="checkbox" id="cfgBlock" v-model="form.blockPrivateEndpoints" title="勾选后禁止访问 127.0.0.1、10.x.x.x 等私网地址，防止服务端请求伪造">
              <label class="form-check-label" for="cfgBlock" title="勾选后禁止访问 127.0.0.1、10.x.x.x 等私网地址，防止服务端请求伪造">严格 SSRF：拒绝回环/私网地址</label>
            </div>
            <div class="form-check form-check-inline">
              <input class="form-check-input" type="checkbox" id="cfgAiEnabled" v-model="form.aiEnabled" title="开启后比对完成可自动生成 AI 风险/影响评估报告">
              <label class="form-check-label" for="cfgAiEnabled" title="开启后比对完成可自动生成 AI 风险/影响评估报告">启用 AI 分析</label>
            </div>
          </div>

          <!-- 解析与导出 -->
          <div v-show="cfgTab==='parse'">
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="命中此前缀的类会被标记为内部业务类，并在差异树中按业务码展开">内部包前缀</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.internalPrefixes" placeholder="命中则按 L1 业务码展开 class" title="命中此前缀的类会被标记为内部业务类，并在差异树中按业务码展开"></div>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgExpand" v-model="form.expandAll" title="强制展开所有 class（包括第三方依赖），否则只展开内部前缀命中的类">
              <label class="form-check-label" for="cfgExpand" title="强制展开所有 class（包括第三方依赖），否则只展开内部前缀命中的类">展开全部（含三方 class）</label>
            </div>
            <!-- 自动逐层解包：WAR/ZIP/JAR 嵌套归档多线程物理解包，比对完成后自动展开；AI/导出/统计覆盖嵌套子文件 -->
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="开启后比对时后端多线程逐层解包所有嵌套归档，嵌套包内部文件进入差异/AI/导出，且树中自动展开；解包完成后才允许 AI 分析与导出资产">
                自动逐层解包
              </label>
              <div class="col-sm-9">
                <div class="form-check form-check-inline mb-0">
                  <input class="form-check-input" type="checkbox" id="cfgUnpack" v-model="form.unpackNested" title="开启后嵌套包（zip/war/jar 等）比对完成即自动逐层解包，无需手动点击展开；解包中禁用 AI 与分析。默认开启">
                  <label class="form-check-label" for="cfgUnpack" title="开启后嵌套包（zip/war/jar 等）比对完成即自动逐层解包，无需手动点击展开；解包中禁用 AI 与分析">开启（默认）</label>
                </div>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="自动逐层解包的并发线程数，越大解包越快但更占内存">解包线程数</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" min="1" max="16" v-model.number="form.unpackThreads" title="自动逐层解包的并发线程数，越大解包越快但更占内存"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="自动逐层解包的最大递归深度；超深嵌套会在此深度截断并保留为可手动展开的归档节点">最大解包深度</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" min="1" max="12" v-model.number="form.unpackMaxDepth" title="自动逐层解包的最大递归深度；超深嵌套会在此深度截断并保留为可手动展开的归档节点"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="差异树概览层默认展开的 TOP 节点数">Top-K（概览展开）</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.topK" title="差异树概览层默认展开的 TOP 节点数"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="AI 二阶段评估时送入的变更摘要条数上限，数值越大分析越全但 token 越高">StageB Top-K</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.stageBTopK" title="AI 二阶段评估时送入的变更摘要条数上限，数值越大分析越全但 token 越高"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="阶段A 全局概览最多纳入的变更文件数；数值越大概览越全、prompt 越大">StageA Top-K（概览文件数）</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" min="1" v-model.number="form.stageATopK" title="阶段A 全局概览最多纳入的变更文件数；数值越大概览越全、prompt 越大"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="阶段A 每个文件的 diff 摘要最多保留的行数（超长单行另受字符上限保护）">StageA 单文件摘要行数</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" min="1" v-model.number="form.stageAFileSampleLines" title="阶段A 每个文件的 diff 摘要最多保留的行数（超长单行另受字符上限保护）"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="单次 AI 请求最大输入 token 护栏：发送前预估超限即取消请求（避免模型拒收的 HTTP 400）。按所用模型上下文窗口设置（128K 窗口建议 120000）">单次请求最大输入 Token</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" min="1000" v-model.number="form.maxPromptTokens" title="单次 AI 请求最大输入 token 护栏：发送前预估超限即取消请求（避免模型拒收的 HTTP 400）。按所用模型上下文窗口设置（128K 窗口建议 120000）"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="预计消耗 token 数超过此值时给出二次确认，防止意外高额账单">成本闸门告警 token</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.costGateWarnTokens" title="预计消耗 token 数超过此值时给出二次确认，防止意外高额账单"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="指定外部 CFR 反编译 jar 的绝对路径；留空使用内置 CFR">自定义 CFR jar</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.cfrJar" placeholder="留空则使用内置 CFR" title="指定外部 CFR 反编译 jar 的绝对路径；留空使用内置 CFR"></div>
            </div>
            <div class="mb-1 mt-2" style="font-size:.78rem;color:var(--bs-secondary-color)">忽略不重要差异（审计降噪，对标 Beyond Compare）：</div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgIgWs" v-model="form.ignoreWhitespace" title="忽略所有空白差异（含缩进/行尾空白），降低纯格式噪声">
              <label class="form-check-label" for="cfgIgWs" title="忽略所有空白差异（含缩进/行尾空白），降低纯格式噪声">忽略空白</label>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgIgCmt" v-model="form.ignoreComments" title="剥离整行注释（// # /* */ <!-- --> 及 javadoc 续行），仅用于行匹配，不改显示内容">
              <label class="form-check-label" for="cfgIgCmt" title="剥离整行注释（// # /* */ <!-- --> 及 javadoc 续行），仅用于行匹配，不改显示内容">忽略整行注释</label>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="自定义正则，命中的子串从行匹配中移除（高级项；正则非法时自动忽略，不会使比对崩溃）">忽略正则</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.ignoreRegex" placeholder="如 \d{4}-\d{2}-\d{2}|@Generated 等" title="自定义正则，命中的子串从行匹配中移除（高级项；正则非法时自动忽略）"></div>
            </div>

            <!-- 比对过滤：多选忽略常见文件类型。勾选后对比会在解析收集阶段跳过这些类型的条目（不进差异树、不参与统计）。 -->
            <div class="mb-1 mt-2" style="font-size:.78rem;color:var(--bs-secondary-color)">比对过滤（忽略下面勾选的文件类型）：</div>
            <div class="d-flex flex-wrap gap-2 mb-2">
              <div v-for="ie in IGNORE_EXT_PRESETS" :key="ie.value" class="form-check form-check-inline mb-1" :title="ie.hint">
                <input class="form-check-input" type="checkbox" :id="'iex' + ie.value.replace(/[^a-zA-Z0-9]/g, '')"
                       :checked="Array.isArray(form.ignoreExtensions) && form.ignoreExtensions.includes(ie.value)"
                       @change="midToggle($event, ie.value)"
                       :title="ie.hint">
                <label class="form-check-label" :for="'iex' + ie.value.replace(/[^a-zA-Z0-9]/g, '')" :title="ie.hint">{{ ie.label }}</label>
              </div>
            </div>
            <div v-if="!Array.isArray(form.ignoreExtensions) || !form.ignoreExtensions.includes('.min.js')" class="form-text" style="font-size:.72rem">
              提示：勾选「字节码 .class」「归档 .jar」等会跳过该类全部差异；默认仅忽略日志/临时/图片等非代码噪声，可随时再次勾选去掉。
            </div>

            <!-- 自定义后缀：除预置勾选项外，可添加任意文件后缀（如 .MF、.properties），
                 保存时与预置项一并提交给后端解析阶段过滤。 -->
            <div class="row g-2 align-items-center mt-1 mb-1">
              <label class="col-sm-3 col-form-label col-form-label-sm text-nowrap" title="添加任意自定义文件后缀；支持带/不带点、逗号或空格分隔多个，如 .MF、.properties">自定义后缀</label>
              <div class="col-sm-9">
                <div class="input-group input-group-sm">
                  <input class="form-control" v-model="customExt" placeholder="如 .MF、.properties（可多个，逗号分隔）" @keydown.enter.prevent="addCustomExt"
                         title="添加任意自定义文件后缀；支持带/不带点、逗号或空格分隔多个">
                  <button class="btn btn-outline-secondary" type="button" @click="addCustomExt" title="把输入的后缀加入忽略列表">添加</button>
                </div>
                <div v-if="Array.isArray(form.ignoreExtensions) && form.ignoreExtensions.length" class="mt-1 d-flex flex-wrap gap-1">
                  <span v-for="ie in form.ignoreExtensions" :key="ie"
                        class="badge rounded-pill text-bg-secondary cursor-pointer d-inline-flex align-items-center gap-1"
                        style="font-size:.7rem" role="button" @click="removeCustomExt(ie)"
                        :title="'点击移除，忽略 ' + ie + ' 类型'">
                    {{ ie }} <i class="bi bi-x-lg" style="font-size:.6rem"></i>
                  </span>
                </div>
                <div class="form-text mb-0" style="font-size:.72rem">当前已忽略 {{ Array.isArray(form.ignoreExtensions) ? form.ignoreExtensions.length : 0 }} 项，点徽章可移除。</div>
              </div>
            </div>
          </div>

          <!-- 差异树过滤 -->
          <div v-show="cfgTab==='filter'">
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="按路径或类名过滤差异树节点">搜索关键字</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.filterSearch" placeholder="按路径/类名过滤差异树" title="按路径或类名过滤差异树节点"></div>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgRegex" v-model="form.filterRegex" title="开启后搜索关键字按正则表达式匹配">
              <label class="form-check-label" for="cfgRegex" title="开启后搜索关键字按正则表达式匹配">正则匹配</label>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgAutoAi" v-model="form.autoAiOnCompare" title="勾选后每次比对完成自动调用 AI 生成风险/影响评估报告">
              <label class="form-check-label" for="cfgAutoAi" title="勾选后每次比对完成自动调用 AI 生成风险/影响评估报告">比对后自动生成 AI 报告</label>
            </div>
            <div class="mb-1" style="font-size:.78rem;color:var(--bs-secondary-color)">差异树显示项：</div>
            <div class="d-flex flex-wrap gap-3">
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsM" v-model="form.filterShowModified" title="在差异树中显示被修改的节点"><label class="form-check-label" for="fsM" title="在差异树中显示被修改的节点">修改</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsA" v-model="form.filterShowAdded" title="在差异树中显示新增的节点"><label class="form-check-label" for="fsA" title="在差异树中显示新增的节点">新增</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsD" v-model="form.filterShowDeleted" title="在差异树中显示删除的节点"><label class="form-check-label" for="fsD" title="在差异树中显示删除的节点">删除</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsU" v-model="form.filterShowUnchanged" title="在差异树中显示未变更的节点；关闭可显著缩短差异树长度"><label class="form-check-label" for="fsU" title="在差异树中显示未变更的节点；关闭可显著缩短差异树长度">未变</label></div>
            </div>
            <div class="form-text" style="font-size:.72rem">关闭「未变」可显著缩短差异树长度；以上偏好仅影响前端展示。</div>
          </div>

          <!-- 界面与高级 -->
          <div v-show="cfgTab==='ui'">
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgPersist" v-model="form.persistApiKey" title="开启后 API Key 将写入本地配置文件；默认关闭以保证密钥不落盘">
              <label class="form-check-label" for="cfgPersist" title="开启后 API Key 将写入本地配置文件；默认关闭以保证密钥不落盘">记住 API Key（落盘，默认关闭）</label>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgPc" v-model="form.projectContextEnabled" title="开启后比对时会额外加载该目录下的项目源码/文档作为 AI 分析的上下文">
              <label class="form-check-label" for="cfgPc" title="开启后比对时会额外加载该目录下的项目源码/文档作为 AI 分析的上下文">启用项目级上下文增强</label>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="项目源码/文档目录，用于为 AI 分析提供背景上下文">上下文目录</label>
              <div class="col-sm-9">
                <div class="input-group input-group-sm">
                  <input class="form-control" v-model="form.projectContextDir" placeholder="项目源码/文档目录" title="项目源码/文档目录，用于为 AI 分析提供背景上下文">
                  <button class="btn btn-outline-secondary" type="button" @click="pickContextDir" :disabled="!canPick" :title="pickTitle">
                    <i class="bi bi-folder2-open"></i>
                  </button>
                </div>
              </div>
            </div>
            <!-- 上下文加载状态（借鉴 IDE「索引加载」交互：加载中/完成态徽标 + 项目统计 + 手动刷新） -->
            <div v-if="form.projectContextEnabled" class="card card-body py-2 mb-2 context-status" style="font-size:.76rem">
              <div class="d-flex align-items-center gap-2 flex-wrap">
                <template v-if="ctxLoading">
                  <span class="spinner-border spinner-border-sm text-primary" role="status"></span>
                  <span class="text-secondary">正在递归识别项目，首次扫描约需数秒…</span>
                </template>
                <template v-else-if="ctxError">
                  <i class="bi bi-exclamation-triangle-fill text-danger"></i>
                  <span class="text-danger">上下文加载失败：{{ ctxError }}</span>
                </template>
                <template v-else-if="ctxData && ctxData.ok">
                  <i class="bi bi-check-circle-fill text-success"></i>
                  <span class="fw-semibold text-success">上下文已加载</span>
                  <span class="text-secondary">
                    · {{ ctxData.projectCount }} 个项目 · {{ ctxData.javaFileCount ?? 0 }} 个 Java 文件
                    <span v-if="ctxData.fromCache" class="text-secondary"><i class="bi bi-database"></i> 缓存命中</span>
                    <span v-else class="text-secondary"><i class="bi bi-arrow-repeat"></i> 已重新扫描</span>
                    · 更新于 {{ fmtTime(ctxData.scannedAt) }}
                  </span>
                </template>
                <template v-else>
                  <i class="bi bi-dash-circle text-secondary"></i>
                  <span class="text-secondary">尚未加载（保存配置后自动扫描）</span>
                </template>
                <button class="btn btn-outline-primary btn-sm ms-auto" type="button" :disabled="ctxLoading" @click="onRefreshContext" title="强制重新递归扫描上下文目录（忽略缓存）">
                  <i class="bi bi-arrow-clockwise"></i> 重新扫描
                </button>
              </div>
              <div v-if="ctxData && ctxData.projects && ctxData.projects.length" class="mt-2">
                <div class="text-secondary mb-1"><i class="bi bi-diagram-3"></i> 识别到的项目（点击展开）</div>
                <ul class="list-unstyled mb-0 ps-2 context-project-list" style="max-height:150px;overflow:auto">
                  <li v-for="p in ctxData.projects" :key="p.relPath" class="d-flex gap-2 align-items-baseline text-nowrap" style="font-size:.74rem">
                    <code class="text-body">{{ p.relPath }}</code>
                    <span class="text-secondary">· {{ p.buildSystem }} · {{ p.moduleCount }} 模块 · {{ p.depCount }} 依赖 · {{ p.fileCount }} 文件</span>
                  </li>
                </ul>
                <div class="form-text mt-1" style="font-size:.68rem">
                  该目录下的项目已作为 AI 分析的背景上下文注入；关闭「启用项目级上下文增强」可停用。
                </div>
              </div>
            </div>
            <!-- 手动清理临时文件：解压残留导致的磁盘爆满时按需回收（不中断进行中任务） -->
            <div class="card card-body py-2 mb-0" style="font-size:.76rem">
              <div class="d-flex align-items-center gap-2 flex-wrap">
                <span class="fw-semibold text-nowrap"><i class="bi bi-broom"></i> 临时文件清理</span>
                <span class="text-secondary">清理解压/抽取残留临时文件与遗留作业目录，释放磁盘空间（反编译缓存与近期日志保留）。</span>
                <button class="btn btn-outline-danger btn-sm ms-auto" type="button" :disabled="cleaning"
                        @click="onCleanupTemp"
                        title="立即回收系统临时目录与 .bempdiff/runtime 下的残留临时文件，避免磁盘爆满；不会中断正在进行的解压任务">
                  <i class="bi" :class="cleaning ? 'bi-arrow-repeat' : 'bi-broom'"></i> {{ cleaning ? '清理中…' : '手动清理临时文件' }}
                </button>
              </div>
              <div v-if="cleanupMsg" class="mt-2 text-success" style="font-size:.72rem"><i class="bi bi-check-circle"></i> {{ cleanupMsg }}</div>
            </div>
          </div>

          <!-- 帮助文档 -->
          <div v-show="cfgTab==='help'" class="help-tab">
            <HelpDoc />
          </div>
        </div>

        <div class="modal-footer py-2 px-4 d-flex align-items-center gap-2">
          <span class="text-secondary me-auto" style="font-size:.75rem">保存后窗口保持打开，可继续编辑；填完点「关闭」。</span>
          <button class="btn btn-outline-secondary btn-sm" @click="close"><i class="bi bi-x-lg"></i> 关闭</button>
          <button class="btn btn-primary btn-sm" @click="onSave"><i class="bi bi-check2"></i> 保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,.4);
  display: flex; align-items: flex-start; justify-content: center; z-index: 1500; padding-top: 4vh; padding-bottom: 4vh;
}
/* 自适应高度：内容再多也不会超出视口被截断。
   flex 列布局让 header/footer 固定、body 内部滚动（下拉条只作用于 body 内容区）。
   max-height 用 viewport 高度减上下留白，短屏/小窗口下 body 自动压缩出滚动条。 */
.modal-content {
  width: 100%;
  max-height: calc(100vh - 8vh);   /* 兜底：老引擎无 dvh 时不支持则用 vh */
  max-height: calc(100dvh - 8vh);
  display: flex; flex-direction: column;
  background-color: var(--bs-body-bg); color: var(--bs-body-color);
}
.modal-body { padding: 1rem 1.5rem; flex: 1 1 auto; min-height: 0; overflow-y: auto; }
</style>