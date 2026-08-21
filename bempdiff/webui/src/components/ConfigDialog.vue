<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { state, saveConfig, testConnection, fetchModels, loadContextStatus } from '../store'
import { pickPath, isElectron, isTauri } from '../lib/tauri.js'

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
  if (out.costGateWarnTokens !== undefined && out.costGateWarnTokens !== null && out.costGateWarnTokens !== '') out.costGateWarnTokens = Number(out.costGateWarnTokens)
  await saveConfig(out)
  // 保存后立即重扫上下文目录（新目录/刚开启都立即生效并展示加载态）
  if (out.projectContextEnabled && out.projectContextDir) await loadContextStatus(true)
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
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="差异树概览层默认展开的 TOP 节点数">Top-K（概览展开）</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.topK" title="差异树概览层默认展开的 TOP 节点数"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm" title="AI 二阶段评估时送入的变更摘要条数上限，数值越大分析越全但 token 越高">StageB Top-K</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.stageBTopK" title="AI 二阶段评估时送入的变更摘要条数上限，数值越大分析越全但 token 越高"></div>
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
  display: flex; align-items: flex-start; justify-content: center; z-index: 1500; padding-top: 5vh;
}
.modal-content { width: 100%; background-color: var(--bs-body-bg); color: var(--bs-body-color); }
.modal-body { padding: 1rem 1.5rem; }
</style>