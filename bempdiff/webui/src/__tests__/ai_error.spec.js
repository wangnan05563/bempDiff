// R8 错误友好化（二期 T01469）——classifyAiError 归因矩阵与友好文案组装。
// 覆盖：鉴权/模型/网络/限速四类可验证特征归因 + 未知兜底保留摘要 + 文案格式。
import { describe, it, expect } from 'vitest'
import { classifyAiError, friendlyAiError } from '../lib/ai_error'

describe('R8 classifyAiError 归因矩阵', () => {
  it('HTTP 401/403 与厂商鉴权文案 → auth + 建议', () => {
    expect(classifyAiError('HTTP 401: invalid api key').type).toBe('auth')
    expect(classifyAiError('HTTP 403 Forbidden').type).toBe('auth')
    const c = classifyAiError('You didn\'t provide an API key')
    expect(c.reason).toContain('鉴权失败')
    expect(c.hint).toContain('API Key')
  })

  it('404 / model not found → model', () => {
    expect(classifyAiError('HTTP 404: model gpt-9 not found').type).toBe('model')
    expect(classifyAiError('The model does not exist').type).toBe('model')
  })

  it('fetch 失败 / 超时 / DNS / 代理 → network', () => {
    expect(classifyAiError('TypeError: Failed to fetch').type).toBe('network')
    expect(classifyAiError('ETIMEDOUT').type).toBe('network')
    expect(classifyAiError('getaddrinfo ENOTFOUND api.openai.com').type).toBe('network')
    expect(classifyAiError('connect ECONNREFUSED 127.0.0.1:11434').type).toBe('network')
  })

  it('429 / 配额 / 余额 → rate', () => {
    expect(classifyAiError('HTTP 429: rate limit exceeded').type).toBe('rate')
    expect(classifyAiError('insufficient_quota').type).toBe('rate')
  })

  it('无法归因 → other 兜底保留摘要（≤80 字符 + 省略号）', () => {
    const c = classifyAiError('某种全新的错误形态')
    expect(c.type).toBe('other')
    expect(c.reason).toBe('某种全新的错误形态')
    const long = 'x'.repeat(120)
    const c2 = classifyAiError(long)
    expect(c2.reason.length).toBe(81)
    expect(c2.reason.endsWith('…')).toBe(true)
  })

  it('friendlyAiError 组装「失败原因 + 建议」格式', () => {
    const s = friendlyAiError('HTTP 401 Unauthorized')
    expect(s).toMatch(/^失败原因：鉴权失败/)
    expect(s).toContain('建议：')
    expect(s).toContain('API Key')
  })

  it('空/空串输入安全兜底', () => {
    expect(classifyAiError('').type).toBe('other')
    expect(classifyAiError(null).type).toBe('other')
  })
})
