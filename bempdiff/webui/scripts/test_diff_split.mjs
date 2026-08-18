// 回归测试：差异窗口「不换行（单行横向滚动）」分栏布局。
// 验证 DiffView 在 nowrap 模式下沉降为左右各 50% 分栏 + 中间分隔条 + 同步滚动，
// 且换行模式仍为单容器 4 列网格（逐行对齐）。
// 运行：在 webui/ 目录下 `node scripts/test_diff_split.mjs`
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { parse } from '@vue/compiler-sfc'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(ROOT, '../src')
const FILE = 'components/DiffView.vue'

function read(file) { return fs.readFileSync(path.join(SRC, file), 'utf8') }
function templateOf(file) {
  const { descriptor, errors } = parse(read(file), { filename: file })
  if (errors && errors.length) throw new Error(file + ' 解析错误: ' + errors.map(e => e.message).join('; '))
  if (!descriptor.template) throw new Error(file + ' 无 <template>')
  return descriptor.template.content
}
function styleOf(file) {
  const { descriptor } = parse(read(file), { filename: file })
  return descriptor.styles.map(s => s.content).join('\n')
}

let pass = 0, fail = 0
const check = (name, cond) => { if (cond) { pass++; console.log('  PASS:', name) } else { fail++; console.log('  FAIL:', name) } }

const src = read(FILE)
const tpl = templateOf(FILE)
const css = styleOf(FILE)

console.log('== 差异窗口 不换行分栏布局 结构验证 ==')

// 1) 换行模式保留单容器 4 列网格（diff-scroll + diff-grid）
check('换行模式：diff-scroll 滚动容器存在', /class="diff-scroll"[^>]*\s+v-if="wrap"/.test(tpl))
check('换行模式：diff-grid 4 列网格容器存在', /class="diff-grid"/.test(tpl))

// 2) 不换行模式：diff-split + 左右两栏 + 中间分隔条
check('不换行模式：diff-split 分支存在 (v-else)', /class="diff-split"[^>]*\s+v-else/.test(tpl))
check('不换行模式：左栏 diff-pane (leftPaneRef)', /class="diff-pane"[^>]*ref="leftPaneRef"/.test(tpl))
check('不换行模式：右栏 diff-pane right (rightPaneRef)', /class="diff-pane right"[^>]*ref="rightPaneRef"/.test(tpl))
check('不换行模式：中间分隔条 diff-splitter', /class="diff-splitter"/.test(tpl))

// 3) 同步滚动：onPaneScroll + 左右 ref 绑定 @scroll
check('同步滚动：onPaneScroll 函数定义', /function onPaneScroll\(side\)/.test(src))
check('同步滚动：左栏绑定 @scroll="onPaneScroll\(.left.\)"', /@scroll="onPaneScroll\('left'\)"/.test(tpl))
check('同步滚动：右栏绑定 @scroll="onPaneScroll\(.right.\)"', /@scroll="onPaneScroll\('right'\)"/.test(tpl))
check('同步滚动：镜像 scrollTop(纵向对齐) + scrollLeft(横向同步)', /dst\.scrollTop = src\.scrollTop/.test(src) && /dst\.scrollLeft = src\.scrollLeft/.test(src))

// 4) 左右内容对齐：两侧均渲染 displayRows（左用 leftSegs / 右用 rightSegs）
check('对齐：左栏渲染 r.leftSegs', /in r\.leftSegs/.test(tpl))
check('对齐：右栏渲染 r.rightSegs', /in r\.rightSegs/.test(tpl))

// 5) CSS：分栏 50/50 + 左栏纵向隐藏滚动条(由右栏同步) + prow 不换行
check('CSS：.diff-split 横向 flex 容器', /\.diff-split\s*\{[^}]*display:\s*flex/.test(css))
check('CSS：.diff-pane 各占一半宽度', /\.diff-pane\s*\{[^}]*flex:\s*1\s*1\s*50%/.test(css))
check('CSS：左栏 overflow-y:hidden (纵向由右栏同步)', /\.diff-pane\s*\{[^}]*overflow-y:\s*hidden/.test(css))
check('CSS：右栏 overflow-y:auto (主纵向滚动源)', /\.diff-pane\.right\s*\{[^}]*overflow-y:\s*auto/.test(css))
check('CSS：.prow 不换行 white-space:pre', /\.prow\s+\.code\s*\{[^}]*white-space:\s*pre/.test(css))

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail ? 1 : 0)
