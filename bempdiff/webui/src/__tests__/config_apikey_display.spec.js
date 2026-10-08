// T01567「API Key 输入框看不到持久化后的字符串」回归。
// 三条断言：①空密钥草稿不得覆盖后端已持久化/已回显的 Key；②新草稿不再写入空 Key；
// ③框内为空时按 hasApiKey/persistApiKey 给出可读状态（区分「内存持有」「真的没配」）。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state, defaultConfig } from '../store'

const DRAFT_KEY = 'bempdiff:config:draft'
const DRAFT = { aiProvider: 'openai', aiBaseUrl: 'https://api.openai.com/v1', aiModel: 'gpt-4o', persistApiKey: true }

async function mountDialog(cfg) {
  state.config = cfg
  const { default: ConfigDialog } = await import('../components/ConfigDialog.vue')
  // 真实 App 中对话框先以 visible=false 挂载，用户点「配置中心」才置 true 触发初始化，
  // 测试复刻该时序（watch 非 immediate，直接挂载时表单不会被填充）。
  const w = mount(ConfigDialog, { props: { visible: false }, attachTo: document.body })
  await w.setProps({ visible: true })
  await w.vm.$nextTick()
  return w
}

function keyInput(w) {
  return w.findAll('input').find(i => (i.attributes('aria-label') || '').includes('访问密钥'))
}

describe('T01567 API Key 回显', () => {
  beforeEach(() => { localStorage.clear(); state.config = defaultConfig() })

  it('空密钥草稿不遮蔽已持久化的 Key（打开即返显真实值）', async () => {
    localStorage.setItem(DRAFT_KEY, JSON.stringify({ ...DRAFT, aiApiKey: '' }))
    const cfg = { ...defaultConfig(), aiApiKey: 'sk-persisted-123', hasApiKey: true, persistApiKey: true }
    const w = await mountDialog(cfg)
    expect(keyInput(w).element.value).toBe('sk-persisted-123')
    w.unmount()
  })

  it('关闭时清空输入框不再把空 Key 写进草稿', async () => {
    const cfg = { ...defaultConfig(), aiApiKey: 'sk-keep-me', hasApiKey: true, persistApiKey: true }
    const w = await mountDialog(cfg)
    const inp = keyInput(w)
    inp.element.value = ''
    await inp.trigger('input')
    const closeBtn = w.findAll('button').find(b => b.text().trim() === '关闭')
    await closeBtn.trigger('click')
    await w.vm.$nextTick()
    const raw = localStorage.getItem(DRAFT_KEY)
    const draft = raw ? JSON.parse(raw) : {}
    expect(draft.aiApiKey).toBeUndefined()
    w.unmount()
  })

  it('后端内存持有 Key 但未勾选记住：给「仅当前进程」提示与覆盖占位', async () => {
    const cfg = { ...defaultConfig(), hasApiKey: true, persistApiKey: false }
    const w = await mountDialog(cfg)
    const st = w.find('[data-testid="apikey-state"]')
    expect(st.exists()).toBe(true)
    expect(st.text()).toContain('当前进程')
    expect(keyInput(w).attributes('placeholder')).toContain('已配置')
    w.unmount()
  })

  it('后端无任何 Key：状态标注「未配置密钥」', async () => {
    const w = await mountDialog({ ...defaultConfig(), hasApiKey: false })
    expect(w.find('[data-testid="apikey-state"]').text()).toBe('未配置密钥')
    w.unmount()
  })
})
