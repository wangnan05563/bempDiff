<script setup>
import { computed } from 'vue'
import { state, isRunning, phaseLabel, cancelExport } from '../store'

function basename(p) {
  if (!p) return ''
  return p.replace(/\\/g, '/').split('/').pop() || p
}

const s = computed(() => (state.job && state.job.stats) || null)
// 全量统计（含 bizChanged/jarChanged）；差异数字统一以后端全量 stats 为准
const fullStats = computed(() => state.job && state.job.stats ? state.job.stats : null)
const versions = computed(() => {
  if (state.job) return `${state.job.oldVersion} → ${state.job.newVersion}`
  const o = basename(state.oldPath)
  const n = basename(state.newPath)
  if (o || n) return `${o || '—'} → ${n || '—'}`
  return '—'
})
</script>

<template>
  <footer class="bg-body-tertiary border-top px-3 py-1 d-flex gap-4 flex-wrap" style="font-size:.75rem;color:var(--bs-secondary-color)">
    <!-- 比对进行中：覆盖解析/解包/比对各阶段，实时展示进度与归属文案。
        与后端拒绝消息「比对尚未完成: RUNNING」逻辑呼应——此阶段触发智能分类/AI分析会得到该提示 -->
    <span v-if="isRunning()" class="text-primary fw-semibold d-flex align-items-center gap-2" style="flex-basis:100%">
      <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
      {{ phaseLabel() }}
      <template v-if="state.jobProgress && (state.jobProgress.message || state.jobProgress.progress)">
        <span class="text-muted fw-normal">{{ state.jobProgress.message }}</span>
        <span class="text-muted fw-normal" v-if="state.jobProgress.progress != null">{{ state.jobProgress.progress }}%</span>
      </template>
      <span class="text-danger fw-normal">比对完成前，智能分类 / AI 分析需等待完成后再进行（此刻触发会提示「比对尚未完成: RUNNING」）</span>
    </span>
    <!-- 导出进行中：从点击「导出差异资产」直至本地保存完成前，底部固定提示「导出中」（可取消，恢复按钮） -->
    <span v-if="state.exporting" class="text-primary fw-semibold" title="正在导出差异资产，请稍候">
      <i class="bi bi-arrow-repeat" style="animation:spinner-border .75s linear infinite"></i> 导出中
      <button type="button" class="btn btn-link btn-sm p-0 ms-1 align-baseline" @click="cancelExport()" title="取消导出">取消</button>
    </span>
    <!-- 比对超时提醒：RUNNING 超过阈值仍未结束 -->
    <span v-if="state.compareStalled" class="text-danger fw-semibold">
      <i class="bi bi-exclamation-triangle"></i> 比对已进行超过 5 分钟仍未完成。可继续等待；若长时间无进展，请检查磁盘/网络后重启工具。
    </span>
    <span v-if="s" title="当前生效统计：随归档逐层解包展开数字逐档增长（解包完成后即等于最终差异数量）">
      统计：新增 <b class="text-body">{{ s.added }}</b> · 删除 <b class="text-body">{{ s.deleted }}</b> ·
      修改 <b class="text-body">{{ s.modified }}</b> · 未变 <b class="text-body">{{ s.unchanged }}</b>
    </span>
    <span v-if="fullStats" title="业务码变更：涉及 L1 内部业务类的变更数量；jar 级变更：整个 jar 包级别发生变更的数量">业务码变更 <b class="text-body">{{ fullStats.bizChanged }}</b> · jar 级变更 <b class="text-body">{{ fullStats.jarChanged }}</b></span>
    <span title="老版本 → 新版本；未比对时显示填入的两个包/目录名">版本：<b class="text-body">{{ versions }}</b></span>
    <span v-if="state.analyzing" class="text-primary"><i class="bi bi-cpu"></i> AI 报告中…</span>
    <span class="ms-auto" title="反编译引擎：CFR；分析方式：AI 模型 或 纯本地规则（取决于是否启用 AI 分析）">反编译引擎 <b class="text-body">CFR</b> · 分析 <b class="text-body">{{ state.config && state.config.aiEnabled ? 'AI' : '本地' }}</b></span>
  </footer>
</template>
