<script setup>
import { ref, watch, computed } from 'vue'
import { state, runCompare, generateReport, downloadExport, toast, applyTheme } from '../store'
import { isTauri, isElectron, pickPath } from '../lib/tauri'

const props = defineProps({
  onOpenConfig: { type: Function, required: true },
  onOpenReport: { type: Function, required: true },
  onOpenAi: { type: Function, required: true }
})

const leftType = ref('package')
const oldPath = ref('sample_v1.war')
const newPath = ref('sample_v2.war')
const showExport = ref(false)

// 实时把当前输入同步到 store，状态栏在未比对时也能显示“版本”预览
watch([oldPath, newPath], ([o, n]) => {
  state.compareInputs = { oldPath: o || '', newPath: n || '' }
}, { immediate: true })

function onReport(ai) { showExport.value = false; generateReport(ai) }
function onExport() { showExport.value = false; downloadExport() }

function assignPath(which, val) {
  if (which === 'old') oldPath.value = val
  else newPath.value = val
}

// 主题切换：整合进工具栏（原在 TopBar 内）
const isDark = computed(() => state.theme === 'dark')
function toggleTheme() { applyTheme(isDark.value ? 'light' : 'dark') }

// 桌面壳（Electron / Tauri）：原生对话框直接拿绝对路径（免上传）；浏览器模式：提示手动输入服务器本机绝对路径。
async function browse(which) {
  const sel = await pickPath({ directory: leftType.value === 'folder' })
  if (sel) {
    assignPath(which, Array.isArray(sel) ? sel[0] : sel)
    return
  }
  if (!isTauri() && !isElectron()) {
    toast('info', '浏览器模式下请直接输入服务器本机绝对路径；打包桌面壳（Electron）后可点击此按钮用原生对话框选择')
  }
}

function buildOptions() {
  const c = state.config || {}
  return {
    expandAll: !!c.expandAll,
    topK: c.topK ?? 12,
    internalPrefixes: c.internalPrefixes || '',
    cfrJar: c.cfrJar || '',
    ignoreWhitespace: !!c.ignoreWhitespace,
    ignoreComments: !!c.ignoreComments,
    ignoreRegex: c.ignoreRegex || ''
  }
}

function doCompare() {
  if (!oldPath.value || !newPath.value) {
    toast('warning', '请先填写老包与新包（或目录）的路径')
    return
  }
  runCompare({
    leftType: leftType.value,
    leftPath: oldPath.value.trim(),
    rightPath: newPath.value.trim(),
    options: buildOptions()
  })
}
</script>

<template>
  <div class="bg-body-tertiary border-bottom px-2 py-1 d-flex flex-wrap align-items-end gap-2">
    <div class="form-check form-check-inline me-1">
      <input class="form-check-input" type="radio" id="t-pkg" value="package" v-model="leftType" title="以单个 war/jar 包作为输入（默认）">
      <label class="form-check-label" for="t-pkg" title="以单个 war/jar 包作为输入（默认）">包</label>
    </div>
    <div class="form-check form-check-inline me-2">
      <input class="form-check-input" type="radio" id="t-folder" value="folder" v-model="leftType" title="以解压后的目录作为输入，对比目录结构的差异">
      <label class="form-check-label" for="t-folder" title="以解压后的目录作为输入，对比目录结构的差异">目录</label>
    </div>

    <div class="input-group input-group-sm" style="max-width:240px">
      <span class="input-group-text" :title="leftType === 'folder' ? '老目录：作为对比基准的目录' : '老包(生产)：当前生产环境运行的版本，作为对比基准'">{{ leftType === 'folder' ? '老目录' : '老包(生产)' }}</span>
      <input class="form-control" v-model="oldPath" :placeholder="leftType==='folder' ? 'D:/path/old' : 'sample_v1.war'">
      <button class="btn btn-outline-secondary" @click="browse('old')" :title="leftType === 'folder' ? '浏览选择老目录（桌面壳可用原生对话框，浏览器模式请手填路径）' : '浏览选择老包（桌面壳可用原生对话框，浏览器模式请手填路径）'"><i class="bi bi-folder2-open"></i></button>
    </div>
    <div class="input-group input-group-sm" style="max-width:240px">
      <span class="input-group-text" :title="leftType === 'folder' ? '新目录：本次要对比的目标目录' : '新包(下发)：本次要下发的版本，作为对比目标'">{{ leftType === 'folder' ? '新目录' : '新包(下发)' }}</span>
      <input class="form-control" v-model="newPath" :placeholder="leftType==='folder' ? 'D:/path/new' : 'sample_v2.war'">
      <button class="btn btn-outline-secondary" @click="browse('new')" :title="leftType === 'folder' ? '浏览选择新目录（桌面壳可用原生对话框，浏览器模式请手填路径）' : '浏览选择新包（桌面壳可用原生对话框，浏览器模式请手填路径）'"><i class="bi bi-folder2-open"></i></button>
    </div>

    <button class="btn btn-primary btn-sm" @click="doCompare" title="加载两个包/目录，反编译并生成差异树（耗时与包大小相关）"><i class="bi bi-arrow-left-right"></i> 开始比对</button>

    <div class="ms-auto d-flex gap-2">
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenReport" :disabled="!state.reportMd" title="查看最近一次生成的差异/AI 分析报告">
        <i class="bi bi-filetype-md"></i> 查看报告
      </button>
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenAi" :disabled="!state.job" title="调用 AI 流式分析本次比对差异，实时逐字展示结论与思考过程">
        <i class="bi bi-cpu"></i> AI 分析
      </button>
      <div class="position-relative">
        <button class="btn btn-outline-secondary btn-sm" @click="showExport = !showExport" :disabled="!state.job" title="导出差异报告或差异资产">
          <i class="bi bi-file-earmark-arrow-down"></i> 导出
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
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenConfig" title="打开配置中心：模型、解析与导出、差异树过滤、界面与高级"><i class="bi bi-gear"></i> 设置</button>
    </div>
  </div>
</template>
