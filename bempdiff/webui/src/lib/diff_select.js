/**
 * 差异行选择与复制（二期 R2 / T01453）——纯函数，供 DiffView 行级选中交互使用。
 *
 * 设计要点：
 * - 选择集以「原始行下标 _i（ri）」为键（Set<number>），split 双栏与 unified 拍平行同源共享，
 *   切换视图模式不丢选择。
 * - 复制按 side（'left'|'right'）取对应侧文本；该侧不存在的行（如 add 行无左内容）跳过，
 *   与「按侧摘录证据行」的审计场景一致。
 * - 行号前缀可选（格式 `12: text`），仅在该侧有行号时附加；偏好由组件持久化。
 */

/**
 * Shift 范围选择：返回 [anchor, ri] 闭区间的行下标数组（升序）。
 * anchor 与 ri 相等时返回单元素。
 */
export function rangeSelection(anchor, ri) {
  const a = Math.max(0, Math.min(anchor, ri))
  const b = Math.max(anchor, ri)
  const out = []
  for (let i = a; i <= b; i++) out.push(i)
  return out
}

/**
 * 构建复制文本。
 * @param {Array} rows 对齐后的行数组（元素含 left/leftText/right/rightText）
 * @param {number[]|Set<number>} ris 选中的原始行下标集合
 * @param {'left'|'right'} side 复制侧
 * @param {{withLineNo?: boolean}} opts
 * @returns {string} 以 \n 连接的文本；无有效行返回 ''
 */
export function buildCopyText(rows, ris, side, opts = {}) {
  if (!rows || !rows.length) return ''
  const list = Array.from(ris || []).sort((a, b) => a - b)
  const withLineNo = !!opts.withLineNo
  const out = []
  for (const ri of list) {
    const r = rows[ri]
    if (!r) continue
    const lineNo = side === 'left' ? r.left : r.right
    const text = side === 'left' ? r.leftText : r.rightText
    // 该侧无内容（add/del 单侧行的另一侧）→ 跳过；text 为空但有行号视为空行保留
    if (lineNo === '' || lineNo == null) {
      if (!text) continue
    }
    out.push(withLineNo && lineNo ? lineNo + ': ' + text : (text || ''))
  }
  return out.join('\n')
}

/** 全选：返回覆盖 0..n-1 的行下标数组（大文件按需转 Set）。 */
export function allRowIndexes(n) {
  const out = new Array(n)
  for (let i = 0; i < n; i++) out[i] = i
  return out
}
