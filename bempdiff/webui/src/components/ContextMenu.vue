<script setup>
// 通用右键菜单（BCompare 风格）：Teleport 到 body、fixed 定位、视口边缘自动翻转。
// 交互符合桌面应用惯例：
//   - 键盘：↑/↓ 循环导航（跳过禁用项）、Home/End 首尾、Enter/Space 执行、Esc 关闭、Tab 焦点陷阱
//   - 焦点：打开时聚焦首个可用项；关闭时焦点还原到触发元素
//   - 外部点击 / 滚动 / 窗口失焦 → 关闭
// items: [{ id, label, icon, disabled, title, group }]（group 变化处渲染分隔线）
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  items: { type: Array, default: () => [] }
})
const emit = defineEmits(['select', 'close'])

const rootEl = ref(null)
const itemEls = ref([])
let triggerEl = null // 打开时的焦点来源（关闭时还原）

const pos = ref({ left: 0, top: 0 })

function focusIndex(i) {
  const els = itemEls.value.filter(Boolean)
  if (!els.length) return
  const n = els.length
  let idx = i
  if (idx < 0) idx = n - 1
  if (idx >= n) idx = 0
  // 跳过禁用项（最多绕一圈）
  for (let k = 0; k < n; k++) {
    const el = els[idx]
    if (!el.getAttribute('aria-disabled')) {
      el.focus()
      return
    }
    idx = (idx + 1) % n
  }
}

function firstEnabledIndex() {
  const els = itemEls.value.filter(Boolean)
  for (let i = 0; i < els.length; i++) {
    if (!els[i].getAttribute('aria-disabled')) return i
  }
  return -1
}

function layout() {
  if (!rootEl.value) return
  const r = rootEl.value.getBoundingClientRect()
  const w = r.width || 220
  const h = r.height || 300
  const pad = 8
  let left = Math.min(props.x, window.innerWidth - w - pad)
  let top = Math.min(props.y, window.innerHeight - h - pad)
  left = Math.max(pad, left)
  top = Math.max(pad, top)
  pos.value = { left, top }
}

watch(() => props.visible, async (v) => {
  if (v) {
    triggerEl = document.activeElement
    await nextTick()
    layout()
    const fi = firstEnabledIndex()
    itemEls.value.forEach((el, i) => { if (el) el.tabIndex = (i === fi) ? 0 : -1 })
    if (fi >= 0 && itemEls.value[fi]) itemEls.value[fi].focus()
  } else {
    // 焦点还原到触发元素
    if (triggerEl && typeof triggerEl.focus === 'function') triggerEl.focus()
    triggerEl = null
  }
}, { immediate: true })

function onKeydown(e) {
  if (!props.visible) return
  const els = itemEls.value.filter(Boolean)
  const cur = document.activeElement
  const curIdx = els.indexOf(cur)
  const n = els.length
  switch (e.key) {
    case 'ArrowDown': e.preventDefault(); focusIndex((curIdx < 0 ? -1 : curIdx) + 1); break
    case 'ArrowUp': e.preventDefault(); focusIndex((curIdx < 0 ? n : curIdx) - 1); break
    case 'Home': e.preventDefault(); focusIndex(0); break
    case 'End': e.preventDefault(); focusIndex(n - 1); break
    case 'Enter':
    case ' ': {
      const el = els[curIdx]
      if (el && !el.getAttribute('aria-disabled')) {
        e.preventDefault()
        selectByIndex(curIdx)
      }
      break
    }
    case 'Escape': e.preventDefault(); emit('close'); break
    case 'Tab':
      // 焦点陷阱：Tab 在菜单内循环
      e.preventDefault()
      focusIndex(e.shiftKey ? (curIdx - 1) : (curIdx + 1))
      break
  }
}

function selectByIndex(i) {
  const item = props.items[i]
  if (!item || item.disabled) return
  emit('select', item)
  emit('close')
}

function onDocMousedown(e) {
  if (!props.visible) return
  if (rootEl.value && !rootEl.value.contains(e.target)) emit('close')
}
function onDocScroll(e) {
  // 菜单自身内部滚动不关闭；外部滚动关闭
  if (rootEl.value && rootEl.value.contains(e.target)) return
  if (props.visible) emit('close')
}
function onBlur(e) {
  // 焦点移出菜单（含移动到其它窗口）→ 关闭（延迟，让菜单内 Tab 循环先接管）
  setTimeout(() => {
    if (!rootEl.value) return
    if (!rootEl.value.contains(document.activeElement)) emit('close')
  }, 0)
}

onMounted(() => {
  document.addEventListener('mousedown', onDocMousedown)
  document.addEventListener('scroll', onDocScroll, true)
  window.addEventListener('blur', onBlur)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown)
  document.removeEventListener('scroll', onDocScroll, true)
  window.removeEventListener('blur', onBlur)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" ref="rootEl" class="cm-menu" role="menu"
         :style="{ left: pos.left + 'px', top: pos.top + 'px' }"
         @keydown="onKeydown">
      <template v-for="(it, i) in items" :key="it.id || i">
        <div v-if="i > 0 && items[i - 1].group !== it.group" class="cm-divider" role="separator"></div>
        <button v-if="!it.divider" type="button" role="menuitem"
                class="cm-item d-flex align-items-center gap-2"
                :class="{ disabled: it.disabled }"
                :aria-disabled="it.disabled || undefined"
                :title="it.title || undefined"
                :ref="el => itemEls[i] = el"
                @click="selectByIndex(i)">
          <i class="bi cm-icon" :class="it.icon" aria-hidden="true"></i>
          <span class="cm-label flex-1 text-truncate">{{ it.label }}</span>
          <span v-if="it.disabled" class="cm-reason text-truncate">{{ it.title }}</span>
        </button>
      </template>
    </div>
  </Teleport>
</template>

<style scoped>
.cm-menu {
  position: fixed;
  z-index: 2000;
  min-width: 15rem;
  max-width: 24rem;
  padding: .3rem 0;
  background: var(--bs-body-bg);
  border: 1px solid var(--bs-border-color);
  border-radius: .375rem;
  box-shadow: 0 .5rem 1rem rgba(0, 0, 0, .18);
  font-size: .82rem;
  outline: none;
}
.cm-divider {
  height: 1px;
  margin: .25rem .5rem;
  background: var(--bs-border-color);
}
.cm-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: .3rem .75rem;
  border: 0;
  background: transparent;
  color: var(--bs-body-color);
  text-align: left;
  white-space: nowrap;
}
.cm-item:hover:not(.disabled), .cm-item:focus-visible {
  background: var(--bs-primary-bg-subtle);
  color: var(--bs-primary-text-emphasis);
  outline: none;
}
.cm-item.disabled {
  color: var(--bs-secondary-color);
  cursor: not-allowed;
  opacity: .85;
}
.cm-item.disabled .cm-reason {
  max-width: 9rem;
  font-size: .68rem;
  color: var(--bs-secondary-color);
  opacity: .75;
}
.cm-icon { width: 1.05rem; text-align: center; flex: 0 0 auto; }
.cm-label { overflow: hidden; }
</style>
