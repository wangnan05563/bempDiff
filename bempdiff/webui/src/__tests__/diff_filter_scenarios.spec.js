// 模拟用户过滤场景的回归测试：
// 场景 A: 4 个过滤全勾选（持久化 defaultConfig），应保留所有节点
// 场景 B: 「未变」未勾选，仅保留 MODIFIED/ADDED/DELETED，UNCHANGED 被过滤
// 场景 C: 4 个过滤全未勾选，按之前修复逻辑未知状态全过滤掉 → filtered 空
// 场景 D: 状态过滤不再对 FOLDER 特例放行：UNCHANGED 文件夹也按「未变」过滤（目录骨架由 buildDirTree 从文件路径前缀重建）
import { describe, it, expect } from 'vitest'

// 直接复制 DiffTree.vue 的过滤逻辑（最小化复现），避免引入 Vue 运行时
function makePasses(config, aiMap = {}, excluded = {}, ignoreRules = []) {
  const cfg = config || {}
  const passStatus = (n) => {
    const show = {
      MODIFIED: cfg.filterShowModified === true,
      ADDED: cfg.filterShowAdded === true,
      DELETED: cfg.filterShowDeleted === true,
      UNCHANGED: cfg.filterShowUnchanged === true
    }
    return show[n.status] === true
  }
  const passSearch = (n) => {
    const q = (cfg.filterSearch || '').trim()
    if (!q) return true
    if (cfg.filterRegex) return new RegExp(q, 'i').test(n.key)
    return n.key.toLowerCase().includes(q.toLowerCase())
  }
  const passRisk = (n) => {
    const rf = cfg.filterRisk
    if (!rf || !rf.length) return true
    if (!Object.keys(aiMap).length) return true
    const c = aiMap[n.key]
    return !!(c && rf.includes(c.risk))
  }
  const passView = (n) => {
    if (excluded[n.key]) return false
    return true // 简化：忽略 ignoreRules
  }
  return (n) => passStatus(n) && passSearch(n) && passRisk(n) && passView(n)
}

describe('过滤场景回归', () => {
  const sampleTree = [
    { key: 'bemp-served/WEB-INF/lib/a.jar/com/x/A.class', status: 'MODIFIED', fileClass: 'CLASS' },
    { key: 'bemp-served/WEB-INF/lib/a.jar/com/x/B.class', status: 'MODIFIED', fileClass: 'CLASS' },
    { key: 'bemp-served/WEB-INF/lib/a.jar/META-INF/MANIFEST.MF', status: 'UNCHANGED', fileClass: 'CONFIG' },
    { key: 'bemp-served/WEB-INF/web.xml', status: 'UNCHANGED', fileClass: 'CONFIG' },
    { key: 'bemp-served/index.jsp', status: 'UNCHANGED', fileClass: 'JSP' },
    { key: 'bemp-served/login/Logo.png', status: 'UNCHANGED', fileClass: 'STATIC' }
  ]

  it('场景 A: 4 个过滤全勾选 → 保留所有节点', () => {
    const cfg = { filterShowModified: true, filterShowAdded: true, filterShowDeleted: true, filterShowUnchanged: true, filterRisk: ['HIGH', 'MEDIUM', 'LOW'] }
    const pass = makePasses(cfg)
    const got = sampleTree.filter(pass)
    expect(got.length).toBe(6)
  })

  it('场景 B: 「未变」未勾选 → 仅保留 MODIFIED（用户截图场景）', () => {
    const cfg = { filterShowModified: true, filterShowAdded: true, filterShowDeleted: true, filterShowUnchanged: false, filterRisk: ['HIGH', 'MEDIUM', 'LOW'] }
    const pass = makePasses(cfg)
    const got = sampleTree.filter(pass)
    expect(got.length).toBe(2) // 仅 2 个 MODIFIED
    expect(got.every(n => n.status === 'MODIFIED')).toBe(true)
  })

  it('场景 C: 4 个过滤全未勾选 → FOLDER 也按状态过滤，全部过滤（filtered 空）', () => {
    const cfg = { filterShowModified: false, filterShowAdded: false, filterShowDeleted: false, filterShowUnchanged: false, filterRisk: ['HIGH', 'MEDIUM', 'LOW'] }
    const treeWithFolder = [...sampleTree, { key: 'bemp-served/', status: 'UNCHANGED', fileClass: 'FOLDER' }]
    const pass = makePasses(cfg)
    const got = treeWithFolder.filter(pass)
    // 文件节点与 FOLDER 节点（UNCHANGED）全被过滤——纯无差异目录不再残留
    expect(got.length).toBe(0)
  })

  it('场景 D: folder 模式下 UNCHANGED 的 FOLDER 也被状态过滤，仅 MODIFIED 文件通过', () => {
    const cfg = { filterShowModified: true, filterShowAdded: false, filterShowDeleted: false, filterShowUnchanged: false, filterRisk: ['HIGH', 'MEDIUM', 'LOW'] }
    const treeWithFolder = [
      { key: 'src/', status: 'UNCHANGED', fileClass: 'FOLDER' },
      { key: 'src/main/', status: 'UNCHANGED', fileClass: 'FOLDER' },
      { key: 'src/main/A.java', status: 'MODIFIED', fileClass: 'CLASS' },
      { key: 'src/main/B.java', status: 'UNCHANGED', fileClass: 'CLASS' }
    ]
    const pass = makePasses(cfg)
    const got = treeWithFolder.filter(pass)
    // UNCHANGED 的目录与文件都被过滤；仅 MODIFIED 文件通过（目录骨架由 buildDirTree 从该文件路径前缀重建，见 dir_tree 测试）
    expect(got.map(n => n.key)).toEqual(['src/main/A.java'])
  })

  it('场景 E: filterRisk 只勾选 HIGH + aiClassify 已存在 → 仅 HIGH 节点通过', () => {
    const cfg = { filterShowModified: true, filterShowAdded: true, filterShowDeleted: true, filterShowUnchanged: true, filterRisk: ['HIGH'] }
    const aiMap = {
      'src/A.java': { risk: 'HIGH', category: '业务' },
      'src/B.java': { risk: 'LOW', category: '业务' }
    }
    const tree = [
      { key: 'src/A.java', status: 'MODIFIED', fileClass: 'CLASS' },
      { key: 'src/B.java', status: 'MODIFIED', fileClass: 'CLASS' }
    ]
    const pass = makePasses(cfg, aiMap)
    const got = tree.filter(pass)
    // A 通过（HIGH 命中），B 被过滤（LOW 不在 filterRisk 中）
    expect(got.map(n => n.key)).toEqual(['src/A.java'])
  })
})
