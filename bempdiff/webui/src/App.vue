<script setup>
import { ref, computed, onMounted } from 'vue'
import { state, init, closeReportPreview } from './store'
import ToolBar from './components/ToolBar.vue'
import DiffTree from './components/DiffTree.vue'
import DiffView from './components/DiffView.vue'
import InfoPanel from './components/InfoPanel.vue'
import StatusBar from './components/StatusBar.vue'
import ConfigDialog from './components/ConfigDialog.vue'
import CompareOverlay from './components/CompareOverlay.vue'
import ReportPreview from './components/ReportPreview.vue'
import CostGateDialog from './components/CostGateDialog.vue'

const showConfig = ref(false)
const showReport = ref(false)
onMounted(() => init())

// ===== 左右栏拖拽调宽（分隔条） =====
// 两栏宽度由此统一管理：树栏随拖拽实时改宽；分析栏「仅在用户拖拽过」后才固定为内联宽度，
// 否则保持 CSS + media query 的窄屏自动压缩（避免用户从未拖拽时被意外覆盖）。
// 注意：宽度必须经 prop 传入组件，而非 :style 透传——DiffTree 渲染为 fragment（含右键菜单/属性弹窗
// 多个顶层根节点），style 无法 fallthrough 到唯一根元素（曾导致拖拽无效的 bug）。
const treeW = ref(300)
const aiW = ref(380)
const aiResized = ref(false)

let dragCtx = null
function startResize(which, e) {
  // 已收起的栏不响应拖拽（分隔条此时已 v-show 隐藏，此处为双保险）
  if (which === 'tree' && state.treePanelCollapsed) return
  if (which === 'ai' && state.aiPanelCollapsed) return
  // 分隔条相邻的实际栏元素：树在分隔条左侧、分析栏在右侧，读其当前宽度作为拖拽基准
  const bar = e.currentTarget
  const peer = which === 'tree' ? bar.previousElementSibling : bar.nextElementSibling
  const baseW = peer ? peer.getBoundingClientRect().width : (which === 'tree' ? 300 : 380)
  dragCtx = { which, startX: e.clientX, baseW }
  // 拖拽期间全局呈现 col-resize 光标并禁用文本选中，避免穿过对比区时闪烁
  document.documentElement.classList.add('resizing-col')
  window.addEventListener('mousemove', onResize)
  window.addEventListener('mouseup', endResize)
  e.preventDefault()
}
function onResize(e) {
  if (!dragCtx) return
  const d = e.clientX - dragCtx.startX
  // 树向右拖变大；分析栏向左拖变大（方向相反）
  const raw = dragCtx.which === 'tree' ? dragCtx.baseW + d : dragCtx.baseW - d
  const MIN = 120
  const MAX = Math.max(MIN + 1, window.innerWidth * 0.6)
  const w = Math.min(MAX, Math.max(MIN, raw))
  if (dragCtx.which === 'tree') treeW.value = w
  else { aiW.value = w; aiResized.value = true }
}
function endResize() {
  if (!dragCtx) return
  dragCtx = null
  document.documentElement.classList.remove('resizing-col')
  window.removeEventListener('mousemove', onResize)
  window.removeEventListener('mouseup', endResize)
}

const toastClass = computed(() => {
  const t = state.toast && state.toast.type
  if (t === 'danger') return 'text-bg-danger'
  if (t === 'success') return 'text-bg-success'
  if (t === 'warning') return 'text-bg-warning'
  // default / info：用跟随主题的 body 背景，避免 dark 模式下 secondary 变亮导致 toast 闪白。
  return 'text-bg-body'
})

// 报告预览关闭：区分来源——任务预览（previewOpen）优先关闭，避免连带关闭先前打开的全局报告（评审 P2 #13）
function onPreviewClose() {
  if (state.previewOpen) closeReportPreview()
  else showReport.value = false
}
</script>

<template>
  <ToolBar :on-open-config="() => (showConfig = true)" :on-open-report="() => (showReport = true)" />

  <!-- 专注模式：隐藏左右栏，中间 diff 占满视野（由 state.focusMode 控制） -->
  <div class="app-main" :class="{ 'focus-mode': state.focusMode }">
    <DiffTree :panel-width="treeW" />
    <div v-show="!state.treePanelCollapsed" class="vt-handle" title="拖拽调整差异文件树宽度"
         @mousedown="startResize('tree', $event)"></div>
    <DiffView />
    <div v-show="!state.aiPanelCollapsed" class="vt-handle" title="拖拽调整智能分析面板宽度"
         @mousedown="startResize('ai', $event)"></div>
    <InfoPanel :panel-width="aiResized ? aiW : undefined" />
  </div>

  <StatusBar />

  <ConfigDialog :visible="showConfig" @close="showConfig = false" />
  <!-- 报告预览：既能看全局 reportMd，也能看 AI 控制台里某次任务的预览报告（:md 优先） -->
  <ReportPreview :visible="showReport || state.previewOpen" :md="state.previewOpen ? state.previewMd : null" @close="onPreviewClose" />
  <CostGateDialog />
  <CompareOverlay />

  <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index:3000">
    <div class="toast align-items-center border-0 show" v-if="state.toast" :class="toastClass" role="alert">
      <div class="d-flex">
        <div class="toast-body">{{ state.toast.text }}</div>
      </div>
    </div>
  </div>
</template>
