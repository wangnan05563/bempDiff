<script setup>
// R6 快捷键速查面板（二期 T01465）：按作用域分组的全局速查，「?」唤起、Esc 或点击遮罩关闭。
import { onMounted, onBeforeUnmount } from 'vue'
import { SHORTCUTS, groupByScope } from '../lib/shortcuts'

const emit = defineEmits(['close'])
const groups = groupByScope(SHORTCUTS)

function onKey(e) {
  if (e.key === 'Escape' || e.key === '?') {
    e.preventDefault()
    emit('close')
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="modal-backdrop" @click="emit('close')">
    <div class="shortcut-panel shadow" role="dialog" aria-label="快捷键速查" @click.stop>
      <div class="d-flex justify-content-between align-items-center mb-2">
        <span style="font-weight:600"><i class="bi bi-keyboard me-1"></i>快捷键速查</span>
        <button class="btn btn-sm btn-outline-secondary py-0 px-2" aria-label="关闭" @click="emit('close')">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      <div v-for="g in groups" :key="g.scope" class="mb-2">
        <div class="sc-scope text-secondary" style="font-size:.7rem">{{ g.scope }}</div>
        <div v-for="s in g.items" :key="s.keys + s.label" class="sc-row d-flex justify-content-between align-items-center">
          <span class="sc-label">{{ s.label }}</span>
          <kbd class="sc-keys">{{ s.keys }}</kbd>
        </div>
      </div>
      <div class="text-secondary" style="font-size:.68rem">
        输入框内不劫持全局快捷键；Ctrl+A / Ctrl+C 仅在无原生文本选区时接管。
      </div>
    </div>
  </div>
</template>

<style scoped>
.shortcut-panel {
  position: fixed; top: 12%; left: 50%; transform: translateX(-50%);
  width: min(30rem, 92vw); max-height: 70vh; overflow: auto;
  background: var(--bs-body-bg); border: 1px solid var(--bs-border-color);
  border-radius: .6rem; padding: .9rem 1rem; z-index: 2000;
  font-size: .8rem;
}
.sc-scope { border-bottom: 1px dashed var(--bs-border-color); margin-bottom: .25rem; padding-bottom: .1rem; }
.sc-row { padding: .15rem 0; }
.sc-keys {
  font-family: var(--bs-font-monospace); font-size: .72rem;
  border: 1px solid var(--bs-border-color); border-radius: .3rem;
  padding: .05rem .4rem; background: var(--bs-tertiary-bg);
  white-space: nowrap;
}
.sc-label { min-width: 0; }
</style>
