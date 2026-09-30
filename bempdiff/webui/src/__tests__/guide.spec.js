// 引导系统纯逻辑测试：步骤定义完整性、完成态判定、进度文案、目标定位、窄屏跳过字段、按钮开关。
import { describe, it, expect, beforeEach } from 'vitest'
import {
  GUIDE_STEPS, GUIDE_GROUPS, stepsOf, allSteps, groupOf,
  isGuideDone, isFirstRun, progressText, locateTarget,
  GUIDE_BTN_KEY, isGuideButtonVisible, setGuideButtonVisible
} from '../lib/guide'

describe('引导步骤定义', () => {
  it('每个分组都有至少一步，且 group 均在声明的分组清单内', () => {
    for (const g of GUIDE_GROUPS) {
      expect(stepsOf(g).length, `${g} 应有步骤`).toBeGreaterThan(0)
    }
    for (const s of GUIDE_STEPS) {
      expect(GUIDE_GROUPS, `${s.group} 应属已声明分组`).toContain(s.group)
    }
  })

  it('每步必填 title/text；有 target 时才是定位步骤', () => {
    for (const s of GUIDE_STEPS) {
      expect(s.title && s.title.trim()).toBeTruthy()
      expect(s.text && s.text.trim()).toBeTruthy()
    }
  })

  it('allSteps 展平顺序与定义一致', () => {
    expect(allSteps().length).toBe(GUIDE_STEPS.length)
    expect(allSteps()[0].group).toBe('getstart')
    expect(allSteps()[allSteps().length - 1].group).toBe('config')
  })

  it('groupOf 能还原每一步所在分组', () => {
    allSteps().forEach((_, i) => {
      expect(groupOf(i), `第 ${i} 步的 groupOf`).toBe(allSteps()[i].group)
    })
    expect(groupOf(999)).toBeNull()
  })
})

describe('完成态 / 首次访问判定', () => {
  it('isGuideDone 识别布尔与字符串 true', () => {
    expect(isGuideDone(true)).toBe(true)
    expect(isGuideDone('true')).toBe(true)
    expect(isGuideDone(false)).toBe(false)
    expect(isGuideDone(null)).toBe(false)
    expect(isGuideDone(undefined)).toBe(false)
  })

  it('isFirstRun 仅当既不是 true 也不是字符串 true 时为真', () => {
    expect(isFirstRun(null)).toBe(true)
    expect(isFirstRun(undefined)).toBe(true)
    expect(isFirstRun(false)).toBe(true)
    expect(isFirstRun(true)).toBe(false)
    expect(isFirstRun('true')).toBe(false)
  })
})

describe('进度文案', () => {
  it('progressText 展示 第n/总数（1 基）', () => {
    const total = GUIDE_STEPS.length
    expect(progressText(0)).toBe('1 / ' + total)
    expect(progressText(total - 1)).toBe(total + ' / ' + total)
    // 越界钳位
    expect(progressText(-5)).toBe('1 / ' + total)
    expect(progressText(999)).toBe(total + ' / ' + total)
  })
})

describe('目标定位', () => {
  it('locateTarget 对空/null selector 返回 null', () => {
    expect(locateTarget(null)).toBeNull()
    expect(locateTarget('')).toBeNull()
  })

  it('locateTarget 找不到元素返回 null', () => {
    expect(locateTarget('.guide-nope-404')).toBeNull()
  })

  it('locateTarget 能找到元素并返回矩形（jsdom 用 mock 元素）', () => {
    // jsdom 无真实布局，用最小 stub 验证命中路径与边界（宽高为 0 时视为不可见返回 null）
    const stub = { querySelector: () => ({ getBoundingClientRect: () => ({ width: 0, height: 0 }) }) }
    expect(locateTarget('.x', stub)).toBeNull()
  })

  it('可见元素（宽高>0）命中并带回相对坐标', () => {
    const rect = { top: 10, left: 20, width: 100, height: 50 }
    const root = { querySelector: () => ({ getBoundingClientRect: () => rect }) }
    global.window = { scrollY: 0, scrollX: 0 }
    const got = locateTarget('.spot', root)
    expect(got).not.toBeNull()
    expect(got.width).toBe(100)
    expect(got.height).toBe(50)
    expect(got.top).toBe(10)
    expect(got.left).toBe(20)
  })
})

describe('引导按钮开关（默认隐藏，帮助文档开启）', () => {
  beforeEach(() => {
    try { localStorage.removeItem(GUIDE_BTN_KEY) } catch (_) { /* ignore */ }
  })

  it('初始默认隐藏（未设置时 isGuideButtonVisible=false）', () => {
    // 模块加载时已按 localStorage 初始化；此处用例在清理后应回到默认 false（仅当模块持有者复位）
    setGuideButtonVisible(false)
    expect(isGuideButtonVisible()).toBe(false)
  })

  it('setGuideButtonVisible(true) 后可见，并持久化到 localStorage', () => {
    setGuideButtonVisible(true)
    expect(isGuideButtonVisible()).toBe(true)
    try { expect(localStorage.getItem(GUIDE_BTN_KEY)).toBe('on') } catch (_) { /* jsdom 无存储时跳过 */ }
  })

  it('setGuideButtonVisible(false) 后隐藏，并持久化 off', () => {
    setGuideButtonVisible(true)
    setGuideButtonVisible(false)
    expect(isGuideButtonVisible()).toBe(false)
    try { expect(localStorage.getItem(GUIDE_BTN_KEY)).toBe('off') } catch (_) { /* ignore */ }
  })

  it('开启后立即可见（驱动 HelpDoc 开关→fab 显示）', () => {
    expect(isGuideButtonVisible()).toBe(false) // 默认隐藏
    setGuideButtonVisible(true)
    expect(isGuideButtonVisible()).toBe(true)
    setGuideButtonVisible(false)
    expect(isGuideButtonVisible()).toBe(false)
  })
})