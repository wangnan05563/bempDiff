// R4 一键摘要——AiConsole Markdown 复制（二期 T01459）。
// 覆盖：已完成任务渲染复制按钮、点击后剪贴板写入（mock clipboard API）+ 成功 toast、
//       无输出时按钮禁用、失败路径降级提示。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import AiConsole from '../components/AiConsole.vue'

const TASK = {
  id: 'ai-1', category: 'risk', title: '整体风险分析', prompt: '',
  fileKey: null, fileStatus: null,
  status: 'done', thinking: [], answer: '# 整体摘要\n\n- 变更规模：3 个文件\n- 风险等级：中',
  error: '', createdAt: Date.now(), alive: true, thinkingCollapsed: true
}

describe('AiConsole 一键复制 Markdown', () => {
  beforeEach(() => {
    state.aiTasks = [JSON.parse(JSON.stringify(TASK))]
    state.aiActiveTaskId = 'ai-1'
  })

  it('已完成任务显示复制按钮，点击写入剪贴板并 toast 成功', async () => {
    const writeText = vi.fn(async () => {})
    Object.assign(navigator, { clipboard: { writeText } })
    const w = mount(AiConsole)
    const btn = w.findAll('button').find(b => (b.attributes('title') || '').includes('复制 Markdown'))
    expect(btn, '应存在「复制 Markdown」按钮').toBeTruthy()
    await btn.trigger('click')
    await w.vm.$nextTick()
    expect(writeText).toHaveBeenCalledTimes(1)
    expect(writeText.mock.calls[0][0]).toContain('# 整体摘要')
    expect(writeText.mock.calls[0][0]).toContain('风险等级：中')
    expect(state.toast && state.toast.text).toContain('已复制为 Markdown')
    w.unmount()
  })

  it('无输出任务复制按钮禁用', async () => {
    state.aiTasks[0].answer = ''
    const w = mount(AiConsole)
    const btn = w.findAll('button').find(b => (b.attributes('title') || '').includes('复制 Markdown'))
    expect(btn.attributes('disabled')).toBeDefined()
    w.unmount()
  })

  it('剪贴板不可用时降级提示且不抛错', async () => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn(async () => { throw new Error('denied') }) } })
    const w = mount(AiConsole)
    const btn = w.findAll('button').find(b => (b.attributes('title') || '').includes('复制 Markdown'))
    await btn.trigger('click')
    await w.vm.$nextTick()
    expect(state.toast && state.toast.text).toContain('复制失败')
    w.unmount()
  })
})
