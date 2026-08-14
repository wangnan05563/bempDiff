<script setup>
import { ref, computed } from 'vue'
import { state, generateReport } from '../store'
import { renderMarkdown, extractSection } from '../lib/markdown'

const tab = ref('file')
const STATUS_LABEL = { ADDED: '新增', DELETED: '删除', MODIFIED: '修改', UNCHANGED: '未变' }

const node = computed(() => {
  if (!state.job || !state.selectedKey) return null
  return state.job.tree.find(n => n.key === state.selectedKey) || null
})
const s = computed(() => (state.job && state.job.stats) ? state.job.stats : null)

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
</script>

<template>
  <div class="col-ai">
    <div class="pane-head"><i class="bi bi-cpu"></i> 智能分析</div>
    <ul class="nav nav-tabs px-2 pt-2">
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='file'}" @click="tab='file'">单文件</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='global'}" @click="tab='global'">全局汇总</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='break'}" @click="tab='break'">破坏性</button></li>
      <li class="nav-item"><button class="nav-link py-1" :class="{active: tab==='audit'}" @click="tab='audit'">审计</button></li>
    </ul>

    <div class="ai-body">
      <!-- 单文件 -->
      <div v-show="tab==='file'">
        <div v-if="node" class="ai-card">
          <h4><i class="bi bi-file-earmark-code"></i> {{ node.key }}</h4>
          <div class="kv">状态：<b>{{ STATUS_LABEL[node.status] }}</b></div>
          <div class="kv">分层：<b>{{ node.layer }}</b> · 类型：<b>{{ node.fileClass }}</b></div>
          <div class="kv">大小：<b>{{ fmtSize(node.size) }}</b></div>
          <div class="kv">反编译引擎：<b>{{ state.decompile ? state.decompile.engine : '—' }}</b></div>
          <div v-if="state.decompile && !state.decompile.ok" class="text-danger mt-2" style="font-size:.8rem">
            该文件无法反编译（引擎：{{ state.decompile.engine }}）
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
          <div class="kv">业务码变更 <b>{{ s.bizChanged }}</b> · jar 级变更 <b>{{ s.jarChanged }}</b></div>
          <div class="kv">条目总数 <b>{{ s.total }}</b></div>
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
          <button class="btn btn-sm btn-outline-primary" :disabled="!state.job || state.busy"
                  @click="generateReport(state.config && state.config.aiEnabled)">
            <i class="bi bi-filetype-md"></i> 生成报告{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
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
          <button class="btn btn-sm btn-outline-primary" :disabled="!state.job || state.busy"
                  @click="generateReport(state.config && state.config.aiEnabled)">
            <i class="bi bi-filetype-md"></i> 生成报告{{ (state.config && state.config.aiEnabled) ? '(AI)' : '' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
