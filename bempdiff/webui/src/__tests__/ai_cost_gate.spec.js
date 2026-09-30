// R4 降级与成本闸门验收（二期 T01460）——ensureAiBudget 全路径 store 级测试。
// 覆盖：闸门关闭直通、未超阈值直通、超阈值弹确认/确认放行/取消拦截、静默入口（自动报告）不弹窗直接拒绝。
// Mock 降级（无 Key/不可达走 Mock 不中断）为后端能力，配套 headless 实测见任务回传。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { state, defaultConfig, ensureAiBudget, confirmCostGate, cancelCostGate } from '../store'
import { api } from '../api/client'

describe('R4 成本闸门 ensureAiBudget', () => {
  beforeEach(() => {
    state.config = defaultConfig()
    state.config.costGateWarnTokens = 100
    state.job = { jobId: 'j-gate', status: 'DONE' }
    state.aiEstimate = null
    state.aiEstimateKey = undefined
    state.costGate = null
    vi.restoreAllMocks()
  })

  it('闸门关闭（阈值 0）直接放行，不取预估不弹窗', async () => {
    state.config.costGateWarnTokens = 0
    const spy = vi.spyOn(api, 'aiEstimate')
    await expect(ensureAiBudget('analyze')).resolves.toBe(true)
    expect(spy).not.toHaveBeenCalled()
    expect(state.costGate).toBeNull()
  })

  it('预估未超阈值直接放行', async () => {
    vi.spyOn(api, 'aiEstimate').mockResolvedValue({ report: 10, analyze: 50, classify: 10, threshold: 100 })
    await expect(ensureAiBudget('analyze')).resolves.toBe(true)
    expect(state.costGate).toBeNull()
  })

  it('预估超阈值弹强制确认，确认后放行', async () => {
    vi.spyOn(api, 'aiEstimate').mockResolvedValue({ report: 10, analyze: 500, classify: 10, threshold: 100 })
    const p = ensureAiBudget('analyze')
    await new Promise(r => setTimeout(r, 0)) // 等 estimate 请求与弹窗状态落位
    expect(state.costGate).toBeTruthy()
    expect(state.costGate.action).toBe('analyze')
    expect(state.costGate.threshold).toBe(100)
    confirmCostGate()
    await expect(p).resolves.toBe(true)
    expect(state.costGate).toBeNull()
  })

  it('预估超阈值取消 → 拦截返回 false，无弹窗残留', async () => {
    vi.spyOn(api, 'aiEstimate').mockResolvedValue({ report: 10, analyze: 500, classify: 10, threshold: 100 })
    const p = ensureAiBudget('analyze')
    await new Promise(r => setTimeout(r, 0))
    cancelCostGate()
    await expect(p).resolves.toBe(false)
    expect(state.costGate).toBeNull()
  })

  it('静默入口（比对后自动报告）超阈值不弹窗、直接拒绝', async () => {
    vi.spyOn(api, 'aiEstimate').mockResolvedValue({ report: 999, analyze: 10, classify: 10, threshold: 100 })
    await expect(ensureAiBudget('report', { silent: true })).resolves.toBe(false)
    expect(state.costGate).toBeNull()
  })

  it('预估接口失败不阻断主流程（放行）', async () => {
    vi.spyOn(api, 'aiEstimate').mockRejectedValue(new Error('network down'))
    await expect(ensureAiBudget('analyze')).resolves.toBe(true)
    expect(state.costGate).toBeNull()
  })
})
