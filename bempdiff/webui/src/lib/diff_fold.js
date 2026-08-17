// 把解析后的 rows 应用“折叠未变”，并为每个变化行分配稳定的跳转序号 _chg（>=0）。
// ctx 行：折叠模式下，仅保留落在某变化行（del/add）前后 win 窗口内的；其余连续 ctx 折叠成一条占位行。
// 设计对标 Beyond Compare 的“忽略未变更区段”——收起噪声段、不隐藏真实代码。
//
// src: 解析后的行数组（每行 {type:'ctx'|'del'|'add', ...}）
// collapse: true=折叠远离变化块的纯 ctx 行
// win: 变化行前后各保留的 context 行数
export function foldContext(src, collapse, win = 3) {
  const out = []
  let chgSeq = 0
  const N = src.length
  let i = 0
  while (i < N) {
    const r = src[i]
    if (r.type !== 'ctx') {
      out.push({ ...r, _chg: chgSeq++ }) // del / add：变化行，纳入跳转序列
      i++
      continue
    }
    if (!collapse) {
      out.push({ ...r, _chg: -1 })
      i++
      continue
    }
    let keep = false
    for (let k = i - 1; k >= 0 && k >= i - win; k--)
      if (src[k].type === 'del' || src[k].type === 'add') { keep = true; break }
    if (!keep)
      for (let k = i + 1; k < N && k <= i + win; k++)
        if (src[k].type === 'del' || src[k].type === 'add') { keep = true; break }
    if (keep) {
      out.push({ ...r, _chg: -1 })
      i++
      continue
    }
    let j = i
    while (j < N && src[j].type === 'ctx') j++
    out.push({ type: 'fold', count: j - i, _chg: -1 })
    i = j
  }
  return out
}
