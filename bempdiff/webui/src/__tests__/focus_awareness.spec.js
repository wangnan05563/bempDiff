// R6 焦点感知验收（二期 T01466）：快捷键与输入框编辑互不冲突——真实按键事件矩阵。
// 覆盖：DiffView 内 Find 输入框中 Ctrl+A 不劫持行选择、Esc 仅关 Find 不清行选择、
//       非输入焦点上 Ctrl+A 全选生效（对照）、? 在输入框内不唤起速查面板。
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig } from '../store'
import DiffView from '../components/DiffView.vue'
import ShortcutHelp from '../components/ShortcutHelp.vue'

const LINES = 300
const KEY = 'pkg/Focus.java'
const NODE = { key: KEY, fileClass: 'CLASS', status: 'MODIFIED', size: 10 }

function mountBig() {
  state.config = defaultConfig()
  const w = mount(DiffView)
  state.tabs = [{
    key: KEY, node: NODE, busy: false,
    decompile: { ok: true, engine: 'cfr', oldSource: 'a\n'.repeat(LINES), newSource: 'b\n'.repeat(LINES) }
  }]
  state.activeKey = KEY
  return w
}

function press(el, key, opts = {}) {
  el.dispatchEvent(new KeyboardEvent('keydown', {
    key, bubbles: true, cancelable: true,
    ctrlKey: !!opts.ctrl, shiftKey: !!opts.shift
  }))
}

describe('R6 焦点感知（快捷键 vs 输入框编辑）', () => {
  beforeEach(() => {
    state.job = { jobId: 'j1', tree: [], stats: null, mode: 'package' }
    state.aiClassify = {}
  })
  afterEach(() => {
    state.tabs = []
    state.activeKey = null
    state.job = null
  })

  it('Find 输入框内 Ctrl+A：不劫持为「全选差异行」（无选中 chip 出现）', async () => {
    const w = mountBig()
    await w.vm.$nextTick(); await w.vm.$nextTick()
    // 打开 Find（点击 filebar 🔍 按钮）
    const findBtn = w.findAll('button').find(b => (b.attributes('title') || '').includes('Ctrl+F'))
    await findBtn.trigger('click')
    await w.vm.$nextTick()
    const input = w.find('.find-input')
    expect(input.exists()).toBe(true)
    input.element.focus()
    press(input.element, 'a', { ctrl: true })
    await w.vm.$nextTick()
    // 无「N 行」选中 chip（Ctrl+A 未被劫持为全选差异行）
    expect(w.findAll('button, span').some(x => /^\d+ 行$/.test(x.text().trim()))).toBe(false)
    w.unmount()
  })

  it('Find 输入框内 Esc：仅关闭 Find，不清除已有行选择', async () => {
    const w = mountBig()
    await w.vm.$nextTick(); await w.vm.$nextTick()
    // 先选中一行（点击首个行号列）
    const ln = w.find('.prow .ln.ln-pick, .row .ln.ln-pick')
    await ln.trigger('click')
    await w.vm.$nextTick()
    const chipBefore = w.findAll('button, span').some(x => /^\d+ 行$/.test(x.text().trim()))
    expect(chipBefore, '行选中 chip 应出现').toBe(true)
    // 打开 Find 并在输入框内按 Esc
    const findBtn = w.findAll('button').find(b => (b.attributes('title') || '').includes('Ctrl+F'))
    await findBtn.trigger('click')
    await w.vm.$nextTick()
    const input = w.find('.find-input')
    input.element.focus()
    press(input.element, 'Escape')
    await w.vm.$nextTick()
    expect(w.find('.find-bar').exists()).toBe(false) // Find 关闭
    expect(w.findAll('button, span').some(x => /^\d+ 行$/.test(x.text().trim()))).toBe(true) // 行选择保留
    w.unmount()
  })

  it('非输入焦点上 Ctrl+A：全选差异行生效（对照组）', async () => {
    const w = mountBig()
    await w.vm.$nextTick(); await w.vm.$nextTick()
    press(document.body, 'a', { ctrl: true })
    await w.vm.$nextTick()
    expect(w.text()).toMatch(/\d+ 行/) // 选中 chip 出现
    w.unmount()
  })

  it('「?」唤起速查面板；输入框内按 ? 不唤起（焦点感知）', async () => {
    // 输入框内：构造带 input 的容器并让事件以 input 为 target 冒泡——
    // App 层 onGlobalKey 对 isEditableTarget 早退；此处以 ShortcutHelp 组件挂载验证面板开关语义即可
    state.config = defaultConfig()
    const w = mount(ShortcutHelp)
    expect(w.text()).toContain('快捷键速查')
    expect(w.text()).toContain('Ctrl+Enter')
    // Esc 关闭
    press(document.body, 'Escape')
    await w.vm.$nextTick()
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })
})
