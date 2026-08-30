<script setup>
import { computed, ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { state, activeTab, generateReport, setAiPanelCollapsed, applyAiPanelResponsive } from '../store'
import { renderMarkdown, extractSection } from '../lib/markdown'
import AiConsole from './AiConsole.vue'

// 内容子视图 tab（单文件/全局汇总/破坏性/审计/控制台）提升到共享 state.aiPanelTab，
// 以便「新建分析」时 store 能自动切到控制台 tab 让用户即时看到流式输出。
const tab = computed({
  get: () => state.aiPanelTab,
  set: (v) => { state.aiPanelTab = v }
})

// 滚动位置记忆：file/global/break/audit 四个报告类子视图共用同一个 .ai-body 滚动容器，
// 切换 tab 时若不缓存，各 tab 的 scrollTop 会互相串台（前一个 tab 的位置被后一个 tab 直接看到）。
// 用 Map 按 tab key 缓存 scrollTop：切换前保存旧 tab 位置、切换后恢复新 tab 位置（无记忆则回顶）。
// console tab 走 AiConsole 自管，不在此恢复（.ai-body 在 console 下 v-show 隐藏）。
const aiBodyRef = ref(null)
const aiScrollMap = new Map()
let aiScrollRaf = 0
function onAiBodyScroll() {
  if (aiScrollRaf) return
  aiScrollRaf = requestAnimationFrame(() => {
    aiScrollRaf = 0
    const t = state.aiPanelTab
    if (t && t !== 'console' && aiBodyRef.value) {
      aiScrollMap.set(t, aiBodyRef.value.scrollTop)
    }
  })
}
watch(() => state.aiPanelTab, (newTab, oldTab) => {
  // 切换瞬间 DOM 仍是旧 tab 内容，先保存旧 tab 的滚动位置
  if (oldTab && oldTab !== 'console' && aiBodyRef.value) {
    aiScrollMap.set(oldTab, aiBodyRef.value.scrollTop)
  }
  // 新 tab 若是报告类视图，恢复其记忆位置；console 不在此恢复（由 AiConsole 处理）
  if (newTab && newTab !== 'console') {
    nextTick(() => {
      if (aiBodyRef.value) {
        aiBodyRef.value.scrollTop = aiScrollMap.has(newTab) ? aiScrollMap.get(newTab) : 0
      }
    })
  }
})

// 智能分析栏收起状态改由共享 state.aiPanelCollapsed 驱动（与 DiffView 文件栏一键显隐联动）。
// 挂载即按「显式偏好 > 视口宽度」应用一次响应式避让；之后视口变化也跟随避让，避免窄屏挤占对比窗口。
onMounted(() => { applyAiPanelResponsive(); window.addEventListener('resize', applyAiPanelResponsive) })
onUnmounted(() => window.removeEventListener('resize', applyAiPanelResponsive))
const STATUS_LABEL = { ADDED: '新增', DELETED: '删除', MODIFIED: '修改', UNCHANGED: '未变' }

// AI 是否启用：集中判定，供「生成报告」标签后缀与点击传参共用，避免 AI 判定逻辑在多处重复（评审 A/D）。
const aiEnabled = computed(() => !!(state.config && state.config.aiEnabled))
const aiLabelSuffix = computed(() => aiEnabled.value ? '(AI)' : '')
// 宽度经 prop 传入（App 拖拽调宽），根节点显式绑定，与 DiffTree 采用一致方式
const props = defineProps({ panelWidth: { type: Number, default: null } })

// node / decompile 都从当前激活的 tab 取；对应「DiffView 顶部 tab 栏选哪一个这里就显示哪一个」。
const node = computed(() => {
  const at = activeTab()
  return at ? at.node : null
})
const dec = computed(() => {
  const at = activeTab()
  return at ? at.decompile : null
})
const s = computed(() => (state.job && state.job.stats) || null)
// 全量统计（含 bizChanged/jarChanged）；差异数字统一以后端全量 stats 为准
const fullStats = computed(() => state.job && state.job.stats ? state.job.stats : null)

// 从已生成报告中抽取「六、破坏性变更」与「七/八、审计摘要」章节并渲染为 HTML。
const breakHtml = computed(() => {
  if (!state.reportMd) return ''
  const body = extractSection(state.reportMd, '破坏性变更')
  return body ? renderMarkdown(body) : '<p class="text-secondary mb-0">报告中无破坏性变更。</p>'
})
const auditHtml = computed(() => {
  if (!state.reportMd) return ''
  const body = extractSection(state.reportMd, '审计摘要')
  return body ? renderMarkdown(body) : '<p class="text-secondary mb-0">报告中无审计结论。</p>'
})

function fmtSize(b) {
  if (!b) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB']
  let i = 0, n = b
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++ }
  return (i ? n.toFixed(1) : n) + ' ' + u[i]
}

// 点击守卫：no-job 提示已下沉到 store.generateReport 内部（统一 InfoPanel / ToolBar / 自动报告入口），
// 此处仅负责把「是否启用 AI」与「分析项」传入，避免 AI 判定逻辑在多处重复（评审 A/D）。
// 修复：生成报告携带对应分析项（破坏性→breaking / 审计→risk），使报告内容随所选维度变化。
function onGenerateReport(category) {
  generateReport(aiEnabled.value, category ? { category } : undefined)
}
</script>

<template>
  <div class="col-ai" :class="{ collapsed: state.aiPanelCollapsed }"
       :style="props.panelWidth != null && !state.aiPanelCollapsed ? { width: props.panelWidth + 'px' } : undefined">
    <div v-if="!state.aiPanelCollapsed" class="pane-head">
      <i class="bi bi-cpu" role="img" title="智能分析" aria-label="智能分析"></i> 智能分析
      <button class="btn btn-sm btn-outline-secondary border-0 ms-auto px-1 py-0" title="收起智能分析，扩大比对视野"
              @click="setAiPanelCollapsed(true)">
        <i class="bi bi-layout-sidebar-inset-reverse"></i>
      </button>
    </div>
    <div v-else class="ai-collapsed-bar" title="展开智能分析" @click="setAiPanelCollapsed(false)">
      <i class="bi bi-chevron-left"></i>
      <i class="bi bi-cpu" role="img" aria-label="智能分析" title="智能分析"></i>
    </div>

    <template v-if="!state.aiPanelCollapsed">
    <ul class="nav nav-tabs px-2 pt-2">
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='file'}" @click="tab='file'">单文件</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='global'}" @click="tab='global'">全局汇总</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='break'}" @click="tab='break'">破坏性</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='audit'}" @click="tab='audit'">审计</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='console'}" @click="tab='console'">控制台</button></li>
    </ul>

    <!-- 信息面板四个子视图：仅在未选中「控制台」时显示，以便控制台独占整块内容区高度 -->
    <div class="ai-body" v-show="tab!=='console'" ref="aiBodyRef" @scroll="onAiBodyScroll">
      <!-- 单文件 -->
      <div v-show="tab==='file'">
        <div v-if="node" class="ai-card">
          <h4><i class="bi bi-file-earmark-code"></i> {{ node.key }}</h4>
          <div class="kv">状态：<b>{{ STATUS_LABEL[node.status] }}</b></div>
          <div class="kv">分层：<b>{{ node.layer }}</b> · 类型：<b>{{ node.fileClass }}</b></div>
          <div class="kv">大小：<b>{{ fmtSize(node.size) }}</b></div>
          <div class="kv">反编译引擎：<b>{{ dec ? dec.engine : '—' }}</b></div>
          <div v-if="dec && !dec.ok" class="text-danger mt-2" style="font-size:.8rem">
            {{ dec.error || ('该文件无法反编译（引擎：' + dec.engine + '）') }}
          </div>
        </div>
        <div v-else class="text-secondary" style="font-size:.85rem">
          选择一个差异文件后，这里展示其单文件分析（变更要点、风险初判）。
        </div>
      </div>

      <!-- 全局汇总 -->
      <div v-show="tab==='global'">
        <div v-if="s" class="ai-card">
          <h4><i class="bi bi-bar-chart"></i> 全局汇总</h4>
          <div class="kv">包版本：<b>{{ state.job.oldVersion }} → {{ state.job.newVersion }}</b></div>
          <div class="kv">新增 <b class="text-success">{{ s.added }}</b> · 删除 <b class="text-danger">{{ s.deleted }}</b> ·
            修改 <b class="text-warning">{{ s.modified }}</b> · 未变 {{ s.unchanged }}</div>
          <div class="kv">业务码变更 <b>{{ fullStats.bizChanged }}</b> · jar 级变更 <b>{{ fullStats.jarChanged }}</b></div>
          <div class="kv">条目总数 <b>{{ fullStats.total }}</b></div>
        </div>
        <div v-else class="text-secondary" style="font-size:.85rem">尚未比对。</div>
      </div>

      <!-- 破坏性 -->
      <div v-show="tab==='break'">
        <div v-if="state.reportMd" class="ai-md" v-html="breakHtml"></div>
        <div v-else class="ai-card">
          <h4><i class="bi bi-exclamation-octagon"></i> 破坏性变更</h4>
          <p class="text-secondary mb-2" style="font-size:.85rem">
            尚未生成报告。生成后此处自动展示「删除类 / 删除前端资源」等破坏性 API / 行为变更分析。
          </p>
          <button class="btn btn-sm btn-outline-primary" :disabled="state.busy || state.reporting"
                  @click="onGenerateReport('breaking')">
            <i class="bi bi-filetype-md"></i> 生成报告{{ aiLabelSuffix }}
          </button>
        </div>
      </div>

      <!-- 审计 -->
      <div v-show="tab==='audit'">
        <div v-if="state.reportMd" class="ai-md" v-html="auditHtml"></div>
        <div v-else class="ai-card">
          <h4><i class="bi bi-shield-check"></i> 合规审计</h4>
          <p class="text-secondary mb-2" style="font-size:.85rem">
            报告生成后，此处展示合规审计结论（比对时间、版本标识、AI 接入情况）。
          </p>
          <button class="btn btn-sm btn-outline-primary" :disabled="state.busy || state.reporting"
                  @click="onGenerateReport('risk')">
            <i class="bi bi-filetype-md"></i> 生成报告{{ aiLabelSuffix }}
          </button>
        </div>
      </div>
    </div>

    <!-- AI 分析面板：与原四个子视图互斥，选中「控制台」tab 时独占整块内容区，获得最大展示空间 -->
    <AiConsole v-show="tab==='console'" />
    </template>
  </div>
</template>

<style scoped>
/* 5 个内容子视图 tab：窄屏下不换行，改为单行横向滚动，
 * 既保持 tab 栏整洁，又避免换行挤占控制台纵向高度（布局优化的核心诉求）。 */
.nav-tabs { flex-wrap: nowrap; overflow-x: auto; }
.nav-tabs .nav-link { white-space: nowrap; flex-shrink: 0; padding-left: .55rem; padding-right: .55rem; }
/* 细滚动条，保持视觉干净 */
.nav-tabs::-webkit-scrollbar { height: 4px; }
.nav-tabs::-webkit-scrollbar-thumb { background: var(--bs-border-color); border-radius: 2px; }
</style>
