// R6 快捷键体系（二期 T01465）——数据完整性与聚焦联动测试。
// 覆盖：SHORTCUTS 清单结构（键位/说明/作用域齐全且无重复键位）、分组函数、
//       Ctrl+K 信号驱动 DiffTree 过滤框聚焦（过滤栏收起时先展开再聚焦）。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { SHORTCUTS, groupByScope, isEditableTarget } from '../lib/shortcuts'
import { state, defaultConfig } from '../store'
import DiffTree from '../components/DiffTree.vue'

describe('R6 快捷键注册表', () => {
  it('每条含 keys/label/scope 且键位无重复', () => {
    const seen = new Set()
    for (const s of SHORTCUTS) {
      expect(s.keys, '键位非空').toBeTruthy()
      expect(s.label, '说明非空').toBeTruthy()
      expect(s.scope, '作用域非空').toBeTruthy()
      expect(seen.has(s.keys), `键位重复：${s.keys}`).toBe(false)
      seen.add(s.keys)
    }
    expect(SHORTCUTS.length).toBeGreaterThanOrEqual(8)
  })

  it('groupByScope 保持出现顺序并正确归组', () => {
    const g = groupByScope(SHORTCUTS)
    expect(g[0].scope).toBe('全局')
    expect(g[g.length - 1].scope).toBe('差异视图')
    const total = g.reduce((n, x) => n + x.items.length, 0)
    expect(total).toBe(SHORTCUTS.length)
  })

  it('isEditableTarget：输入框/文本域/可编辑元素命中，普通元素不命中', () => {
    const mk = (tagName, editable = false) => ({ target: { tagName, isContentEditable: editable } })
    expect(isEditableTarget(mk('INPUT'))).toBe(true)
    expect(isEditableTarget(mk('TEXTAREA'))).toBe(true)
    expect(isEditableTarget(mk('DIV', true))).toBe(true)
    expect(isEditableTarget(mk('DIV'))).toBe(false)
    expect(isEditableTarget(null)).toBe(false)
  })
})

describe('R6 Ctrl+K 聚焦差异树过滤框', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.job = { jobId: 'j1', tree: [], stats: null, mode: 'package' }
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

  it('信号自增后过滤输入框获得焦点', async () => {
    const w = mount(DiffTree, { props: { panelWidth: null }, attachTo: document.body })
    await w.vm.$nextTick()
    state.treeFilterFocusTick++
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    const input = w.find('input[aria-label="搜索文件名"]')
    expect(input.exists()).toBe(true)
    expect(document.activeElement).toBe(input.element)
    w.unmount()
  })

  it('过滤栏收起时 Ctrl+K 先展开过滤栏再聚焦', async () => {
    const w = mount(DiffTree, { props: { panelWidth: null }, attachTo: document.body })
    await w.vm.$nextTick()
    // 收起过滤栏
    const menuBtn = w.find('button[title="视图模式与排序方式"]')
    await menuBtn.trigger('click')
    const item = w.findAll('.dropdown-item').find(a => a.text().includes('显示过滤栏'))
    await item.trigger('click')
    await w.vm.$nextTick()
    expect(w.find('input[aria-label="搜索文件名"]').exists()).toBe(false)
    // Ctrl+K 信号 → 展开 + 聚焦
    state.treeFilterFocusTick++
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    const input = w.find('input[aria-label="搜索文件名"]')
    expect(input.exists()).toBe(true)
    expect(document.activeElement).toBe(input.element)
    try { localStorage.removeItem('bempdiff.treeFilterBarVisible') } catch (_) {}
    w.unmount()
  })
})
