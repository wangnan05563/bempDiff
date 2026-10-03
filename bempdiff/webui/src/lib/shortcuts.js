/**
 * 全局快捷键注册表（二期 R6 / T01465）。
 *
 * 设计要点：
 * - 纯数据 + 纯函数：速查面板（ShortcutHelp）与单测共用同一份清单，避免「实际绑定」与「文档」漂移。
 * - 冲突原则：输入框 / 可编辑元素内不劫持任何全局键（isEditableTarget 统一判定）；
 *   Ctrl+A/C 的「无原生文本选区才接管」语义在 DiffView 内实现（此处仅登记文档）。
 */
// label/scope 保留中文原文（兼容既有单测与未迁移调用方），
// labelKey/scopeKey 为 i18n 键：渲染侧优先 t(labelKey)、缺失时回退 label。
export const SHORTCUTS = [
  { keys: 'Ctrl+Enter', label: '开始比对', labelKey: 'sc.startCompare', scope: '全局', scopeKey: 'sc.scope.global' },
  { keys: 'Ctrl+K', label: '定位到差异树过滤框', labelKey: 'sc.focusFilter', scope: '全局', scopeKey: 'sc.scope.global' },
  { keys: '?', label: '快捷键速查面板', labelKey: 'sc.shortcutHelp', scope: '全局', scopeKey: 'sc.scope.global' },
  { keys: 'Ctrl/Alt + ↑/↓', label: '跳转上一处 / 下一处差异', labelKey: 'sc.prevNextDiff', scope: '差异视图', scopeKey: 'sc.scope.diff' },
  { keys: 'Ctrl+F', label: '差异内容内查找', labelKey: 'sc.findInDiff', scope: '差异视图', scopeKey: 'sc.scope.diff' },
  { keys: 'Ctrl+A', label: '全选差异行（无原生文本选区时）', labelKey: 'sc.selectAllRows', scope: '差异视图', scopeKey: 'sc.scope.diff' },
  { keys: 'Ctrl+C', label: '复制选中行（无原生文本选区时）', labelKey: 'sc.copyRows', scope: '差异视图', scopeKey: 'sc.scope.diff' },
  { keys: 'Esc', label: '关闭查找 / 清除行选择 / 退出专注模式', labelKey: 'sc.escMulti', scope: '差异视图', scopeKey: 'sc.scope.diff' }
]

/** 按作用域分组（速查面板渲染用；顺序与 SHORTCUTS 一致）。 */
export function groupByScope(list) {
  const groups = []
  const idx = new Map()
  for (const s of list || []) {
    if (!idx.has(s.scope)) {
      idx.set(s.scope, { scope: s.scope, items: [] })
      groups.push(idx.get(s.scope))
    }
    idx.get(s.scope).items.push(s)
  }
  return groups
}

/** 输入框 / 文本域 / 可编辑元素内不劫持全局快捷键。 */
export function isEditableTarget(e) {
  const t = e && e.target
  if (!t) return false
  const tag = t.tagName || ''
  return tag === 'INPUT' || tag === 'TEXTAREA' || t.isContentEditable === true
}
