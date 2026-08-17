import { reactive } from 'vue'
import { api } from './api/client'
import { activateTab as activateTabImpl, closeTabReducer as closeTabImpl, findActiveTab } from './lib/tabs'

// 状态相关展示常量，集中维护，DiffTree/DiffView/InfoPanel 共享，避免重复定义
export const STATUS_META = {
  ADDED:     { cls: 'text-bg-success', label: '新增', dot: 'var(--bs-success)' },
  DELETED:   { cls: 'text-bg-danger',  label: '删除', dot: 'var(--bs-danger)' },
  MODIFIED:  { cls: 'text-bg-warning', label: '修改', dot: 'var(--bs-warning)' },
  UNCHANGED: { cls: 'text-bg-secondary', label: '未变', dot: 'var(--bs-secondary)' }
}
export const STATUS_LABEL = { ADDED: '新增', DELETED: '删除', MODIFIED: '修改', UNCHANGED: '未变' }

// tabs 中每项的形状：{ key, node, decompile, busy, error }
//   key       - 节点的全局 key（state.job.tree 中唯一）
//   node      - 该 key 对应的树节点快照（避免 DiffView 二次 find 树）
//   decompile - 反编译结果，null 表示还没好；{ ok:false, ... } 表示不可反编译
//   busy      - true 表示反编译请求中（即便 decompile 已为 null）
//   error     - 反编译抛异常的 message；与 decompile.ok=false 区分（一个是后端说不行，一个是网络/解析挂了）
export const state = reactive({
  theme: localStorage.getItem('bempdiff-theme') || 'light',
  job: null,
  compareInputs: { oldPath: '', newPath: '' },   // 工具栏当前填写的路径，供状态栏实时预览
  tabs: [],                // 多文件对比页（DiffView 顶部 tab 栏数据源）
  activeKey: null,         // 当前激活的 tab 对应节点 key
  focusMode: false,        // 差异对比「专注模式」：隐藏左右栏，放大中间 diff 视野
  filter: 'all',
  busy: false,
  busyText: '',
  analyzing: false,        // 后台 AI 报告生成中，不弹全屏遮罩
  config: null,
  reportMd: null,
  reportAi: false,
  error: null,
  toast: null
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
    state.config = defaultConfig()
    console.warn('加载配置失败，使用默认值：', e.message)
  }
}

export function defaultConfig() {
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
    ignoreWhitespace: false,
    ignoreComments: false,
    ignoreRegex: '',
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

/** 切换差异对比「专注模式」（隐藏左右栏，放大中间 diff 视野）。 */
export function toggleFocusMode() { state.focusMode = !state.focusMode }
export function setFocusMode(v) { state.focusMode = !!v }

export async function runCompare({ leftType, leftPath, rightPath, options }) {
  state.busy = true; state.busyText = '正在比对包/目录…'
  state.error = null
  try {
    const r = await api.compare({ leftType, leftPath, rightPath, options })
    state.job = r
    // 重新比对：清空旧 tab（树变化，旧 key 可能已不存在）
    state.tabs = []
    state.activeKey = null
    state.reportMd = null
    toast('success', `比对完成：新增 ${r.stats.added} · 删除 ${r.stats.deleted} · 修改 ${r.stats.modified}`)
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

/**
 * 打开 / 激活一个对比 tab。左树点击触发：
 * - key 已在 tabs 里 → 仅切 activeKey（不触发二次反编译）
 * - key 不在         → push 占位 tab 并异步反编译，结果回写到对应 tab
 *   （用 key 定位而不是索引，因为反编译期间用户可能开了其他 tab）
 * 状态机本身抽到 lib/tabs.js，便于纯函数单测。
 */
export async function selectEntry(key) {
  if (!state.job) return
  const node = state.job.tree.find(n => n.key === key) || null
  // 调用 lib/tabs.js 的纯函数决定要不要新开
  const r = activateTabImpl(state.tabs, state.activeKey, key, node)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
  if (!r.opened) return
  try {
    const dec = await api.decompile(state.job.jobId, key)
    // 可能用户在这段时间点了别的并启动了别的请求；用 key 找到当时那个 tab
    const t = state.tabs.find(x => x.key === key)
    if (t) {
      t.decompile = dec
      t.busy = false
      if (!dec.ok) {
        toast('warning', `「${node ? node.key : key}」无法反编译：${dec.engine || 'unknown'}`)
      }
    }
  } catch (e) {
    const t = state.tabs.find(x => x.key === key)
    if (t) { t.decompile = null; t.busy = false; t.error = e.message }
    toast('danger', '反编译失败：' + e.message)
  }
}

/** 关闭一个 tab。若关的就是当前激活的，则激活邻居（右 → 左 → null）。状态机纯函数在 lib/tabs.js。 */
export function closeTab(key) {
  const r = closeTabImpl(state.tabs, state.activeKey, key)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
}

/** 当前激活的 tab 视图（给 DiffView/InfoPanel 用）。无激活时为 null。 */
export function activeTab() {
  return findActiveTab(state.tabs, state.activeKey)
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