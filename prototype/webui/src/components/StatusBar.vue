<script setup>
import { computed } from 'vue'
import { state } from '../store'

const s = computed(() => state.job && state.job.stats ? state.job.stats : null)
const versions = computed(() => state.job
  ? `${state.job.oldVersion} → ${state.job.newVersion}`
  : '—')
</script>

<template>
  <footer class="bg-body-tertiary border-top px-3 py-1 d-flex gap-4 flex-wrap" style="font-size:.75rem;color:var(--bs-secondary-color)">
    <span v-if="s">
      统计：新增 <b class="text-body">{{ s.added }}</b> · 删除 <b class="text-body">{{ s.deleted }}</b> ·
      修改 <b class="text-body">{{ s.modified }}</b> · 未变 <b class="text-body">{{ s.unchanged }}</b>
    </span>
    <span v-if="s">业务码变更 <b class="text-body">{{ s.bizChanged }}</b> · jar 级变更 <b class="text-body">{{ s.jarChanged }}</b></span>
    <span>版本：<b class="text-body">{{ versions }}</b></span>
    <span v-if="state.analyzing" class="text-primary"><i class="bi bi-cpu"></i> AI 报告中…</span>
    <span class="ms-auto">反编译引擎 <b class="text-body">CFR</b> · 分析 <b class="text-body">{{ state.config && state.config.aiEnabled ? 'AI' : '本地' }}</b></span>
  </footer>
</template>
