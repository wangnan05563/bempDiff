<script setup>
// 全屏文件拖拽遮罩：文件拖入窗口任意位置时弹出全屏遮罩，中央提示本次应填入的包类型
// （请拖入老包 / 请拖入新包），松开后自动解析路径填入对应字段并按「奇老偶新」循环。
//
// 状态机见 lib/dropCycle.js：路径未填→老包；老包已填新包空→新包；都填(或再拖)→回老包。
// 时序：
//   dragenter → 弹出遮罩并据 cycle.peek() 显示提示；dragover 维持；dragleave 计数归零收起；
//   drop      → 收起遮罩，取 cycle.claim() 得目标类型，提取 file.path 填入对应字段。
// 用 depth 层级计数避免子元素进出导致遮罩闪烁；window capture 阶段接管，防止与工具栏按侧拖放双重触发。
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { state, inferType, triggerCompare, toast } from '../store'
import { createDropCycle } from '../lib/dropCycle'

const active = ref(false)   // 遮罩是否显示（true 时覆盖全屏）
const target = ref('old')   // 本次拖拽对应的包类型：'old' | 'new'
const depth = ref(0)        // 嵌套 dragenter/dragleave 层级计数
const cycle = createDropCycle(() => state.oldPath, () => state.newPath)

// 仅响应文件拖拽（排除拖文本/链接/选中内容）
function hasFiles(e) {
  const types = e && e.dataTransfer && e.dataTransfer.types
  return !!types && Array.from(types).includes('Files')
}

// 每次成为一个新的连续拖拽（depth 由 0→1）时，读取下一次目标用于遮罩提示，避免层级内反复刷新提示。
function onDragEnter(e) {
  if (!hasFiles(e)) return
  e.preventDefault()
  e.stopPropagation()
  if (depth.value === 0) { target.value = cycle.peek() }
  depth.value++
  active.value = true
}

function onDragOver(e) {
  if (!hasFiles(e)) return
  e.preventDefault() // 必须 preventDefault 才允许 drop
  e.stopPropagation()
}

function onDragLeave(e) {
  if (!hasFiles(e)) return
  e.preventDefault()
  e.stopPropagation()
  depth.value = Math.max(0, depth.value - 1)
  if (depth.value === 0) active.value = false
}

// 落地：单次处理（drop 后立即归零 depth+收起遮罩）；复用既有 file.path 提取方式。
function onDrop(e) {
  if (!hasFiles(e)) return
  e.preventDefault()
  e.stopPropagation()
  depth.value = 0
  active.value = false
  const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  const path = file && file.path
  if (!path) { toast('warning', '未能读取拖入文件的路径，请使用桌面壳（Electron）拖入'); return }
  const slot = cycle.claim() // 推进奇偶位：下一次自动切换为另一包类型
  assign(slot, path)
}

// 填入对应字段（复用 store.inferType 判定 package/folder；老侧同步 leftType）
function assign(slot, path) {
  if (slot === 'old') { state.oldPath = path; state.leftType = inferType(path) }
  else state.newPath = path
  // 两路径齐备即自动开始比对（与工具栏既有「拖拽即比」口径一致）
  if (state.oldPath && state.newPath) triggerCompare()
}

function onWindowDragEnter(e) { onDragEnter(e) }
function onWindowDragOver(e) { onDragOver(e) }
function onWindowDragLeave(e) { onDragLeave(e) }
function onWindowDrop(e) { onDrop(e) }

onMounted(() => {
  // capture 阶段监听：遮罩接管整个窗口的文件拖放，避免事件冒泡触发工具栏的按侧放置
  window.addEventListener('dragenter', onWindowDragEnter, true)
  window.addEventListener('dragover', onWindowDragOver, true)
  window.addEventListener('dragleave', onWindowDragLeave, true)
  window.addEventListener('drop', onWindowDrop, true)
})
onBeforeUnmount(() => {
  window.removeEventListener('dragenter', onWindowDragEnter, true)
  window.removeEventListener('dragover', onWindowDragOver, true)
  window.removeEventListener('dragleave', onWindowDragLeave, true)
  window.removeEventListener('drop', onWindowDrop, true)
})
</script>

<template>
  <div v-if="active" class="drop-mask" aria-hidden="true">
    <div class="drop-mask-box">
      <i class="bi" :class="target === 'old' ? 'bi-box-arrow-down-left' : 'bi-box-arrow-down-right'"></i>
      <div class="drop-mask-title">{{ target === 'old' ? '请拖入老包' : '请拖入新包' }}</div>
      <div class="drop-mask-sub">
        {{ target === 'old' ? '生产当前运行的版本（对比基准）' : '本次要下发的版本（对比目标）' }}
      </div>
      <div class="drop-mask-tip">松手后自动填入{{ target === 'old' ? '老包' : '新包' }}路径</div>
    </div>
  </div>
</template>

<style scoped>
/* 全屏遮罩：覆盖整个拖拽区域；关闭（active=false）后恢复正常页面展示 */
.drop-mask {
  position: fixed; inset: 0; z-index: 2400;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(2px);
  display: flex; align-items: center; justify-content: center;
}
.drop-mask-box {
  text-align: center; color: #fff; padding: 2.5rem 4rem;
  border-radius: 1rem; border: 2px dashed rgba(255, 255, 255, 0.7);
  background: rgba(0, 0, 0, 0.35);
}
.drop-mask-box i { font-size: 3rem; display: block; margin-bottom: .5rem; }
.drop-mask-title { font-size: 1.6rem; font-weight: 600; }
.drop-mask-sub { font-size: .9rem; opacity: .85; margin-top: .25rem; }
.drop-mask-tip { font-size: .78rem; opacity: .7; margin-top: .5rem; }
</style>