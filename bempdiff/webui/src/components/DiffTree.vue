<script setup>
import { computed, ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { state, selectEntry, toggleArchive, STATUS_META } from '../store'

// STATUS_META 已迁到 store.js，与 DiffView/InfoPanel 共享
const LAYER_LABEL = { L0: 'L0 包级', L1: 'L1 业务码', L2: 'L2 三方依赖', '?': '其他' }
const LAYER_TITLE = {
  L0: 'L0 包级：war/jar 顶层包的差异',
  L1: 'L1 业务码：命中内部包前缀的内部业务类差异',
  L2: 'L2 三方依赖：第三方库 class 的差异',
  '?': '其他：未归类的差异项'
}

const tree = computed(() => (state.job && state.job.tree) || [])
const counts = computed(() => {
  const c = { ADDED: 0, DELETED: 0, MODIFIED: 0, UNCHANGED: 0 }
  for (const n of tree.value) if (c[n.status] !== undefined) c[n.status]++
  return c
})
const filtered = computed(() => {
  const cfg = state.config || {}
  const q = (cfg.filterSearch || '').trim()
  const regex = !!cfg.filterRegex
  let list = tree.value
  // 状态多选项过滤：勾选即显示，取消即隐藏（全部取消 = 空）
  const showStatus = {
    MODIFIED: cfg.filterShowModified !== false,
    ADDED: cfg.filterShowAdded !== false,
    DELETED: cfg.filterShowDeleted !== false,
    UNCHANGED: cfg.filterShowUnchanged !== false
  }
  list = list.filter(n => showStatus[n.status] !== false)
  // 文件名模糊 / 正则搜索
  if (q) {
    try {
      const re = regex ? new RegExp(q, 'i') : null
      list = list.filter(n => re ? re.test(n.key) : n.key.toLowerCase().includes(q.toLowerCase()))
    } catch (_) { /* 非法正则时忽略搜索 */ }
  }
  // 风险过滤：已做智能分类且配置了风险筛选项时生效（A1 优先级联动）
  const aiMap = state.aiClassify || {}
  const rf = cfg.filterRisk
  if (rf && rf.length && Object.keys(aiMap).length) {
    list = list.filter(n => { const c = aiMap[n.key]; return c && rf.includes(c.risk) })
  }
  return list
})

const groups = computed(() => {
  const order = ['L0', 'L1', 'L2', '?']
  const map = new Map()
  for (const n of filtered.value) {
    const L = n.layer || '?'
    if (!map.has(L)) map.set(L, [])
    map.get(L).push(n)
  }
  const out = []
  const cfgG = state.config || {}
  const aiMapG = state.aiClassify || {}
  const pushGroup = (L) => {
    if (!map.has(L)) return
    const nodes = map.get(L)
    if (cfgG.sortByRisk) {
      nodes.sort((a, b) =>
        riskRank(aiMapG[b.key] && aiMapG[b.key].risk) - riskRank(aiMapG[a.key] && aiMapG[a.key].risk)
        || a.key.localeCompare(b.key))
    } else {
      nodes.sort((a, b) => a.key.localeCompare(b.key))
    }
    out.push({ layer: L, label: LAYER_LABEL[L] || L, nodes })
  }
  order.forEach(pushGroup)
  for (const L of map.keys()) if (!order.includes(L)) pushGroup(L)
  return out
})

const aiClassify = computed(() => state.aiClassify || {})
const RISK_META = {
  HIGH: { dot: 'var(--bs-danger)', label: '高', cls: 'text-bg-danger' },
  MEDIUM: { dot: 'var(--bs-warning)', label: '中', cls: 'text-bg-warning' },
  LOW: { dot: 'var(--bs-success)', label: '低', cls: 'text-bg-success' }
}
function riskMeta(r) { return RISK_META[r] || null }
function riskRank(r) { return r === 'HIGH' ? 3 : r === 'MEDIUM' ? 2 : r === 'LOW' ? 1 : 0 }
function toggleRisk(r) {
  const cfg = state.config || {}
  if (!Array.isArray(cfg.filterRisk)) cfg.filterRisk = []
  const i = cfg.filterRisk.indexOf(r)
  if (i >= 0) cfg.filterRisk.splice(i, 1); else cfg.filterRisk.push(r)
}
function statusMeta(s) { return STATUS_META[s] || STATUS_META.UNCHANGED }

// ===================== 虚拟化滚动（D4） =====================
// 把 groups 拍平为「分组头 + 节点」的线性 rows，固定行高，仅渲染可视区切片。
const ROW_H = 30 // 每行固定像素高度（分组头与节点同高，保证对齐）
const rowStyle = { height: ROW_H + 'px', boxSizing: 'border-box' }

const rows = computed(() => {
  const out = []
  for (const g of groups.value) {
    out.push({ kind: 'header', layer: g.layer, label: g.label, count: g.nodes.length })
    for (const n of g.nodes) {
      out.push({ kind: 'node', node: n })
      // 归档展开：把已加载（或加载中）的内部条目插到父节点后面（虚拟化同数组渲染）
      if ((n.fileClass === 'ARCHIVE' || n.fileClass === 'JAR') && state.expandedArchives[n.key]) {
        const cache = state.archiveChildren[n.key]
        const kids = (cache && cache.children) || []
        if (cache && cache.loading && !kids.length) {
          out.push({ kind: 'archiveLoading', parentKey: n.key })
        } else {
          for (const c of kids) out.push({ kind: 'archiveChild', node: c, parentKey: n.key })
        }
      }
    }
  }
  return out
})
const totalH = computed(() => rows.value.length * ROW_H)

const scrollEl = ref(null)
const scrollTop = ref(0)
const viewportH = ref(600) // 初始估值，挂载后由容器实测覆盖
const OVERSCAN = 6
const start = computed(() => Math.max(0, Math.floor(scrollTop.value / ROW_H) - OVERSCAN))
const visibleCount = computed(() => Math.ceil(viewportH.value / ROW_H) + OVERSCAN * 2)
const slice = computed(() => rows.value.slice(start.value, start.value + visibleCount.value))

function onScroll(e) { scrollTop.value = e.target.scrollTop }
function measure() {
  if (scrollEl.value) viewportH.value = scrollEl.value.clientHeight || 600
}

let ro = null
onMounted(() => {
  nextTick(measure)
  if (typeof ResizeObserver !== 'undefined' && scrollEl.value) {
    ro = new ResizeObserver(() => measure())
    ro.observe(scrollEl.value)
  }
})
onBeforeUnmount(() => { if (ro) ro.disconnect() })

// 新比对 / 差异树变化：回到顶部
watch(tree, () => {
  scrollTop.value = 0
  if (scrollEl.value) scrollEl.value.scrollTop = 0
})
</script>

<template>
  <div class="col-tree">
    <div class="pane-head" title="按层级展示两个包/目录的差异文件，点击任一文件打开双栏源码比对">
      <i class="bi bi-diagram-3"></i> 差异文件树
      <span class="ms-auto fw-normal" style="font-size:.75rem;color:var(--bs-secondary-color)">
        {{ tree.length }} 项
      </span>
    </div>

    <!-- 搜索框 + 正则开关 -->
    <div class="dt-toolbar px-2 py-1" v-if="state.config">
      <div class="input-group input-group-sm">
        <span class="input-group-text"><i class="bi bi-search"></i></span>
        <input class="form-control" type="text" v-model="state.config.filterSearch"
               placeholder="搜索文件名（模糊）" aria-label="搜索文件名">
        <button class="btn btn-outline-secondary" type="button" :class="{active: state.config.filterRegex}"
                @click="state.config.filterRegex = !state.config.filterRegex"
                :title="state.config.filterRegex ? '正则模式：搜索框内容按正则表达式匹配' : '模糊模式：忽略大小写，包含即匹配'">
          <i class="bi bi-regex"></i>
        </button>
      </div>
      <div class="form-text mt-1" style="font-size:.72rem" v-if="state.config.filterRegex">
        正则模式：在上方输入合法正则，如 <code>^com/</code> 或 <code>\.java$</code>
      </div>
    </div>

    <!-- 状态过滤：可直接勾选显示/隐藏差异文件 -->
    <div class="dt-filters px-2 py-1 d-flex flex-wrap gap-2" v-if="state.config">
      <div class="form-check form-check-inline mb-0">
        <input class="form-check-input" type="checkbox" id="ftMod" v-model="state.config.filterShowModified" title="显示内容发生变化的文件">
        <label class="form-check-label" for="ftMod" title="显示内容发生变化的文件"><i class="bi bi-circle-fill me-1" style="font-size:.5rem;color:var(--bs-warning)"></i>修改 {{ counts.MODIFIED }}</label>
      </div>
      <div class="form-check form-check-inline mb-0">
        <input class="form-check-input" type="checkbox" id="ftAdd" v-model="state.config.filterShowAdded" title="显示新出现的文件">
        <label class="form-check-label" for="ftAdd" title="显示新出现的文件"><i class="bi bi-circle-fill me-1" style="font-size:.5rem;color:var(--bs-success)"></i>新增 {{ counts.ADDED }}</label>
      </div>
      <div class="form-check form-check-inline mb-0">
        <input class="form-check-input" type="checkbox" id="ftDel" v-model="state.config.filterShowDeleted" title="显示被移除的文件">
        <label class="form-check-label" for="ftDel" title="显示被移除的文件"><i class="bi bi-circle-fill me-1" style="font-size:.5rem;color:var(--bs-danger)"></i>删除 {{ counts.DELETED }}</label>
      </div>
      <div class="form-check form-check-inline mb-0">
        <input class="form-check-input" type="checkbox" id="ftUnc" v-model="state.config.filterShowUnchanged" title="显示内容一致的文件；关闭可显著缩短差异树长度">
        <label class="form-check-label" for="ftUnc" title="显示内容一致的文件；关闭可显著缩短差异树长度"><i class="bi bi-circle-fill me-1" style="font-size:.5rem;color:var(--bs-secondary)"></i>未变 {{ counts.UNCHANGED }}</label>
      </div>
    </div>

    <!-- 智能分类：风险排序与风险过滤（仅在做完智能分类后出现） -->
    <div class="dt-ai px-2 py-1 d-flex align-items-center gap-2 flex-wrap" v-if="Object.keys(aiClassify).length">
      <button class="btn btn-sm btn-outline-secondary py-0" :class="{active: state.config.sortByRisk}"
              @click="state.config.sortByRisk = !state.config.sortByRisk"
              title="按 AI 风险等级排序（高→低），优先审阅高风险变更">
        <i class="bi bi-sort-down"></i>
      </button>
      <div class="btn-group btn-group-sm" role="group" title="按风险等级过滤">
        <button v-for="r in ['HIGH','MEDIUM','LOW']" :key="r" type="button"
                class="btn" :class="(state.config.filterRisk||[]).includes(r) ? 'btn-secondary' : 'btn-outline-secondary'"
                @click="toggleRisk(r)">{{ riskMeta(r).label }}</button>
      </div>
      <span class="text-secondary" style="font-size:.72rem">已标注 {{ Object.keys(aiClassify).length }} 项</span>
    </div>

    <!-- 虚拟滚动容器：仅渲染可视行，数千节点也不卡顿 -->
    <div class="tree-scroll" ref="scrollEl" @scroll="onScroll">
      <div class="vt-root" :style="{ height: totalH + 'px' }">
        <div class="vt-window" :style="{ transform: 'translateY(' + (start * ROW_H) + 'px)' }">
          <template v-for="row in slice" :key="row.kind === 'header' ? ('h-' + row.layer) : (row.kind === 'archiveChild' ? row.node.key : row.node.key)">
            <div v-if="row.kind === 'header'" class="list-group-item grp-head py-0 px-2 d-flex align-items-center"
                 :style="rowStyle" :title="LAYER_TITLE[row.layer] || row.label">
              {{ row.label }}（{{ row.count }}）
            </div>
            <!-- 归档展开加载占位 -->
            <div v-else-if="row.kind === 'archiveLoading'" class="list-group-item tree-node d-flex align-items-center gap-2 py-0 px-2 text-secondary"
                 :style="rowStyle">
              <span class="caret-slot"></span>
              <span class="spinner-border spinner-border-sm" style="width:.6rem;height:.6rem"></span>
              <span class="node-key text-truncate flex-1" style="font-size:.78rem">正在展开内部条目…</span>
            </div>
            <button v-else type="button"
                    class="list-group-item list-group-item-action tree-node d-flex align-items-center gap-2 py-0 px-2"
                    :style="rowStyle"
                    :class="{active: state.activeKey === row.node.key, 'child-row': row.kind === 'archiveChild'}"
                    :title="row.node.key"
                    @click="selectEntry(row.node.key)">
              <!-- 归档节点：左侧 caret 折叠/展开（点击不触发打开）；非归档：占位保持对齐 -->
              <span class="caret-slot">
                <i v-if="row.node.fileClass === 'ARCHIVE' || row.node.fileClass === 'JAR'"
                   class="bi tree-caret"
                   :class="state.expandedArchives[row.node.key] ? 'bi-chevron-down' : 'bi-chevron-right'"
                   @click.stop="toggleArchive(row.node.key)"
                   :title="state.expandedArchives[row.node.key] ? '折叠内部条目' : '展开内部条目'"></i>
              </span>
              <i class="bi bi-circle-fill" style="font-size:.5rem" :style="{color: statusMeta(row.node.status).dot}"></i>
              <span class="node-key text-truncate flex-1" :class="{'child-name': row.kind === 'archiveChild'}">{{ row.kind === 'archiveChild' ? row.node.name : row.node.key }}</span>
              <span class="badge text-bg-light badge-fc border" :title="'文件类型：' + (row.node.fileClass || '未知')">{{ row.node.fileClass }}</span>
              <template v-if="aiClassify[row.node.key] && row.kind !== 'archiveChild'">
                <span class="badge" :class="riskMeta(aiClassify[row.node.key].risk).cls"
                      :title="'AI 风险等级：' + riskMeta(aiClassify[row.node.key].risk).label">{{ riskMeta(aiClassify[row.node.key].risk).label }}</span>
                <span class="badge text-bg-light badge-fc border"
                      :title="'AI 自动分类：' + aiClassify[row.node.key].category + '；' + (aiClassify[row.node.key].reason || '')">{{ aiClassify[row.node.key].category }}</span>
              </template>
            </button>
          </template>
        </div>
      </div>
      <div v-if="!tree.length" class="text-center text-secondary py-4" style="font-size:.8rem">
        暂无差异，请先「开始比对」
      </div>
    </div>
  </div>
</template>

<style scoped>
.dt-toolbar { border-bottom: 1px solid var(--bs-border-color); }
.dt-filters { border-bottom: 1px solid var(--bs-border-color); }
.dt-filters .form-check-label { font-size: .8rem; color: var(--bs-body-color); }
.dt-ai { border-bottom: 1px solid var(--bs-border-color); background: var(--bs-tertiary-bg); }
.dt-ai .btn-group-sm .btn { font-size: .72rem; padding: .1rem .5rem; }
/* 虚拟滚动：滚动容器已 overflow:auto（main.css）；内部绝对定位窗口按 translateY 偏移可视切片 */
.vt-root { position: relative; width: 100%; }
.vt-window { position: absolute; top: 0; left: 0; right: 0; }
.tree-node { overflow: hidden; }
/* 归档展开 caret：与状态点同宽占位，保证子节点与父节点对齐 */
.caret-slot { display: inline-flex; width: .9rem; flex: 0 0 auto; justify-content: center; }
.tree-caret { font-size: .7rem; color: var(--bs-secondary-color); cursor: pointer; }
.tree-caret:hover { color: var(--bs-body-color); }
/* 内部条目子节点：缩进 + 轻微底色，提示其归属父归档 */
.child-row { padding-left: .9rem !important; background: var(--bs-tertiary-bg); }
.child-row.child-name { font-size: .82rem; }
</style>
