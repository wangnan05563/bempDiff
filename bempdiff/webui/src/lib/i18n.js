/**
 * i18n 轻量框架（二期 R12 / T01477）——中英双语，术语首次附英文对照。
 *
 * 设计要点：
 * - 零依赖 reactive：i18n.locale 变更即驱动所有引用 t() 的组件重渲染；
 * - 字典按 key 组织（zh-CN 为基准字典），en-US 缺失 key 自动回退中文——支持渐进迁移：
 *   组件接入时只需在两个字典里补同 key 条目（约定见 extractGuide）；
 * - 术语对照：TERMS 提供「差异树 Diff Tree」式对照（术语首次出现处使用），
 *   帮助文档「术语中英对照」条目为权威清单（helpContent.js）；
 * - 语言偏好持久化 localStorage（bempdiff-lang），默认 zh-CN。
 */
import { reactive } from 'vue'

export const LANG_KEY = 'bempdiff-lang'
export const LOCALES = [
  { key: 'zh-CN', label: '中文' },
  { key: 'en-US', label: 'English' }
]

const DICT = {
  'zh-CN': {
    'app.compare': '开始比对',
    'app.cancelCompare': '取消比对',
    'app.processing': '处理中…',
    'phase.parsing': '正在解析包…',
    'phase.unpacking': '正在自动迭代解包…',
    'phase.diffing': '正在比对 / 计算差异…',
    'phase.building': '正在构建差异树…',
    'phase.default': '比对进行中…',
    'phase.timeout': '比对已进行超过 5 分钟仍未完成。可继续等待；若长时间无进展，请检查磁盘/网络后重启工具。',
    'phase.aiWait': '比对完成前，智能分类 / AI 分析需等待完成后再进行（此刻触发会提示「比对尚未完成: RUNNING」）',
    'status.version': '版本',
    'status.decompiling': '正在反编译/美化源码…',
    'status.parsingDiff': '正在解析差异…',
    'overlay.checking': '正在检查更新…',
    'step.unpack': '解包',
    'step.diff': '比对',
    'step.build': '出树',
    'tree.filterPlaceholder': '搜索文件名（模糊）',
    'tree.filterPlaceholderRegex': '搜索文件名（正则）',
    'ai.thinking': '正在准备分析…',
    'ai.generating': 'AI 正在生成…',
    'ai.done': '分析完成',
    'ai.aborted': '已中断',
    'ai.failed': '失败',
    'term.diffTree': '差异树',
    'term.compare': '比对',
    'term.decompile': '反编译',
    'term.smartClassify': '智能分类',
    'term.aiAnalysis': '智能分析',
    'term.report': '报告',
    'term.export': '导出',
    'term.layer': '分层'
  },
  'en-US': {
    'app.compare': 'Start Compare',
    'app.cancelCompare': 'Cancel Compare',
    'app.processing': 'Working…',
    'phase.parsing': 'Parsing packages…',
    'phase.unpacking': 'Unpacking nested archives…',
    'phase.diffing': 'Comparing / computing differences…',
    'phase.building': 'Building diff tree…',
    'phase.default': 'Compare in progress…',
    'phase.timeout': 'Compare has been running for over 5 minutes. You may keep waiting; if it stalls, check disk/network and restart.',
    'phase.aiWait': 'AI classify / analysis must wait until the compare finishes (triggering now returns "compare still running: RUNNING")',
    'status.version': 'Version',
    'status.decompiling': 'Decompiling / prettifying sources…',
    'status.parsingDiff': 'Parsing differences…',
    'overlay.checking': 'Checking for updates…',
    'step.unpack': 'Unpack',
    'step.diff': 'Compare',
    'step.build': 'Tree',
    'tree.filterPlaceholder': 'Search file names (fuzzy)',
    'tree.filterPlaceholderRegex': 'Search file names (regex)',
    'ai.thinking': 'Preparing analysis…',
    'ai.generating': 'AI is generating…',
    'ai.done': 'Analysis complete',
    'ai.aborted': 'Aborted',
    'ai.failed': 'Failed',
    'term.diffTree': 'Diff Tree',
    'term.compare': 'Compare',
    'term.decompile': 'Decompile',
    'term.smartClassify': 'Smart Classification',
    'term.aiAnalysis': 'AI Analysis',
    'term.report': 'Report',
    'term.export': 'Export',
    'term.layer': 'Layer'
  }
}

function initialLocale() {
  try {
    const v = localStorage.getItem(LANG_KEY)
    if (v === 'zh-CN' || v === 'en-US') return v
  } catch (_) { /* 静默 */ }
  return 'zh-CN'
}

export const i18n = reactive({ locale: initialLocale() })

/** 翻译：缺失 key 回退中文，再回退 key 本身；支持 {name} 插值。 */
export function t(key, params) {
  let s = (DICT[i18n.locale] && DICT[i18n.locale][key]) || DICT['zh-CN'][key] || key
  if (params) {
    for (const k of Object.keys(params)) s = s.split('{' + k + '}').join(String(params[k]))
  }
  return s
}

/** 切换语言并持久化（html[lang] 同步，供读屏）。 */
export function setLocale(locale) {
  if (!DICT[locale]) return
  i18n.locale = locale
  try {
    localStorage.setItem(LANG_KEY, locale)
    document.documentElement.setAttribute('lang', locale)
  } catch (_) { /* 静默 */ }
}

/** 术语对照（术语「首次出现」处使用）：中文语境附英文，英文语境直接英文。 */
export function tTerm(key) {
  const en = DICT['en-US'][key] || key
  if (i18n.locale === 'en-US') return en
  const zh = DICT['zh-CN'][key] || key
  return zh === en ? zh : `${zh} (${en})`
}

/** 术语中英对照表（帮助文档「术语对照」条目数据源，权威清单）。 */
export const TERM_TABLE = [
  ['差异树', 'Diff Tree', '按目录层级组织差异文件的导航视图'],
  ['比对', 'Compare', '加载两个包/目录并计算逐文件差异的全过程'],
  ['反编译', 'Decompile', '把 .class 字节码还原为可读 Java 源码'],
  ['智能分类', 'Smart Classification', 'AI 自动对差异文件打标分类并评估风险等级'],
  ['智能分析', 'AI Analysis', '基于差异内容的 AI 解读（风险/影响/测试要点等）'],
  ['报告', 'Report', '差异与 AI 分析结果的汇总文档（Markdown/HTML）'],
  ['导出', 'Export', '把差异资产（源码/Jar）打包下载'],
  ['分层', 'Layer', 'L0 顶层包 / L1 归档内 / L2 嵌套归档内的层级标注']
]
