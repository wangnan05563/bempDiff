// 行内差异高亮：字符级或词级 LCS，返回片段数组 [{t:'eq'|'del'|'add', s}]。
// 用于双栏 diff 的“词/字符级”高亮（对标 GitHub inline diff）。
// n*m 上限保护：超长行（单行 minified 等）退化为整行高亮，避免 O(n*m) 卡 UI。
//
// mode: 'char'（默认，逐字符）| 'word'（按空白/符号分词后逐词）

// 通用数组 LCS：a/b 为「可比对的单元数组」（字符数组或词数组）。
// 返回片段数组；若超过 max 上限则返回 null（调用方退化为整行高亮）。
function lcsTokens(a, b, max) {
  const n = a.length, m = b.length
  if (n === 0 && m === 0) return []
  if (n * m > max) return null
  const dp = Array.from({ length: n + 1 }, () => new Int32Array(m + 1))
  for (let i = n - 1; i >= 0; i--)
    for (let j = m - 1; j >= 0; j--)
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
  const segs = []
  let i = 0, j = 0, cur = null
  const push = (t, s) => {
    if (cur && cur.t === t) cur.s += s
    else { cur = { t, s }; segs.push(cur) }
  }
  while (i < n && j < m) {
    if (a[i] === b[j]) { push('eq', a[i]); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { push('del', a[i]); i++ }
    else { push('add', b[j]); j++ }
  }
  while (i < n) { push('del', a[i]); i++ }
  while (j < m) { push('add', b[j]); j++ }
  return segs
}

// 词级分词：保留空白 token，使拼接后还原原始文本。
function tokenize(s) {
  return s.match(/\s+|[^\s]+/g) || []
}

export function inlineDiff(oldS, newS, mode = 'char') {
  const wordMode = mode === 'word'
  const a = wordMode ? tokenize(oldS) : Array.from(oldS)
  const b = wordMode ? tokenize(newS) : Array.from(newS)
  const max = wordMode ? 200000 : 40000
  const segs = lcsTokens(a, b, max)
  if (!segs) return [{ t: 'del', s: oldS }, { t: 'add', s: newS }]
  return segs
}
