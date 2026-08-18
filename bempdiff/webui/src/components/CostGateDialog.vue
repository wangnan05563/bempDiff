<script setup>
import { computed } from 'vue'
import { state, confirmCostGate, cancelCostGate } from '../store'

// 各 AI 入口的中文标签（与 store.ensureAiBudget 的 action 对齐）
const ACTION_LABEL = {
  report: 'AI 报告',
  analyze: 'AI 流式分析',
  classify: '智能分类'
}

const visible = computed(() => !!state.costGate)
const action = computed(() => (state.costGate ? state.costGate.action : ''))
const estimate = computed(() => (state.costGate ? Math.round(state.costGate.estimate) : 0))
const threshold = computed(() => (state.costGate ? Math.round(state.costGate.threshold) : 0))
// token ≈ 字符数 / 2 → 反推字符数，给审计人员一个更直观的体量感知
const approxChars = computed(() => estimate.value * 2)
const label = computed(() => ACTION_LABEL[action.value] || action.value)
// 超阈值倍数（用于提示严重度）
const ratio = computed(() => (threshold.value > 0 ? (estimate.value / threshold.value) : 1))
const severe = computed(() => ratio.value >= 3)
</script>

<template>
  <div class="modal-backdrop" v-if="visible" @click.self="cancelCostGate">
    <div class="modal-dialog modal-sm modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header py-2 px-3">
          <h6 class="modal-title mb-0">
            <i class="bi bi-shield-exclamation text-warning"></i> AI 成本预估确认
          </h6>
        </div>

        <div class="modal-body py-3 px-3">
          <p class="mb-2" style="font-size:.88rem">
            本次「<strong>{{ label }}</strong>」预估消耗约
            <strong class="text-warning">{{ estimate }}</strong> tokens
            （成本阈值 {{ threshold }} tokens）。
          </p>
          <p class="mb-2 small text-secondary">
            约 {{ approxChars.toLocaleString() }} 字符的提示词将发往 AI 模型，可能产生相应费用。
          </p>
          <div class="alert py-2 mb-0" :class="severe ? 'alert-danger' : 'alert-warning'"
               style="font-size:.8rem">
            <i class="bi bi-exclamation-triangle"></i>
            已超成本阈值 {{ severe ? '数倍' : '' }}，确认发起调用？
            <span class="d-block mt-1 text-secondary">金融合规提示：请确认本次分析确属必要，避免非预期的大额 AI 开销。</span>
          </div>
        </div>

        <div class="modal-footer py-2 px-3">
          <button class="btn btn-outline-secondary btn-sm" @click="cancelCostGate">
            <i class="bi bi-x-circle"></i> 取消
          </button>
          <button class="btn btn-primary btn-sm" @click="confirmCostGate">
            <i class="bi bi-check-circle"></i> 确认发起
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4000; /* 高于 AI 分析弹窗（其 backdrop 为默认层），形成模态叠加 */
}
.modal-content {
  background-color: var(--bs-body-bg);
  color: var(--bs-body-color);
}
</style>
