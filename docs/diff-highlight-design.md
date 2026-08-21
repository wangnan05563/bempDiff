# Diff Gutter 差异化对比栏 + 语法高亮配色方案

> 实现日期：2026-08-21 ｜ 涉及文件：`webui/src/lib/syntax_highlight.js`（新增）、`webui/src/components/DiffView.vue`（改造）、`webui/src/__tests__/syntax_highlight.spec.js`（新增）

## 一、需求与目标

1. **diff gutter**：双栏差异视图中，每一行左侧（对比栏）清晰标识 新增/删除/修改 状态，用差异化颜色 + 图标。
2. **语法高亮**：按文件类型（JS/TS、Java、Python、HTML/CSS、Markdown 等）自动匹配语法着色，代码层次一目了然。
3. **颜色不冲突**：语法高亮的字体色与差异标记（增/删/改）的背景色/边框色互不混淆。
4. **视觉层次**：关键字、函数、类型、字符串、数字、注释等语法元素有明显色彩区分；差异标记醒目不刺眼。
5. **可维护**：统一色板（CSS 变量）、统一 token 类型表、语言扩展低成本。

## 二、整体架构

```
源码行（old/new）
   │  alignLines 行级 LCS（已有）
   ▼
差异行 rows（ctx/rep/del/add）── 虚拟滚动只渲染可见行
   │  getInline()（已有，改为并入 token）
   ▼
每个 diff 片段（im-del/im-add/普通）内部再 tokenizeLine()
   │  syntax_highlight.js（新增）
   ▼
双层 DOM：<span class="im-del">（背景=差异语义）> <span class="tok tok-kw">（前景=语法层次）
   │
   ▼
渲染：diff gutter 列（图标+色条）｜行号列 ｜代码列
```

**分层原则**：背景管「差异语义」（红=删、绿=增、橙=改），前景管「语法层次」（token 语义色）。两层互不干扰，这是冲突规避的根本。

## 三、diff gutter 规范

| 行类型 | 左侧栏（旧） | 右侧栏（新） | 整行底色 | gutter 色条 |
|---|---|---|---|---|
| ctx 未变 | — | — | 无 | 无 |
| add 新增 | — | ➕ plus | 淡绿 `--bs-success-bg-subtle` | 绿 20% |
| del 删除 | ➖ dash | — | 淡红 `--bs-danger-bg-subtle` | 红 20% |
| rep 修改 | ✏️ pencil | ✏️ pencil | 淡橙 `--bs-warning-bg-subtle` | 橙 26% |

- 图标只在「该侧存在该类型内容」时出现（`gtIcon(v, side)`：左栏 del/rep，右栏 add/rep），空侧 gutter 保持空白占位，维持列对齐。
- gutter 自带 `border-right` 与行号列分隔；图标色：add=`--bs-success`、del=`--bs-danger`、rep=`#b45309`（深琥珀，保证淡橙底上可读）。
- 网格列：换行模式 `1.15rem 3.2rem 1fr 1.15rem 3.2rem 1fr`（左 gutter｜左行号｜左代码｜右 gutter｜右行号｜右代码）；分栏模式 `1.15rem 3.2rem 1fr`。

## 四、语法高亮：token 类型与色板

**一套语义色板跨语言统一**（视觉统一 + 维护简单），语言差异只体现在产出哪些 token 类型。

### token 类型表

| token | CSS 类 | 语义 | 覆盖语言 |
|---|---|---|---|
| keyword | `.tok-keyword` | 关键字/控制流 | 全部代码语言 |
| string | `.tok-string` | 字符串/引号值 | 全部 + HTML 属性值 |
| comment | `.tok-comment` | 注释/引用 | 全部 |
| number | `.tok-number` | 数字/颜色值 | 全部 + CSS |
| ident | `.tok-ident` | 标识符/变量/选择器 | 全部 |
| func | `.tok-func` | 函数调用（后跟 `(`） | 全部代码语言 |
| type | `.tok-type` | 类型/类名（首字母大写） | Java/TS/Python |
| tag | `.tok-tag` | HTML 标签 | HTML/JSP |
| attr | `.tok-attr` | HTML/CSS 属性名 | HTML/CSS |
| heading | `.tok-heading` | 标题 | Markdown |
| link | `.tok-link` | 链接文本 | Markdown |
| emph | `.tok-emph` | 粗体/斜体 | Markdown |
| inlinecode | `.tok-inlinecode` | 行内代码 | Markdown |
| op | `.tok-op` | 运算符/分隔符 | 全部 |
| ws / plain | 无 span | 空白/普通文本（不包裹，省 DOM） | 全部 |

### 色板（CSS 变量，定义在 `.col-center`）

| 变量 | 色值 | 用途 |
|---|---|---|
| `--tok-kw` | `#7c3aed` 紫 | 关键字 |
| `--tok-str` | `#b45309` 琥珀 | 字符串 |
| `--tok-com` | `#64748b` 蓝灰 | 注释 |
| `--tok-num` | `#0891b2` 青 | 数字 |
| `--tok-id` | `#475569` 深蓝灰 | 标识符 |
| `--tok-fn` | `#2563eb` 蓝 | 函数调用 |
| `--tok-type` | `#0891b2` 青 | 类型 |
| `--tok-tag` | `#be185d` 玫红 | HTML 标签 |
| `--tok-attr` | `#0284c7` 蓝 | 属性名 |
| `--tok-hd` | `#7c3aed` 紫 | 标题/强调 |
| `--tok-link` | `#2563eb` 蓝 | 链接 |
| `--tok-code` | `#b45309` 琥珀 | 行内代码 |
| `--tok-op` | `#64748b` 蓝灰 | 运算符 |

### 冲突规避矩阵（核心）

| diff 语义色（保留区） | 语法色（避开区） |
|---|---|
| 红 `#dc3545` 系（删除、`--bs-danger`） | ❌ 语法色不用纯红/亮红 |
| 绿 `#198754` 系（新增、`--bs-success`） | ❌ 语法色不用纯绿/亮绿 |
| 橙 `#fd7e14` 系（修改、`--bs-warning`） | ❌ 语法色不用纯橙/亮橙 |

规避手段（三层兜底）：
1. **源头规避**：token 色板全部落在 紫/蓝/青/蓝灰/琥珀褐/玫红 等非红绿橙区系，色相上不与 diff 三系混淆。
2. **分层兜底**：diff 片段内的 token 自动加深 40% —— `.code .im-del .tok, .code .im-add .tok { color: color-mix(in srgb, currentColor 60%, #000); }`，保证红/绿底上对比度足够、且各 token 类型仍保留各自色相层次。
3. **语义兜底**：整行底色（add/del/rep）均为 Bootstrap 的 `*-bg-subtle` 极淡色，与任何前景语法色叠加对比度都充分。

## 五、语言识别

```js
langOf(key, fc)  // 扩展名优先 → FileClass 兜底 → 'plain'
```

- 扩展名表：`.js/.mjs/.cjs/.jsx → js`、`.ts/.tsx → ts`、`.java → java`、`.py → python`、`.html/.htm/.xhtml → html`、`.css/.scss/.less → css`、`.md/.markdown → markdown`、`.jsp/.jspx/.tag/.tagx → jsp`。
- FileClass 兜底：`CLASS→java`、`JS→js`、`HTML→html`、`CSS→css`、`JSP→jsp`，其余 `→plain`（XML/JSON/properties 等配置类暂不高亮，纯文本显示）。
- 大小写不敏感。`.md/.py/.ts` 后端不细分类型（归 OTHER/CONFIG），必须靠扩展名表命中。

## 六、性能

- **虚拟滚动 + 惰性计算**：diff 视图只渲染可见行（约 40 行），tokenize 在 `getInline()` 内对可见行逐行执行，结果并入既有 `inlineCache`（key = `行下标|语言`），切文件/粒度/折叠时清空。
- **O(n) 每行**：词法为单遍状态机（字符串转义 / 块注释 / 行注释），不做回溯；HTML/CSS/Markdown 用前缀正则扫描。
- **超大文件无需降级**：tokenize 成本与「行数」无关只与「可见行」有关，`oversized`（>6 万行）降级仅针对行内 LCS，语法高亮不受影响。

## 七、扩展新语言 / 新 token 类型

1. **新语言**（如 Go）：
   - 关键字加入 `KEYWORDS`（超集，多数语言可复用）；
   - `CODE_CONF` 加 `go: { lineComment: '//', blockComment: ['/*','*/'], quotes: ['"', "'", '`'] }`；
   - 若语法特殊（如 Go 的 struct tag），在 `tokenizeLine()` 分发里加专用函数；
   - `langOf()` 扩展名表加 `.go → go`，`FC_LANG` 无需动。
2. **新 token 类型**（如 XML 标签名）：
   - `TOKEN_TYPES` 加类型名；
   - 某语言 tokenizer 产出该类型；
   - `.col-center` 加一个 `--tok-*` 变量 + 一个 `.tok-*` 色类（若用现成色可复用变量）；
   - `syntax_highlight.spec.js` 补断言。
3. **暗色主题**：色板为明暗通用中调；如需更亮，加 `[data-bs-theme="dark"] .col-center { --tok-*: ... }` 覆盖即可，类名与结构不变。

## 八、验证

- `vite build`：49 模块，exit 0。
- `vitest run`：10 文件 128 用例全绿（新增 syntax_highlight.spec.js 13 用例：各语言 tokenize 结构、字符串转义、token 合并、langOf 扩展名/兜底/大小写）。
- 独立验证：TipButton.spec.js 存在定时器类 flaky（满载环境偶发），单跑全绿，与本次改动无关。
