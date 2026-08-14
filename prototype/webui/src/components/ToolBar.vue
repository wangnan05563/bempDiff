<script setup>
import { ref } from 'vue'
import { state, runCompare, generateReport, downloadExport, toast } from '../store'
import { isTauri, pickPath } from '../lib/tauri'

const props = defineProps({
  onOpenConfig: { type: Function, required: true },
  onOpenReport: { type: Function, required: true }
})

const leftType = ref('package')
const oldPath = ref('sample_v1.war')
const newPath = ref('sample_v2.war')
const showExport = ref(false)

function onReport(ai) { showExport.value = false; generateReport(ai) }
function onExport() { showExport.value = false; downloadExport() }

function assignPath(which, val) {
  if (which === 'old') oldPath.value = val
  else newPath.value = val
}

// Tauri 模式：原生对话框直接拿绝对路径（免上传）；浏览器模式：提示手动输入服务器本机绝对路径。
async function browse(which) {
  const sel = await pickPath({ directory: leftType.value === 'folder' })
  if (sel) {
    assignPath(which, Array.isArray(sel) ? sel[0] : sel)
    return
  }
  if (!isTauri()) {
    toast('info', '浏览器模式下请直接输入服务器本机绝对路径；打包为 Tauri 后可用原生对话框选择')
  }
}

function buildOptions() {
  const c = state.config || {}
  return {
    expandAll: !!c.expandAll,
    topK: c.topK ?? 12,
    internalPrefixes: c.internalPrefixes || '',
    cfrJar: c.cfrJar || ''
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
  <div class="bg-light border-bottom px-3 py-2 d-flex flex-wrap align-items-end gap-2">
    <div class="form-check form-check-inline me-1">
      <input class="form-check-input" type="radio" id="t-pkg" value="package" v-model="leftType">
      <label class="form-check-label" for="t-pkg">包</label>
    </div>
    <div class="form-check form-check-inline me-2">
      <input class="form-check-input" type="radio" id="t-folder" value="folder" v-model="leftType">
      <label class="form-check-label" for="t-folder">目录</label>
    </div>

    <div class="input-group input-group-sm" style="max-width:340px">
      <span class="input-group-text">{{ leftType === 'folder' ? '老目录' : '老包(生产)' }}</span>
      <input class="form-control" v-model="oldPath" :placeholder="leftType==='folder' ? 'D:/path/old' : 'sample_v1.war'">
      <button class="btn btn-outline-secondary" @click="browse('old')" title="浏览"><i class="bi bi-folder2-open"></i></button>
    </div>
    <div class="input-group input-group-sm" style="max-width:340px">
      <span class="input-group-text">{{ leftType === 'folder' ? '新目录' : '新包(下发)' }}</span>
      <input class="form-control" v-model="newPath" :placeholder="leftType==='folder' ? 'D:/path/new' : 'sample_v2.war'">
      <button class="btn btn-outline-secondary" @click="browse('new')" title="浏览"><i class="bi bi-folder2-open"></i></button>
    </div>

    <button class="btn btn-primary btn-sm" @click="doCompare"><i class="bi bi-arrow-left-right"></i> 开始比对</button>

    <div class="ms-auto d-flex gap-2">
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenReport" :disabled="!state.reportMd">
        <i class="bi bi-filetype-md"></i> 查看报告
      </button>
      <div class="position-relative">
        <button class="btn btn-outline-secondary btn-sm" @click="showExport = !showExport" :disabled="!state.job">
          <i class="bi bi-file-earmark-arrow-down"></i> 导出
        </button>
        <ul class="dropdown-menu dropdown-menu-end show py-1" v-if="showExport"
            style="position:absolute;right:0;top:100%;z-index:1000">
          <li><a class="dropdown-item" href="#" @click.prevent="onReport(false)"><i class="bi bi-filetype-md"></i> 生成报告</a></li>
          <li><a class="dropdown-item" href="#" @click.prevent="onReport(true)"><i class="bi bi-cpu"></i> 生成报告(AI)</a></li>
          <li><a class="dropdown-item" href="#" @click.prevent="onExport"><i class="bi bi-box-seam"></i> 导出差异资产(zip)</a></li>
        </ul>
      </div>
      <button class="btn btn-outline-secondary btn-sm" @click="onOpenConfig"><i class="bi bi-gear"></i> 设置</button>
    </div>
  </div>
</template>
