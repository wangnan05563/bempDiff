// R8 验收（二期 T01470）：三类失败原因区分 + 破坏性确认框含后果与安全选项。
// 覆盖：testConnection 鉴权/网络/成功三路 toast 文案、ToolBar 删除/清空历史确认文案
// （含「不可恢复」后果说明）与取消安全路径（confirm=false 不动数据）。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig, testConnection, pushCompareHistory, removeCompareHistory, clearCompareHistory } from '../store'
import { api } from '../api/client'
import ToolBar from '../components/ToolBar.vue'

describe('R8 连接失败三类原因区分', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.toast = null
    vi.restoreAllMocks()
  })

  it('鉴权失败（401）→ toast 含「失败原因：鉴权失败」与建议', async () => {
    vi.spyOn(api, 'testAi').mockResolvedValue({ ok: false, message: 'HTTP 401: invalid api key' })
    await testConnection({ baseUrl: 'x', apiKey: 'k' })
    expect(state.toast.type).toBe('warning')
    expect(state.toast.text).toContain('失败原因：鉴权失败')
    expect(state.toast.text).toContain('API Key')
  })

  it('网络不可达（fetch 失败）→ toast 含「失败原因：网络不可达」与代理建议', async () => {
    vi.spyOn(api, 'testAi').mockRejectedValue(new TypeError('Failed to fetch'))
    await testConnection({ baseUrl: 'x', apiKey: 'k' })
    expect(state.toast.type).toBe('danger')
    expect(state.toast.text).toContain('失败原因：网络不可达')
    expect(state.toast.text).toContain('代理')
  })

  it('连通成功 → success toast（不受失败路径影响）', async () => {
    vi.spyOn(api, 'testAi').mockResolvedValue({ ok: true, message: '已连通' })
    await testConnection({ baseUrl: 'x', apiKey: 'k' })
    expect(state.toast.type).toBe('success')
    expect(state.toast.text).toContain('已连通')
  })
})

describe('R8 破坏性确认框（后果说明 + 安全选项）', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.job = { jobId: 'j1', status: 'DONE', tree: [], stats: null, mode: 'package' }
    state.aiTasks = []
    state.exportRecords = []
    state.reportMd = '# x'
    state.compareHistory = []
    try { localStorage.removeItem('bempdiff.compareHistory') } catch (_) {}
    pushCompareHistory('D:/a.war', 'D:/b.war', 'package')
  })

  function mountBar() {
    return mount(ToolBar, {
      props: { onOpenConfig: () => {}, onOpenReport: () => {} },
      attachTo: document.body
    })
  }

  it('删除历史：确认文案含「不可恢复」后果说明；确认后删除', async () => {
    const spy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const w = mountBar()
    await w.find('button[title="最近比对会话：点击恢复路径对（仅记录路径与时间，不含差异内容与 AI 结果）"]').trigger('click')
    const del = w.findAll('[role="button"], i').find(x => (x.attributes('title') || '') === '删除该条历史')
    await del.trigger('click')
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0]).toContain('不可恢复')
    expect(spy.mock.calls[0][0]).toContain('不影响任何本地文件')
    expect(state.compareHistory.length).toBe(0)
    w.unmount()
  })

  it('取消（安全选项）：confirm=false 时不删除任何数据', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const w = mountBar()
    await w.find('button[title="最近比对会话：点击恢复路径对（仅记录路径与时间，不含差异内容与 AI 结果）"]').trigger('click')
    const del = w.findAll('[role="button"], i').find(x => (x.attributes('title') || '') === '删除该条历史')
    await del.trigger('click')
    expect(state.compareHistory.length).toBe(1) // 取消 = 无任何修改
    w.unmount()
  })

  it('清空历史：确认文案含后果说明；确认后清空', async () => {
    const spy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const w = mountBar()
    await w.find('button[title="最近比对会话：点击恢复路径对（仅记录路径与时间，不含差异内容与 AI 结果）"]').trigger('click')
    const clear = w.findAll('a').find(a => a.text().includes('清空'))
    await clear.trigger('click')
    expect(spy.mock.calls[0][0]).toContain('不可恢复')
    expect(state.compareHistory.length).toBe(0)
    w.unmount()
  })
})
