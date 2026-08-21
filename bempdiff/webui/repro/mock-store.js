// 复现台架专用 mock store：模拟「AI 分析报告已生成」后的控制台状态。
// 仅导出 AiConsole.vue 实际 import 的符号；答案内容模拟真实报告（含宽表格/代码块/长行）。
import { reactive } from 'vue'

export const AI_CATEGORIES = [
  { key: 'risk',       label: '整体风险分析',   icon: 'bi-shield-exclamation' },
  { key: 'breaking',   label: '破坏性变更专项', icon: 'bi-exclamation-octagon' },
  { key: 'impact',     label: '影响范围分析',   icon: 'bi-diagram-3' },
  { key: 'testpoints', label: '测试要点分析',   icon: 'bi-list-check' },
  { key: 'custom',     label: '自定义问题',     icon: 'bi-chat-left-text' }
]

const LONG_REPORT = `# AI 风险分析报告

## 一、总体风险结论
整体风险：**高**

## 二、破坏性变更清单

| 序号 | 变更文件 | 变更类型 | 影响模块 | 影响范围 | 风险等级 | 迁移建议 | 涉及流程 | 关联接口 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | com/hundsun/bank/ticket/service/TicketQueryService.class | 方法删除 | 业务查询 | 核心交易链 | 高 | 需回归 | 查询流程 | ITicketQueryService | 影响面大 |
| 2 | com/hundsun/bank/ticket/model/TicketOrder.java | 字段类型变更 | 订单模型 | 全链路 | 高 | 需联调 | 下单流程 | 内部使用 | 注意序列化兼容性 |
| 3 | com/hundsun/bank/common/security/AuthFilter.class | 接口签名变更 | 统一鉴权 | 所有入口 | 严重 | 必须回归 | 鉴权流程 | AuthFilter.doFilter | 需重点验证 |

\`\`\`diff
- com/hundsun/bank/ticket/service/TicketQueryService.class [删除] queryByTicketNo(String, String)
+ com/hundsun/bank/ticket/service/TicketQueryService.class [新增] queryByTicketNo(String, String, int)
- com/hundsun/bank/ticket/model/TicketOrder.java [修改] private String status
+ com/hundsun/bank/ticket/model/TicketOrder.java [修改] private TicketStatusEnum status
\`\`\`

## 三、影响范围分析
本次变更共涉及 **3 个业务模块**，其中核心交易链影响最大，建议按以下顺序回归：
1. 先回归查询链路
2. 再验证下单链路
3. 最后做全量回归

## 四、详细说明
涉及的核心方法调用链较长，且存在循环依赖，建议在测试环境先行验证。这里有一段非常长的说明文字用于模拟宽内容对控制台布局的影响，这段文字会持续延伸下去直到超过可视区域的宽度，用来观察报告内容对控制台布局与标签页区域是否产生挤压或滚动干扰。
`

export const state = reactive({
  job: { status: 'DONE', jobId: 'repro-job', oldVersion: 'v1', newVersion: 'v2' },
  aiTasks: [
    {
      id: 'ai-1', category: 'risk', title: '整体风险分析',
      prompt: '', status: 'done',
      thinking: [
        { phase: 'thinking', message: '读取差异树 128 个变更节点' },
        { phase: 'tool_call', message: '调用 stageB 分析破坏性变更' },
        { phase: 'composing', message: '整理风险结论' }
      ],
      answer: LONG_REPORT, error: '', createdAt: Date.now() - 120000
    },
    {
      id: 'ai-2', category: 'custom',
      title: '自定义：请详细分析本次业务系统升级中所有破坏性接口变更及其影响范围，重点说明涉及的业务流程与数据迁移风险',
      prompt: '请详细分析本次业务系统升级中所有破坏性接口变更及其影响范围',
      status: 'done', thinking: [], answer: '## 结论\n破坏性变更集中在业务查询与订单模型，具体见上方报告。', error: '', createdAt: Date.now() - 90000
    },
    {
      id: 'ai-3', category: 'impact', title: '影响范围分析',
      prompt: '', status: 'streaming', thinking: [], answer: '正在分析影响范围：涉及模块…', error: '', createdAt: Date.now() - 30000
    },
    {
      id: 'ai-4', category: 'testpoints', title: '测试要点分析',
      prompt: '', status: 'thinking', thinking: [{ phase: 'thinking', message: '正在准备测试要点…' }], answer: '', error: '', createdAt: Date.now() - 5000
    }
  ],
  aiActiveTaskId: 'ai-1',
  aiPanelCollapsed: false,
  aiPanelTab: 'console',
  busy: false,
  reporting: false,
  analyzing: false
})

export function toast(type, text) { console.log('[toast]', type, text) }
export function startAiAnalysis() { console.log('startAiAnalysis') }
export function stopAiAnalysis(id) {
  const t = state.aiTasks.find(x => x.id === id)
  if (t && (t.status === 'thinking' || t.status === 'streaming')) t.status = 'aborted'
}
export function restartAiAnalysis(id) { console.log('restartAiAnalysis', id) }
export function selectAiTask(id) { state.aiActiveTaskId = id }
export function closeAiTask(id) {
  const i = state.aiTasks.findIndex(x => x.id === id)
  if (i < 0) return
  state.aiTasks.splice(i, 1)
  if (state.aiActiveTaskId === id) {
    state.aiActiveTaskId = state.aiTasks.length ? state.aiTasks[Math.max(0, i - 1)].id : null
  }
}
export function openReportPreview(md) { console.log('openReportPreview', md && md.length) }
