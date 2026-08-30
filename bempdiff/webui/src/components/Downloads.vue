<script setup>
import { ref, computed } from 'vue'
import { state, toggleExports, refreshExports, deleteExport, removeSyncExport, fmtBytes } from '../store'

const query = ref('')
const range = ref('all') // all / today /7d

const STATUS_LABEL = { queued: '排队', running: '生成中', done: '完成', error: '失败' }

const asyncList = computed(() => {
  const q = query.value.trim().toLowerCase()
  const now = Date.now()
  let dayMs = range.value === 'today' ? 86400000 : range.value === '7d' ? 7 * 86400000 : Number.MAX_SAFE_INTEGER
  return state.exportRecords
    .filter(r => (q === '' || (r.filename || '').toLowerCase().includes(q) || (r.id || '').toLowerCase().includes(q)))
    .filter(r => !dayMs || (now - (r.createdAt || 0)) <= dayMs)
})

// 同步导出资产：本机浏览器下载目录的同步导出记录（客户端持久化，最新在前）
const syncList = computed(() => state.syncExports || [])

function statusChip(r) {
  if (r.status === 'done') return 'badge text-bg-success'
  if (r.status === 'error') return 'badge text-bg-danger'
  if (r.status === 'running') return 'badge text-bg-info'
  return 'badge text-bg-secondary'
}
function fmtTime(ms) {
  if (!ms) return '—'
  const d = new Date(ms)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
</script>

<template>
  <div class="dl-panel shadow" role="dialog" aria-label="下载管理">
    <div class="dl-head">
      <span class="fw-semibold"><i class="bi bi-download me-1"></i>下载管理</span>
      <span class="text-secondary" style="font-size:.72rem">导出记录</span>
      <div class="ms-auto d-flex gap-1">
        <button class="btn btn-sm btn-outline-secondary" title="刷新列表" @click="refreshExports()"><i class="bi bi-arrow-clockwise"></i></button>
        <button class="btn btn-sm btn-outline-secondary" title="关闭" @click="toggleExports()"><i class="bi bi-x-lg"></i></button>
      </div>
    </div>

    <div class="dl-hint text-secondary">
      同步导出资产保存在浏览器下载目录；异步导出资产由后端生成，完成后在此点击「下载」。查看记录，按时间回溯。
    </div>

    <!-- 同步导出资产（本机浏览器下载目录，客户端回溯） -->
    <div class="dl-section-title"><i class="bi bi-lightning-charge"></i> 同步导出资产
      <span class="text-secondary" style="font-size:.66rem">共 {{ syncList.length }} 项</span>
    </div>
    <div class="dl-list dl-list-sync">
      <template v-if="syncList.length">
        <div v-for="s in syncList" :key="s.createdAt" class="dl-row">
          <div class="dl-main">
            <span class="dl-name" :title="s.filename">{{ s.filename }}</span>
            <span class="text-secondary dl-sub">同步导出 · {{ fmtTime(s.createdAt) }} · 已保存到浏览器下载目录</span>
          </div>
          <span class="text-secondary dl-size">{{ fmtBytes(s.size) }}</span>
          <div class="dl-actions">
            <button class="btn btn-sm btn-outline-secondary" title="从本机记录中移除（不影响已下载文件）" @click="removeSyncExport(s.createdAt)">移除</button>
          </div>
        </div>
      </template>
      <div v-else class="dl-empty">暂无同步导出记录（小包直接下载后此处出现）</div>
    </div>

    <!-- 异步导出资产（后端生成记录，可下载/删除） -->
    <div class="dl-section-title"><i class="bi bi-clock-history"></i> 异步导出资产
      <span class="text-secondary" style="font-size:.66rem">共 {{ asyncList.length }} 项</span>
    </div>
    <div class="dl-toolbar d-flex gap-2">
      <input class="form-control form-control-sm" v-model.trim="query" placeholder="筛选文件名或任务ID">
      <select class="form-select form-select-sm" style="width:auto" v-model="range">
        <option value="all">全部时间</option>
        <option value="today">今天</option>
        <option value="7d">近 7 天</option>
      </select>
    </div>
    <div class="dl-list dl-list-async">
      <template v-if="asyncList.length">
        <div v-for="r in asyncList" :key="r.id" class="dl-row">
          <div class="dl-main">
            <span class="dl-name" :title="r.filename">{{ r.filename }}</span>
            <span class="text-secondary dl-sub" :title="r.id">任务 {{ r.id }} · {{ fmtTime(r.createdAt) }}</span>
          </div>
          <span :class="statusChip(r)">{{ STATUS_LABEL[r.status] || r.status }}</span>
          <span class="text-secondary dl-size">{{ r.status === 'done' ? fmtBytes(r.size) : fmtBytes(r.estimatedBytes) }}</span>
          <span v-if="r.status === 'error'" class="text-danger dl-msg" :title="r.message">{{ r.message }}</span>
          <span v-else-if="r.status === 'running' && r.etaText" class="text-secondary dl-msg">{{ r.etaText }}</span>
          <div class="dl-actions">
            <a v-if="r.status === 'done'" class="btn btn-sm btn-primary"
               :href="`/api/export/${encodeURIComponent(r.id)}/download`" :download="r.filename">下载</a>
            <button class="btn btn-sm btn-outline-danger" title="删除此导出记录与文件" @click="deleteExport(r.id)">删除</button>
          </div>
        </div>
      </template>
      <div v-else class="dl-empty">暂无异步导出记录（大包导出完成后此处出现）。</div>
    </div>
  </div>
</template>

<style scoped>
.dl-panel {
  position: fixed;
  right: 1rem;
  top: 3.4rem;
  width: 30rem;
  max-width: calc(100vw - 2rem);
  z-index: 1060;
  background: var(--bs-body-bg);
  border: 1px solid var(--bs-border-color);
  border-radius: .5rem;
  padding: .6rem .75rem;
  display: flex;
  flex-direction: column;
  gap: .4rem;
}
.dl-head { display: flex; align-items: center; gap: .5rem; }
/* 同步/异步资产分区标题：统一图标 + 命名，层级清晰；上下留白与主体分隔 */
.dl-section-title {
  display: flex; align-items: center; gap: .35rem;
  font-size: .74rem; font-weight: 600; color: var(--bs-secondary-color);
  border-top: 1px solid var(--bs-border-color);
  padding: .35rem 0 .15rem;
  margin-top: .25rem;
}
.dl-section-title:first-of-type { border-top: none; }
.dl-hint { font-size: .72rem; }
.dl-toolbar input { min-width: 0; }
.dl-list { max-height: 20rem; overflow-y: auto; display: flex; flex-direction: column; gap: .3rem; }
.dl-row {
  display: flex; align-items: center; gap: .5rem;
  padding: .3rem .4rem; border: 1px solid var(--bs-border-color); border-radius: .4rem;
}
.dl-main { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; }
.dl-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dl-sub, .dl-size, .dl-msg { font-size: .7rem; }
.dl-msg { max-width: 7rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dl-actions { display: flex; gap: .3rem; }
.dl-empty { text-align: center; padding: 1rem; font-size: .78rem; }
</style>