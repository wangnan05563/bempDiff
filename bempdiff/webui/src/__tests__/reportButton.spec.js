// 复现验证：破坏性 / 审计栏「生成报告」按钮缺陷（修复回归）。
// 原缺陷：按钮 :disabled 含 !state.job，未比对时按钮被禁用，点击被浏览器静默吞掉
//         → 界面没有任何反应、无遮罩、无进度条。
// 修复后：移除 !state.job 禁用态；点击走 onGenerateReport 守卫——无 job 弹 toast 警告，
//        有 job 调 generateReport 走 busy 遮罩；CompareOverlay 在 busy 且非比对中时渲染不确定进度条。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import InfoPanel from '../components/InfoPanel.vue'
import CompareOverlay from '../components/CompareOverlay.vue'
import { state } from '../store'

// mock 后端 api，避免真实网络；report 立即返回测试 markdown。
vi.mock('../api/client', () => ({
  api: {
    report: vi.fn(async () => '# 测试报告\n内容'),
    getConfig: vi.fn(async () => ({ aiEnabled: false }))
  }
}))

function findReportBtn(wrapper) {
  return wrapper.findAll('button').find(b => b.text().includes('生成报告'))
}

beforeEach(() => {
  state.job = null
  state.reportMd = null
  state.reportCache = {}
  state.busy = false
  state.busyText = ''
  state.reporting = false
  state.jobProgress = null
  state.config = { aiEnabled: false }
  state.toast = null
  // 隔离补强：InfoPanel 挂载时 applyAiPanelResponsive 会按视口宽自动收起智能分析栏，
  // 若上一用例已把 aiPanelCollapsed 置 true 且未重置，后续用例的 v-if 内容区不渲染 → 按钮丢失。
  state.aiPanelCollapsed = false
  state.aiPanelTab = 'file'
  state.tabs = []
  state.activeKey = null
  try { localStorage.removeItem('bempdiff.analysisCollapsed') } catch (_) { /* jsdom 下正常 */ }
})

describe('破坏性/审计栏 生成报告按钮', () => {
  it('T1 无 job 点击：不再静默无响应，改为弹 toast 警告且不触发报告生成', async () => {
    state.job = null
    const wrapper = mount(InfoPanel)
    const btn = findReportBtn(wrapper)
    expect(btn).toBeTruthy()
    // 修复点：按钮不应再被 !state.job 禁用（原缺陷即此）
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    await nextTick()
    // 守卫应弹警告 toast，给出明确反馈（原缺陷是点击毫无反应）
    expect(state.toast).not.toBeNull()
    expect(state.toast.type).toBe('warning')
    expect(state.toast.text).toContain('比对')
    // 不应进入报告生成（busy/reporting 未被拉起，reportMd 未变）
    expect(state.busy).toBe(false)
    expect(state.reporting).toBe(false)
    expect(state.reportMd).toBeNull()
  })

  it('T2 有 job 点击：真正调用 generateReport 并写入 reportMd，busy 在结束后复位', async () => {
    state.job = {
      jobId: 'j1', oldVersion: 'v1', newVersion: 'v2',
      stats: { added: 1, deleted: 0, modified: 0, unchanged: 0, bizChanged: 0, jarChanged: 0, total: 1 }
    }
    const wrapper = mount(InfoPanel)
    const btn = findReportBtn(wrapper)
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    // generateReport 为 async，等待 mock 的 report 完成 + finally 复位
    await new Promise(r => setTimeout(r, 50))
    await nextTick()
    expect(state.reportMd).toContain('测试报告')
    expect(state.busy).toBe(false)
    expect(state.reporting).toBe(false)
  })
})

describe('CompareOverlay 遮罩与进度条', () => {
  it('T3 报告生成中(busy 且非比对)渲染遮罩 + 不确定进度条', async () => {
    const wrapper = mount(CompareOverlay)
    expect(wrapper.find('.busy-overlay').exists()).toBe(false)
    state.busy = true
    state.busyText = '正在生成报告…'
    state.jobProgress = null // 非比对中
    await nextTick()
    expect(wrapper.find('.busy-overlay').exists()).toBe(true)
    // v-else 分支：不确定进度条（role=progressbar）应存在
    expect(wrapper.findAll('[role="progressbar"]').length).toBeGreaterThan(0)
  })

  it('T4 busy=false 时遮罩不渲染（无残留）', async () => {
    const wrapper = mount(CompareOverlay)
    state.busy = false
    await nextTick()
    expect(wrapper.find('.busy-overlay').exists()).toBe(false)
  })
})
