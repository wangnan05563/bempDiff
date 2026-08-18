<script setup>
import { computed, ref, shallowRef, nextTick, onMounted, onUnmounted, onUpdated, watch } from 'vue'
import { state, activeTab, closeTab, toggleFocusMode, setFocusMode, toggleAiPanel, STATUS_META, STATUS_LABEL } from '../store'
import { inlineDiff } from '../lib/diff_inline'
import { foldContext } from '../lib/diff_fold'

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
const CHUNK = 5000            // 差异解析分块行数（避免大文件一次性解析卡 UI）
const PARSE_SYNC_LIMIT = 20000 // 小于此行数一次性同步解析（免分块闪烁）
const OVERSIZED_LINES = 60000  // 差异行数超此阈值 → 降级「简洁视图」（关行内高亮 + 默认折叠未变）

const wrap = ref(true) // true=换行（默认，完整展示长行）；false=不换行（横向滚动，BCompare 风格）
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
// 分块异步解析 unified diffText → 基础行（含原始下标 _i，供行内差异缓存与定位）
// parseGen：每次切换文件自增，在途分块循环检测到 gen 变化即中止（切换取消机制）。
// ======================================================================
const rawText = computed(() => (dec.value && dec.value.diffText) ? dec.value.diffText : '')
const parseGen = ref(0)
const parseStatus = ref('idle') // idle | parsing | done
const rows = shallowRef([])     // 基础解析行（无折叠、无行内高亮），_i = 原始下标
const totalStats = ref({ added: 0, removed: 0, unchanged: 0, total: 0 })

function isMeta(raw) {
  return raw.startsWith('@@') || raw.startsWith('\\ No newline') ||
         raw.startsWith('--- ') || raw.startsWith('+++ ')
}
function pushRow(out, raw, counters, idx) {
  if (isMeta(raw)) return
  counters.total++
  if (raw.startsWith('-')) { counters.leftNo++; counters.removed++; out.push({ type: 'del', _i: idx, left: '' + counters.leftNo, leftText: raw.slice(1), right: '', rightText: '' }) }
  else if (raw.startsWith('+')) { counters.rightNo++; counters.added++; out.push({ type: 'add', _i: idx, left: '', leftText: '', right: '' + counters.rightNo, rightText: raw.slice(1) }) }
  else if (raw.startsWith(' ')) { counters.leftNo++; counters.rightNo++; counters.unchanged++; out.push({ type: 'ctx', _i: idx, left: '' + counters.leftNo, leftText: raw.slice(1), right: '' + counters.rightNo, rightText: raw.slice(1) }) }
  else { counters.unchanged++; out.push({ type: 'ctx', _i: idx, left: '', leftText: raw, right: '', rightText: raw }) }
}
const idle = (typeof window !== 'undefined' && window.requestIdleCallback)
  ? (cb) => window.requestIdleCallback(cb, { timeout: 200 })
  : (cb) => setTimeout(cb, 0)

function startParse(text) {
  const gen = ++parseGen.value
  inlineCache.clear()
  rows.value = []
  totalStats.value = { added: 0, removed: 0, unchanged: 0, total: 0 }
  if (!text) { parseStatus.value = 'done'; return }
  const lines = text.split('\n')
  const total = lines.length
  if (total <= PARSE_SYNC_LIMIT) {
    const c = { leftNo: 0, rightNo: 0, added: 0, removed: 0, unchanged: 0, total: 0 }
    const out = []
    for (const raw of lines) pushRow(out, raw, c, out.length)
    rows.value = out
    totalStats.value = c
    parseStatus.value = 'done'
    return
  }
  parseStatus.value = 'parsing'
  const c = { leftNo: 0, rightNo: 0, added: 0, removed: 0, unchanged: 0, total: 0 }
  const acc = []
  let idx = 0
  const step = () => {
    if (gen !== parseGen.value) return // 已切换到别的文件 → 中止在途分块
    const end = Math.min(idx + CHUNK, total)
    for (; idx < end; idx++) pushRow(acc, lines[idx], c, rows.value.length + acc.length)
    if (acc.length) { rows.value = rows.value.concat(acc.splice(0)) }
    if (idx < total) idle(step)
    else { totalStats.value = c; parseStatus.value = 'done' }
  }
  step()
}

// 折叠未变（collapse）后用于渲染的行集合；fold 行无 _i。
const foldedRows = computed(() => {
  if (!rows.value.length) return []
  return foldContext(rows.value, collapse.value, foldWin)
})

// 差异行（add/del）快速定位序列；i = 原始下标 _i。
const diffRows = computed(() => {
  const rs = rows.value
  const out = []
  for (let i = 0; i < rs.length; i++) {
    if (rs[i].type === 'add' || rs[i].type === 'del') out.push({ r: rs[i], i })
  }
  return out
})
const diffCount = computed(() => diffRows.value.length)

// ======================================================================
// 行内差异：仅对「可见行」惰性计算并缓存（不在全量行上跑 LCS，避免大文件卡顿）。
// inlineCache 为非响应式 Map；inlineOn 变化（粒度/超大/展开）时清空。
// ======================================================================
const inlineCache = new Map()
const oversized = computed(() => rows.value.length > OVERSIZED_LINES)
const inlineOn = computed(() => granularity.value !== 'line' && (!oversized.value || forceFull.value))
function plainSegs(text) { return [{ m: false, kind: '', s: text || '' }] }
function getInline(ri) {
  const rs = rows.value
  const r = rs[ri]
  if (!r) return { left: [{ m: false, s: '' }], right: [{ m: false, s: '' }] }
  if (!inlineOn.value) return { left: plainSegs(r.leftText), right: plainSegs(r.rightText) }
  if (inlineCache.has(ri)) return inlineCache.get(ri)
  computeBlock(ri)
  return inlineCache.get(ri) || { left: plainSegs(r.leftText), right: plainSegs(r.rightText) }
}
// 计算 del/add 配对块的左右行内片段，按原始下标缓存两侧。
function computeBlock(ri) {
  const rs = rows.value
  const t0 = rs[ri].type
  let di, dj, aj, ak
  if (t0 === 'del') {
    di = ri; while (di - 1 >= 0 && rs[di - 1].type === 'del') di--
    dj = ri; while (dj < rs.length && rs[dj].type === 'del') dj++
    aj = dj; ak = dj; while (ak < rs.length && rs[ak].type === 'add') ak++
  } else {
    aj = ri; while (aj - 1 >= 0 && rs[aj - 1].type === 'add') aj--
    ak = ri; while (ak < rs.length && rs[ak].type === 'add') ak++
    dj = aj; di = dj; while (di - 1 >= 0 && rs[di - 1].type === 'del') di--
  }
  const n = Math.max(dj - di, ak - aj)
  for (let t = 0; t < n; t++) {
    const dI = di + t, aI = aj + t
    const d = dI < dj ? rs[dI] : null
    const a = aI < ak ? rs[aI] : null
    let l, r
    if (d && a) {
      const segs = inlineDiff(d.leftText || '', a.rightText || '', granularity.value)
      l = segs.filter(s => s.t !== 'add').map(s => ({ m: s.t === 'del', kind: 'del', s: s.s }))
      r = segs.filter(s => s.t !== 'del').map(s => ({ m: s.t === 'add', kind: 'add', s: s.s }))
    } else if (d) { l = plainSegs(d.leftText); r = plainSegs('') }
    else { l = plainSegs(''); r = plainSegs(a.rightText) }
    if (dI < dj) inlineCache.set(dI, { left: l, right: plainSegs('') })
    if (aI < ak) inlineCache.set(aI, { left: plainSegs(''), right: r })
  }
}
watch([granularity, oversized, forceFull, rawText], () => inlineCache.clear())

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

// tab 标题里塞不下时省略号显示
function titleShort(k, max = 60) {
  if (!k) return ''
  return k.length > max ? k.slice(0, max - 1) + '…' : k
}

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

// 进入新文件：重置滚动位置 + 重新解析 + 清空导航状态
watch(rawText, (t) => {
  scrollTop.value = 0
  curIdx.value = -1
  flashRi.value = -1
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null }
  startParse(t || '')
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
  <div class="col-center">
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
      <i class="bi bi-file-earmark-code"></i>
      <span class="path">{{ node ? titleShort(node.key, 80) : (activeTab ? titleShort(activeTab.key, 80) : '未选择文件') }}</span>
      <span v-if="node" class="badge" :class="STATUS_CLS[node.status]">{{ STATUS_LABEL[node.status] }}</span>
      <span v-else-if="activeTab" class="badge text-bg-light border" :title="'内部条目（归档内文件）'">ARCHIVE-INNER</span>
      <span class="badge text-bg-light border" v-if="node">{{ node.fileClass }}</span>
      <span class="ms-auto text-secondary me-2" style="font-size:.75rem" title="当前文件使用的反编译引擎（默认 CFR）">
        反编译引擎：{{ dec ? dec.engine : '—' }}
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
        <span>大文件已降级为简洁视图：共 <b>{{ rows.length.toLocaleString() }}</b> 行差异
          （<span class="text-success">+{{ totalStats.added.toLocaleString() }}</span> /
           <span class="text-danger">-{{ totalStats.removed.toLocaleString() }}</span> /
           {{ totalStats.unchanged.toLocaleString() }} 未变），已关闭行内高亮并默认折叠未变更以保障流畅。</span>
        <button class="btn btn-sm btn-outline-primary py-0 px-2 ms-2" @click="forceFull = true; collapse = false">展开完整差异（可能卡顿）</button>
      </div>

      <!-- 换行模式：单容器 4 列网格（整行背景 + 逐行对齐，长行自动换行） -->
      <div class="diff-scroll" ref="diffAreaRef" v-if="wrap" @scroll="onDiffScroll">
        <div class="diff-grid" :style="{ height: totalHeight + 'px' }">
          <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
               class="row" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
               :data-ri="v.ri" :ref="el => setRowRef(v.abs, el)"
               :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
            <template v-if="v.type === 'fold'">
              <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                    :title="'已折叠 ' + v.count + ' 行未变更内容，点击展开'"
                    @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ v.count }} 行未变更内容（点击展开）⋯</span>
            </template>
            <template v-else>
              <span class="ln">{{ v.left }}</span>
              <span class="code flex-1"><span v-for="(s, si) in v.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
              <span class="ln">{{ v.right }}</span>
              <span class="code flex-1"><span v-for="(s, si) in v.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
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
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
              <template v-if="v.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="'已折叠 ' + v.count + ' 行未变更内容，点击展开'"
                      @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ v.count }} 行未变更内容（点击展开）⋯</span>
              </template>
              <template v-else>
                <span class="ln">{{ v.left }}</span>
                <span class="code flex-1"><span v-for="(s, si) in v.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
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
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0' }">
              <template v-if="v.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="'已折叠 ' + v.count + ' 行未变更内容，点击展开'"
                      @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ v.count }} 行未变更内容（点击展开）⋯</span>
              </template>
              <template v-else>
                <span class="ln">{{ v.right }}</span>
                <span class="code flex-1"><span v-for="(s, si) in v.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
              </template>
            </div>
          </div>
        </div>
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
.row { grid-template-columns: 3.2rem 1fr 3.2rem 1fr; }
.prow { grid-template-columns: 3.2rem 1fr; }
.row:hover, .prow:hover { background: var(--bs-tertiary-bg); }
.row .ln, .prow .ln {
  text-align: right; padding: 0 .4rem;
  color: var(--bs-secondary-color);
  background: var(--bs-tertiary-bg);
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
}
/* 行内词/字符级差异高亮（对标 GitHub inline diff） */
.code .im-del { background: rgba(220,53,69,.38); border-radius: 2px; }
.code .im-add { background: rgba(25,135,84,.38); border-radius: 2px; }
/* 折叠占位行 */
.row.fold, .prow.fold { background: var(--bs-tertiary-bg); }
.row.fold .fold-ph, .prow.fold .fold-ph { color: var(--bs-secondary-color); font-style: italic; text-align: center; cursor: pointer; user-select: none; }
.row.add, .prow.add { background: var(--bs-success-bg-subtle); }
.row.add .ln:last-of-type { border-left: 1px solid var(--bs-border-color); }
.row.del, .prow.del { background: var(--bs-danger-bg-subtle); }
/* 当前定位的差异行：左侧主色条，便于快速辨识 */
.row.current, .prow.current { box-shadow: inset 3px 0 0 var(--bs-primary); }
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
</style>
