// R10 主题强调色（二期 T01475）——色盘数据、持久化与应用链路测试。
import { describe, it, expect, beforeEach } from 'vitest'
import { ACCENTS, accentOf, loadAccentKey, applyAccent, ACCENT_KEY } from '../lib/accents'
import { state } from '../store'

describe('R10 强调色色盘', () => {
  beforeEach(() => {
    try { localStorage.removeItem(ACCENT_KEY) } catch (_) {}
  })

  it('预设 6 色：key/color/rgb 齐全且无重复，含 Bootstrap 默认蓝', () => {
    expect(ACCENTS.length).toBe(6)
    const keys = new Set()
    for (const a of ACCENTS) {
      expect(a.key).toBeTruthy()
      expect(a.name).toBeTruthy()
      expect(a.color).toMatch(/^#[0-9a-f]{6}$/i)
      expect(a.rgb).toMatch(/^\d+, \d+, \d+$/)
      keys.add(a.key)
    }
    expect(keys.size).toBe(6)
    expect(ACCENTS[0].color.toLowerCase()).toBe('#0d6efd')
  })

  it('accentOf：合法 key 命中，非法 key 回退经典蓝', () => {
    expect(accentOf('purple').name).toBe('典雅紫')
    expect(accentOf('nonexistent').key).toBe('blue')
  })

  it('applyAccent：设置 html data-accent 与 CSS 变量并持久化', () => {
    const a = applyAccent('purple')
    expect(a.key).toBe('purple')
    expect(document.documentElement.getAttribute('data-accent')).toBe('purple')
    expect(document.documentElement.style.getPropertyValue('--acc')).toBe('#7c5cff')
    expect(document.documentElement.style.getPropertyValue('--acc-rgb')).toBe('124, 92, 255')
    expect(localStorage.getItem(ACCENT_KEY)).toBe('purple')
  })

  it('loadAccentKey：持久化读回；坏数据回退 blue', () => {
    applyAccent('teal')
    expect(loadAccentKey()).toBe('teal')
    localStorage.setItem(ACCENT_KEY, 'hacked')
    expect(loadAccentKey()).toBe('blue')
  })

  it('重复应用（重启模拟）：data-accent 稳定恢复', () => {
    applyAccent('rose')
    // 模拟重启：仅凭 loadAccentKey 恢复
    const key = loadAccentKey()
    applyAccent(key)
    expect(document.documentElement.getAttribute('data-accent')).toBe('rose')
  })
})
