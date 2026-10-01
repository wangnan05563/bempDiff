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
