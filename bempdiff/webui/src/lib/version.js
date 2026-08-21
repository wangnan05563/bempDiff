// 包文件名智能版本识别与排序（与后端 PackageVersion.java 逻辑镜像，用于前端展示/自动排序）。
// 支持：点分式 1.6.1 / v2.0.1-SNAPSHOT；构建号式 036M061(20260707-1135)。
// 同名不同版本判定：去扩展名后公共前缀 ≥4 字符、剩余尾段均形似版本（含数字且仅含数字/字母/()/._-）且不同。

const DOTTED = /[Vv]?(\d+(?:\.\d+)+(?:[-_][A-Za-z0-9]+)?)$|^[Vv]?(\d+)$/
const BUILD_TAIL = /([A-Za-z]*\d{2,}[A-Za-z]*\d*(?:\([^()]*\))?(?:[-_][A-Za-z0-9]+)?)$/g
const VERSION_TAIL_CHARS = /^[A-Za-z0-9()._\-]+$/

function fileName(path) {
  if (!path) return ''
  const n = String(path).replace(/\\/g, '/')
  const idx = n.lastIndexOf('/')
  return idx >= 0 ? n.slice(idx + 1) : n
}

function stripExtension(name) {
  const dot = name.lastIndexOf('.')
  const slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'))
  if (dot > 0 && dot > slash) return name.slice(0, dot)
  return name
}

/** 从文件名提取版本号（无则 null）。 */
export function extractFromFileName(path) {
  const stem = stripExtension(fileName(path))
  const dm = stem.match(DOTTED)
  if (dm) return dm[1] || dm[2]
  BUILD_TAIL.lastIndex = 0
  let best = null
  let m
  while ((m = BUILD_TAIL.exec(stem)) !== null) best = m[1]
  return best
}

function longestCommonPrefix(a, b) {
  const n = Math.min(a.length, b.length)
  let i = 0
  while (i < n && a[i] === b[i]) i++
  return a.slice(0, i)
}

function looksVersionTail(t) {
  return !!t && /\d/.test(t) && VERSION_TAIL_CHARS.test(t)
}

/** 两个文件名是否为同一包的不同版本。 */
export function sameBaseDifferentVersion(a, b) {
  if (!a || !b || a === b) return false
  const sa = stripExtension(fileName(a))
  const sb = stripExtension(fileName(b))
  if (sa === sb) return false
  const lcp = longestCommonPrefix(sa, sb)
  if (lcp.length < 4) return false
  const ra = sa.slice(lcp.length)
  const rb = sb.slice(lcp.length)
  if (!ra || !rb) return false
  if (!looksVersionTail(ra) || !looksVersionTail(rb)) return false
  return ra !== rb
}

function isNumeric(s) {
  return /^\d+$/.test(s)
}

function compareToken(x, y) {
  const xn = isNumeric(x), yn = isNumeric(y)
  if (xn && yn) return Number(x) - Number(y)
  if (xn) return -1
  if (yn) return 1
  const a = x.toLowerCase(), b = y.toLowerCase()
  return a < b ? -1 : a > b ? 1 : 0
}

/** 版本排序：a 旧于 b 返回负数。 */
export function compareVersions(a, b) {
  const va = a || ''
  const vb = b || ''
  const ta = va.match(/\d+|[^\d]/g) || []
  const tb = vb.match(/\d+|[^\d]/g) || []
  const n = Math.max(ta.length, tb.length)
  for (let i = 0; i < n; i++) {
    const c = compareToken(ta[i] || '', tb[i] || '')
    if (c !== 0) return c
  }
  return 0
}

/** 智能配对：同名不同版本 → [旧, 新]；否则原样 [a, b]。 */
export function orderOldNew(a, b) {
  if (sameBaseDifferentVersion(a, b) && compareVersions(extractFromFileName(a), extractFromFileName(b)) > 0) {
    return [b, a]
  }
  return [a, b]
}
