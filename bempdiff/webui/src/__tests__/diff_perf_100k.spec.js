// FR-022 虚拟滚动万行级性能护栏（compare三期 T01805）：10 万行差异文件挂载到首帧可交互。
// 口径对齐 tree_perf_50k.spec.js：jsdom 无法测真实帧率，以「挂载→首帧渲染完成」为代理指标。
// 10 万 rep 行会触发 OVERSIZED_LINES(60000) 降级（关行内高亮 + 默认折叠）——该降级本身就是
// FR-022 的设计组成部分（超大文件不追求整量行内标注，保证可交互优先），一并断言生效。
import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { state } from '../store'
import DiffView from '../components/DiffView.vue'

const LINES = 100000
// 逐行 1:1 修改（rep 行 10 万）：覆盖行级对齐 + rows 全量构建 + 窗口切片最重路径
const oldSrc = Array.from({ length: LINES }, (_, i) => 'old line ' + i).join('\n')
const newSrc = Array.from({ length: LINES }, (_, i) => 'new line ' + i).join('\n')

const KEY = 'pkg/Huge.java'
const NODE = { key: KEY, fileClass: 'CLASS', status: 'MODIFIED', size: 4096 }

describe('FR-022 10 万行 diff 渲染性能', () => {
  afterEach(() => {
    state.tabs = []
    state.activeKey = null
    state.job = null
  })

  it(`10 万行挂载到首帧 ≤ 护栏线，DOM 行数受窗口约束，降级简洁视图生效`, async () => {
    const w = mount(DiffView)
    state.tabs = [{
      key: KEY, node: NODE, busy: false,
      decompile: { ok: true, engine: 'cfr', oldSource: oldSrc, newSource: newSrc }
    }]
    state.activeKey = KEY
    const t0 = performance.now()
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    const elapsed = performance.now() - t0
    console.info(`[perf] 10 万行 diff 首帧实测 ${elapsed.toFixed(0)}ms`)
    // 虚拟窗口生效：DOM 行数恒定，不随 10 万总量铺开
    const prows = w.findAll('.prow')
    expect(prows.length, '首帧应渲染可视窗口行').toBeGreaterThan(0)
    expect(prows.length, `10 万行文件实际渲染 ${prows.length} 行（窗口 + OVERSCAN）`).toBeLessThan(500)
    // 超大文件降级生效：banner 提示 + 全量高度按 10 万行撑开（内容不丢）
    const banner = w.find('.oversized-banner')
    expect(banner.exists(), '超 6 万差异行应出现降级提示条').toBe(true)
    // 护栏线 12000ms：全量套件并行噪声余量（PRD 性能 SLA 见非功能约束 §5.2，
    // 单独运行实测值记入回传存证）
    expect(elapsed, `10 万行首帧实测 ${elapsed.toFixed(0)}ms（护栏线 12000ms）`).toBeLessThan(12000)
    w.unmount()
  })
})
