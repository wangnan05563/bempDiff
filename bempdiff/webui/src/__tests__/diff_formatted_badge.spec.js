// T01810 FR-006 展示层：AST 口径「格式或位置变更」徽标——
// 被折叠为 UNCHANGED 的格式化/重排条目在差异树上显式可辨，不与普通未变混淆。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig } from '../store'
import DiffTree from '../components/DiffTree.vue'

function tree() {
  return [
    { key: 'src/Svc.java', status: 'UNCHANGED', fileClass: 'OTHER', layer: 'L0', size: 100, formattedOnly: true },
    { key: 'src/Plain.java', status: 'UNCHANGED', fileClass: 'OTHER', layer: 'L0', size: 100 },
    { key: 'src/Real.java', status: 'MODIFIED', fileClass: 'CLASS', layer: 'L0', size: 100 }
  ]
}

describe('FR-006 格式或位置变更徽标（T01810）', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.config.filterShowUnchanged = true
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
  })

  it('formattedOnly 节点显示徽标，普通未变/实质变更节点不显示', async () => {
    state.job = { jobId: 'j-fmt', tree: tree(), stats: null, mode: 'folder' }
    const w = mount(DiffTree, { props: { panelWidth: null } })
    await w.vm.$nextTick()
    const badges = w.findAll('.badge').filter(b => b.text().includes('格式或位置变更'))
    expect(badges.length, '恰 1 个 formattedOnly 徽标').toBe(1)
    // 徽标归属正确：仅 Svc.java 行渲染
    const svcRow = w.findAll('.tree-node').find(r => r.text().includes('Svc.java'))
    expect(svcRow, 'Svc.java 行应渲染').toBeTruthy()
    expect(svcRow.text()).toContain('格式或位置变更')
    const plainRow = w.findAll('.tree-node').find(r => r.text().includes('Plain.java'))
    expect(plainRow.text(), '普通未变不应带徽标').not.toContain('格式或位置变更')
    w.unmount()
  })

  it('后端未下发 formattedOnly 字段时（false 缺省）不渲染徽标', async () => {
    state.job = { jobId: 'j-fmt2', tree: tree().map(n => ({ ...n, formattedOnly: undefined })), stats: null, mode: 'folder' }
    const w = mount(DiffTree, { props: { panelWidth: null } })
    await w.vm.$nextTick()
    const badges = w.findAll('.badge').filter(b => b.text().includes('格式或位置变更'))
    expect(badges.length, '无字段不应渲染徽标').toBe(0)
    w.unmount()
  })
})
