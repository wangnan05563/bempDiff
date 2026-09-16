// T00638 回归：浏览按钮必须能选出**单文件**（war/jar/zip）。
// 背景：上一版「auto 同弹文件+目录」在 Windows/Linux 上被 Electron 降级为仅目录选择器，
// 导致点击浏览只能选到文件夹、选不到包文件。现改为「主按钮=文件 / 下拉=目录」两条显式模式。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

const { pickPath, state, toast } = vi.hoisted(() => ({
  pickPath: vi.fn(),
  state: { oldPath: '', newPath: '', leftType: 'package', job: null, exporting: false },
  toast: vi.fn()
}))
vi.mock('../lib/tauri', () => ({
  isTauri: () => false,
  isElectron: () => true,
  pickPath: (...a) => pickPath(...a)
}))
vi.mock('../store', () => ({
  state,
  triggerCompare: vi.fn(),
  generateReport: vi.fn(),
  downloadExport: vi.fn(),
  runAiClassify: vi.fn(),
  toast,
  applyTheme: vi.fn(),
  ingestShellPaths: vi.fn(),
  inferType: (p) => (/\.(war|jar|zip|ear|tar\.gz|tgz)$/i.test(p || '') ? 'package' : 'folder'),
  startAiAnalysis: vi.fn(),
  aiAnyRunning: () => false,
  isUnpacking: () => false,
  openExports: vi.fn()
}))

import ToolBar from '../components/ToolBar.vue'

function mountToolBar() {
  return mount(ToolBar, {
    props: { onOpenConfig: () => {}, onOpenReport: () => {} },
    global: { stubs: { PathBreadcrumb: true, Downloads: true } }
  })
}

beforeEach(() => {
  pickPath.mockReset()
  state.oldPath = ''
  state.newPath = ''
  state.leftType = 'package'
})

describe('ToolBar 浏览按钮（T00638 回归）', () => {
  it('主按钮走文件选择：pickPath 收到 directory:false，且单文件路径被填入老包', async () => {
    pickPath.mockResolvedValue('C:/pkg/app.war')
    const w = mountToolBar()
    const btn = w.findAll('button').find((b) => (b.attributes('title') || '').includes('浏览选择老包'))
    expect(btn, '应存在老包浏览主按钮').toBeTruthy()
    await btn.trigger('click')
    await nextTick()
    expect(pickPath.mock.calls[0][0]).toEqual({ directory: false })
    expect(state.oldPath).toBe('C:/pkg/app.war')
    expect(state.leftType).toBe('package')
    w.unmount()
  })

  it('下拉「选择目录」走目录选择：pickPath 收到 directory:true，目录路径按 folder 识别', async () => {
    pickPath.mockResolvedValue('C:/extracted/app')
    const w = mountToolBar()
    const caret = w.findAll('button').find((b) => (b.attributes('title') || '') === '更多选择：文件 / 目录')
    await caret.trigger('click')
    await nextTick()
    const dirItem = w.findAll('a.dropdown-item').find((a) => a.text().includes('选择目录'))
    expect(dirItem, '下拉应含「选择目录」项').toBeTruthy()
    await dirItem.trigger('click')
    await nextTick()
    expect(pickPath.mock.calls[0][0]).toEqual({ directory: true })
    expect(state.oldPath).toBe('C:/extracted/app')
    expect(state.leftType).toBe('folder')
    w.unmount()
  })

  it('新侧主按钮同样走文件选择（双侧对称）', async () => {
    pickPath.mockResolvedValue('D:/pkg/new.jar')
    const w = mountToolBar()
    const btn = w.findAll('button').find((b) => (b.attributes('title') || '').includes('浏览选择新包'))
    await btn.trigger('click')
    await nextTick()
    expect(pickPath.mock.calls[0][0]).toEqual({ directory: false })
    expect(state.newPath).toBe('D:/pkg/new.jar')
    w.unmount()
  })

  it('回归防线：不得再把 directory 与 auto 混用（老 auto 同弹在 Windows 只出目录）', async () => {
    pickPath.mockResolvedValue('C:/pkg/a.war')
    const w = mountToolBar()
    for (const b of w.findAll('button')) {
      const t = b.attributes('title') || ''
      if (t.includes('浏览选择老包') || t.includes('浏览选择新包')) await b.trigger('click')
    }
    await nextTick()
    for (const call of pickPath.mock.calls) {
      expect(Object.keys(call[0]).sort()).toEqual(['directory'])
      expect(typeof call[0].directory).toBe('boolean')
    }
    w.unmount()
  })
})
