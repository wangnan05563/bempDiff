// 通用右键菜单（ContextMenu.vue）交互测试。
// 覆盖：打开聚焦首个可用项、↑/↓ 循环导航（跳过禁用项）、Home/End、Enter 执行并关闭、
// Esc 关闭、禁用项点击不触发、外部点击关闭 —— 桌面应用键盘导航与焦点管理惯例。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ContextMenu from '../components/ContextMenu.vue'

const ITEMS = [
  { id: 'open', label: '打开', icon: 'bi-box-arrow-up-right', group: 'file', disabled: false, title: '' },
  { id: 'reveal', label: '在文件资源管理器中显示', icon: 'bi-folder-symlink', group: 'file', disabled: true, title: '包内条目无磁盘路径' },
  { id: 'setBase', label: '设为基准文件夹', icon: 'bi-pin-angle', group: 'manage', disabled: false, title: '' },
  { id: 'delete', label: '删除', icon: 'bi-trash', group: 'manage', disabled: true, title: '基准文件夹不可删除' },
  { id: 'props', label: '属性', icon: 'bi-info-circle', group: 'info', disabled: false, title: '' }
]

function mountMenu(props = {}) {
  const wrapper = mount(ContextMenu, {
    props: { visible: true, x: 100, y: 100, items: ITEMS, ...props },
    attachTo: document.body
  })
  return wrapper
}

function itemEls() {
  return [...document.querySelectorAll('.cm-item')]
}

async function flush() {
  await nextTick()
  await nextTick()
}

function key(el, k) {
  el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }))
}

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('打开与焦点管理', () => {
  it('可见时聚焦第一个可用项（跳过前面禁用的）', async () => {
    const w = mountMenu()
    await flush()
    const els = itemEls()
    expect(els.length).toBe(5)
    expect(document.activeElement).toBe(els[0]) // open（第一个可用）
    w.unmount()
  })
  it('不可见时不渲染菜单', () => {
    const w = mount(ContextMenu, { props: { visible: false, x: 0, y: 0, items: ITEMS } })
    expect(document.querySelectorAll('.cm-item').length).toBe(0)
    w.unmount()
  })
  it('关闭后焦点还原（触发元素）', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    const w = mountMenu()
    await flush()
    await w.setProps({ visible: false })
    await flush()
    expect(document.activeElement).toBe(trigger)
    w.unmount()
  })
})

describe('键盘导航', () => {
  it('ArrowDown：跳到下一个可用项（跳过禁用）', async () => {
    const w = mountMenu()
    await flush()
    const els = itemEls()
    key(document.activeElement, 'ArrowDown')
    expect(document.activeElement).toBe(els[2]) // setBase（reveal 禁用被跳过）
    w.unmount()
  })
  it('ArrowUp：循环回绕到最后一个可用项', async () => {
    const w = mountMenu()
    await flush()
    const els = itemEls()
    key(document.activeElement, 'ArrowUp')
    expect(document.activeElement).toBe(els[4]) // props
    w.unmount()
  })
  it('Home/End：分别到首个/末个可用项', async () => {
    const w = mountMenu()
    await flush()
    const els = itemEls()
    key(document.activeElement, 'End')
    expect(document.activeElement).toBe(els[4])
    key(document.activeElement, 'Home')
    expect(document.activeElement).toBe(els[0])
    w.unmount()
  })
  it('Enter：执行选中项并关闭菜单', async () => {
    const w = mountMenu()
    await flush()
    key(document.activeElement, 'Enter')
    const sel = w.emitted('select')
    expect(sel).toBeTruthy()
    expect(sel[0][0].id).toBe('open')
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })
  it('Esc：关闭菜单（emit close）', async () => {
    const w = mountMenu()
    await flush()
    key(document.activeElement, 'Escape')
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })
  it('禁用项 Enter：不触发 select', async () => {
    const w = mountMenu()
    await flush()
    itemEls()[1].focus() // reveal（禁用）
    key(document.activeElement, 'Enter')
    expect(w.emitted('select')).toBeUndefined()
    w.unmount()
  })
  it('Tab：焦点陷阱（Tab 在菜单内循环，不跳出）', async () => {
    const w = mountMenu()
    await flush()
    const els = itemEls()
    key(document.activeElement, 'Tab')
    expect(document.activeElement).toBe(els[2])
    w.unmount()
  })
})

describe('鼠标交互', () => {
  it('点击可用项：select 并 close', async () => {
    const w = mountMenu()
    await flush()
    itemEls()[0].click()
    const sel = w.emitted('select')
    expect(sel).toBeTruthy()
    expect(sel[0][0].id).toBe('open')
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })
  it('点击禁用项：不触发任何事件', async () => {
    const w = mountMenu()
    await flush()
    itemEls()[1].click() // reveal（禁用）
    expect(w.emitted('select')).toBeUndefined()
    expect(w.emitted('close')).toBeUndefined()
    w.unmount()
  })
  it('外部点击：关闭菜单', async () => {
    const w = mountMenu()
    await flush()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })
  it('菜单内点击：不关闭', async () => {
    const w = mountMenu()
    await flush()
    const menu = document.querySelector('.cm-menu')
    menu.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(w.emitted('close')).toBeUndefined()
    w.unmount()
  })
})
