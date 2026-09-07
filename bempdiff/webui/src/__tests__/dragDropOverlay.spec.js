// 全屏拖拽遮罩（DragDropOverlay）组件单测：文件拖入弹出遮罩提示对应包类型，
// 落地自动填入老包/新包路径、奇数/偶数循环、遮罩随 leave/drop 收起。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// mock store：state 用普通对象即可（组件模板只依赖自身 ref，测试只读 state 落值）。
const { state, triggerCompare, toast } = vi.hoisted(() => ({
  state: { oldPath: '', newPath: '', leftType: 'package' },
  triggerCompare: vi.fn(),
  toast: vi.fn()
}))
vi.mock('../store', () => ({
  state,
  inferType: (p) => (/\.(war|jar|zip|ear)$/i.test(p || '') ? 'package' : 'folder'),
  triggerCompare,
  toast
}))

import DragDropOverlay from '../components/DragDropOverlay.vue'

// 构造一个带 dataTransfer（模拟 File 拖拽）的调度对象。jsdom 未暴露 DataTransfer 全局，
// 这里用最小结构：types=['Files'] 供 hasFiles() 判定，files[0] 供路径提取。
function makeFileDrag(path) {
  const name = (path || 'x.zip').split('/').pop() || 'x.zip'
  const file = new File(['x'], name, { type: 'application/zip' })
  if (path) Object.defineProperty(file, 'path', { value: path, configurable: true })
  return { types: ['Files'], files: [file], items: { add: () => {} } }
}
function send(type, dt) {
  const ev = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(ev, 'dataTransfer', { value: dt })
  window.dispatchEvent(ev)
  return new Promise((r) => setTimeout(r, 0))
}

beforeEach(() => {
  state.oldPath = ''
  state.newPath = ''
  state.leftType = 'package'
  triggerCompare.mockClear()
  toast.mockClear()
})

describe('DragDropOverlay 拖拽循环', () => {
  it('拖入文件弹出遮罩并提示「请拖入老包」；落地填入老包路径并收起', async () => {
    const w = mount(DragDropOverlay)
    await send('dragenter', makeFileDrag())
    await nextTick()
    expect(w.find('.drop-mask').exists()).toBe(true)
    expect(w.find('.drop-mask-title').text()).toBe('请拖入老包')

    await send('drop', makeFileDrag('C:/pkg/v1.war'))
    await nextTick()
    expect(w.find('.drop-mask').exists()).toBe(false)
    expect(state.oldPath).toBe('C:/pkg/v1.war')
    expect(state.leftType).toBe('package')
    w.unmount()
  })

  it('第2次拖拽提示「请拖入新包」并填入新包路径', async () => {
    const w = mount(DragDropOverlay)
    await send('drop', makeFileDrag('C:/pkg/v1.war')) // 第1次→老包
    await nextTick()

    await send('dragenter', makeFileDrag())
    await nextTick()
    expect(w.find('.drop-mask-title').text()).toBe('请拖入新包')

    await send('drop', makeFileDrag('C:/pkg/v2.war'))
    await nextTick()
    expect(state.newPath).toBe('C:/pkg/v2.war')
    // 两路径齐备触发比对
    expect(triggerCompare).toHaveBeenCalled()
    w.unmount()
  })

  it('两项都已填后，下一次拖拽回到「请拖入老包」（循环重置）', async () => {
    const w = mount(DragDropOverlay)
    await send('drop', makeFileDrag('C:/a.zip'))
    await send('drop', makeFileDrag('C:/b.zip'))
    await nextTick()

    await send('dragenter', makeFileDrag())
    await nextTick()
    expect(w.find('.drop-mask-title').text()).toBe('请拖入老包')
    w.unmount()
  })

  it('dragleave 后遮罩收起恢复正常页面', async () => {
    const w = mount(DragDropOverlay)
    await send('dragenter', makeFileDrag())
    await nextTick()
    expect(w.find('.drop-mask').exists()).toBe(true)
    await send('dragleave', makeFileDrag())
    await nextTick()
    expect(w.find('.drop-mask').exists()).toBe(false)
    w.unmount()
  })

  it('读取不到 file.path（浏览器非 Electron）时拒绝填入并提示', async () => {
    const w = mount(DragDropOverlay)
    // 无 path 的 File：不 defineProperty path
    const file = new File(['x'], 'a.zip', { type: 'application/zip' })
    await send('drop', { types: ['Files'], files: [file], items: { add: () => {} } })
    await nextTick()
    expect(state.oldPath).toBe('')
    expect(toast).toHaveBeenCalled()
    w.unmount()
  })
})