<script setup>
// 图标按钮 + 延迟 Tooltip（气泡提示）。
// 通用小组件：树工具栏「全部展开/全部折叠」等纯图标按钮复用。
//  - icon     : Bootstrap Icons 类名（如 'bi-plus-square'）
//  - tooltip  : 悬浮提示文案（自定义；默认置空则不显示气泡）
//  - delay    : 悬浮后延迟显示毫秒数（默认 400）
//  - disabled : 禁用态（不触发 click；悬浮提示仍显示，便于说明原因）
//  - ariaLabel: 无障碍标签（缺省回落 tooltip）
import { ref, onBeforeUnmount } from 'vue'

const props = defineProps({
  icon: { type: String, default: '' },
  tooltip: { type: String, default: '' },
  delay: { type: Number, default: 400 },
  disabled: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '' }
})
const emit = defineEmits(['click'])

const show = ref(false)
let timer = null

function onEnter() {
  clearTimeout(timer)
  timer = setTimeout(() => { show.value = true }, props.delay)
}
function onLeave() {
  clearTimeout(timer)
  show.value = false
}
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="tip-btn position-relative">
    <button type="button"
            class="btn btn-outline-secondary btn-sm d-inline-flex align-items-center justify-content-center"
            :class="{ disabled }"
            :disabled="disabled"
            :aria-label="ariaLabel || tooltip"
            @mouseenter="onEnter"
            @mouseleave="onLeave"
            @focus="onEnter"
            @blur="onLeave"
            @click="emit('click')">
      <i class="bi" :class="icon" aria-hidden="true"></i>
    </button>
    <Transition name="tip-fade">
      <div v-if="show && tooltip" class="tip-bubble" role="tooltip">{{ tooltip }}</div>
    </Transition>
  </div>
</template>

<style scoped>
.tip-btn { display: inline-flex; }
.tip-btn button { width: 1.85rem; height: 1.85rem; padding: 0; }
/* 气泡：显示于按钮上方居中，深色底 + 箭头，避免被相邻元素遮挡 */
.tip-bubble {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--bs-body-color);
  color: var(--bs-body-bg);
  padding: .22rem .55rem;
  border-radius: .3rem;
  font-size: .72rem;
  line-height: 1.3;
  white-space: nowrap;
  max-width: 16rem;
  z-index: 1100;
  pointer-events: none;
  box-shadow: 0 .15rem .4rem rgba(0, 0, 0, .18);
}
.tip-bubble::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: var(--bs-body-color);
}
.tip-fade-enter-active,
.tip-fade-leave-active { transition: opacity .15s ease, transform .15s ease; }
.tip-fade-enter-from,
.tip-fade-leave-to { opacity: 0; transform: translateX(-50%) translateY(2px); }
</style>
