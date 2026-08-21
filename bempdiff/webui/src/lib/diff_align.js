// 行级对齐（Beyond Compare 风格「同行显示」）：把旧/新行序列按 LCS 对齐成行对。
// 输出 [{ type, left, right }]：
//  - ctx : 两侧相同行（1:1）
//  - rep : 两侧对应但内容不同（1:1）——同一水平行上左右对照，配合行内差异做“替换”高亮
//  - del : 仅旧侧（1:0，删除行；对侧留空单元格）
//  - add : 仅新侧（0:1，新增行；对侧留空单元格）
//
// 对齐策略（对标 Beyond Compare / GitHub split diff）：
//  1) 先用行级 LCS 找精确相同的公共行（ctx），剩余 del/add 组成「变更块」；
//  2) 变更块内用「相似度」把近似相同的旧/新行配成 rep（修改对，行内再做字符/词级高亮），
//     相似度过低或数量不对等的余量仍为 del/add —— 避免把“插入的整段新代码”错配成修改；
//  3) 超大输入退化为线性对齐（公共前后缀 + 中段配对），大文件不卡 UI。
const MAX_CELLS = 6000000 // 6e6 单元格 ≈ 24MB，超过退化为线性对齐
const SIM_THRESH = 0.5    // 修改对相似度阈值（公共前后缀占比）
const MAX_RUN = 80        // 变更块行数护栏：超限直接全量 del/add，避免配对 DP 失控

/** 快速相似度：公共前缀+后缀长度 / 最大长度（0~1）。相等行 = 1，任一侧空 = 0。 */
export function simRatio(a, b) {
  if (a === b) return 1
  if (!a || !b) return 0
  const n = a.length
  const m = b.length
  const maxLen = Math.max(n, m)
  if (maxLen === 0) return 1
  let p = 0
  while (p < n && p < m && a[p] === b[p]) p++
  let s = 0
  while (s < n - p && s < m - p && a[n - 1 - s] === b[m - 1 - s]) s++
  return (p + s) / maxLen
}

/**
 * 变更块内相似行配对：把 dels 与 adds 按相似度做最优配对（DP 最大化总分）。
 * 返回配对下标数组 [[di, ai], ...]；未配对的余量由调用方保持 del/add。
 * 相似度低于阈值的配对得 0 分、自动不配；块超过 MAX_RUN 直接不配（全量 del/add）。
 * dels/adds: [{ s }]（s = 行文本）。
 */
export function pairRun(dels, adds) {
  const n = dels.length
  const m = adds.length
  if (!n || !m || n > MAX_RUN || m > MAX_RUN) return []
  const sim = []
  for (let i = 0; i < n; i++) {
    const row = new Array(m)
    for (let j = 0; j < m; j++) row[j] = simRatio(dels[i].s, adds[j].s)
    sim.push(row)
  }
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      const score = sim[i][j] >= SIM_THRESH ? sim[i][j] : 0
      const pair = dp[i + 1][j + 1] + score
      dp[i][j] = Math.max(pair, dp[i + 1][j], dp[i][j + 1])
    }
  }
  const pairs = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    const score = sim[i][j] >= SIM_THRESH ? sim[i][j] : 0
    if (score > 0 && dp[i][j] === dp[i + 1][j + 1] + score) { pairs.push([i, j]); i++; j++ }
    else if (dp[i][j] === dp[i + 1][j]) i++
    else j++
  }
  return pairs
}

/**
 * 变更块后处理：把一段连续的 del/add trace 行，按相似度配成 rep，余量保持 del/add。
 * trace: [{ type:'del'|'add', left, right }]，输出 [{ type:'rep'|'del'|'add', left, right }]。
 */
export function postPair(trace) {
  const out = []
  let i = 0
  const n = trace.length
  while (i < n) {
    const t = trace[i]
    if (t.type !== 'del' && t.type !== 'add') { out.push(t); i++; continue }
    // 收集连续 del/add 变更块
    const dels = []
    const adds = []
    const start = i
    while (i < n && (trace[i].type === 'del' || trace[i].type === 'add')) {
      if (trace[i].type === 'del') dels.push({ s: trace[i].left })
      else adds.push({ s: trace[i].right })
      i++
    }
    const pairs = pairRun(dels, adds)
    if (!pairs.length) {
      // 无配对：原样输出
      for (let k = start; k < i; k++) {
        const tt = trace[k]
        out.push(tt.type === 'del' ? { type: 'del', left: tt.left, right: '' } : { type: 'add', left: '', right: tt.right })
      }
      continue
    }
    const paired = new Map() // del 下标 -> add 下标
    const usedAdd = new Set()
    for (const [di, ai] of pairs) { paired.set(di, ai); usedAdd.add(ai) }
    // 按原顺序输出：del 行遇到配对 → rep（对侧取配对的 add 文本）；add 行已被配对消费则跳过
    let delPos = 0
    let addPos = 0
    for (let k = start; k < i; k++) {
      const tt = trace[k]
      if (tt.type === 'del') {
        const ai = paired.get(delPos)
        if (ai !== undefined) out.push({ type: 'rep', left: tt.left, right: adds[ai].s })
        else out.push({ type: 'del', left: tt.left, right: '' })
        delPos++
      } else {
        if (!usedAdd.has(addPos)) out.push({ type: 'add', left: '', right: tt.right })
        addPos++
      }
    }
  }
  return out
}

/** 完整 LCS 对齐（小输入）：精确相同行 ctx，其余变更块做相似配对。 */
function lcsAlign(a, b) {
  const n = a.length
  const m = b.length
  const dp = Array.from({ length: n + 1 }, () => new Int32Array(m + 1))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  const trace = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) { trace.push({ type: 'ctx', left: a[i], right: b[j] }); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { trace.push({ type: 'del', left: a[i], right: '' }); i++ }
    else { trace.push({ type: 'add', left: '', right: b[j] }); j++ }
  }
  while (i < n) { trace.push({ type: 'del', left: a[i], right: '' }); i++ }
  while (j < m) { trace.push({ type: 'add', left: '', right: b[j] }); j++ }
  return postPair(trace)
}

/** 线性对齐（超大输入兜底）：公共前缀/后缀 + 中段 del/add 变更块做相似配对。 */
function linearAlign(a, b) {
  const n = a.length
  const m = b.length
  const out = []
  let i = 0
  let j = 0
  while (i < n && j < m && a[i] === b[j]) { out.push({ type: 'ctx', left: a[i], right: b[j] }); i++; j++ }
  let s = 0
  while (s < n - i && s < m - j && a[n - 1 - s] === b[m - 1 - s]) s++
  const midOld = n - i - s
  const midNew = m - j - s
  const midTrace = []
  for (let t = 0; t < midOld; t++) midTrace.push({ type: 'del', left: a[i + t], right: '' })
  for (let t = 0; t < midNew; t++) midTrace.push({ type: 'add', left: '', right: b[j + t] })
  out.push(...postPair(midTrace))
  for (let t = 0; t < s; t++) out.push({ type: 'ctx', left: a[n - s + t], right: b[m - s + t] })
  return out
}

export function alignLines(oldLines, newLines) {
  const n = oldLines.length
  const m = newLines.length
  if (n * m <= MAX_CELLS) return lcsAlign(oldLines, newLines)
  return linearAlign(oldLines, newLines)
}
