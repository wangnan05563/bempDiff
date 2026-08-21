// 回归测试：破坏性/审计栏「生成报告」按钮缺陷（复现验证，不依赖 vite / npm 批量安装）。
// 用 @vue/compiler-sfc 官方解析器提取 <template>，避免脆弱正则（评审 E）。
// 运行：在 webui/ 目录下执行 `node scripts/test_report_button.mjs`（脚本用 fileURLToPath 定位，与 cwd 无关）。
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { parse } from '@vue/compiler-sfc'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(ROOT, '../src')

function read(file) {
  return fs.readFileSync(path.join(SRC, file), 'utf8')
}
// 用官方 SFC 解析器提取根级 <template> 内容，稳定可靠（评审 E：避免贪婪正则误判嵌套 template）。
function templateOf(file) {
  const { descriptor, errors } = parse(read(file), { filename: file })
  if (errors && errors.length) throw new Error(file + ' 解析错误: ' + errors.map(e => e.message).join('; '))
  if (!descriptor.template) throw new Error(file + ' 无 <template>')
  return descriptor.template.content
}

let pass = 0, fail = 0
const check = (name, cond) => { if (cond) { pass++; console.log('  PASS:', name) } else { fail++; console.log('  FAIL:', name) } }

console.log('== 复现验证：破坏性/审计栏 生成报告按钮修复 ==')
const infoSrc = read('components/InfoPanel.vue')
const infoTpl = templateOf('components/InfoPanel.vue')

// 1) 点击已绑定 onGenerateReport（修复核心：原缺陷是 :disabled 吞点击）。
//    前缀匹配 + 恰好 2 处带参调用（破坏性→breaking / 审计→risk，分析项已接入报告生成）。
check('T1 按钮 @click 绑定 onGenerateReport', infoTpl.includes('@click="onGenerateReport') && (infoTpl.match(/onGenerateReport\(/g) || []).length === 2)
// 2) 禁用绑定不再含 !state.job（原缺陷根因：未比对时按钮被禁用→点击无反应）
check('T2 禁用绑定不再含 !state.job', !infoTpl.includes('!state.job'))
// 3) 禁用绑定保留 busy||reporting（防重复触发，仍生效）
check('T3 禁用绑定为 state.busy || state.reporting', infoTpl.includes(':disabled="state.busy || state.reporting"'))
// 4) 两个生成报告按钮（破坏性 + 审计）均已统一修复（模板中各出现一次 onGenerateReport）
check('T5 两个 tab 的按钮均走 onGenerateReport', (infoTpl.match(/onGenerateReport/g) || []).length === 2)
// 8) AI 判定不再散落模板：模板中不应再出现 (state.config && state.config.aiEnabled)（已集中于 aiEnabled computed，评审 D）
check('T8 模板无散落 AI 判定表达式', !infoTpl.includes('(state.config && state.config.aiEnabled)'))
// 9) 两个按钮标签统一引用 aiLabelSuffix（评审 D）
check('T9 两个按钮标签引用 aiLabelSuffix', (infoTpl.match(/aiLabelSuffix/g) || []).length === 2)

const ovSrc = read('components/CompareOverlay.vue')
const ovTpl = templateOf('components/CompareOverlay.vue')
// 5) 报告生成遮罩含进度条 role=progressbar（满足期望：处理进度条）
check('T6 CompareOverlay 含 progressbar', ovTpl.includes('role="progressbar"'))
// 6) 非比对态(v-else)分支渲染不确定进度条（comparing 为 false 时走此分支）
check('T7 CompareOverlay 非比对态渲染进度条(v-else)', ovTpl.includes('v-else'))
// 10) comparing 判定显式排除报告态（评审 C）：脚本含 !state.reporting
check('T10 comparing 排除报告态 !state.reporting', ovSrc.includes('!state.reporting'))

console.log(`\nRESULT: PASS=${pass} FAIL=${fail} (total ${pass + fail})`)
process.exit(fail ? 1 : 0)
