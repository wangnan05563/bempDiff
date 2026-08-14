<script setup>
import { computed } from 'vue'
import { state, selectEntry } from '../store'

const STATUS_META = {
  ADDED: { cls: 'text-bg-success', label: '新增', dot: 'var(--bs-success)' },
  DELETED: { cls: 'text-bg-danger', label: '删除', dot: 'var(--bs-danger)' },
  MODIFIED: { cls: 'text-bg-warning', label: '修改', dot: 'var(--bs-warning)' },
  UNCHANGED: { cls: 'text-bg-secondary', label: '未变', dot: 'var(--bs-secondary)' }
}
const LAYER_LABEL = { L0: 'L0 包级', L1: 'L1 业务码', L2: 'L2 三方依赖', '?': '其他' }

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
  // 状态过滤（按钮主过滤）
  if (state.filter !== 'all') list = list.filter(n => n.status === state.filter)
  // 未变项全局开关（差异树默认很长，可一键折叠）
  if (cfg.filterShowUnchanged === false) list = list.filter(n => n.status !== 'UNCHANGED')
  // 关键字搜索（支持正则）
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
    <div class="pane-head">
      <i class="bi bi-diagram-3"></i> 差异文件树
      <span class="ms-auto fw-normal" style="font-size:.75rem;color:var(--bs-secondary-color)">
        {{ tree.length }} 项
      </span>
    </div>

    <div class="legend">
      <span><i style="background:var(--bs-success)"></i>新增</span>
      <span><i style="background:var(--bs-danger)"></i>删除</span>
      <span><i style="background:var(--bs-warning)"></i>修改</span>
      <span><i style="background:var(--bs-secondary)"></i>未变</span>
    </div>

    <div class="btn-group btn-group-sm w-100 rounded-0" role="group">
      <button class="btn btn-outline-secondary" :class="{active: state.filter==='all'}" @click="state.filter='all'">全部</button>
      <button class="btn btn-outline-secondary" :class="{active: state.filter==='ADDED'}" @click="state.filter='ADDED'">新增 {{ counts.ADDED }}</button>
      <button class="btn btn-outline-secondary" :class="{active: state.filter==='DELETED'}" @click="state.filter='DELETED'">删除 {{ counts.DELETED }}</button>
      <button class="btn btn-outline-secondary" :class="{active: state.filter==='MODIFIED'}" @click="state.filter='MODIFIED'">修改 {{ counts.MODIFIED }}</button>
    </div>

    <div class="tree-scroll list-group list-group-flush">
      <template v-for="g in groups" :key="g.layer">
        <div class="list-group-item grp-head py-1 px-2">{{ g.label }}（{{ g.nodes.length }}）</div>
        <button v-for="n in g.nodes" :key="n.key" type="button"
                class="list-group-item list-group-item-action tree-node d-flex align-items-center gap-2 py-1 px-2"
                :class="{active: state.selectedKey === n.key}"
                @click="selectEntry(n.key)">
          <i class="bi bi-circle-fill" style="font-size:.5rem" :style="{color: statusMeta(n.status).dot}"></i>
          <span class="node-key text-truncate flex-1">{{ n.key }}</span>
          <span class="badge text-bg-light badge-fc border">{{ n.fileClass }}</span>
        </button>
      </template>
      <div v-if="!tree.length" class="text-center text-secondary py-4" style="font-size:.8rem">
        暂无差异，请先「开始比对」
      </div>
    </div>
  </div>
</template>
