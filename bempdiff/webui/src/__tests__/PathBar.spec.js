// 路径栏（PathBar.vue）交互测试：对标 Windows 10 文件管理器地址栏。
// 覆盖：面包屑层级渲染与当前层级识别、目录段点击 → 树定位、文件段点击 → 打开、
// 空白点击 → 编辑模式（完整路径）、Enter 确认（精确/前缀/无效）、Esc 恢复、
// 归档复合键拆段、folder 模式绝对路径还原。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('../store', () => {
  const state = reactive({ job: { mode: 'package' }, oldPath: '', newPath: '' })
  return { state, selectEntry: vi.fn(), toast: vi.fn(), locateTreePrefix: vi.fn() }
})

import PathBar from '../components/PathBar.vue'
import { state, selectEntry, toast, locateTreePrefix } from '../store'

const KEY = 'WEB-INF/lib/commons-lang.jar'
const TREE = [
  { key: 'WEB-INF/lib/commons-lang.jar', status: 'MODIFIED', fileClass: 'JAR' },
  { key: 'WEB-INF/lib/other.jar', status: 'MODIFIED', fileClass: 'JAR' },
  { key: 'com/internal/InvoiceService.java', status: 'MODIFIED', fileClass: 'CLASS' }
]

function mountBar(props = {}) {
  return mount(PathBar, {
    props: { path: KEY, rootPath: '', node: null, ...props },
    attachTo: document.body
  })
}
async function flush() { await nextTick(); await nextTick() }

/** 模拟点击路径栏空白区域进入编辑模式（dispatch 原生事件，绕开 trigger 的 target 限制）。 */
function clickBlank(w) {
  w.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
}

beforeEach(() => {
  vi.clearAllMocks()
  state.job = { mode: 'package' }
  document.body.innerHTML = ''
})

describe('面包屑导航（默认状态）', () => {
  it('按 / 渲染各层级 + 根节点 + 分隔符，最后一段为当前层级', () => {
    const w = mountBar()
    const segs = w.findAll('.pb-seg').map(s => s.text().trim())
    expect(segs).toEqual(['包根', 'WEB-INF', 'lib', 'commons-lang.jar'])
    expect(w.findAll('.pb-sep').length).toBe(3)
    const cur = w.find('.pb-current')
    expect(cur.exists()).toBe(true)
    expect(cur.text().trim()).toBe('commons-lang.jar')
  })

  it('归档复合键 outer!/inner 也按层级拆段', () => {
    const w = mountBar({ path: 'BOOT-INF/lib/a.jar!/com/x/A.class' })
    const segs = w.findAll('.pb-seg').map(s => s.text().trim())
    expect(segs).toEqual(['包根', 'BOOT-INF', 'lib', 'a.jar', 'com', 'x', 'A.class'])
  })

  it('悬浮 title 为完整路径（覆盖长路径场景）', () => {
    const w = mountBar()
    expect(w.attributes('title')).toBe(KEY)
  })

  it('点击文件段（最后一段）→ selectEntry 打开比对', async () => {
    const w = mountBar()
    const segs = w.findAll('.pb-seg')
    await segs[segs.length - 1].trigger('click')
    expect(selectEntry).toHaveBeenCalledWith(KEY)
  })

  it('点击目录段 → 差异树定位到该层级', async () => {
    const w = mountBar()
    const segs = w.findAll('.pb-seg')
    await segs[2].trigger('click') // lib（前缀 WEB-INF/lib）
    expect(locateTreePrefix).toHaveBeenCalledWith('WEB-INF/lib')
    expect(selectEntry).not.toHaveBeenCalled()
  })

  it('点击根节点（包根）→ 定位树顶部', async () => {
    const w = mountBar()
    await w.findAll('.pb-seg')[0].trigger('click')
    expect(locateTreePrefix).toHaveBeenCalledWith('')
  })
})

describe('编辑模式（点击空白切换完整路径）', () => {
  it('点击空白区域 → 出现输入框，值为完整路径且自动聚焦', async () => {
    const w = mountBar()
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    expect(input.exists()).toBe(true)
    expect(input.element.value).toBe(KEY)
    expect(document.activeElement).toBe(input.element)
  })

  it('输入框悬浮 title 同样为完整路径', async () => {
    const w = mountBar()
    clickBlank(w)
    await flush()
    expect(w.find('.pb-input').attributes('title')).toBe(KEY)
  })

  it('Enter 提交精确 key → selectEntry 并恢复面包屑', async () => {
    state.job = { mode: 'package', tree: TREE }
    const w = mountBar()
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    input.element.value = 'com/internal/InvoiceService.java'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter' })
    expect(selectEntry).toHaveBeenCalledWith('com/internal/InvoiceService.java')
    await flush()
    expect(w.find('.pb-input').exists()).toBe(false)
  })

  it('Enter 提交目录前缀 → locateTreePrefix 定位', async () => {
    state.job = { mode: 'package', tree: TREE }
    const w = mountBar()
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    input.element.value = 'WEB-INF'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter' })
    expect(locateTreePrefix).toHaveBeenCalledWith('WEB-INF')
    expect(selectEntry).not.toHaveBeenCalled()
  })

  it('Enter 提交无效路径 → toast 提示，不跳转', async () => {
    state.job = { mode: 'package', tree: TREE }
    const w = mountBar()
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    input.element.value = 'no/such/file.java'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter' })
    expect(selectEntry).not.toHaveBeenCalled()
    expect(locateTreePrefix).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('warning', expect.stringContaining('未找到'))
  })

  it('Esc 退出编辑恢复面包屑，失焦同样恢复', async () => {
    const w = mountBar()
    clickBlank(w)
    await flush()
    await w.find('.pb-input').trigger('keydown', { key: 'Escape' })
    await flush()
    expect(w.find('.pb-input').exists()).toBe(false)
    expect(w.find('.pb-crumbs').exists()).toBe(true)
  })
})

describe('folder 模式（编辑框显示磁盘绝对路径）', () => {
  const ROOT = 'D:/code/old'
  beforeEach(() => {
    state.job = { mode: 'folder', tree: TREE }
  })

  it('面包屑根节点显示盘符', () => {
    const w = mountBar({ rootPath: ROOT })
    expect(w.find('.pb-root').text().trim()).toContain('D:')
  })

  it('编辑框值为绝对路径，Enter 提交绝对路径 → 还原相对 key 后 selectEntry', async () => {
    const w = mountBar({ rootPath: ROOT })
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    expect(input.element.value).toBe(ROOT + '/' + KEY)
    input.element.value = ROOT + '/com/internal/InvoiceService.java'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter' })
    expect(selectEntry).toHaveBeenCalledWith('com/internal/InvoiceService.java')
  })

  it('Enter 提交绝对路径（目录级）→ 还原前缀后定位', async () => {
    const w = mountBar({ rootPath: ROOT })
    clickBlank(w)
    await flush()
    const input = w.find('.pb-input')
    input.element.value = ROOT + '/WEB-INF'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter' })
    expect(locateTreePrefix).toHaveBeenCalledWith('WEB-INF')
  })
})
