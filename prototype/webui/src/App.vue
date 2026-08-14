<script setup>
import { ref, computed, onMounted } from 'vue'
import { state, init } from './store'
import TopBar from './components/TopBar.vue'
import ToolBar from './components/ToolBar.vue'
import DiffTree from './components/DiffTree.vue'
import DiffView from './components/DiffView.vue'
import InfoPanel from './components/InfoPanel.vue'
import StatusBar from './components/StatusBar.vue'
import ConfigDialog from './components/ConfigDialog.vue'
import CompareOverlay from './components/CompareOverlay.vue'
import ReportPreview from './components/ReportPreview.vue'

const showConfig = ref(false)
const showReport = ref(false)
onMounted(() => init())

const toastClass = computed(() => {
  const t = state.toast && state.toast.type
  if (t === 'danger') return 'text-bg-danger'
  if (t === 'success') return 'text-bg-success'
  if (t === 'warning') return 'text-bg-warning'
  return 'text-bg-secondary'
})
</script>

<template>
  <TopBar />
  <ToolBar :on-open-config="() => (showConfig = true)" :on-open-report="() => (showReport = true)" />

  <div class="app-main">
    <DiffTree />
    <DiffView />
    <InfoPanel />
  </div>

  <StatusBar />

  <ConfigDialog :visible="showConfig" @close="showConfig = false" />
  <ReportPreview :visible="showReport" @close="showReport = false" />
  <CompareOverlay />

  <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index:3000">
    <div class="toast align-items-center border-0 show text-white" v-if="state.toast" :class="toastClass" role="alert">
      <div class="d-flex">
        <div class="toast-body">{{ state.toast.text }}</div>
      </div>
    </div>
  </div>
</template>
