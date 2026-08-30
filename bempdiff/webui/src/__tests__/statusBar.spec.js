// 状态栏「比对进行中」提示 + 超时提醒：覆盖解析/解包/比对各阶段，并与「比对尚未完成: RUNNING」逻辑呼应。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import StatusBar from '../components/StatusBar.vue'

describe('StatusBar 比对进行中提示', () => {
  beforeEach(() => {
    state.jobProgress = null
    state.compareStalled = false
    state.job = null
    state.exporting = false
  })

  it('解包阶段显示「正在自动迭代解包」与进度/引导文案', () => {
    state.jobProgress = { status: 'RUNNING', phase: 'unpacking', progress: 40, message: '正在逐层解包' }
    const w = mount(StatusBar)
    expect(w.text()).toContain('正在自动迭代解包…')
    expect(w.text()).toContain('40%')
    expect(w.text()).toContain('比对尚未完成: RUNNING')
    w.unmount()
  })

  it('解析/比对阶段也显示「比对进行中」归属文案', () => {
    state.jobProgress = { status: 'RUNNING', phase: 'parsing', progress: 10, message: '解析包（旧）…' }
    const w = mount(StatusBar)
    expect(w.text()).toContain('正在解析包…')
    expect(w.text()).toContain('比对尚未完成: RUNNING')
    w.unmount()
  })

  it('非运行态不显示运行提示', () => {
    state.jobProgress = { status: 'DONE', phase: '', progress: 100 }
    const w = mount(StatusBar)
    expect(w.text()).not.toContain('比对尚未完成')
    w.unmount()
  })

  it('比对超时（RUNNING 超过阈值）显示提醒', () => {
    state.jobProgress = { status: 'RUNNING', phase: 'diffing', progress: 70, message: '' }
    state.compareStalled = true
    const w = mount(StatusBar)
    expect(w.text()).toContain('已进行超过 5 分钟仍未完成')
    expect(w.text()).toContain('请检查磁盘/网络后重启工具')
    w.unmount()
  })

  it('导出进行中显示「导出中」并提供取消入口', () => {
    state.exporting = true
    const w = mount(StatusBar)
    expect(w.text()).toContain('导出中')
    expect(w.find('button[title="取消导出"]').exists()).toBe(true)
    w.unmount()
  })

  it('导出完成/错误后清除「导出中」提示', () => {
    state.exporting = false
    const w = mount(StatusBar)
    expect(w.text()).not.toContain('导出中')
    w.unmount()
  })
})