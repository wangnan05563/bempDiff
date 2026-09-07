// 帮助文档内容数据模型（设置页「帮助文档」tab 的数据源）。
//
// 设计目标：
//  - 层级化结构：功能分类(category) → 说明条目(entry) → 结构化内容块(block)。
//  - 纯数据 + 纯函数，便于单测（结构校验、检索、版本同步全部可离线断言）。
//  - 内容随版本维护：DOC_VERSION 记录「本文档对应内容版本」，与产品 APP_VERSION
//    通过 contentMatchesVersion() 判定是否滞后，避免文档与产品功能脱节。
//
// 维护约定（修改本文档时注意）：
//  - 新增/调整功能后：新增条目或更新条目内容，并在需要时递增 DOC_VERSION，
//    使「帮助内容版本」与「产品版本」的对照保持可信。
//  - entry.id 全局唯一且稳定（作为导航/检索/测试锚点），不要随意改名。

/** 产品名称（展示用）。 */
export const PRODUCT_NAME = 'BempDiff'

/** 产品版本（Web UI 版本；桌面壳如暴露 appVersion 则运行时优先取壳层版本）。 */
export const APP_VERSION = '0.1.0'

/** 帮助内容版本：与 APP_VERSION 保持同套主·次版本口径（内容随产品演进同步递增），
 *  仅当主版本不一致时提示「内容可能滞后」。 */
export const DOC_VERSION = '0.1'

/**
 * 运行时解析「当前产品版本」：优先取桌面壳暴露的 appVersion（版本最贴近安装包），
 * 否则回落到内置 APP_VERSION。这样即使壳层暂未暴露版本号，也能给出可用的版本标注。
 */
export function resolveAppVersion() {
  try {
    const v = (typeof window !== 'undefined') && window.bempdiff && window.bempdiff.appVersion
    if (v) return String(v)
  } catch (_) { /* 环境无 window（SSR/测例）时忽略，回落内置常量 */ }
  return APP_VERSION
}

/** 内容块 type 白名单（见 buildHelp 校验）。 */
export const BLOCK_TYPES = ['para', 'list', 'steps', 'note', 'warn']

/**
 * 判定「帮助内容是否与当前产品版本同步」。
 * 用「主.次」两段版本号相同作为同步标准（0.1.0 与 0.1 视为同步；0.1 与 0.2 视为滞后提示更新）。
 * 取版本号前两段：避免 patch 级频繁递增造成误报，同时次版本不对齐时能及早提醒文档滞后。
 * 纯逻辑，测例直接断言。
 */
function versionBase2(v) {
  const m = String(v || '').match(/\d+(?:\.\d+)?/)
  return m ? m[0] : String(v || '')
}
export function contentMatchesVersion(docVersion = DOC_VERSION, appVersion = resolveAppVersion()) {
  return versionBase2(docVersion) === versionBase2(appVersion)
}

// ---------------------------------------------------------------------------
// 内容块辅助构造（让书写更紧凑、可读）
// ---------------------------------------------------------------------------
const para = (text) => ({ type: 'para', text })
const list = (items) => ({ type: 'list', items })
const steps = (items) => ({ type: 'steps', items })
const note = (text) => ({ type: 'note', text })
const warn = (text) => ({ type: 'warn', text })

/**
 * 文档条目（entry）：一个可独立导航/检索的最小主题单元。
 * id 全局唯一；title 为导航与标题；intro 为一段导语；blocks 为正文结构块；
 * keywords 用于增强检索（用户习惯用词，可不完全出现在正文）。
 */
function entry(o) {
  return {
    id: o.id,
    title: o.title,
    intro: o.intro || '',
    blocks: o.blocks || [],
    keywords: o.keywords || []
  }
}

// ---------------------------------------------------------------------------
// 内容：功能分类 → 条目
// ---------------------------------------------------------------------------
export const CATEGORIES = [
  {
    key: 'basics',
    title: '基础功能',
    icon: 'bi-stack',
    entries: [
      entry({
        id: 'compare',
        title: '如何开始一次对比',
        intro: '选择「老包」与「新包」即可开始比对。支持文件夹、WAR、JAR、ZIP、EAR 等常见格式，一次比对得到文件级差异总览。',
        keywords: ['开始对比', '导入', '选择', 'war', 'jar', 'zip', '文件夹'],
        blocks: [
          para('老包与新包既可以是两个压缩包/文件夹，也可以是同一个文件夹的两次快照。'),
          steps([
            '在左侧「老包路径」/「新包路径」填入两个待比对的路径（也可直接拖入或通过右键菜单选择）。',
            '若有同名不同版本的包（如 app-1.0.zip 与 app-2.0.zip），系统会自动按版本排序，无需手动区分新旧。',
            '点击「开始对比」，等待进度完成后即可查看差异统计与差异文件树。',
          ]),
          note('路径既支持压缩包也支持文件夹；比对文件夹时，内部嵌套的 ZIP/WAR/JAR 也会被自动逐层解包（见「自动逐层解包」）。'),
          warn('请确认两个路径中的内容确实是「旧版→新版」关系，顺序颠倒会导致新增/删除判定颠倒。'),
        ]
      }),
      entry({
        id: 'drop',
        title: '用拖拽快速填入老包/新包',
        intro: '把文件直接拖进应用窗口，即可自动交替填入老包与新包路径，无需逐个手动选择。',
        keywords: ['拖拽', '拖放', '拖入', '自动填入', '循环', '遮罩'],
        blocks: [
          para('桌面壳下把任意压缩包或文件夹直接拖进窗口即可，系统会按顺序循环填入老包/新包路径。'),
          list([
            '第一次拖入 → 自动填入「老包路径」。',
            '第二次拖入 → 自动填入「新包路径」。',
            '第三次及以后 → 循环交替：奇数次填老包、偶数次填新包。',
          ]),
          para('拖拽过程中会弹出全屏遮罩，中央提示本次应填入的类型（「请拖入老包 / 请拖入新包」）；松开鼠标即完成填充，遮罩随之关闭、恢复页面展示。'),
          steps([
            '把旧版包拖进窗口，松开后自动填入老包路径。',
            '再把新版包拖进窗口，松开后自动填入新包路径并开始对比。',
            '要比较新的一对时：第三次拖入回到老包、第四次填新包，依此类推。',
          ]),
          note('当两个路径齐备时系统会自动开始对比；若读取不到拖入文件的路径（浏览器模式），会提示改用桌面壳或手动填写。'),
        ]
      }),
      entry({
        id: 'tree',
        title: '差异文件树与视图',
        intro: '差异树以层级方式展示所有变化文件，支持按状态过滤、搜索、切换视图，并可通过右键快速处理条目。',
        keywords: ['差异树', '过滤', '搜索', '树视图', '列表视图', '忽略'],
        blocks: [
          para('差异树把结果按「修改 / 新增 / 删除 / 未变」归类。'),
          list([
            '顶部可勾选状态过滤：只显示「修改」「新增」「删除」等，默认隐藏「未变」可显著缩短列表。',
            '搜索框支持模糊匹配，也可切换为正则模式做精确匹配。',
            '视图可在「树结构」与「列表」两种方式间切换。',
            '右键条目可执行：打开对比、排除（临时隐藏）、加入忽略规则、查看属性、重命名/复制/删除（文件夹模式）等。',
          ]),
          note('「排除」仅当前会话临时隐藏，可在树底部「恢复」；「忽略」会写入规则并持久化生效。'),
        ]
      }),
      entry({
        id: 'diff',
        title: '内容比对（双栏差异）',
        intro: '点击差异树中的任一文件，即可在中间区域打开左右双栏对比，逐行查看两种版本的内容差异。',
        keywords: ['diff', '对比', '内容', '双栏', '源码', '高亮'],
        blocks: [
          para('支持源码类（Java/JS/HTML/CSS 等）、文本类、Office 文档（Word/Excel/PPT）与字节码（自动反编译）等多种文件的差异查看。'),
          steps([
            '在差异树中点击一个「修改」或「新增 / 删除」文件。',
            '中间双栏将分别展示老版 / 新版内容，差异行会高亮标识。',
            '可使用展开/折叠与行内对比功能聚焦到具体变化；Office 文档会先解析为内容再做逐行对比。',
          ]),
          note('「未变」文件也可打开查看，适合确认无实质变化的内容。'),
        ]
      }),
      entry({
        id: 'export',
        title: '导出差异资产',
        intro: '把本次差异以压缩包形式导出，便于交付或归档。导出包内按「增量」与「删除」分类组织。',
        keywords: ['导出', '差异资产', '增量', '删除', 'zip'],
        blocks: [
            para('导出按规模自动分流：小包同步导出（实时显示进度），大包异步后台生成（提示“导出已启动，预计X分钟完成”），完成后到「下载管理」页下载。导出包内按「增量」与「删除」分类组织。'),
            list([
              'increment/：新增与修改的文件（需要带走的增量）。',
              'deleted/：被删除的文件（便于留存对照/审计）。',
            ]),
            steps([
              '完成一次对比后，点击工具栏「导出差异资产」。',
              '若正在自动逐层解包比对，需等待其完成后才能导出（导出会自动等待）。',
              '小包会直接下载；大包在「下载管理」页选择本次任务后点击「下载」获取。',
            ]),
          ]
      }),
    ]
  },
  {
    key: 'advanced',
    title: '高级功能',
    icon: 'bi-stars',
    entries: [
      entry({
        id: 'unpack',
        title: '自动逐层解包',
        intro: '对比嵌套包（ZIP/WAR/JAR 层层嵌套）时，默认会自动异步多线程逐层解包并展开，无需手动点击每个包。',
        keywords: ['解包', '嵌套', '递归', '展开', '多线程'],
        blocks: [
          para('针对「文件夹 / WAR / ZIP」对比优化：比对完成后，嵌套包内部文件会自动解包并出现在差异树中，AI 分析与差异导出也会覆盖到嵌套子文件。'),
          list([
            '在「设置 → 解析与导出」可调整「解包线程数」与「最大解包深度」。',
            '深度接近上限或超时未完成的嵌套归档会保留为可手动展开的节点，不丢失结构。',
          ]),
          warn('过大的包开启自动解包会更占内存与耗时；如不需要查看嵌套内部，可在设置中关闭自动逐层解包。'),
        ]
      }),
      entry({
        id: 'classify',
        title: 'AI 智能分类',
        intro: '让 AI 对差异文件自动打上风险等级与变化类别标签，帮你优先审阅高风险变更。',
        keywords: ['智能分类', '风险', '打标', '分类'],
        blocks: [
          steps([
            '完成一次对比后，点击「AI 智能分类」。',
            '系统为差异文件打上「高 / 中 / 低」风险与变化类别。',
            '在差异树上即可按风险排序或过滤，优先处理高风险文件。',
          ]),
          note('需在「设置 → AI 服务」配置并启用可用的模型，且已完成一次比对。'),
        ]
      }),
      entry({
        id: 'ai',
        title: 'AI 智能分析',
        intro: '对整体变更执行智能分析，支持多类分析并行发起，结果以控制台流式展示。',
        keywords: ['ai', 'AI分析', '风险', '影响范围', '测试要点', '破坏性'],
        blocks: [
          para('支持的分析类别（可并行、互不阻塞）：'),
          list([
            '整体风险分析：总览这次变化的风险情况。',
            '破坏性变更专项：识别可能破坏现有功能的变更。',
            '影响范围分析：评估变化波及的模块与范围。',
            '测试要点分析：给出应重点回归的测试建议。',
            '自定义问题：按你的自由表述发起分析。',
          ]),
          note('AI 分析需待自动解包比对完成后方可发起；分析会占用模型额度，超成本阈值时会先给你确认。'),
        ]
      }),
      entry({
        id: 'report',
        title: '报告生成',
        intro: '把比对与 AI 分析结果整理成一份结构化报告，也支持针对单一主题的聚焦报告。',
        keywords: ['报告', '生成', '导出', 'markdown'],
        blocks: [
          list([
            '整体报告：包含差异统计、变更文件清单、源码 diff 摘要与 AI 章节的完整报告。',
            '聚焦报告：仅针对你选择的某一具体分析项（如「测试要点」）生成精简报告。',
          ]),
          note('可在「设置」开启「比对后自动生成 AI 报告」，减少手动操作。'),
        ]
      }),
      entry({
        id: 'context',
        title: '项目级上下文增强',
        intro: '把本机项目源码/文档作为背景上下文注入 AI，让分析结果更贴合你的业务。',
        keywords: ['上下文', '项目', '增强', '注入'],
        blocks: [
          steps([
            '在「设置 → 界面与高级」启用「项目级上下文增强」，并选择项目源码/文档目录。',
            '保存配置后自动扫描该项目目录结构。',
            '此后发起 AI 分析时，会把项目概览注入分析请求，结果更贴合实际代码。',
          ]),
        ]
      }),
      entry({
        id: 'decompile',
        title: '反编译查看',
        intro: '对于只有字节码（.class）的变更，自动反编译为源码后对比，避免直接看不可读的字节码。',
        keywords: ['反编译', 'class', '字节码', 'cfr'],
        blocks: [
          para('内置反编译器，也可在「设置 → 解析与导出」指定外部 CFR jar。命中内部业务前缀的类默认按业务码展开。'),
          warn('反编译结果仅作理解参考，切勿作为生产的唯一依据。'),
        ]
      }),
    ]
  },
  {
    key: 'faq',
    title: '常见问题（FAQ）',
    icon: 'bi-question-circle',
    entries: [
      entry({
        id: 'faq_compare',
        title: '为什么没有看到任何差异？',
        intro: '分别排查「路径是否真正可比」「过滤是否掩盖了结果」「是否处于解包/分析中」。',
        keywords: ['无差异', '空白', '空树'],
        blocks: [
          list([
            '确认左侧为旧版、右侧为新版，且内容确实存在差异。',
            '看差异树顶部的状态过滤：若勾选了状态过滤把某项隐藏，结果可能被“藏起来”。',
            '搜索框是否带有上一次的关键字/正则，把结果过滤掉了。',
            '是否仍在「自动逐层解包」或 AI 分析中——这些阶段完成后结果才会稳定。',
          ]),
        ]
      }),
      entry({
        id: 'faq_ai',
        title: '点了 AI 分析没反应或报错？',
        intro: 'AI 依赖模型连通性与任务状态，按下面的顺序检查。',
        keywords: ['AI', '没反应', '报错', '模型', 'key'],
        blocks: [
          list([
            '先在「设置 → AI 服务」确认已启用并在模型厂商下拉选择正确模型，可点「连接测试」验证连通。',
            '确认已至少完成一次「开始对比」——AI 需基于比对结果分析。',
            '在自动逐层解包期间 AI 会被禁用，请等解包完成后再发起。',
            '公网模型可能需要配置代理或关闭「严格 SSRF」限制（如使用本地模型时）。',
          ]),
        ]
      }),
      entry({
        id: 'faq_export',
        title: '导出差异资产失败/为空？',
        intro: '导出依赖已完成、解包已结束，且至少存在新增或修改文件。',
        keywords: ['导出失败', '导出为空', '资产'],
        blocks: [
          list([
            '确认对比已完成（状态为完成），且自动解包已结束。',
            '若差异全部为「未变」或「删除但无增量」，导出包中没有新增/修改项是正常的。',
            '超大包导出耗时较长，请耐心等待下载完成。',
          ]),
        ]
      }),
      entry({
        id: 'faq_unpack',
        title: '自动解包卡住或很慢？',
        intro: '大包 + 深嵌套 + 高并发，可能受深度/字节上限/机器性能影响。',
        keywords: ['解包慢', '解包卡', '嵌套', '内存'],
        blocks: [
          list([
            '大包建议先关闭自动逐层解包（仅比对顶层），需要时再单独展开。',
            '在「设置 → 解析与导出」降低「最大解包深度」或「解包线程数」可减少资源占用。',
            '超时或触顶的归档会保留为可手动展开节点，不会丢失结构。',
          ]),
        ]
      }),
      entry({
        id: 'faq_setup',
        title: '桌面版如何配置 AI / 代理？',
        intro: '所有配置都在「配置中心」界面完成，无需手工改文件。',
        keywords: ['配置', '代理', '安装', '设置'],
        blocks: [
          list([
            '模型服务与密钥：设置 → AI 服务。',
            '代理（访问公网模型）：设置 → AI 服务 → HTTP/HTTPS 代理。',
            '本地/私有化模型通常无需 API Key，并需关闭「严格 SSRF」。',
          ]),
        ]
      }),
    ]
  },
  {
    key: 'tips',
    title: '使用技巧',
    icon: 'bi-lightbulb',
    entries: [
      entry({
        id: 'tips_ignore',
        title: '用“忽略”减少噪声',
        intro: '日志、临时文件、图片等高频噪声可通过忽略规则快速排除，让差异树更干净。',
        keywords: ['忽略', '排除', '噪声', '过滤'],
        blocks: [
          list([
            '右键条目 →「忽略」：把该文件加入忽略规则，持久化生效。',
            '在「设置 → 解析与导出 → 比对过滤」勾选要整体忽略的文件类型（如 .log/.tmp）。',
            '忽略规则可在「差异文件树 → 属性弹窗」统一查看与清空。',
          ]),
        ]
      }),
      entry({
        id: 'tips_perf',
        title: '大包对比更快的姿势',
        intro: '同一份数据，合并忽略与关闭无关层可明显提速。',
        keywords: ['性能', '大包', '优化', '卡'],
        blocks: [
          list([
            '关闭「未变」显示，减少渲染与浏览量。',
            '若非业务必要，忽略 .class / .jar 等编译产物只保留源码。',
            '调低解包深度与线程数，避免无谓资源消耗。',
          ]),
        ]
      }),
      entry({
        id: 'tips_cost',
        title: '控制 AI 分析与成本',
        intro: 'AI 调用会按 token 计费，配置成本闸门可避免意外高额。',
        keywords: ['成本', 'token', '闸门', '预算'],
        blocks: [
          list([
            '在「设置 → 解析与导出」设置「成本闸门告警 token」阈值。',
            '超过阈值时，AI 分析会先确认再调用，避免误触发高额账单。',
            '优先用本地/私有化模型，可降低合规与成本压力。',
          ]),
        ]
      }),
    ]
  }
]

/** 扁平化的全部条目（带 categoryKey/categoryTitle），供检索与导航使用。 */
export const HELP_ENTRIES = (() => {
  const out = []
  for (const c of CATEGORIES) {
    for (const e of c.entries) {
      out.push({ ...e, categoryKey: c.key, categoryTitle: c.title })
    }
  }
  return out
})()

/** 检索用的拼装文本（标题 + 导语 + 块文本 + 关键词），命中即纳入结果。 */
function entrySearchText(e) {
  const blockText = (e.blocks || [])
    .map((b) => [b.text, ...(b.items || [])].filter(Boolean).join(' '))
    .join(' ')
  return [e.title, e.intro, blockText, ...(e.keywords || [])].join(' ').toLowerCase()
}

/**
 * 帮助检索：返回命中的条目（按分类分组后可导航）。query 为空返回 []。
 * 命中规则：条目的标题/导语/正文/关键词任意一处包含 query（不区分大小写）。
 */
export function searchHelp(query, entries = HELP_ENTRIES) {
  const q = String(query || '').trim().toLowerCase()
  if (!q) return []
  return entries.filter((e) => entrySearchText(e).includes(q))
}

/**
 * 结构自检：确保内容模型可被组件/测试放心消费。
 * 返回错误列表（为空即合法）。主要校验：
 *  - 分类/条目/块层级齐全；entry.id 唯一；
 *  - 每个块 type 合法、steps/note/warn 有文本、list/steps 有 items。
 * 供单测调用，也便于未来维护时快速定位内容缺陷。
 */
export function validateHelp(entries = HELP_ENTRIES) {
  const errors = []
  const seen = new Set()
  for (const e of entries) {
    if (!e.id) errors.push(`条目缺少 id: ${e.title || '(无标题)'}`)
    if (e.id) {
      if (seen.has(e.id)) errors.push(`条目 id 重复: ${e.id}`)
      seen.add(e.id)
    }
    if (!e.title) errors.push(`条目缺少 title: id=${e.id || '(无)'}`)
    if (!Array.isArray(e.blocks)) errors.push(`条目 blocks 非数组: ${e.id}`)
    for (const b of e.blocks || []) {
      if (!BLOCK_TYPES.includes(b.type)) errors.push(`${e.id}.blocks 含非法 type=${b.type}`)
      if ((b.type === 'steps' || b.type === 'list') && !Array.isArray(b.items)) errors.push(`${e.id} 的 ${b.type} 块缺少 items`)
      if ((b.type === 'para' || b.type === 'note' || b.type === 'warn') && !b.text) errors.push(`${e.id} 的 ${b.type} 块缺少 text`)
    }
  }
  return errors
}