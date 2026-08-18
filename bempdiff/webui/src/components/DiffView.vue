<script setup>
import { computed, ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
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

// 把后端返回的 unified diffText 解析成左右对齐的行（含行号）。
// 单滚动容器 + 每行 4 列网格 => 左右天然同步滚动且逐行对齐（add/del 空白侧自动等高）。
const rows = computed(() => {
  const d = dec.value
  if (!d || !d.diffText) return []
  const out = []
  let leftNo = 0, rightNo = 0
  for (const raw of d.diffText.split('\n')) {
    if (raw.startsWith('@@') || raw.startsWith('\\ No newline') ||
        raw.startsWith('--- ') || raw.startsWith('+++ ')) continue
    if (raw.startsWith('-')) {
      leftNo++
      out.push({ type: 'del', left: '' + leftNo, leftText: raw.slice(1), right: '', rightText: '' })
    } else if (raw.startsWith('+')) {
      rightNo++
      out.push({ type: 'add', left: '', leftText: '', right: '' + rightNo, rightText: raw.slice(1) })
    } else if (raw.startsWith(' ')) {
      leftNo++; rightNo++
      out.push({ type: 'ctx', left: '' + leftNo, leftText: raw.slice(1), right: '' + rightNo, rightText: raw.slice(1) })
    } else {
      out.push({ type: 'ctx', left: '', leftText: raw, right: '', rightText: raw })
    }
  }
  return out
})

// 行内差异高亮：把相邻 del/add 行配对，按当前粒度做词/字符级 LCS。
// 每个 row 产出 {left:[{m,kind,s}], right:[...]}，m=true 表示需 <mark> 高亮。
const inlineMap = computed(() => {
  const rs = rows.value
  const res = rs.map(r => ({
    left: [{ m: false, kind: '', s: r.leftText || '' }],
    right: [{ m: false, kind: '', s: r.rightText || '' }]
  }))
  if (granularity.value === 'line') return res
  let i = 0
  while (i < rs.length) {
    if (rs[i].type === 'del') {
      let j = i; while (j < rs.length && rs[j].type === 'del') j++
      let k = j; while (k < rs.length && rs[k].type === 'add') k++
      const dels = rs.slice(i, j), adds = rs.slice(j, k)
      const n = Math.max(dels.length, adds.length)
      for (let t = 0; t < n; t++) {
        const d = dels[t], a = adds[t]
        if (d && a) {
          const segs = inlineDiff(d.leftText || '', a.rightText || '', granularity.value)
          const di = rs.indexOf(d), ai = rs.indexOf(a)
          res[di].left = segs.filter(s => s.t !== 'add').map(s => ({ m: s.t === 'del', kind: 'del', s: s.s }))
          res[ai].right = segs.filter(s => s.t !== 'del').map(s => ({ m: s.t === 'add', kind: 'add', s: s.s }))
        }
      }
      i = k
    } else if (rs[i].type === 'add') {
      let j = i; while (j < rs.length && rs[j].type === 'add') j++
      i = j
    } else { i++ }
  }
  return res
})

// 显示行：折叠未变（collapse）后在变化行内嵌行内高亮片段。
// 每项携带原始索引 ri（= rows 中的下标），供「上一处/下一处」导航按 data-ri 定位。
const displayRows = computed(() => {
  const indexed = rows.value.map((r, i) => ({ ...r, _i: i }))
  const folded = collapse.value ? foldContext(indexed, true, foldWin) : indexed
  return folded.map(x => {
    if (x.type === 'fold') return { type: 'fold', count: x.count, ri: -1 }
    const m = inlineMap.value[x._i]
    return {
      type: x.type, ri: x._i,
      left: x.left, leftText: x.leftText,
      right: x.right, rightText: x.rightText,
      leftSegs: m.left, rightSegs: m.right
    }
  })
})

// 中键关闭 tab；左键点 tab 文字切激活，点 x 关闭。
function onTabClick(key) { state.activeKey = key }
function onTabClose(e, key) { e.stopPropagation(); closeTab(key) }
function onTabMouseDown(e, key) {
  // 中键关闭
  if (e.button === 1) { e.preventDefault(); closeTab(key) }
}

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
    if (!diffRows.value.length) return // 无差异可跳时不拦截原按键（不再无条件 preventDefault）
    e.preventDefault()
    if (e.key === 'ArrowUp') gotoPrev()
    else gotoNext()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null } // 卸载时清理跳转高亮定时器，避免孤儿回调
})

// ---------- 差异行快速定位（上一处 / 下一处） ----------
// 仅 add / del 行算「差异行」；ctx 上下文行跳过。
// curIdx 指向 diffRows 中当前高亮的差异行；-1 表示尚未定位（下一处从首个开始）。
const diffRows = computed(() => rows.value.map((r, i) => ({ r, i })).filter(x => x.r.type === 'add' || x.r.type === 'del'))
const diffCount = computed(() => diffRows.value.length)
const curIdx = ref(-1)
const currentRowIdx = computed(() => (curIdx.value >= 0 && curIdx.value < diffRows.value.length) ? diffRows.value[curIdx.value].i : -1)
const diffAreaRef = ref(null) // 换行模式（单容器）滚动层
// 不换行模式：左右分栏滚动层 + 同步滚动（纵向逐行对齐、横向同步左右滑动）
const leftPaneRef = ref(null)
const rightPaneRef = ref(null)
let paneSyncing = false
function onPaneScroll(side) {
  if (paneSyncing) return
  const src = side === 'left' ? leftPaneRef.value : rightPaneRef.value
  const dst = side === 'left' ? rightPaneRef.value : leftPaneRef.value
  if (!src || !dst) return
  paneSyncing = true
  // 纵向：保证左右两侧逐行对齐；横向：两侧底部滚动条同步左右滑动
  dst.scrollTop = src.scrollTop
  dst.scrollLeft = src.scrollLeft
  requestAnimationFrame(() => { paneSyncing = false })
}
const flashRi = ref(-1) // 当前需要 flash 高亮的行索引；动画结束后复位为 -1 以便重复触发
let flashTimer = null

// 切换激活文件（activeKey 变化：切 tab / 关 tab / 重新比对）时重置差异行导航状态，
// 避免计数器越界（如旧文件定位第 7 处、新文件只有 3 处显示 7/3）与「当前行」残留高亮。
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
  // 等 DOM 更新后把目标行滚动进可视区
  nextTick(() => {
    const ri = diffRows.value[clamped].i
    const sel = `.row[data-ri="${ri}"], .prow[data-ri="${ri}"]`
    if (wrap.value) {
      // 换行模式：单容器滚动
      const el = diffAreaRef.value
      const target = el && el.querySelector(sel)
      if (!target) return
      const cRect = el.getBoundingClientRect()
      const tRect = target.getBoundingClientRect()
      const delta = (tRect.top - cRect.top) - (el.clientHeight / 2) + (tRect.height / 2)
      el.scrollTo({ top: Math.max(0, el.scrollTop + delta), behavior: 'smooth' })
    } else {
      // 不换行模式：左右分栏，两侧同步滚动（纵向对齐 + 横向同步）
      ;[leftPaneRef.value, rightPaneRef.value].forEach(pane => {
        if (!pane) return
        const target = pane.querySelector(sel)
        if (!target) return
        const cRect = pane.getBoundingClientRect()
        const tRect = target.getBoundingClientRect()
        const delta = (tRect.top - cRect.top) - (pane.clientHeight / 2) + (tRect.height / 2)
        pane.scrollTo({ top: Math.max(0, pane.scrollTop + delta), behavior: 'smooth' })
      })
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
</script>

<template>
  <div class="col-center">
    <!--
      tab 栏：始终渲染，避免 0/1 tab 时这层"忽隐忽现"导致用户找不到。
      - 0 tab：占位提示「未打开任何对比 · 从左侧差异树选择文件」
      - ≥1 tab：每个 tab 显示状态色点 + 文件末段 + busy spinner + 关闭 x
      视觉锚点：背景 --bs-tertiary-bg 与下方 filebar 的 --bs-body-bg 形成层次，下方 2px 边框明确分层。
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
      <span class="path">{{ node ? titleShort(node.key, 80) : '未选择文件' }}</span>
      <span v-if="node" class="badge" :class="STATUS_CLS[node.status]">{{ STATUS_LABEL[node.status] }}</span>
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

    <div class="diff-area" v-if="node && dec && dec.ok">
      <!-- 换行模式：单容器 4 列网格（整行背景 + 逐行对齐，长行自动换行） -->
      <div class="diff-scroll" ref="diffAreaRef" v-if="wrap">
        <div class="diff-grid">
          <div v-for="(r, i) in displayRows" :key="r.ri >= 0 ? 'r' + r.ri : 'f' + i"
               class="row" :class="[r.type === 'fold' ? 'fold' : r.type, { current: r.ri === currentRowIdx, flash: r.ri === flashRi }]"
               :data-ri="r.ri">
            <template v-if="r.type === 'fold'">
              <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                    :title="'已折叠 ' + r.count + ' 行未变更内容，点击展开'"
                    @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ r.count }} 行未变更内容（点击展开）⋯</span>
            </template>
            <template v-else>
              <span class="ln">{{ r.left }}</span>
              <span class="code flex-1"><span v-for="(s, si) in r.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
              <span class="ln">{{ r.right }}</span>
              <span class="code flex-1"><span v-for="(s, si) in r.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
            </template>
          </div>
        </div>
      </div>

      <!-- 不换行模式：左右分栏各占一半宽度 + 中间分隔条；两侧底部横向滚动条同步左右滑动 -->
      <div class="diff-split" v-else>
        <div class="diff-pane" ref="leftPaneRef" @scroll="onPaneScroll('left')">
          <div class="pane-grid">
            <div v-for="(r, i) in displayRows" :key="r.ri >= 0 ? 'r' + r.ri : 'f' + i"
                 class="prow" :class="[r.type === 'fold' ? 'fold' : r.type, { current: r.ri === currentRowIdx, flash: r.ri === flashRi }]"
                 :data-ri="r.ri">
              <template v-if="r.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="'已折叠 ' + r.count + ' 行未变更内容，点击展开'"
                      @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ r.count }} 行未变更内容（点击展开）⋯</span>
              </template>
              <template v-else>
                <span class="ln">{{ r.left }}</span>
                <span class="code flex-1"><span v-for="(s, si) in r.leftSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
              </template>
            </div>
          </div>
        </div>

        <div class="diff-splitter" title="左 / 右 文件分栏"></div>

        <div class="diff-pane right" ref="rightPaneRef" @scroll="onPaneScroll('right')">
          <div class="pane-grid">
            <div v-for="(r, i) in displayRows" :key="r.ri >= 0 ? 'r' + r.ri : 'f' + i"
                 class="prow" :class="[r.type === 'fold' ? 'fold' : r.type, { current: r.ri === currentRowIdx, flash: r.ri === flashRi }]"
                 :data-ri="r.ri">
              <template v-if="r.type === 'fold'">
                <span class="code flex-1 fold-ph" style="grid-column:1/-1" role="button" tabindex="0"
                      :title="'已折叠 ' + r.count + ' 行未变更内容，点击展开'"
                      @click="collapse = false" @keydown.enter="collapse = false">⋯ 已折叠 {{ r.count }} 行未变更内容（点击展开）⋯</span>
              </template>
              <template v-else>
                <span class="ln">{{ r.right }}</span>
                <span class="code flex-1"><span v-for="(s, si) in r.rightSegs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}">{{ s.s }}</span></span>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="diff-area center-empty" v-else-if="node && busy">
      <div class="spinner-border text-secondary" role="status" aria-hidden="true"></div>
      <div class="mt-2">正在反编译/美化源码…</div>
    </div>

    <div class="diff-area center-empty" v-else-if="node && dec && !dec.ok">
      <div class="ico"><i class="bi bi-exclamation-triangle"></i></div>
      <div>该文件无法反编译（引擎：{{ dec.engine }}）</div>
      <div v-if="dec.diffText" class="text-start mt-2" style="white-space:pre-wrap;font-size:.8rem">{{ dec.diffText }}</div>
    </div>

    <div class="diff-area center-empty" v-else-if="node && tabErr">
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
/* 外层 diff 区：flex 列，内部滚动容器各自处理溢出，避免整体抖动 */
.diff-area { display: flex; flex-direction: column; flex: 1 1 auto; min-height: 0; background: var(--bs-body-bg); overflow: hidden; }

/* 换行模式：单滚动容器 + 4 列网格（逐行对齐，整行背景） */
.diff-scroll { flex: 1 1 auto; min-height: 0; overflow: auto; }
.diff-grid { display: block; }

/* 不换行模式：左右分栏各占一半宽度 + 中间分隔条 + 同步横向/纵向滚动 */
.diff-split { flex: 1 1 auto; min-height: 0; display: flex; }
/* 左栏：可横向滚动（底部滚动条），纵向隐藏滚动条但由右栏同步驱动，保证逐行对齐 */
.diff-pane { flex: 1 1 50%; min-width: 0; overflow-x: auto; overflow-y: hidden; background: var(--bs-body-bg); }
/* 右栏：横向 + 纵向均可滚动，作为同步主滚动源 */
.diff-pane.right { overflow-y: auto; }
.diff-splitter { flex: 0 0 5px; background: var(--bs-border-color); }
.pane-grid { display: block; width: max-content; min-width: 100%; }

/* 行容器：换行用 .row（4 列），不换行用 .prow（2 列），共享视觉规则 */
.row, .prow {
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
  font-family: var(--bs-font-monospace);
  font-size: .82rem;
  line-height: 1.5;
}
.row .code { white-space: pre-wrap; word-break: break-word; }
.prow .code { white-space: pre; } /* 不换行：单行横向滚动 */
/* 行内词/字符级差异高亮（对标 GitHub inline diff） */
.code .im-del { background: rgba(220,53,69,.38); border-radius: 2px; }
.code .im-add { background: rgba(25,135,84,.38); border-radius: 2px; }
/* 折叠占位行 */
.row.fold, .prow.fold { background: var(--bs-tertiary-bg); }
.row.fold .fold-ph, .prow.fold .fold-ph { color: var(--bs-secondary-color); font-style: italic; text-align: center; cursor: pointer; user-select: none; }
.row.add, .prow.add { background: var(--bs-success-bg-subtle); }
.row.add .ln:last-of-type { border-left: 1px solid var(--bs-border-color); } /* 换行模式：空侧与新侧分隔线 */
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
/*
 * 关键设计：tabbar 是一层"永远在"的容器（0 tab 时也渲染占位提示），
 * 用 tertiary-bg 与下方 filebar 的 body-bg 形成层次，避免用户看不到
 * 「已打开的对比 tab」这一层；同时 2px 边框 + 最小高度保证视觉锚点。
 */
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
  border-bottom: none;        /* Bootstrap nav-tabs 自带下边框，这里去掉避免双线 */
  margin-bottom: 0;           /* 同上，避免与 .dvt-tabbar 的 border-bottom 叠 */
  background: transparent;     /* 用父容器 .dvt-tabbar 的背景，避免重复 */
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