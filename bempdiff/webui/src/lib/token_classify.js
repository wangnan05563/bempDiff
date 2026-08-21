// 代码元素（token）类型识别：给定一行文本与 0 基列号，返回该位置的元素类型。
// 用于对比栏底部状态栏的「对比内容属性展示」——判断光标所在位置属于
// 关键字 / 字符串 / 注释 / 数字 / 标识符 / 运算符 / 空白 / 行尾 / 空行 等。
//
// 算法：从行首单遍扫描到目标列，同步跟踪「字符串 / 块注释」状态（尊重转义），
// 命中位置后按字符形态分类。支持 Java / JS / TS / CSS / HTML 等常见语法的基本词法。

/** 常见关键字集合（Java / JS / TS 常用子集，按需扩充）。 */
const KEYWORDS = new Set([
  'abstract','assert','boolean','break','byte','case','catch','char','class','const',
  'continue','default','do','double','else','enum','extends','final','finally','float',
  'for','goto','if','implements','import','instanceof','int','interface','long','native',
  'new','package','private','protected','public','return','short','static','strictfp',
  'super','switch','synchronized','this','throw','throws','transient','try','void',
  'volatile','while','var','let','function','async','await','export','typeof','delete',
  'in','of','yield','null','true','false','undefined','NaN','Infinity','def','print',
  'from','as','with','using'
])

/** 单字符运算符（含括号/分隔符）。 */
const OPERATOR_CHARS = new Set('+-*/%=!<>&|^~?:.,;()[]{}@$#\\'.split(''))

/** 类型 → 展示元信息（label 中文名 + 主题色，明暗主题均可读）。 */
export const TOKEN_META = {
  keyword: { label: '关键字', color: '#7c3aed' },
  string:  { label: '字符串', color: '#198754' },
  comment: { label: '注释',   color: '#6c757d' },
  number:  { label: '数字',   color: '#fd7e14' },
  ident:   { label: '标识符', color: '#0d6efd' },
  op:      { label: '运算符', color: '#0dcaf0' },
  ws:      { label: '空白',   color: '#adb5bd' },
  eol:     { label: '行尾',   color: '#adb5bd' },
  blank:   { label: '空行',   color: '#adb5bd' },
  other:   { label: '其他',   color: '#adb5bd' }
}

/** 从 0 基列号 c0 向左右扩展同字符类词元。 */
function expandWord(line, c0, isWordChar) {
  let s = c0
  let e = c0
  while (s - 1 >= 0 && isWordChar(line[s - 1])) s--
  while (e < line.length && isWordChar(line[e])) e++
  return line.slice(s, e)
}

/**
 * 识别 line 中 0 基列号 c0 处的元素类型。
 * @param {string} line 整行文本
 * @param {number} c0 0 基列号（超出行长按行尾处理）
 * @returns {{type:string, label:string, word:string}}
 */
export function tokenInfoAt(line, c0) {
  const n = (line || '').length
  if (n === 0) return { type: 'blank', label: '空行', word: '' }
  const col = Math.max(0, Math.min(c0, n))
  if (col >= n) return { type: 'eol', label: '行尾', word: '' }

  let i = 0
  let state = 'normal' // normal | string | comment
  let q = ''
  while (i <= col) {
    const ch = line[i]
    const nx = line[i + 1]
    if (state === 'comment') {
      if (ch === '*' && nx === '/') { state = 'normal'; i += 2; continue }
      i++
      continue
    }
    if (state === 'string') {
      if (ch === '\\') { i += 2; continue } // 转义：跳过下一字符
      if (ch === q) { state = 'normal'; q = '' }
      i++
      continue
    }
    // normal
    if (ch === '/' && nx === '/') {
      return { type: 'comment', label: '注释', word: line.slice(i).trim() }
    }
    if (ch === '/' && nx === '*') { state = 'comment'; i += 2; continue }
    if (ch === '"' || ch === "'" || ch === '`') { state = 'string'; q = ch; i++; continue }
    i++
  }

  // 扫描结束：落在字符串 / 块注释内部
  if (state === 'comment') return { type: 'comment', label: '注释', word: '' }
  if (state === 'string') return { type: 'string', label: '字符串', word: '' }

  const ch = line[col]
  if (/\s/.test(ch)) return { type: 'ws', label: '空白', word: '' }
  if (/\d/.test(ch)) {
    return { type: 'number', label: '数字', word: expandWord(line, col, (c) => /[\w.+-]/.test(c)) }
  }
  if (ch === '.' && /\d/.test(line[col + 1] || '')) {
    return { type: 'number', label: '数字', word: expandWord(line, col, (c) => /[\w.+-]/.test(c)) }
  }
  if (/[A-Za-z_$]/.test(ch)) {
    const w = expandWord(line, col, (c) => /[\w$]/.test(c))
    return KEYWORDS.has(w)
      ? { type: 'keyword', label: '关键字', word: w }
      : { type: 'ident', label: '标识符', word: w }
  }
  if (OPERATOR_CHARS.has(ch)) return { type: 'op', label: '运算符', word: ch }
  return { type: 'other', label: '其他', word: ch }
}
