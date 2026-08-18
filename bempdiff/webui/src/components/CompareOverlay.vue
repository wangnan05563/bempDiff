<script setup>
import { computed } from 'vue'
import { state, cancelCompare } from '../store'

// 仅「确实在比对」才走确定进度条 + 取消；报告生成期间 state.reporting=true 应始终走 v-else 不确定条，
// 故显式排除报告态，避免未来 jobProgress 残留导致遮罩误显示为「取消比对」（评审 C）。
// 其它 busy（如手动/自动生成报告）无确定进度数据（后端 api.report 为一次性阻塞请求，不流式上报），
// 渲染不确定动画进度条，满足「遮罩层 + 进度条提示正在生成中」；不伪造百分比。
const comparing = computed(() =>
  !!state.jobProgress && !state.reporting &&
  (state.jobProgress.status === 'QUEUED' || state.jobProgress.status === 'RUNNING'))
const pct = computed(() => {
  const p = state.jobProgress ? (state.jobProgress.progress || 0) : 0
  return Math.max(0, Math.min(100, p))
})
</script>

<template>
  <div class="busy-overlay" v-if="state.busy">
    <div class="busy-card shadow">
      <div class="spinner-border text-primary" role="status"></div>
      <div>{{ state.busyText || '处理中…' }}</div>

      <div v-if="comparing" class="w-100 mt-2">
        <div class="d-flex justify-content-between mb-1" style="font-size:.75rem;color:var(--bs-secondary-color)">
          <span>{{ state.jobProgress.message || '处理中…' }}</span>
          <span>{{ pct }}%</span>
        </div>
        <div class="progress" style="height:.5rem">
          <div class="progress-bar progress-bar-striped progress-bar-animated"
               role="progressbar" :style="{ width: pct + '%' }"></div>
        </div>
        <button class="btn btn-sm btn-outline-secondary mt-3" @click="cancelCompare">
          <i class="bi bi-x-circle"></i> 取消比对
        </button>
      </div>

      <!-- 其它 busy（手动 / 自动生成报告等）：无确定进度，渲染不确定动画进度条，
           满足「遮罩层 + 进度条提示正在生成中」；不伪造百分比（后端 api.report 为一次性阻塞请求，无流式进度）。 -->
      <div v-else class="w-100 mt-3">
        <div class="progress" style="height:.5rem">
          <div class="progress-bar progress-bar-striped progress-bar-animated w-100"
               role="progressbar" aria-label="报告生成中"></div>
        </div>
      </div>
    </div>
  </div>
</template>
