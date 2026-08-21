<script setup>
import { computed } from 'vue'
import { state, generateReport } from '../store'
import { renderMarkdown } from '../lib/markdown'
import { colorizeReport, sevClassFromText } from '../lib/severity'

const props = defineProps({ visible: { type: Boolean, default: false }, md: { type: String, default: null } })
const emit = defineEmits(['close'])

// 报告正文来源：优先用调用方传入的 md（AI 控制台预览某次任务报告），否则用全局 state.reportMd。
// 用 null 判断而非 `||`：避免传入空字符串时错误回退到全局报告（评审 P2 #14）
const src = computed(() => props.md != null ? props.md : state.reportMd)
// 报告预览标题：来自任务预览时标注「AI 分析」，全局时沿用原逻辑
const srcIsTask = computed(() => props.md != null)

// 报告正文：渲染后做 AI 风险严重性着色（sev-* class）
const html = computed(() => src.value ? colorizeReport(renderMarkdown(src.value)) : '')

// 顶部「总体风险结论」汇总条：从报告中抽取 `整体风险：**高**`
const overallRisk = computed(() => {
  const m = src.value && src.value.match(/整体风险[：:]\s*\*\*?([^\n*]+?)\*\*?/)
  return m ? m[1].trim() : null
})
const riskCls = computed(() => overallRisk.value ? sevClassFromText(overallRisk.value) : null)

function downloadMd() {
  if (!src.value) return
  const blob = new Blob([src.value], { type: 'text/markdown;charset=utf-8' })
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
    <div class="modal-dialog modal-xl modal-dialog-scrollable report-dialog">
      <div class="modal-content">
        <div class="modal-header py-2">
          <h6 class="modal-title mb-0"><i class="bi bi-filetype-md"></i> 差异分析报告
            <small class="fw-normal text-secondary ms-2" style="font-size:.75rem">
              {{ srcIsTask ? 'AI 分析' : (state.reportAi ? '含 AI 智能分析' : '基础报告') }}
            </small>
          </h6>
          <button type="button" class="btn-close" @click="emit('close')"></button>
        </div>

        <div class="modal-body report-body">
          <div v-if="overallRisk" class="risk-banner mb-3" :class="'rb-' + (riskCls || 'none')">
            <i class="bi bi-shield-exclamation"></i>
            <span>总体风险结论：<b :class="riskCls">{{ overallRisk }}</b></span>
          </div>
          <div v-if="src" class="md-render" v-html="html"></div>
          <div v-else class="empty-report text-center text-secondary py-5">
            <div class="ico mb-2"><i class="bi bi-file-earmark-x"></i></div>
            <div>尚未生成报告。</div>
            <button v-if="!srcIsTask" class="btn btn-sm btn-outline-primary mt-3" :disabled="!state.job || state.busy"
                    @click="generateReport(state.config && state.config.aiEnabled)">
              <i class="bi bi-filetype-md"></i> 生成报告{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
            </button>
          </div>
        </div>

        <div class="modal-footer py-2 px-3">
          <button class="btn btn-outline-secondary btn-sm" :disabled="!src" @click="downloadMd">
            <i class="bi bi-download"></i> 下载 .md
          </button>
          <button v-if="!srcIsTask" class="btn btn-outline-primary btn-sm" :disabled="!state.job || state.busy"
                  @click="generateReport(state.reportAi, { category: state.reportAi ? state.reportCategory : undefined, force: true })">
            <i class="bi bi-arrow-clockwise"></i> 重新生成{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
          </button>
          <button class="btn btn-secondary btn-sm ms-auto" @click="emit('close')">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,.4);
  display: flex; align-items: flex-start; justify-content: center; z-index: 1600;
  padding: 3vh 2vw;
}
.report-dialog { width: min(1200px, 96vw); max-width: none; margin: 0; }
.modal-content {
  width: 100%; max-height: 94vh;
  display: flex; flex-direction: column;
  background-color: var(--bs-body-bg); color: var(--bs-body-color);
}
.report-body { flex: 1 1 auto; min-height: 0; overflow: auto; padding: 1.25rem 1.5rem; }
.report-body .ico { font-size: 2rem; }

/* 差异报告分析框：清晰边框容器 + 充足内边距，文字/表格不与边框贴边 */
.md-render {
  border: 1px solid var(--bs-border-color);
  border-radius: .5rem;
  padding: 1.25rem 1.5rem;
  background: var(--bs-body-bg);
  font-size: .85rem; line-height: 1.6;
}
/* 分析框内 Markdown 元素间距补足（在全局 .md-* 基础上） */
.md-render .md-h1 { margin-top: 0; }
.md-render .md-table { font-size: .8rem; }
.empty-report .ico { font-size: 2.5rem; opacity: .5; }
</style>
