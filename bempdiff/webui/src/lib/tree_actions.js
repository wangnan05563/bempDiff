// 差异树右键菜单（参考 Beyond Compare）：菜单项定义 + 对象类型判定 + 可用性矩阵。
// 纯函数设计：itemState() 不依赖 Vue/store，vitest 可直接覆盖「文件/文件夹/基准文件夹/包模式」矩阵。
// 执行器（run）由 DiffTree.vue 实现（需访问 store/api/Electron 桥），此处只定义 判定与文案。

import { basenameOf } from './ignore'

/** 对象类型：baseFolder=已设为基准的文件夹 | folder=文件夹 | file=文件（含包内条目）。 */
export function nodeTypeOf(node, ctx = {}) {
  if (!node) return 'file'
  if (ctx.baseFolder && node.key === ctx.baseFolder) return 'baseFolder'
  if (node.fileClass === 'FOLDER') return 'folder'
  return 'file'
}

/**
 * 复制内容构建（纯函数，供「复制文件名/路径/相对路径」共用与测试）：
 *  - name：基名（最后一段）
 *  - relPath：包内/对比内相对路径（即 key）
 *  - absPath：文件夹模式=根目录+key 的物理路径；包模式无磁盘路径，回落为 key（前端提示"包内路径"）
 */
export function buildCopyText(node, ctx = {}) {
  const key = (node && node.key) || ''
  return {
    name: basenameOf(key),
    relPath: key,
    absPath: ctx.mode === 'folder' && ctx.rootPath ? joinPath(ctx.rootPath, key) : key
  }
}

function joinPath(root, key) {
  const r = String(root || '').replace(/[\\/]+$/, '')
  const k = String(key || '').replace(/^\/+/, '')
  return r + '/' + k
}

// 分组：file=文件操作 | manage=管理 | view=视图 | copy=复制 | info=信息 | ai=AI
export const MENU_ITEMS = [
  { id: 'open',        group: 'file',   label: '打开',                     icon: 'bi-box-arrow-up-right' },
  { id: 'reveal',      group: 'file',   label: '在文件资源管理器中显示',   icon: 'bi-folder-symlink' },
  { id: 'setBase',     group: 'manage', label: '设为基准文件夹',           icon: 'bi-pin-angle' },
  { id: 'rename',      group: 'manage', label: '重命名',                   icon: 'bi-pencil' },
  { id: 'delete',      group: 'manage', label: '删除',                     icon: 'bi-trash' },
  { id: 'copyFile',    group: 'manage', label: '复制文件（到另一侧）',     icon: 'bi-copy' },
  { id: 'exclude',     group: 'view',   label: '排除',                     icon: 'bi-eye-slash' },
  { id: 'ignore',      group: 'view',   label: '忽略',                     icon: 'bi-slash-circle' },
  { id: 'copyName',    group: 'copy',   label: '复制文件名',               icon: 'bi-file-earmark' },
  { id: 'copyPath',    group: 'copy',   label: '复制路径',                 icon: 'bi-link-45deg' },
  { id: 'copyRelPath', group: 'copy',   label: '复制相对路径',             icon: 'bi-subtract' },
  { id: 'props',       group: 'info',   label: '属性',                     icon: 'bi-info-circle' },
  { id: 'aiSummary',   group: 'ai',     label: 'AI功能总结',               icon: 'bi-stars' }
]

/** 不可用原因（title 悬浮提示文案）：null=可用；字符串=不可用原因。 */
export function disabledReason(id, node, ctx = {}) {
  const type = nodeTypeOf(node, ctx)
  const hasDisk = ctx.mode === 'folder'
  const shell = !!ctx.shell
  const isBase = type === 'baseFolder'
  switch (id) {
    case 'open':
      // 文件/文件夹：folder 模式=系统打开（需桌面壳）；包模式=打开内容比对（始终可用）
      return (hasDisk && !shell) ? '浏览器模式无法调用系统程序打开文件，请使用桌面壳（或点击行打开内容比对）' : null
    case 'reveal':
      if (!hasDisk) return '包内条目无磁盘路径，无法在资源管理器中显示'
      return shell ? null : '浏览器模式无法定位资源管理器，请使用桌面壳'
    case 'setBase':
      if (type === 'baseFolder') return null // 变为「取消设为基准」
      if (type !== 'folder') return '仅文件夹对象可设为基准文件夹'
      if (!hasDisk) return '包对比模式无文件夹对象'
      return null
    case 'rename':
      if (isBase) return '基准文件夹不可重命名'
      if (!hasDisk) return '包内条目无磁盘路径，无法重命名'
      return null
    case 'delete':
      if (isBase) return '基准文件夹不可删除'
      if (!hasDisk) return '包内条目无磁盘路径，无法删除'
      return null
    case 'copyFile':
      if (isBase) return '基准文件夹不可作为复制目标'
      if (!hasDisk) return '包内条目无磁盘路径，无法复制到另一侧'
      return null
    case 'props':
    case 'exclude':
    case 'ignore':
    case 'copyName':
    case 'copyPath':
    case 'copyRelPath':
      return null
    case 'aiSummary':
      // 文件夹无内容差异，后端单文件管线会直接拒绝——前置禁用避免用户走完闸门后才失败（评审 H2）
      if (type === 'folder' || type === 'baseFolder') return '文件夹无内容差异，请选择具体文件'
      return null
    default:
      return null
  }
}

/** 菜单项最终状态：{ disabled, title, label }（label 随对象类型变化，如 设为/取消 基准）。 */
export function itemState(item, node, ctx = {}) {
  const reason = disabledReason(item.id, node, ctx)
  let label = item.label
  if (item.id === 'setBase' && nodeTypeOf(node, ctx) === 'baseFolder') label = '取消设为基准文件夹'
  if (item.id === 'copyFile') {
    const t = nodeTypeOf(node, ctx)
    const status = (node && node.status) || ''
    if (t !== 'baseFolder' && status === 'ADDED') label = '复制文件（右 → 左）'
  }
  return { disabled: !!reason, title: reason || '', label }
}

/**
 * 过滤出对当前对象可见（非隐藏）的菜单项（含分组顺序）。
 * 返回扁平结构 { id, icon, group, label, disabled, title }：
 *   - id/icon/group 来自 MENU_ITEMS（ContextMenu 模板与 DiffTree.runAction 的 switch 都要用顶层 id）；
 *   - disabled/title/label 来自 itemState（随对象类型变化）。
 * 注意：此前实现返回嵌套 { item, ... } 导致菜单项顶层无 id，ContextMenu 点击 emit 的 item.id 为
 * undefined，DiffTree.runAction 的 switch(item.id) 全部落空 → 点击菜单项毫无反应（图标/分隔线也丢失）。
 */
export function menuItemsFor(node, ctx = {}) {
  return MENU_ITEMS.map(item => ({ ...item, ...itemState(item, node, ctx) }))
}
