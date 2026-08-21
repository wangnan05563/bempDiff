// 语法高亮 tokenizer：把一行代码/文本切分为带类型的 token 序列 [{type, text}]，
// 供 DiffView 的 diff gutter 双栏渲染做「语法着色 + 差异标记分层」。
//
// 设计原则：
//  1. 一套语义色板（CSS 变量 --tok-*，见 DiffView scoped 样式），跨语言视觉统一；
//     语言差异体现在「产出哪些 token 类型」（关键字集 / HTML 标签 / Markdown 标题…）。
//  2. 语义色刻意避开差异标记三系（红=删除、绿=新增、橙=修改），
//     从源头消除「语法色与 diff 色混淆」；diff 片段内 token 再由 CSS 加深保可读（见 .im-del/.im-add .tok）。
//  3. 词法为轻量状态机（字符串转义 / 块注释 / 行注释），单遍扫描，O(n) 每行；
//     配合 DiffView 虚拟滚动只渲染可见行 + 结果并入行内 diff 缓存，大文件无额外负担。
//
// 新增语言步骤：加关键字到 KEYWORDS / CODE_CONF 配置，或在 tokenizeLine 分发里加专用函数，
// 再到 DiffView scoped 样式给新 token 类型补 .tok-* 色类（无新类型则无需动样式）。

/** 各语言共用关键字超集（Java/JS/TS/Python 常用，按需扩充）。 */
const KEYWORDS = new Set([
  'abstract','assert','boolean','break','byte','case','catch','char','class','const',
  'continue','default','do','double','else','enum','extends','final','finally','float',
  'for','goto','if','implements','import','instanceof','int','interface','long','native',
  'new','package','private','protected','public','return','short','static','strictfp',
  'super','switch','synchronized','this','throw','throws','transient','try','void',
  'volatile','while','var','let','function','async','await','export','typeof','delete',
  'in','of','yield','null','true','false','undefined','NaN','Infinity','def','print',
  'from','as','with','using','global','lambda','record','sealed','permits','yield',
  'elif','except','finally','raise','pass','not','and','or','is','None','True','False',
  'nonlocal','match','case'
])

/** 语言词法配置（C 系与 Python 共用 tokenizeCode，仅注释/引号规则不同）。 */
const CODE_CONF = {
  js:     { lineComment: '//', blockComment: ['/*', '*/'], quotes: ['"', "'", '`'] },
  ts:     { lineComment: '//', blockComment: ['/*', '*/'], quotes: ['"', "'", '`'] },
  java:   { lineComment: '//', blockComment: ['/*', '*/'], quotes: ['"', "'"] },
  python: { hashComment: true, quotes: ['"', "'"] }
}

/** 标准 token 类型表（新增类型需同步 DiffView scoped 样式补 .tok-* 色类）。 */
export const TOKEN_TYPES = [
  'keyword', 'string', 'comment', 'number', 'ident', 'func', 'type',
  'tag', 'attr', 'heading', 'link', 'emph', 'inlinecode', 'op', 'ws', 'plain'
]

function isWordStart(ch) { return /[A-Za-z_$]/.test(ch) }
function isWord(ch) { return /[\w$]/.test(ch) }

/** 通用代码词法（C 系 / Python）。 */
function tokenizeCode(line, conf) {
  const segs = []
  const n = line.length
  let i = 0
  const push = (type, text) => {
    if (!text) return
    const last = segs[segs.length - 1]
    if (last && last.type === type && (type === 'ws' || type === 'plain')) last.text += text
    else segs.push({ type, text })
  }
  while (i < n) {
    const ch = line[i]
    const nx = line[i + 1]
    // 行注释（C 系 // 或 Python #）
    if (conf.lineComment && ch === '/' && nx === '/') { push('comment', line.slice(i)); break }
    if (conf.hashComment && ch === '#') { push('comment', line.slice(i)); break }
    // 块注释 /* */
    if (conf.blockComment && ch === '/' && nx === '*') {
      const end = line.indexOf('*/', i + 2)
      push('comment', end < 0 ? line.slice(i) : line.slice(i, end + 2))
      i = end < 0 ? n : end + 2
      continue
    }
    // 字符串（含转义）
    if (conf.quotes.includes(ch)) {
      const q = ch
      let j = i + 1
      let esc = false
      while (j < n) {
        const c = line[j]
        if (esc) { esc = false; j++; continue }
        if (c === '\\') { esc = true; j++; continue }
        if (c === q) { j++; break }
        j++
      }
      push('string', line.slice(i, j))
      i = j
      continue
    }
    // 空白
    if (/\s/.test(ch)) {
      let j = i
      while (j < n && /\s/.test(line[j])) j++
      push('ws', line.slice(i, j)); i = j
      continue
    }
    // 数字（含小数点；1e-5 会被拆成 "1e" + "-" + "5"，可接受）
    if (/\d/.test(ch) || (ch === '.' && /\d/.test(nx || ''))) {
      let j = i
      while (j < n && /[\w.]/.test(line[j])) j++
      push('number', line.slice(i, j)); i = j
      continue
    }
    // 标识符 / 关键字 / 函数调用 / 类型名
    if (isWordStart(ch)) {
      let j = i
      while (j < n && isWord(line[j])) j++
      const w = line.slice(i, j)
      if (KEYWORDS.has(w)) push('keyword', w)
      else {
        let k = j
        while (k < n && /\s/.test(line[k])) k++
        if (line[k] === '(') push('func', w)            // 后跟 ( → 函数调用
        else if (/^[A-Z]/.test(w)) push('type', w)      // 首字母大写 → 类型/类名
        else push('ident', w)
      }
      i = j
      continue
    }
    // 运算符 / 分隔符（连续合并）
    if (/[+\-*/%=!<>&|^~?:.,;()\[\]{}@#$]/.test(ch)) {
      let j = i
      while (j < n && /[+\-*/%=!<>&|^~?:.,;()\[\]{}@#$]/.test(line[j])) j++
      push('op', line.slice(i, j)); i = j
      continue
    }
    push('plain', ch); i++
  }
  return segs
}

/** HTML/JSP：标签、属性、引号值、注释、文本。 */
function tokenizeHtml(line) {
  const segs = []
  const n = line.length
  let i = 0
  const push = (type, text) => {
    if (!text) return
    const last = segs[segs.length - 1]
    if (last && last.type === type && (type === 'ws' || type === 'plain')) last.text += text
    else segs.push({ type, text })
  }
  while (i < n) {
    const rest = line.slice(i)
    const mCom = /^<!--[\s\S]*?-->/.exec(rest)
    if (mCom) { push('comment', mCom[0]); i += mCom[0].length; continue }
    const mTag = /^<\/?[A-Za-z][\w-]*/.exec(rest)
    if (mTag) { push('tag', mTag[0]); i += mTag[0].length; continue }
    const mClose = /^\/?>/.exec(rest)
    if (mClose) { push('op', mClose[0]); i += mClose[0].length; continue }
    const mAttr = /^[A-Za-z_:][\w:.-]*(?=\s*=)/.exec(rest)
    if (mAttr) { push('attr', mAttr[0]); i += mAttr[0].length; continue }
    if (rest[0] === '=') { push('op', '='); i++; continue }
    const mStr = /^"[^"]*"|^'[^']*'/.exec(rest)
    if (mStr) { push('string', mStr[0]); i += mStr[0].length; continue }
    const mWs = /^\s+/.exec(rest)
    if (mWs) { push('ws', mWs[0]); i += mWs[0].length; continue }
    let j = i
    while (j < n && !/[\s<>=/"']/.test(line[j])) j++
    push('plain', line.slice(i, Math.max(j, i + 1))); i = Math.max(j, i + 1)
  }
  return segs
}

/** CSS：注释、字符串、颜色/数值、变量、属性名、选择器、分隔符。 */
function tokenizeCss(line) {
  const segs = []
  const n = line.length
  let i = 0
  const push = (type, text) => {
    if (!text) return
    const last = segs[segs.length - 1]
    if (last && last.type === type && (type === 'ws' || type === 'plain')) last.text += text
    else segs.push({ type, text })
  }
  while (i < n) {
    const rest = line.slice(i)
    const mCom = /^\/\*[\s\S]*?\*\//.exec(rest)
    if (mCom) { push('comment', mCom[0]); i += mCom[0].length; continue }
    const mStr = /^"[^"]*"|^'[^']*'/.exec(rest)
    if (mStr) { push('string', mStr[0]); i += mStr[0].length; continue }
    const mHex = /^#[\da-fA-F]{3,8}\b/.exec(rest)
    if (mHex) { push('number', mHex[0]); i += mHex[0].length; continue }
    const mNum = /^[\d.]+(?:px|em|rem|%|vh|vw|vmin|vmax|s|ms|deg|fr|ch|ex|cm|mm|in|pt|pc)?/.exec(rest)
    if (mNum) { push('number', mNum[0]); i += mNum[0].length; continue }
    const mVar = /^--[\w-]+/.exec(rest)
    if (mVar) { push('attr', mVar[0]); i += mVar[0].length; continue }
    const mProp = /^[\w-]+(?=\s*:)/.exec(rest)
    if (mProp) { push('attr', mProp[0]); i += mProp[0].length; continue }
    const mSel = /^\.?[\w-]+/.exec(rest)
    if (mSel) { push('ident', mSel[0]); i += mSel[0].length; continue }
    if (/[{}:;,>~+*]/.test(rest[0])) { push('op', rest[0]); i++; continue }
    const mWs = /^\s+/.exec(rest)
    if (mWs) { push('ws', mWs[0]); i += mWs[0].length; continue }
    push('plain', line[i]); i++
  }
  return segs
}

/** Markdown：标题、引用、分隔线、行内代码、链接、粗体/斜体。 */
function tokenizeMarkdown(line) {
  const segs = []
  const n = line.length
  const push = (type, text) => {
    if (!text) return
    const last = segs[segs.length - 1]
    if (last && last.type === type && (type === 'ws' || type === 'plain')) last.text += text
    else segs.push({ type, text })
  }
  const mH = /^(#{1,6})(\s+)(.*)$/.exec(line)
  if (mH) {
    push('op', mH[1]); push('ws', mH[2]); push('heading', mH[3])
    return segs
  }
  const mQ = /^>\s?/.exec(line)
  let i = 0
  if (mQ) { push('op', mQ[0]); i = mQ[0].length }
  if (/^([-*_])\1{2,}\s*$/.test(line)) { push('op', line.trim()); return segs }
  while (i < n) {
    const rest = line.slice(i)
    const mCode = /^`[^`]*`/.exec(rest)
    if (mCode) { push('inlinecode', mCode[0]); i += mCode[0].length; continue }
    const mLink = /^\[([^\]]*)\]\(([^)]*)\)/.exec(rest)
    if (mLink) {
      push('link', '[' + mLink[1] + ']')
      push('string', '(' + mLink[2] + ')')
      i += mLink[0].length
      continue
    }
    const mBold = /^\*\*([^*]+)\*\*|^__([^_]+)__/.exec(rest)
    if (mBold) { push('emph', mBold[0]); i += mBold[0].length; continue }
    const mItal = /^\*([^*]+)\*|^_([^_]+)_/.exec(rest)
    if (mItal) { push('emph', mItal[0]); i += mItal[0].length; continue }
    const mWs = /^\s+/.exec(rest)
    if (mWs) { push('ws', mWs[0]); i += mWs[0].length; continue }
    push('plain', line[i]); i++
  }
  return segs
}

/** 扩展名 → 语言（优先于 FileClass，覆盖 .md/.py/.ts 等后端不细分类型的文件）。 */
const EXT_LANG = [
  ['.js', 'js'], ['.mjs', 'js'], ['.cjs', 'js'], ['.jsx', 'js'],
  ['.ts', 'ts'], ['.tsx', 'ts'],
  ['.java', 'java'],
  ['.py', 'python'], ['.pyw', 'python'],
  ['.html', 'html'], ['.htm', 'html'], ['.xhtml', 'html'],
  ['.css', 'css'], ['.scss', 'css'], ['.less', 'css'],
  ['.md', 'markdown'], ['.markdown', 'markdown'],
  ['.jsp', 'jsp'], ['.jspx', 'jsp'], ['.tag', 'jsp'], ['.tagx', 'jsp']
]

/** 文件类型（FileClass）→ 语言兜底映射。 */
const FC_LANG = { CLASS: 'java', JS: 'js', HTML: 'html', CSS: 'css', JSP: 'jsp' }

/**
 * 判定某文件使用的高亮语言。
 * @param {string} key 文件完整 key（路径）
 * @param {string} fc FileClass（CLASS/JS/HTML/CSS/...，来自后端）
 * @returns {string} 'js' | 'ts' | 'java' | 'python' | 'html' | 'css' | 'jsp' | 'markdown' | 'plain'
 */
export function langOf(key, fc) {
  const k = (key || '').toLowerCase()
  for (const [ext, lang] of EXT_LANG) {
    if (k.endsWith(ext)) return lang
  }
  return FC_LANG[fc] || 'plain'
}

/**
 * 整行 tokenize。
 * @param {string} line 单行文本（不含换行符）
 * @param {string} lang langOf 返回的语言
 * @returns {Array<{type:string, text:string}>}
 */
export function tokenizeLine(line, lang) {
  line = line || ''
  if (!line) return []
  switch (lang) {
    case 'html':
    case 'jsp':
      return tokenizeHtml(line)
    case 'css':
      return tokenizeCss(line)
    case 'markdown':
      return tokenizeMarkdown(line)
    case 'plain':
      return [{ type: 'plain', text: line }]
    default:
      return tokenizeCode(line, CODE_CONF[lang] || CODE_CONF.js)
  }
}
