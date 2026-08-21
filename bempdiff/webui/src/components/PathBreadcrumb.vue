<script setup>
// 包路径面包屑（对标 Windows 10 文件管理器地址栏，语义与 PathBar 不同——PathBar 点击层级做差异树定位，
// 本组件点击层级做「路径导航」：把 v-model 更新为该层级的前缀路径，并高亮该层级）：
//  - 默认（未点击）状态：面包屑导航，按 / 与 \ 拆出各级目录/文件名；
//    点击第 i 级 → 路径即时更新为「从根到该级」的前缀（如 D:/a/b/c 点 b → D:/a/b），该级高亮；
//  - 点击空白处：切换为完整路径编辑框（纯文本，可查看/编辑/全选复制），Enter 确认、Esc/失焦恢复面包屑；
//  - 复制按钮：一键复制完整路径到剪贴板；
//  - 兼容 Windows 盘符（D: → D:/）与相对路径；disabled（AI 分析中）时全组件禁用。
import { ref, computed, nextTick } from 'vue'
import { toast } from '../store'

const props = defineProps({
  modelValue: { type: String, default: '' }, // 完整路径字符串（双向绑定）
  label: { type: String, default: '' },      // 语义提示（老包/新包/老目录/新目录），用于空态与复制 toast
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

// 归一化：\ → /（盘符、UNC 统一斜杠），便于拆段
const normPath = computed(() => (props.modelValue || '').replace(/\\/g, '/'))

/** 层级拆分：去尾斜杠后按 / 拆段（盘符 D: 作为独立一级；UNC 双斜杠的空段被滤除）。 */
const segments = computed(() => {
  const p = normPath.value.replace(/\/+$/, '')
  if (!p) return []
  return p.split('/').filter(Boolean)
})

const firstIsDrive = computed(() => /^[a-zA-Z]:$/.test(segments.value[0] || ''))

/** 点击第 i 级 → 前缀路径（盘符根规范化：D: → D:/）。 */
function prefixOf(i) {
  const parts = segments.value.slice(0, i + 1)
  let s = parts.join('/')
  if (parts.length === 1 && firstIsDrive.value) s = parts[0] + '/'
  return s
}

// 点击层级：导航到该前缀 + 短暂高亮（flash 该级，落点即路径尾段 pb-current 主色加粗）
const flashKey = ref('')
let flashTimer = null
function onSegClick(seg, i) {
  if (props.disabled) return
  const prefix = prefixOf(i)
  if (prefix !== props.modelValue) emit('update:modelValue', prefix)
  flashKey.value = seg
  if (flashTimer) clearTimeout(flashTimer)
  flashTimer = setTimeout(() => { flashKey.value = '' }, 900)
}

// ---------- 编辑模式（查看/编辑完整路径） ----------
const editing = ref(false)
const editText = ref('')
const inputRef = ref(null)
function enterEdit() {
  if (props.disabled) return
  editing.value = true
  editText.value = props.modelValue
  nextTick(() => { const el = inputRef.value; if (el) { el.focus(); el.select() } }) // 自动全选便于复制/覆盖
}
function exitEdit() { editing.value = false }
// 点击面包屑空白（非层级节点/非复制按钮）→ 进入编辑
function onBarClick(e) {
  if (editing.value || props.disabled) return
  if (e.target && e.target.closest && e.target.closest('.pb-seg, .pb-copy')) return
  enterEdit()
}
function commitEdit() {
  const v = editText.value.trim()
  exitEdit()
  if (v !== props.modelValue) emit('update:modelValue', v)
}
function onEditKey(e) {
  if (e.key === 'Enter') commitEdit()
  else if (e.key === 'Escape') exitEdit()
}

// ---------- 复制完整路径（clipboard API，降级 execCommand） ----------
async function copyPath() {
  const text = props.modelValue || ''
  if (!text) return
  let ok = false
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
      ok = true
    }
  } catch (e) { ok = false }
  if (!ok) {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try { ok = document.execCommand('copy') } catch (e) { ok = false }
    document.body.removeChild(ta)
  }
  toast('success', (props.label ? props.label + ' ' : '') + '路径已复制：' + text)
}
</script>

<template>
  <div class="pbc" :class="{ 'pbc-disabled': disabled }" :title="modelValue || '未设置路径'" @click="onBarClick">
    <Transition name="pb-swap" mode="out-in">
      <input v-if="editing" ref="inputRef" v-model="editText" class="pb-input"
             :title="modelValue" spellcheck="false" autocomplete="off"
             :disabled="disabled"
             @keydown="onEditKey" @blur="exitEdit" @click.stop />
      <div v-else class="pb-crumbs">
        <template v-for="(seg, i) in segments" :key="i">
          <i v-if="i > 0" class="bi bi-chevron-right pb-sep"></i>
          <button type="button" class="pb-seg"
                  :class="{
                    'pb-current': i === segments.length - 1,
                    'pb-flash': seg === flashKey,
                    'pb-drive': i === 0 && firstIsDrive
                  }"
                  :title="prefixOf(i)" :disabled="disabled"
                  @click.stop="onSegClick(seg, i)">
            <i v-if="i === 0 && firstIsDrive" class="bi bi-hdd"></i>{{ seg }}
          </button>
        </template>
        <span v-if="!segments.length" class="pb-empty">{{ label ? '未设置' + label + '路径' : '未设置路径' }}</span>
        <button v-if="modelValue" type="button" class="pb-copy" :disabled="disabled"
                title="复制完整路径" @click.stop="copyPath">
          <i class="bi bi-clipboard"></i>
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* 容器：Win10 地址栏样式 —— 浅底圆角、hover 描边、focus 主色光圈 */
.pbc {
  display: inline-flex;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  background: var(--bs-body-bg);
  border: 1px solid var(--bs-border-color);
  border-radius: .375rem;
  padding: .08rem .35rem;
  cursor: text;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.pbc:hover { border-color: var(--bs-secondary-color); }
.pbc:focus-within { border-color: var(--bs-primary); box-shadow: 0 0 0 .15rem rgba(var(--bs-primary-rgb), .18); }
.pbc-disabled, .pbc-disabled:hover { border-color: var(--bs-border-color); cursor: default; opacity: .75; }

/* 面包屑区：横向可滚动（长路径不换行） */
.pb-crumbs {
  display: flex;
  align-items: center;
  overflow-x: auto;
  scrollbar-width: thin;
  max-width: 100%;
}
/* 层级节点 */
.pb-seg {
  border: none;
  background: transparent;
  font-family: var(--bs-font-monospace);
  font-size: .76rem;
  line-height: 1.5;
  color: var(--bs-secondary-color);
  padding: 0 .3rem;
  border-radius: 3px;
  white-space: nowrap;
  flex: 0 0 auto;
  cursor: pointer;
}
.pb-seg:hover { background: rgba(var(--bs-primary-rgb), .12); color: var(--bs-primary); }
.pb-seg:disabled { cursor: default; }
/* 盘符根：图标 + 加粗 */
.pb-drive { display: inline-flex; align-items: center; gap: .22rem; font-weight: 600; }
.pb-drive i { font-size: .72rem; }
/* 当前层级（路径尾段）：主色加粗，标识所在层级 */
.pb-current { color: var(--bs-body-color); font-weight: 600; }
/* 点击层级后的落点高亮：主色淡底淡出动画（层级跳转反馈） */
.pb-flash { animation: pbFlash .9s ease-out; }
@keyframes pbFlash {
  0% { background: rgba(var(--bs-primary-rgb), .28); color: var(--bs-primary); }
  100% { background: transparent; color: var(--bs-body-color); }
}
/* 层级分隔符 */
.pb-sep { font-size: .5rem; color: var(--bs-secondary-color); opacity: .75; flex: 0 0 auto; }
.pb-empty { font-size: .76rem; color: var(--bs-secondary-color); padding: 0 .3rem; white-space: nowrap; }
/* 复制按钮：hover 显示主色 */
.pb-copy {
  border: none;
  background: transparent;
  color: var(--bs-secondary-color);
  font-size: .7rem;
  padding: .1rem .25rem;
  border-radius: 3px;
  flex: 0 0 auto;
  cursor: pointer;
  margin-left: auto;
}
.pb-copy:hover { color: var(--bs-primary); background: rgba(var(--bs-primary-rgb), .12); }

/* 编辑模式：透明无边框输入框，等宽字体显示完整路径 */
.pb-input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--bs-font-monospace);
  font-size: .78rem;
  color: var(--bs-body-color);
  padding: .05rem .1rem;
}

/* 面包屑 ↔ 编辑框切换动画 */
.pb-swap-enter-active, .pb-swap-leave-active { transition: opacity .12s ease, transform .12s ease; }
.pb-swap-enter-from, .pb-swap-leave-to { opacity: 0; transform: translateY(2px); }
</style>
