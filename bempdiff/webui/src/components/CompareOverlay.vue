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

// R3 进度三段化（二期 T01457）：后端 Job.phase 已有标记序列
// queued → parsing → unpacking → diffing → building → done（BempServer.markRunning 上报）。
// 三段映射（诚实口径）：反编译不在比对主流程（打开文件时按需进行，无整体百分比），
// 故第三段为「出树」（building），不伪造「反编译」段。
const STEPS = [
  { label: '解包', phases: ['parsing', 'unpacking'] },
  { label: '比对', phases: ['diffing'] },
  { label: '出树', phases: ['building'] }
]
// 当前段下标：phase 缺失（旧后端/其它来源）返回 -1，模板退化为单条进度条（向后兼容）。
const phaseIdx = computed(() => {
  const ph = state.jobProgress && state.jobProgress.phase
  if (!ph) return -1
  return STEPS.findIndex(s => s.phases.includes(ph))
})
</script>

<template>
  <div class="busy-overlay" v-if="state.busy">
    <div class="busy-card shadow">
      <div class="spinner-border text-primary" role="status"></div>
      <div>{{ state.busyText || '处理中…' }}</div>

      <div v-if="comparing" class="w-100 mt-2">
        <!-- R3 三段进度指示：解包 → 比对 → 出树（当前段高亮，已完成段打勾） -->
        <div class="cmp-steps d-flex align-items-center justify-content-center gap-1 mb-2"
             style="font-size:.72rem" v-if="phaseIdx >= 0"
             :aria-label="'比对进度：' + STEPS.map(s => s.label).join(' → ')">
          <template v-for="(s, i) in STEPS" :key="s.label">
            <span class="cmp-step d-inline-flex align-items-center gap-1"
                  :class="{ 'cmp-step-active': i === phaseIdx, 'cmp-step-done': i < phaseIdx }">
              <i class="bi" :class="i < phaseIdx ? 'bi-check2-circle' : (i === phaseIdx ? 'bi-arrow-right-circle-fill' : 'bi-circle')" style="font-size:.65rem"></i>
              {{ s.label }}
            </span>
            <i v-if="i < STEPS.length - 1" class="bi bi-chevron-right" style="font-size:.6rem;color:var(--bs-secondary-color)"></i>
          </template>
        </div>
        <div class="d-flex justify-content-between mb-1" style="font-size:.75rem;color:var(--bs-secondary-color)">
          <span>{{ state.jobProgress.message || '处理中…' }}</span>
          <span>{{ pct }}%</span>
        </div>
        <div class="progress" style="height:.5rem">
          <div class="progress-bar progress-bar-striped progress-bar-animated" aria-label="比对总进度"
               role="progressbar" :aria-valuenow="pct" aria-valuemin="0" aria-valuemax="100"
               :style="{ width: pct + '%' }"></div>
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

<style scoped>
/* R3 三段步骤指示（T01457）：默认弱化、当前段主色加粗、已完成段成功色 */
.cmp-step { color: var(--bs-secondary-color); font-variant-numeric: tabular-nums; }
.cmp-step-active { color: var(--bs-primary); font-weight: 600; }
.cmp-step-done { color: var(--bs-success); }
</style>
