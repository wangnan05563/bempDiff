<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { renderMarkdown } from '../lib/markdown'
import { api } from '../api/client'
import { ensureAiBudget } from '../store'

const props = defineProps({
  visible: { type: Boolean, default: false },
  jobId: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const streaming = ref(false)
const live = ref(false)
const answer = ref('')
const thinkingSteps = ref([])      // [{ phase, message }]
const thinkingCollapsed = ref(true) // 默认折叠（参考 wiki ThinkingBlock）
const errorMsg = ref('')
const controller = ref(null)
const bodyRef = ref(null)

const hasAnswer = computed(() => answer.value.length > 0)

watch(() => props.visible, (v) => {
  if (v) { reset(); start() }
  else { stop() }
})

function reset() {
  streaming.value = false
  live.value = false
  answer.value = ''
  thinkingSteps.value = []
  thinkingCollapsed.value = true
  errorMsg.value = ''
}

async function start() {
  if (!props.jobId) {
    errorMsg.value = '当前没有可分析的任务，请先完成一次「开始比对」。'
    return
  }
  // 成本闸门（P0 #5）：超阈值强制确认，未确认则不发起任何 AI 调用
  const ok = await ensureAiBudget('analyze')
  if (!ok) {
    errorMsg.value = '已取消：本次 AI 分析预估超成本阈值，未发起调用。'
    return
  }
  reset()
  streaming.value = true
  live.value = true
  controller.value = api.analyzeStream(props.jobId, {
    onThinking: (d) => {
      thinkingSteps.value.push({ phase: d.phase || 'thinking', message: d.message || '' })
      scrollBottom()
    },
    onAnswer: (d) => { answer.value += (d.text || ''); scrollBottom() },
    onDone: () => { streaming.value = false; live.value = false; scrollBottom() },
    onError: (d) => {
      streaming.value = false; live.value = false
      errorMsg.value = (d && d.message) || '分析失败'
    }
  })
}

function stop() {
  if (controller.value) { controller.value.abort(); controller.value = null }
  streaming.value = false
  live.value = false
}

function close() { stop(); emit('close') }

async function scrollBottom() {
  await nextTick()
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

function phaseLabel(p) {
  const m = { thinking: '思考', tool_call: '调用', composing: '组织' }
  return m[p] || '思考'
}

function toggleThinking() {
  // 流式阶段不允许手动折叠（与 wiki 行为一致），结束后用户可自由展开/折叠
  if (live.value) return
  thinkingCollapsed.value = !thinkingCollapsed.value
}
</script>

<template>
  <div class="modal-backdrop" v-if="visible" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header py-2 px-4">
          <h6 class="modal-title mb-0">
            <i class="bi bi-cpu"></i> AI 分析
            <small class="fw-normal text-secondary ms-2" style="font-size:.75rem">实时流式输出 · 含可展开的思考过程</small>
          </h6>
          <button type="button" class="btn-close" @click="close"></button>
        </div>

        <div class="modal-body ai-dialog-body">
          <div v-if="errorMsg" class="alert alert-danger py-2 mb-3" style="font-size:.82rem">
            <i class="bi bi-exclamation-triangle"></i> {{ errorMsg }}
          </div>

          <!-- 思考过程：浅灰文字、默认折叠、可展开（参考 wiki ThinkingBlock） -->
          <div class="thinking-block" :class="{ collapsed: thinkingCollapsed && !live, live }">
            <div class="thinking-header" @click="toggleThinking">
              <i class="bi bi-lightbulb thinking-icon"></i>
              <span class="thinking-summary">
                已思考 {{ thinkingSteps.length }} 步<span v-if="thinkingSteps.length"> · 点击{{ (thinkingCollapsed && !live) ? '展开' : '折叠' }}查看思考过程</span>
              </span>
              <i class="bi thinking-toggle" :class="(thinkingCollapsed && !live) ? 'bi-chevron-right' : 'bi-chevron-down'"></i>
            </div>
            <div class="thinking-body" v-show="!thinkingCollapsed || live">
              <div v-for="(s, idx) in thinkingSteps" :key="idx" class="thinking-step">
                <span class="step-phase" :class="s.phase">{{ phaseLabel(s.phase) }}</span>
                <span class="step-message">{{ s.message }}</span>
              </div>
            </div>
          </div>

          <!-- 答案流式输出区 -->
          <div ref="bodyRef" class="ai-answer ai-md">
            <div v-if="!hasAnswer && streaming" class="loading-dots">
              <span></span><span></span><span></span> AI 正在生成…
            </div>
            <div v-else-if="!hasAnswer && !streaming && !errorMsg" class="text-secondary" style="font-size:.82rem">
              暂无输出
            </div>
            <div v-html="renderMarkdown(answer)"></div><span v-if="streaming" class="stream-cursor">▋</span>
          </div>
        </div>

        <div class="modal-footer py-2 px-4 d-flex align-items-center gap-2">
          <span class="me-auto text-secondary" style="font-size:.75rem">
            <span v-if="streaming"><i class="bi bi-arrow-repeat spin"></i> 分析中…</span>
            <span v-else-if="hasAnswer"><i class="bi bi-check2-circle"></i> 分析完成</span>
          </span>
          <button v-if="streaming" class="btn btn-outline-secondary btn-sm" @click="stop">
            <i class="bi bi-stop-fill"></i> 停止
          </button>
          <button v-else class="btn btn-outline-secondary btn-sm" :disabled="!props.jobId" @click="start">
            <i class="bi bi-arrow-clockwise"></i> 重新分析
          </button>
          <button class="btn btn-primary btn-sm" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 遮罩与内容：与其他三个 modal（ConfigDialog / CostGateDialog / ReportPreview）保持一致。
 * 缺省时 Bootstrap .modal-backdrop 默认 background-color:#000 + opacity:.5
 * 会铺满全屏呈「黑屏」——必须显式给半透明背景 + 颜色随主题。 */
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,.4);
  display: flex; align-items: flex-start; justify-content: center; z-index: 1500; padding-top: 5vh;
}
.modal-content { width: 100%; background-color: var(--bs-body-bg); color: var(--bs-body-color); }
.ai-dialog-body {
  padding: 1rem 1.5rem;
  max-height: 68vh;
  overflow-y: auto;
}
/* 思考块：浅灰文字 + 微弱紫色左边框（呼应 wiki 视觉），默认折叠 */
.thinking-block {
  margin: 4px 0 14px;
  padding: 6px 10px;
  background: rgba(178, 38, 255, 0.05);
  border-left: 2px solid rgba(178, 38, 255, 0.35);
  border-radius: 0 6px 6px 0;
}
.thinking-block.live { background: rgba(178, 38, 255, 0.08); }
.thinking-header {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--bs-secondary-color);
}
.thinking-icon { font-size: 12px; color: var(--bs-secondary-color); }
/* 流式阶段图标脉动，提示「正在思考」 */
.thinking-block.live .thinking-icon { animation: thinking-pulse 1.5s ease-in-out infinite; }
@keyframes thinking-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.15); }
}
.thinking-summary { color: var(--bs-secondary-color); font-size: 11.5px; }
.thinking-toggle { margin-left: auto; font-size: 11px; color: var(--bs-secondary-color); }
.thinking-body {
  margin-top: 6px;
  max-height: 240px;
  overflow-y: auto;
  transition: max-height 200ms ease, opacity 200ms ease;
}
.thinking-block.collapsed .thinking-body { max-height: 0; opacity: 0; overflow: hidden; }
.thinking-step {
  display: flex;
  gap: 6px;
  padding: 3px 0;
  font-size: 11px;
  color: var(--bs-secondary-color);
  border-bottom: 1px dashed rgba(128, 128, 128, 0.12);
  align-items: flex-start;
}
.step-phase {
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
  white-space: nowrap;
  color: var(--bs-secondary-color);
  background: rgba(178, 38, 255, 0.1);
}
.step-message { flex: 1; color: var(--bs-secondary-color); font-size: 11px; line-height: 1.5; }

/* 答案区：复用 .ai-md 排版，叠加流式光标 */
.ai-answer {
  margin-top: 6px;
  padding: 10px 12px;
  border: 1px solid var(--bs-border-color);
  border-radius: 8px;
  background: var(--bs-body-bg);
  min-height: 120px;
}
.stream-cursor {
  display: inline-block;
  margin-left: 1px;
  color: var(--bs-primary);
  animation: cursor-blink 1s steps(2) infinite;
}
@keyframes cursor-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.loading-dots { color: var(--bs-secondary-color); font-size: .85rem; }
.loading-dots span {
  display: inline-block;
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--bs-secondary-color);
  margin: 0 1px;
  animation: dot-bounce 1.2s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: .2s; }
.loading-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: .4; }
  40% { transform: translateY(-4px); opacity: 1; }
}
.spin { animation: spin 1s linear infinite; display: inline-block; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>
