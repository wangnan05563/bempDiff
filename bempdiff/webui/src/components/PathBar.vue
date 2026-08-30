<script setup>
// 路径展示组件（对标 Windows 10 文件管理器地址栏）：
//  - 默认（未点击）状态：面包屑导航，各层级为可点击节点，点击目录段在差异树中定位、点击文件段打开比对；
//  - 点击空白处：切换为完整路径编辑框（纯文本，可查看/复制/编辑），Enter 确认跳转、Esc/失焦恢复面包屑；
//  - 悬浮提示：两种模式下 title 均为完整路径，长路径/溢出时鼠标悬浮即可查看。
// 路径形态：相对 key（包内路径，如 WEB-INF/lib/x.jar 或归档复合键 outer!/inner）；
// folder 模式可传 rootPath（磁盘根）→ 编辑框显示绝对路径。
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { state, selectEntry, toast, locateTreePrefix } from '../store'

const props = defineProps({
  path: { type: String, default: '' },      // 相对 key
  rootPath: { type: String, default: '' },  // folder 模式磁盘根（按差异状态选侧后的根）
  node: { type: Object, default: null }     // 当前树节点（可选，用于状态/类型提示）
})

// 拆段：/ 与 ! 均为层级分隔（归档复合键 outer!/inner 也逐级显示）
const segments = computed(() => (props.path || '').split(/[\/!]/).filter(Boolean))

const isFolderMode = computed(() => !!(state.job && state.job.mode === 'folder'))

// 完整路径：folder 模式 = 磁盘根 + 相对 key；package 模式 = 相对 key（本身即完整）
const fullPath = computed(() => {
  const p = props.path || ''
  if (props.rootPath) return props.rootPath.replace(/[\\/]+$/, '') + '/' + p
  return p
})

// 面包屑首节点（Win10 地址栏第一段）：folder → 盘符；package → 包根
const rootLabel = computed(() => {
  if (props.rootPath) {
    const m = props.rootPath.match(/^[a-zA-Z]:/)
    return m ? m[0] : '根目录'
  }
  return '包根'
})
const rootTitle = computed(() => (isFolderMode.value ? (props.rootPath || '') : '压缩包内根目录'))

// ---------- 编辑模式 ----------
const editing = ref(false)
const editText = ref('')
const inputRef = ref(null)

function enterEdit() {
  editing.value = true
  editText.value = fullPath.value
  nextTick(() => {
    const el = inputRef.value
    if (el) { el.focus(); el.select() } // 自动全选，便于直接复制/覆盖
  })
}
function exitEdit() { editing.value = false }

// 点击路径栏空白区域 → 编辑模式（Win10：点击地址栏空白切换为完整路径编辑）
function onBarClick(e) {
  if (editing.value) return
  if (e.target && e.target.closest && e.target.closest('.pb-seg')) return
  enterEdit()
}

// 目录段（非最后一段）→ 差异树定位；文件段（最后一段）→ 打开比对
function onSegClick(seg, i) {
  if (i === segments.value.length - 1) {
    if (props.path) selectEntry(props.path)
  } else {
    const prefix = segments.value.slice(0, i + 1).join('/')
    locateTreePrefix(prefix)
  }
}
function onRootClick() { locateTreePrefix('') } // 包根/盘符 → 定位到树顶部

function commitEdit() {
  const v = editText.value.trim()
  exitEdit()
  if (!v) return
  // folder 模式：编辑框内容为绝对路径 → 还原为相对 key 再匹配差异树
  let rel = v.replace(/\\/g, '/')
  if (props.rootPath) {
    const root = props.rootPath.replace(/[\\/]+$/, '').replace(/\\/g, '/')
    if (rel === root) { locateTreePrefix(''); return }
    if (rel.startsWith(root + '/')) rel = rel.slice(root.length + 1)
  }
  rel = rel.replace(/^\/+/, '')
  const tree = (state.job && state.job.tree) || []
  if (tree.some(n => n.key === rel)) { selectEntry(rel); return }        // 精确文件 → 打开
  if (tree.some(n => n.key.startsWith(rel + '/'))) { locateTreePrefix(rel); return } // 目录前缀 → 定位
  toast('warning', '未找到该路径（请输入差异树中的相对路径）')
}

function onEditKey(e) {
  if (e.key === 'Enter') commitEdit()
  else if (e.key === 'Escape') exitEdit()
}

// ======================================================================
// 溢出反馈：解决「长路径被截断、用户不知道还能往哪边滚」的问题（纯展示增强）
//  1) canLeft/canRight：按当前 scrollLeft 判定左右是否还有可滚动空间 → 驱动两侧渐隐遮罩，
//     让「还可继续滚动」的提示可见；
//  2) autoRevealCurrent：切换文件后把「当前文件段」自动滚入可视区右缘，保证用户第一眼
//     就能看到当前所处层级，而不是被截断在最右。
//  3) 触控反馈：滚动容器加 touch-action: pan-x，移动端可横向滑动查看完整路径。
//  不改动任何点击 / 编辑 / 定位逻辑，避免破坏既有交互。
// ======================================================================
const crumbsRef = ref(null)
const canLeft = ref(false)
const canRight = ref(false)
let pbResize = null

function updateOverflow() {
  const el = crumbsRef.value
  if (!el) { canLeft.value = false; canRight.value = false; return }
  // scrollLeft 有浮点精度波动，留 1px 容差判定是否还有空间
  canLeft.value = el.scrollLeft > 1
  canRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
}

/** 把当前文件段（最后一个 .pb-current 节点）滚入可视区域右缘，避免当前层级被截断。 */
function autoRevealCurrent() {
  const el = crumbsRef.value
  if (!el) return
  const cur = el.querySelector('.pb-current')
  if (!cur) return
  // 仅当确实溢出时才滚动，避免小幅抖动
  if (el.scrollWidth <= el.clientWidth + 1) return
  el.scrollTo({ left: cur.offsetLeft + cur.offsetWidth - el.clientWidth, behavior: 'smooth' })
  updateOverflow()
}

// 路径/段数变化（换文件）后：当前段自动入视；等 DOM 就绪后再量溢出
const revealCb = () => nextTick(() => { autoRevealCurrent(); updateOverflow() })
// 换文件（props.path 变化）或从编辑态切回面包屑时，重新定位当前段并刷新遮罩
watch(() => [props.path, editing.value], revealCb)

// 编辑态（input）/面包屑（crumbs）二选一：进入编辑态 crumbs 节点销毁、退出时挂载新节点，
// 若只在 onMounted observe 一次，后续新节点不再被监听，容器尺寸变化时遮罩失效。
// 用 watch(crumbsRef) 在节点变化时 disconnect+reobserve，保证遮罩始终跟随当前节点。
watch(crumbsRef, (el, oldEl) => {
  if (pbResize) {
    if (oldEl) pbResize.unobserve(oldEl)
    if (el) pbResize.observe(el)
    else { pbResize.disconnect(); pbResize = null }
  }
})

onMounted(() => {
  updateOverflow()
  revealCb()
  if (typeof ResizeObserver !== 'undefined' && crumbsRef.value) {
    pbResize = new ResizeObserver(updateOverflow)
    pbResize.observe(crumbsRef.value)
  }
})
onBeforeUnmount(() => { if (pbResize) pbResize.disconnect() })
</script>

<template>
  <div class="pathbar" :title="fullPath || '未选择文件'" @click="onBarClick">
    <Transition name="pb-swap" mode="out-in">
      <input v-if="editing" ref="inputRef" v-model="editText" class="pb-input"
             :title="fullPath" spellcheck="false" autocomplete="off"
             @keydown="onEditKey" @blur="exitEdit" @click.stop />
      <div v-else ref="crumbsRef" class="pb-crumbs" @scroll.passive="updateOverflow">
        <button v-if="segments.length" type="button" class="pb-seg pb-root"
                :title="rootTitle" @click.stop="onRootClick">
          <i class="bi" :class="isFolderMode ? 'bi-hdd' : 'bi-archive'"></i>{{ rootLabel }}
        </button>
        <template v-for="(seg, i) in segments" :key="i">
          <i class="bi bi-chevron-right pb-sep"></i>
          <button type="button" class="pb-seg" :class="{ 'pb-current': i === segments.length - 1 }"
                  :title="segments.slice(0, i + 1).join('/')"
                  @click.stop="onSegClick(seg, i)">
            {{ seg }}
          </button>
        </template>
        <span v-if="!segments.length" class="pb-empty">未选择文件</span>
      </div>
    </Transition>
    <!-- 溢出渐隐遮罩：指示该方向还有可滚动内容（长路径不被截断后"看不见"）。
         放在 Transition 之外，避免成为切换动画的过渡目标（Vue3 多根告警 + 动画交叉）。 -->
    <span v-if="canLeft && !editing" class="pb-fade pb-fade-left"></span>
    <span v-if="canRight && !editing" class="pb-fade pb-fade-right"></span>
  </div>
</template>

<style scoped>
/* 容器：Win10 地址栏样式 —— 浅底圆角、hover 描边、focus 主色光圈 */
.pathbar {
  display: inline-flex;
  align-items: center;
  /* 相对定位 → 作为两侧溢出渐隐遮罩（.pb-fade）的定位基准 */
  position: relative;
  overflow: hidden;           /* 遮罩收敛在圆角内，避免溢出到外部 */
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  background: var(--bs-body-bg);
  border: 1px solid var(--bs-border-color);
  border-radius: .375rem;
  padding: .08rem .35rem;
  cursor: text;               /* 提示可点击进入编辑 */
  transition: border-color .15s ease, box-shadow .15s ease;
}
.pathbar:hover { border-color: var(--bs-secondary-color); }
.pathbar:focus-within { border-color: var(--bs-primary); box-shadow: 0 0 0 .15rem rgba(var(--bs-primary-rgb), .18); }

/* 溢出渐隐遮罩：柔和渐隐可滚动方向的边界，提示该侧还有内容可查看。
   用 radial/linear 渐变从透明渐到底色，不拦截点击（pointer-events:none）。 */
.pb-fade {
  position: absolute;
  top: 1px;
  bottom: 1px;
  width: 1.1rem;
  pointer-events: none;       /* 不拦截面包屑点击 */
  z-index: 1;
}
.pb-fade-left { left: 0; background: linear-gradient(to right, var(--bs-body-bg), transparent); }
.pb-fade-right { right: 0; background: linear-gradient(to left, var(--bs-body-bg), transparent); }

/* 面包屑区：横向可滚动（长路径不换行，滚动条细）；pan-x 支持移动端横向滑动查看完整路径 */
.pb-crumbs {
  display: flex;
  align-items: center;
  overflow-x: auto;
  scrollbar-width: thin;
  touch-action: pan-x;        /* 触控反馈：只允许横向手势滚动，避免误触纵向页滚 */
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
/* 根节点（盘符/包根）：图标 + 加粗 */
.pb-root { display: inline-flex; align-items: center; gap: .22rem; font-weight: 600; }
.pb-root i { font-size: .72rem; }
/* 当前层级（文件段）：主色加粗，标识所在层级 */
.pb-current { color: var(--bs-body-color); font-weight: 600; }
/* 层级分隔符 */
.pb-sep { font-size: .5rem; color: var(--bs-secondary-color); opacity: .75; flex: 0 0 auto; }
.pb-empty { font-size: .76rem; color: var(--bs-secondary-color); padding: 0 .3rem; }

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

/* 面包屑 ↔ 编辑框切换动画：淡入淡出 + 轻微位移 */
.pb-swap-enter-active, .pb-swap-leave-active { transition: opacity .12s ease, transform .12s ease; }
.pb-swap-enter-from, .pb-swap-leave-to { opacity: 0; transform: translateY(2px); }
</style>
