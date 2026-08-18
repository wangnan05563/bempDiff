<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { state, triggerCompare, generateReport, downloadExport, runAiClassify, toast, applyTheme, ingestShellPaths, inferType, startAiAnalysis } from '../store'
import { isTauri, isElectron, pickPath } from '../lib/tauri'

const props = defineProps({
  onOpenConfig: { type: Function, required: true },
  onOpenReport: { type: Function, required: true }
})

const showExport = ref(false)
// 拖拽高亮状态：'old' | 'new' | null
const dragOver = ref(null)

// 点击下拉框外部自动收缩：监听 document click，若点击目标不在导出按钮容器内则关闭。
const exportWrap = ref(null)
function onDocClick(e) {
  if (showExport.value && exportWrap.value && !exportWrap.value.contains(e.target)) {
    showExport.value = false
  }
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

function onReport(ai) { showExport.value = false; generateReport(ai) }
function onExport() { showExport.value = false; downloadExport() }
function onClassify() { runAiClassify() }
// 工具栏 AI 按钮：直接发起一个「整体风险分析」任务（非阻塞，结果落在下方控制台），不再弹阻塞模态。
function onAiAnalyze() { startAiAnalysis('risk') }

function assignPath(which, val) {
  if (which === 'old') { state.oldPath = val; state.leftType = inferType(val) }
  else state.newPath = val
}

// 主题切换：整合进工具栏（原在 TopBar 内）
const isDark = computed(() => state.theme === 'dark')
function toggleTheme() { applyTheme(isDark.value ? 'light' : 'dark') }

// AI 分析进行中：禁用工具栏的对比按钮与路径输入框，避免干扰正在生成的对比结果。
const aiBusy = computed(() => state.aiTasks.some(t => t.status === 'thinking' || t.status === 'streaming'))

// 桌面壳（Electron / Tauri）：原生对话框直接拿绝对路径（免上传）；浏览器模式：提示手动输入服务器本机绝对路径。
async function browse(which) {
  const sel = await pickPath({ directory: state.leftType === 'folder' })
  if (sel) {
    assignPath(which, Array.isArray(sel) ? sel[0] : sel)
    return
  }
  if (!isTauri() && !isElectron()) {
    toast('info', '浏览器模式下请直接输入服务器本机绝对路径；打包桌面壳（Electron）后可点击此按钮用原生对话框选择')
  }
}

// ---------- 拖拽即比（仅桌面壳可拿到真实路径；浏览器模式降级提示） ----------
function onDragOver(which, e) {
  if (!isElectron()) return
  e.preventDefault()
  dragOver.value = which
}
function onDragLeave(which) {
  if (dragOver.value === which) dragOver.value = null
}
function onDrop(which, e) {
  dragOver.value = null
  if (!isElectron()) {
    toast('info', '浏览器模式不支持直接拖入文件，请使用桌面壳（Electron）或手填路径')
    return
  }
  e.preventDefault()
  const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  const p = file && file.path
  if (!p) {
    toast('warning', '未能读取拖入的路径，请重试或手填')
    return
  }
  assignPath(which, p)
  // 两个路径齐备即自动开始比对（拖拽即比的「即」）
  if (state.oldPath && state.newPath) triggerCompare()
}

// 工具栏「开始比对」按钮
function doCompare() { triggerCompare() }
</script>

<template>
  <div class="bg-body-tertiary border-bottom px-2 py-1 d-flex flex-wrap align-items-center gap-2">
    <!-- 输入类型切换：图标 btn-group 分段控件 -->
    <div class="btn-group btn-group-sm" role="group" aria-label="输入类型">
      <button type="button" class="btn" :class="state.leftType === 'package' ? 'btn-primary' : 'btn-outline-secondary'"
              @click="state.leftType = 'package'" :disabled="aiBusy" title="以单个 war/jar 包作为输入（默认）">
        <i class="bi bi-file-earmark-zip"></i>
      </button>
      <button type="button" class="btn" :class="state.leftType === 'folder' ? 'btn-primary' : 'btn-outline-secondary'"
              @click="state.leftType = 'folder'" :disabled="aiBusy" title="以解压后的目录作为输入，对比目录结构的差异">
        <i class="bi bi-folder"></i>
      </button>
    </div>

    <!-- 老包/老目录：可拖入文件/目录（桌面壳） -->
    <div class="input-group input-group-sm drop-zone" :class="{ 'drop-active': dragOver === 'old' }"
         style="max-width:240px"
         @dragover="onDragOver('old', $event)" @dragenter="onDragOver('old', $event)"
         @dragleave="onDragLeave('old')" @drop="onDrop('old', $event)">
      <span class="input-group-text" :title="state.leftType === 'folder' ? '老目录：作为对比基准的目录' : '老包(生产)：当前生产环境运行的版本，作为对比基准'">{{ state.leftType === 'folder' ? '老目录' : '老包(生产)' }}</span>
      <input class="form-control" v-model="state.oldPath" :disabled="aiBusy" :placeholder="state.leftType==='folder' ? 'D:/path/old' : 'sample_v1.war'">
      <button class="btn btn-outline-secondary" @click="browse('old')" :disabled="aiBusy" :title="state.leftType === 'folder' ? '浏览选择老目录（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框' : '浏览选择老包（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框'"><i class="bi bi-folder2-open"></i></button>
    </div>
    <!-- 新包/新目录：可拖入文件/目录（桌面壳） -->
    <div class="input-group input-group-sm drop-zone" :class="{ 'drop-active': dragOver === 'new' }"
         style="max-width:240px"
         @dragover="onDragOver('new', $event)" @dragenter="onDragOver('new', $event)"
         @dragleave="onDragLeave('new')" @drop="onDrop('new', $event)">
      <span class="input-group-text" :title="state.leftType === 'folder' ? '新目录：本次要对比的目标目录' : '新包(下发)：本次要下发的版本，作为对比目标'">{{ state.leftType === 'folder' ? '新目录' : '新包(下发)' }}</span>
      <input class="form-control" v-model="state.newPath" :disabled="aiBusy" :placeholder="state.leftType==='folder' ? 'D:/path/new' : 'sample_v2.war'">
      <button class="btn btn-outline-secondary" @click="browse('new')" :disabled="aiBusy" :title="state.leftType === 'folder' ? '浏览选择新目录（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框' : '浏览选择新包（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框'"><i class="bi bi-folder2-open"></i></button>
    </div>

    <button class="btn btn-primary btn-sm" @click="doCompare" :disabled="aiBusy" title="加载两个包/目录，反编译并生成差异树（耗时与包大小相关）"><i class="bi bi-arrow-left-right"></i></button>

    <div class="ms-auto d-flex gap-2">
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenReport" :disabled="!state.reportMd" title="查看最近一次生成的差异/AI 分析报告">
        <i class="bi bi-filetype-md"></i>
      </button>
      <button class="btn btn-outline-secondary btn-sm" @click="onAiAnalyze"
              :disabled="!state.job || aiBusy"
              :title="aiBusy ? 'AI 分析进行中，已禁用以防干扰' : '发起新的 AI 分析（在下方控制台实时流式输出，可并行多类别）'">
        <i class="bi bi-cpu"></i>
      </button>
      <button class="btn btn-outline-secondary btn-sm" @click="onClassify"
              :disabled="!state.job || state.job.status !== 'DONE' || state.classifying"
              :title="state.classifying ? '智能分类中…' : 'AI 自动对差异文件打标分类并评估风险等级，结果在左侧差异树展示'">
        <span v-if="state.classifying" class="spinner-border spinner-border-sm me-1"></span>
        <i v-else class="bi bi-tags"></i>
        {{ state.classifying ? '分类中' : '智能分类' }}
      </button>
      <div class="position-relative" ref="exportWrap">
        <button class="btn btn-outline-secondary btn-sm" @click="showExport = !showExport" :disabled="!state.job" title="导出差异报告或差异资产">
          <i class="bi bi-file-earmark-arrow-down"></i>
        </button>
        <ul class="dropdown-menu dropdown-menu-end show py-1" v-if="showExport"
            style="position:absolute;right:0;top:100%;z-index:1000">
          <li><a class="dropdown-item" href="#" @click.prevent="onReport(false)" title="生成纯文本/Markdown 差异报告（不调用 AI）"><i class="bi bi-filetype-md"></i> 生成报告</a></li>
          <li><a class="dropdown-item" href="#" @click.prevent="onReport(true)" title="调用 AI 生成风险/影响评估报告（需启用 AI 分析并配置模型）"><i class="bi bi-cpu"></i> 生成报告(AI)</a></li>
          <li><a class="dropdown-item" href="#" @click.prevent="onExport" title="导出差异文件、反编译源码、报告等资产为 zip 包"><i class="bi bi-box-seam"></i> 导出差异资产(zip)</a></li>
        </ul>
      </div>
      <button class="btn btn-outline-secondary btn-sm" @click="toggleTheme"
              :title="isDark ? '切换浅色' : '切换深色'">
        <i class="bi" :class="isDark ? 'bi-sun' : 'bi-moon-stars'"></i>
      </button>
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenConfig" title="打开配置中心：模型、解析与导出、差异树过滤、界面与高级"><i class="bi bi-gear"></i></button>
    </div>
  </div>
</template>
