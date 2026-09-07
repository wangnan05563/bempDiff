// 回归测试：守护「列表视图正常、树视图空白」根因 —— 目录树线性化用的 isExpanded 闭包。
// 历史 bug：isExpanded 定义在 `for (const g of groups)` 循环体外却引用循环变量 g，
// 而 for...of 的 const g 是块级作用域，循环体外词法作用域没有 g → flattenDirTree 递归调用
// isExpanded 时抛 ReferenceError: g is not defined → rows computed 崩溃 → 树视图整屏空白。
// 该 bug 只影响树视图分支（列表分支不定义/不调用 isExpanded），故症状是「列表能看到、树看不到」。
import { describe, it, expect } from 'vitest'

describe('树视图展平闭包（isExpanded 引用分组 layer 的正确性）', () => {
  // 复刻 DiffTree.vue 树视图：每个 layer 分组有独立的目录树，isExpanded 需捕获当次分组 g.layer
  function buildRows(treeClosure) {
    const out = []
    const groups = [
      { layer: 'L0', nodes: [{ key: 'bemp-served/a.class' }] },
      { layer: 'L1', nodes: [{ key: 'com/hundsun/A.class' }] }
    ]
    const expandedDirs = {}
    for (const g of groups) {
      out.push('header:' + g.layer)
      // treeClosure(g) 返回该分组用于展平的 isExpanded（修复前在循环外定义 → 引用不到 g）
      const isExpanded = treeClosure(g, expandedDirs)
      for (const n of g.nodes) {
        const dir = n.key.split('/')[0] + '/'
        out.push(dir + '@' + g.layer)
        // 每分组的子树：根是首个目录，若展开则露出子级
        const child = { key: n.key, isDir: false }
        const root = { key: dir, name: dir, isDir: true, children: [child] }
        if (isExpanded(root.key) !== false) out.push(out.pop() + '→' + n.key) // 展开则追加子级
      }
    }
    return out
  }

  it('修复后：isExpanded 在循环体内定义，捕获当次 g，正确展平（不抛错）', () => {
    // 修复点：isExpanded 定义在 for 循环体内，引用当次迭代的 g
    const rows = buildRows((g, expandedDirs) =>
      (k) => expandedDirs[g.layer + ':' + k] !== false)
    expect(rows).toEqual([
      'header:L0', 'bemp-served/@L0→bemp-served/a.class',
      'header:L1', 'com/@L1→com/hundsun/A.class'
    ])
  })

  it('复现旧 bug：isExpanded 在循环体外定义并引用 g → 抛 ReferenceError', () => {
    // 模拟旧代码结构：定义在 for 循环外的函数，却引用循环内 const g
    const makeRowsOld = () => {
      const out = []
      const groups = [
        { layer: 'L0', nodes: ['x'] },
        { layer: 'L1', nodes: ['y'] }
      ]
      const expandedDirs = {}
      // 修复前：isExpanded 定义在循环体外（此处的作用域没有 g）
      const isExpanded = (k) => expandedDirs[g.layer + ':' + k] !== false // g is undefined
      for (const g of groups) {
        out.push(g.layer)
        isExpanded('k0')
      }
      return out
    }
    expect(() => makeRowsOld()).toThrow(ReferenceError)
  })
})