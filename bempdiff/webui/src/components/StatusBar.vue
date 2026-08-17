<script setup>
import { computed } from 'vue'
import { state } from '../store'

function basename(p) {
  if (!p) return ''
  return p.replace(/\\/g, '/').split('/').pop() || p
}

const s = computed(() => state.job && state.job.stats ? state.job.stats : null)
const versions = computed(() => {
  if (state.job) return `${state.job.oldVersion} → ${state.job.newVersion}`
  const o = basename(state.compareInputs.oldPath)
  const n = basename(state.compareInputs.newPath)
  if (o || n) return `${o || '—'} → ${n || '—'}`
  return '—'
})
</script>

<template>
  <footer class="bg-body-tertiary border-top px-3 py-1 d-flex gap-4 flex-wrap" style="font-size:.75rem;color:var(--bs-secondary-color)">
    <span v-if="s" title="本次比对的文件变更统计：新增 / 删除 / 修改 / 未变的文件数">
      统计：新增 <b class="text-body">{{ s.added }}</b> · 删除 <b class="text-body">{{ s.deleted }}</b> ·
      修改 <b class="text-body">{{ s.modified }}</b> · 未变 <b class="text-body">{{ s.unchanged }}</b>
    </span>
    <span v-if="s" title="业务码变更：涉及 L1 内部业务类的变更数量；jar 级变更：整个 jar 包级别发生变更的数量">业务码变更 <b class="text-body">{{ s.bizChanged }}</b> · jar 级变更 <b class="text-body">{{ s.jarChanged }}</b></span>
    <span title="老版本 → 新版本；未比对时显示填入的两个包/目录名">版本：<b class="text-body">{{ versions }}</b></span>
    <span v-if="state.analyzing" class="text-primary"><i class="bi bi-cpu"></i> AI 报告中…</span>
    <span class="ms-auto" title="反编译引擎：CFR；分析方式：AI 模型 或 纯本地规则（取决于是否启用 AI 分析）">反编译引擎 <b class="text-body">CFR</b> · 分析 <b class="text-body">{{ state.config && state.config.aiEnabled ? 'AI' : '本地' }}</b></span>
  </footer>
</template>
