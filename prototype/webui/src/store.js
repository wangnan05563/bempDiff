import { reactive } from 'vue'
import { api } from './api/client'

export const state = reactive({
  theme: localStorage.getItem('bempdiff-theme') || 'light',
  job: null,            // compare 返回：{ jobId, mode, oldFile, newFile, oldVersion, newVersion, stats, tree[] }
  selectedKey: null,    // 当前选中的差异树节点 key
  decompile: null,      // { oldSrc, newSrc, diffText, engine, ok }
  filter: 'all',        // 差异树过滤：all | ADDED | DELETED | MODIFIED | UNCHANGED
  busy: false,
  busyText: '',
  analyzing: false,     // 后台（静默）AI 报告生成中，不弹全屏遮罩
  config: null,         // /api/config GET 结果
  reportMd: null,       // 最近一次生成的报告 markdown
  reportAi: false,
  error: null,
  toast: null           // { type, text }
})

let toastTimer = null
export function toast(type, text, ms = 3200) {
  state.toast = { type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { state.toast = null }, ms)
}

export function applyTheme(t) {
  state.theme = t
  localStorage.setItem('bempdiff-theme', t)
  document.documentElement.setAttribute('data-bs-theme', t)
}

export async function init() {
  applyTheme(state.theme)
  try {
    state.config = await api.getConfig()
  } catch (e) {
    // 后端未启动时给默认值，不阻塞 UI 渲染
    state.config = defaultConfig()
    console.warn('加载配置失败，使用默认值：', e.message)
  }
}

export function defaultConfig() {
  // 注意：aiProvider 必须发后端能识别的 code（openai/ollama/qwen/azure/custom…），
  // 不再是中文展示名（历史 bug 已修）。真实配置以 /api/config 返回为准，此处仅为后端不可用时的兜底。
  return {
    aiProvider: 'openai',
    aiBaseUrl: 'https://api.openai.com/v1',
    aiApiKey: '',
    aiModel: 'gpt-4o',
    aiEnabled: false,
    stageBTopK: 15,
    costGateWarnTokens: 8000,
    internalPrefixes: 'com.hundsun',
    expandAll: false,
    topK: 15,
    cfrJar: '',
    httpProxy: '',
    httpsProxy: '',
    blockPrivateEndpoints: false,
    persistApiKey: false,
    projectContextDir: '',
    projectContextEnabled: false,
    filterSearch: '',
    filterRegex: false,
    filterShowModified: true,
    filterShowAdded: true,
    filterShowDeleted: true,
    filterShowUnchanged: true,
    autoAiOnCompare: true,
    hasApiKey: false
  }
}

export async function runCompare({ leftType, leftPath, rightPath, options }) {
  state.busy = true; state.busyText = '正在比对包/目录…'
  state.error = null
  try {
    const r = await api.compare({ leftType, leftPath, rightPath, options })
    state.job = r
    state.selectedKey = null
    state.decompile = null
    state.reportMd = null
    toast('success', `比对完成：新增 ${r.stats.added} · 删除 ${r.stats.deleted} · 修改 ${r.stats.modified}`)
    // 配置开启且 AI 已启用时，比对后自动在后台（静默）生成 AI 报告，供破坏性/审计 Tab 与预览使用。
    if (state.config && state.config.autoAiOnCompare && state.config.aiEnabled) {
      generateReport(true, { silent: true })
    }
  } catch (e) {
    state.error = e.message
    toast('danger', '比对失败：' + e.message)
  } finally {
    state.busy = false
  }
}

export async function selectEntry(key) {
  if (!state.job) return
  state.selectedKey = key
  state.busy = true; state.busyText = '正在反编译/美化源码…'
  try {
    state.decompile = await api.decompile(state.job.jobId, key)
    if (!state.decompile.ok) {
      toast('warning', '该文件无法反编译：' + (state.decompile.engine || 'unknown'))
    }
  } catch (e) {
    state.decompile = null
    toast('danger', '反编译失败：' + e.message)
  } finally {
    state.busy = false
  }
}

export async function generateReport(ai, opts = {}) {
  if (!state.job) return
  const silent = !!opts.silent
  if (silent) state.analyzing = true
  else { state.busy = true; state.busyText = ai ? '正在调用 AI 分析…' : '正在生成报告…' }
  try {
    state.reportMd = await api.report(state.job.jobId, ai)
    state.reportAi = ai
    if (!silent) toast('success', ai ? 'AI 分析报告已生成' : '报告已生成')
  } catch (e) {
    toast('danger', '生成报告失败：' + e.message)
  } finally {
    if (silent) state.analyzing = false
    else state.busy = false
  }
}

export async function downloadExport() {
  if (!state.job) return
  try {
    const blob = await api.exportZip(state.job.jobId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `bempdiff-export-${state.job.jobId}.zip`
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
    toast('success', '差异资产已导出')
  } catch (e) {
    toast('danger', '导出失败：' + e.message)
  }
}

export async function saveConfig(cfg) {
  try {
    await api.putConfig(cfg)
    state.config = cfg
    toast('success', '配置已保存')
  } catch (e) {
    toast('danger', '保存配置失败：' + e.message)
  }
}

export async function testConnection(payload) {
  try {
    const r = await api.testAi(payload)
    // 后端返回 { ok, message, ... } 或错误包
    if (r && r.ok) {
      toast('success', '连接测试：' + (r.message || '已连通'))
      return true
    }
    toast('warning', '连接测试：' + (r && r.message ? r.message : '未连通'))
    return false
  } catch (e) {
    toast('danger', '连接测试失败：' + e.message)
    return false
  }
}
