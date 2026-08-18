<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { state, startAiAnalysis, stopAiAnalysis, restartAiAnalysis, selectAiTask, closeAiTask, openReportPreview, AI_CATEGORIES } from '../store'
import { renderMarkdown } from '../lib/markdown'

const selCategory = ref('risk')
const customPrompt = ref('')
const showCustom = computed(() => selCategory.value === 'custom')
const bodyRef = ref(null)

const hasJob = computed(() => !!(state.job && state.job.status === 'DONE'))
const active = computed(() => state.aiTasks.find(t => t.id === state.aiActiveTaskId) || null)
const hasTasks = computed(() => state.aiTasks.length > 0)
const runningCount = computed(() => state.aiTasks.filter(t => t.status === 'thinking' || t.status === 'streaming').length)

function newAnalysis() {
  if (!hasJob.value) return
  if (selCategory.value === 'custom') startAiAnalysis('custom', customPrompt.value.trim())
  else startAiAnalysis(selCategory.value)
}

function phaseLabel(p) {
  const m = { thinking: '思考', tool_call: '调用', composing: '组织' }
  return m[p] || '思考'
}
function statusMeta(t) {
  switch (t.status) {
    case 'thinking':
    case 'streaming': return { cls: 'bi-arrow-repeat spin', color: 'text-primary' }
    case 'done': return { cls: 'bi-check2-circle', color: 'text-success' }
    case 'error': return { cls: 'bi-exclamation-triangle', color: 'text-danger' }
    case 'aborted': return { cls: 'bi-stop-fill', color: 'text-secondary' }
    default: return { cls: 'bi-circle', color: 'text-secondary' }
  }
}
const mdHtml = computed(() => (active.value && active.value.answer) ? renderMarkdown(active.value.answer) : '')

// 自动滚底：监听当前任务 answer/thinking 长度变化
function scrollBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
    requestAnimationFrame(() => { if (el) el.scrollTop = el.scrollHeight })
  })
}
watch(() => (active.value ? active.value.answer.length + '|' + active.value.thinking.length : ''), scrollBottom)

function previewReport() {
  if (active.value && active.value.answer) openReportPreview(active.value.answer)
}
function previewDisabled(t) { return !(t && t.answer && t.answer.length) }
</script>

<template>
  <div class="ai-console">
    <div class="console-head">
      <i class="bi bi-terminal"></i><span class="ms-1">AI 分析控制台</span>
      <span v-if="runningCount" class="badge text-bg-primary ms-2">{{ runningCount }} 进行中</span>
    </div>

    <!-- 新建分析：类别选择 + 发起 -->
    <div class="console-new d-flex align-items-center gap-2 flex-wrap">
      <select class="form-select form-select-sm" style="width:auto" v-model="selCategory">
        <option v-for="c in AI_CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <input v-if="showCustom" class="form-control form-control-sm" style="max-width:220px"
             v-model="customPrompt" placeholder="输入你的分析问题…" :disabled="!hasJob">
      <button class="btn btn-sm btn-primary" @click="newAnalysis" :disabled="!hasJob" title="发起一次新的 AI 分析（并行，不阻塞界面）">
        <i class="bi bi-plus-lg"></i> 新建分析
      </button>
      <span v-if="!hasJob" class="text-secondary" style="font-size:.75rem">完成比对后可发起</span>
    </div>

    <!-- 无任务占位 -->
    <div v-if="!hasTasks" class="console-empty text-secondary">
      尚无 AI 分析任务。选择类别后点击「新建分析」，结果将在下方实时流式输出，可并行发起多类别分析。
    </div>

    <template v-else>
      <!-- 并行任务 tab 栏：每个任务独立窗口 -->
      <div class="console-tabs d-flex flex-wrap gap-1">
        <button v-for="t in state.aiTasks" :key="t.id"
                class="console-tab btn btn-sm"
                :class="{ active: t.id === state.aiActiveTaskId }"
                @click="selectAiTask(t.id)">
          <i class="bi" :class="statusMeta(t).cls + ' ' + statusMeta(t).color"></i>
          <span class="tab-title">{{ t.title }}</span>
          <i class="bi bi-x tab-close" @click.stop="closeAiTask(t.id)" title="关闭此分析"></i>
        </button>
      </div>

      <!-- 当前任务控制台窗口 -->
      <div class="console-body" ref="bodyRef">
        <div v-if="active.error" class="alert alert-danger py-2 mb-2" style="font-size:.82rem">
          <i class="bi bi-exclamation-triangle"></i> {{ active.error }}
        </div>

        <!-- 思考过程：浅灰、默认折叠、可展开 -->
        <div class="thinking-block" :class="{ collapsed: active.thinkingCollapsed !== false }">
          <div class="thinking-header" @click="active.thinkingCollapsed = !(active.thinkingCollapsed === true)">
            <i class="bi bi-lightbulb thinking-icon"></i>
            <span class="thinking-summary">
              已思考 {{ active.thinking.length }} 步 · 点击{{ active.thinkingCollapsed === false ? '折叠' : '展开' }}查看思考过程
            </span>
            <i class="bi thinking-toggle" :class="active.thinkingCollapsed === false ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
          </div>
          <div class="thinking-body" v-show="active.thinkingCollapsed === false">
            <div v-for="(s, idx) in active.thinking" :key="idx" class="thinking-step">
              <span class="step-phase" :class="s.phase">{{ phaseLabel(s.phase) }}</span>
              <span class="step-message">{{ s.message }}</span>
            </div>
          </div>
        </div>

        <!-- 答案流式输出 -->
        <div class="ai-answer ai-md">
          <div v-if="!active.answer && active.status==='streaming'" class="loading-dots">
            <span></span><span></span><span></span> AI 正在生成…
          </div>
          <div v-else-if="!active.answer && active.status==='thinking'" class="text-secondary" style="font-size:.82rem">
            正在准备分析…
          </div>
          <div v-else-if="!active.answer" class="text-secondary" style="font-size:.82rem">暂无输出</div>
          <div v-html="mdHtml"></div><span v-if="active.status==='streaming'" class="stream-cursor">▋</span>
        </div>
      </div>

      <!-- 操作：中断 / 重新分析 / 预览报告 / 关闭 -->
      <div class="console-foot d-flex align-items-center gap-2">
        <span class="me-auto text-secondary" style="font-size:.75rem">
          <span v-if="active.status==='streaming'"><i class="bi bi-arrow-repeat spin"></i> 分析中…</span>
          <span v-else-if="active.status==='thinking'"><i class="bi bi-arrow-repeat spin"></i> 准备中…</span>
          <span v-else-if="active.status==='done'"><i class="bi bi-check2-circle text-success"></i> 分析完成</span>
          <span v-else-if="active.status==='aborted'"><i class="bi bi-stop-fill"></i> 已中断</span>
          <span v-else-if="active.status==='error'"><i class="bi bi-exclamation-triangle text-danger"></i> 失败</span>
        </span>
        <button v-if="active.status==='thinking' || active.status==='streaming'"
                class="btn btn-outline-secondary btn-sm" @click="stopAiAnalysis(active.id)">
          <i class="bi bi-stop-fill"></i> 中断
        </button>
        <button v-else class="btn btn-outline-secondary btn-sm" @click="restartAiAnalysis(active.id)" :disabled="!hasJob">
          <i class="bi bi-arrow-clockwise"></i> 重新分析
        </button>
        <button class="btn btn-outline-primary btn-sm" :disabled="previewDisabled(active)" @click="previewReport" title="在新窗口预览完整 Markdown 报告">
          <i class="bi bi-filetype-md"></i> 预览报告
        </button>
        <button class="btn btn-outline-secondary btn-sm" @click="closeAiTask(active.id)">
          <i class="bi bi-x-lg"></i> 关闭
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ai-console { flex: 1 1 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden;
  padding: .75rem .75rem .5rem; }
.console-head { font-size: .8rem; font-weight: 600; color: var(--bs-secondary-color); display: flex; align-items: center; }
.console-new { margin: .4rem 0; }
.console-empty { font-size: .8rem; padding: .5rem 0; }
.console-tabs { margin-bottom: .4rem; max-height: 7rem; overflow: auto; }
.console-tab { --bs-btn-padding-y: .15rem; --bs-btn-padding-x: .5rem; font-size: .74rem; display: flex; align-items: center; gap: .3rem;
  border: 1px solid var(--bs-border-color); background: var(--bs-tertiary-bg); color: var(--bs-body-color); }
.console-tab.active { border-color: var(--bs-primary); background: var(--bs-primary-bg-subtle); }
.console-tab .tab-title { max-width: 9rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.console-tab .tab-close { font-size: .7rem; opacity: .6; }
.console-tab .tab-close:hover { opacity: 1; color: var(--bs-danger); }
.console-body { flex: 1 1 auto; min-height: 0; overflow: auto; border: 1px solid var(--bs-border-color);
  border-radius: 8px; padding: .6rem .75rem; background: var(--bs-body-bg); }
.console-foot { margin-top: .4rem; }

/* 思考块：浅灰 + 微弱紫色左边框（呼应 AiAnalysisDialog 视觉），默认折叠 */
.thinking-block { margin: 4px 0 10px; padding: 6px 10px; background: rgba(178,38,255,0.05);
  border-left: 2px solid rgba(178,38,255,0.35); border-radius: 0 6px 6px 0; }
.thinking-header { cursor: pointer; display: flex; align-items: center; gap: 6px; font-size: 11.5px; color: var(--bs-secondary-color); }
.thinking-icon { font-size: 12px; color: var(--bs-secondary-color); }
.thinking-summary { color: var(--bs-secondary-color); font-size: 11.5px; }
.thinking-toggle { margin-left: auto; font-size: 11px; color: var(--bs-secondary-color); }
.thinking-body { margin-top: 6px; max-height: 200px; overflow-y: auto; }
.thinking-block.collapsed .thinking-body { display: none; }
.thinking-step { display: flex; gap: 6px; padding: 3px 0; font-size: 11px; color: var(--bs-secondary-color);
  border-bottom: 1px dashed rgba(128,128,128,0.12); align-items: flex-start; }
.step-phase { padding: 1px 5px; border-radius: 3px; font-size: 10px; white-space: nowrap; color: var(--bs-secondary-color); background: rgba(178,38,255,0.1); }
.step-message { flex: 1; color: var(--bs-secondary-color); font-size: 11px; line-height: 1.5; }

.ai-answer { margin-top: 6px; padding: 10px 12px; border: 1px solid var(--bs-border-color); border-radius: 8px; background: var(--bs-body-bg); min-height: 80px; }
.stream-cursor { display: inline-block; margin-left: 1px; color: var(--bs-primary); animation: cursor-blink 1s steps(2) infinite; }
@keyframes cursor-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.loading-dots { color: var(--bs-secondary-color); font-size: .85rem; }
.loading-dots span { display: inline-block; width: 5px; height: 5px; border-radius: 50%; background: var(--bs-secondary-color); margin: 0 1px; animation: dot-bounce 1.2s infinite; }
.loading-dots span:nth-child(2) { animation-delay: .2s; }
.loading-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dot-bounce { 0%, 80%, 100% { transform: translateY(0); opacity: .4; } 40% { transform: translateY(-4px); opacity: 1; } }
.spin { animation: spin 1s linear infinite; display: inline-block; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>
