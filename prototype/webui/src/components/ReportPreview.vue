<script setup>
import { computed } from 'vue'
import { state, generateReport } from '../store'
import { renderMarkdown } from '../lib/markdown'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

const html = computed(() => state.reportMd ? renderMarkdown(state.reportMd) : '')

function downloadMd() {
  if (!state.reportMd) return
  const blob = new Blob([state.reportMd], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `bempdiff-report-${state.job ? state.job.jobId : 'job'}.md`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="modal-backdrop" v-if="visible" @click.self="emit('close')">
    <div class="modal-dialog modal-xl modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header py-2">
          <h6 class="modal-title mb-0"><i class="bi bi-filetype-md"></i> 差异分析报告
            <small class="fw-normal text-secondary ms-2" style="font-size:.75rem">
              {{ state.reportAi ? '含 AI 智能分析' : '基础报告' }}
            </small>
          </h6>
          <button type="button" class="btn-close" @click="emit('close')"></button>
        </div>

        <div class="modal-body report-body">
          <div v-if="state.reportMd" class="md-render" v-html="html"></div>
          <div v-else class="text-center text-secondary py-5">
            <div class="ico mb-2"><i class="bi bi-file-earmark-x"></i></div>
            <div>尚未生成报告。</div>
            <button class="btn btn-sm btn-outline-primary mt-3" :disabled="!state.job || state.busy"
                    @click="generateReport(state.config && state.config.aiEnabled)">
              <i class="bi bi-filetype-md"></i> 生成报告{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
            </button>
          </div>
        </div>

        <div class="modal-footer py-2">
          <button class="btn btn-outline-secondary btn-sm" :disabled="!state.reportMd" @click="downloadMd">
            <i class="bi bi-download"></i> 下载 .md
          </button>
          <button class="btn btn-outline-primary btn-sm" :disabled="!state.job || state.busy"
                  @click="generateReport(state.config && state.config.aiEnabled)">
            <i class="bi bi-arrow-clockwise"></i> 重新生成{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
          </button>
          <button class="btn btn-secondary btn-sm" @click="emit('close')">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,.4);
  display: flex; align-items: flex-start; justify-content: center; z-index: 1600; padding-top: 4vh;
}
.modal-content { width: 100%; max-height: 92vh; }
.report-body { overflow: auto; }
.report-body .ico { font-size: 2rem; }
</style>
