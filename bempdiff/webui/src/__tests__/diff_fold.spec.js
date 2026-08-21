// diff_fold（折叠未变）测试。
// 守护需求：对比栏「全量内容 / 差异内容」两种模式——
//   collapse=false：全量保留（含未变更行）；
//   collapse=true, win=3：折叠「远离变化块」的未变更段（保留变化行附近 context，BCompare 风格）；
//   collapse=true, win=0：仅差异内容模式——全部未变更行折叠成占位，只留差异行。
import { describe, it, expect } from 'vitest'
import { foldContext } from '../lib/diff_fold'

const ctx = (n) => Array.from({ length: n }, (_, i) => ({ type: 'ctx', leftText: 'c' + i }))
const mk = (...rows) => rows.flat()

describe('foldContext 折叠未变', () => {
  it('collapse=false 全量保留（ctx 与变化行都显示）', () => {
    const src = mk([{ type: 'ctx' }, { type: 'del' }, { type: 'ctx' }, { type: 'add' }, { type: 'ctx' }])
    const out = foldContext(src, false, 3)
    expect(out.filter(r => r.type === 'fold').length).toBe(0)
    expect(out.length).toBe(5)
    expect(out.filter(r => r._chg >= 0).length).toBe(2) // del/add 纳入跳转序列
    expect(out.filter(r => r.type === 'ctx').every(r => r._chg === -1)).toBe(true)
  })

  it('collapse=true 折叠远离变化块的连续 ctx（保留 win 内 context）', () => {
    // 注：foldContext 以「段首行是否落在某变化行的 win 窗口内」决定整段折叠——
    // del 前段 [0..4] 段首 0 不在 del(5) 的窗口(2..8) 内 → 整段折叠；del 后段 [6..10] 段首 6 在窗口内 → 逐个保留 6,7,8，9 超出 → 折叠。
    const src = mk(ctx(5), [{ type: 'del' }], ctx(5))
    const out = foldContext(src, true, 3)
    expect(out.filter(r => r.type === 'fold').length).toBe(2)
    expect(out.filter(r => r.type === 'ctx').length).toBe(3)
    expect(out.filter(r => r.type === 'fold').map(r => r.count)).toEqual([5, 2])
    expect(out.filter(r => r.type === 'del').length).toBe(1)
  })

  it('win=0（仅差异内容模式）：全部 ctx 折叠成占位，只留差异行', () => {
    const src = mk(ctx(5), [{ type: 'del' }], ctx(3), [{ type: 'add' }], ctx(4), [{ type: 'rep' }], ctx(2))
    const out = foldContext(src, true, 0)
    expect(out.filter(r => r.type === 'ctx').length).toBe(0) // 无未变更行残留
    expect(out.filter(r => r.type === 'fold').length).toBe(4) // 每段 ctx 一条占位
    expect(out.filter(r => r.type === 'fold').map(r => r.count)).toEqual([5, 3, 4, 2])
    expect(out.filter(r => r.type === 'del' || r.type === 'add' || r.type === 'rep').length).toBe(3)
    expect(out.filter(r => r._chg >= 0).map(r => r._chg)).toEqual([0, 1, 2]) // 跳转序号连续
  })

  it('仅差异模式下文件首尾的 ctx 段也折叠', () => {
    const src = mk(ctx(2), [{ type: 'add' }], ctx(2))
    const out = foldContext(src, true, 0)
    expect(out.map(r => r.type)).toEqual(['fold', 'add', 'fold'])
  })

  it('无差异（全 ctx）时 win=0 折叠成单条占位', () => {
    const out = foldContext(ctx(8), true, 0)
    expect(out.length).toBe(1)
    expect(out[0].type).toBe('fold')
    expect(out[0].count).toBe(8)
  })

  it('空数组 → 空输出', () => {
    expect(foldContext([], true, 0)).toEqual([])
  })
})
