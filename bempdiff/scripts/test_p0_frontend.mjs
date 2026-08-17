// BempDiff P0 前端算法单测（可重复运行）。
// 直接 import 真实模块（webui/src/lib/*），覆盖 P0-①③④ 的核心纯算法：
//   P0-① inlineDiff        行内词级高亮
//   P0-③ foldContext       折叠未变 + 变化行序号
//   P0-④ matchSev          AI 风险严重性档位匹配
// 运行：node bempdiff/scripts/test_p0_frontend.mjs
import { inlineDiff } from '../webui/src/lib/diff_inline.js'
import { foldContext } from '../webui/src/lib/diff_fold.js'
import { matchSev } from '../webui/src/lib/severity.js'

let pass = 0, fail = 0
function check(name, cond) { if (cond) pass++; else { fail++; console.log('  FAIL:', name) } }
const recon = (segs, t) => segs.filter(s => s.t === t || s.t === 'eq').map(s => s.s).join('')

// ── P0-① inlineDiff ────────────────────────────────────────────────
check('T1 del 侧重建=原串', recon(inlineDiff('    int count = 0;', '    int counter = 0;'), 'del') === '    int count = 0;')
check('T1 add 侧重建=新串', recon(inlineDiff('    int count = 0;', '    int counter = 0;'), 'add') === '    int counter = 0;')
check('T1 替换场景含 del+eq+add', (() => { const s = inlineDiff('aXb', 'aYb'); return s.some(x => x.t === 'del') && s.some(x => x.t === 'eq') && s.some(x => x.t === 'add') })())
check('T2 标识符插入', (() => { const s = inlineDiff('void run() {}', 'void runX() {}'); return recon(s, 'del') === 'void run() {}' && recon(s, 'add') === 'void runX() {}' })())
check('T3 纯增(空原串)', (() => { const s = inlineDiff('', 'new line'); return s.length === 1 && s[0].t === 'add' })())
check('T4 纯删(空新串)', (() => { const s = inlineDiff('old line', ''); return s.length === 1 && s[0].t === 'del' })())
check('T5 完全相同=单 eq', (() => { const s = inlineDiff('same', 'same'); return s.length === 1 && s[0].t === 'eq' })())
check('T6 数字变更', (() => { const s = inlineDiff('n = 1;', 'n = 2;'); return recon(s, 'del') === 'n = 1;' && recon(s, 'add') === 'n = 2;' })())
check('T7 长行退化(n*m>40000)', (() => { const a = 'a'.repeat(300), b = 'b'.repeat(300); const s = inlineDiff(a, b); return s.length === 2 && s[0].t === 'del' && s[1].t === 'add' })())
check('T8 中文变更', (() => { const s = inlineDiff('返回 成功', '返回 失败'); return recon(s, 'del') === '返回 成功' && recon(s, 'add') === '返回 失败' })())

// ── P0-③ foldContext ───────────────────────────────────────────────
const C = () => ({ type: 'ctx' }), D = () => ({ type: 'del' }), A = () => ({ type: 'add' })
const total = rows => rows.reduce((m, r) => Math.max(m, r._chg + 1), 0)

{ // 结尾远处 ctx 折叠
  const src = [C(), C(), D(), A(), C(), C(), C(), C()]
  const d = foldContext(src, true)
  check('T9 不折叠全保留', foldContext(src, false).length === 8)
  check('T9 ctx 守恒(保留5+fold1=原6)', d.filter(r => r.type === 'ctx').length + d.filter(r => r.type === 'fold').reduce((s, r) => s + r.count, 0) === 6)
  check('T9 折叠出 1 个 fold', d.filter(r => r.type === 'fold').length === 1)
  check('T9 fold count=1', d.find(r => r.type === 'fold').count === 1)
  check('T9 变化行保留', d.filter(r => r.type === 'del' || r.type === 'add').length === 2)
  check('T9 totalChg=2', total(d) === 2)
}
{ // 开头远处 ctx 折叠
  const src = [C(), C(), C(), C(), D(), A()]
  const d = foldContext(src, true)
  check('T10 开头 4 行折叠为 1 fold', d[0].type === 'fold' && d[0].count === 4)
  check('T10 totalChg=2', total(d) === 2)
}
{ // 两个相隔较远的块，中间 ctx 折叠
  const src = [C(), C(), C(), C(), D(), A(), C(), C(), C(), C(), D(), A()]
  const d = foldContext(src, true)
  check('T11 开头折叠块 count=4', d[0].type === 'fold' && d[0].count === 4)
  check('T11 中间无额外 fold', d.filter(r => r.type === 'fold').length === 1)
  check('T11 totalChg=4', total(d) === 4)
}
{ // 全 ctx 整段折叠
  const src = Array.from({ length: 10 }, C)
  const d = foldContext(src, true)
  check('T12 全 ctx 折叠为 1 fold(count=10)', d.length === 1 && d[0].type === 'fold' && d[0].count === 10)
  check('T12 totalChg=0', total(d) === 0)
}
{ // 纯变化无折叠、序号连续
  const src = [D(), A(), D(), A()]
  const d = foldContext(src, true)
  check('T13 无 fold', d.filter(r => r.type === 'fold').length === 0)
  check('T13 _chg 连续 0..3', d.map(r => r._chg).join(',') === '0,1,2,3')
}
{ // CTX_WIN 边界精确性
  const src = [D(), A(), C(), C(), C(), C()]
  const d = foldContext(src, true)
  check('T14 仅最后 1 行折叠', d.filter(r => r.type === 'fold').length === 1 && d.find(r => r.type === 'fold').count === 1)
  check('T14 保留 3 行窗口内 ctx', d.filter(r => r.type === 'ctx').length === 3)
}

// ── P0-④ matchSev ──────────────────────────────────────────────────
check('T15 整体风险:高→high', matchSev('高', '整体风险：高') === 'sev-high')
check('T15 风险:高风险→high', matchSev('高风险', '风险：高风险') === 'sev-high')
check('T15 风险:严重→breaking', matchSev('严重', '风险：严重') === 'sev-breaking')
check('T15 风险:致命→breaking', matchSev('致命', '风险：致命') === 'sev-breaking')
check('T15 风险:中→med', matchSev('中', '风险：中') === 'sev-med')
check('T15 风险:中等→med', matchSev('中等', '风险：中等') === 'sev-med')
check('T15 风险:低→low', matchSev('低', '风险：低') === 'sev-low')
check('T15 风险:低风险→low', matchSev('低风险', '风险：低风险') === 'sev-low')
check('T15 风险:无风险→none', matchSev('无风险', '风险：无风险') === 'sev-none')
check('T15 嵌套:每文件初评风险下的高→high', matchSev('高', '每文件初评风险： key：高 — reason') === 'sev-high')
check('T15 非风险上下文(影响范围:较大)→null', matchSev('较大', '影响范围：较大') === null)
check('T15 风险但非档位词(待观察)→null', matchSev('待观察', '风险：待观察') === null)

console.log(`P0 frontend: PASS=${pass} FAIL=${fail} (total ${pass + fail})`)
process.exit(fail ? 1 : 0)
