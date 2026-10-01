// R7 无障碍验收（二期 T01467/T01468 前半）——可访问名称全覆盖泛化断言。
// 规则：所有 <button> 必须具备可访问名称之一：aria-label 属性 / 文本内容（含图标按钮由
// aria-label 补齐，T01467 批量注入 57 处）。对比度沿用 Bootstrap 5.3 语义变量体系
// （--bs-secondary-color 等自带 ≥4.5:1），自定义色仅作装饰性点缀（图标/徽标，非正文文本）。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig } from '../store'
import ToolBar from '../components/ToolBar.vue'
import AiConsole from '../components/AiConsole.vue'
import ShortcutHelp from '../components/ShortcutHelp.vue'

function accessibleName(btn) {
  const label = btn.attributes('aria-label')
  if (label && label.trim()) return label.trim()
  const text = btn.text().trim()
  if (text) return text
  return null
}

function assertAllButtonsNamed(w, tag) {
  const buttons = w.findAll('button')
  expect(buttons.length, `${tag} 应渲染出按钮`).toBeGreaterThan(0)
  const unnamed = buttons.filter(b => !accessibleName(b))
  expect(unnamed.map(b => b.html()), `${tag} 存在无可访问名称的按钮`).toEqual([])
}

describe('R7 可访问名称全覆盖', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.job = { jobId: 'j1', status: 'DONE', tree: [], stats: null, mode: 'package' }
    state.reportMd = '# 报告'
    state.aiTasks = []
    state.exportRecords = []
  })

  it('ToolBar 全部按钮具备可访问名称', () => {
    const w = mount(ToolBar, {
      props: { onOpenConfig: () => {}, onOpenReport: () => {} },
      attachTo: document.body
    })
    assertAllButtonsNamed(w, 'ToolBar')
    w.unmount()
  })

  it('AiConsole 全部按钮具备可访问名称（含流式中断态）', async () => {
    state.aiTasks = [{
      id: 'ai-1', category: 'risk', title: '整体风险分析', prompt: '',
      status: 'streaming', thinking: [], answer: '', error: '', createdAt: Date.now(),
      alive: true, thinkingCollapsed: true
    }]
    state.aiActiveTaskId = 'ai-1'
    const w = mount(AiConsole, { attachTo: document.body })
    assertAllButtonsNamed(w, 'AiConsole')
    // 中断态也有名称
    const stop = w.findAll('button').find(b => (b.attributes('aria-label') || '').includes('中断'))
    expect(stop, '中断按钮应有 aria-label').toBeTruthy()
    w.unmount()
  })

  it('速查面板对话框具备 role/aria 与按钮名称', () => {
    const w = mount(ShortcutHelp, { attachTo: document.body })
    expect(w.find('[role="dialog"]').exists()).toBe(true)
    assertAllButtonsNamed(w, 'ShortcutHelp')
    w.unmount()
  })
})
