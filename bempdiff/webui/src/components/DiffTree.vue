<script setup>
import { computed } from 'vue'
import { state, selectEntry, STATUS_META } from '../store'

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
  const pushGroup = (L) => {
    if (!map.has(L)) return
    out.push({ layer: L, label: LAYER_LABEL[L] || L, nodes: map.get(L).sort((a, b) => a.key.localeCompare(b.key)) })
  }
  order.forEach(pushGroup)
  for (const L of map.keys()) if (!order.includes(L)) pushGroup(L)
  return out
})

function statusMeta(s) { return STATUS_META[s] || STATUS_META.UNCHANGED }
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

    <div class="tree-scroll list-group list-group-flush">
      <template v-for="g in groups" :key="g.layer">
        <div class="list-group-item grp-head py-1 px-2" :title="LAYER_TITLE[g.layer] || g.label">{{ g.label }}（{{ g.nodes.length }}）</div>
        <button v-for="n in g.nodes" :key="n.key" type="button"
                class="list-group-item list-group-item-action tree-node d-flex align-items-center gap-2 py-1 px-2"
                :class="{active: state.activeKey === n.key}"
                :title="n.key"
                @click="selectEntry(n.key)">
          <i class="bi bi-circle-fill" style="font-size:.5rem" :style="{color: statusMeta(n.status).dot}"></i>
          <span class="node-key text-truncate flex-1">{{ n.key }}</span>
          <span class="badge text-bg-light badge-fc border" :title="'文件类型：' + (n.fileClass || '未知')">{{ n.fileClass }}</span>
        </button>
      </template>
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
</style>
