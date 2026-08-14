<script setup>
import { reactive, ref, watch } from 'vue'
import { state, saveConfig, testConnection } from '../store'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

// 厂商预设：value=落到后端的 provider 代码（openai/ollama/qwen/...），label=展示名。
// 与 java_core LlmPreset.builtinPresets() 保持一致（前端只发 code，不发明文厂商名）。
const PRESETS = [
  { key: 'openai',   label: 'OpenAI (GPT)',        baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o', local: false },
  { key: 'azure',    label: 'Azure OpenAI',        baseUrl: 'https://<resource>.openai.azure.com', model: 'gpt-4o', local: false },
  { key: 'ollama',   label: 'Ollama（本地/私有化，推荐）', baseUrl: 'http://localhost:11434/v1', model: 'qwen2.5:7b', local: true },
  { key: 'deepseek', label: 'DeepSeek',            baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', local: false },
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

watch(() => props.visible, (v) => {
  if (v) Object.assign(form, JSON.parse(JSON.stringify(state.config || {})))
})

function close() { emit('close') }

// 切换厂商时，若该字段仍是上一预设的默认值或为空，则自动填充新预设的 baseUrl/model。
function onProviderChange() {
  const p = PRESETS.find(x => x.key === form.aiProvider)
  if (!p || p.key === 'custom') return
  const isDefaultOrEmpty = !form.aiBaseUrl || form.aiBaseUrl === prevBaseUrl
  if (isDefaultOrEmpty) {
    form.aiBaseUrl = p.baseUrl
    form.aiModel = p.model
    prevBaseUrl = p.baseUrl
  }
}
let prevBaseUrl = ''

async function onTest() {
  testing.value = true
  await testConnection({
    provider: form.aiProvider, baseUrl: form.aiBaseUrl, apiKey: form.aiApiKey,
    model: form.aiModel, httpProxy: form.httpProxy, httpsProxy: form.httpsProxy,
    blockPrivateEndpoints: !!form.blockPrivateEndpoints
  })
  testing.value = false
}

function onSave() {
  // 清理空字符串 topK 等，确保数字字段为 number
  const out = JSON.parse(JSON.stringify(form))
  if (out.topK !== undefined && out.topK !== null && out.topK !== '') out.topK = Number(out.topK)
  if (out.stageBTopK !== undefined && out.stageBTopK !== null && out.stageBTopK !== '') out.stageBTopK = Number(out.stageBTopK)
  if (out.costGateWarnTokens !== undefined && out.costGateWarnTokens !== null && out.costGateWarnTokens !== '') out.costGateWarnTokens = Number(out.costGateWarnTokens)
  saveConfig(out)
  close()
}
</script>

<template>
  <div class="modal-backdrop" v-if="visible" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header py-2">
          <h6 class="modal-title mb-0"><i class="bi bi-sliders"></i> 配置中心
            <small class="fw-normal text-secondary ms-2" style="font-size:.75rem">所有配置均在界面完成，无需改文件</small>
          </h6>
          <button type="button" class="btn-close" @click="close"></button>
        </div>

        <div class="alert alert-warning d-flex gap-2 align-items-start m-3 mb-0 py-2" role="alert" style="font-size:.8rem">
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
              <label class="col-sm-3 col-form-label col-form-label-sm">模型厂商</label>
              <div class="col-sm-9">
                <select class="form-select form-select-sm" v-model="form.aiProvider" @change="onProviderChange">
                  <option v-for="p in PRESETS" :key="p.key" :value="p.key">{{ p.label }}</option>
                </select>
                <div class="form-text mb-0" style="font-size:.72rem" v-if="PRESETS.find(x=>x.key===form.aiProvider)?.local">
                  本地模型：默认无需 API Key，且 blockPrivateEndpoints 应关闭以允许访问回环地址。
                </div>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">Base URL</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.aiBaseUrl"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">API Key</label>
              <div class="col-sm-9 input-group input-group-sm">
                <input class="form-control" :type="showKey ? 'text' : 'password'" v-model="form.aiApiKey" placeholder="本地模型可留空">
                <button class="btn btn-outline-secondary" type="button" @click="showKey = !showKey">
                  <i class="bi" :class="showKey ? 'bi-eye-slash' : 'bi-eye'"></i>
                </button>
                <button class="btn btn-outline-secondary" type="button" :disabled="testing" @click="onTest">
                  <i class="bi bi-plug"></i> {{ testing ? '测试中…' : '连接测试' }}
                </button>
              </div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">模型名称</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.aiModel"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">HTTP 代理</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.httpProxy" placeholder="企业网访问公网模型时使用"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">HTTPS 代理</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.httpsProxy" placeholder="可选"></div>
            </div>
            <div class="form-check form-check-inline">
              <input class="form-check-input" type="checkbox" id="cfgBlock" v-model="form.blockPrivateEndpoints">
              <label class="form-check-label" for="cfgBlock">严格 SSRF：拒绝回环/私网地址</label>
            </div>
            <div class="form-check form-check-inline">
              <input class="form-check-input" type="checkbox" id="cfgAiEnabled" v-model="form.aiEnabled">
              <label class="form-check-label" for="cfgAiEnabled">启用 AI 分析</label>
            </div>
          </div>

          <!-- 解析与导出 -->
          <div v-show="cfgTab==='parse'">
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">内部包前缀</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.internalPrefixes" placeholder="命中则按 L1 业务码展开 class"></div>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgExpand" v-model="form.expandAll">
              <label class="form-check-label" for="cfgExpand">展开全部（含三方 class）</label>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">Top-K（概览展开）</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.topK"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">StageB Top-K</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.stageBTopK"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">成本闸门告警 token</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" type="number" v-model.number="form.costGateWarnTokens"></div>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">自定义 CFR jar</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.cfrJar" placeholder="留空则使用内置 CFR"></div>
            </div>
          </div>

          <!-- 差异树过滤 -->
          <div v-show="cfgTab==='filter'">
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">搜索关键字</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.filterSearch" placeholder="按路径/类名过滤差异树"></div>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgRegex" v-model="form.filterRegex">
              <label class="form-check-label" for="cfgRegex">正则匹配</label>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgAutoAi" v-model="form.autoAiOnCompare">
              <label class="form-check-label" for="cfgAutoAi">比对后自动生成 AI 报告</label>
            </div>
            <div class="mb-1" style="font-size:.78rem;color:var(--bs-secondary-color)">差异树显示项：</div>
            <div class="d-flex flex-wrap gap-3">
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsM" v-model="form.filterShowModified"><label class="form-check-label" for="fsM">修改</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsA" v-model="form.filterShowAdded"><label class="form-check-label" for="fsA">新增</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsD" v-model="form.filterShowDeleted"><label class="form-check-label" for="fsD">删除</label></div>
              <div class="form-check"><input class="form-check-input" type="checkbox" id="fsU" v-model="form.filterShowUnchanged"><label class="form-check-label" for="fsU">未变</label></div>
            </div>
            <div class="form-text" style="font-size:.72rem">关闭「未变」可显著缩短差异树长度；以上偏好仅影响前端展示。</div>
          </div>

          <!-- 界面与高级 -->
          <div v-show="cfgTab==='ui'">
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgPersist" v-model="form.persistApiKey">
              <label class="form-check-label" for="cfgPersist">记住 API Key（落盘，默认关闭）</label>
            </div>
            <div class="form-check form-check-inline mb-2">
              <input class="form-check-input" type="checkbox" id="cfgPc" v-model="form.projectContextEnabled">
              <label class="form-check-label" for="cfgPc">启用项目级上下文增强</label>
            </div>
            <div class="row g-2 align-items-center mb-2">
              <label class="col-sm-3 col-form-label col-form-label-sm">上下文目录</label>
              <div class="col-sm-9"><input class="form-control form-control-sm" v-model="form.projectContextDir" placeholder="项目源码/文档目录"></div>
            </div>
          </div>
        </div>

        <div class="modal-footer py-2">
          <button class="btn btn-secondary btn-sm" @click="close">取消</button>
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
.modal-content { width: 100%; }
</style>
