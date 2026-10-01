/**
 * 全局快捷键注册表（二期 R6 / T01465）。
 *
 * 设计要点：
 * - 纯数据 + 纯函数：速查面板（ShortcutHelp）与单测共用同一份清单，避免「实际绑定」与「文档」漂移。
 * - 冲突原则：输入框 / 可编辑元素内不劫持任何全局键（isEditableTarget 统一判定）；
 *   Ctrl+A/C 的「无原生文本选区才接管」语义在 DiffView 内实现（此处仅登记文档）。
 */
export const SHORTCUTS = [
  { keys: 'Ctrl+Enter', label: '开始比对', scope: '全局' },
  { keys: 'Ctrl+K', label: '定位到差异树过滤框', scope: '全局' },
  { keys: '?', label: '快捷键速查面板', scope: '全局' },
  { keys: 'Ctrl/Alt + ↑/↓', label: '跳转上一处 / 下一处差异', scope: '差异视图' },
  { keys: 'Ctrl+F', label: '差异内容内查找', scope: '差异视图' },
  { keys: 'Ctrl+A', label: '全选差异行（无原生文本选区时）', scope: '差异视图' },
  { keys: 'Ctrl+C', label: '复制选中行（无原生文本选区时）', scope: '差异视图' },
  { keys: 'Esc', label: '关闭查找 / 清除行选择 / 退出专注模式', scope: '差异视图' }
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
