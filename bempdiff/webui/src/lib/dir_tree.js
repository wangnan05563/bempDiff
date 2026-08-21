// 差异树构建工具：把扁平节点列表（key 为相对路径）转换为 VS Code / IntelliJ 风格的目录树。
// 纯函数，不依赖 Vue/store；供 DiffTree.vue 树视图 + vitest 单测使用。
//
// 规则（对齐主流 IDE 文件树展示方式）：
//  - 目录节点：key 以 '/' 结尾。folder 对比模式后端会下发显式 FOLDER 节点（携带状态，供右键操作
//    ——设为基准/删除/重命名等）；包模式无 FOLDER 节点，由文件 key 的路径段自动推导出纯容器目录。
//  - 文件节点：key 为完整相对路径，显示名 = 基名（末段），不再整行显示相对路径。
//  - 排序：目录优先于文件，同级按名称字典序；可传 fileCompare 覆盖文件间排序（如 AI 风险置顶）。
//  - 展开/折叠：flattenDirTree 按 isExpanded(dirKey) 决定是否深入子级，默认全展开。

/** key 按 '/' 切段（过滤空段，容错首尾斜杠）。 */
export function segsOf(key) {
  return String(key || '').split('/').filter(Boolean)
}

/** 文件/目录名 = 末段（目录 key 去尾斜杠后取末段）。 */
export function nameOf(key) {
  const s = segsOf(key)
  return s.length ? s[s.length - 1] : String(key || '')
}

/**
 * 构建目录树（已递归排序）。
 * @param nodes 过滤后的扁平节点数组（元素含 key；fileClass === 'FOLDER' 视为显式目录节点）
 * @param opts { fileCompare: (a,b)=>number } 可选：文件节点排序比较器（默认按名称字典序）
 * @returns {{ root: TreeNode[] }} root 为嵌套树数组，元素形如：
 *   { key, name, isDir:true,  node: FOLDER节点|null, children: TreeNode[] }
 *   { key, name, isDir:false, node: 文件节点,        children: null }
 */
export function buildDirTree(nodes, opts = {}) {
  const explicit = new Map() // dirKey(尾斜杠) -> FOLDER 节点
  const files = []
  for (const n of nodes || []) {
    if (!n || !n.key) continue
    if (n.fileClass === 'FOLDER') explicit.set(n.key, n)
    else files.push(n)
  }

  // 收集目录 key：显式 FOLDER 节点 + 文件 key 的全部路径前缀
  const dirKeys = new Set()
  for (const f of files) {
    const segs = segsOf(f.key)
    let p = ''
    for (let i = 0; i < segs.length - 1; i++) {
      p += segs[i] + '/'
      dirKeys.add(p)
    }
  }
  for (const k of explicit.keys()) dirKeys.add(k)

  const dirMap = new Map()
  for (const k of dirKeys) {
    const segs = segsOf(k)
    dirMap.set(k, { key: k, name: segs[segs.length - 1], isDir: true, node: explicit.get(k) || null, children: [] })
  }

  const parentKeyOf = (dirKey) => {
    const segs = segsOf(dirKey)
    return segs.length > 1 ? segs.slice(0, -1).join('/') + '/' : null
  }

  const root = []
  for (const [k, dir] of dirMap) {
    const pk = parentKeyOf(k)
    if (pk && dirMap.has(pk)) dirMap.get(pk).children.push(dir)
    else root.push(dir)
  }

  for (const f of files) {
    const segs = segsOf(f.key)
    const dirK = segs.length > 1 ? segs.slice(0, -1).join('/') + '/' : ''
    const entry = { key: f.key, name: segs[segs.length - 1], isDir: false, node: f, children: null }
    if (dirK && dirMap.has(dirK)) dirMap.get(dirK).children.push(entry)
    else root.push(entry) // 顶层文件（key 无目录段）
  }

  const fileCmp = opts.fileCompare || ((a, b) => (a.name || '').localeCompare(b.name || ''))
  const cmp = (a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1
    return fileCmp(a, b)
  }
  const sortRec = (list) => {
    list.sort(cmp)
    for (const d of list) if (d.isDir) sortRec(d.children)
  }
  sortRec(root)

  return { root }
}

/**
 * 按展开状态线性化目录树（供虚拟滚动）：isExpanded(dirKey) 返回 false 表示该目录折叠（跳过其子级）。
 * 每行 { key, name, isDir, depth, node }；文件行 key 即 node.key，目录行 key 以 '/' 结尾。
 */
export function flattenDirTree(root, isExpanded) {
  const out = []
  const walk = (list, depth) => {
    for (const e of list) {
      out.push({ key: e.key, name: e.name, isDir: e.isDir, depth, node: e.node, children: e.children })
      if (e.isDir && isExpanded(e.key) !== false) walk(e.children, depth + 1)
    }
  }
  walk(root || [], 0)
  return out
}

/**
 * 收集目录 key 按深度分层（供「全部展开/全部折叠」逐层交错动画）：
 * 返回 layers[depth] = [dirKey...]，layers[0] 为顶层目录、其后逐层加深。
 * 纯函数：基于节点数组构建一次目录树后按深度归类。
 */
export function dirLayersOf(nodes) {
  const { root } = buildDirTree(nodes)
  const layers = []
  const walk = (list, depth) => {
    for (const e of list) {
      if (!e.isDir) continue
      if (!layers[depth]) layers[depth] = []
      layers[depth].push(e.key)
      walk(e.children, depth + 1)
    }
  }
  walk(root, 0)
  return layers
}

/**
 * jar 包内条目分层（2026-08-21 新增，守护「展开 jar 后按内部目录层次缩进」）。
 * 后端 ArchiveTree.computeChildren 返回扁平条目：name=内部全路径（如 "META-INF/MANIFEST.MF"），
 * isDir 标记目录节点（如 "META-INF/"）。本函数按内部路径段数计算相对 jar 根的层级，
 * 并按折叠目录过滤其后代。
 * @param kids 后端扁平条目数组（元素含 key/name/isDir/status/fileClass）
 * @param foldedDirKeys 折叠目录的完整复合键集合（形如 "outer!/META-INF/"），其下文件隐藏
 * @returns [{ kind:'archiveDir'|'archiveChild', key, node, depth, name, innerPath }]
 *   depth 为相对 jar 根层级：目录 "META-INF/" → 1；"META-INF/maven/" → 2；文件 "META-INF/MANIFEST.MF" → 2。
 */
export function flattenArchiveChildren(kids, foldedDirKeys = new Set()) {
  const foldedPrefixes = []
  for (const c of kids || []) {
    if (c && c.isDir && foldedDirKeys.has(c.key)) {
      const fp = String(c.name != null ? c.name : c.key || '').replace(/\\/g, '/')
      if (fp) foldedPrefixes.push(fp)
    }
  }
  const out = []
  for (const c of kids || []) {
    if (!c || !c.key) continue
    const isDirNode = !!c.isDir
    const inner = String(c.name != null ? c.name : (c.key.split('!/').pop() || '')).replace(/\\/g, '/')
    if (!isDirNode) {
      let hidden = false
      for (const fp of foldedPrefixes) {
        if (inner.startsWith(fp) && inner.length > fp.length) { hidden = true; break }
      }
      if (hidden) continue
    }
    const segs = inner.split('/').filter(Boolean)
    out.push({
      kind: isDirNode ? 'archiveDir' : 'archiveChild',
      key: c.key,
      node: c,
      depth: segs.length, // 相对 jar 根层级 = 路径段数
      name: segs[segs.length - 1] || inner,
      innerPath: inner
    })
  }
  return out
}
