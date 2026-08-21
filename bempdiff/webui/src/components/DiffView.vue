<script setup>
import { computed, ref, shallowRef, nextTick, onMounted, onUnmounted, onUpdated, watch } from 'vue'
import { state, activeTab, closeTab, toggleFocusMode, setFocusMode, toggleAiPanel, STATUS_META, STATUS_LABEL } from '../store'
import { inlineDiff } from '../lib/diff_inline'
import { alignLines } from '../lib/diff_align'
import { foldContext } from '../lib/diff_fold'
import { tokenInfoAt, TOKEN_META } from '../lib/token_classify'
import { langOf, tokenizeLine } from '../lib/syntax_highlight'
import PathBar from './PathBar.vue'

// STATUS_META / STATUS_LABEL 从 store.js 共享，避免与 DiffTree/InfoPanel 重复定义
const STATUS_CLS = {
  ADDED: 'text-bg-success',
  DELETED: 'text-bg-danger',
  MODIFIED: 'text-bg-warning',
  UNCHANGED: 'text-bg-secondary'
}

// ---------- 性能相关常量 ----------
const EST_ROW_H = 21          // 行高估算值（真实高度由 measureVisible 回填）
const OVERSCAN = 12           // 视口上下额外渲染行数（滚动缓冲）
const OVERSIZED_LINES = 60000  // 差异行数超此阈值 → 降级「简洁视图」（关行内高亮 + 默认折叠未变）

// 默认 BCompare 风格：左右分栏并排（不换行、双侧横向滚动同步）；可切换为自动换行单容器
const wrap = ref(false)
// 行内差异高亮粒度：'line'=整行（关闭行内高亮）| 'word'=词级 | 'char'=字符级
const granularity = ref('char')
// 粒度图标按钮（无文字，悬浮提示当前粒度，点击循环切换）
const GRAN_ORDER = ['line', 'word', 'char']
const GRAN_META = {
  line: { label: '整行', icon: 'bi-text-left' },
  word: { label: '词级', icon: 'bi-text-indent-left' },
  char: { label: '字符级', icon: 'bi-type' }
}
const granularityLabel = computed(() => (GRAN_META[granularity.value] || GRAN_META.char).label)
const granularityIcon = computed(() => (GRAN_META[granularity.value] || GRAN_META.char).icon)
function cycleGranularity() {
  const i = GRAN_ORDER.indexOf(granularity.value)
  granularity.value = GRAN_ORDER[(i + 1) % GRAN_ORDER.length]
}
// 折叠未变：收起远离变化块的纯 ctx 行（对标 Beyond Compare 忽略未变更区段）
const collapse = ref(false)
const foldWin = 3

// 查看模式：diffOnly=false=全量内容（显示全部代码行，含未变更）；true=仅差异内容（未变更行全部折叠，只留差异行）。
// 差异内容模式复用 foldContext(rows, true, 0)——win=0 时所有 ctx 行折叠成占位条，与报告「只写差异内容」语义一致。
const diffOnly = ref(false)
/** 折叠条点击：差异模式 → 切回全量内容；普通折叠 → 展开该段。 */
function onFoldClick() {
  if (diffOnly.value) diffOnly.value = false
  else collapse.value = false
}
/** 折叠条文案（区分两种折叠来源）。 */
function foldLabel(v) {
  return diffOnly.value
    ? '⋯ 已隐藏 ' + v.count + ' 行未变更内容（点击查看全量）⋯'
    : '⋯ 已折叠 ' + v.count + ' 行未变更内容（点击展开）⋯'
}

// ---------- 超大文件降级：简洁视图 ----------
const forceFull = ref(false) // 用户在超大文件时手动「展开完整差异」（开启行内高亮 + 展开未变）

const tabs = computed(() => state.tabs)
const at = computed(() => activeTab())
const node = computed(() => at.value ? at.value.node : null)
const dec = computed(() => at.value ? at.value.decompile : null)
const busy = computed(() => !!(at.value && at.value.busy && !dec.value))
const tabErr = computed(() => at.value ? at.value.error : null)

// 文件 key 末段做 tab 标题；保留原 key 作 tooltip 完整路径
function tabTitle(t) {
  const k = (t.node && t.node.key) || t.key
  if (!k) return ''
  const slash = Math.max(k.lastIndexOf('/'), k.lastIndexOf('\\'))
  return slash >= 0 ? k.slice(slash + 1) : k
}
function fullKey(t) { return (t.node && t.node.key) || t.key }
function statusDot(s) { return (STATUS_META[s] || STATUS_META.UNCHANGED).dot }

// ======================================================================
// 文件类型视觉元数据（IntelliJ 风格：不同类型 → 专属图标 + 强调色）。
// accent 用于差异区行号列/当前行/折叠行的类型色点缀；badgeBg 用于 filebar/tab 徽标淡底。
// 颜色均为主题中性的「类型色」，仅作点缀，不与增删改高亮（红/绿/橙）冲突。
// ======================================================================
const FCLASS_META = {
  CLASS:  { label: 'Java 类',  icon: 'bi-filetype-java',    accent: '#7c5cff' },
  JAR:    { label: '依赖 JAR', icon: 'bi-archive',          accent: '#9c6ade' },
  CONFIG: { label: '配置',     icon: 'bi-gear',             accent: '#d97706' },
  JS:     { label: 'JS',       icon: 'bi-filetype-js',      accent: '#c9930e' },
  HTML:   { label: 'HTML',     icon: 'bi-filetype-html',    accent: '#d9534f' },
  CSS:    { label: 'CSS',      icon: 'bi-filetype-css',     accent: '#1f7fc4' },
  JSP:    { label: 'JSP',      icon: 'bi-filetype-xml',     accent: '#c05621' },
  OFFICE: { label: 'Office',   icon: 'bi-file-earmark-easel', accent: '#0f9d8f' },
  STATIC: { label: '资源',     icon: 'bi-image',            accent: '#6b7280' },
  ARCHIVE:{ label: '归档',     icon: 'bi-file-earmark-zip', accent: '#8a94a6' },
  OTHER:  { label: '其他',     icon: 'bi-file-earmark',     accent: '#6b7280' }
}
function fclassMeta(fc) { return FCLASS_META[fc] || FCLASS_META.OTHER }
// 当前激活文件类型（tab 快照优先，回退节点）
const currentFc = computed(() => {
  if (at.value && at.value.node && at.value.node.fileClass) return at.value.node.fileClass
  if (node.value && node.value.fileClass) return node.value.fileClass
  return 'OTHER'
})
const currentFcMeta = computed(() => fclassMeta(currentFc.value))
function fcOf(t) { return (t.node && t.node.fileClass) || 'OTHER' }

// 语法高亮语言：扩展名优先（覆盖 .md/.py/.ts 等后端未细分类型），FileClass 兜底。
// key 取当前激活文件（tab 快照 / 树节点 / 归档内部复合键）。
const lang = computed(() => {
  const k = (at.value && at.value.node && at.value.node.key)
    || (at.value && at.value.key)
    || (node.value && node.value.key)
    || ''
  return langOf(k, currentFc.value)
})
// diff gutter 图标：左栏标记 del/rep，右栏标记 add/rep（对应行内容所在侧）；ctx/空侧无图标
function gtIcon(v, side) {
  if (side === 'left') return v.type === 'del' ? 'bi-dash-lg' : (v.type === 'rep' ? 'bi-pencil' : '')
  return v.type === 'add' ? 'bi-plus-lg' : (v.type === 'rep' ? 'bi-pencil' : '')
}

// ======================================================================
// 解析：用旧/新两侧源码做行级 LCS 对齐（BCompare 风格「同行显示」）。
// 行型：ctx=相同 | rep=修改对（1:1 同行左右对照 + 行内高亮）| del=仅旧侧 | add=仅新侧。
// alignLines 内部有单元格上限护栏（超限退化为 O(n+m) 线性对齐），大文件也不卡 UI。
// ======================================================================
const rawOld = computed(() => (dec.value && dec.value.oldSource) ? dec.value.oldSource : '')
const rawNew = computed(() => (dec.value && dec.value.newSource) ? dec.value.newSource : '')
const parseGen = ref(0)
const parseStatus = ref('idle') // idle | done（对齐为同步计算，无需分块）
const rows = shallowRef([])     // 对齐后的行（含原始下标 _i，供行内差异缓存与定位）
const totalStats = ref({ added: 0, removed: 0, modified: 0, unchanged: 0, total: 0 })

function startParse() {
  const gen = ++parseGen.value
  inlineCache.clear()
  rows.value = []
  totalStats.value = { added: 0, removed: 0, modified: 0, unchanged: 0, total: 0 }
  const oldLines = rawOld.value ? rawOld.value.split('\n') : []
  const newLines = rawNew.value ? rawNew.value.split('\n') : []
  if (!oldLines.length && !newLines.length) { parseStatus.value = 'done'; return }
  const aligned = alignLines(oldLines, newLines)
  let ln = 0
  let rn = 0
  const c = { added: 0, removed: 0, modified: 0, unchanged: 0, total: aligned.length }
  const out = new Array(aligned.length)
  for (let k = 0; k < aligned.length; k++) {
    const a = aligned[k]
    if (a.type === 'ctx') {
      ln++; rn++; c.unchanged++
      out[k] = { type: 'ctx', _i: k, left: '' + ln, leftText: a.left, right: '' + rn, rightText: a.right }
    } else if (a.type === 'rep') {
      ln++; rn++; c.modified++
      out[k] = { type: 'rep', _i: k, left: '' + ln, leftText: a.left, right: '' + rn, rightText: a.right }
    } else if (a.type === 'del') {
      ln++; c.removed++
      out[k] = { type: 'del', _i: k, left: '' + ln, leftText: a.left, right: '', rightText: '' }
    } else {
      rn++; c.added++
      out[k] = { type: 'add', _i: k, left: '', leftText: '', right: '' + rn, rightText: a.right }
    }
  }
  if (gen !== parseGen.value) return // 切换文件竞态：丢弃迟到结果
  rows.value = out
  totalStats.value = c
  parseStatus.value = 'done'
}

// 折叠未变（collapse）后用于渲染的行集合；fold 行无 _i。
// diffOnly（仅差异内容）时 win=0 折叠全部未变行，只留差异行；否则按 collapse 折叠远离变化块的 ctx。
const foldedRows = computed(() => {
  if (!rows.value.length) return []
  if (diffOnly.value) return foldContext(rows.value, true, 0)
  return foldContext(rows.value, collapse.value, foldWin)
})

// 差异行（rep/del/add）快速定位序列；i = 原始下标 _i。
const diffRows = computed(() => {
  const rs = rows.value
  const out = []
  for (let i = 0; i < rs.length; i++) {
    const t = rs[i].type
    if (t === 'rep' || t === 'del' || t === 'add') out.push({ r: rs[i], i })
  }
  return out
})
const diffCount = computed(() => diffRows.value.length)

// ======================================================================
// 行内差异 + 语法高亮：仅对「可见行」惰性计算并缓存（不在全量行上跑，避免大文件卡顿）。
// rep 行 = 修改对（1:1），对 旧行/新行 做词/字符级 LCS → 左栏 del 片段（红）、右栏 add 片段（绿）；
// 每个 diff 片段内部再 tokenize → 语法色分层：片段背景管差异语义，token 前景色管语法层次，
// diff 片段内 token 由 CSS 加深（.im-del/.im-add .tok）保证红/绿底上可读。
// inlineCache 为非响应式 Map（key = 行下标|语言，语言变化随切文件清空）；
// inlineOn 变化（粒度/超大/展开）时清空。
// ======================================================================
const inlineCache = new Map()
const oversized = computed(() => rows.value.length > OVERSIZED_LINES)
const inlineOn = computed(() => granularity.value !== 'line' && (!oversized.value || forceFull.value))
function withToks(seg) {
  return { m: seg.m, kind: seg.kind, s: seg.s, toks: tokenizeLine(seg.s, lang.value) }
}
function getInline(ri) {
  const r = rows.value[ri]
  if (!r) return { left: [{ m: false, s: '', toks: [] }], right: [{ m: false, s: '', toks: [] }] }
  const key = ri + '|' + lang.value
  // 仅 rep 行需要行内差异；ctx/del/add 整行高亮即可（BCompare 同款：替换行才做字符级细分）
  if (!inlineOn.value || r.type !== 'rep') {
    return { left: withToks({ m: false, kind: '', s: r.leftText }), right: withToks({ m: false, kind: '', s: r.rightText }) }
  }
  if (inlineCache.has(key)) return inlineCache.get(key)
  const segs = inlineDiff(r.leftText || '', r.rightText || '', granularity.value)
  const left = segs.filter(s => s.t !== 'add').map(s => withToks({ m: s.t === 'del', kind: 'del', s: s.s }))
  const right = segs.filter(s => s.t !== 'del').map(s => withToks({ m: s.t === 'add', kind: 'add', s: s.s }))
  const result = { left, right }
  inlineCache.set(key, result)
  return result
}
watch([granularity, oversized, forceFull, rawOld, rawNew, lang], () => inlineCache.clear())

// ======================================================================
// 虚拟滚动：前缀和 offsets + 二分定位可见区间 + 动态行高回填
// ======================================================================
const scrollTop = ref(0)
const viewportH = ref(600)
const diffAreaRef = ref(null) // 换行模式滚动层
const leftPaneRef = ref(null)
const rightPaneRef = ref(null)
const rowEls = new Map()      // abs(折叠行下标) -> DOM 元素，供实测行高

const offsets = computed(() => {
  const hs = heights.value
  const n = foldedRows.value.length
  const arr = new Array(n + 1)
  arr[0] = 0
  for (let i = 1; i <= n; i++) arr[i] = arr[i - 1] + (hs[i - 1] || EST_ROW_H)
  return arr
})
const totalHeight = computed(() => offsets.value[foldedRows.value.length] || 0)

function findStart(y) {
  const o = offsets.value
  let lo = 0, hi = o.length - 1, ans = 0
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (o[mid] <= y) { ans = mid; lo = mid + 1 } else hi = mid - 1 }
  return ans
}
function findEnd(y) {
  const o = offsets.value
  let lo = 0, hi = o.length - 1, ans = o.length - 1
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (o[mid] < y) lo = mid + 1; else { ans = mid; hi = mid - 1 } }
  return ans
}
const startIndex = computed(() => Math.max(0, findStart(scrollTop.value) - OVERSCAN))
const endIndex = computed(() => Math.min(foldedRows.value.length, findEnd(scrollTop.value + viewportH.value) + OVERSCAN))

// 仅渲染可见区间内的行；行内差异按可见行惰性计算（getInline 内部命中缓存）。
const visibleRows = computed(() => {
  const fr = foldedRows.value
  const res = []
  const s = startIndex.value, e = endIndex.value
  for (let i = s; i < e; i++) {
    const x = fr[i]
    if (x.type === 'fold') { res.push({ type: 'fold', count: x.count, ri: -1, abs: i }); continue }
    const ri = x._i
    const segs = getInline(ri)
    res.push({
      type: x.type, ri, abs: i,
      left: x.left, leftText: x.leftText,
      right: x.right, rightText: x.rightText,
      leftSegs: segs.left, rightSegs: segs.right
    })
  }
  return res
})

const heights = ref([])
// 折叠行集合变化（分块解析推进 / 折叠切换）时，保留已测得的高度、新增行用估算值。
watch(foldedRows, (fr) => {
  const h = heights.value.slice(0, fr.length)
  while (h.length < fr.length) h.push(EST_ROW_H)
  heights.value = h
}, { flush: 'sync' })

function setRowRef(abs, el) {
  if (el) rowEls.set(abs, el)
  else rowEls.delete(abs)
}
function measureVisible() {
  const hs = heights.value.slice()
  let changed = false
  rowEls.forEach((el, abs) => {
    const h = el.offsetHeight
    if (h > 0 && Math.abs((hs[abs] || EST_ROW_H) - h) > 0.5) { hs[abs] = h; changed = true }
  })
  if (changed) heights.value = hs
}

// 滚动：更新 scrollTop / viewportH（wrap 单容器；nowrap 双栏共用同一 scrollTop）
function onDiffScroll(e) {
  const el = e.target
  scrollTop.value = el.scrollTop
  viewportH.value = el.clientHeight
}
let paneSyncing = false
function onPaneScroll(side) {
  if (paneSyncing) return
  const src = side === 'left' ? leftPaneRef.value : rightPaneRef.value
  const dst = side === 'left' ? rightPaneRef.value : leftPaneRef.value
  if (!src || !dst) return
  scrollTop.value = src.scrollTop
  viewportH.value = src.clientHeight
  paneSyncing = true
  dst.scrollTop = src.scrollTop
  requestAnimationFrame(() => { paneSyncing = false })
}

// 超大文件：默认折叠未变（简洁视图）；用户可在横幅手动展开完整差异。
watch(oversized, (v) => { if (v) collapse.value = true })

// ---------- PathBar 路径栏数据 ----------
// 当前激活路径：优先树节点 key，归档内部条目（ARCHIVE-INNER，node 为 null）回退 tab 复合键
const pathKey = computed(() => {
  if (node.value) return node.value.key
  const t = activeTab.value
  return (t && ((t.node && t.node.key) || t.key)) || ''
})
// folder 模式的磁盘根（按差异状态选侧，与 DiffTree.diskRootOf 一致）：ADDED → 右根，其余 → 左根。
// 归档内部条目位于压缩包内，无磁盘路径 → 返回 ''（PathBar 编辑框退化为相对 key）。
const diskRoot = computed(() => {
  if (!state.job || state.job.mode !== 'folder' || !node.value) return ''
  const isAdded = node.value.status === 'ADDED'
  return isAdded ? (state.newPath || '') : (state.oldPath || '')
})

// 专注模式：切换 store.focusMode（App 层据此隐藏左右栏）。
function onToggleFocus() { toggleFocusMode() }

// 专注模式下按 ESC 退出；Ctrl/Alt + ↑/↓ 在差异行间跳转；组件卸载时移除监听。
// 弹窗（.modal-backdrop）打开时不拦截任何快捷键，避免穿透到下层 diff 视图或与「关弹窗」冲突。
function onKey(e) {
  if (document.querySelector('.modal-backdrop')) return
  if (e.key === 'Escape' && state.focusMode) { setFocusMode(false); return }
  if ((e.ctrlKey || e.altKey) && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
    const tag = (e.target && e.target.tagName) || ''
    if (tag === 'INPUT' || tag === 'TEXTAREA') return // 输入框内不拦截，避免影响输入
    if (!diffRows.value.length) return // 无差异可跳时不拦截原按键
    e.preventDefault()
    if (e.key === 'ArrowUp') gotoPrev()
    else gotoNext()
  }
}

// 中键关闭 tab；左键点 tab 文字切激活，点 x 关闭。
function onTabClick(key) { state.activeKey = key }
function onTabClose(e, key) { e.stopPropagation(); closeTab(key) }
function onTabMouseDown(e, key) {
  if (e.button === 1) { e.preventDefault(); closeTab(key) } // 中键关闭
}

// ---------- 差异行快速定位（上一处 / 下一处） ----------
const curIdx = ref(-1)
const currentRowIdx = computed(() => (curIdx.value >= 0 && curIdx.value < diffRows.value.length) ? diffRows.value[curIdx.value].i : -1)
const flashRi = ref(-1) // 当前需要 flash 高亮的行原始下标；动画结束后复位
let flashTimer = null

// 切换激活文件时重置差异行导航状态，避免计数器越界与残留高亮。
watch(() => (at.value && at.value.key), () => {
  curIdx.value = -1
  flashRi.value = -1
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null }
})

function locate(idx) {
  if (!diffRows.value.length) return
  const clamped = (idx + diffRows.value.length) % diffRows.value.length
  curIdx.value = clamped
  const ri = diffRows.value[clamped].i
  flashRi.value = ri
  if (flashTimer) clearTimeout(flashTimer)
  flashTimer = setTimeout(() => { flashRi.value = -1 }, 900)
  nextTick(() => {
    const di = foldedRows.value.findIndex(x => x._i === ri)
    if (di < 0) {
      // 目标行被折叠收起：展开未变后再定位
      if (collapse.value) { collapse.value = false; nextTick(() => locate(idx)); return }
      return
    }
    const top = offsets.value[di] || 0
    const h = heights.value[di] || EST_ROW_H
    const target = wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value)
    if (!target) return
    target.scrollTo({ top: Math.max(0, top - viewportH.value / 2 + h / 2), behavior: 'smooth' })
    if (!wrap.value && leftPaneRef.value && rightPaneRef.value) {
      rightPaneRef.value.scrollTop = leftPaneRef.value.scrollTop
    }
  })
}
function gotoPrev() {
  if (!diffRows.value.length) return
  locate(curIdx.value <= 0 ? diffRows.value.length - 1 : curIdx.value - 1)
}
function gotoNext() {
  if (!diffRows.value.length) return
  locate(curIdx.value < 0 ? 0 : (curIdx.value + 1) % diffRows.value.length)
}

// ======================================================================
// 光标定位（BCompare/编辑器风格）：点击代码行 → 闪烁竖线光标 + 状态栏行列坐标 + 元素类型
// ======================================================================
const caretPos = ref(null) // { ri, side:'left'|'right', col0 } —— 点击落下的持久光标
const hoverPos = ref(null) // { ri, side, col0 } —— 鼠标悬停位置（状态栏实时跟随）
let charWidthPx = 12      // 代码字体等宽字符宽度（onMounted 实测回填）
const CODE_PAD_PX = 9.6   // .code padding-left 0.6rem ≈ 9.6px

function measureCharWidth() {
  const probe = document.createElement('span')
  probe.className = 'code-font-probe'
  probe.textContent = 'MMMMMMMMMMMMMMMMMMMM' // 20 个 M 求单字符宽
  document.body.appendChild(probe)
  charWidthPx = probe.getBoundingClientRect().width / 20 || charWidthPx
  probe.remove()
}

/** 由点击/移动事件计算目标单元格与 0 基列号（优先 caretRangeFromPoint 精确命中字符）。 */
function locateCell(e, v) {
  if (!e.target || !e.target.closest || v.type === 'fold') return null
  const cell = e.target.closest('[data-side]')
  if (!cell) return null
  const side = cell.getAttribute('data-side') === 'right' ? 'right' : 'left'
  let col0 = 0
  if (document.caretRangeFromPoint) {
    const range = document.caretRangeFromPoint(e.clientX, e.clientY)
    if (range && range.startContainer) {
      const node = range.startContainer
      const segEl = node.nodeType === 3 ? node.parentElement : node
      // 从当前段向前累计同格文本长度（排除光标占位），得到行内绝对列号
      let acc = 0
      let cur = segEl ? segEl.previousElementSibling : null
      while (cur) {
        if (!cur.classList || !cur.classList.contains('code-caret')) acc += (cur.textContent || '').length
        cur = cur.previousElementSibling
      }
      col0 = acc + range.startOffset
      return { side, col0 }
    }
  }
  // 兜底：offsetX / 等宽字符宽
  const rect = cell.getBoundingClientRect()
  col0 = Math.max(0, Math.round((e.clientX - rect.left - CODE_PAD_PX) / (charWidthPx || 12)))
  return { side, col0 }
}

function onRowMove(e, v) {
  const p = locateCell(e, v)
  if (!p) return
  const last = hoverPos.value
  if (last && last.ri === v.ri && last.side === p.side && last.col0 === p.col0) return
  hoverPos.value = { ri: v.ri, side: p.side, col0: p.col0 }
}

function onRowClick(e, v) {
  const p = locateCell(e, v)
  if (!p) return
  caretPos.value = { ri: v.ri, side: p.side, col0: p.col0 }
  hoverPos.value = { ...p, ri: v.ri }
}

/** 当前行是否渲染光标（点击位置所在行、对应侧）。 */
function showCaret(v, side) {
  return !!(caretPos.value && caretPos.value.ri === v.ri && caretPos.value.side === side)
}

/** 光标横向偏移（等宽字体：列号 × 字符宽 + 单元格左内边距）。 */
function caretX(v, col0) {
  return CODE_PAD_PX + (col0 || 0) * charWidthPx
}

/** 底部状态栏信息：行/列坐标 + 该位置元素类型（优先点击位置，否则跟随悬停）。 */
const statusInfo = computed(() => {
  const p = caretPos.value || hoverPos.value
  if (!p) return null
  const r = rows.value[p.ri]
  if (!r) return null
  const sideLeft = p.side === 'left'
  const text = sideLeft ? (r.leftText || '') : (r.rightText || '')
  const lineNo = sideLeft ? r.left : r.right
  const c0 = Math.max(0, Math.min(p.col0, text.length))
  const tok = tokenInfoAt(text, c0)
  return {
    lineNo: lineNo || '—',
    col: c0 + 1,
    side: sideLeft ? '左' : '右',
    tok,
    meta: TOKEN_META[tok.type] || TOKEN_META.other
  }
})

// 进入新文件：重置滚动位置 + 重新解析 + 清空导航与光标状态
watch([rawOld, rawNew], () => {
  scrollTop.value = 0
  curIdx.value = -1
  flashRi.value = -1
  caretPos.value = null
  hoverPos.value = null
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null }
  startParse()
})

// ---------- 尺寸观察：容器尺寸变化（换行重排/缩放）时重置估算高度触发复测 ----------
let ro = null
function setupObserver() {
  if (ro) { ro.disconnect(); ro = null }
  const el = wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value)
  if (!el) return
  viewportH.value = el.clientHeight || viewportH.value
  ro = new ResizeObserver(() => {
    const c = wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value)
    if (c) viewportH.value = c.clientHeight
    // 宽度变化会改变行高 → 重置为估算，下一次 onUpdated 实测回填
    heights.value = new Array(foldedRows.value.length).fill(EST_ROW_H)
  })
  ro.observe(el)
}
watch(wrap, () => { nextTick(setupObserver) })

onMounted(() => {
  window.addEventListener('keydown', onKey)
  measureCharWidth()
  nextTick(setupObserver)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null }
  if (ro) { ro.disconnect(); ro = null }
})
onUpdated(() => measureVisible())
</script>

<template>
  <div class="col-center" :data-fc="currentFc"
       :style="{ '--dt-accent': currentFcMeta.accent }">
    <!--
      tab 栏：始终渲染，避免 0/1 tab 时这层"忽隐忽现"导致用户找不到。
    -->
    <div class="dvt-tabbar">
      <span class="dvt-count">
        <i class="bi bi-files"></i> 已打开 <b>{{ tabs.length }}</b> 个对比
      </span>
      <ul class="nav nav-tabs dvt-tabs" role="tablist">
        <template v-if="tabs.length">
          <li v-for="t in tabs" :key="t.key" class="nav-item dvt-tab" role="presentation">
            <button class="nav-link d-flex align-items-center gap-1"
                    :class="{active: state.activeKey === t.key}"
                    :title="fullKey(t)"
                    role="tab"
                    :aria-selected="state.activeKey === t.key"
                    @click="onTabClick(t.key)"
                    @mousedown="onTabMouseDown($event, t.key)">
              <i class="bi bi-circle-fill" style="font-size:.45rem" :style="{color: statusDot(t.node && t.node.status)}"></i>
              <i class="bi dvt-fc-icon" :class="fclassMeta(fcOf(t)).icon"
                 :style="{color: state.activeKey === t.key ? fclassMeta(fcOf(t)).accent : 'var(--bs-secondary-color)'}"
                 :title="'文件类型：' + fclassMeta(fcOf(t)).label"></i>
              <span class="dvt-title text-truncate">{{ tabTitle(t) }}</span>
              <span v-if="t.busy && !t.decompile" class="spinner-border spinner-border-sm ms-1" role="status" aria-hidden="true" style="width:.7rem;height:.7rem"></span>
              <i class="bi bi-x dvt-close" role="button" aria-label="关闭" @click="onTabClose($event, t.key)"></i>
            </button>
          </li>
        </template>
        <li v-else class="nav-item dvt-tab-empty">
          <span class="nav-link disabled text-secondary" tabindex="-1">
            <i class="bi bi-arrow-bar-left"></i> 未打开任何对比 · 从左侧差异树选择文件
          </span>
        </li>
      </ul>
    </div>

    <div class="filebar">
      <i class="bi filebar-icon" :class="currentFcMeta.icon"
         :style="{color: currentFcMeta.accent}"
         :title="'文件类型：' + currentFcMeta.label"></i>
      <!-- 路径栏（Win10 地址栏交互）：面包屑导航 ↔ 完整路径编辑，悬浮显示完整路径 -->
      <PathBar v-if="pathKey" :path="pathKey" :root-path="diskRoot" :node="node" />
      <span v-else class="path text-secondary">未选择文件</span>
      <span v-if="node" class="badge" :class="STATUS_CLS[node.status]">{{ STATUS_LABEL[node.status] }}</span>
      <span v-else-if="activeTab" class="badge text-bg-light border" :title="'内部条目（归档内文件）'">ARCHIVE-INNER</span>
      <span class="badge fc-badge" v-if="node"
            :style="{ color: currentFcMeta.accent, borderColor: currentFcMeta.accent + '66', background: 'color-mix(in srgb, ' + currentFcMeta.accent + ' 12%, transparent)' }"
            :title="'文件类型：' + currentFcMeta.label">
        <i class="bi" :class="currentFcMeta.icon"></i>{{ currentFcMeta.label }}
      </span>
      <span class="ms-auto text-secondary me-2" style="font-size:.75rem" title="当前文件使用的反编译引擎（默认 CFR）">
        反编译引擎：{{ dec ? dec.engine : '—' }}
      </span>
      <!-- 差异统计（BCompare 风格）：+新增 / −删除 / ~修改 / =未变 -->
      <span class="dvt-stats me-2" v-if="parseStatus === 'done' && rows.length" title="差异统计：新增 / 删除 / 修改 / 未变">
        <span class="text-success"><i class="bi bi-plus-circle"></i> {{ totalStats.added.toLocaleString() }}</span>
        <span class="text-danger"><i class="bi bi-dash-circle"></i> {{ totalStats.removed.toLocaleString() }}</span>
        <span class="text-warning"><i class="bi bi-pencil-square"></i> {{ totalStats.modified.toLocaleString() }}</span>
        <span class="text-secondary"><i class="bi bi-equals"></i> {{ totalStats.unchanged.toLocaleString() }}</span>
      </span>
      <button class="btn btn-sm btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
              @click="toggleAiPanel" :title="state.aiPanelCollapsed ? '展开智能分析栏，查看单文件/全局分析' : '收起智能分析栏，扩大比对视野'">
        <i class="bi" :class="state.aiPanelCollapsed ? 'bi-layout-sidebar' : 'bi-layout-sidebar-inset-reverse'"></i>
      </button>
      <button class="btn btn-sm btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
              @click="onToggleFocus" :title="state.focusMode ? '退出专注模式（Esc）' : '专注对比：隐藏左右栏、放大视野（Esc 退出）'">
        <i class="bi" :class="state.focusMode ? 'bi-fullscreen-exit' : 'bi-arrows-fullscreen'"></i>
      </button>
      <button class="btn btn-sm btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
              @click="wrap = !wrap" :title="wrap ? '当前：自动换行（单栏逐行对齐）· 点击切换为不换行（左右分栏 + 底部横向滚动同步）' : '当前：不换行（左右分栏 + 底部横向滚动同步）· 点击切换为自动换行'">
        <i class="bi" :class="wrap ? 'bi-text-wrap' : 'bi-text-paragraph'"></i>
      </button>
      <button class="btn btn-sm btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
              @click="cycleGranularity" :title="'行内差异粒度：' + granularityLabel + '（点击在 整行 / 词级 / 字符级 间循环切换）'">
        <i class="bi" :class="granularityIcon"></i>
      </button>
      <button class="btn btn-sm py-0 px-2" style="font-size:1.05rem"
              :class="collapse ? 'btn-primary' : 'btn-outline-secondary'"
              @click="collapse = !collapse"
              :title="collapse ? '已折叠未变更行，点击展开全部' : '折叠远离变化块的未变更行（对标 Beyond Compare）'">
        <i class="bi" :class="collapse ? 'bi-arrows-expand' : 'bi-arrows-collapse'"></i>
      </button>
      <!-- 查看模式：全量内容（含未变更）/ 仅差异内容（未变更行全部折叠） -->
      <div class="btn-group btn-group-sm ms-1" role="group" aria-label="查看模式">
        <button class="btn py-0 px-2" style="font-size:1.05rem"
                :class="diffOnly ? 'btn-outline-secondary' : 'btn-primary'"
                @click="diffOnly = false"
                :title="diffOnly ? '全量内容：显示全部代码行（含未变更）' : '当前：全量内容（显示全部代码行，含未变更）'">
          <i class="bi bi-file-earmark-text"></i>
        </button>
        <button class="btn py-0 px-2" style="font-size:1.05rem"
                :class="diffOnly ? 'btn-primary' : 'btn-outline-secondary'"
                @click="diffOnly = true"
                :title="diffOnly ? '当前：仅差异内容（隐藏未变更行，只显示变更行）' : '仅差异内容：只显示变更行（隐藏未变更内容）'">
          <i class="bi bi-diff"></i>
        </button>
      </div>
      <!-- 差异行快速定位：上一处 / 下一处（仅 add/del 算差异行） -->
      <div class="btn-group btn-group-sm ms-1" role="group" aria-label="差异行定位">
        <button class="btn btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
                @click="gotoPrev" :disabled="!diffRows.length" title="跳到上一处差异（Ctrl/Alt + ↑）">
          <i class="bi bi-chevron-up"></i>
        </button>
        <span class="btn btn-outline-secondary py-0 px-2 disabled d-flex align-items-center justify-content-center" style="font-size:.72rem;pointer-events:none;min-width:3.2rem;flex-shrink:0;font-variant-numeric:tabular-nums">
          {{ diffCount ? curIdx + 1 : 0 }}/{{ diffCount }}
        </span>
        <button class="btn btn-outline-secondary py-0 px-2" style="font-size:1.05rem"
                @click="gotoNext" :disabled="!diffRows.length" title="跳到下一处差异（Ctrl/Alt + ↓）">
          <i class="bi bi-chevron-down"></i>
        </button>
      </div>
    </div>

    <!-- 主体：反编译完成且差异解析完成 → 虚拟滚动渲染 -->
    <div class="diff-area" v-if="(node || dec) && dec.ok && parseStatus === 'done'">
      <!-- 超大文件降级横幅：简洁视图提示 + 手动展开完整差异 -->
      <div class="oversized-banner" v-if="oversized && !forceFull">
        <i class="bi bi-speedometer2"></i>
        <span>大文件已降级为简洁视图：共 <b>{{ rows.length.toLocaleString() }}</b> 行
          （<span class="text-success">+{{ totalStats.added.toLocaleString() }}</span> /
           <span class="text-danger">-{{ totalStats.removed.toLocaleString() }}</span> /
           <span class="text-warning">~{{ totalStats.modified.toLocaleString() }}</span> /
           {{ totalStats.unchanged.toLocaleString() }} 未变），已关闭行内高亮并默认折叠未变更以保障流畅。</span>
        <button class="btn btn-sm btn-outline-primary py-0 px-2 ms-2" @click="forceFull = true; collapse = false">展开完整差异（可能卡顿）</button>
      </div>

      <!-- 换行模式：单容器 4 列网格（整行背景 + 逐行对齐，长行自动换行） -->
      <div class="diff-scroll" ref="diffAreaRef" v-if="wrap" @scroll="onDiffScroll">
        <div class="diff-grid" :style="{ height: totalHeight + 'px' }">
          <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
               class="row" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
               :data-ri="v.ri" :ref="el => setRowRef(v.abs, el)"
               @mousemove="onRowMove($event, v)" @click="onRowClick($event, v)"
               :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
            <template v-if="v.type === 'fold'">
              <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                    :title="foldLabel(v)"
                    @click="onFoldClick" @keydown.enter="onFoldClick">{{ foldLabel(v) }}</span>
            </template>
            <template v-else>
              <span class="gt" :class="'gt-' + v.type"><i v-if="gtIcon(v, 'left')" class="bi" :class="gtIcon(v, 'left')"></i></span>
              <span class="ln">{{ v.left }}</span>
              <span class="code flex-1" data-side="left"><span v-for="(s, si) in v.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}"><template v-for="(t, ti) in s.toks" :key="ti"><span v-if="t.type !== 'ws' && t.type !== 'plain'" class="tok" :class="'tok-' + t.type">{{ t.text }}</span><template v-else>{{ t.text }}</template></template></span><span v-if="showCaret(v, 'left')" class="code-caret" :style="{ left: caretX(v, caretPos.col0) + 'px' }"></span></span>
              <span class="gt" :class="'gt-' + v.type"><i v-if="gtIcon(v, 'right')" class="bi" :class="gtIcon(v, 'right')"></i></span>
              <span class="ln">{{ v.right }}</span>
              <span class="code flex-1" data-side="right"><span v-for="(s, si) in v.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}"><template v-for="(t, ti) in s.toks" :key="ti"><span v-if="t.type !== 'ws' && t.type !== 'plain'" class="tok" :class="'tok-' + t.type">{{ t.text }}</span><template v-else>{{ t.text }}</template></template></span><span v-if="showCaret(v, 'right')" class="code-caret" :style="{ left: caretX(v, caretPos.col0) + 'px' }"></span></span>
            </template>
          </div>
        </div>
      </div>

      <!-- 不换行模式：左右分栏各占一半宽度 + 中间分隔条；两侧底部横向滚动条同步左右滑动 -->
      <div class="diff-split" v-else>
        <div class="diff-pane" ref="leftPaneRef" @scroll="onPaneScroll('left')">
          <div class="pane-grid" :style="{ height: totalHeight + 'px' }">
            <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
                 class="prow" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
                 :data-ri="v.ri" :ref="el => setRowRef(v.abs, el)"
                 @mousemove="onRowMove($event, v)" @click="onRowClick($event, v)"
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
              <template v-if="v.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="foldLabel(v)"
                      @click="onFoldClick" @keydown.enter="onFoldClick">{{ foldLabel(v) }}</span>
              </template>
              <template v-else>
                <span class="gt" :class="'gt-' + v.type"><i v-if="gtIcon(v, 'left')" class="bi" :class="gtIcon(v, 'left')"></i></span>
                <span class="ln">{{ v.left }}</span>
                <span class="code flex-1" data-side="left"><span v-for="(s, si) in v.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}"><template v-for="(t, ti) in s.toks" :key="ti"><span v-if="t.type !== 'ws' && t.type !== 'plain'" class="tok" :class="'tok-' + t.type">{{ t.text }}</span><template v-else>{{ t.text }}</template></template></span><span v-if="showCaret(v, 'left')" class="code-caret" :style="{ left: caretX(v, caretPos.col0) + 'px' }"></span></span>
              </template>
            </div>
          </div>
        </div>

        <div class="diff-splitter" title="左 / 右 文件分栏"></div>

        <div class="diff-pane right" ref="rightPaneRef" @scroll="onPaneScroll('right')">
          <div class="pane-grid" :style="{ height: totalHeight + 'px' }">
            <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
                 class="prow" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
                 :data-ri="v.ri" :ref="el => setRowRef(v.abs, el)"
                 @mousemove="onRowMove($event, v)" @click="onRowClick($event, v)"
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
              <template v-if="v.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="foldLabel(v)"
                      @click="onFoldClick" @keydown.enter="onFoldClick">{{ foldLabel(v) }}</span>
              </template>
              <template v-else>
                <span class="gt" :class="'gt-' + v.type"><i v-if="gtIcon(v, 'right')" class="bi" :class="gtIcon(v, 'right')"></i></span>
                <span class="ln">{{ v.right }}</span>
                <span class="code flex-1" data-side="right"><span v-for="(s, si) in v.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}"><template v-for="(t, ti) in s.toks" :key="ti"><span v-if="t.type !== 'ws' && t.type !== 'plain'" class="tok" :class="'tok-' + t.type">{{ t.text }}</span><template v-else>{{ t.text }}</template></template></span><span v-if="showCaret(v, 'right')" class="code-caret" :style="{ left: caretX(v, caretPos.col0) + 'px' }"></span></span>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 光标定位状态栏：实时行列坐标 + 元素类型（点击落下光标，悬停实时跟随） -->
      <div class="diff-statusbar" :title="'光标定位：点击代码行落下闪烁光标，悬停实时跟随；类型为光标所在元素属性'">
        <template v-if="statusInfo">
          <span class="dsb-item"><i class="bi bi-cursor"></i> 行 <b>{{ statusInfo.lineNo }}</b> · 列 <b>{{ statusInfo.col }}</b>（{{ statusInfo.side }}侧）</span>
          <span class="dsb-item" :style="{ color: statusInfo.meta.color }">
            <i class="bi bi-tag"></i> {{ statusInfo.meta.label }}
            <template v-if="statusInfo.tok.word">：<code class="dsb-word">{{ statusInfo.tok.word }}</code></template>
          </span>
        </template>
        <template v-else>
          <span class="dsb-item text-secondary"><i class="bi bi-info-circle"></i> 将鼠标移到代码行查看行列与元素类型，点击可落下光标</span>
        </template>
      </div>
    </div>

    <!-- 加载态：反编译中 或 差异解析中 -->
    <div class="diff-area center-empty" v-else-if="(node || dec) && (busy || (dec && dec.ok && parseStatus !== 'done'))">
      <div class="spinner-border text-secondary" role="status" aria-hidden="true"></div>
      <div class="mt-2">{{ busy ? '正在反编译/美化源码…' : '正在解析差异…' }}</div>
    </div>

    <div class="diff-area center-empty" v-else-if="(node || dec) && dec && !dec.ok">
      <div class="ico"><i class="bi bi-exclamation-triangle"></i></div>
      <div>该文件无法反编译（引擎：{{ dec.engine }}）</div>
      <div v-if="dec.diffText" class="text-start mt-2" style="white-space:pre-wrap;font-size:.8rem">{{ dec.diffText }}</div>
    </div>

    <div class="diff-area center-empty" v-else-if="(node || dec) && tabErr">
      <div class="ico"><i class="bi bi-x-octagon text-danger"></i></div>
      <div>反编译失败：{{ tabErr }}</div>
    </div>

    <div class="diff-area center-empty" v-else>
      <div class="ico"><i class="bi bi-columns-gap"></i></div>
      <div>从左侧差异树选择一个 <b>修改 / 新增 / 删除</b> 的文件查看双栏源码比对</div>
    </div>
  </div>
</template>

<style scoped>
.diff-area { overflow: hidden; flex: 1 1 auto; min-height: 0; background: var(--bs-body-bg); display: flex; flex-direction: column; }
.oversized-banner {
  flex: 0 0 auto; font-size: .76rem; padding: .35rem .6rem;
  background: var(--bs-warning-bg-subtle); border-bottom: 1px solid var(--bs-border-color);
  color: var(--bs-secondary-color); display: flex; align-items: center; flex-wrap: wrap; gap: .3rem;
}
.oversized-banner b { color: var(--bs-body-color); }
.diff-scroll { overflow: auto; flex: 1 1 auto; min-height: 0; position: relative; }
.diff-grid { position: relative; }
.diff-split { flex: 1 1 auto; min-height: 0; display: flex; }
.diff-pane { overflow: auto; flex: 1 1 0; min-width: 0; position: relative; }
.diff-pane.right { border-left: 1px solid var(--bs-border-color); }
.diff-splitter { width: 4px; background: var(--bs-tertiary-bg); flex: 0 0 auto; }
.pane-grid { position: relative; }
.row, .prow {
  box-sizing: border-box;
  display: grid;
  gap: 0;
  align-items: stretch;
  border-bottom: 1px solid var(--bs-border-color);
}
.row { grid-template-columns: 1.15rem 3.2rem 1fr 1.15rem 3.2rem 1fr; }
.prow { grid-template-columns: 1.15rem 3.2rem 1fr; }
/* diff gutter：行类型色条 + 图标（add=绿+plus、del=红+dash、rep=橙+pencil；ctx/空侧无图标无底色）。
   图标只在「该侧存在该类型内容」时出现：左栏 del/rep、右栏 add/rep（gtIcon 控制）。 */
.row .gt, .prow .gt {
  display: flex; align-items: center; justify-content: center;
  font-size: .68rem; user-select: none;
  border-right: 1px solid var(--bs-border-color);
}
.row .gt.gt-add, .prow .gt.gt-add { background: color-mix(in srgb, var(--bs-success) 20%, transparent); color: var(--bs-success); }
.row .gt.gt-del, .prow .gt.gt-del { background: color-mix(in srgb, var(--bs-danger) 20%, transparent); color: var(--bs-danger); }
.row .gt.gt-rep, .prow .gt.gt-rep { background: color-mix(in srgb, var(--bs-warning) 26%, transparent); color: #b45309; }
.row:hover, .prow:hover { background: var(--bs-tertiary-bg); }
.row .ln, .prow .ln {
  text-align: right; padding: 0 .4rem;
  color: var(--bs-secondary-color);
  /* 行号列：轻微叠加当前文件类型色（IntelliJ 风格点缀，识别文件性质，不抢内容） */
  background: color-mix(in srgb, var(--dt-accent, var(--bs-tertiary-bg)) 8%, var(--bs-tertiary-bg));
  user-select: none;
  border-right: 1px solid var(--bs-border-color);
}
.row .code, .prow .code {
  padding: 0 .6rem;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--bs-font-monospace);
  font-size: .82rem;
  line-height: 1.5;
  position: relative;   /* 光标定位基准 */
  cursor: text;         /* 提示可点击定位 */
  color: var(--bs-body-color); /* 锁定正文色，避免高亮背景影响文字可读性 */
}
/* 光标定位：闪烁竖线（BCompare/编辑器风格），点击落下，位置 = 列号 × 等宽字符宽 */
.code-caret {
  position: absolute;
  top: 2px;
  bottom: 2px;
  width: 2px;
  background: var(--bs-primary);
  pointer-events: none;
  animation: caretBlink 1s steps(1) infinite;
}
@keyframes caretBlink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}
/* 等宽字符宽度测量探针（隐藏，样式与 .code 一致） */
.code-font-probe {
  position: absolute;
  visibility: hidden;
  white-space: pre;
  font-family: var(--bs-font-monospace);
  font-size: .82rem;
  line-height: 1.5;
}
/* 底部光标状态栏：行列坐标 + 元素类型 */
.diff-statusbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: .2rem .75rem;
  min-height: 1.7rem;
  font-size: .74rem;
  color: var(--bs-secondary-color);
  background: var(--bs-tertiary-bg);
  border-top: 1px solid var(--bs-border-color);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
}
.diff-statusbar b { color: var(--bs-body-color); font-weight: 600; }
.diff-statusbar .dsb-item { display: inline-flex; align-items: center; gap: .3rem; }
.diff-statusbar .dsb-word {
  font-size: .72rem;
  background: var(--bs-body-bg);
  border: 1px solid var(--bs-border-color);
  border-radius: 3px;
  padding: 0 .25rem;
  max-width: 18rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 行内词/字符级差异高亮（对标 Beyond Compare / GitHub inline diff）：
   文字色锁定正文色，红/绿底上仍清晰；浅橙行底上差异片段用更高饱和 + 内描边突出「差异点」 */
.code .im-del { background: rgba(220,53,69,.42); border-radius: 2px; color: var(--bs-body-color); }
.code .im-add { background: rgba(25,135,84,.42); border-radius: 2px; color: var(--bs-body-color); }
/* 折叠占位行：类型色 + 斜体，提示被折叠段落归属 */
.row.fold, .prow.fold { background: var(--bs-tertiary-bg); }
.row.fold .fold-ph, .prow.fold .fold-ph { color: var(--dt-accent, var(--bs-secondary-color)); font-style: italic; text-align: center; cursor: pointer; user-select: none; }
.row.add, .prow.add { background: var(--bs-success-bg-subtle); }
.row.add .ln, .prow.add .ln { color: var(--bs-success); font-weight: 600; }
.row.add .ln:last-of-type { border-left: 1px solid var(--bs-border-color); }
.row.del, .prow.del { background: var(--bs-danger-bg-subtle); }
.row.del .ln, .prow.del .ln { color: var(--bs-danger); font-weight: 600; }
.row.del .code, .prow.del .code { color: var(--bs-body-color); }
/* BCompare 风格「替换行」：修改对同行左右对照，整体淡橙底；行内红/绿片段继续细分，
   差异片段加内描边（inset ring）使其从行底/同色文字中清晰跳出，一眼定位差异点 */
.row.rep, .prow.rep { background: var(--bs-warning-bg-subtle); }
.row.rep .code .im-del, .prow.rep .code .im-del { background: rgba(220,53,69,.6); box-shadow: inset 0 0 0 1px rgba(220,53,69,.32); color: var(--bs-body-color); }
.row.rep .code .im-add, .prow.rep .code .im-add { background: rgba(25,135,84,.6); box-shadow: inset 0 0 0 1px rgba(25,135,84,.32); color: var(--bs-body-color); }
/* ===== 语法高亮 token 色板 =====
 * 语义色刻意避开 diff 标记三系（红=删除 / 绿=新增 / 橙=修改），
 * 与整行底色、行内片段底色不混淆；diff 片段内的 token 用 color-mix 加深保证红/绿底可读。
 * 扩展：新增 token 类型时在此补 .tok-* 类；暗色主题可加 [data-bs-theme="dark"] .col-center 覆盖变量。 */
.col-center {
  --tok-kw: #7c3aed;   /* 关键字/控制流 */
  --tok-str: #b45309;  /* 字符串 */
  --tok-com: #64748b;  /* 注释/引用 */
  --tok-num: #0891b2;  /* 数字/颜色值 */
  --tok-id: #475569;   /* 标识符/变量/选择器 */
  --tok-fn: #2563eb;   /* 函数调用 */
  --tok-type: #0891b2; /* 类型/类名 */
  --tok-tag: #be185d;  /* HTML 标签 */
  --tok-attr: #0284c7; /* HTML/CSS 属性名 */
  --tok-hd: #7c3aed;   /* Markdown 标题/强调 */
  --tok-link: #2563eb; /* 链接 */
  --tok-code: #b45309; /* 行内代码 */
  --tok-op: #64748b;   /* 运算符/分隔符 */
}
.tok-keyword { color: var(--tok-kw); }
.tok-string { color: var(--tok-str); }
.tok-comment { color: var(--tok-com); }
.tok-number { color: var(--tok-num); }
.tok-ident { color: var(--tok-id); }
.tok-func { color: var(--tok-fn); }
.tok-type { color: var(--tok-type); }
.tok-tag { color: var(--tok-tag); }
.tok-attr { color: var(--tok-attr); }
.tok-heading { color: var(--tok-hd); }
.tok-link { color: var(--tok-link); }
.tok-emph { color: var(--tok-hd); }
.tok-inlinecode { color: var(--tok-code); }
.tok-op { color: var(--tok-op); }
/* diff 片段（红/绿底）内：token 保留类型色但加深 40%，红绿底上仍清晰、语法层次不丢 */
.code .im-del .tok, .code .im-add .tok { color: color-mix(in srgb, currentColor 60%, #000); }
/* 文件栏差异统计（+新增 −删除 ~修改 =未变） */
.dvt-stats { display: inline-flex; align-items: center; gap: .5rem; font-size: .72rem; white-space: nowrap; }
/* 当前定位的差异行：左侧类型色主条 + 行号高亮 + 内容极淡类型色底（层级辨识） */
.row.current, .prow.current { box-shadow: inset 3px 0 0 var(--dt-accent, var(--bs-primary)); }
.row.current .ln, .prow.current .ln { color: var(--dt-accent, var(--bs-primary)); font-weight: 700; }
.row.current .code, .prow.current .code { background: color-mix(in srgb, var(--dt-accent, var(--bs-primary)) 5%, transparent); }
/* 跳转动画：从主色高亮淡出，提示目标位置 */
.row.flash, .prow.flash { animation: diffFlash .9s ease-out; }
@keyframes diffFlash {
  0% { background: var(--bs-primary-bg-subtle); }
  100% { background: transparent; }
}

/* ===== tab 栏 ===== */
.dvt-tabbar {
  display: flex;
  align-items: center;
  gap: .75rem;
  padding: .35rem .75rem;
  min-height: 2.4rem;
  background: var(--bs-tertiary-bg);
  border-bottom: 2px solid var(--bs-border-color);
  flex: 0 0 auto;
}
.dvt-count {
  flex: 0 0 auto;
  font-size: .76rem;
  color: var(--bs-secondary-color);
  padding-right: .5rem;
  border-right: 1px solid var(--bs-border-color);
}
.dvt-count b { color: var(--bs-body-color); }
.dvt-tabs {
  flex: 1 1 auto;
  display: flex;
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  border-bottom: none;
  margin-bottom: 0;
  background: transparent;
}
.dvt-tab { flex: 0 0 auto; max-width: 24rem; }
.dvt-tab .nav-link {
  font-size: .78rem;
  line-height: 1.2;
  padding: .25rem .55rem;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: .375rem .375rem 0 0;
  color: var(--bs-secondary-color);
  max-width: 24rem;
}
.dvt-tab .nav-link.active {
  color: var(--bs-body-color);
  background: var(--bs-body-bg);
  border-color: var(--bs-border-color);
  border-bottom-color: var(--bs-body-bg);
}
.dvt-title { max-width: 16rem; }
.dvt-close {
  font-size: .85rem;
  opacity: .55;
  padding: 0 .2rem;
  border-radius: .25rem;
}
.dvt-close:hover { opacity: 1; background: var(--bs-tertiary-bg); }
.dvt-tab-empty .nav-link {
  font-size: .78rem;
  padding: .25rem .55rem;
  border: none;
  background: transparent;
}

.center-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: var(--bs-secondary-color); text-align: center; padding: 2rem;
}
.center-empty .ico { font-size: 2rem; margin-bottom: .5rem; }

/* ===== 文件类型视觉（IntelliJ 风格） ===== */
.filebar-icon { font-size: 1.05rem; flex: 0 0 auto; }
.fc-badge {
  display: inline-flex; align-items: center; gap: .3rem;
  font-size: .68rem; font-weight: 600;
  border: 1px solid; border-radius: .375rem; padding: .1rem .45rem;
}
.dvt-fc-icon { font-size: .8rem; flex: 0 0 auto; }
</style>
