// FR-010 命名保存常用对比任务测试：与自动历史分池、同名覆盖、回填还原、删除。
import { describe, it, expect, beforeEach } from 'vitest'
import { state, loadSavedSessions, saveNamedSession, applyNamedSession, removeNamedSession } from '../store'

const KEY = 'bempdiff.savedSessions'

describe('FR-010 命名保存常用对比任务', () => {
  beforeEach(() => {
    try { localStorage.removeItem(KEY) } catch (_) {}
    state.savedSessions = []
    state.oldPath = 'E:/rel-v1.war'
    state.newPath = 'E:/rel-v2.war'
    state.leftType = 'package'
  })

  it('保存命名会话：仅路径元数据（不含差异内容与 Key），同名覆盖刷新时间戳', () => {
    expect(saveNamedSession('周验收')).toBe(true)
    expect(saveNamedSession('周验收')).toBe(true) // 同名第二次（覆盖）
    const list = loadSavedSessions()
    expect(list.length).toBe(1)
    expect(list[0]).toMatchObject({ name: '周验收', oldPath: 'E:/rel-v1.war', newPath: 'E:/rel-v2.war', leftType: 'package' })
    expect(list[0].at).toBeGreaterThan(0)
    // 持久化到 localStorage
    const raw = JSON.parse(localStorage.getItem(KEY) || '[]')
    expect(raw.length).toBe(1)
  })

  it('空名不保存；路径为空不保存', () => {
    state.oldPath = ''
    expect(saveNamedSession('x')).toBe(false)
    state.oldPath = 'E:/a'
    state.newPath = ''
    expect(saveNamedSession('x')).toBe(false)
    state.newPath = 'E:/b'
    expect(saveNamedSession('   ')).toBe(false)
    expect(loadSavedSessions().length).toBe(0)
  })

  it('应用命名会话：回填左右路径与输入类型（重放参数完整还原）', () => {
    saveNamedSession('目录对比')
    state.oldPath = 'X:/other'
    state.newPath = 'Y:/other'
    state.leftType = 'folder'
    expect(applyNamedSession(0)).toBe(true)
    expect(state.oldPath).toBe('E:/rel-v1.war')
    expect(state.newPath).toBe('E:/rel-v2.war')
    expect(state.leftType).toBe('package')
  })

  it('应用/删除越界静默（返回 false / 不抛）', () => {
    expect(applyNamedSession(99)).toBe(false)
    expect(() => removeNamedSession(99)).not.toThrow()
  })

  it('删除后列表同步持久化；与自动历史分池（key 独立）', () => {
    saveNamedSession('a')
    saveNamedSession('b')
    removeNamedSession(0)
    const list = loadSavedSessions()
    expect(list.length).toBe(1)
    expect(list[0].name).toBe('a')
    // 独立 key：自动历史键不受影响
    expect(localStorage.getItem('bempdiff.compareHistory')).toBe(null)
  })

  it('坏数据静默清空（不抛异常）', () => {
    localStorage.setItem(KEY, 'not-json')
    state.savedSessions = []
    expect(loadSavedSessions().length).toBe(0)
  })
})
