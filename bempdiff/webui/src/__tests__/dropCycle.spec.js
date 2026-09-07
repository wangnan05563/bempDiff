// 拖拽循环状态机（dropCycle）单测：未填→老包、老包已填新包空→新包、都填→重置回老包、
// 严格「奇数次老包、偶数次新包」。
import { describe, it, expect } from 'vitest'
import { createDropCycle } from '../lib/dropCycle'

describe('dropCycle 状态机', () => {
  it('路径均未填时等待老包；循环填 老→新→老→新（奇老偶新）', () => {
    const paths = { old: '', new: '' }
    const c = createDropCycle(() => paths.old, () => paths.new)
    expect(c.peek()).toBe('old')
    expect(c.claim()).toBe('old') // 第1次 → 老包
    paths.old = 'a.zip'
    expect(c.peek()).toBe('new')
    expect(c.claim()).toBe('new') // 第2次 → 新包
    paths.new = 'b.zip'
    expect(c.peek()).toBe('old') // 两路径齐备 → 回老包
    expect(c.claim()).toBe('old') // 第3次 → 老包
    paths.old = 'c.zip'
    expect(c.claim()).toBe('new') // 第4次 → 新包
  })

  it('老包已填、新包为空 → 直接等待新包', () => {
    const paths = { old: 'a.zip', new: '' }
    const c = createDropCycle(() => paths.old, () => paths.new)
    expect(c.peek()).toBe('new')
    expect(c.claim()).toBe('new')
  })

  it('两个均已填 → 重置回等待老包', () => {
    const paths = { old: 'a.zip', new: 'b.zip' }
    const c = createDropCycle(() => paths.old, () => paths.new)
    expect(c.peek()).toBe('old')
    expect(c.claim()).toBe('old')
  })
})