// R2 行级选中复制与差异内查找（二期 T01453）——纯函数单测。
// 覆盖：范围选择闭区间、按侧复制（跳过单侧缺失行）、行号前缀、全选、
//       searchRows 双侧命中/折叠条跳过/大小写开关/空关键字。
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { rangeSelection, buildCopyText, allRowIndexes } from '../lib/diff_select'
import { searchRows } from '../lib/diff_search'
import { state } from '../store'
import DiffView from '../components/DiffView.vue'

const ROWS = [
  { type: 'ctx', _i: 0, left: '1', leftText: 'alpha', right: '1', rightText: 'alpha' },
  { type: 'rep', _i: 1, left: '2', leftText: 'old line', right: '2', rightText: 'new line' },
  { type: 'del', _i: 2, left: '3', leftText: 'gone', right: '', rightText: '' },
  { type: 'add', _i: 3, left: '', leftText: '', right: '3', rightText: 'fresh' }
]

describe('rangeSelection 范围选择', () => {
  it('正常顺序闭区间', () => {
    expect(rangeSelection(1, 3)).toEqual([1, 2, 3])
  })
  it('倒序也返回升序闭区间', () => {
    expect(rangeSelection(3, 1)).toEqual([1, 2, 3])
  })
  it('锚点与目标相同 → 单元素', () => {
    expect(rangeSelection(2, 2)).toEqual([2])
  })
})

describe('buildCopyText 按侧复制', () => {
  it('左侧行文本（升序输出）', () => {
    expect(buildCopyText(ROWS, [2, 0], 'left')).toBe('alpha\ngone')
  })
  it('右侧跳过无新内容的行（del 行）', () => {
    expect(buildCopyText(ROWS, [1, 2, 3], 'right')).toBe('new line\nfresh')
  })
  it('左侧跳过无旧内容的行（add 行）', () => {
    expect(buildCopyText(ROWS, [3], 'left')).toBe('')
  })
  it('行号前缀格式「行号: 内容」，该侧无行号不加前缀', () => {
    expect(buildCopyText(ROWS, [1, 3], 'left', { withLineNo: true })).toBe('2: old line')
    expect(buildCopyText(ROWS, [1, 3], 'right', { withLineNo: true })).toBe('2: new line\n3: fresh')
  })
  it('空选择/空行集返回空串', () => {
    expect(buildCopyText(ROWS, [], 'left')).toBe('')
    expect(buildCopyText([], [0], 'left')).toBe('')
  })
  it('接受 Set 输入', () => {
    expect(buildCopyText(ROWS, new Set([0]), 'left')).toBe('alpha')
  })
})

describe('allRowIndexes 全选', () => {
  it('覆盖 0..n-1', () => {
    expect(allRowIndexes(4)).toEqual([0, 1, 2, 3])
    expect(allRowIndexes(0)).toEqual([])
  })
})

const FOLDED = [
  { type: 'ctx', _i: 0, leftText: 'alpha init', rightText: 'alpha init' },
  { type: 'fold', count: 5 },
  { type: 'rep', _i: 6, leftText: 'Old Value = 1', rightText: 'New Value = 2' },
  { type: 'add', _i: 7, leftText: '', rightText: 'brand new' }
]

describe('searchRows 差异内查找', () => {
  it('双侧命中记录各自 side', () => {
    const hits = searchRows(FOLDED, 'alpha')
    expect(hits).toEqual([{ di: 0, sides: ['left', 'right'] }])
  })
  it('仅单侧命中', () => {
    expect(searchRows(FOLDED, 'new value')).toEqual([{ di: 2, sides: ['right'] }])
    expect(searchRows(FOLDED, 'brand')).toEqual([{ di: 3, sides: ['right'] }])
  })
  it('折叠条不参与查找', () => {
    expect(searchRows(FOLDED, '已折叠')).toEqual([])
  })
  it('默认不区分大小写，可开关', () => {
    expect(searchRows(FOLDED, 'OLD VALUE').length).toBe(1)
    expect(searchRows(FOLDED, 'OLD VALUE', { caseSensitive: true })).toEqual([])
    expect(searchRows(FOLDED, 'Old Value', { caseSensitive: true })).toEqual([{ di: 2, sides: ['left'] }])
  })
  it('空关键字返回空且不抛错', () => {
    expect(searchRows(FOLDED, '')).toEqual([])
    expect(searchRows(FOLDED, '   ')).toEqual([])
    expect(searchRows(FOLDED, null)).toEqual([])
  })
  it('万行文件单 pass 扫描 ≤300ms（PRD R2 验收口径）', () => {
    const big = Array.from({ length: 10000 }, (_, i) => ({
      type: 'ctx', _i: i, leftText: 'line content ' + i, rightText: 'line content ' + i
    }))
    big[9999].rightText = 'needle at tail'
    const t0 = performance.now()
    const hits = searchRows(big, 'needle')
    const elapsed = performance.now() - t0
    expect(hits).toEqual([{ di: 9999, sides: ['right'] }])
    expect(elapsed, `万行查找实测 ${elapsed.toFixed(1)}ms`).toBeLessThan(300)
  })
})

// ---------------- DiffView 二进制摘要卡（T01454/T01455） ----------------

const BIN_NODE = {
  key: 'pkg/img/logo.png', fileClass: 'STATIC', status: 'MODIFIED',
  size: 7, oldSize: 5, newSize: 7, oldSha256: 'sha-old', newSha256: 'sha-new'
}

function mountWithDec(dec, node = BIN_NODE) {
  state.tabs = [{ key: node.key, node, decompile: dec, busy: false }]
  state.activeKey = node.key
  const w = mount(DiffView)
  return w
}

describe('DiffView 二进制条目摘要卡', () => {
  afterEach(() => {
    state.tabs = []
    state.activeKey = null
    state.job = null
  })

  it('二进制失败条目渲染摘要卡：内容已变化 + 双端大小', () => {
    const w = mountWithDec({ ok: false, engine: 'none', error: '内部条目为二进制（STATIC），不支持内容 diff' })
    expect(w.text()).toContain('二进制条目摘要')
    expect(w.text()).toContain('内容已变化')
    expect(w.text()).toContain('5 B')
    expect(w.text()).toContain('7 B')
    w.unmount()
  })

  it('哈希相同 → 内容一致；单侧缺失 → 无法判定', () => {
    const same = { ...BIN_NODE, oldSha256: 'sha-x', newSha256: 'sha-x' }
    let w = mountWithDec({ ok: false, engine: 'none', error: '二进制不支持内容 diff' }, same)
    expect(w.text()).toContain('内容一致')
    w.unmount()

    const half = { ...BIN_NODE, oldSha256: 'sha-x', newSha256: undefined, newSize: undefined }
    w = mountWithDec({ ok: false, engine: 'none', error: '二进制不支持内容 diff' }, half)
    expect(w.text()).toContain('无法判定')
    w.unmount()
  })

  it('非二进制失败不渲染摘要卡', () => {
    const w = mountWithDec({ ok: false, engine: 'cfr', error: '语法解析失败：unexpected token' })
    expect(w.text()).not.toContain('二进制条目摘要')
    w.unmount()
  })
})
