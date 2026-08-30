// tabs.js 状态机纯函数测试：固定(pinned)逻辑 —— 置顶排序 + 关闭类操作跳过已固定页。
import { describe, it, expect } from 'vitest'
import { activateTab, closeTabReducer, pinTabReducer, closeOtherTabsReducer, closeAllTabsReducer } from '../lib/tabs'

function seed() {
  let s = { tabs: [], activeKey: null }
  for (const k of ['a', 'b', 'c']) {
    s = activateTab(s.tabs, s.activeKey, k, { key: k, status: 'MODIFIED' })
  }
  return { tabs: s.tabs, activeKey: s.activeKey } // activeKey = 'c'
}

describe('pinTabReducer 固定置顶', () => {
  it('固定 a → 置顶，activeKey 保持不变', () => {
    const base = seed() // [a,b,c], active=c
    const r = pinTabReducer(base.tabs, base.activeKey, 'a')
    expect(r.tabs.map(t => t.key)).toEqual(['a', 'b', 'c'])
    expect(r.tabs[0].pinned).toBe(true)
    expect(r.activeKey).toBe('c')
  })

  it('取消固定 → 回到普通（仍保留原相对顺序）', () => {
    const base = seed()
    const pinned = pinTabReducer(base.tabs, base.activeKey, 'b') // [b,a,c]
    const unpinned = pinTabReducer(pinned.tabs, pinned.activeKey, 'b')
    expect(unpinned.tabs.filter(t => t.pinned)).toHaveLength(0)
  })
})

describe('closeOtherTabsReducer 关闭其他', () => {
  it('保留固定页 + 指定 keepKey', () => {
    const base = seed()
    let r = pinTabReducer(base.tabs, base.activeKey, 'a') // a 固定: [a,b,c]
    r = closeOtherTabsReducer(r.tabs, r.activeKey, 'b')
    expect(r.tabs.map(t => t.key).sort()).toEqual(['a', 'b'])
  })
})

describe('closeAllTabsReducer 全部关闭', () => {
  it('无固定 → 全部清空', () => {
    const base = seed()
    const r = closeAllTabsReducer(base.tabs, base.activeKey)
    expect(r.tabs).toHaveLength(0)
    expect(r.activeKey).toBeNull()
  })
  it('有固定 → 仅保留固定页', () => {
    const base = seed()
    let r = pinTabReducer(base.tabs, base.activeKey, 'c') // c 固定
    r = closeAllTabsReducer(r.tabs, r.activeKey)
    expect(r.tabs.map(t => t.key)).toEqual(['c'])
    expect(r.activeKey).toBe('c')
  })
})

describe('closeTabReducer 关闭单个（固定页可主动关）', () => {
  it('关闭固定页也生效', () => {
    const base = seed()
    let r = pinTabReducer(base.tabs, base.activeKey, 'a') // a pinned
    r = closeTabReducer(r.tabs, r.activeKey, 'a')
    expect(r.tabs.some(t => t.key === 'a')).toBe(false)
  })
})