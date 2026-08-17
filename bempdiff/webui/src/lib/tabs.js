// DiffView 多 tab 对比的状态机纯函数。store.js 的 selectEntry / closeTab 实际调用它们。
//
// 设计动机：左树点击触发反编译会覆盖之前的对比页。改成 tab 后，多个反编译可并存，
// 用户在 tab 之间切换不会丢失上下文。每个 tab 形状：
//   { key, node, decompile, busy, error }
//   key       - 树节点 key（job 内唯一）
//   node      - 该 key 对应的树节点快照（避免组件二次 find 树）
//   decompile - 反编译结果，null 表示还没好；{ ok:false, ... } 表示不可反编译
//   busy      - true 表示反编译请求中（即便 decompile 仍为 null）
//   error     - 反编译抛异常的 message

/**
 * 打开/激活一个 tab。
 * - key 已在 tabs 中 → 仅切激活，返回 opened=false（调用方**不要**触发反编译）
 * - key 不在        → push 一个占位 tab（busy=true, decompile=null），返回 opened=true
 * @param {Array} tabs 当前 tabs（不可变，返回新数组）
 * @param {string|null} activeKey
 * @param {string} key  要打开的 key
 * @param {object} node 对应树节点
 * @returns {{tabs: Array, activeKey: string|null, opened: boolean}}
 */
export function activateTab(tabs, activeKey, key, node) {
  const existing = tabs.find(t => t.key === key)
  if (existing) return { tabs, activeKey: key, opened: false }
  return {
    tabs: [...tabs, { key, node, decompile: null, busy: true, error: null }],
    activeKey: key,
    opened: true
  }
}

/**
 * 关闭一个 tab。
 * - 关闭的是当前激活 → 激活邻居（右优先，再左，再 null）
 * - 关闭的不是当前激活 → activeKey 不变
 * - key 不存在 → noop
 * @returns {{tabs: Array, activeKey: string|null}}
 */
export function closeTabReducer(tabs, activeKey, key) {
  const i = tabs.findIndex(t => t.key === key)
  if (i < 0) return { tabs, activeKey }
  const wasActive = activeKey === key
  const next = tabs.slice()
  next.splice(i, 1)
  if (!wasActive) return { tabs: next, activeKey }
  const after = next[i] || next[i - 1] || null
  return { tabs: next, activeKey: after ? after.key : null }
}

/**
 * 根据 activeKey 找到对应 tab（给 activeTab() 用）。无激活或找不到时返回 null。
 */
export function findActiveTab(tabs, activeKey) {
  if (!activeKey) return null
  return tabs.find(t => t.key === activeKey) || null
}