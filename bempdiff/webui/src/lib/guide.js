// 引导系统纯逻辑：步骤定义 + 完成态存储。组件 GuideOverlay.vue 只负责渲染与交互，
// 状态与数据逻辑集中在此，便于单测与后续扩展新菜单/功能区。
//
// 说明：BempDiff 是无路由的单页桌面工具（无传统"菜单/路由"），故「各菜单的引导」映射为
// 各功能区块（工具栏操作 / 差异文件树 / 对比视图 / 智能分析 / 配置中心）的分组步骤。
// 每步通过 selector 定位高亮目标元素；目标可能暂未渲染（如未对比时无差异树），用 placeholder
// 注明"执行后再观察"，并把高亮目标设为存在的容器或弹层自身，避免空悬。

// 存储键（与项目既有 bempdiff-* 前缀一致）
export const GUIDE_DONE_KEY = 'bempdiff-guide-done'
export const GUIDE_AUTOPLAY_KEY = 'bempdiff-guide-autoplay'
// 引导开启状态（true=已向用户展示过），用于"仅首次自动弹一次"
export const GUIDE_SEEN_KEY = 'bempdiff-guide-seen'
// 常驻「引导」按钮启动键：默认隐藏，需在「帮助文档」菜单中开启后才显示（见 HelpDoc.vue 联动）。
export const GUIDE_BTN_KEY = 'bempdiff-guide-btn'

// 引导按钮可见性状态：进程内响应式（HelpDoc 开关与 GuideOverlay 渲染共享同一实时状态），
// 并持久化到 localStorage 以便下次会话保持。默认 false（按钮隐藏，避免常驻占位/遮挡）。
let _btnVisible = loadBtnVisible()
export const guideButtonVisible = { get value() { return _btnVisible } }

function loadBtnVisible() {
  try { return localStorage.getItem(GUIDE_BTN_KEY) === 'on' } catch (_) { return false }
}
/** 开启/关闭常驻引导按钮（HelpDoc「开始引导」入口调用）。 */
export function setGuideButtonVisible(v) {
  _btnVisible = v
  try { localStorage.setItem(GUIDE_BTN_KEY, v ? 'on' : 'off') } catch (_) { /* 存储不可用仅本次生效 */ }
}
export function isGuideButtonVisible() { return _btnVisible }

// 分组与步骤定义。字段说明：
//   group  - 分组名（对应一个功能区块）
//   title  - 步骤标题
//   text   - 步骤说明（中文）
//   target - 要高亮/滚向的元素 CSS selector（可能不存在）
//   minWidth - 该步骤建议至少需要的最小窗口宽度（px），窄屏自动跳过（防布局错位）
export const GUIDE_GROUPS = ['getstart', 'toolbar', 'difftree', 'diffview', 'ai', 'config']

// title/text 保留中文原文（兼容既有单测与未迁移调用方）；
// titleKey/textKey 为 i18n 键：渲染侧优先 t(titleKey)，缺失时回退 title。
export const GUIDE_STEPS = [
  // 入门：整体介绍（无特定高亮，展示欢迎语）
  { group: 'getstart', title: '欢迎使用 BempDiff', titleKey: 'guide.step0.title', text: 'BempDiff 用于对比两个包（war/jar/zip）或两个目录的内容差异，并结合 AI 输出风险与影响分析。跟随引导快速了解核心操作。', textKey: 'guide.step0.text', target: null },
  // 工具栏
  { group: 'toolbar', title: '① 选择输入（老包 / 新包）', titleKey: 'guide.step1.title', text: '在顶部左侧选择"包"或"目录"类型，然后在老包/新包输入框填入路径。支持点击浏览选择，桌面壳也支持直接拖入文件。', textKey: 'guide.step1.text', target: '.drop-zone', minWidth: 1000 },
  { group: 'toolbar', title: '② 开始比对', titleKey: 'guide.step2.title', text: '两侧路径就绪后，点击蓝色"比对"按钮（arrow-left-right 图标），后端将反编译并生成差异文件树。', textKey: 'guide.step2.text', target: '.btn-primary[title*="反编译"]', minWidth: 900 },
  // 差异文件树
  { group: 'difftree', title: '③ 差异文件树', titleKey: 'guide.step3.title', text: '左侧为差异文件树：按层级展示修改/新增/删除/未变文件，可搜索、按状态或风险过滤。若尚未比对，此区域会显示"暂无差异"。', textKey: 'guide.step3.text', target: '.col-tree', placeholder: true },
  // 对比视图
  { group: 'diffview', title: '④ 双栏源码比对', titleKey: 'guide.step4.title', text: '点击差异树中的文件，中间区域展示双栏（或 Git 风格）内容比对，差异行高亮、支持换行/全屏专注等切换。', textKey: 'guide.step4.text', target: '.app-main', placeholder: true },
  // 智能分析
  { group: 'ai', title: '⑤ AI 智能分析', titleKey: 'guide.step5.title', text: '右侧智能分析栏可发起整体风险分析、破坏性变更、影响范围、测试要点等多类分析；也可用顶部 CPU 图标发起新分析。', textKey: 'guide.step5.text', target: '.col-info, .btn-outline-secondary[title*="AI 分析"]', placeholder: true },
  // 配置中心
  { group: 'config', title: '⑥ 配置中心', titleKey: 'guide.step6.title', text: '点击顶部齿轮图标打开配置中心：可配置 AI 服务（模型/连接测试）、解析与导出、差异树过滤、界面与高级等。', textKey: 'guide.step6.text', target: '.btn-outline-secondary[title*="配置中心"]' }
]

/** 取某分组的所有步骤。 */
export function stepsOf(group) {
  return GUIDE_STEPS.filter(s => s.group === group)
}

/** 全部步骤展平，按定义顺序。 */
export function allSteps() {
  return GUIDE_STEPS
}

/** 第 idx 步（展平序）所在分组（用于分组标题展示）。 */
export function groupOf(idx) {
  const s = allSteps()[idx]
  return s ? s.group : null
}

/** 是否整组引导已完成。 */
export function isGuideDone(storage) {
  return storage === true || String(storage).toLowerCase() === 'true'
}

/** 是否首次（尚未 seen）。 */
export function isFirstRun(seen) {
  return seen !== true && seen !== 'true'
}

/** 生成第 idx 步的进度文案，如 "3 / 9"。 */
export function progressText(idx) {
  const total = allSteps().length
  const cur = Math.min(Math.max(idx, 0), total - 1)
  return (cur + 1) + ' / ' + total
}

/** 从选择器解析高亮矩形（视口坐标，直接用于 position:fixed 高亮框）：找不到时返回 null。 */
export function locateTarget(selector, root = document) {
  if (!selector) return null
  try {
    const el = root.querySelector(selector)
    if (!el) return null
    const r = el.getBoundingClientRect()
    if (r.width === 0 && r.height === 0) return null
    // getBoundingClientRect 已是相对视口坐标，与组件内 fixed 定位坐标系一致，
    // 无需叠加 window scroll 偏移。
    return {
      el,
      top: r.top, left: r.left, width: r.width, height: r.height
    }
  } catch (_) { return null }
}