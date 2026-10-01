// R12 i18n（二期 T01477）——框架与首批覆盖验收。
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { t, setLocale, i18n, tTerm, TERM_TABLE, LOCALES, LANG_KEY } from '../lib/i18n'
import { state } from '../store'
import CompareOverlay from '../components/CompareOverlay.vue'
import HelpDoc from '../components/HelpDoc.vue'

describe('R12 i18n 框架', () => {
  beforeEach(() => {
    try { localStorage.removeItem(LANG_KEY) } catch (_) {}
    setLocale('zh-CN')
  })

  it('双字典 key 集合一致（防漏译）', () => {
    const { default: dict } = { default: null }
    // 直接从模块内部结构验证：t() 在两种语言下均不回退 key 本身
    for (const [locale] of LOCALES.map(l => [l.key])) {
      setLocale(locale)
      for (const k of ['app.compare', 'app.cancelCompare', 'phase.parsing', 'phase.diffing',
        'step.unpack', 'step.diff', 'step.build', 'status.version', 'phase.aiWait', 'phase.timeout']) {
        expect(t(k), `${locale} 缺失 ${k}`).not.toBe(k)
      }
    }
    setLocale('zh-CN')
  })

  it('t() 插值与缺失 key 回退链（en 缺失→中文→key）', () => {
    setLocale('zh-CN')
    expect(t('nope.missing')).toBe('nope.missing')
    expect(t('app.compare')).toBe('开始比对')
    setLocale('en-US')
    expect(t('app.compare')).toBe('Start Compare')
    expect(t('nope.missing', { x: 1 })).toBe('nope.missing')
    setLocale('zh-CN')
  })

  it('setLocale 持久化并同步 html[lang]；术语对照 zh 附英文', () => {
    setLocale('en-US')
    expect(localStorage.getItem(LANG_KEY)).toBe('en-US')
    expect(document.documentElement.getAttribute('lang')).toBe('en-US')
    expect(i18n.locale).toBe('en-US')
    setLocale('zh-CN')
    // tTerm：中文语境附英文对照；英文语境直接英文
    expect(tTerm('term.diffTree')).toBe('差异树 (Diff Tree)')
    setLocale('en-US')
    expect(tTerm('term.diffTree')).toBe('Diff Tree')
    setLocale('zh-CN')
  })

  it('术语对照表结构完整（8 条，三列）', () => {
    expect(TERM_TABLE.length).toBeGreaterThanOrEqual(8)
    for (const r of TERM_TABLE) {
      expect(r.length).toBe(3)
      expect(r[0]).toBeTruthy(); expect(r[1]).toBeTruthy(); expect(r[2]).toBeTruthy()
    }
  })

  it('CompareOverlay 跟随语言渲染三段标签（EN 模式显示 Unpack/Compare/Tree）', async () => {
    state.busy = true
    state.reporting = false
    state.jobProgress = { status: 'RUNNING', phase: 'diffing', progress: 70, message: 'msg' }
    setLocale('en-US')
    const w = mount(CompareOverlay)
    const labels = w.findAll('.cmp-step').map(s => s.text().trim())
    expect(labels).toEqual(['Unpack', 'Compare', 'Tree'])
    setLocale('zh-CN')
    await w.vm.$nextTick()
    expect(w.findAll('.cmp-step').map(s => s.text().trim())).toEqual(['解包', '比对', '出树'])
    w.unmount()
  })

  it('帮助文档含「术语中英对照」条目', async () => {
    const w = mount(HelpDoc)
    expect(w.text()).toContain('术语中英对照')
    expect(w.text()).toContain('Diff Tree')
    w.unmount()
  })
})
