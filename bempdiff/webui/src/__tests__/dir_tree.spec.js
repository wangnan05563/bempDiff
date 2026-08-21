// 目录树构建工具（dir_tree.js）测试。
// 守护需求：差异文件树按目录层级递归展开（VS Code/IntelliJ 风格）——
// 目录节点由文件 key 路径段推导（或 FOLDER 显式节点）、节点仅显示当前层级名称、
// 目录优先于文件排序、按展开状态线性化供虚拟滚动。
import { describe, it, expect } from 'vitest'
import { segsOf, nameOf, buildDirTree, flattenDirTree, dirLayersOf, flattenArchiveChildren } from '../lib/dir_tree'

const n = (key, extra = {}) => ({ key, status: 'MODIFIED', fileClass: 'CLASS', ...extra })

describe('segsOf / nameOf 分段与命名', () => {
  it('segsOf 按 / 切段并过滤空段', () => {
    expect(segsOf('com/x/A.class')).toEqual(['com', 'x', 'A.class'])
    expect(segsOf('/a/b/')).toEqual(['a', 'b'])
    expect(segsOf('')).toEqual([])
    expect(segsOf(null)).toEqual([])
  })
  it('nameOf 取末段（目录 key 去尾斜杠）', () => {
    expect(nameOf('com/x/A.class')).toBe('A.class')
    expect(nameOf('WEB-INF/lib/')).toBe('lib')
    expect(nameOf('')).toBe('')
  })
})

describe('buildDirTree 目录层级推导', () => {
  it('由文件 key 推导目录层级并挂载文件（目录优先 + 名称排序）', () => {
    const { root } = buildDirTree([
      n('com/x/A.class'),
      n('com/x/B.class'),
      n('com/y/C.class'),
      n('top-level.js', { fileClass: 'JS' })
    ])
    expect(root.length).toBe(2)
    expect(root[0].isDir).toBe(true)
    expect(root[0].name).toBe('com')
    expect(root[0].key).toBe('com/')
    expect(root[0].children.map(c => c.name)).toEqual(['x', 'y'])
    expect(root[0].children[0].children.map(c => c.name)).toEqual(['A.class', 'B.class'])
    expect(root[0].children[1].children.map(c => c.name)).toEqual(['C.class'])
    expect(root[1].isDir).toBe(false)
    expect(root[1].name).toBe('top-level.js')
    expect(root[1].node.fileClass).toBe('JS')
  })

  it('FOLDER 显式节点合并为目录节点（携带 node 供右键操作）', () => {
    const { root } = buildDirTree([
      n('WEB-INF/lib/', { fileClass: 'FOLDER', status: 'UNCHANGED' }),
      n('WEB-INF/lib/foo.jar', { fileClass: 'JAR' }),
      n('WEB-INF/web.xml', { fileClass: 'CONFIG' })
    ])
    const webinf = root.find(r => r.name === 'WEB-INF')
    expect(webinf).toBeTruthy()
    expect(webinf.key).toBe('WEB-INF/')
    const lib = webinf.children.find(c => c.name === 'lib')
    expect(lib.isDir).toBe(true)
    expect(lib.node).toBeTruthy() // FOLDER 节点挂载在推导目录上
    expect(lib.node.fileClass).toBe('FOLDER')
    expect(lib.children.map(c => c.name)).toEqual(['foo.jar'])
  })

  it('目录优先于文件、同级按名称排序', () => {
    const { root } = buildDirTree([
      n('z.txt', { fileClass: 'CONFIG' }),
      n('a/b.txt', { fileClass: 'CONFIG' }),
      n('m.txt', { fileClass: 'CONFIG' })
    ])
    expect(root.map(r => r.name)).toEqual(['a', 'm.txt', 'z.txt'])
  })

  it('fileCompare 覆盖文件间排序（如 AI 风险置顶）', () => {
    const riskRank = { 'a/Low.java': 1, 'a/High.java': 3 }
    const { root } = buildDirTree([
      n('a/Low.java'),
      n('a/High.java')
    ], {
      fileCompare: (x, y) => (riskRank[y.key] || 0) - (riskRank[x.key] || 0) || x.name.localeCompare(y.name)
    })
    expect(root[0].children.map(c => c.name)).toEqual(['High.java', 'Low.java'])
  })

  it('空输入 → 空树', () => {
    expect(buildDirTree([]).root).toEqual([])
    expect(buildDirTree(null).root).toEqual([])
  })
})

describe('flattenDirTree 线性化与展开/折叠', () => {
  const { root } = buildDirTree([
    n('com/x/A.class'),
    n('com/x/B.class'),
    n('com/y/C.class')
  ])

  it('默认全展开：深度优先线性化，depth 正确', () => {
    const flat = flattenDirTree(root, () => true)
    expect(flat.map(r => r.key)).toEqual([
      'com/', 'com/x/', 'com/x/A.class', 'com/x/B.class', 'com/y/', 'com/y/C.class'
    ])
    expect(flat.map(r => r.depth)).toEqual([0, 1, 2, 2, 1, 2])
  })

  it('折叠根目录：仅保留目录行本身', () => {
    const flat = flattenDirTree(root, (k) => k !== 'com/')
    expect(flat.map(r => r.key)).toEqual(['com/'])
  })

  it('部分折叠：折叠 x/ 保留 y/ 及其文件', () => {
    const flat = flattenDirTree(root, (k) => k !== 'com/x/')
    expect(flat.map(r => r.key)).toEqual(['com/', 'com/x/', 'com/y/', 'com/y/C.class'])
  })

  it('null 根 → 空数组', () => {
    expect(flattenDirTree(null, () => true)).toEqual([])
  })
})

describe('dirLayersOf 目录深度分层（供「全部展开/全部折叠」逐层动画）', () => {
  it('按深度分层：layers[0]=顶层目录，逐层加深', () => {
    const layers = dirLayersOf([
      n('com/x/A.class'),
      n('com/y/C.class'),
      n('static/js/app.js', { fileClass: 'JS' })
    ])
    expect(layers[0]).toEqual(['com/', 'static/'])
    expect(layers[1]).toEqual(['com/x/', 'com/y/', 'static/js/'])
    expect(layers.length).toBe(2) // 无更深目录，不产生空洞层
  })

  it('深层目录正确分层（a/b/c）', () => {
    const layers = dirLayersOf([
      n('a/b/c/D.class'),
      n('a/b/E.class'),
      n('a/F.class')
    ])
    expect(layers[0]).toEqual(['a/'])
    expect(layers[1]).toEqual(['a/b/'])
    expect(layers[2]).toEqual(['a/b/c/'])
    expect(layers.length).toBe(3)
  })

  it('无目录（纯顶层文件）或空输入 → 空分层', () => {
    expect(dirLayersOf([n('top.txt', { fileClass: 'CONFIG' })])).toEqual([])
    expect(dirLayersOf([])).toEqual([])
    expect(dirLayersOf(null)).toEqual([])
  })
})

describe('flattenArchiveChildren jar 包内条目分层（2026-08-21 守护「展开 jar 按目录层次缩进」）', () => {
  // 模拟后端 ArchiveTree.computeChildren 返回：name=内部全路径，isDir 标记目录
  const dirNode = (name, key = 'lib/x.jar!/' + name) => ({ key, name, isDir: true, status: 'UNCHANGED', fileClass: 'OTHER' })
  const fileNode = (name, key = 'lib/x.jar!/' + name, fc = 'CONFIG') => ({ key, name, isDir: false, status: 'MODIFIED', fileClass: fc })

  it('按内部路径段数计算层级：目录=段数，文件=段数（比父目录深一级）', () => {
    const kids = [
      dirNode('META-INF/'),
      fileNode('META-INF/MANIFEST.MF'),
      dirNode('META-INF/maven/'),
      fileNode('META-INF/maven/pom.xml'),
      fileNode('properties/version.properties'),
      fileNode('top.txt')
    ]
    const flat = flattenArchiveChildren(kids)
    const byName = Object.fromEntries(flat.map(r => [r.name, r]))
    expect(flat.length).toBe(6)
    expect(byName['META-INF'].kind).toBe('archiveDir')
    expect(byName['META-INF'].depth).toBe(1)
    expect(byName['MANIFEST.MF'].kind).toBe('archiveChild')
    expect(byName['MANIFEST.MF'].depth).toBe(2)
    expect(byName['maven'].kind).toBe('archiveDir')
    expect(byName['maven'].depth).toBe(2)
    expect(byName['pom.xml'].depth).toBe(3)
    expect(byName['version.properties'].depth).toBe(2)
    expect(byName['top.txt'].depth).toBe(1)
  })

  it('折叠目录：其后代文件隐藏、目录自身保留', () => {
    const kids = [
      dirNode('META-INF/'),
      fileNode('META-INF/MANIFEST.MF'),
      fileNode('META-INF/maven/pom.xml'),
      fileNode('properties/version.properties')
    ]
    const folded = flattenArchiveChildren(kids, new Set(['lib/x.jar!/META-INF/']))
    expect(folded.map(r => r.name)).toEqual(['META-INF', 'version.properties'])
  })

  it('未折叠目录：全部展示；空/缺 key 输入容错', () => {
    const kids = [
      dirNode('a/'),
      fileNode('a/b.txt'),
      { name: 'no-key.txt', isDir: false }, // 缺 key → 跳过
      null
    ]
    const flat = flattenArchiveChildren(kids)
    expect(flat.map(r => r.name)).toEqual(['a', 'b.txt'])
    expect(flattenArchiveChildren([])).toEqual([])
    expect(flattenArchiveChildren(null)).toEqual([])
  })
})
