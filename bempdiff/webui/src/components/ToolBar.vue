<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { state, triggerCompare, generateReport, downloadExport, runAiClassify, toast, applyTheme, ingestShellPaths, inferType, startAiAnalysis, aiAnyRunning } from '../store'
import { isTauri, isElectron, pickPath } from '../lib/tauri'
import PathBreadcrumb from './PathBreadcrumb.vue'

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
// 面包屑点击/编辑提交后同步输入类型（与 assignPath 口径一致：old 侧按扩展名推断 package/folder）
function onPathUpdate(which, v) {
  if (which === 'old') state.leftType = inferType(v)
}

// ======================================================================
// BCompare 风格路径导航：单侧/两侧「向上一层」+ 后退/前进历史
// ======================================================================
const MAX_HIST = 100
const histStack = ref([])     // [{old, new}] 路径对快照（浏览器式历史）
const histIndex = ref(-1)     // 当前快照在栈中的位置
const histLock = ref(false)   // 恢复历史时禁止再次入栈（防回环）

function snapshot() { return { old: state.oldPath, new: state.newPath } }

/** 把当前路径对记入历史（与栈顶相同则忽略；前进分支上的旧未来被截断）。 */
function pushHist() {
  if (histLock.value) return
  const s = snapshot()
  const top = histStack.value[histIndex.value]
  if (top && top.old === s.old && top.new === s.new) return
  histStack.value = histStack.value.slice(0, histIndex.value + 1)
  histStack.value.push(s)
  if (histStack.value.length > MAX_HIST) histStack.value.shift()
  histIndex.value = histStack.value.length - 1
}

/** 恢复历史快照（不重新入栈）。 */
function applySnapshot(s) {
  histLock.value = true
  try {
    if (s.old !== undefined) { state.oldPath = s.old; state.leftType = inferType(s.old) }
    if (s.new !== undefined) { state.newPath = s.new }
  } finally { histLock.value = false }
}

/** 计算上一层目录路径（Windows / POSIX 通用）；已到根或无可上则原样返回。 */
function upOneLevel(p) {
  if (!p) return p
  const s = String(p).replace(/\\/g, '/').replace(/\/+$/, '')
  const idx = s.lastIndexOf('/')
  if (idx < 0) return p
  const parent = s.slice(0, idx)
  if (!parent) return '/'
  // Windows 盘符根：D: → D:/（保持可继续向上判定的形式）
  if (parent.length === 2 && parent[1] === ':') return parent + '/'
  return parent
}

function toastRoot(which) {
  toast('info', (which === 'old' ? '老' : '新') + '路径已位于最上层目录')
}

/** 单侧向上一层（保留输入类型不变，避免静默切换 package/folder）。 */
function goUp(which) {
  if (aiBusy.value) return
  const cur = which === 'old' ? state.oldPath : state.newPath
  const next = upOneLevel(cur)
  if (next === cur) { toastRoot(which); return }
  pushHist()
  if (which === 'old') state.oldPath = next
  else state.newPath = next
}

/** 两侧同时向上一层。 */
function goUpBoth() {
  if (aiBusy.value) return
  const o = upOneLevel(state.oldPath)
  const n = upOneLevel(state.newPath)
  if (o === state.oldPath && n === state.newPath) {
    toast('info', '两侧路径均已位于最上层目录')
    return
  }
  pushHist()
  state.oldPath = o
  state.newPath = n
}

const canBack = computed(() => histIndex.value > 0)
const canForward = computed(() => histIndex.value >= 0 && histIndex.value < histStack.value.length - 1)

/** 后退：回到上一次浏览的路径。 */
function goBack() {
  if (!canBack.value || aiBusy.value) return
  histIndex.value--
  applySnapshot(histStack.value[histIndex.value])
}

/** 前进：回到后退之前的路径。 */
function goForward() {
  if (!canForward.value || aiBusy.value) return
  histIndex.value++
  applySnapshot(histStack.value[histIndex.value])
}

// 主题切换：整合进工具栏（原在 TopBar 内）
const isDark = computed(() => state.theme === 'dark')
function toggleTheme() { applyTheme(isDark.value ? 'light' : 'dark') }

// AI 分析进行中：禁用工具栏的对比按钮与路径输入框，避免干扰正在生成的对比结果。
// 复用 store.aiAnyRunning 单一判定源，避免双处维护漂移（评审 P2 #15）。
const aiBusy = computed(() => aiAnyRunning())

// 桌面壳（Electron / Tauri）：原生对话框直接拿绝对路径（免上传）；浏览器模式：提示手动输入服务器本机绝对路径。
async function browse(which) {
  const sel = await pickPath({ directory: state.leftType === 'folder' })
  if (sel) {
    pushHist() // 记入历史：后退可回到浏览前的路径
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
  pushHist() // 记入历史：后退可回到拖拽前的路径
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

    <!-- 老包/老目录：可拖入文件/目录（桌面壳）；面包屑点击层级即导航到该级目录 -->
    <div class="d-flex align-items-center gap-1 drop-zone" :class="{ 'drop-active': dragOver === 'old' }"
         style="max-width:320px"
         @dragover="onDragOver('old', $event)" @dragenter="onDragOver('old', $event)"
         @dragleave="onDragLeave('old')" @drop="onDrop('old', $event)">
      <span class="tb-label" :title="state.leftType === 'folder' ? '老目录：作为对比基准的目录' : '老包(生产)：当前生产环境运行的版本，作为对比基准'">{{ state.leftType === 'folder' ? '老目录' : '老' }}</span>
      <PathBreadcrumb v-model="state.oldPath" class="tb-crumb" :label="state.leftType === 'folder' ? '老目录' : '老包'" :disabled="aiBusy" @update:model-value="(v) => onPathUpdate('old', v)" />
      <button class="btn btn-outline-secondary btn-sm" @click="browse('old')" :disabled="aiBusy" :title="state.leftType === 'folder' ? '浏览选择老目录（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框' : '浏览选择老包（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框'"><i class="bi bi-folder2-open"></i></button>
      <button class="btn btn-outline-secondary btn-sm" @click="goUp('old')" :disabled="aiBusy" title="向上一层：老路径切换到其上级目录"><i class="bi bi-arrow-up-circle"></i></button>
    </div>
    <!-- 新包/新目录：可拖入文件/目录（桌面壳）；面包屑点击层级即导航到该级目录 -->
    <div class="d-flex align-items-center gap-1 drop-zone" :class="{ 'drop-active': dragOver === 'new' }"
         style="max-width:320px"
         @dragover="onDragOver('new', $event)" @dragenter="onDragOver('new', $event)"
         @dragleave="onDragLeave('new')" @drop="onDrop('new', $event)">
      <span class="tb-label" :title="state.leftType === 'folder' ? '新目录：本次要对比的目标目录' : '新包(下发)：本次要下发的版本，作为对比目标'">{{ state.leftType === 'folder' ? '新目录' : '新' }}</span>
      <PathBreadcrumb v-model="state.newPath" class="tb-crumb" :label="state.leftType === 'folder' ? '新目录' : '新包'" :disabled="aiBusy" @update:model-value="(v) => onPathUpdate('new', v)" />
      <button class="btn btn-outline-secondary btn-sm" @click="browse('new')" :disabled="aiBusy" :title="state.leftType === 'folder' ? '浏览选择新目录（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框' : '浏览选择新包（桌面壳可用原生对话框，浏览器模式请手填路径）；也可直接把文件拖入此框'"><i class="bi bi-folder2-open"></i></button>
      <button class="btn btn-outline-secondary btn-sm" @click="goUp('new')" :disabled="aiBusy" title="向上一层：新路径切换到其上级目录"><i class="bi bi-arrow-up-circle"></i></button>
    </div>

    <!-- BCompare 风格路径导航：两侧同时向上 + 后退/前进历史 -->
    <button class="btn btn-outline-secondary btn-sm" @click="goUpBoth" :disabled="aiBusy" title="两侧同时向上一层：左右路径都切换到各自上级目录">
      <i class="bi bi-arrow-up-square"></i>
    </button>
    <div class="btn-group btn-group-sm" role="group" aria-label="路径导航历史">
      <button class="btn btn-outline-secondary" @click="goBack" :disabled="!canBack || aiBusy" title="返回：回到上一次浏览的路径（后退）">
        <i class="bi bi-arrow-left"></i>
      </button>
      <button class="btn btn-outline-secondary" @click="goForward" :disabled="!canForward || aiBusy" title="前进：回到后退之前的路径">
        <i class="bi bi-arrow-right"></i>
      </button>
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
              :disabled="!state.job || state.job.status !== 'DONE' || state.classifying || aiBusy"
              :title="state.classifying ? '智能分类中…' : (aiBusy ? 'AI 分析进行中，请稍候' : 'AI 自动对差异文件打标分类并评估风险等级，结果在左侧差异树展示')">
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
          <li><a class="dropdown-item" href="#" :class="{ disabled: aiBusy }" @click.prevent="!aiBusy && onReport(true)" title="调用 AI 生成风险/影响评估报告（需启用 AI 分析并配置模型；AI 分析进行中暂不可用）"><i class="bi bi-cpu"></i> 生成报告(AI)</a></li>
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

<style scoped>
/* 包路径输入区：label + 面包屑 + 操作按钮（flex 布局替代 input-group，容纳可横向滚动的面包屑） */
.tb-label {
  font-size: .72rem;
  font-weight: 600;
  color: var(--bs-secondary-color);
  white-space: nowrap;
  flex: 0 0 auto;
}
.tb-crumb { flex: 1 1 auto; min-width: 0; }
.drop-zone.drop-active { outline: 2px dashed var(--bs-primary); outline-offset: -2px; }
</style>
