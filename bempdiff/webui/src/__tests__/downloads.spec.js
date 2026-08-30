// 下载管理面板（Downloads.vue）：同步/异步两类导出资产分区展示；
// 异步区按文件名/任务ID筛选、按时间范围过滤；同步区展示本机同步导出记录。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { state, startExport, openExports } from '../store'
import Downloads from '../components/Downloads.vue'

// 轻量 JSON 响应对象：不依赖 jsdom 是否暴露全局 Response/fetch，满足 startExport 用到的字段
function jsonResp(data) {
  return {
    ok: true,
    headers: { get: () => 'application/json' },
    json: async () => data,
    text: async () => ''
  }
}

function rec(id, name, status, createdAt, size) {
  return { id, jobId: 'j1', filename: name, status, createdAt, size, estimatedBytes: size, message: '' }
}
const now = Date.now()

function syncRow(fn, createdAt) { return { filename: fn, size: 100, createdAt } }

describe('Downloads（下载管理）', () => {
  beforeEach(() => {
    state.exportRecords = [
      rec('exp_a', 'bempdiff-export-j1.zip', 'done', now, 5000),
      rec('exp_b', 'bempdiff-export-j2.zip', 'error', now - 3600_000, 100),
      rec('exp_c', 'bempdiff-export-j3.zip', 'running', now - 100000, 0)
    ]
    state.syncExports = [syncRow('sync-local-ja.zip', now), syncRow('sync-local-jb.zip', now - 500000)]
  })

  it('同时展示同步/异步两类资产选项，且互不重复', () => {
    const w = mount(Downloads, { attachTo: document.body })
    expect(w.find('.dl-panel').exists()).toBe(true)
    expect(w.text()).toContain('下载管理')
    expect(w.text()).toContain('同步导出资产')
    expect(w.text()).toContain('异步导出资产')
    // 同步区：展示本机同步记录
    expect(w.findAll('.dl-list-sync .dl-row').length).toBe(2)
    expect(w.find('.dl-list-sync').text()).toContain('sync-local-ja.zip')
    // 异步区：展示后端导出记录，且与同步区不重复（各自独立，无同一记录交叉出现）
    expect(w.findAll('.dl-list-async .dl-row').length).toBe(3)
    expect(w.find('.dl-list-async').text()).toContain('bempdiff-export-j1.zip')
    expect(w.find('.dl-list-async').text()).toContain('完成')
    // 完成的异步记录提供「下载」链接
    const dl = w.find('.dl-list-async a[download="bempdiff-export-j1.zip"]')
    expect(dl.exists()).toBe(true)
    expect(dl.attributes('href')).toContain('/api/export/exp_a/download')
    w.unmount()
  })

  it('异步区按任务ID关键字筛选', async () => {
    const w = mount(Downloads, { attachTo: document.body })
    await w.find('.dl-toolbar input').setValue('exp_b')
    await nextTick()
    const rows = w.findAll('.dl-list-async .dl-row')
    expect(rows.length).toBe(1)
    expect(rows[0].text()).toContain('bempdiff-export-j2.zip')
    w.unmount()
  })

  it('异步区按时间范围过滤', async () => {
    const w = mount(Downloads, { attachTo: document.body })
    const sel = w.find('.dl-toolbar select')
    await sel.setValue('all')
    await nextTick()
    expect(w.findAll('.dl-list-async .dl-row').length).toBe(3)
    await sel.setValue('today')
    await nextTick()
    // 全部 created 都在今天内，应仍为 3
    expect(w.findAll('.dl-list-async .dl-row').length).toBe(3)
    w.unmount()
  })
})

// 方案 B 修复回归：大包异步导出不允许自动弹出「下载管理」面板（避免遮挡工具栏导出下拉菜单），
// 仅保留手动打开（openExports）的交互；点击「导出差异资产」本身始终是手动触发。
describe('导出流程（方案B：不自动弹面板）', () => {
  beforeEach(() => {
    state.job = { jobId: 'J1', status: 'DONE' }
    state.exporting = false
    state.exportsOpen = false
    state.jobProgress = null // isUnpacking()=false，放行导出
    state.exportRecords = []
    global.fetch = undefined
  })

  it('点击导出差异资产（大包异步）：不自动打开下载管理面板，且导出标志复位', async () => {
    // 后端按 URL 分流响应：导出启动返回 JSON（大包异步）、列表为空、单条立即为 done（避免 pollExport 悬挂在 setTimeout）
    global.fetch = vi.fn(async (url) => {
      const u = String(url)
      if (u.includes('/export/start')) return jsonResp({ id: 'E1', etaText: '约剩 1 分钟' })
      if (u.includes('/api/export/list')) return jsonResp([])
      if (u.includes('/api/export/E1')) return jsonResp({ id: 'E1', status: 'done', filename: 'x.zip', createdAt: 0, size: 1 })
      return jsonResp({})
    })
    await startExport()
    expect(state.exportsOpen).toBe(false) // 方案B核心：大包不自动弹面板
    expect(state.exporting).toBe(false)   // 一次导出结束，标志复位允许下次触发
    expect(state.exportRecords).toBeDefined()
  })

  it('手动打开（openExports）：才置 exportsOpen，并拉取最新导出记录', async () => {
    global.fetch = vi.fn(async () => jsonResp([
      { id: 'E9', filename: 'bempdiff-export-J1.zip', status: 'done', createdAt: 0, size: 9, estimatedBytes: 9, message: '' }
    ]))
    openExports()
    expect(state.exportsOpen).toBe(true) // 手动点击后立即打开面板
    expect(global.fetch).toHaveBeenCalled()
    // 面板被打开时异步拉取记录（refreshExports 填充 exportRecords 供面板列表展示）；waitFor 等其完成
    await vi.waitFor(() => expect(state.exportRecords).toHaveLength(1))
    expect(state.exportRecords[0].id).toBe('E9')
  })
})