<script setup>
import { computed, ref, shallowRef, nextTick, onMounted, onUnmounted, onUpdated, watch } from 'vue'
import { state, activeTab, closeTab, pinTab, closeOtherTabs, closeAllTabs, toggleFocusMode, setFocusMode, toggleAiPanel, STATUS_META, STATUS_LABEL } from '../store'
import { toast } from '../store'
import { inlineDiff } from '../lib/diff_inline'
import { alignLines } from '../lib/diff_align'
import { foldContext } from '../lib/diff_fold'
import { tokenInfoAt, TOKEN_META } from '../lib/token_classify'
import { langOf, tokenizeLine } from '../lib/syntax_highlight'
import PathBar from './PathBar.vue'
import ContextMenu from './ContextMenu.vue'

// STATUS_META / STATUS_LABEL 从 store.js 共享，避免与 DiffTree/InfoPanel 重复定义
const STATUS_CLS = {
  ADDED: 'text-bg-success',
  DELETED: 'text-bg-danger',
  MODIFIED: 'text-bg-warning',
  UNCHANGED: 'text-bg-secondary'
}

// ---------- 性能相关常量 ----------
const EST_ROW_H = 21          // 行高估算值（真实高度由 measureVisible 回填）。
                               // ⚠️ 必须与 CSS .urow/.urow.fold 的 height:21px 保持同步——
                               // unified 虚拟滚动的 uniOffsets/uniTotal/scrollToUni/top 都按此值等差计算，
                               // 改这里必须同步改 CSS，否则热力图点击跳转与行定位会漂移。
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

// Git 风格 unified 视图（参考 IDE Git 插件对比模式）：左侧固定旧行号列 + 新行号列 + 后侧统一内容列，
// 相同/差异分段展示、差异行标 +/-、字符级高亮，右侧热力差异地图辅助定位。
// 默认关闭，保留既有 BCompare 式 split/wrap 视图可回退。
const gitMode = ref(false)
/** 折叠条点击：差异模式 → 切回全量内容（同时重置折叠）并展开；普通折叠 → 展开该段。 */
function onFoldClick() {
  if (diffOnly.value) { diffOnly.value = false; collapse.value = false }
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

/**
 * 哈希徽标圆点色：取 7 位短哈希的前 2 位当 hue，避免任意两个文件都同色（视觉上「同色 = 同文件」）。
 * 缺失占位 0000000 统一给灰色，提示「该侧无内容」。
 */
function hashTone(h) {
  if (!h || h === '0000000') return '#9ca3af'
  const v = parseInt(h.substring(0, 2), 16) || 0
  return 'hsl(' + (v * 3.6).toFixed(0) + ', 60%, 50%)'
}

// 语法高亮语言：扩展名优先（覆盖 .md/.py/.ts 等后端未细分类型），FileClass 兜底。
// key 取当前激活文件（tab 快照 / 树节点 / 归档内部复合键）。
const lang = computed(() => {
  const k = (at.value && at.value.node && at.value.node.key)
    || (at.value && at.value.key)
    || (node.value && node.value.key)
    || ''
  return langOf(k, currentFc.value)
})
// diff gutter 图标：还原为 +/- 号——左栏 del/rep 标「−」（旧侧删除），右栏 add/rep 标「+」（新侧新增）；
// ctx/空侧无图标（gtIcon 返回空串即不渲染 <i>）。+/- 符号更贴合 git/BDiff 约定，一眼看出差值语义。
function gtIcon(v, side) {
  if (side === 'left') return v.type === 'del' || v.type === 'rep' ? 'bi-dash' : ''
  return v.type === 'add' || v.type === 'rep' ? 'bi-plus' : ''
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
// 边缘情况：全 ctx 文件无变化行，collapse 折叠会吞掉全部内容，此时强制不折叠。
const foldedRows = computed(() => {
  if (!rows.value.length) return []
  if (diffOnly.value) return foldContext(rows.value, true, 0)
  const hasChanges = rows.value.some(r => r.type === 'del' || r.type === 'add' || r.type === 'rep')
  return foldContext(rows.value, collapse.value && hasChanges, foldWin)
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
  // 仅 rep 行需要行内差异；ctx/del/add 整行高亮即可（BCompare 同款：替换行才做字符级细分）。
  // 注：即便单段也必须用数组包裹——模板按 v-for="s in leftSegs" 遍历，传入对象会被当成
  // 4 个属性（m/kind/s/toks）展开，渲染出空 span，常见 ctx 文件全量内容"看不见"的根因。
  if (!inlineOn.value || r.type !== 'rep') {
    return { left: [withToks({ m: false, kind: '', s: r.leftText })], right: [withToks({ m: false, kind: '', s: r.rightText })] }
  }
  if (inlineCache.has(key)) return inlineCache.get(key)
  const segs = inlineDiff(r.leftText || '', r.rightText || '', granularity.value)
  const left = segs.filter(s => s.t !== 'add').map(s => withToks({ m: s.t === 'del', kind: 'del', s: s.s }))
  const right = segs.filter(s => s.t !== 'del').map(s => withToks({ m: s.t === 'add', kind: 'add', s: s.s }))
  const result = { left, right }
  inlineCache.set(key, result)
  return result
}
watch([granularity, oversized, forceFull, rawOld, rawNew, lang], () => { inlineCache.clear(); uniInlineCache.clear() })

// ======================================================================
// 虚拟滚动：前缀和 offsets + 二分定位可见区间 + 动态行高回填
// ======================================================================
const scrollTop = ref(0)
const viewportH = ref(600)
const diffAreaRef = ref(null) // 换行模式滚动层
const leftPaneRef = ref(null)
const rightPaneRef = ref(null)
const rowEls = new Map()      // abs(折叠行下标) -> {left,right,wrap}（split 双栏左右各一 dom；wrap 仅 wrap）
// split 模式左右两栏各自渲染同 abs 的 .prow，若只存单个 el 会被后注册者覆盖，
// 导致 measureVisible 只测到单侧高度：当另一侧内容换行成多行、offsetHeight 更大时，
// offsets 预留高度不足，该行溢出压到下一行区域（视觉「行叠加」）。故按侧分桶存储。
const rowSlots = (abs) => { if (!rowEls.has(abs)) rowEls.set(abs, { left: null, right: null, wrap: null }); return rowEls.get(abs) }

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

// ======================================================================
// Git 风格 unified 视图数据：把 foldedRows 拍平成「左旧行号|新行号|内容」的单列展示行。
//  - ctx→1 行（两行号齐）；del→1 行（仅旧行号）；add→1 行（仅新行号）；
//  - rep→拍成 2 行（『-』旧行、『+』新行），行内字符高亮由 uniInline 配对左右文本。
// 独立于 split/wrap 的虚拟滚动：unified 固定行高（不换行，超长行横向滚动），
// 因此 uniOffsets 是等差序列，热力差异地图按变化行分布等比映射，点击跳转对应行。
// ======================================================================
const uniAreaRef = ref(null)        // unified 滚动层
const uniInlineCache = new Map()    // key = 源foldedRows下标|侧|粒度|语言
/** 待拍平的折叠行下标 → 语义（'ne'=正常/'oid'=仅旧/'nid'=仅新/'mod'=修改对），供判断是否差异行 */
const isUniChange = (t) => t === 'del' || t === 'add' || t === 'rep-del' || t === 'rep-add'

const uniRows = computed(() => {
  const fr = foldedRows.value
  const out = []
  for (let i = 0; i < fr.length; i++) {
    const r = fr[i]
    if (r.type === 'fold') { out.push({ type: 'fold', count: r.count, src: i }); continue }
    switch (r.type) {
      case 'ctx':
        out.push({ type: 'ctx', oldLn: r.left, newLn: r.right, text: r.leftText || '', kind: 'c', src: i })
        break
      case 'del':
        out.push({ type: 'del', oldLn: r.left, newLn: '', text: r.leftText || '', kind: 'd', src: i })
        break
      case 'add':
        out.push({ type: 'add', oldLn: '', newLn: r.right, text: r.rightText || '', kind: 'a', src: i })
        break
      default: { // rep 修改对 → 两行
        out.push({ type: 'rep-del', oldLn: r.left, newLn: '', text: r.leftText || '', src: i, kind: 'd', mod: true })
        out.push({ type: 'rep-add', oldLn: '', newLn: r.right, text: r.rightText || '', src: i, kind: 'a', mod: true })
      }
    }
  }
  return out
})

/** 修改对行内差异：按侧取对应高亮片段（del 侧取 del 片段、add 侧取 add 片段），其余 eq 常规显示。 */
const uniSegs = computed(() => {
  const ur = uniRows.value
  const out = new Array(ur.length)
  for (let i = 0; i < ur.length; i++) {
    const x = ur[i]
    if (x.mod) {
      const fr = foldedRows.value[x.src]
      // 整行粒度(line)＝关闭行内差异细化：rep 修改对被拆成 rep-del/rep-add 两行，靠 .urow.rep-del/.rep-add
      // 整行淡底色标识即可，不再细分 im-del/im-add。若不拦截，inlineDiff 会把 'line' 当字符级处理，
      // 使「整行」档在 git 模式退化为字符粒度——这就是粒度按钮在 git 模式看不出变化的根因。
      if (granularity.value === 'line') {
        const text = x.kind === 'd' ? ((fr && fr.leftText) || '') : ((fr && fr.rightText) || '')
        out[i] = [{ m: false, kind: '', s: text, toks: tokenizeLine(text, lang.value) }]
        continue
      }
      // 缓存 key 追加两侧行文本指纹：折叠/仅差异切换会让同一 src 下标对应到不同内容的 rep 行
      //（foldContext 的 win 变化会重排 foldedRows），不加指纹会命中旧片段导致高亮错配。
      const lf = ((fr && fr.leftText) || '')
      const rf = ((fr && fr.rightText) || '')
      const key = x.src + '|' + x.kind + '|' + granularity.value + '|' + lang.value + '|' + lf + '\u0001' + rf
      if (!uniInlineCache.has(key)) {
        const segs = inlineDiff(lf, rf, granularity.value)
        const half = x.kind === 'd'
          ? segs.filter(s => s.t !== 'add').map(s => withToks({ m: s.t === 'del', kind: 'del', s: s.s }))
          : segs.filter(s => s.t !== 'del').map(s => withToks({ m: s.t === 'add', kind: 'add', s: s.s }))
        uniInlineCache.set(key, half)
      }
      out[i] = uniInlineCache.get(key)
    } else if (x.type === 'ctx' || x.type === 'del' || x.type === 'add') {
      out[i] = [withToks({ m: false, kind: '', s: x.text })]
    } else {
      out[i] = [] // fold 占位行：模板走 fold 分支不渲染 segs，给空数组保证 out[i] 恒为数组
    }
  }
  return out
})

// unified 虚拟滚动：固定行高，偏移为等差 → 二分定位可见区间
const uniOffsets = computed(() => {
  const n = uniRows.value.length
  const arr = new Array(n + 1)
  arr[0] = 0
  for (let i = 1; i <= n; i++) arr[i] = arr[i - 1] + EST_ROW_H
  return arr
})
const uniTotal = computed(() => uniRows.value.length * EST_ROW_H)
function uniFindStart(y) {
  const o = uniOffsets.value
  let lo = 0, hi = o.length - 1, ans = 0
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (o[mid] <= y) { ans = mid; lo = mid + 1 } else hi = mid - 1 }
  return ans
}
function uniFindEnd(y) {
  const o = uniOffsets.value
  let lo = 0, hi = o.length - 1, ans = o.length - 1
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (o[mid] < y) lo = mid + 1; else { ans = mid; hi = mid - 1 } }
  return ans
}
const uniStart = computed(() => Math.max(0, uniFindStart(scrollTop.value) - OVERSCAN))
const uniEnd = computed(() => Math.min(uniRows.value.length, uniFindEnd(scrollTop.value + viewportH.value) + OVERSCAN))
const uniVisible = computed(() => {
  const ur = uniRows.value
  const us = uniSegs.value
  const res = []
  for (let i = uniStart.value; i < uniEnd.value; i++) {
    const x = ur[i]
    res.push({ type: x.type, abs: i, oldLn: x.oldLn, newLn: x.newLn, text: x.text, count: x.count, segs: us[i], kind: x.kind, ri: uniAbsToRi(i) })
  }
  return res
})
function onUniScroll(e) {
  scrollTop.value = e.target.scrollTop
  viewportH.value = e.target.clientHeight
}
// 热力差异地图：合并连续变化行为一条色块，记录其在 unified 全高中的起止偏移
const uniHeat = computed(() => {
  const ur = uniRows.value
  const off = uniOffsets.value
  const bars = []
  let i = 0
  const n = ur.length
  while (i < n) {
    if (!isUniChange(ur[i].type)) { i++; continue }
    let j = i
    let hasDel = false, hasAdd = false
    while (j < n && isUniChange(ur[j].type)) {
      if (ur[j].kind === 'd') hasDel = true
      else if (ur[j].kind === 'a') hasAdd = true
      j++
    }
    bars.push({ top: off[i], bottom: off[j], h: off[j] - off[i], start: i, endAbs: j, del: hasDel, add: hasAdd })
    i = j
  }
  return bars
})
function scrollToUni(abs) {
  const el = uniAreaRef.value
  if (!el) return
  el.scrollTo({ top: Math.max(0, (uniOffsets.value[abs] || 0) - el.clientHeight / 2 + EST_ROW_H / 2), behavior: 'smooth' })
}

// 热力地图点击后高亮的目标拍平行（响应式，保证 .heat 类随滚动/重渲染稳定呈现）
const centerUniAbs = ref(-1)
function onUniHeatClick(bar) {
  flashRi.value = -1
  scrollToUni(bar.start)
  centerUniAbs.value = bar.start
}
// 双栏(split/wrap)热力差异地图：与 unified 同款——合并连续变更行(DEL/ADD/REP)为色块，
// 按全高比例映射到右缘。foldedRows 的 offsets 为长度 n+1（末项=总高），块边界直接取 off[i]/off[j]。
const splitHeat = computed(() => {
  const fr = foldedRows.value
  if (!fr.length) return []
  const off = offsets.value
  const bars = []
  let i = 0, n = fr.length
  while (i < n) {
    const t = fr[i].type
    if (t !== 'del' && t !== 'add' && t !== 'rep') { i++; continue }
    let j = i, hasDel = false, hasAdd = false
    while (j < n) {
      const c = fr[j].type
      if (c !== 'del' && c !== 'add' && c !== 'rep') break
      if (c === 'add') hasAdd = true
      else if (c === 'del') hasDel = true
      else { hasDel = true; hasAdd = true } // rep 修改对同时含删除侧+新增侧，块标混合（橙）
      j++
    }
    const top = off[i] || 0
    const bottom = off[j] || top
    bars.push({ top, bottom, h: bottom - top, start: i, endAbs: j, del: hasDel, add: hasAdd })
    i = j
  }
  return bars
})
function onSplitHeatClick(bar) {
  flashRi.value = -1
  scrollToSplit(bar.start)
}
function scrollToSplit(abs) {
  // wrap 换行模式是单滚动容器(diffAreaRef)，split 分栏是左右 pane——按模式选滚动目标
  const target = wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value)
  if (!target) return
  const top = offsets.value[abs] || 0
  const h = heights.value[abs] || EST_ROW_H
  target.scrollTo({ top: Math.max(0, top - viewportH.value / 2 + h / 2), behavior: 'smooth' })
  // split 双栏再同步右栏，保持视野一致；wrap 单容器无此操作
  if (!wrap.value && leftPaneRef.value && rightPaneRef.value) rightPaneRef.value.scrollTop = leftPaneRef.value.scrollTop
}
/**
 * unified 拍平行 abs → 原始 rows 下标（_i）。
 * 映射链：uniRows[abs].src（foldedRows 下标）→ foldedRows[src]._i（rows 下标）。
 * fold 行无 _i（无可定位的源行）→ 返回 -1。
 * 供状态栏（statusInfo 用 rows[ri]）与光标定位复用，避免把拍平行号误当原始下标。
 */
function uniAbsToRi(abs) {
  const ur = uniRows.value[abs]
  if (!ur || ur.type === 'fold') return -1
  const fr = foldedRows.value[ur.src]
  return (fr && typeof fr._i === 'number') ? fr._i : -1
}

// unified 单列：rep-add 展示的是新侧内容（右列语义），其余行统一按旧侧取文本/行号
function uniSideOf(v) { return v.type === 'rep-add' ? 'right' : 'left' }

function onUniMove(e, v) {
  if (v.type === 'fold') return
  const p = locateCell(e, v)
  if (!p) return
  const ri = uniAbsToRi(v.abs)
  if (ri < 0) return
  const side = uniSideOf(v)
  const last = hoverPos.value
  if (last && last.ri === ri && last.side === side && last.col0 === p.col0) return
  hoverPos.value = { ri, side, col0: p.col0 }
}
function onUniClick(e, v) {
  if (v.type === 'fold') return
  const p = locateCell(e, v)
  if (!p) return
  const ri = uniAbsToRi(v.abs)
  if (ri < 0) return
  const side = uniSideOf(v)
  caretPos.value = { ri, side, col0: p.col0 }
  hoverPos.value = { ri, side, col0: p.col0 }
}
// 切换视图/换行/Git 模式时：统一滚动位置 + 清空光标/悬停状态（git 模式下 ri 语义与 split 不同，
// 残留旧光标会让状态栏显示过期坐标），再量取滚动容器高度。
watch([gitMode, wrap], () => {
  scrollTop.value = 0
  caretPos.value = null
  hoverPos.value = null
  curIdx.value = -1
  nextTick(() => { if (uniAreaRef.value) viewportH.value = uniAreaRef.value.clientHeight })
})

const heights = ref([])
// 折叠行集合变化（分块解析推进 / 折叠切换）时，保留已测得的高度、新增行用估算值。
watch(foldedRows, (fr) => {
  const h = heights.value.slice(0, fr.length)
  while (h.length < fr.length) h.push(EST_ROW_H)
  heights.value = h
}, { flush: 'sync' })

function setRowRef(abs, el, side) {
  // el 为空（dom 卸载）时仅清对应侧，不影响其余侧；split 双栏据此互补测量
  const s = rowSlots(abs)
  if (el) s[side] = el
  else s[side] = null
}
function measureVisible() {
  const hs = heights.value.slice()
  let changed = false
  rowEls.forEach((s, abs) => {
    // 行高取两侧实际高度最大值：offsets 按此预留，才能容纳换行成多行的那一侧，杜绝"行叠加"
    const lh = s.left ? s.left.offsetHeight : 0
    const rh = s.right ? s.right.offsetHeight : 0
    const wh = s.wrap ? s.wrap.offsetHeight : 0
    const h = Math.max(lh, rh, wh)
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

// 中键关闭 tab；左键点 tab 文字切激活，点 x 关闭；右键打开 tab 上下文菜单。
function onTabClick(key) { state.activeKey = key }
function onTabClose(e, key) { e.stopPropagation(); closeTab(key) }
function onTabMouseDown(e, key) {
  if (e.button === 1) { e.preventDefault(); closeTab(key) } // 中键关闭
}

// ===================== tab 标题右键菜单（BCompare 风格） =====================
// 桌面壳能力检测：复制到剪贴板 / 系统级「在资源管理器中显示」仅在 Electron/Tauri 可用。
const tabCtx = ref({ visible: false, x: 0, y: 0, key: null })
const tabCtxNode = ref(null) // 当前右键的 tab 及其 node（用于路径解析）
const hasShell = computed(() => !!(typeof window !== 'undefined' && window.bempdiff &&
  (typeof window.bempdiff.openPath === 'function' || typeof window.bempdiff.showInFolder === 'function')))

function openTabCtx(e, t) {
  e.preventDefault(); e.stopPropagation()
  tabCtxNode.value = t
  tabCtx.value = { visible: true, x: e.clientX, y: e.clientY, key: t.key }
}
function closeTabCtx() { tabCtx.value = { ...tabCtx.value, visible: false } }

// 右键菜单项（BCompare 风格、含定界分组）：
//   关闭类 / 路径复制（含资源管理器，仅桌面壳+有磁盘时可用）/ 固定。
const tabMenuItems = computed(() => {
  const t = tabCtxNode.value
  if (!t) return []
  const info = t ? tabDiskInfo(t) : null
  const hasDisk = !!(info && info.hasDisk)
  const pinned = !!t.pinned
  const others = state.tabs.length > 1
  const anyPinnedOther = state.tabs.some(x => x.pinned && x.key !== t.key)
  return [
    { id: 'close',       group: 'close', label: '关闭',              icon: 'bi-x-lg',   disabled: false, title: '关闭当前对比页' },
    { id: 'closeOthers', group: 'close', label: '关闭其他',          icon: 'bi-collection', disabled: !others, title: !others ? '仅一个对比页，无可关闭的其它页' : '关闭除当前与已固定外的其它对比页' },
    { id: 'closeAll',    group: 'close', label: '全部关闭',          icon: 'bi-x-square', disabled: !others && !anyPinnedOther, title: anyPinnedOther ? '已固定的对比页将被保留' : '关闭所有对比页（已固定的保留）' },
    { id: 'sep',         group: 'copy',  divider: true },
    { id: 'copyPath',    group: 'copy',  label: '复制路径',          icon: 'bi-link-45deg', disabled: false, title: hasDisk ? '复制文件绝对路径' : '复制包内相对路径（无磁盘路径）' },
    { id: 'copyRelPath', group: 'copy',  label: '复制相对路径',      icon: 'bi-subtract', disabled: false, title: '复制文件相对路径' },
    { id: 'reveal',      group: 'copy',  label: '在资源管理器中显示', icon: 'bi-folder2-open', disabled: !hasDisk || !hasShell.value, title: !hasShell.value ? '浏览器模式无法调用资源管理器，请使用桌面壳' : (!hasDisk ? '包内条目无磁盘路径，无法在资源管理器中定位' : '在系统文件管理器中定位该文件') },
    { id: 'sep2',        group: 'pin',   divider: true },
    { id: 'pin',         group: 'pin',   label: pinned ? '取消固定' : '固定', icon: pinned ? 'bi-pin-angle' : 'bi-pin', disabled: false, title: pinned ? '取消固定，该页将可被关闭/排序' : '固定该对比页，置顶且在关闭类操作中保留' }
  ]
})

function onTabMenuSelect(item) {
  const t = tabCtxNode.value
  if (!item || !t) return
  switch (item.id) {
    case 'close': closeTab(t.key); return
    case 'closeOthers': closeOtherTabs(t.key); return
    case 'closeAll': closeAllTabs(); return
    case 'copyPath': onTabCopyPath(); return
    case 'copyRelPath': onTabCopyRelPath(); return
    case 'reveal': onTabRevealInFolder(); return
    case 'pin': pinTab(t.key); return
  }
}

// 复制文本到剪贴板（clipboard API，非安全上下文降级 execCommand）。
async function copyTabText(text, tip) {
  try {
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    toast('success', tip + (text && text.length > 60 ? '：' + text.slice(0, 60) + '…' : ''))
  } catch (_) {
    toast('danger', '复制失败（剪贴板不可用）')
  }
  closeTabCtx()
}

// 当前右键 tab 的路径信息：
//  - folder 模式：node 持磁盘绝对路径 absPath（按差异状态选左/右根）+ 相对根 relPath，可复制绝对/相对路径与资源管理器定位；
//  - 包内条目（ARCHIVE-INNER，node 为 null）或包比对模式：无磁盘路径，仅包内相对 key（复制路径按包内 key，资源管理器项禁用）。
function tabDiskInfo(t) {
  const folderMode = !!state.job && state.job.mode === 'folder'
  const node = t && t.node
  if (!node) return { absPath: '', relPath: (t && t.key) || '', hasDisk: false }
  const abs = node.absPath || ''
  return {
    absPath: abs,
    relPath: node.relPath || node.key || '',
    hasDisk: folderMode && !!abs
  }
}

async function onTabCopyPath() {
  const t = tabCtxNode.value
  if (!t) return
  const info = tabDiskInfo(t)
  if (info.hasDisk) await copyTabText(info.absPath, '已复制绝对路径')
  else await copyTabText(info.relPath || t.key, '已复制包内路径')
}
async function onTabCopyRelPath() {
  const t = tabCtxNode.value
  if (!t) return
  const info = tabDiskInfo(t)
  await copyTabText(info.relPath || t.key, '已复制相对路径')
}
async function onTabRevealInFolder() {
  const t = tabCtxNode.value
  if (!t) return
  const info = tabDiskInfo(t)
  if (!info.hasDisk) { toast('warning', '包内条目无磁盘路径，无法在资源管理器中显示'); closeTabCtx(); return }
  try {
    const r = await window.bempdiff.showInFolder(info.absPath)
    if (r && typeof r === 'object' && !r.ok) toast('danger', r.message || '资源管理器显示失败')
    else toast('info', '已在资源管理器中定位')
  } catch (e) { toast('danger', '资源管理器显示失败：' + e.message) }
  closeTabCtx()
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
    // Git 风格 unified 视图：目标行在 uniRows 中的位置 = foldedRows[di] 对应的拍平行起点
    // （rep 拆成两行，取第一个匹配的拍平行即可）。滚动走 uniAreaRef，与 split/wrap 无关。
    if (gitMode.value) {
      const uAbs = uniRows.value.findIndex(u => u.src === di)
      if (uAbs < 0) return
      scrollToUni(uAbs)
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
  const el = gitMode.value ? uniAreaRef.value : (wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value))
  if (!el) return
  viewportH.value = el.clientHeight || viewportH.value
  ro = new ResizeObserver(() => {
    const c = gitMode.value ? uniAreaRef.value : (wrap.value ? diffAreaRef.value : (leftPaneRef.value || rightPaneRef.value))
    if (c) viewportH.value = c.clientHeight
    // unified 固定行高，heights 仅 split/wrap 用——git 模式跳过，避免多余重算
    if (!gitMode.value) {
      // 宽度变化会改变行高 → 重置为估算，下一次 onUpdated 实测回填
      heights.value = new Array(foldedRows.value.length).fill(EST_ROW_H)
    }
  })
  ro.observe(el)
}
watch([wrap, gitMode], () => { nextTick(setupObserver) })

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
                    @mousedown="onTabMouseDown($event, t.key)"
                    @contextmenu="openTabCtx($event, t)">
              <i v-if="t.pinned" class="bi bi-pin-angle-fill" style="font-size:.6rem" :style="{color: 'var(--bs-secondary-color)'}" title="已固定（关闭类操作保留）"></i>
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

    <!-- tab 标题右键菜单（复用通用 ContextMenu：BCompare 风格，含分组分隔与禁用理由） -->
    <ContextMenu :visible="tabCtx.visible" :x="tabCtx.x" :y="tabCtx.y" :items="tabMenuItems"
                 @select="onTabMenuSelect" @close="closeTabCtx" />

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
      <!-- Git 短哈希徽标：左侧旧版（7 位 SHA-1 截断，缺失则 0000000 占位），右侧新版；
           类比 `git rev-parse --short=7`，两侧相同时合并为单色（说明此文件未变）。
           视觉上参照 IDE Git 插件：用等宽字体 + 淡底色 + 小徽标，方便扫读。 -->
      <span class="hash-badge" v-if="dec && (dec.oldHash || dec.newHash)" :title="'Git 风格 7 位短哈希（基于文件字节 SHA-1）· 旧侧 ' + (dec.oldHash || '0000000') + ' / 新侧 ' + (dec.newHash || '0000000')">
        <span class="hash-side hash-old" :class="{ 'hash-empty': dec.oldHash === '0000000' }">
          <i class="bi bi-circle-fill hash-dot" :style="{ color: hashTone(dec.oldHash) }"></i>{{ dec.oldHash || '0000000' }}
        </span>
        <i class="bi bi-arrow-right hash-arrow"></i>
        <span class="hash-side hash-new" :class="{ 'hash-empty': dec.newHash === '0000000' }">
          <i class="bi bi-circle-fill hash-dot" :style="{ color: hashTone(dec.newHash) }"></i>{{ dec.newHash || '0000000' }}
        </span>
      </span>
      <!-- 右侧信息+操作区：包成整块 flex，窄屏触发 filebar 换行时整块跳到下一行并靠右，
           避免统计/按钮在第二行散落在左边、层级杂乱。 -->
      <div class="filebar-actions">
      <span class="text-secondary" style="font-size:.75rem" title="当前文件使用的反编译引擎（默认 CFR）">
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
              @click="gitMode = !gitMode"
              :title="gitMode ? '当前：Git 风格统一对比（左行号 + 后统一内容 + 热力地图）· 点击切换为双栏' : '切换为 Git 风格统一对比（左行号列固定，插除/新增分段展示 + 右侧热力差异地图）'">
        <i class="bi" :class="gitMode ? 'bi-file-diff' : 'bi-columns-gap'"></i>
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
      <!-- 查看模式：全量内容 / 仅差异内容 合并为单个动画图标，点击切换（图标随状态翻转淡入） -->
      <button class="btn btn-sm dvt-mode-btn ms-1 py-0 px-2" style="font-size:1.05rem"
              :class="diffOnly ? 'btn-primary' : 'btn-outline-secondary'"
              @click="diffOnly = !diffOnly; collapse = false"
              :title="diffOnly ? '当前：仅差异内容（隐藏未变更行）· 点击切换为全量内容' : '当前：全量内容（显示全部代码行，含未变更）· 点击切换为仅差异内容'">
        <i :key="diffOnly ? 'diff' : 'full'"
           class="bi dvt-mode-ic"
           :class="diffOnly ? 'bi-distribute-vertical' : 'bi-file-earmark-text'"></i>
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
    </div>

    <!-- 主体：反编译完成且差异解析完成 → 虚拟滚动渲染 -->
    <div class="diff-area" v-if="(node || dec) && dec && dec.ok && parseStatus === 'done'">
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

      <!-- Git 风格 unified 视图：左「旧行号|新行号」列固定 + 后统一内容列（删/改/增段按 -/+ 区分），右侧热力差异地图 -->
      <div class="diff-unified" v-if="gitMode">
        <div class="uni-body" ref="uniAreaRef" @scroll="onUniScroll">
          <div class="uni-grid" :style="{ height: uniTotal + 'px' }">
            <div v-for="(v, k) in uniVisible" :key="'u' + v.abs"
                 class="urow" :class="[v.type === 'fold' ? 'fold' : v.type, { heat: v.abs === centerUniAbs, current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
                 @mousemove="onUniMove($event, v)" @click="onUniClick($event, v)"
                 :style="{ top: (v.abs * EST_ROW_H) + 'px' }">
              <template v-if="v.type === 'fold'">
                <span class="u-fold" role="button" tabindex="0" :title="foldLabel(v)" @click="onFoldClick" @keydown.enter="onFoldClick">{{ foldLabel(v) }}</span>
              </template>
              <template v-else>
                <span class="u-sign" :class="'u-sign-' + v.kind">{{ v.kind === 'd' ? '−' : (v.kind === 'a' ? '+' : ' ') }}</span>
                <span class="u-ln-old" :class="{ 'u-ln-d': v.oldLn && v.kind === 'd' }">{{ v.oldLn }}</span>
                <span class="u-ln-new" :class="{ 'u-ln-a': v.newLn && v.kind === 'a' }">{{ v.newLn }}</span>
                <span class="u-code" data-side="uni"><span v-for="(s, si) in v.segs" :key="si" :class="{'im-del': s.m && s.kind === 'del', 'im-add': s.m && s.kind === 'add'}"><template v-for="(t, ti) in s.toks" :key="ti"><span v-if="t.type !== 'ws' && t.type !== 'plain'" class="tok" :class="'tok-' + t.type">{{ t.text }}</span><template v-else>{{ t.text }}</template></template></span></span>
              </template>
            </div>
          </div>
        </div>
        <!-- 右侧热力差异地图：等比映射差异块位置，点击跳转定位（无内容/无差异时隐藏） -->
        <div class="uni-heat" v-if="uniTotal > 0 && uniHeat.length" :title="'全局差异地图：深色块=删除、浅色=新增，点击跳转到对应位置'">
          <div v-for="(b, bi) in uniHeat" :key="'hb' + bi" class="uni-heat-bar"
               :class="{ 'uni-heat-del': b.del, 'uni-heat-add': b.add }"
               :style="{ top: (b.top / uniTotal * 100) + '%', height: (isNaN(b.h / uniTotal * 100) ? 1 : b.h / uniTotal * 100) + '%' }"
               :title="'差异区域 ' + (b.start + 1) + '..' + (b.endAbs)"
               @click="onUniHeatClick(b)"></div>
        </div>
      </div>

      <!-- 换行模式：单容器 4 列网格（整行背景 + 逐行对齐，长行自动换行） -->
      <div class="diff-flex" v-else-if="wrap">
        <div class="diff-scroll" ref="diffAreaRef" @scroll="onDiffScroll">
          <div class="diff-grid" :style="{ height: totalHeight + 'px' }">
          <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
               class="row" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
               :data-ri="v.ri" :ref="el => setRowRef(v.abs, el, 'wrap')"
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

        <!-- 换行模式右侧热力差异地图：等比映射差异块位置，点击跳转定位（与双栏一致） -->
        <div class="uni-heat" v-if="splitHeat.length" :title="'全局差异地图：深色块=删除、浅色=新增，点击跳转到对应位置'">
          <div v-for="(b, bi) in splitHeat" :key="'wb' + bi" class="uni-heat-bar"
               :class="{ 'uni-heat-del': b.del, 'uni-heat-add': b.add }"
               :style="{ top: (b.top / totalHeight * 100) + '%', height: (isNaN(b.h / totalHeight * 100) ? 1 : b.h / totalHeight * 100) + '%' }"
               :title="'差异区域 ' + (b.start + 1) + '..' + (b.endAbs)"
               @click="onSplitHeatClick(b)"></div>
        </div>
      </div>

      <!-- 不换行模式：左右分栏各占一半宽度 + 中间分隔条；两侧底部横向滚动条同步左右滑动 -->
      <div class="diff-split" v-else>
        <div class="diff-pane" ref="leftPaneRef" @scroll="onPaneScroll('left')">
          <div class="pane-grid" :style="{ height: totalHeight + 'px' }">
            <div v-for="(v, k) in visibleRows" :key="v.ri >= 0 ? 'r' + v.ri : 'f' + v.abs"
                 class="prow" :class="[v.type === 'fold' ? 'fold' : v.type, { current: v.ri === currentRowIdx, flash: v.ri === flashRi }]"
                 :data-ri="v.ri" :ref="el => setRowRef(v.abs, el, 'left')"
                 @mousemove="onRowMove($event, v)" @click="onRowClick($event, v)"
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0', minHeight: (heights[v.abs] || EST_ROW_H) + 'px' }">
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
                 :data-ri="v.ri" :ref="el => setRowRef(v.abs, el, 'right')"
                 @mousemove="onRowMove($event, v)" @click="onRowClick($event, v)"
                 :style="{ position: 'absolute', top: offsets[v.abs] + 'px', left: '0', right: '0', minHeight: (heights[v.abs] || EST_ROW_H) + 'px' }">
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

        <!-- 双栏右侧热力差异地图：等比映射差异块位置，点击跳转定位（复用 unified 的色块样式） -->
        <div class="uni-heat" v-if="splitHeat.length" :title="'全局差异地图：深色块=删除、浅色=新增，点击跳转到对应位置'">
          <div v-for="(b, bi) in splitHeat" :key="'sb' + bi" class="uni-heat-bar"
               :class="{ 'uni-heat-del': b.del, 'uni-heat-add': b.add }"
               :style="{ top: (b.top / totalHeight * 100) + '%', height: (isNaN(b.h / totalHeight * 100) ? 1 : b.h / totalHeight * 100) + '%' }"
               :title="'差异区域 ' + (b.start + 1) + '..' + (b.endAbs)"
               @click="onSplitHeatClick(b)"></div>
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
      <!-- 失败时要展示后端返回的具体原因(dec.error)，而非只报引擎名——例如旧版二进制 .xls/.doc 会被明确提示「请另存为 .xlsx/.docx」 -->
      <div v-if="dec.engine" class="text-secondary mb-1" style="font-size:.85rem">反编译引擎：{{ dec.engine }}</div>
      <div>{{ dec.error || '该文件无法反编译' }}</div>
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
/* 换行(wrap)模式热力条容器：横向 flex，左侧滚动区 + 右缘 .uni-heat 热力地图并排 */
.diff-flex { display: flex; flex: 1 1 auto; min-height: 0; position: relative; }
.diff-grid { position: relative; }
/* Git 风格 unified 视图：左「旧行号|新行号」固定列 + 统一内容列；右缘热力差异地图 */
.diff-unified { flex: 1 1 auto; min-height: 0; display: flex; position: relative; }
.uni-body { overflow: auto; flex: 1 1 0; min-width: 0; position: relative; }
.uni-grid { position: relative; }
.urow {
  position: absolute; left: 0;
  box-sizing: border-box;
  height: 21px; /* ⚠️ 必须与 JS EST_ROW_H(21) 同步——unified 虚拟滚动/热力图按此值等差计算，改须两处同改 */
  display: flex; align-items: stretch;
  width: max-content; min-width: 100%;   /* 超长行可横向滚动，行号/符号列 sticky 固定 */
  font-family: var(--bs-font-monospace);
  font-size: .82rem; line-height: 1.5;
  color: var(--bs-body-color);
  white-space: pre;
}
/* 左列（符号/旧行号/新行号）横向滚动时 sticky 固定在左缘，保证「左侧行号序列」恒可见 */
.urow .u-sign {
  width: 1.3rem; flex: 0 0 1.3rem; display: flex; align-items: center; justify-content: center;
  position: sticky; left: 0; z-index: 1;
  background: var(--bs-tertiary-bg);
  border-right: 1px solid var(--bs-border-color); user-select: none; color: var(--bs-secondary-color);
}
/* git 风格 +/- 标注：符号列随删/增行走红/绿，让 -/+ 在滚动时依旧可辨识（区别于中性内容行）。 */
.urow.del .u-sign, .urow.rep-del .u-sign { background: rgba(248, 81, 73, 0.14); color: var(--bs-danger); font-weight: 700; }
.urow.add .u-sign, .urow.rep-add .u-sign { background: rgba(46, 160, 67, 0.14); color: var(--bs-success); font-weight: 700; }
.urow .u-ln-old, .urow .u-ln-new {
  flex: 0 0 3.2rem; text-align: right; padding: 0 .4rem;
  position: sticky; z-index: 1;
  color: var(--bs-secondary-color);
  background: color-mix(in srgb, var(--dt-accent, var(--bs-tertiary-bg)) 8%, var(--bs-tertiary-bg));
  user-select: none; border-right: 1px solid var(--bs-border-color);
}
.urow .u-ln-old { left: 1.3rem; }
.urow .u-ln-new { left: 4.5rem; }
.urow .u-ln-d { color: var(--bs-danger); font-weight: 700; }
.urow .u-ln-a { color: var(--bs-success); font-weight: 700; }
.urow .u-code {
  flex: 1 1 auto; min-width: 0; padding: 0 .6rem; cursor: text; overflow: visible;
}
/* 差异行底色：删除(旧)红 / 新增(新)绿 / 修改段占位——只对内容与符号列刷淡底，行号列保持中性。
   Trae IDE 风格：与 split 模式同步，整行底色从 10% 降到 8% 极浅，避免与行内片段背景叠加产生糊状。 */
.urow.del, .urow.rep-del { background: rgba(248, 81, 73, 0.10); }
.urow.add, .urow.rep-add { background: rgba(46, 160, 67, 0.10); }
.urow.fold { background: var(--bs-tertiary-bg); height: 21px; }
.urow.fold .u-fold {
  flex: 1; align-self: center; text-align: center; font-size: .72rem;
  color: var(--bs-secondary-color); cursor: pointer; user-select: none;
}
.urow:hover { background: var(--bs-tertiary-bg); }
.urow.heat { outline: 2px solid var(--bs-primary); outline-offset: -2px; }
/* unified 差异导航高亮：current=当前差异行（左侧类型色条 + 行号加深），flash=跳转后短暂闪烁。
   与 split/wrap 的 .row.current/.row.flash 视觉一致，保证 git 模式下「上一处/下一处」同样有反馈。 */
.urow.current {
  --cur-halo: color-mix(in srgb, var(--dt-accent, var(--bs-primary)) 35%, transparent);
  box-shadow:
    inset 6px 0 0 0 var(--dt-accent, var(--bs-primary)),
    inset 9px 0 0 0 var(--cur-halo);
}
.urow.current .u-ln-old, .urow.current .u-ln-new { color: var(--dt-accent, var(--bs-primary)); font-weight: 700; }
.urow.flash { animation: uniFlash .9s ease-out; }
@keyframes uniFlash {
  0% { background: var(--bs-primary-bg-subtle); }
  100% { background: transparent; }
}
/* unified 行内 token 沿用既有 .tok / .im-del / .im-add 高亮，无需重复定义 */
/* 热力差异地图：定位到右缘，垂直铺满，色块按差异块等比映射 */
.uni-heat {
  flex: 0 0 10px; margin-left: 4px; border-left: 1px solid var(--bs-border-color);
  background: var(--bs-tertiary-bg); position: relative; min-height: 0;
}
.uni-heat-bar {
  position: absolute; right: 1px; width: 8px; border-radius: 2px; cursor: pointer;
  transition: width .12s, right .12s;
}
.uni-heat-bar:hover { width: 12px; right: -1px; }
.uni-heat-del { background: color-mix(in srgb, var(--bs-danger) 78%, transparent); }
.uni-heat-add { background: color-mix(in srgb, var(--bs-success) 70%, transparent); }
.uni-heat-add.uni-heat-del { background: color-mix(in srgb, #b45309 70%, transparent); }
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
/* diff gutter：行类型色条 + 图标（add/del/rep 统一用 💡 灯泡，Trae IDE 风格）。
   图标只在「该侧存在该类型内容」时出现：左栏 del/rep、右栏 add/rep（gtIcon 控制）。 */
.row .gt, .prow .gt {
  display: flex; align-items: center; justify-content: center;
  font-size: .72rem; user-select: none;
  border-right: 1px solid var(--bs-border-color);
}
/* Trae 风格：变更行 gutter 用极浅同色底 + 主色灯泡，无饱和度叠加避免视觉重 */
.row .gt.gt-add, .prow .gt.gt-add { background: rgba(46, 160, 67, 0.10); color: #1a7f37; }
.row .gt.gt-del, .prow .gt.gt-del { background: rgba(248, 81, 73, 0.10); color: #cf222e; }
.row .gt.gt-rep, .prow .gt.gt-rep { background: rgba(187, 128, 9, 0.10); color: #9a6700; }
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
   Trae IDE 风格：行内差异片段用 IDE 同款极浅同色系（VS Code Deletion #ffebe9 / Addition #e6ffec），
   不再叠加深色 box-shadow 描边（小字号下 1px inset 描边会与背景叠加产生「重影」）。
   文字保留 token 类型色（currentColor），让红/绿底上的语法层次自然透出，不再 color-mix 加深。 */
.code .im-del { background: #ffebe9; border-radius: 2px; color: inherit; }
.code .im-add { background: #e6ffec; border-radius: 2px; color: inherit; }
/* Git 风格 unified 视图：内容区类名不是 .code 而是 .u-code，需为同样片段补同款高亮，
   否则 rep 行的词/字符级细分（im-del/im-add）匹配不到规则，粒度切换在 git 模式下无可见差异。
   颜色较 split 视图加深一档：unified 内容紧凑、行底淡色更易被区分需求淹没，加深红/绿便于一眼分辨差异位。 */
.u-code .im-del { background: rgba(248, 81, 73, 0.38); border-radius: 2px; color: inherit; }
.u-code .im-add { background: rgba(46, 160, 67, 0.36); border-radius: 2px; color: inherit; }
[data-bs-theme="dark"] .code .im-del { background: rgba(248, 81, 73, 0.32); }
[data-bs-theme="dark"] .code .im-add { background: rgba(46, 160, 67, 0.32); }
[data-bs-theme="dark"] .u-code .im-del { background: rgba(248, 81, 73, 0.55); }
[data-bs-theme="dark"] .u-code .im-add { background: rgba(46, 160, 67, 0.52); }
/* 折叠占位行：类型色 + 斜体，提示被折叠段落归属；前置 ● 圆点表达"被收起"语义。
   圆点用当前文件类型色（--dt-accent），与 filebar 类型徽标呼应。 */
.row.fold, .prow.fold { background: var(--bs-tertiary-bg); border-left: 3px solid var(--dt-accent, var(--bs-border-color)); }
.row.fold .fold-ph, .prow.fold .fold-ph {
  color: var(--dt-accent, var(--bs-secondary-color));
  font-style: italic; text-align: center; cursor: pointer; user-select: none;
  display: flex; align-items: center; justify-content: center; gap: .4rem;
}
.row.fold .fold-ph::before, .prow.fold .fold-ph::before {
  content: '●'; color: var(--dt-accent, var(--bs-secondary-color));
  font-size: .55rem; line-height: 1;
}
/* 差异行：左侧 3px 主色条（gutter 内已刷底色，色条强化"该行是变更"语义，一眼锁定）。
   Trae IDE 风格：整行底色从 14-18% 降到 8-10%，避免与行内片段背景叠加产生糊状；
   文字继承正文色（inherited），让 token 类型色在淡色行底上自然透出，保持代码可读性。 */
.row, .prow { box-shadow: inset 0 0 0 0 transparent; }
.row.add, .prow.add {
  background: rgba(46, 160, 67, 0.06);
  box-shadow: inset 3px 0 0 #1a7f37;
}
.row.add .ln, .prow.add .ln { color: #1a7f37; font-weight: 600; }
.row.add .ln:last-of-type { border-left: 1px solid var(--bs-border-color); }
.row.del, .prow.del {
  background: rgba(248, 81, 73, 0.06);
  box-shadow: inset 3px 0 0 #cf222e;
}
.row.del .ln, .prow.del .ln { color: #cf222e; font-weight: 600; }
/* BCompare 风格「替换行」：修改对同行左右对照，整体极淡橙底（VS Code modified 风格）+ 左侧 3px 橙条；
   行内 im-del/im-add 继续用 IDE 极浅色细分，差异片段不再加 inset 描边避免重影 */
.row.rep, .prow.rep {
  background: rgba(187, 128, 9, 0.06);
  box-shadow: inset 3px 0 0 #9a6700;
}
.row.rep .ln, .prow.rep .ln { color: #9a6700; font-weight: 600; }
/* 暗色主题：整行底色降到 10%（更淡），只作"该行有变更"的轻提示——差异细节交给行内 im-del/im-add 片段
   （0.32 透明同色），否则「整行 0.16 + 片段 0.32」同色相叠会在大段修改区糊成深色斑块（视觉重影/字糊）。 */
[data-bs-theme="dark"] .row.add, [data-bs-theme="dark"] .prow.add { background: rgba(46, 160, 67, 0.10); box-shadow: inset 3px 0 0 #3fb950; }
[data-bs-theme="dark"] .row.add .ln, [data-bs-theme="dark"] .prow.add .ln { color: #3fb950; }
[data-bs-theme="dark"] .row.del, [data-bs-theme="dark"] .prow.del { background: rgba(248, 81, 73, 0.10); box-shadow: inset 3px 0 0 #f85149; }
[data-bs-theme="dark"] .row.del .ln, [data-bs-theme="dark"] .prow.del .ln { color: #f85149; }
[data-bs-theme="dark"] .row.rep, [data-bs-theme="dark"] .prow.rep { background: rgba(187, 128, 9, 0.09); box-shadow: inset 3px 0 0 #d29922; }
[data-bs-theme="dark"] .row.rep .ln, [data-bs-theme="dark"] .prow.rep .ln { color: #d29922; }
/* split 模式 rep 行右 code 右缘虚线：强化「该行在另一侧有对应修改对」—— 比 .gt-rep 图标更显眼，
   也避免在长行/缩进行里看不到 gutter 图标。仅作用于 split 模式右栏，wrap/unified 模式无此需求。 */
.diff-pane.right .prow.rep .code {
  border-right: 1px dashed var(--bs-warning);
  padding-right: .4rem;
  margin-right: -1px; /* 抵消虚线宽度避免挤压布局 */
}
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
/* diff 片段（红/绿底）内：token 保留类型色（Trae IDE 风格），让语法层次在淡色行底上自然透出。
   旧的 color-mix 60% 加深会让 token 与红/绿底融合成糊状，Trae 选择保留 token 原色，靠行底淡色 + 片段
   极浅背景的对比保证可读性。 */
.code .im-del .tok, .code .im-add .tok { color: inherit; }
/* unified 视图内容区（.u-code）同样让片段内 token 继承，保持红/绿底上可读 */
.u-code .im-del .tok, .u-code .im-add .tok { color: inherit; }
/* 文件栏差异统计（+新增 −删除 ~修改 =未变） */
.dvt-stats { display: inline-flex; align-items: center; gap: .5rem; font-size: .72rem; white-space: nowrap; }
/* 当前定位的差异行：双层描边（6px 类型色主条 + 3px 极淡辅条）+ 行号主色加粗。
   box-shadow 多 inset 叠加实现"双层条"——主条最贴近边缘 6px，辅条再往内 3px 形成"主-辅"层次。
   落到 .row.add/.row.del/.row.rep 上时，原本的 3px 差异色条会与 current 的 6px 主色条叠加，current 优先。
   注：不再给 .code 叠加主色背景，避免与红/绿整行底色 + im-del/im-add 片段背景三重叠加产生糊状。
   实现细节：把 color-mix(...) 预解析到 --cur-halo 变量，避免 cssnano 把
   `inset 9px 0 0 color-mix(...)` 压成 `inset 9px 0 color-mix(...)`（缺 spread radius，
   box-shadow 简写在某些浏览器下被整条丢弃，current 双层描边退化为 rep 灯条）。 */
.row.current, .prow.current {
  --cur-halo: color-mix(in srgb, var(--dt-accent, var(--bs-primary)) 35%, transparent);
  box-shadow:
    inset 6px 0 0 0 var(--dt-accent, var(--bs-primary)),
    inset 9px 0 0 0 var(--cur-halo);
}
.row.current .ln, .prow.current .ln { color: var(--dt-accent, var(--bs-primary)); font-weight: 700; }
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
/* Git 短哈希徽标：等宽字体 + 圆点色调 + 缺失占位降透明；旧/新两色 + 箭头表达「从 X 到 Y」。
   同色 dot 帮助识别「该文件未变」；hashTone 来自前 2 位十六进制，分布较均匀。 */
.hash-badge {
  display: inline-flex; align-items: center; gap: .35rem;
  font-family: var(--bs-font-monospace);
  font-size: .7rem; font-weight: 500;
  padding: .08rem .5rem; border-radius: .375rem;
  background: color-mix(in srgb, var(--bs-body-color) 6%, transparent);
  border: 1px solid var(--bs-border-color);
  user-select: text;
}
.hash-side { display: inline-flex; align-items: center; gap: .25rem; }
.hash-dot { font-size: .55rem; flex: 0 0 auto; }
.hash-arrow { font-size: .65rem; color: var(--bs-secondary-color); margin: 0 .1rem; }
.hash-empty { opacity: .5; font-style: italic; }
/* 查看模式动画图标：点击切换 全量/仅差异，图标以翻转+缩放+淡入进入（:key 变更强制重播动画） */
.dvt-mode-ic { display: inline-block; animation: dvtModeIn .22s ease; }
@keyframes dvtModeIn {
  0% { opacity: 0; transform: rotate(-90deg) scale(.6); }
  100% { opacity: 1; transform: rotate(0) scale(1); }
}
</style>
