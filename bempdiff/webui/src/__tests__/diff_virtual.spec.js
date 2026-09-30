// R3 虚拟滚动回归护栏（二期 T01456）：大文件/大树只渲染可视窗口行，不整量铺 DOM。
// 覆盖：DiffView split 模式 2000 行源码 → .prow 数量 ≪ 总行数；总高容器按全量撑开；
//       滚动后窗口平移（startIndex 移动 → 渲染集合更新）。
import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import DiffView from '../components/DiffView.vue'

const LINES = 2000
const oldSrc = Array.from({ length: LINES }, (_, i) => 'old line ' + i).join('\n')
const newSrc = Array.from({ length: LINES }, (_, i) => 'new line ' + i).join('\n')

const KEY = 'pkg/Big.java'
const NODE = { key: KEY, fileClass: 'CLASS', status: 'MODIFIED', size: 100 }

function mountBig() {
  // 先挂载再加载数据：DiffView 靠 watch(rawOld/rawNew) 触发解析（真实 app 同为切换文件驱动）
  const w = mount(DiffView)
  state.tabs = [{
    key: KEY, node: NODE, busy: false,
    decompile: { ok: true, engine: 'cfr', oldSource: oldSrc, newSource: newSrc }
  }]
  state.activeKey = KEY
  return w
}

describe('R3 DiffView 虚拟滚动', () => {
  afterEach(() => {
    state.tabs = []
    state.activeKey = null
    state.job = null
  })

  it('2000 行只渲染可视窗口（.prow ≪ 总行数），容器按全量高度撑开', async () => {
    const w = mountBig()
    // tabs 后挂：rawOld/rawNew 变化触发 startParse（同步），两轮 nextTick 等渲染层稳定
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    const prows = w.findAll('.prow')
    expect(prows.length, '应渲染出行').toBeGreaterThan(0)
    expect(prows.length, `2000 行文件实际渲染 ${prows.length} 行（窗口 + OVERSCAN）`).toBeLessThan(200)
    // pane-grid 高度 = 全量行高估算（2000 × 21px），证明内容高度未因窗口裁剪而丢失
    const grid = w.find('.pane-grid')
    expect(grid.exists()).toBe(true)
    const h = parseFloat(grid.attributes('style').match(/height:\s*([\d.]+)px/)?.[1] || '0')
    expect(h).toBeGreaterThan(LINES * 10)
    w.unmount()
  })

  it('滚动后渲染窗口平移（可见行集合随 scrollTop 更新）', async () => {
    const w = mountBig()
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    const before = w.findAll('.prow').map(x => x.attributes('data-ri')).join(',')
    // 模拟滚动：直接驱动滚动容器事件（jsdom 无真实布局，scrollTop 手动置位）
    const pane = w.find('.diff-pane')
    await pane.trigger('scroll')
    const el = pane.element
    el.scrollTop = 10000
    await pane.trigger('scroll')
    await w.vm.$nextTick()
    const after = w.findAll('.prow').map(x => x.attributes('data-ri')).join(',')
    expect(after, '滚动后应渲染不同的行窗口').not.toBe(before)
    w.unmount()
  })
})
