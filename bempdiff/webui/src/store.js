import { reactive } from 'vue'
import { api } from './api/client'
import { activateTab as activateTabImpl, closeTabReducer as closeTabImpl, pinTabReducer as pinTabImpl,
  closeOtherTabsReducer as closeOtherTabsImpl, closeAllTabsReducer as closeAllTabsImpl, findActiveTab } from './lib/tabs'
import { extractFromFileName, sameBaseDifferentVersion, orderOldNew } from './lib/version'
import { loadIgnoreRules, saveIgnoreRules, addRule as addRuleFn, removeRule as removeRuleFn } from './lib/ignore'

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
  treePanelCollapsed: false, // 差异文件树栏收起状态（收起后横向空间全部让给比对窗口）
  aiPanelTab: 'file',      // 智能分析栏内容子视图：file/global/break/audit/console（控制台为独立 tab，独占内容区高度）
  aiClassify: {},          // 智能分类结果：key -> { risk(HIGH/MEDIUM/LOW), category, reason }（A1+B2）
  classifying: false,      // 智能分类进行中
  exporting: false,        // 差异资产导出中（驱动导出按钮的加载态，避免重复点击）
  exportProgress: null,    // 同步导出实时进度 { loaded, total, percent, etaText } 或 null
  exportRecords: [],       // 下载管理：导出记录列表（含异步进行中/已完成）
  syncExports: loadSyncExports(), // 下载管理：同步导出资产（本机浏览器下载目录，客户端持久化以便回溯）
  exportsOpen: false,      // 下载管理面板开关
  aiEstimate: null,        // 成本闸门：AI token 预估缓存 { report, analyze, classify, threshold }
  aiEstimateKey: null,     // 成本闸门：当前 aiEstimate 对应的缓存键（category|prompt 指纹），用于按分析项失效缓存
  costGate: null,          // 成本闸门：强制确认弹窗载荷 { action, estimate, threshold }（非 null 即弹窗）
  // 比对任务的实时进度（后端异步，前端轮询）：{ status, progress, phase, message } 或 null
  jobProgress: null,
  compareStartedAt: null,  // RUNNING 起始时刻（用于比对超时提醒）
  compareStalled: false,   // RUNNING 持继超过阈值仍未结束
  stallNotified: false,    // 超时提醒已弹（仅一次），避免重复打扰
  activeCompareId: null,   // 当前正在轮询的比对 jobId；变化即视为中止旧轮询（支持重开/取消）
  config: null,
  // 项目级上下文（上下文目录递归识别）状态：{ loading, data, error }
  // data 来自 /api/ai/context：{ ok, rootPath, projectCount, scannedAt, cacheFile, fromCache, javaFileCount, projects[], message }
  aiContext: { loading: false, data: null, error: '' },
  reportMd: null,
  reportAi: false,
  // 报告缓存（按类别维度，修复「多类别报告并存互相覆盖」）：key = 'ai:<category>' | 'base' → markdown。
  // reportMd 始终指向「当前展示」的报告；切换分析项时命中缓存可秒开，无需重复调用后端。
  reportCache: {},
  // 并行 AI 分析任务模型（替代原阻塞模态；每个任务独立 tab + 独立控制台窗口，互不阻塞）
  aiTasks: [],            // { id, category, title, prompt, status, thinking[], answer, error }
  aiActiveTaskId: null,   // 当前聚焦的 AI 分析任务 tab
  aiSelCategory: 'risk',  // 「新建分析 / 生成报告(AI)」共享的当前分析项（risk/breaking/impact/testpoints/custom）
  reportCategory: null,   // 最近一次生成报告所用的分析项（ReportPreview「重新生成」沿用；null=基础报告/默认整体分析）
  previewMd: null,        // 控制台「预览报告」临时报告正文（与全局 reportMd 解耦，避免互相覆盖）
  previewOpen: false,
  error: null,
  toast: null,
  // 归档展开：复合键(outer!/inner) -> { loading, error, children:[{key,status,fileClass,size,name,expandable}] }
  archiveChildren: {},
  // 当前已展开的归档复合键集合（DiffTree 用于渲染子节点 + 旋转图标）
  expandedArchives: {},
  // 右键菜单（BCompare 风格）相关状态
  excludedKeys: {},        // 排除（视图临时隐藏，可一键恢复）：key -> true
  ignoreRules: [],         // 忽略规则（持久化 localStorage）：[{ type: 'exact'|'prefix'|'name'|'ext', value }]
  baseFolder: null,        // 基准文件夹 key（folder 模式，右键「设为基准文件夹」）
  propertyNode: null,      // 「属性」弹窗目标节点快照（非 null 即显示）
  // 差异树定位请求（PathBar 面包屑点击目录段 / 编辑框确认目录时触发）：
  // { prefix, seq } —— prefix 为相对路径前缀（'' = 顶部），seq 自增触发 DiffTree watch
  treeLocate: { prefix: null, seq: 0 },
  // AI 模型列表（按 API Base URL + Key 自动获取），供配置中心「模型名称」字段下拉/补全
  aiModels: []
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
  state.ignoreRules = loadIgnoreRules() // 右键菜单「忽略」规则：跨会话持久化
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
    ignoreExtensions: [], // 比对级忽略扩展名（多选，如 .log/.tmp）：解析收集阶段直接跳过，不参与差异比对
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
    treeViewMode: 'tree',     // 差异树展示模式：'tree'(目录树：按目录层级递归展开) | 'list'(全平摊)
    treeSortMode: 'path',     // 差异树排序方式（仅 list 模式生效；tree 模式强制 path）
    sortByRisk: false,      // 差异树按 AI 风险等级排序（高→低）
    filterRisk: ['HIGH', 'MEDIUM', 'LOW'], // 差异树风险过滤（多选；空=不过滤）
    autoAiOnCompare: true,
    // 自动逐层解包（WAR/ZIP/JAR 嵌套归档多线程物理展开）：默认开启，比对时后端多线程逐层解包，
    // AI/导出覆盖嵌套子文件；解包完成后才置 DONE（解包中 AI/导出被禁用）。
    unpackNested: true,
    unpackThreads: 4,        // 解包并发线程数
    unpackMaxDepth: 6,       // 最大递归解包深度
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
// 差异文件树栏（.col-tree）收起/展开：手动切换，不做窄屏自动避让（树栏无自动收起需求）。
export function setTreePanelCollapsed(v) { state.treePanelCollapsed = !!v }
export function toggleTreePanel() { setTreePanelCollapsed(!state.treePanelCollapsed) }
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
  state.aiEstimateKey = null
  state.costGate = null
  state.excludedKeys = {}   // 新一轮比对：清空排除/基准/属性弹窗（树已重建）
  state.baseFolder = null
  state.propertyNode = null
  state.jobProgress = { status: 'queued', progress: 0, phase: 'queued', message: '已提交，等待处理' }
  try {
    const r = await api.compare({ leftType, leftPath, rightPath, options })
    const jobId = r.jobId
    // 智能识别：后端已按版本自动排序（同名不同版本）
    if (r.autoOrdered) {
      const vOld = r.oldVersion || '?'
      const vNew = r.newVersion || '?'
      toast('info', `已识别为同一包的不同版本，自动按版本排序：旧 ${vOld} → 新 ${vNew}`)
    }
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
    // 重新比对：中止并清空旧 AI 分析任务（旧任务与旧树/旧 job 不再对应，避免残留任务对新 job 误跑）
    for (const t of state.aiTasks) { const c = aiControllers.get(t.id); if (c) c.abort() }
    aiControllers.clear()
    state.aiTasks = []
    state.aiActiveTaskId = null
    // 重新比对：清空旧 tab（树变化，旧 key 可能已不存在）
    state.tabs = []
    state.activeKey = null
    state.reportMd = null
    state.reportCache = {}   // 新一轮比对：清空报告缓存（旧报告与旧树 key 不再对应）
    state.archiveChildren = {}   // 新一轮比对：清空归档展开缓存
    state.expandedArchives = {}
    toast('success', `比对完成：新增 ${final.stats.added} · 删除 ${final.stats.deleted} · 修改 ${final.stats.modified}`)
    // 自动逐层解包展示（B 机制）：比对完成即后台递归展开所有归档节点（/api/entry/recursive），
    // 无需手动点击即可查看嵌套包内部；异步 fire-and-forget，不阻塞差异树首屏渲染。
    if (state.config && state.config.unpackNested !== false) autoExpandArchives(final.tree)
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
    ignoreRegex: c.ignoreRegex || '',
    ignoreExtensions: Array.isArray(c.ignoreExtensions) ? c.ignoreExtensions : [],
    // 自动逐层解包：默认开启（后端多线程物理解包），可配线程数与深度
    unpackNested: c.unpackNested !== false,
    unpackThreads: Math.max(1, Number(c.unpackThreads) || 4),
    unpackMaxDepth: Math.max(1, Number(c.unpackMaxDepth) || 6)
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
    // 智能识别：两个同为压缩包且同名不同版本 → 自动按版本排序（旧→新），无需手动分辨新旧
    let pOld = items[0].path
    let pNew = items[1].path
    if (items[0].type === 'package' && items[1].type === 'package'
        && sameBaseDifferentVersion(pOld, pNew)) {
      const ordered = orderOldNew(pOld, pNew)
      pOld = ordered[0]
      pNew = ordered[1]
      toast('info', `已识别为同一包的不同版本（${extractFromFileName(pOld)} → ${extractFromFileName(pNew)}），自动开始比对`)
    }
    state.leftType = items[0].type
    state.oldPath = pOld
    state.newPath = pNew
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
    // 比对超时提醒：RUNNING 持续超过阈值仍未结束，触发一次显式通知（不阻塞、不中断比对）。
    // 竞态下后端可能在 RUNNING 时拒绝 AI 分析/智能分类（“比对尚未完成: RUNNING”），此提示与之一致。
    if (s.status === 'RUNNING') {
      if (!state.compareStartedAt) state.compareStartedAt = Date.now()
      if (!state.compareStalled && Date.now() - state.compareStartedAt > COMPARE_TIMEOUT_MS) {
        state.compareStalled = true
        if (!state.stallNotified) {
          state.stallNotified = true
          toast('warning', '比对进行已超过 ' + Math.round(COMPARE_TIMEOUT_MS / 60000) + ' 分钟仍未完成；可继续等待，若长时间无进展请检查磁盘/网络后重启工具')
        }
      }
    } else if (s.status === 'DONE' || s.status === 'ERROR' || s.status === 'CANCELLED') {
      // 终态：清空超时统计，下次比对重新计数/提醒
      state.compareStartedAt = null
      state.compareStalled = false
      state.stallNotified = false
    }
    if (s.status === 'DONE') return s
    if (s.status === 'ERROR') throw new Error(s.error || '比对失败')
    if (s.status === 'CANCELLED') return null
    // 退避轮询：解包等瞬态阶段高频捕捉 phase；稳定运行阶段降频（终态已在循环内 return，不受此等待影响）
    const waitMs = s.phase === 'unpacking' ? POLL_FAST_MS : POLL_NORM_MS
    await new Promise(res => setTimeout(res, waitMs))
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
        toast('warning', `「${node ? node.key : key}」无法反编译：${dec.error || dec.engine || 'unknown'}`)
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

/** 切换「固定/取消固定」当前 tab：pinned 置顶，关闭类操作会跳过它。 */
export function pinTab(key) {
  const r = pinTabImpl(state.tabs, state.activeKey, key)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
}

/** 关闭除 keepKey 之外的所有 tab（保留固定项）。 */
export function closeOtherTabs(keepKey) {
  const r = closeOtherTabsImpl(state.tabs, state.activeKey, keepKey)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
}

/** 关闭全部 tab（保留固定项）。 */
export function closeAllTabs() {
  const r = closeAllTabsImpl(state.tabs, state.activeKey)
  state.tabs = r.tabs
  state.activeKey = r.activeKey
}

// ---------- 差异树右键菜单（BCompare 风格）状态动作 ----------

/** 排除某条目（视图临时隐藏，可一键恢复）。返回当前被排除总数。 */
export function excludeEntry(key) {
  if (!key) return 0
  state.excludedKeys[key] = true
  return Object.keys(state.excludedKeys).length
}

/** 恢复全部被排除的条目。 */
export function restoreAllExcluded() {
  state.excludedKeys = {}
  toast('info', '已恢复全部被排除的条目')
}

/** 添加忽略规则（同类型同值去重），持久化 localStorage。 */
export function addIgnoreRule(type, value) {
  const next = addRuleFn(state.ignoreRules, type, value)
  if (next.length === state.ignoreRules.length) return
  state.ignoreRules = next
  saveIgnoreRules(state.ignoreRules)
  toast('success', '已添加忽略规则，树中匹配条目将被隐藏')
}

/** 按下标删除忽略规则并持久化。 */
export function removeIgnoreRule(index) {
  state.ignoreRules = removeRuleFn(state.ignoreRules, index)
  saveIgnoreRules(state.ignoreRules)
}

/** 清空全部忽略规则并持久化。 */
export function clearIgnoreRules() {
  state.ignoreRules = []
  saveIgnoreRules(state.ignoreRules)
  toast('info', '已清空忽略规则')
}

/** 设置/取消基准文件夹（文件夹对象右键「设为基准文件夹」，再次点击取消）。 */
export function setBaseFolder(key) {
  state.baseFolder = (state.baseFolder === key) ? null : key
}

/** 打开「属性」弹窗（目标节点快照，非 null 即显示）。 */
export function showProperties(node) {
  state.propertyNode = node
}
export function hideProperties() {
  state.propertyNode = null
}

/**
 * 请求差异树定位到某路径层级（PathBar 面包屑点击目录段 / 编辑框确认目录时调用）。
 * prefix：相对路径前缀（如 'WEB-INF/lib'，'' = 定位到树顶部）。
 * DiffTree watch state.treeLocate.seq 后滚动到第一个匹配节点并 flash 高亮。
 */
export function locateTreePrefix(prefix) {
  state.treeLocate = { prefix: prefix || null, seq: (state.treeLocate.seq || 0) + 1 }
}

/** 右键「AI功能总结」：对单个文件发起 AI 分析任务（复用 AiConsole 流式管线）。 */
export function startFileAiSummary(node) {
  if (!node || !state.job) {
    toast('warning', '请先完成一次「开始比对」')
    return
  }
  // 传 fileKey 走后端「单文件聚焦」管线：仅计算并分析该文件的差异内容，
  // 而非把文件名拼进自定义 prompt（那样后端仍分析全量差异，结论与整体风险分析雷同）。
  // fileStatus 必须透传：归档内部条目（复合键）不在后端顶层 DiffResult 里，
  // 后端 statusOf 对其一律误判 MODIFIED，前端树节点的 status 才是权威值（评审 H1）
  startAiAnalysis('file', '', { fileKey: node.key, fileStatus: node.status || '' })
}

/**
 * 懒加载某归档（含嵌套归档复合键）的内部条目清单（单层）。
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

/**
 * 自动递归解包：一次拉取完整嵌套差异树（后端逐层递归展开所有嵌套压缩包，
 * 受深度/节点护栏约束），并把每个嵌套归档的 children 落缓存、标记展开。
 * 用于「点开一个 zip，其内部所有嵌套 zip 自动逐层展开对比」，无需逐级手动点击。
 */
export async function fetchRecursiveTree(key) {
  if (!state.job) return null
  state.archiveChildren[key] = { loading: true, error: null, children: (state.archiveChildren[key] && state.archiveChildren[key].children) || [] }
  try {
    const tree = await api.entryRecursive(state.job.jobId, key)
    const kids = tree.children || []
    state.archiveChildren[key] = { loading: false, error: null, children: kids }
    state.expandedArchives[key] = true
    seedArchiveTree(kids)
    return kids
  } catch (e) {
    // 兼容旧后端（无 /api/entry/recursive 路由返回 404「未知 entry 操作」）：降级为单层展开，
    // 嵌套归档仍可逐级手动点击展开，避免展开失败硬报错阻塞比对浏览。
    try {
      const kids = await api.entryChildren(state.job.jobId, key)
      state.archiveChildren[key] = { loading: false, error: null, children: kids }
      state.expandedArchives[key] = true
      toast('warning', '后端未启用自动递归解包（服务端版本过旧），已降级为单层展开，可逐级点击展开嵌套归档')
      return kids
    } catch (e2) {
      state.archiveChildren[key] = { loading: false, error: e2.message, children: [] }
      toast('danger', '展开归档失败：' + e2.message)
      return []
    }
  }
}

/** 把递归解包返回的嵌套树写入缓存并标记全部嵌套归档为展开态（后端已按深度护栏截断）。 */
function seedArchiveTree(nodes) {
  for (const n of nodes) {
    if (Array.isArray(n.children) && (n.fileClass === 'ARCHIVE' || n.fileClass === 'JAR')) {
      state.archiveChildren[n.key] = { loading: false, error: null, children: n.children }
      state.expandedArchives[n.key] = true
      seedArchiveTree(n.children)
    }
  }
}

/**
 * 自动递归展开所有顶层归档节点（B 机制）：比对完成后由 runCompare 触发，无需手动点击即可
 * 查看嵌套包内部。顺序逐个拉取（/api/entry/recursive，一次返回完整嵌套树 + 递归标记展开），
 * 单节点失败不影响其余（try/catch 吞掉，避免一个坏归档阻塞整体浏览）。
 */
async function autoExpandArchives(tree) {
  if (!Array.isArray(tree)) return
  for (const n of tree) {
    if (n && (n.fileClass === 'ARCHIVE' || n.fileClass === 'JAR')) {
      try { await fetchRecursiveTree(n.key) } catch (_) { /* 单点失败不阻塞其余归档展开 */ }
    }
  }
}

/**
 * 切换归档展开态（点击展开按钮时调用）：展开则自动递归解包全部嵌套层并缓存子节点，
 * 折叠则仅清标记。
 */
export async function toggleArchive(key) {
  if (state.expandedArchives[key]) {
    delete state.expandedArchives[key]
    return false
  }
  state.expandedArchives[key] = true
  await fetchRecursiveTree(key) // 自动解包并逐层递归对比嵌套压缩包内容
  return true
}

/** 当前激活的 tab 视图（给 DiffView/InfoPanel 用）。无激活时为 null。 */
export function activeTab() {
  return findActiveTab(state.tabs, state.activeKey)
}

/** 设置共享的「分析项」选择（AiConsole 新建分析下拉 / 工具栏生成报告(AI) 共用）。 */
export function setAiSelCategory(v) { state.aiSelCategory = v }

export async function generateReport(ai, opts = {}) {
  // 统一入口守卫：所有调用方（InfoPanel / ToolBar / ReportPreview / 比对后自动报告）在缺失比对结果时都给明确反馈，
  // 避免「点了没反应、无遮罩、无提示」。此前 ToolBar 入口未守卫，会静默 return 导致同一症状。
  if (!state.job) { toast('warning', '请先完成一次比对，再生成报告'); return }
  if (state.reporting) return // 防止自动报告与手动报告并发重叠（后到者直接放弃，避免 reportMd 互相覆盖）
  // 分析项：显式传入优先（InfoPanel 破坏性→breaking / 审计→risk / ReportPreview 沿用上次）；否则用共享下拉当前选择。
  const category = opts.category || state.aiSelCategory
  const prompt = opts.prompt || null
  // 报告缓存键：'ai:<category>' 区分各 AI 分析项，'base' 为无 AI 基础报告——多类别报告并存互不覆盖。
  const cacheKey = ai ? 'ai:' + (category || 'default') : 'base'
  // 缓存命中短路：同类别报告已生成过则直接展示（秒开），无需再走成本闸门/后端调用。
  // opts.force=true（ReportPreview「重新生成」）强制刷新，绕过缓存。
  if (!opts.force && state.reportCache[cacheKey]) {
    state.reportMd = state.reportCache[cacheKey]
    state.reportAi = ai
    state.reportCategory = ai ? category : null
    if (!opts.silent) toast('info', '已展示该分析项的报告缓存（如需刷新请点「重新生成」）')
    return
  }
  // 成本闸门（P0 #5）：AI 报告超阈值需强制确认；静默自动报告超阈值则跳过并提示手动。
  if (ai) {
    const ok = await ensureAiBudget('report', { silent: !!opts.silent, category, prompt })
    if (!ok) {
      if (opts.silent) toast('warning', 'AI 报告预估超成本阈值，已跳过自动生成，请手动确认后生成')
      return
    }
  }
  // 项目级上下文目录：配置开启时随请求透传（后端未收到时亦会回退服务端配置，双保险）
  const projDir = (state.config && state.config.projectContextEnabled) ? (state.config.projectContextDir || null) : null
  state.reporting = true
  const silent = !!opts.silent
  if (silent) state.analyzing = true
  else { state.busy = true; state.busyText = ai ? '正在调用 AI 分析…' : '正在生成报告…' }
  try {
    const md = await api.report(state.job.jobId, ai, category, prompt, projDir)
    state.reportCache[cacheKey] = md
    state.reportMd = md
    state.reportAi = ai
    state.reportCategory = ai ? category : null   // 记录本次分析项，供 ReportPreview「重新生成」沿用
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
  // 比对进行中（含解析/解包/对比）直接拦截并给进度引导，避免竞态下后端返回「409 比对尚未完成: RUNNING」。
  if (!state.job || state.job.status !== 'DONE') { toast('warning', '请先完成一次比对，再进行智能分类'); return }
  if (isRunning()) { toast('warning', '比对仍在进行中，请等待比对完成后，再进行智能分类'); return }
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
    const msg = (e && e.message) || String(e)
    toast('danger', '智能分类失败：' + msg)
    // 竞态/结果吻合：后端在比对 RUNNING 时拒绝（“比对尚未完成: RUNNING”）。
    // 不改错误含义，仅补充操作指引；底部「比对进行中」横条会同步显示归属阶段。
    if (/RUNNING|尚未完成|比对还在|仍不是 DONE/i.test(msg)) {
      toast('info', '比对仍在进行中，请在底部查看“比对进行中”的进度说明，等待比对完成后重试。若超过 5 分钟无进展，请检查磁盘/网络后重启工具。')
    }
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

/** 是否正处于「逐层解包」阶段。解包是异步多线程逐层展开，后端 phase=unpacking 时禁止一切 AI 分析
 *  与报告生成（须待解包完全完成、快照就绪后方可分析，否则分析不全面）。
 *  与 aiAnyRunning 一样作单一判定源，供 StatusBar/AiConsole/ToolBar 共用，避免双处维护漂移。 */
export function isUnpacking() {
  return !!state.jobProgress && state.jobProgress.phase === 'unpacking'
}

/** 比对超时提醒阈值：RUNNING 持续超过此值时提示用户（默认 5 分钟）。 */
const COMPARE_TIMEOUT_MS = 5 * 60 * 1000
// 轮询退避：仅「自动迭代解包」等瞬态阶段需高频轮询以捕捉瞬时 phase（此前一律 80ms 造成比对全程高频请求）；
// 稳定运行阶段用更低频，减少比对生命周期内的无谓后端请求。
const POLL_FAST_MS = 80
const POLL_NORM_MS = 200

/** 是否正处于比对进行中（QUEUED/RUNNING，覆盖解析/解包/比对各阶段）。 */
export function isRunning() {
  const st = (state.jobProgress && state.jobProgress.status) || ''
  return st === 'RUNNING' || st === 'QUEUED' || st === 'queued'
}

/** 当前处理阶段的可读文案（与“比对尚未完成: RUNNING”逻辑呼应）。 */
export function phaseLabel() {
  const ph = (state.jobProgress && state.jobProgress.phase) || ''
  switch (ph) {
    case 'parsing': return '正在解析包…'
    case 'unpacking': return '正在自动迭代解包…'
    case 'diffing': return '正在比对 / 计算差异…'
    default: return '比对进行中…'
  }
}

function fileSummaryLabel(fileKey) {
  const k = String(fileKey || '')
  const i = k.lastIndexOf('!/')
  if (i < 0) return k.split('/').pop() || k
  const outer = k.slice(0, i).split('/').pop()
  const inner = k.slice(i + 2).split('/').pop()
  return outer + '!/' + inner
}

/** 发起一个新的 AI 分析任务（非阻塞）。category 取自 AI_CATEGORIES；custom 时传 prompt；
 *  opts.fileKey 非空时为单文件「AI功能总结」任务（仅分析该文件）。 */
export function startAiAnalysis(category = 'risk', prompt = '', opts = {}) {
  // 比对进行中（含解析/解包/对比）直接拦截并给进度引导，避免竞态下后端返回「409 比对尚未完成: RUNNING」。
  if (isRunning()) {
    toast('warning', '比对仍在进行中，请等待比对完成后，再发起 AI 分析')
    return
  }
  if (!state.job || state.job.status !== 'DONE') {
    toast('warning', '请先完成一次「开始比对」，再发起 AI 分析')
    return
  }
  // 'file' 不在下拉目录中（仅右键入口使用），找不到时构造虚拟类别避免误标为「整体风险分析」
  const cat = AI_CATEGORIES.find(c => c.key === category) || { key: category, label: '文件总结', icon: 'bi-file-earmark-text' }
  const fileKey = opts.fileKey || null
  const fileStatus = opts.fileStatus || null
  const id = 'ai-' + (++aiTaskSeq)
  const title = cat.key === 'custom'
    ? '自定义：' + (prompt || '').slice(0, 18)
    : (fileKey ? '文件总结：' + fileSummaryLabel(fileKey) : cat.label)
  const task = {
    id, category: cat.key, title, prompt: cat.key === 'custom' ? (prompt || '') : '',
    fileKey, fileStatus,
    status: 'thinking', thinking: [], answer: '', error: '', createdAt: Date.now(),
    alive: true,          // 任务存活令牌：中断/关闭后置 false；闸门等待窗口内被中断则不再启动流（评审 P0 #2）
    thinkingCollapsed: true // 思维链折叠态（显式初始化，避免依赖 undefined 隐式语义，评审 P2 #12）
  }
  state.aiTasks.push(task)
  const t = state.aiTasks[state.aiTasks.length - 1] // 取响应式代理引用，后续 mutation 都走它
  state.aiActiveTaskId = id
  state.aiPanelCollapsed = false // 展开智能分析栏以露出控制台
  state.aiPanelTab = 'console'    // 自动切到「控制台」tab，确保用户即时看到流式输出
  ensureAiBudget('analyze', { category: cat.key, prompt: cat.key === 'custom' ? (prompt || '') : '', fileKey }).then((ok) => {
    if (!t.alive) return // 等待闸门期间任务已被中断/关闭，丢弃，不复活（评审 P0 #2）
    if (!ok) {
      t.status = 'error'
      t.error = '已取消：本次 AI 分析预估超成本阈值，未发起调用。'
      return
    }
    runAiTaskStream(t)
  })
}

/** 拉起某个 task 的 SSE 流（从头开始）。
 *  回调归属守卫：若该 task 已被「重新分析/关闭」换上新 controller，旧流的异步回调
 *  （onThinking/onAnswer/onDone/onError）一律忽略，避免污染新流答案或误删新 controller。 */
function runAiTaskStream(t) {
  t.status = 'streaming'
  t.thinking = []
  t.answer = ''
  t.error = ''
  const body = { category: t.category, prompt: t.prompt || '' }
  // 单文件任务：透传 fileKey/fileStatus，后端走「单文件聚焦」管线（仅分析该文件）；
  // fileStatus 为前端树节点权威变更类型，复合键场景后端无法自行判定（评审 H1）
  if (t.fileKey) {
    body.fileKey = t.fileKey
    if (t.fileStatus) body.fileStatus = t.fileStatus
  }
  // 项目级上下文目录：配置开启时随流式分析透传（后端未收到时亦会回退服务端配置）
  if (state.config && state.config.projectContextEnabled && state.config.projectContextDir) {
    body.projectDir = state.config.projectContextDir
  }
  const controller = api.analyzeStream(state.job.jobId, {
    onThinking: (d) => {
      // 仅当当前流的 controller 仍是本 controller 时才写入（防止陈旧流污染）
      if (aiControllers.get(t.id) !== controller) return
      t.thinking.push({ phase: d.phase || 'thinking', message: d.message || '' })
    },
    onAnswer: (d) => {
      if (aiControllers.get(t.id) !== controller) return
      t.answer += (d.text || '')
    },
    onDone: (d) => {
      if (aiControllers.get(t.id) !== controller) return
      aiControllers.delete(t.id)
      t.status = (d && d.aborted) ? 'aborted' : 'done'
    },
    onError: (d) => {
      if (aiControllers.get(t.id) !== controller) return
      aiControllers.delete(t.id)
      t.status = 'error'
      t.error = (d && d.message) || '分析失败'
    }
  }, body)
  aiControllers.set(t.id, controller)
}

/** 中断某任务：中止 SSE 读取（服务端继续跑完也无妨，前端已停止消费）；置 alive=false 防止闸门未决窗口内复活（评审 P0 #2）。 */
export function stopAiAnalysis(id) {
  const c = aiControllers.get(id)
  if (c) { c.abort(); aiControllers.delete(id) }
  const t = state.aiTasks.find(x => x.id === id)
  if (t) {
    t.alive = false
    if (t.status === 'thinking' || t.status === 'streaming') t.status = 'aborted'
  }
}

/** 重新开始某任务（复用同一 task 对象重置后重跑）。同样受成本闸门约束，放行后才重跑（评审 P0 #3）。 */
export function restartAiAnalysis(id) {
  const t = state.aiTasks.find(x => x.id === id)
  if (!t) return
  stopAiAnalysis(id) // 中断旧流 + alive=false
  t.alive = true     // 标记本任务将继续；等待闸门期间若被再次中断/关闭则置回 false
  t.status = 'thinking'
  t.thinking = []
  t.answer = ''
  t.error = ''
  ensureAiBudget('analyze', { category: t.category, prompt: t.prompt, fileKey: t.fileKey }).then((ok) => {
    if (!t.alive) return // 等待闸门期间任务被中断/关闭，丢弃
    if (!ok) {
      t.status = 'error'
      t.error = '已取消：本次 AI 分析预估超成本阈值，未发起调用。'
      return
    }
    runAiTaskStream(t)
  })
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

export function downloadExport() {
  // 兼容旧入口：一律走「小包同步 / 大包异步」的新机制。
  return startExport()
}

/** 格式化字节为可读字符串。 */
export function fmtBytes(n) {
  if (!n || n <= 0) return '—'
  const u = ['B', 'KB', 'MB', 'GB']
  let i = 0, v = Number(n)
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(v >= 100 || i === 0 ? 0 : 1)} ${u[i]}`
}

/** 打开/关闭「下载管理」面板，并拉取最新导出记录。 */
export function openExports() { state.exportsOpen = true; refreshExports() }
export function closeExports() { state.exportsOpen = false }
export function toggleExports() {
  state.exportsOpen = !state.exportsOpen
  if (state.exportsOpen) refreshExports()
}
export async function refreshExports(list) {
  try {
    const r = await api.exportList()
    if (Array.isArray(r)) { state.exportRecords = r; return r }
  } catch (e) { /* 后端不可用等，静默 */ }
  return state.exportRecords
}
export async function deleteExport(id) {
  try {
    await api.exportDelete(id)
    state.exportRecords = state.exportRecords.filter(x => x.id !== id)
  } catch (e) {
    toast('danger', '删除导出记录失败：' + (e.message || e))
  }
}

// ---- 同步导出资产（客户端记录，随浏览器下载目录留存，供「下载管理」回溯） ----
const SYNC_EXPORT_KEY = 'bempdiff-sync-exports'
const SYNC_EXPORT_CAP = 50 // 菜单项数量上限：仅保留最近 50 条，避免无限累积
/** 从 localStorage 载入同步导出记录（数组缺失/损坏时兜底为空）。 */
function loadSyncExports() {
  try {
    const raw = localStorage.getItem(SYNC_EXPORT_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch (e) { return [] }
}
/** 记录一次同步导出完成（unshift 最新在前，超出上限截断并持久化）。 */
export function recordSyncExport(entry) {
  const next = [{ filename: entry.filename, size: entry.size || 0, createdAt: entry.createdAt || Date.now() }, ...state.syncExports]
  const capped = next.slice(0, SYNC_EXPORT_CAP)
  state.syncExports = capped
  try { localStorage.setItem(SYNC_EXPORT_KEY, JSON.stringify(capped)) } catch (e) { /* 存储不可用则仅内存 */ }
}
/** 从同步导出列表中移除一条（按 createdAt 定位，仅本机记录，不影响浏览器已下载文件）。 */
export function removeSyncExport(createdAt) {
  state.syncExports = state.syncExports.filter(x => x.createdAt !== createdAt)
  try { localStorage.setItem(SYNC_EXPORT_KEY, JSON.stringify(state.syncExports)) } catch (e) { /* 忽略 */ }
}

// 当前同步导出请求的取消句柄；非空表示导出进行中，可被用户取消（底部状态栏“导出中”旁的 ×）。
let exportAbort = null

/** 主动取消本次导出：中断请求、恢复按钮与提示（取消/异常均走 startExport 的 finally 兜底复位）。 */
export function cancelExport() {
  const c = exportAbort
  exportAbort = null
  if (c) { try { c.abort() } catch (e) { /* 忽略 */ } }
  state.exporting = false
  state.exportProgress = null
  toast('info', '已取消导出')
}

/** 差异资产导出入口：由后端按规模分流 —— 小包同步流式（读流显示进度）、大包异步（后台生成 + 下载管理页）。 */
export async function startExport() {
  if (!state.job || state.exporting) return
  // 解包中禁止导出：须待自动逐层解包比对完成后（job DONE）方可导出差异资产；
  // 后端 /export/start 同样以 status==DONE 兜底 409，此处前端先行拦截以给出友好提示。
  if (isUnpacking()) { toast('warning', '正在自动逐层解包，请稍候解包完成后导出'); return }
  state.exporting = true
  state.exportProgress = null
  const ctrl = new AbortController()
  exportAbort = ctrl
  try {
    const res = await fetch(`/api/job/${encodeURIComponent(state.job.jobId)}/export/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
      signal: ctrl.signal
    })
    const ct = (res.headers.get('content-type') || '').toLowerCase()
    if (ct.includes('application/json')) {
      // 大包：异步任务已启动，写入下载管理并轮询状态
      let rec
      try { rec = await res.json() } catch (e) { rec = {} }
      toast('info', `导出已启动，预计 ${rec.etaText || '若干时间'} 完成，可在「下载管理」查看`)
      await refreshExports(rec)
      // 方案 B：不自动弹出「下载管理」面板——避免覆盖工具栏区域的导出下拉菜单（遮挡其可见项/导出按钮）。
      // 保持『导出差异资产』为纯手动触发：用户按上一条 toast 指引点「下载管理」（openExports）再查看/下载。
      if (rec && rec.id) pollExport(rec.id)
    } else {
      // 小包：同步读流，展示实时进度；完成后触发浏览器保存
      await consumeExportStream(res)
    }
  } catch (e) {
    // 用户取消（AbortError）不应视为失败；取消提示已由 cancelExport 给出。
    if (!(e && e.name === 'AbortError')) {
      toast('danger', '导出失败：' + (e.message || e))
    }
  } finally {
    exportAbort = null
    state.exporting = false
    state.exportProgress = null
  }
}

/** 同步导出：读取响应体并实时更新进度，写完转为 Blob 下载。 */
async function consumeExportStream(res) {
  if (!res || !res.ok) {
    let msg = `${res ? res.status : ''} ${res ? res.statusText : '无法连接'}`
    try { if (res) { const j = await res.json(); if (j && j.error) msg = j.error } } catch (e) { /* 非 JSON 忽略 */ }
    throw new Error(msg || '导出失败')
  }
  const total = Number(res.headers.get('content-length') || 0)
  const reader = res.body.getReader()
  const chunks = []
  let loaded = 0
  const started = Date.now()
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      if (value) { chunks.push(value); loaded += value.length }
      if (total > 0) {
        const percent = Math.min(100, Math.round((loaded / total) * 100))
        const speed = loaded / Math.max(1, (Date.now() - started) / 1000)
        const remainSec = speed > 0 ? Math.max(0, (total - loaded) / speed) : 0
        state.exportProgress = {
          loaded, total, percent,
          etaText: fmtEta(remainSec)
        }
      }
    }
  } finally {
    reader.releaseLock()
  }
  const blob = new Blob(chunks, { type: 'application/zip' })
  const url = URL.createObjectURL(blob)
  const filename = `bempdiff-export-${state.job.jobId}.zip`
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
  toast('success', '导出完成，已开始下载')
  // 同步导出同样记入客户端「下载管理」的同步资产列表，便于回溯（文件本体存于浏览器下载目录）。
  recordSyncExport({ filename, size: loaded, createdAt: Date.now() })
}

function fmtEta(secs) {
  if (!secs || secs <= 0) return ''
  if (secs < 60) return `约剩 ${Math.ceil(secs)} 秒`
  return `约剩 ${Math.ceil(secs / 60)} 分钟`
}

/** 异步导出：轮询记录直至 done/error，done 后刷新下载管理列表。 */
async function pollExport(id, attempts = 0) {
  try {
    const rec = await api.exportGet(id)
    if (rec && (rec.status === 'done' || rec.status === 'error')) {
      await refreshExports()
      if (rec.status === 'done') {
        toast('success', '导出完成，点击「下载管理」中的下载即可获取')
      } else {
        toast('danger', rec.message || '导出失败，请在下载管理查看')
      }
      return
    }
    if (attempts < 600) {
      setTimeout(() => pollExport(id, attempts + 1), 2000)
    }
  } catch (e) {
    // 网络抖动等，隔几秒重试直至不再增长
    if (attempts < 120) setTimeout(() => pollExport(id, attempts + 1), 3000)
  }
}

export { fmtBytes as formatBytes }

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

/**
 * 查询/刷新「上下文目录」的递归项目索引状态（配置中心可视化 + 生效态感知）。
 * refresh=true 强制后端重扫（忽略缓存）；默认读缓存（命中时秒回）。
 * 返回 { ok, projectCount, scannedAt, fromCache, javaFileCount, projects[] } 或 null。
 */
export async function loadContextStatus(refresh = false) {
  const dir = (state.config && state.config.projectContextEnabled)
    ? (state.config.projectContextDir || '') : ''
  if (!dir) {
    state.aiContext = { loading: false, data: null, error: '' }
    return null
  }
  state.aiContext.loading = true
  state.aiContext.error = ''
  try {
    const data = await api.contextStatus(dir, refresh)
    state.aiContext.data = (data && data.ok) ? data : null
    if (!data || !data.ok) state.aiContext.error = (data && data.message) || '上下文加载失败'
    return data
  } catch (e) {
    state.aiContext.data = null
    state.aiContext.error = e.message || '上下文加载失败'
    return null
  } finally {
    state.aiContext.loading = false
  }
}

/**
 * 按 API Base URL + Key 自动获取可用模型列表（后端 /api/ai/models）。
 * payload: { provider, baseUrl, apiKey, httpProxy?, httpsProxy?, blockPrivateEndpoints? }
 * 成功时把模型 ID 列表写入 state.aiModels 供配置中心「模型名称」字段下拉/补全，返回该列表；
 * 失败/为空时弹告警并返回空数组（不抛，调用方按返回值处理）。
 */
export async function fetchModels(payload) {
  try {
    const r = await api.fetchModels(payload)
    if (r && r.ok && Array.isArray(r.models) && r.models.length) {
      state.aiModels = r.models
      toast('success', `已获取 ${r.models.length} 个可用模型`)
      return r.models
    }
    const msg = (r && r.lastError) ? r.lastError : '未获取到模型列表'
    toast('warning', '获取模型列表失败：' + msg)
    return []
  } catch (e) {
    toast('danger', '获取模型列表失败：' + e.message)
    return []
  }
}

// ---------------- 成本闸门（P0 #5）：超阈值强制确认 ----------------

// 强制确认弹窗的等待队列（置于 reactive 之外，避免函数被 Vue 响应式包裹）。
// 队列化：多个 AI 入口（分析/报告/分类）并发超阈值时逐个确认、先到先弹，
// 避免单例 resolve 句柄被覆盖导致先到入口的 Promise 永久悬挂（评审 P0 #1）。
const costGateQueue = []   // [{ resolve, action, estimate, threshold }]

function showNextCostGate() {
  const item = costGateQueue[0]
  state.costGate = item ? { action: item.action, estimate: item.estimate, threshold: item.threshold } : null
}

/** 用户确认成本闸门：放行队头被拦截的 AI 调用，并展示下一个等待确认的入口（如有）。 */
export function confirmCostGate() {
  const item = costGateQueue.shift()
  if (item) item.resolve(true)
  showNextCostGate()
}

/** 用户取消成本闸门：放弃队头本次 AI 调用，并展示下一个等待确认的入口（如有）。 */
export function cancelCostGate() {
  const item = costGateQueue.shift()
  if (item) item.resolve(false)
  showNextCostGate()
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
  // 取预估（per-job 缓存；缓存键绑定「分析项 + prompt 指纹 + 单文件键」，换类别、换自定义问题
  // 或换目标文件即失效，避免闸门估算与实际消耗脱节）。
  // 不同分析项的 prompt 不同、token 预估不同；同一 category 下 custom 换问题也要失效（评审 P1 #5）。
  const cat = opts.category || null
  const pr = opts.prompt || null
  const fk = opts.fileKey || null
  const estKey = (cat || '') + '|' + (pr || '') + '|' + (fk || '')
  if (state.aiEstimate && state.aiEstimateKey !== estKey) {
    state.aiEstimate = null
  }
  if (!state.aiEstimate && state.job) {
    try {
      state.aiEstimate = await api.aiEstimate(state.job.jobId, cat, pr, fk)
      state.aiEstimateKey = estKey
    }
    catch (e) { return true }
  }
  const est = state.aiEstimate ? (state.aiEstimate[action] || 0) : 0
  if (est <= threshold) return true
  if (silent) return false
  // 超阈值 → 弹强制确认，等待用户决策（队列化：并发超阈值的入口逐个确认，先到先弹，互不覆盖）
  return await new Promise((resolve) => {
    costGateQueue.push({ resolve, action, estimate: est, threshold })
    if (!state.costGate) showNextCostGate()
  })
}