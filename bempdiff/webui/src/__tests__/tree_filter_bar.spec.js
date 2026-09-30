// 差异树 R1 过滤缺口（二期 T01451）：命中数实时显示 + 过滤栏收起/记忆。
// 覆盖：无过滤/有过滤的标题计数切换、过滤生效判定（搜索/状态勾选/风险实际生效）、
// 过滤栏显隐（菜单项存在、收起后两个过滤区块不渲染、命中计数保留、localStorage 记忆重挂载生效）。
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import { defaultConfig } from '../store'
import DiffTree from '../components/DiffTree.vue'

const NODES = [
  { key: 'com/foo/A.class', status: 'MODIFIED', fileClass: 'CLASS', layer: 'L1' },
  { key: 'com/foo/B.class', status: 'ADDED', fileClass: 'CLASS', layer: 'L1' },
  { key: 'lib/gson.class', status: 'UNCHANGED', fileClass: 'CLASS', layer: 'L2' }
]

function freshState() {
  state.config = defaultConfig()
  state.job = { jobId: 'j1', tree: NODES, stats: null, mode: 'package' }
  state.aiClassify = {}
  state.excludedKeys = {}
  state.ignoreRules = []
  state.archiveChildren = {}
  state.expandedArchives = {}
  state.treeLocate = null
  state.propertyNode = null
  state.baseFolder = null
  state.activeKey = null
  try { localStorage.removeItem('bempdiff.treeFilterBarVisible') } catch (_) {}
}

async function mountTree() {
  const w = mount(DiffTree, { props: { panelWidth: null } })
  await w.vm.$nextTick()
  return w
}

/** 打开「...」视图菜单并点击「显示过滤栏」开关（与用户真实交互同路径）。 */
async function toggleFilterBar(w) {
  await w.find('button[title="视图模式与排序方式"]').trigger('click')
  const item = w.findAll('.dropdown-item').find(a => a.text().includes('显示过滤栏'))
  expect(item, '视图菜单应包含「显示过滤栏」开关项').toBeTruthy()
  await item.trigger('click')
  await w.vm.$nextTick()
}

describe('R1 命中数实时显示', () => {
  beforeEach(freshState)

  it('无过滤生效：标题显示总数「3 项」，不出现「命中」', async () => {
    const w = await mountTree()
    expect(w.text()).toContain('3 项')
    expect(w.text()).not.toContain('命中')
    w.unmount()
  })

  it('搜索过滤生效：显示「命中 2 / 3 项」', async () => {
    state.config.filterSearch = 'com/foo'
    const w = await mountTree()
    expect(w.text()).toContain('命中 2 / 3 项')
    w.unmount()
  })

  it('任一状态取消勾选即视为过滤生效', async () => {
    state.config.filterShowUnchanged = false
    const w = await mountTree()
    expect(w.text()).toContain('命中 2 / 3 项')
    w.unmount()
  })

  it('风险过滤仅在实际生效时计数（未分类/全选三档均不算）', async () => {
    state.config.filterRisk = ['HIGH', 'MEDIUM', 'LOW']
    state.aiClassify = { 'com/foo/A.class': { risk: 'HIGH', category: '逻辑变更' } }
    let w = await mountTree()
    expect(w.text()).not.toContain('命中') // 全选三档 = 无过滤效果
    w.unmount()

    state.config.filterRisk = ['HIGH']
    w = await mountTree()
    expect(w.text()).toContain('命中 1 / 3 项') // 单选生效
    w.unmount()
  })
})

describe('R1 过滤栏收起与记忆', () => {
  beforeEach(freshState)
  afterEach(() => {
    try { localStorage.removeItem('bempdiff.treeFilterBarVisible') } catch (_) {}
  })

  it('「...」菜单含「显示过滤栏」开关项', async () => {
    const w = await mountTree()
    await w.find('button[title="视图模式与排序方式"]').trigger('click')
    expect(w.text()).toContain('显示过滤栏')
    w.unmount()
  })

  it('收起后搜索框与状态勾选区不渲染，命中计数保留在标题行', async () => {
    state.config.filterSearch = 'com/foo'
    const w = await mountTree()
    await toggleFilterBar(w)
    expect(w.find('.dt-toolbar').exists()).toBe(false)
    expect(w.find('.dt-filters').exists()).toBe(false)
    expect(w.text()).toContain('命中 2 / 3 项')
    w.unmount()
  })

  it('偏好写入 localStorage，重挂载后保持收起（记忆生效）', async () => {
    const w = await mountTree()
    await toggleFilterBar(w)
    w.unmount()
    expect(localStorage.getItem('bempdiff.treeFilterBarVisible')).toBe('false')
    const w2 = await mountTree()
    expect(w2.find('.dt-toolbar').exists()).toBe(false)
    w2.unmount()
  })
})
