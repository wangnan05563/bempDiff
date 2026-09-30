/**
 * 差异内容内查找（二期 R2 / T01453）——纯函数，供 DiffView Find 对话框使用。
 *
 * 设计要点：
 * - 输入为折叠后的行数组（foldedRows：普通行含 leftText/rightText，fold 折叠条含 type:'fold'），
 *   查找范围与当前视图（全量/仅差异/折叠）天然一致。
 * - 返回行级命中：{ di（foldedRows 下标）, sides: ['left','right'] }；行级高亮 + 跳转定位，
 *   不做行内字符高亮（审计场景定位到行即可，避免侵入 segs 渲染链路拖慢大文件）。
 * - 线性单 pass 扫描，万行文件 ≤300ms（PRD R2 验收口径）。
 */

/**
 * 在折叠行数组中查找关键字。
 * @param {Array} fr foldedRows（元素：{type:'fold'} 或含 leftText/rightText 的对齐行）
 * @param {string} query 关键字（空/空白返回 []）
 * @param {{caseSensitive?: boolean}} opts
 * @returns {Array<{di:number, sides:string[]}>}
 */
export function searchRows(fr, query, opts = {}) {
  const q = (query == null ? '' : String(query))
  if (!q.trim()) return []
  const cs = !!opts.caseSensitive
  const needle = cs ? q : q.toLowerCase()
  const out = []
  for (let di = 0; di < fr.length; di++) {
    const r = fr[di]
    if (!r || r.type === 'fold') continue
    const sides = []
    const lt = r.leftText || ''
    const rt = r.rightText || ''
    const hitL = contains(lt, needle, cs)
    const hitR = contains(rt, needle, cs)
    if (hitL) sides.push('left')
    if (hitR) sides.push('right')
    if (sides.length) out.push({ di, sides })
  }
  return out
}

function contains(text, needle, cs) {
  if (!text) return false
  return cs ? text.indexOf(needle) >= 0 : text.toLowerCase().indexOf(needle) >= 0
}
