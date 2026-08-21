// 行级对齐（diff_align）测试：相似行配对成 rep（BCompare 风格「修改对」）、
// 纯增删保持 add/del、超大文件线性兜底也配对、行内差异片段（diff_inline）正确。
import { describe, it, expect } from 'vitest'
import { alignLines, simRatio, pairRun, postPair } from '../lib/diff_align'
import { inlineDiff } from '../lib/diff_inline'

function summarize(aligned) {
  return aligned.map(a => a.type === 'rep'
    ? `rep(${a.left}|${a.right})`
    : a.type === 'del' ? `del(${a.left})` : a.type === 'add' ? `add(${a.right})` : 'ctx')
}

describe('simRatio 相似度', () => {
  it('相同行 = 1，空行 = 0', () => {
    expect(simRatio('abc', 'abc')).toBe(1)
    expect(simRatio('abc', '')).toBe(0)
    expect(simRatio('', 'abc')).toBe(0)
    expect(simRatio('', '')).toBe(1)
  })
  it('公共前后缀占比', () => {
    expect(simRatio('getUserName() {', 'getUserDisplayName() {')).toBeGreaterThan(0.6)
    expect(simRatio('return a;', 'throw new E();')).toBeLessThan(0.5)
  })
})

describe('alignLines 相似行配对成 rep（修改对）', () => {
  it('整行只有少数变化 → rep 对，而非整行 del/add', () => {
    const oldL = ['public String getUserName() {', '    return user.get("name");', '}']
    const newL = ['public String getUserDisplayName() {', '    return user.get("displayName");', '}']
    const out = alignLines(oldL, newL)
    const types = out.map(x => x.type)
    expect(types).toEqual(['rep', 'rep', 'ctx'])
    expect(out[0]).toMatchObject({ type: 'rep', left: 'public String getUserName() {', right: 'public String getUserDisplayName() {' })
  })

  it('rep 对行内差异能定位出变化片段（左删右增）', () => {
    const out = alignLines(['return true;'], ['return false;'])
    expect(out[0].type).toBe('rep')
    const segs = inlineDiff(out[0].left, out[0].right, 'char')
    expect(segs.some(s => s.t === 'del')).toBe(true)
    expect(segs.some(s => s.t === 'add')).toBe(true)
    // LCS 下 "true"/"false" 共享尾缀 "e;"，故高亮区间为 tru/fals，eq 前后缀保留
    expect(segs.find(s => s.t === 'del').s).toBe('tru')
    expect(segs.find(s => s.t === 'add').s).toBe('fals')
    expect(segs[0].s).toBe('return ')
    expect(segs[segs.length - 1].s).toBe('e;')
  })

  it('纯插入/删除（内容不相似）→ 保持 add/del，不误配成 rep', () => {
    const oldL = ['int a = 1;', 'int b = 2;']
    const newL = ['int a = 1;', 'System.out.println("log");', 'int b = 2;']
    const out = alignLines(oldL, newL)
    expect(out.map(x => x.type)).toEqual(['ctx', 'add', 'ctx'])
  })

  it('整段重写但行间有相似 → 部分配 rep、余量 del/add', () => {
    const oldL = ['old1()', 'completelyDifferentLine()']
    const newL = ['new1()', 'completelyDifferentLine2()', 'extraAdded()']
    const out = alignLines(oldL, newL)
    const types = out.map(x => x.type)
    // old1/new1 相似→rep；completelyDifferentLine 与 _2 相似→rep；extraAdded 纯新增
    expect(types).toContain('rep')
    expect(types).toContain('add')
  })

  it('混合变更块：相似行配对、不相似行保持 del/add', () => {
    const oldL = ['same()', 'alpha = 1;', 'xxx = 2;', 'tail()']
    const newL = ['same()', 'alpha = 10;', 'brandNewStmt();', 'tail()']
    const out = alignLines(oldL, newL)
    const types = out.map(x => x.type)
    expect(types[0]).toBe('ctx')
    // alpha=1 与 alpha=10 相似 → rep
    expect(types[1]).toBe('rep')
    // xxx=2 与 brandNewStmt() 不相似 → del + add
    expect(types[2]).toBe('del')
    expect(types[3]).toBe('add')
    expect(types[4]).toBe('ctx')
  })
})

describe('postPair 边界', () => {
  it('纯 del/add 无配对：原样输出', () => {
    const trace = [
      { type: 'del', left: 'a', right: '' },
      { type: 'add', left: '', right: 'b' },
      { type: 'add', left: '', right: 'c' }
    ]
    const out = postPair(trace)
    expect(out.map(x => x.type)).toEqual(['del', 'add', 'add'])
  })
  it('跨 ctx 的多个变更块互不影响', () => {
    const trace = [
      { type: 'ctx', left: 'k', right: 'k' },
      { type: 'del', left: 'x1', right: '' },
      { type: 'add', left: '', right: 'x2' },
      { type: 'ctx', left: 'k2', right: 'k2' },
      { type: 'del', left: 'y1', right: '' }
    ]
    const out = postPair(trace)
    expect(out[0].type).toBe('ctx')
    expect(out[1].type).toBe('rep') // x1/x2 相似
    expect(out[2].type).toBe('ctx')
    expect(out[3].type).toBe('del') // 仅剩 y1
  })
  it('pairRun 空输入/超长块返回空配对', () => {
    expect(pairRun([], [])).toEqual([])
    const big = Array.from({ length: 100 }, (_, i) => ({ s: 'x' + i }))
    expect(pairRun(big, big.slice(0, 50))).toEqual([])
  })
})

describe('alignLines 大文件线性兜底（MAX_CELLS 超限）', () => {
  it('公共前缀 ctx + 中段相似行 rep + 余量 del', () => {
    // n*m > 6e6 → 走 linearAlign；公共前缀 2400 行，中段 80 删 + 50 增（均 ≤ MAX_RUN 护栏）
    const P = 2400, D = 80, A = 50
    const oldL = Array.from({ length: P }, (_, i) => 'same ' + i)
      .concat(Array.from({ length: D }, (_, i) => 'old ' + (P + i)))
    const newL = Array.from({ length: P }, (_, i) => 'same ' + i)
      .concat(Array.from({ length: A }, (_, i) => 'new ' + (P + i)))
    const out = alignLines(oldL, newL)
    const types = out.map(x => x.type)
    expect(types[0]).toBe('ctx')       // 公共前缀
    expect(types[P - 1]).toBe('ctx')
    expect(types).toContain('rep')     // old/new 相似行配对
    expect(types).toContain('del')     // old 余量删除行
  })
})

describe('inlineDiff 行内差异', () => {
  it('完全相同 → 全 eq', () => {
    expect(inlineDiff('abc', 'abc')).toEqual([{ t: 'eq', s: 'abc' }])
  })
  it('中间差异 → 前缀/差异/后缀三段', () => {
    const segs = inlineDiff('version = "1.2.3"', 'version = "1.2.4"', 'char')
    expect(segs.map(s => s.t)).toEqual(['eq', 'del', 'add', 'eq'])
    expect(segs[1].s).toBe('3')
    expect(segs[2].s).toBe('4')
  })
  it('首/尾差异不丢片段', () => {
    const segs = inlineDiff('aaa', 'bbb', 'char')
    expect(segs.map(s => s.t)).toEqual(['del', 'add'])
    expect(segs[0].s).toBe('aaa')
    expect(segs[1].s).toBe('bbb')
  })
  it('word 粒度按词切分（公共前后缀裁剪后高亮中段变化词）', () => {
    const segs = inlineDiff('return getUserName();', 'return getUserDisplayName();', 'word')
    const add = segs.find(s => s.t === 'add')
    // 前后缀裁剪后，中段 = "Display"（新增部分）；左侧无可删字符
    expect(add && add.s).toBe('Display')
    expect(segs[segs.length - 1].t).toBe('eq')
  })
  it('空串安全', () => {
    expect(inlineDiff('', '')).toEqual([{ t: 'eq', s: '' }])
    expect(inlineDiff('a', '', 'char')).toEqual([{ t: 'del', s: 'a' }])
    expect(inlineDiff('', 'a', 'char')).toEqual([{ t: 'add', s: 'a' }])
  })
})
