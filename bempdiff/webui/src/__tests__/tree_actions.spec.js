// 差异树右键菜单（BCompare 风格）可用性矩阵测试。
// 覆盖：对象类型判定（文件/文件夹/基准文件夹）、13 个菜单项 × 模式（package/folder、有/无桌面壳）的
// 启用/禁用矩阵、动态文案（设为/取消基准、复制方向）、复制文本构建（文件名/路径/相对路径）。
import { describe, it, expect } from 'vitest'
import {
  MENU_ITEMS, menuItemsFor, nodeTypeOf, disabledReason, itemState, buildCopyText
} from '../lib/tree_actions'

const FILE_NODE = { key: 'com/foo/Bar.class', status: 'MODIFIED', fileClass: 'CLASS', size: 1024 }
const FOLDER_NODE = { key: 'WEB-INF/lib/', status: 'UNCHANGED', fileClass: 'FOLDER', size: 0 }
const ADDED_FILE = { key: 'new/thing.js', status: 'ADDED', fileClass: 'JS', size: 10 }
const PKG_CTX = { mode: 'package', status: 'MODIFIED', baseFolder: null, shell: true }
const FOLDER_CTX_SHELL = { mode: 'folder', status: 'MODIFIED', baseFolder: null, shell: true }
const FOLDER_CTX_NO_SHELL = { mode: 'folder', status: 'MODIFIED', baseFolder: null, shell: false }
const BASE_CTX = { mode: 'folder', status: 'UNCHANGED', baseFolder: 'WEB-INF/lib/', shell: true }

const ALL_IDS = MENU_ITEMS.map(m => m.id)

describe('nodeTypeOf 对象类型判定', () => {
  it('普通节点 → file', () => {
    expect(nodeTypeOf(FILE_NODE, PKG_CTX)).toBe('file')
  })
  it('FileClass=FOLDER 且非基准 → folder', () => {
    expect(nodeTypeOf(FOLDER_NODE, PKG_CTX)).toBe('folder')
  })
  it('等于 baseFolder → baseFolder（优先于 folder）', () => {
    expect(nodeTypeOf(FOLDER_NODE, BASE_CTX)).toBe('baseFolder')
  })
  it('null 节点兜底为 file', () => {
    expect(nodeTypeOf(null, PKG_CTX)).toBe('file')
  })
})

describe('包对比模式（无磁盘路径）的可用性', () => {
  const map = () => Object.fromEntries(menuItemsFor(FILE_NODE, PKG_CTX).map(x => [x.id, x]))
  it('打开可用（包模式=打开内容比对）', () => {
    expect(disabledReason('open', FILE_NODE, PKG_CTX)).toBeNull()
  })
  it('磁盘操作全部禁用并给出明确原因', () => {
    expect(disabledReason('reveal', FILE_NODE, PKG_CTX)).toContain('无磁盘路径')
    expect(disabledReason('rename', FILE_NODE, PKG_CTX)).toContain('无磁盘路径')
    expect(disabledReason('delete', FILE_NODE, PKG_CTX)).toContain('无磁盘路径')
    expect(disabledReason('copyFile', FILE_NODE, PKG_CTX)).toContain('无磁盘路径')
  })
  it('设为基准：非文件夹对象禁用', () => {
    expect(disabledReason('setBase', FILE_NODE, PKG_CTX)).toContain('仅文件夹对象')
  })
  it('与磁盘无关的操作全部可用', () => {
    for (const id of ['props', 'exclude', 'ignore', 'copyName', 'copyPath', 'copyRelPath', 'aiSummary']) {
      expect(disabledReason(id, FILE_NODE, PKG_CTX), id).toBeNull()
    }
  })
})

describe('文件夹对比模式（有磁盘路径）', () => {
  it('文件对象：open/reveal 有壳可用、无壳禁用（原因明确）', () => {
    expect(disabledReason('open', FILE_NODE, FOLDER_CTX_SHELL)).toBeNull()
    expect(disabledReason('reveal', FILE_NODE, FOLDER_CTX_SHELL)).toBeNull()
    expect(disabledReason('open', FILE_NODE, FOLDER_CTX_NO_SHELL)).toContain('浏览器模式')
    expect(disabledReason('reveal', FILE_NODE, FOLDER_CTX_NO_SHELL)).toContain('浏览器模式')
  })
  it('文件对象：rename/delete/copyFile 可用', () => {
    for (const id of ['rename', 'delete', 'copyFile']) {
      expect(disabledReason(id, FILE_NODE, FOLDER_CTX_SHELL), id).toBeNull()
    }
  })
  it('文件夹对象：setBase 可用（其余文件操作同样可用）', () => {
    expect(disabledReason('setBase', FOLDER_NODE, FOLDER_CTX_SHELL)).toBeNull()
    expect(disabledReason('rename', FOLDER_NODE, FOLDER_CTX_SHELL)).toBeNull()
    expect(disabledReason('delete', FOLDER_NODE, FOLDER_CTX_SHELL)).toBeNull()
    expect(disabledReason('copyFile', FOLDER_NODE, FOLDER_CTX_SHELL)).toBeNull()
  })
  it('基准文件夹对象：危险操作全部禁用、setBase 变为「取消」', () => {
    expect(disabledReason('delete', FOLDER_NODE, BASE_CTX)).toContain('基准文件夹')
    expect(disabledReason('rename', FOLDER_NODE, BASE_CTX)).toContain('基准文件夹')
    expect(disabledReason('copyFile', FOLDER_NODE, BASE_CTX)).toContain('基准文件夹')
    const st = itemState(MENU_ITEMS.find(m => m.id === 'setBase'), FOLDER_NODE, BASE_CTX)
    expect(st.label).toBe('取消设为基准文件夹')
    expect(st.disabled).toBe(false)
  })
  it('ADDED 文件：复制方向文案为 右→左', () => {
    const st = itemState(MENU_ITEMS.find(m => m.id === 'copyFile'), ADDED_FILE, { ...FOLDER_CTX_SHELL, status: 'ADDED' })
    expect(st.label).toContain('右 → 左')
  })
})

describe('菜单完整性', () => {
  it('恰好 13 个菜单项且 id 唯一', () => {
    expect(ALL_IDS.length).toBe(13)
    expect(new Set(ALL_IDS).size).toBe(13)
  })
  it('每项都有 label/icon/group', () => {
    for (const m of MENU_ITEMS) {
      expect(m.label, m.id).toBeTruthy()
      expect(m.icon, m.id).toMatch(/^bi-/)
      expect(['file', 'manage', 'view', 'copy', 'info', 'ai'], m.id).toContain(m.group)
    }
  })
  it('menuItemsFor 返回扁平结构：顶层含 id/icon/group（ContextMenu 渲染与 runAction switch 依赖）', () => {
    const items = menuItemsFor(FILE_NODE, PKG_CTX)
    expect(items.length).toBe(13)
    for (const x of items) {
      expect(x.id, '顶层缺少 id').toBeTruthy()
      expect(x.icon, x.id).toMatch(/^bi-/)
      expect(x.group, x.id).toBeTruthy()
      expect(typeof x.disabled, x.id).toBe('boolean')
      expect(typeof x.title, x.id).toBe('string')
      expect(typeof x.label, x.id).toBe('string')
    }
    // 与 MENU_ITEMS 顺序/集合一致
    expect(items.map(x => x.id)).toEqual(ALL_IDS)
    // 打开菜单项：label/disabled 来自 itemState 覆盖，id 仍为 open
    const open = items.find(x => x.id === 'open')
    expect(open.disabled).toBe(false)
    expect(open.label).toBe('打开')
  })
})

describe('复制文本构建（buildCopyText）', () => {
  it('名称 = 基名', () => {
    expect(buildCopyText(FILE_NODE, PKG_CTX).name).toBe('Bar.class')
  })
  it('相对路径 = key', () => {
    expect(buildCopyText(FILE_NODE, PKG_CTX).relPath).toBe('com/foo/Bar.class')
  })
  it('文件夹模式：绝对路径 = 根 + key', () => {
    const t = buildCopyText(FILE_NODE, { mode: 'folder', rootPath: 'D:/old' })
    expect(t.absPath).toBe('D:/old/com/foo/Bar.class')
  })
  it('文件夹模式：根尾斜杠与 key 头斜杠归一', () => {
    expect(buildCopyText(FILE_NODE, { mode: 'folder', rootPath: 'D:/old/' }).absPath).toBe('D:/old/com/foo/Bar.class')
  })
  it('包模式：无磁盘路径，绝对路径回落为 key', () => {
    const t = buildCopyText(FILE_NODE, { mode: 'package' })
    expect(t.absPath).toBe('com/foo/Bar.class')
  })
  it('目录 key 的名称 = 去尾斜杠的末段', () => {
    expect(buildCopyText(FOLDER_NODE, PKG_CTX).name).toBe('lib')
  })
})
