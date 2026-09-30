// R5 比对会话历史（二期 T01463）——store 级测试。
// 覆盖：push 记录与去重（同路径对刷新时间戳）、20 条滚动上限、localStorage 持久化（跨实例装载）、
//       删除（含越界静默）、恢复回填（左右路径 + 输入类型）、清空。
import { describe, it, expect, beforeEach } from 'vitest'
import { state, pushCompareHistory, loadCompareHistory, removeCompareHistory, restoreCompareHistory, clearCompareHistory } from '../store'

const KEY = 'bempdiff.compareHistory'

function fresh() {
  state.compareHistory = []
  state.oldPath = ''
  state.newPath = ''
  state.leftType = 'package'
  try { localStorage.removeItem(KEY) } catch (_) {}
}

describe('R5 会话历史记录', () => {
  beforeEach(fresh)

  it('push 记录路径对与输入类型，最新在前', () => {
    pushCompareHistory('D:/a-v1.war', 'D:/a-v2.war', 'package')
    pushCompareHistory('E:/dir1', 'E:/dir2', 'folder')
    const h = loadCompareHistory()
    expect(h.length).toBe(2)
    expect(h[0]).toMatchObject({ oldPath: 'E:/dir1', newPath: 'E:/dir2', leftType: 'folder' })
    expect(h[1].oldPath).toBe('D:/a-v1.war')
  })

  it('连续相同路径对去重：只刷新时间戳不重复插入', () => {
    pushCompareHistory('D:/a.war', 'D:/b.war', 'package')
    const first = loadCompareHistory()[0]
    pushCompareHistory('D:/a.war', 'D:/b.war', 'package')
    const h = loadCompareHistory()
    expect(h.length).toBe(1)
    expect(h[0].at).toBeGreaterThanOrEqual(first.at)
  })

  it('滚动上限 20 条（最旧被淘汰）', () => {
    for (let i = 0; i < 25; i++) pushCompareHistory(`D:/o${i}.war`, `D:/n${i}.war`, 'package')
    const h = loadCompareHistory()
    expect(h.length).toBe(20)
    expect(h[0].oldPath).toBe('D:/o24.war')
    expect(h[19].oldPath).toBe('D:/o5.war') // 前 5 条被挤出
  })

  it('持久化 localStorage，重装载（模拟重启）可读回', () => {
    pushCompareHistory('D:/a-v1.war', 'D:/a-v2.war', 'package')
    expect(JSON.parse(localStorage.getItem(KEY)).length).toBe(1)
    state.compareHistory = [] // 模拟重启后内存清零
    const h = loadCompareHistory()
    expect(h.length).toBe(1)
    expect(h[0].newPath).toBe('D:/a-v2.war')
  })
})

describe('R5 会话恢复与删除', () => {
  beforeEach(fresh)

  it('恢复回填左右路径与输入类型（不自动比对）', () => {
    pushCompareHistory('E:/old-dir', 'E:/new-dir', 'folder')
    expect(restoreCompareHistory(0)).toBe(true)
    expect(state.oldPath).toBe('E:/old-dir')
    expect(state.newPath).toBe('E:/new-dir')
    expect(state.leftType).toBe('folder')
  })

  it('删除指定条目；越界索引静默', () => {
    pushCompareHistory('D:/a.war', 'D:/b.war', 'package')
    pushCompareHistory('D:/c.war', 'D:/d.war', 'package')
    removeCompareHistory(0)
    expect(loadCompareHistory().length).toBe(1)
    expect(loadCompareHistory()[0].oldPath).toBe('D:/a.war')
    removeCompareHistory(99)
    expect(loadCompareHistory().length).toBe(1)
  })

  it('清空后内存与 localStorage 均为空', () => {
    pushCompareHistory('D:/a.war', 'D:/b.war', 'package')
    clearCompareHistory()
    expect(state.compareHistory.length).toBe(0)
    expect(localStorage.getItem(KEY)).toBeNull()
  })

  it('空路径 push 静默忽略', () => {
    pushCompareHistory('', 'D:/b.war', 'package')
    expect(loadCompareHistory().length).toBe(0)
  })
})
