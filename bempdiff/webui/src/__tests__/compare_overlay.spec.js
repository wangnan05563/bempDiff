// R3 比对进度三段化（二期 T01457）：CompareOverlay 步骤指示器。
// 覆盖：phase 映射（parsing/unpacking→解包、diffing→比对、building→出树）、当前段高亮与
// 已完成段打勾、phase 缺失退化为单条进度条（向后兼容）、报告态不走确定进度。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import CompareOverlay from '../components/CompareOverlay.vue'

function mountOverlay() {
  const w = mount(CompareOverlay)
  return w
}

describe('R3 三段进度指示', () => {
  beforeEach(() => {
    state.busy = false
    state.reporting = false
    state.busyText = ''
    state.jobProgress = null
  })

  it('diffing 阶段：解包/出树弱化，当前段「比对」高亮，已完成「解包」打勾', () => {
    state.busy = true
    state.jobProgress = { status: 'RUNNING', phase: 'diffing', progress: 70, message: '计算差异…' }
    const w = mountOverlay()
    expect(w.find('.cmp-steps').exists()).toBe(true)
    const steps = w.findAll('.cmp-step')
    expect(steps.map(s => s.text().trim())).toEqual(['解包', '比对', '出树'])
    expect(steps[0].classes()).toContain('cmp-step-done')
    expect(steps[1].classes()).toContain('cmp-step-active')
    expect(steps[2].classes()).not.toContain('cmp-step-active')
    expect(w.text()).toContain('70%')
    w.unmount()
  })

  it('unpacking 阶段映射到「解包」段', () => {
    state.busy = true
    state.jobProgress = { status: 'RUNNING', phase: 'unpacking', progress: 35, message: '正在逐层解包（旧侧）…' }
    const w = mountOverlay()
    const steps = w.findAll('.cmp-step')
    expect(steps[0].classes()).toContain('cmp-step-active')
    w.unmount()
  })

  it('building 阶段映射到「出树」段，前两段均打勾', () => {
    state.busy = true
    state.jobProgress = { status: 'RUNNING', phase: 'building', progress: 90, message: '构建差异树…' }
    const w = mountOverlay()
    const steps = w.findAll('.cmp-step')
    expect(steps[0].classes()).toContain('cmp-step-done')
    expect(steps[1].classes()).toContain('cmp-step-done')
    expect(steps[2].classes()).toContain('cmp-step-active')
    w.unmount()
  })

  it('phase 缺失（旧后端兼容）：不渲染步骤指示器，仅单条进度条', () => {
    state.busy = true
    state.jobProgress = { status: 'RUNNING', progress: 50, message: '处理中' }
    const w = mountOverlay()
    expect(w.find('.cmp-steps').exists()).toBe(false)
    expect(w.find('.progress-bar').exists()).toBe(true)
    expect(w.text()).toContain('50%')
    w.unmount()
  })

  it('报告态（reporting）不走比对进度分支', () => {
    state.busy = true
    state.reporting = true
    state.jobProgress = { status: 'RUNNING', phase: 'diffing', progress: 70 }
    const w = mountOverlay()
    expect(w.find('.cmp-steps').exists()).toBe(false)
    w.unmount()
  })

  it('非比对 busy 态不显示任何进度数据', () => {
    state.busy = true
    state.jobProgress = null
    const w = mountOverlay()
    expect(w.find('.cmp-steps').exists()).toBe(false)
    expect(w.text()).not.toContain('取消比对')
    w.unmount()
  })
})
