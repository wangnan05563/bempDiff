<script setup>
import { ref, computed, onMounted } from 'vue'
import { state, init } from './store'
import ToolBar from './components/ToolBar.vue'
import DiffTree from './components/DiffTree.vue'
import DiffView from './components/DiffView.vue'
import InfoPanel from './components/InfoPanel.vue'
import StatusBar from './components/StatusBar.vue'
import ConfigDialog from './components/ConfigDialog.vue'
import CompareOverlay from './components/CompareOverlay.vue'
import ReportPreview from './components/ReportPreview.vue'
import AiAnalysisDialog from './components/AiAnalysisDialog.vue'

const showConfig = ref(false)
const showReport = ref(false)
const showAi = ref(false)
onMounted(() => init())

const toastClass = computed(() => {
  const t = state.toast && state.toast.type
  if (t === 'danger') return 'text-bg-danger'
  if (t === 'success') return 'text-bg-success'
  if (t === 'warning') return 'text-bg-warning'
  // default / info：用跟随主题的 body 背景，避免 dark 模式下 secondary 变亮导致 toast 闪白。
  return 'text-bg-body'
})
</script>

<template>
  <ToolBar :on-open-config="() => (showConfig = true)" :on-open-report="() => (showReport = true)" :on-open-ai="() => (showAi = true)" />

  <!-- 专注模式：隐藏左右栏，中间 diff 占满视野（由 state.focusMode 控制） -->
  <div class="app-main" :class="{ 'focus-mode': state.focusMode }">
    <DiffTree />
    <DiffView />
    <InfoPanel />
  </div>

  <StatusBar />

  <ConfigDialog :visible="showConfig" @close="showConfig = false" />
  <ReportPreview :visible="showReport" @close="showReport = false" />
  <AiAnalysisDialog :visible="showAi" :job-id="state.job ? state.job.jobId : ''" @close="showAi = false" />
  <CompareOverlay />

  <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index:3000">
    <div class="toast align-items-center border-0 show" v-if="state.toast" :class="toastClass" role="alert">
      <div class="d-flex">
        <div class="toast-body">{{ state.toast.text }}</div>
      </div>
    </div>
  </div>
</template>
