// FR-014 语法高亮主题包：4 套内置色板（亮 2 / 暗 2），基于 CSS 变量按 token 类型着色。
// 变量名与 DiffView.vue scoped 段的默认色板一一对应（--tok-kw/str/com/...）；
// DiffView 以内联 style 绑定所选主题的 vars 到 .col-center——切换即时生效（无需重挂载）。
//
// 与 FR-004 双主题联动约定：dark 标记声明该主题适配套色明暗；主题切换（亮↔暗）时
// 若当前高亮主题的 dark 属性与新界面模式不符，调用方（store.setThemeHl）自动切到该模式的默认主题。

export const HL_THEME_KEY = 'bempdiff.hlTheme'

/** 13 个 token 变量名（与 DiffView scoped 默认色板同序，供校验与遍历）。 */
export const HL_VAR_NAMES = [
  'kw', 'str', 'com', 'num', 'id', 'fn', 'type', 'tag', 'attr', 'hd', 'link', 'code', 'op'
]

const defaultVars = {
  kw: '#7c3aed', str: '#b45309', com: '#64748b', num: '#0891b2', id: '#475569',
  fn: '#2563eb', type: '#0891b2', tag: '#be185d', attr: '#0284c7', hd: '#7c3aed',
  link: '#2563eb', code: '#b45309', op: '#64748b'
}

export const HL_THEMES = [
  {
    key: 'default',
    labelKey: 'dv.hl.default',
    dark: false,
    vars: { ...defaultVars }
  },
  {
    key: 'github-light',
    labelKey: 'dv.hl.githubLight',
    dark: false,
    vars: {
      kw: '#cf222e', str: '#0a3069', com: '#6e7781', num: '#0550ae', id: '#24292f',
      fn: '#8250df', type: '#953800', tag: '#116329', attr: '#0550ae', hd: '#0550ae',
      link: '#0a3069', code: '#953800', op: '#57606a'
    }
  },
  {
    key: 'github-dark',
    labelKey: 'dv.hl.githubDark',
    dark: true,
    vars: {
      kw: '#ff7b72', str: '#a5d6ff', com: '#8b949e', num: '#79c0ff', id: '#e6edf3',
      fn: '#d2a8ff', type: '#ffa657', tag: '#7ee787', attr: '#79c0ff', hd: '#79c0ff',
      link: '#a5d6ff', code: '#ffa657', op: '#8b949e'
    }
  },
  {
    key: 'one-dark',
    labelKey: 'dv.hl.oneDark',
    dark: true,
    vars: {
      kw: '#c678dd', str: '#98c379', com: '#5c6370', num: '#d19a66', id: '#abb2bf',
      fn: '#61afef', type: '#e5c07b', tag: '#e06c75', attr: '#d19a66', hd: '#61afef',
      link: '#56b6c2', code: '#98c379', op: '#56b6c2'
    }
  }
]

/** 按 key 取主题；非法 key 回退 default。 */
export function hlThemeOf(key) {
  return HL_THEMES.find(t => t.key === key) || HL_THEMES[0]
}

/** 校验主题 vars：13 个变量全部为合法 hex 色。主题包完整性护栏（新增主题时兜底）。 */
export function isValidTheme(t) {
  if (!t || typeof t.vars !== 'object') return false
  return HL_VAR_NAMES.every(n => {
    const v = t.vars[n]
    return typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v)
  })
}

/** 全量主题完整性自检：4 套齐全、key 唯一、色值合法。 */
export function validateAll() {
  if (HL_THEMES.length < 4) return false
  const keys = new Set(HL_THEMES.map(t => t.key))
  if (keys.size !== HL_THEMES.length) return false
  return HL_THEMES.every(isValidTheme)
}
