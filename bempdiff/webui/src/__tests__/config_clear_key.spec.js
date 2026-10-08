// T01615「取消勾选『记住 API Key』会不可逆删除磁盘密钥」防护回归。
// 断言四条：①持久化密钥时提供显式「清除已保存密钥」入口；②取消记住 + 保存先二次确认、未确认不发 PUT；
// ③确认条「取消」回滚勾选且不发 PUT；④「勾选记住但框空」的后果在状态行长驻（不再只靠会被覆盖的 toast）。
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig } from '../store'

let calls
function mockFetch() {
  calls = []
  global.fetch = vi.fn(async (url, init) => {
    calls.push({ url, body: init && init.body ? JSON.parse(init.body) : null })
    return { ok: true, status: 200, statusText: 'OK', json: async () => ({}), text: async () => '' }
  })
}

async function mountDialog(cfg) {
  state.config = cfg
  const { default: ConfigDialog } = await import('../components/ConfigDialog.vue')
  // 复刻真实时序：先 visible=false 挂载，再置 true 触发初始化（watch 非 immediate）
  const w = mount(ConfigDialog, { props: { visible: false }, attachTo: document.body })
  await w.setProps({ visible: true })
  await w.vm.$nextTick()
  return w
}

const persistedCfg = () => ({ ...defaultConfig(), aiApiKey: 'sk-keep-me-456', hasApiKey: true, persistApiKey: true })
function putCalls() { return calls.filter(c => c.url === '/api/config' && c.body !== null) }
async function save(w) {
  // 弹窗内另有同名「保存」（各 Tab 自己的小保存按钮），footer 的主保存按钮才是 onSave
  const b = w.findAll('button').find(x => /btn-primary/.test(x.attributes('class') || '') && x.text().trim() === '保存')
  await b.trigger('click')
  await w.vm.$nextTick()
  await new Promise(r => setTimeout(r, 0))
}

describe('T01615 密钥清除二次确认与显式入口', () => {
  beforeEach(() => { localStorage.clear(); state.config = defaultConfig(); mockFetch() })
  afterEach(() => { global.fetch = undefined })

  it('后端已持久化密钥时显示「清除已保存密钥」按钮', async () => {
    const w = await mountDialog(persistedCfg())
    const btn = w.find('[data-testid="clear-key-btn"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('清除已保存密钥')
    w.unmount()
  })

  it('取消勾选「记住」并保存：先要求二次确认，未确认不发 PUT', async () => {
    const w = await mountDialog(persistedCfg())
    await w.find('#cfgPersist').setValue(false)
    expect(w.find('[data-testid="apikey-nowrite"]').text()).toContain('将删除')
    await save(w)
    expect(putCalls().length).toBe(0)
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(true)
    // 确认后同一 PUT 才落地，且携带 persistApiKey=false
    await w.find('[data-testid="clear-key-ok"]').trigger('click')
    await new Promise(r => setTimeout(r, 0))
    expect(putCalls().length).toBe(1)
    expect(putCalls()[0].body.persistApiKey).toBe(false)
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(false)
    w.unmount()
  })

  it('确认条点「取消」：勾选回到记住态且不发 PUT', async () => {
    const w = await mountDialog(persistedCfg())
    await w.find('#cfgPersist').setValue(false)
    await save(w)
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(true)
    await w.find('[data-testid="clear-key-cancel"]').trigger('click')
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(false)
    expect(w.find('#cfgPersist').element.checked).toBe(true)
    expect(putCalls().length).toBe(0)
    w.unmount()
  })

  it('「清除已保存密钥」入口直接触发确认条，确认后 PUT 不落盘密钥', async () => {
    const w = await mountDialog(persistedCfg())
    await w.find('[data-testid="clear-key-btn"]').trigger('click')
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(true)
    await w.find('[data-testid="clear-key-ok"]').trigger('click')
    await new Promise(r => setTimeout(r, 0))
    expect(putCalls().length).toBe(1)
    expect(putCalls()[0].body.persistApiKey).toBe(false)
    expect(putCalls()[0].body.aiApiKey).toBe('')
    w.unmount()
  })

  it('勾选记住但密钥为空且后端无密钥：状态行常驻「不会写入任何密钥」', async () => {
    const w = await mountDialog({ ...defaultConfig(), hasApiKey: false, persistApiKey: true })
    const hint = w.find('[data-testid="apikey-nowrite"]')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('不会写入任何密钥')
    w.unmount()
  })

  it('后端本就没有落盘密钥时取消「记住」不误报（无确认条、直接保存）', async () => {
    const w = await mountDialog({ ...defaultConfig(), hasApiKey: false, persistApiKey: true })
    await w.find('#cfgPersist').setValue(false)
    expect(w.find('[data-testid="apikey-nowrite"]').exists()).toBe(false)
    await save(w)
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(false)
    expect(putCalls().length).toBe(1)
    expect(putCalls()[0].body.persistApiKey).toBe(false)
    w.unmount()
  })

  it('会话内先保存过密钥后，「取消记住 + 保存」仍要求二次确认', async () => {
    // 真实安装版复验抓到的回归：store.saveConfig 把「请求体」当成服务端状态写回 state.config，
    // 而请求体不含 hasApiKey → 该字段变 undefined → savedKeyOnDisk 永久为假 → 守卫静默失效，
    // 取消勾选「记住」再保存会直接删掉 properties 里的密钥行。此处让 PUT 如实返回 toJson()。
    let serverCfg = { ...defaultConfig(), aiApiKey: '', hasApiKey: false, persistApiKey: false }
    calls = []
    global.fetch = vi.fn(async (url, init) => {
      const body = init && init.body ? JSON.parse(init.body) : null
      calls.push({ url, body })
      if (url === '/api/config' && init && init.method === 'PUT') {
        serverCfg = {
          ...serverCfg, ...body,
          hasApiKey: !!(body.aiApiKey && body.persistApiKey),
          aiApiKey: body.aiApiKey,
        }
      }
      const snapshot = { ...serverCfg }
      delete snapshot.aiApiKey
      if (serverCfg.hasApiKey && serverCfg.persistApiKey) snapshot.aiApiKey = serverCfg.aiApiKey
      // api 客户端会读 content-type 决定 json/text，桩必须提供 headers
      return {
        ok: true, status: 200, statusText: 'OK',
        headers: { get: (k) => (String(k).toLowerCase() === 'content-type' ? 'application/json' : null) },
        json: async () => snapshot, text: async () => ''
      }
    })

    const w = await mountDialog({ ...defaultConfig(), hasApiKey: false, persistApiKey: false })
    const keyIn = w.findAll('input').find(i => (i.attributes('aria-label') || '').includes('访问密钥'))
    // 第一次保存：写入密钥并勾选「记住」→ 密钥落盘
    await keyIn.setValue('sk-first-111')
    await w.find('#cfgPersist').setValue(true)
    await save(w)
    expect(putCalls().length).toBe(1)
    expect(state.config.hasApiKey).toBe(true)

    // 第二次保存：取消「记住」→ 必须仍然先要二次确认，且确认前不发 PUT
    await w.find('#cfgPersist').setValue(false)
    expect(w.find('[data-testid="apikey-nowrite"]').text()).toContain('将删除')
    await save(w)
    expect(putCalls().length).toBe(1)
    expect(w.find('[data-testid="clear-key-confirm"]').exists()).toBe(true)
    w.unmount()
  })
})
