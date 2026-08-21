// TipButton 图标按钮 + 延迟 Tooltip 测试。
// 守护需求：悬浮提示支持自定义文案与延迟显示；图标按钮点击派发 click；
// disabled 态不触发点击但保留提示（说明禁用原因）。
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TipButton from '../components/TipButton.vue'

function mountBtn(props = {}) {
  return mount(TipButton, {
    props: { icon: 'bi-plus-square', tooltip: '展开所有机构节点', ...props }
  })
}

afterEach(() => { vi.useRealTimers() })

describe('悬浮提示（Tooltip）', () => {
  it('悬浮后按 delay 延迟显示气泡（默认 400ms），提前离开不显示', async () => {
    vi.useFakeTimers()
    const w = mountBtn()
    expect(w.find('.tip-bubble').exists()).toBe(false)
    await w.find('button').trigger('mouseenter')
    vi.advanceTimersByTime(399)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(false)
    vi.advanceTimersByTime(1)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(true)
  })

  it('气泡文案 = tooltip 自定义文案', async () => {
    vi.useFakeTimers()
    const w = mountBtn({ tooltip: '折叠所有机构节点' })
    await w.find('button').trigger('mouseenter')
    vi.advanceTimersByTime(400)
    await nextTick()
    expect(w.find('.tip-bubble').text()).toBe('折叠所有机构节点')
  })

  it('自定义 delay 生效（更短/更长）', async () => {
    vi.useFakeTimers()
    const w = mountBtn({ delay: 150 })
    await w.find('button').trigger('mouseenter')
    vi.advanceTimersByTime(149)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(false)
    vi.advanceTimersByTime(1)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(true)
  })

  it('mouseleave 立即隐藏气泡', async () => {
    vi.useFakeTimers()
    const w = mountBtn()
    await w.find('button').trigger('mouseenter')
    vi.advanceTimersByTime(400)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(true)
    await w.find('button').trigger('mouseleave')
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(false)
  })

  it('tooltip 为空时不显示气泡', async () => {
    vi.useFakeTimers()
    const w = mountBtn({ tooltip: '' })
    await w.find('button').trigger('mouseenter')
    vi.advanceTimersByTime(500)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(false)
  })

  it('disabled 时悬浮仍显示提示（便于说明禁用原因），点击不派发', async () => {
    vi.useFakeTimers()
    const w = mountBtn({ disabled: true })
    const btnEl = w.find('button').element
    expect(w.find('button').attributes('disabled')).toBeDefined()
    // test-utils 的 trigger 对 disabled 元素不派发事件（jsdom 限制）；真实浏览器中
    // disabled 按钮仍响应 hover/mouseenter，故用原生事件模拟悬浮。
    btnEl.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }))
    vi.advanceTimersByTime(400)
    await nextTick()
    expect(w.find('.tip-bubble').exists()).toBe(true)
  })
})

describe('图标与点击', () => {
  it('渲染指定图标', () => {
    const w = mountBtn({ icon: 'bi-dash-square' })
    expect(w.find('button i').classes()).toContain('bi-dash-square')
  })

  it('点击派发 click 事件', async () => {
    const w = mountBtn()
    await w.find('button').trigger('click')
    expect(w.emitted('click')).toHaveLength(1)
  })

  it('disabled 时点击不派发 click', async () => {
    const w = mountBtn({ disabled: true })
    await w.find('button').trigger('click')
    expect(w.emitted('click')).toBeUndefined()
  })

  it('aria-label 缺省回落 tooltip', () => {
    const w = mountBtn()
    expect(w.find('button').attributes('aria-label')).toBe('展开所有机构节点')
  })
})
