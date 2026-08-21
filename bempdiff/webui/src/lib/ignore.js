// 忽略规则引擎（右键菜单「忽略」）：纯函数，便于单测。
// 规则类型：
//   exact  - 精确匹配完整 key（如 "com/foo/Bar.class"）
//   prefix - 匹配 key 前缀（如 "node_modules/" 忽略整目录）
//   name   - 匹配文件名（基名，如 "Thumbs.db"）
//   ext    - 匹配扩展名（如 ".log" 忽略所有日志）
// 规则持久化到 localStorage（key: 'bempdiff-ignore-rules'），与后端无关。

export const IGNORE_TYPES = [
  { key: 'exact',  label: '精确路径' },
  { key: 'prefix', label: '目录前缀' },
  { key: 'name',   label: '文件名' },
  { key: 'ext',    label: '扩展名' }
]

export function basenameOf(key) {
  // 目录 key 以 '/' 结尾（如 "WEB-INF/lib/"）→ 先去尾斜杠再取末段
  const k = String(key || '').replace(/\/+$/, '')
  const i = k.lastIndexOf('/')
  return i >= 0 ? k.substring(i + 1) : k
}

/** 把 key 转为可读的规则值（忽略时默认按当前形态推荐）。 */
export function defaultRuleFor(key, type = 'name') {
  if (type === 'ext') {
    const n = basenameOf(key)
    const i = n.lastIndexOf('.')
    return i > 0 ? n.substring(i) : ''
  }
  if (type === 'name') return basenameOf(key)
  if (type === 'prefix') return key.endsWith('/') ? key : key.substring(0, key.lastIndexOf('/') + 1)
  return key
}

/** 判断 key 是否命中任意一条忽略规则。 */
export function isIgnored(key, rules) {
  if (!rules || !rules.length) return false
  for (const r of rules) {
    if (matchRule(key, r)) return true
  }
  return false
}

/** 单条规则匹配判定。 */
export function matchRule(key, rule) {
  if (!rule || !rule.value) return false
  const v = String(rule.value)
  switch (rule.type) {
    case 'exact': return key === v
    case 'prefix': return key.startsWith(v)
    case 'name': return basenameOf(key) === v
    case 'ext': return basenameOf(key).toLowerCase().endsWith(v.toLowerCase())
    default: return false
  }
}

const LS_KEY = 'bempdiff-ignore-rules'

export function loadIgnoreRules() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.filter(r => r && r.type && r.value) : []
  } catch (_) {
    return []
  }
}

export function saveIgnoreRules(rules) {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(rules))
  } catch (_) { /* 持久化失败不阻塞功能 */ }
}

/** 添加规则（同类型同值去重），返回新数组。 */
export function addRule(rules, type, value) {
  const v = String(value || '').trim()
  if (!v) return rules
  if (rules.some(r => r.type === type && r.value === v)) return rules
  return [...rules, { type, value: v }]
}

/** 删除规则（按下标），返回新数组。 */
export function removeRule(rules, index) {
  if (index < 0 || index >= rules.length) return rules
  return rules.filter((_, i) => i !== index)
}
