/**
 * 主题强调色（二期 R10 / T01475）——明暗主题之上的强调色自定义。
 *
 * 设计要点：
 * - 预设色盘（含 Bootstrap 默认蓝），每项带 rgb 三元组（Bootstrap 部分变量吃 rgb 形式）；
 * - 应用方式：html 根元素 data-accent 属性 + --acc / --acc-rgb 变量，全局 CSS 重映射
 *   btn-primary / outline-primary / progress-bar / text-primary / 表单焦点等关键元素；
 * - 持久化 localStorage（bempdiff-accent），与明暗主题（bempdiff-theme）正交叠加。
 */
// name 保留中文原文（兼容既有单测 accentOf().name 断言）；nameKey 为 i18n 键（渲染侧优先）。
export const ACCENTS = [
  { key: 'blue',   name: '经典蓝', nameKey: 'acc.blue',   color: '#0d6efd', rgb: '13, 110, 253' },
  { key: 'purple', name: '典雅紫', nameKey: 'acc.purple', color: '#7c5cff', rgb: '124, 92, 255' },
  { key: 'teal',   name: '青碧',   nameKey: 'acc.teal',   color: '#0f9d8f', rgb: '15, 157, 143' },
  { key: 'green',  name: '沉稳绿', nameKey: 'acc.green',  color: '#198754', rgb: '25, 135, 84' },
  { key: 'orange', name: '活力橙', nameKey: 'acc.orange', color: '#fd7e14', rgb: '253, 126, 20' },
  { key: 'rose',   name: '玫瑰红', nameKey: 'acc.rose',   color: '#e83e8c', rgb: '232, 62, 140' }
]

export const ACCENT_KEY = 'bempdiff-accent'

/** 按.key 取色项；非法 key 回退经典蓝。 */
export function accentOf(key) {
  return ACCENTS.find(a => a.key === key) || ACCENTS[0]
}

/** 读取持久化的强调色 key（坏数据回退 blue）。 */
export function loadAccentKey() {
  try {
    const v = localStorage.getItem(ACCENT_KEY)
    if (v && ACCENTS.some(a => a.key === v)) return v
  } catch (_) { /* 非安全上下文等静默 */ }
  return 'blue'
}

/** 应用强调色：html[data-accent] + CSS 变量注入 + 持久化。 */
export function applyAccent(key) {
  const a = accentOf(key)
  try {
    localStorage.setItem(ACCENT_KEY, a.key)
    const el = document.documentElement
    el.setAttribute('data-accent', a.key)
    el.style.setProperty('--acc', a.color)
    el.style.setProperty('--acc-rgb', a.rgb)
  } catch (_) { /* 静默 */ }
  return a
}
