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
  leftType: 'package',     // 比对输入类型：package（war/jar）| folder（解压目录）
  oldPath: 'sample_v1.war',// 老包/老目录（对比基准）
  newPath: 'sample_v2.war',// 新包/新目录（对比目标）
  tabs: [],                // 多文件对比页（DiffView 顶部 tab 栏数据源）
  activeKey: null,         // 当前激活的 tab 对应节点 key
  focusMode: false,        // 差异对比「专注模式」：隐藏左右栏，放大中间 diff 视野
  filter: 'all',
  busy: false,
  busyText: '',
  analyzing: false,        // 后台 AI 报告生成中，不弹全屏遮罩
  reporting: false,        // 生成报告锁：防止自动报告（比对后）与手动报告并发重叠
  aiPanelCollapsed: false, // 智能分析栏收起状态（与 DiffView 文件栏一键显隐联动，避免遮挡对比窗口）
  aiClassify: {},          // 智能分类结果：key -> { risk(HIGH/MEDIUM/LOW), category, reason }（A1+B2）
  classifying: false,      // 智能分类进行中
  aiEstimate: null,        // 成本闸门：AI token 预估缓存 { report, analyze, classify, threshold }
  costGate: null,          // 成本闸门：强制确认弹窗载荷 { action, estimate, threshold }（非 null 即弹窗）
  // 比对任务的实时进度（后端异步，前端轮询）：{ status, progress, phase, message } 或 null
  jobProgress: null,
  activeCompareId: null,   // 当前正在轮询的比对 jobId；变化即视为中止旧轮询（支持重开/取消）
  config: null,
  reportMd: null,
  reportAi: false,
  // 并行 AI 分析任务模型（替代原阻塞模态；每个任务独立 tab + 独立控制台窗口，互不阻塞）
  aiTasks: [],            // { id, category, title, prompt, status, thinking[], answer, error }
  aiActiveTaskId: null,   // 当前聚焦的 AI 分析任务 tab
  previewMd: null,        // 控制台「预览报告」临时报告正文（与全局 reportMd 解耦，避免互相覆盖）
  previewOpen: false,
  error: null,
  toast: null,
  // 归档展开：复合键(outer!/inner) -> { loading, error, children:[{key,status,fileClass,size,name,expandable}] }
  archiveChildren: {},
  // 当前已展开的归档复合键集合（DiffTree 用于渲染子节点 + 旋转图标）
  expandedArchives: {}
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
  // 桌面壳 Shell 集成：接收右键菜单 / 命令行传入的比对路径（Electron preload 暴露）。
  if (typeof window !== 'undefined' && window.bempdiff && typeof window.bempdiff.onShellCompare === 'function') {
    window.bempdiff.onShellCompare((paths) => { ingestShellPaths(paths) })
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
    sortByRisk: false,      // 差异树按 AI 风险等级排序（高→低）
    filterRisk: ['HIGH', 'MEDIUM', 'LOW'], // 差异树风险过滤（多选；空=不过滤）
    autoAiOnCompare: true,
    hasApiKey: false
  }
}

/** 切换差异对比「专注模式」（隐藏左右栏，放大中间 diff 视野）。 */
export function toggleFocusMode() { state.focusMode = !state.focusMode }
export function setFocusMode(v) { state.focusMode = !!v }

// 智能分析栏（.col-ai）收起/展开：与 DiffView 文件栏一键显隐联动，避免遮挡对比窗口。
// 用户显式切换时写入 localStorage，作为后续响应式避让（窄屏自动收起）的覆盖优先级。
const AI_COLLAPSE_KEY = 'bempdiff.analysisCollapsed'
const AI_AUTO_COLLAPSE_W = 1080
export function setAiPanelCollapsed(v) {
  state.aiPanelCollapsed = !!v
  localStorage.setItem(AI_COLLAPSE_KEY, String(state.aiPanelCollapsed))
}
export function toggleAiPanel() { setAiPanelCollapsed(!state.aiPanelCollapsed) }
// 响应式避让：有显式偏好（localStorage）则尊重之；否则按视口宽度自动收起（窄屏不挤占 diff）。
export function applyAiPanelResponsive() {
  const pref = localStorage.getItem(AI_COLLAPSE_KEY)
  state.aiPanelCollapsed = pref !== null ? pref === 'true' : window.innerWidth <= AI_AUTO_COLLAPSE_W
}

export async function runCompare({ leftType, leftPath, rightPath, options }) {
  state.busy = true
  state.busyText = '正在提交比对任务…'
  state.error = null
  state.aiClassify = {}      // 新一轮比对：清空上一轮智能分类结果
  state.classifying = false
  state.aiEstimate = null   // 新一轮比对：清空上一轮成本闸门预估缓存
  state.costGate = null
  state.jobProgress = { status: 'queued', progress: 0, phase: 'queued', message: '已提交，等待处理' }
  try {
    const r = await api.compare({ leftType, leftPath, rightPath, options })
    const jobId = r.jobId
    state.activeCompareId = jobId
    state.jobProgress = {
      status: r.status || 'queued',
      progress: r.progress || 0,
      phase: r.phase || 'queued',
      message: r.message || '已提交'
    }
    const final = await pollJob(jobId)
    if (!final) return // 已取消 / 被新比对取代 → 不清空旧结果，直接返回
    state.job = final
    // 重新比对：清空旧 tab（树变化，旧 key 可能已不存在）
    state.tabs = []
    state.activeKey = null
    state.reportMd = null
    state.archiveChildren = {}   // 新一轮比对：清空归档展开缓存
    state.expandedArchives = {}
    toast('success', `比对完成：新增 ${final.stats.added} · 删除 ${final.stats.deleted} · 修改 ${final.stats.modified}`)
    if (state.config && state.config.autoAiOnCompare && state.config.aiEnabled) {
      generateReport(true, { silent: true })
    }
  } catch (e) {
    state.error = e.message
    toast('danger', '比对失败：' + e.message)
  } finally {
    state.busy = false
    state.jobProgress = null
    state.activeCompareId = null
  }
}

// ---------- 比对路径入口（拖拽 / 右键菜单 / 工具栏共用） ----------

/** 由路径推断输入类型：war/jar/zip/ear 等归档 → package，其余（目录或未知）→ folder。 */
export function inferType(p) {
  return /\.(war|jar|zip|ear|tar\.gz|tgz)$/i.test(p || '') ? 'package' : 'folder'
}

/** 由当前配置构造比对 options（与 ToolBar.buildOptions 保持一致）。 */
export function buildOptions() {
  const c = state.config || {}
  return {
    expandAll: !!c.expandAll,
    topK: c.topK ?? 12,
    internalPrefixes: c.internalPrefixes || '',
    cfrJar: c.cfrJar || '',
    ignoreWhitespace: !!c.ignoreWhitespace,
    ignoreComments: !!c.ignoreComments,
    ignoreRegex: c.ignoreRegex || ''
  }
}

/** 工具栏「开始比对」按钮与拖拽/右键菜单共用入口。 */
export function triggerCompare() {
  if (!state.oldPath || !state.newPath) {
    toast('warning', '请先填写老包与新包（或目录）的路径')
    return
  }
  runCompare({
    leftType: state.leftType,
    leftPath: state.oldPath.trim(),
    rightPath: state.newPath.trim(),
    options: buildOptions()
  })
}

/**
 * 接收外部传入的比对路径（右键菜单 / 拖拽 / 命令行参数），写入工具栏并视情况自动比对。
 * 采用「pending 左 → 右」方案（Beyond Compare / WinMerge 同款）：
 *   - 收到 1 个路径：若左空则填左，否则填右；仅一个时不自动比对，提示再选一个。
 *   - 收到 ≥2 个路径：第一填左、第二填右，立即自动比对。
 *   - 左右均已存在时再收到：替换左、清空右，等待下一次补全（避免误覆盖进行中的对比）。
 * @param {string|string[]} rawPaths
 */
export function ingestShellPaths(rawPaths) {
  const paths = (Array.isArray(rawPaths) ? rawPaths : [rawPaths])
    .filter((p) => typeof p === 'string' && p.trim().length)
    .map((p) => p.trim())
  if (!paths.length) return
  const items = paths.slice(0, 2).map((p) => ({ path: p, type: inferType(p) }))
  if (items.length >= 2) {
    state.leftType = items[0].type
    state.oldPath = items[0].path
    state.newPath = items[1].path
  } else {
    const it = items[0]
    if (!state.oldPath) {
      state.leftType = it.type
      state.oldPath = it.path
    } else if (!state.newPath) {
      state.newPath = it.path
    } else {
      state.leftType = it.type
      state.oldPath = it.path
      state.newPath = ''
    }
  }
  if (state.oldPath && state.newPath) {
    toast('info', '已通过外部入口（右键菜单 / 拖拽）接收比对路径，自动开始比对')
    triggerCompare()
  } else {
    toast('info', '已接收第一个比对路径，请从资源管理器再右键一个文件/目录以完成对比')
  }
}

/**
 * 轮询比对任务直到终态。返回 DONE 时的完整结果；CANCELLED/被取代返回 null（由调用方决定不清空旧 job）。
 * 用 state.activeCompareId 作为「当前轮询是否属于本次比对」的开关：用户重开或取消会改写它，旧轮询自然退出。
 */
async function pollJob(jobId) {
  while (state.activeCompareId === jobId) {
    const s = await api.jobStatus(jobId)
    state.jobProgress = {
      status: s.status,
      progress: s.progress || 0,
      phase: s.phase || '',
      message: s.message || ''
    }
    if (s.status === 'DONE') return s
    if (s.status === 'ERROR') throw new Error(s.error || '比对失败')
    if (s.status === 'CANCELLED') return null
    await new Promise(res => setTimeout(res, 350))
  }
  return null // activeCompareId 已变（取消 / 新比对）→ 中止
}

/** 取消正在进行的比对（仅置标记；后端在阶段边界终止，前端轮询见 CANCELLED 即退出）。 */
export function cancelCompare() {
  const id = state.activeCompareId
  if (!id) return
  state.activeCompareId = null // 让当前轮询循环退出
  api.cancelJob(id).catch(() => {})
  state.busyText = '已取消'
}

/**
 * 打开 / 激活一个对比 tab。左树点击触发：
 * - key 已在 tabs 里 → 仅切 activeKey（不触发二次反编译）
 * - key 不在         → push 占位 tab 并异步反编译，结果回写到对应 tab
 *   （用 key 定位而不是索引，因为反编译期间用户可能开了其他 tab）
 * 状态机本身抽到 lib/tabs.js，便于纯函数单测。
 */
// 在途反编译请求按 key 记录，便于切换/重复点击时取消上一次同 key 请求（节省带宽与 CPU）。
const inflightDecompile = new Map()

export async function selectEntry(key) {
  if (!state.job) return
  const node = state.job.tree.find(n => n.key === key) || null
  // 调用 lib/tabs.js 的纯函数决定要不要新开
  const r = activateTabImpl(state.tabs, state.activeKey, key, node)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
  if (!r.opened) return
  // 取消上一次同一 key 的在途请求（重复点击 / 反复切换同一文件时去重）
  const prev = inflightDecompile.get(key)
  if (prev) prev.abort()
  const ac = new AbortController()
  inflightDecompile.set(key, ac)
  try {
    const dec = await api.decompile(state.job.jobId, key, { signal: ac.signal })
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
    if (e && e.name === 'AbortError') return // 被新切换/重复点击取消，静默忽略
    const t = state.tabs.find(x => x.key === key)
    if (t) { t.decompile = null; t.busy = false; t.error = e.message }
    toast('danger', '反编译失败：' + e.message)
  } finally {
    if (inflightDecompile.get(key) === ac) inflightDecompile.delete(key)
  }
}

/** 关闭一个 tab。若关的就是当前激活的，则激活邻居（右 → 左 → null）。状态机纯函数在 lib/tabs.js。 */
export function closeTab(key) {
  const r = closeTabImpl(state.tabs, state.activeKey, key)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
}

/**
 * 懒加载某归档（含嵌套归档复合键）的内部条目清单。
 * 结果按复合键缓存到 state.archiveChildren；重复调用直接复用（除非 force）。
 * 用于差异树点击归档「展开」按钮时拉取子节点。
 */
export async function fetchEntryChildren(key, force = false) {
  if (!state.job) return null
  const cached = state.archiveChildren[key]
  if (cached && !force) return cached.children || []
  // 置 loading（保证响应式更新：先建对象再赋值，避免 Vue 跳过新键）
  state.archiveChildren[key] = { loading: true, error: null, children: cached ? (cached.children || []) : [] }
  try {
    const children = await api.entryChildren(state.job.jobId, key)
    state.archiveChildren[key] = { loading: false, error: null, children }
    return children
  } catch (e) {
    state.archiveChildren[key] = { loading: false, error: e.message, children: [] }
    toast('danger', '展开归档失败：' + e.message)
    return []
  }
}

/** 切换归档展开态（点击展开按钮时调用）：展开则拉取并缓存子节点，折叠则仅清标记。 */
export async function toggleArchive(key) {
  if (state.expandedArchives[key]) {
    delete state.expandedArchives[key]
    return false
  }
  state.expandedArchives[key] = true
  await fetchEntryChildren(key, false)
  return true
}

/** 当前激活的 tab 视图（给 DiffView/InfoPanel 用）。无激活时为 null。 */
export function activeTab() {
  return findActiveTab(state.tabs, state.activeKey)
}

export async function generateReport(ai, opts = {}) {
  // 统一入口守卫：所有调用方（InfoPanel / ToolBar / 比对后自动报告）在缺失比对结果时都给明确反馈，
  // 避免「点了没反应、无遮罩、无提示」。此前 ToolBar 入口未守卫，会静默 return 导致同一症状。
  if (!state.job) { toast('warning', '请先完成一次比对，再生成报告'); return }
  if (state.reporting) return // 防止自动报告与手动报告并发重叠（后到者直接放弃，避免 reportMd 互相覆盖）
  // 成本闸门（P0 #5）：AI 报告超阈值需强制确认；静默自动报告超阈值则跳过并提示手动。
  if (ai) {
    const ok = await ensureAiBudget('report', { silent: !!opts.silent })
    if (!ok) {
      if (opts.silent) toast('warning', 'AI 报告预估超成本阈值，已跳过自动生成，请手动确认后生成')
      return
    }
  }
  state.reporting = true
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
      state.reporting = false
    }
  }

/** AI 智能分类与优先级（B2 自动打标 + A1 风险分级）：对差异树文件打标并在左树展示。 */
export async function runAiClassify() {
  if (!state.job || state.job.status !== 'DONE') { toast('warning', '请先完成一次比对，再进行智能分类'); return }
  if (state.classifying) return
  // 成本闸门（P0 #5）：超阈值强制确认
  const ok = await ensureAiBudget('classify')
  if (!ok) return
  state.classifying = true
  try {
    const projDir = (state.config && state.config.projectContextEnabled) ? (state.config.projectContextDir || null) : null
    const data = await api.classify(state.job.jobId, projDir)
    const map = {}
    for (const it of (data.items || [])) map[it.key] = { risk: it.risk, category: it.category, reason: it.reason }
    state.aiClassify = map
    const cov = data.coverage || Object.keys(map).length
    const total = data.totalChanged || 0
    toast('success', `智能分类完成：已标注 ${cov}/${total} 个变更文件`)
  } catch (e) {
    toast('danger', '智能分类失败：' + e.message)
  } finally {
    state.classifying = false
  }
}

// ---------------- 并行 AI 分析（非阻塞控制台，替代原阻塞模态） ----------------
// 每次「新建分析」生成一个独立 task 写入 state.aiTasks；后端 ai-analyze 按请求走各自虚拟线程，
// 天然支持并行。前端不再置 state.busy，故不会弹出全屏遮罩、不阻塞用户操作。
// task.status ∈ { thinking, streaming, done, error, aborted }

/** AI 分析类别目录：用户可在控制台下拉选择，并行发起多种类别的分析。 */
export const AI_CATEGORIES = [
  { key: 'risk',       label: '整体风险分析',   icon: 'bi-shield-exclamation' },
  { key: 'breaking',   label: '破坏性变更专项', icon: 'bi-exclamation-octagon' },
  { key: 'impact',     label: '影响范围分析',   icon: 'bi-diagram-3' },
  { key: 'testpoints', label: '测试要点分析',   icon: 'bi-list-check' },
  { key: 'custom',     label: '自定义问题',     icon: 'bi-chat-left-text' }
]

let aiTaskSeq = 0
// AbortController 置于 reactive 之外（原生对象被响应式包裹会破坏 .abort() 的 this 绑定）
const aiControllers = new Map()

/** 是否有 AI 分析任务正在运行（供工具栏在分析中禁用对比/路径输入，避免干扰结果）。 */
export function aiAnyRunning() {
  return state.aiTasks.some(t => t.status === 'thinking' || t.status === 'streaming')
}

/** 发起一个新的 AI 分析任务（非阻塞）。category 取自 AI_CATEGORIES；custom 时传 prompt。 */
export function startAiAnalysis(category = 'risk', prompt = '') {
  if (!state.job || state.job.status !== 'DONE') {
    toast('warning', '请先完成一次「开始比对」，再发起 AI 分析')
    return
  }
  const cat = AI_CATEGORIES.find(c => c.key === category) || AI_CATEGORIES[0]
  const id = 'ai-' + (++aiTaskSeq)
  const title = cat.key === 'custom' ? '自定义：' + (prompt || '').slice(0, 18) : cat.label
  const task = {
    id, category: cat.key, title, prompt: cat.key === 'custom' ? (prompt || '') : '',
    status: 'thinking', thinking: [], answer: '', error: '', createdAt: Date.now()
  }
  state.aiTasks.push(task)
  const t = state.aiTasks[state.aiTasks.length - 1] // 取响应式代理引用，后续 mutation 都走它
  state.aiActiveTaskId = id
  state.aiPanelCollapsed = false // 展开智能分析栏以露出控制台
  ensureAiBudget('analyze').then((ok) => {
    if (!ok) {
      t.status = 'error'
      t.error = '已取消：本次 AI 分析预估超成本阈值，未发起调用。'
      return
    }
    runAiTaskStream(t)
  })
}

/** 拉起某个 task 的 SSE 流（从头开始）。 */
function runAiTaskStream(t) {
  t.status = 'streaming'
  t.thinking = []
  t.answer = ''
  t.error = ''
  const body = { category: t.category, prompt: t.prompt || '' }
  const controller = api.analyzeStream(state.job.jobId, {
    onThinking: (d) => { t.thinking.push({ phase: d.phase || 'thinking', message: d.message || '' }) },
    onAnswer: (d) => { t.answer += (d.text || '') },
    onDone: (d) => {
      aiControllers.delete(t.id)
      t.status = (d && d.aborted) ? 'aborted' : 'done'
    },
    onError: (d) => {
      aiControllers.delete(t.id)
      t.status = 'error'
      t.error = (d && d.message) || '分析失败'
    }
  }, body)
  aiControllers.set(t.id, controller)
}

/** 中断某任务：中止 SSE 读取（服务端继续跑完也无妨，前端已停止消费）。 */
export function stopAiAnalysis(id) {
  const c = aiControllers.get(id)
  if (c) { c.abort(); aiControllers.delete(id) }
  const t = state.aiTasks.find(x => x.id === id)
  if (t && (t.status === 'thinking' || t.status === 'streaming')) t.status = 'aborted'
}

/** 重新开始某任务（复用同一 task 对象重置后重跑）。 */
export function restartAiAnalysis(id) {
  const t = state.aiTasks.find(x => x.id === id)
  if (!t) return
  stopAiAnalysis(id)
  runAiTaskStream(t)
}

/** 切换聚焦的任务 tab。 */
export function selectAiTask(id) { state.aiActiveTaskId = id }

/** 关闭某任务：先中断（若运行中）再移出列表；若关的是当前聚焦，则聚焦邻居。 */
export function closeAiTask(id) {
  stopAiAnalysis(id)
  const i = state.aiTasks.findIndex(x => x.id === id)
  if (i < 0) return
  state.aiTasks.splice(i, 1)
  if (state.aiActiveTaskId === id) {
    state.aiActiveTaskId = state.aiTasks.length ? state.aiTasks[Math.max(0, i - 1)].id : null
  }
}

/** 打开「预览报告」弹窗（展示某个 task 的 Markdown 结论，与全局 reportMd 解耦）。 */
export function openReportPreview(md) {
  if (!md) return
  state.previewMd = md
  state.previewOpen = true
}
export function closeReportPreview() {
  state.previewOpen = false
  state.previewMd = null
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

// ---------------- 成本闸门（P0 #5）：超阈值强制确认 ----------------

// 强制确认弹窗的 Promise resolve 句柄（置于 reactive 之外，避免函数被 Vue 响应式包裹）。
let costGateResolve = null

/** 用户确认成本闸门：放行被拦截的 AI 调用。 */
export function confirmCostGate() {
  const r = costGateResolve
  costGateResolve = null
  state.costGate = null
  if (r) r(true)
}

/** 用户取消成本闸门：放弃本次 AI 调用。 */
export function cancelCostGate() {
  const r = costGateResolve
  costGateResolve = null
  state.costGate = null
  if (r) r(false)
}

/**
 * 成本闸门核心：判断某 AI 入口是否可放行。
 *  - 闸门关闭（threshold<=0）→ 直接放行
 *  - 预估未超阈值 → 直接放行
 *  - 预估超阈值：
 *      - 交互入口（silent=false）→ 弹强制确认弹窗，等待用户确认/取消
 *      - 自动/静默入口（silent=true，如比对后自动报告）→ 不弹窗，返回 false 由调用方提示手动
 * action ∈ { 'report', 'analyze', 'classify' }。
 */
export async function ensureAiBudget(action, opts = {}) {
  const silent = !!opts.silent
  if (!state.config) return true
  const threshold = state.config.costGateWarnTokens || 0
  if (threshold <= 0) return true // 闸门未启用
  // 取预估（per-job 缓存；失败不阻塞，降级为放行）
  if (!state.aiEstimate && state.job) {
    try { state.aiEstimate = await api.aiEstimate(state.job.jobId) }
    catch (e) { return true }
  }
  const est = state.aiEstimate ? (state.aiEstimate[action] || 0) : 0
  if (est <= threshold) return true
  if (silent) return false
  // 超阈值 → 弹强制确认，等待用户决策
  return await new Promise((resolve) => {
    costGateResolve = resolve
    state.costGate = { action, estimate: est, threshold }
  })
}