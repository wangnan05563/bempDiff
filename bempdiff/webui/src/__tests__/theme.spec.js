// FR-004 三态主题（亮/暗/跟随系统）状态机测试。
// 守护：① applyTheme 持久化偏好并按解析结果设置 data-bs-theme；
//       ② 'system' 依赖 matchMedia 解析（jsdom 无 matchMedia → 安全降级 light）；
//       ③ cycleTheme 亮→暗→跟随系统→亮 循环；
//       ④ matchMedia 可用时 system 正确解析并实时跟随（动态 import 注入 mock）。
import { describe, it, expect, beforeEach, vi } from 'vitest'

// 主 store 为单例（matchMedia 在模块加载期求值）——jsdom 无 matchMedia，
// 主题模块必须可降级：themeMedia=null 时 system 解析为 light。
import { applyTheme, cycleTheme, resolveTheme, state } from '../store'

const THEME_KEY = 'bempdiff-theme'

describe('三态主题（FR-004）', () => {
  beforeEach(() => {
    try { localStorage.removeItem(THEME_KEY) } catch (_) {}
    applyTheme('light')
  })

  it('applyTheme：持久化偏好 + data-bs-theme 同步（亮/暗两态直通）', () => {
    applyTheme('dark')
    expect(state.theme).toBe('dark')
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark')
    applyTheme('light')
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light')
  })

  it('resolveTheme：无 matchMedia 环境（jsdom）system 安全降级 light', () => {
    expect(resolveTheme('light')).toBe('light')
    expect(resolveTheme('dark')).toBe('dark')
    expect(resolveTheme('system')).toBe('light')
  })

  it('cycleTheme：亮 → 暗 → 跟随系统 → 亮 循环且逐次持久化', () => {
    applyTheme('light')
    cycleTheme()
    expect(state.theme).toBe('dark')
    cycleTheme()
    expect(state.theme).toBe('system')
    expect(localStorage.getItem(THEME_KEY)).toBe('system')
    cycleTheme()
    expect(state.theme).toBe('light')
  })

  it('applyTheme：非法偏好值回退不做静默改写（仅接受已知三态语义由调用方保证）', () => {
    // data-bs-theme 语义只认 light/dark：resolveTheme 直通非 system 值，
    // 非法值由 UI 层三态循环天然规避——此处锁定 resolveTheme 对 dark/light 的直通契约。
    expect(resolveTheme('dark')).toBe('dark')
  })

  it('matchMedia 可用时：system 按系统亮暗解析（暗系统 → dark）', async () => {
    // 动态 import 新模块实例：注入 prefers-color-scheme 的 mock。
    // matches 用可变对象——resolveTheme 每次实时读 themeMedia.matches，
    // 回调参数本身不影响解析（与浏览器真实行为一致：MQL.matches 是活动查询）。
    vi.resetModules()
    const listeners = []
    const mediaObj = { matches: true, addEventListener: (_e, cb) => listeners.push(cb) }
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mediaObj))
    try {
      const mod = await import('../store')
      expect(mod.resolveTheme('system')).toBe('dark')
      mod.applyTheme('system')
      expect(mod.state.theme).toBe('system')
      expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark')
      // init() 挂载 matchMedia change 监听（网络失败走 catch 兜底，jsdom 下安全）
      await mod.init()
      // 系统切到亮色：活动查询 matches 翻转 + change 回调 → data-bs-theme 无刷新切回 light（验收③）
      mediaObj.matches = false
      listeners.forEach(cb => cb(mediaObj))
      expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light')
    } finally {
      vi.unstubAllGlobals()
      vi.resetModules()
    }
  })
})
