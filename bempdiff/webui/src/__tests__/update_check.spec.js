// R9 启动更新检查（二期 T01471）——store 级验收。
// 覆盖：发现新版本 → state.updateAvailable（含 tag/url）；24h 节流（第二次调用不重复请求）；
//       已是最新/检查失败静默不提示；节流时间戳在首次调用即写入。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { state, checkUpdateSilently } from '../store'
import { api } from '../api/client'

const KEY = 'bempdiff.updateCheckAt'

describe('R9 启动更新检查（静默）', () => {
  beforeEach(() => {
    state.updateAvailable = null
    try { localStorage.removeItem(KEY) } catch (_) {}
    vi.restoreAllMocks()
  })

  it('发现新版本：updateAvailable 记录 tag 与下载页 URL', async () => {
    vi.spyOn(api, 'checkUpdate').mockResolvedValue({
      ok: true, upToDate: false, latest: { tag: '1.2.0', url: 'https://github.com/x/releases/tag/v1.2.0' }, message: '发现新版本'
    })
    await checkUpdateSilently()
    expect(state.updateAvailable).toEqual({
      tag: '1.2.0', url: 'https://github.com/x/releases/tag/v1.2.0', message: '发现新版本'
    })
    expect(Number(localStorage.getItem(KEY))).toBeGreaterThan(0)
  })

  it('24h 节流：已检查过时直接返回，不再请求', async () => {
    localStorage.setItem(KEY, String(Date.now()))
    const spy = vi.spyOn(api, 'checkUpdate').mockResolvedValue({ ok: true, upToDate: true, latest: null })
    await checkUpdateSilently()
    expect(spy).not.toHaveBeenCalled()
    expect(state.updateAvailable).toBeNull()
  })

  it('已是最新（upToDate=true）与检查失败均静默不提示', async () => {
    vi.spyOn(api, 'checkUpdate').mockResolvedValue({ ok: true, upToDate: true, latest: { tag: '0.0.1', url: '' } })
    await checkUpdateSilently()
    expect(state.updateAvailable).toBeNull()

    localStorage.removeItem(KEY)
    vi.spyOn(api, 'checkUpdate').mockRejectedValue(new Error('network down'))
    await checkUpdateSilently()
    expect(state.updateAvailable).toBeNull()
  })

  it('关闭提示（dismiss）仅影响本次会话，不影响节流时间戳', async () => {
    vi.spyOn(api, 'checkUpdate').mockResolvedValue({
      ok: true, upToDate: false, latest: { tag: '1.2.0', url: 'u' }, message: ''
    })
    await checkUpdateSilently()
    const { dismissUpdateHint } = await import('../store')
    dismissUpdateHint()
    expect(state.updateAvailable).toBeNull()
    expect(Number(localStorage.getItem(KEY))).toBeGreaterThan(0)
  })
})

// ---- R9 验收（T01472）：提示条 UI 护栏（防误删关键属性/入口） ----
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

describe('R9 更新提示条 UI 护栏', () => {
  const appSrc = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'App.vue'), 'utf-8')

  it('提示条含可访问名称、跳转下载页（新标签+noopener）、关闭钮与不自动安装语义', () => {
    expect(appSrc).toContain('update-hint')
    expect(appSrc).toContain('aria-label="发现新版本提示"')
    expect(appSrc).toContain('前往下载页')
    expect(appSrc).toContain('target="_blank"')
    expect(appSrc).toContain('rel="noopener"')
    expect(appSrc).toContain('aria-label="关闭更新提示"')
    expect(appSrc).toContain('不会自动安装')
    expect(appSrc).toContain('checkUpdateSilently')
  })
})

describe('R9 关闭开关持久化（T01472）', () => {
  beforeEach(() => {
    state.updateAvailable = null
    try { localStorage.removeItem(KEY) } catch (_) {}
    try { localStorage.removeItem('bempdiff.updateDismissedTag') } catch (_) {}
    vi.restoreAllMocks()
  })

  it('关闭提示持久化：同版本不再提示，新版本照常提示', async () => {
    const { dismissUpdateHint } = await import('../store')
    vi.spyOn(api, 'checkUpdate').mockResolvedValue({
      ok: true, upToDate: false, latest: { tag: '1.2.0', url: 'u' }, message: ''
    })
    await checkUpdateSilently()
    dismissUpdateHint()
    expect(localStorage.getItem('bempdiff.updateDismissedTag')).toBe('1.2.0')
    // 同版本：静默（节流过期后重新检查也不再提示）
    localStorage.removeItem(KEY)
    await checkUpdateSilently()
    expect(state.updateAvailable).toBeNull()
    // 新版本：照常提示
    vi.spyOn(api, 'checkUpdate').mockResolvedValue({
      ok: true, upToDate: false, latest: { tag: '1.3.0', url: 'u' }, message: ''
    })
    localStorage.removeItem(KEY)
    await checkUpdateSilently()
    expect(state.updateAvailable).toEqual({ tag: '1.3.0', url: 'u', message: '' })
  })
})
