#!/usr/bin/env python3
"""把 bempdiff 生成的 Markdown 报告渲染为 IntelliJ IDEA Darcula 风格的 HTML 摘要报告。
支持版本号显式覆盖、Java/JS/CSS/HTML 语法高亮、diff 行级/字符级高亮。"""
import re
import sys
from html import escape
from pathlib import Path

REPORT_MD = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("e2e_work/report.md")
OUT_HTML = Path(sys.argv[2]) if len(sys.argv) > 2 else REPORT_MD.with_suffix(".html")
OLD_VER = sys.argv[3] if len(sys.argv) > 3 else None
NEW_VER = sys.argv[4] if len(sys.argv) > 4 else None

# ---------- 样式 ----------
CSS = """
:root {
  --bg: #2B2B2B;
  --bg-panel: #313335;
  --bg-code: #2B2B2B;
  --text: #A9B7C6;
  --text-dim: #808080;
  --keyword: #CC7832;
  --string: #6A8759;
  --number: #6897BB;
  --comment: #808080;
  --method: #FFC66D;
  --class: #B5BCD9;
  --operator: #A9B7C6;
  --tag: #B5BCD9;
  --attr: #B5BCD9;
  --sel: #FFC66D;
  --prop: #A9B7C6;
  --del-bg: #3C2F2F;
  --del-border: #F0524F;
  --add-bg: #2F3C2F;
  --add-border: #3DAB53;
  --mod-bg: #3D3A2B;
  --mod-border: #D6B032;
  --line-num: #606366;
  --border: #4B4B4B;
}
* { box-sizing: border-box; }
body {
  font-family: "Microsoft YaHei", "PingFang SC", "Source Han Sans", sans-serif;
  background: var(--bg);
  color: var(--text);
  margin: 0;
  padding: 0;
  line-height: 1.6;
}
.container { max-width: 1200px; margin: 0 auto; padding: 24px; }
h1 { font-size: 24px; border-bottom: 2px solid var(--border); padding-bottom: 12px; color: #E8EAF6; }
h2 { font-size: 20px; margin-top: 32px; color: #B5BCD9; border-left: 4px solid var(--keyword); padding-left: 12px; }
h3 { font-size: 16px; margin-top: 24px; color: var(--method); }
.info-bar {
  display: flex; gap: 24px; background: var(--bg-panel); padding: 16px; border-radius: 6px;
  border: 1px solid var(--border); margin: 16px 0;
}
.info-item { display: flex; flex-direction: column; }
.info-label { font-size: 12px; color: var(--text-dim); }
.info-value { font-size: 16px; font-weight: bold; color: #E8EAF6; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin: 16px 0; }
.stat-card {
  background: var(--bg-panel); border: 1px solid var(--border); border-radius: 6px;
  padding: 16px; text-align: center;
}
.stat-value { font-size: 28px; font-weight: bold; }
.stat-label { font-size: 12px; color: var(--text-dim); }
.add { color: #3DAB53; }
.del { color: #F0524F; }
.mod { color: #D6B032; }
.file-block {
  background: var(--bg-code); border: 1px solid var(--border); border-radius: 6px;
  margin: 12px 0; overflow: hidden;
}
.file-header {
  background: var(--bg-panel); padding: 8px 12px; font-size: 13px; color: var(--method);
  border-bottom: 1px solid var(--border); display: flex; justify-content: space-between;
}
.file-engine { color: var(--text-dim); font-size: 12px; }
.code-table { width: 100%; border-collapse: collapse; font-family: "JetBrains Mono", "Consolas", "Monaco", monospace; font-size: 13px; }
.code-table td { padding: 1px 8px; vertical-align: top; white-space: pre-wrap; word-break: break-word; }
.ln {
  width: 40px; text-align: right; color: var(--line-num); background: #313335;
  border-right: 1px solid var(--border); user-select: none;
}
.line-del { background: var(--del-bg); border-left: 3px solid var(--del-border); }
.line-add { background: var(--add-bg); border-left: 3px solid var(--add-border); }
.line-mod { background: var(--mod-bg); border-left: 3px solid var(--mod-border); }
.mark-del { color: var(--del-border); font-weight: bold; }
.mark-add { color: var(--add-border); font-weight: bold; }
.inline-del { background: rgba(240,82,79,0.35); border-radius: 2px; }
.inline-add { background: rgba(61,171,83,0.35); border-radius: 2px; }
.section-note { color: var(--text-dim); font-size: 13px; margin: 8px 0; }
ul.plain { list-style: none; padding-left: 0; }
ul.plain li { padding: 4px 0; }
.footer { margin-top: 40px; padding-top: 16px; border-top: 1px solid var(--border); color: var(--text-dim); font-size: 12px; }
.keyword { color: var(--keyword); font-weight: bold; }
.type { color: var(--class); font-weight: bold; }
.string { color: var(--string); }
.number { color: var(--number); }
.comment { color: var(--comment); font-style: italic; }
.method { color: var(--method); }
.operator { color: var(--operator); }
.tag { color: var(--tag); }
.attr { color: var(--attr); }
.selector { color: var(--sel); }
.property { color: var(--prop); }
"""

# ---------- 语法高亮器 ----------
JAVA_KEYWORDS = {"package", "import", "public", "private", "protected", "static", "final",
                 "class", "interface", "enum", "extends", "implements", "return", "if", "else",
                 "for", "while", "do", "switch", "case", "default", "break", "continue", "new",
                 "this", "super", "try", "catch", "finally", "throw", "throws", "synchronized",
                 "volatile", "transient", "native", "abstract", "strictfp", "boolean", "byte",
                 "char", "short", "int", "long", "float", "double", "void", "true", "false", "null"}
JS_KEYWORDS = {"function", "var", "let", "const", "return", "if", "else", "for", "while",
               "do", "switch", "case", "default", "break", "continue", "new", "this", "class",
               "extends", "import", "export", "from", "true", "false", "null", "undefined",
               "typeof", "instanceof", "in", "of", "async", "await", "yield"}


def span(cls, text):
    return f'<span class="{cls}">{escape(text)}</span>'


def highlight_generic(line, keywords):
    # 先 escape，然后对注释、字符串、关键字、数字做替换（保证不重叠）
    tokens = []
    i = 0
    n = len(line)
    while i < n:
        # 注释 // ...
        if line[i:i + 2] == "//":
            tokens.append(("comment", line[i:]))
            break
        # 字符串 "..." 或 '...'
        if line[i] in '"\'':
            q = line[i]
            j = i + 1
            while j < n and line[j] != q:
                if line[j] == '\\' and j + 1 < n:
                    j += 2
                else:
                    j += 1
            if j < n:
                j += 1
            tokens.append(("string", line[i:j]))
            i = j
            continue
        # 数字
        if line[i].isdigit() or (line[i] == '.' and i + 1 < n and line[i + 1].isdigit()):
            j = i
            while j < n and (line[j].isdigit() or line[j] in '.xXaAbBcCdDeEfF'):
                j += 1
            tokens.append(("number", line[i:j]))
            i = j
            continue
        # 标识符 / 关键字
        if line[i].isalpha() or line[i] == '_':
            j = i
            while j < n and (line[j].isalnum() or line[j] == '_'):
                j += 1
            word = line[i:j]
            kind = "keyword" if word in keywords else ("method" if line[j:j + 1] == '(' else "text")
            tokens.append((kind, word))
            i = j
            continue
        # 操作符/符号（空格不包 span，避免 HTML 膨胀）
        if line[i].isspace():
            tokens.append(("text", line[i]))
        else:
            tokens.append(("operator", line[i]))
        i += 1
    return "".join(span(k, v) if k != "text" else escape(v) for k, v in tokens)


def highlight_java(line):
    # 对方法调用/定义做简单增强：把标识符后的 ( 识别为 method
    html = highlight_generic(line, JAVA_KEYWORDS)
    # 简单识别声明中的方法名：类型 空格 名字(
    html = re.sub(r'(<span class="type">|</span>|\s)+([A-Za-z_][\w]*)\(', lambda m: f'{m.group(0)[:-len(m.group(2))]}<span class="method">{m.group(2)}</span>(', html)
    return html


def highlight_js(line):
    return highlight_generic(line, JS_KEYWORDS)


def highlight_css(line):
    # 简单高亮：{ } : ; 以及选择器/属性/值
    html = escape(line)
    # 选择器：行首直到 {
    html = re.sub(r'^(\s*[^{}\n]+?)(\{)', lambda m: f'{span("selector", m.group(1))}{span("operator", m.group(2))}', html)
    # 属性名（单词后跟 :）
    html = re.sub(r'(\s*)([\w-]+)(\s*:)', lambda m: f'{m.group(1)}{span("property", m.group(2))}{m.group(3)}', html)
    # 字符串
    html = re.sub(r'("[^"]*"|\'[^\']*\')', lambda m: span("string", m.group(1)), html)
    # 数字
    html = re.sub(r'\b(\d+(?:\.\d+)?(?:px|em|rem|%)?)\b', lambda m: span("number", m.group(1)), html)
    return html


def highlight_html(line):
    # 标签 <xxx attr="...">
    html = escape(line)
    def tag_repl(m):
        out = m.group(1)  # < or </
        name = m.group(2)
        rest = m.group(3)
        out += span("tag", name)
        # 属性
        rest = re.sub(r'\s([\w-]+)(=)("[^"]*"|\'[^\']*\')',
                      lambda a: f' {span("attr", a.group(1))}{span("operator", a.group(2))}{span("string", a.group(3))}', rest)
        out += rest
        return out
    html = re.sub(r'(<\/?)([\w-]+)([^>]*?)(/?>)', tag_repl, html)
    # 注释
    html = re.sub(r'(&lt;!--.*?--&gt;)', lambda m: span("comment", m.group(1)), html)
    return html


def pick_highlighter(path):
    p = path.lower()
    if p.endswith(".java") or "/demo" in p:
        return highlight_java
    if p.endswith(".js"):
        return highlight_js
    if p.endswith(".css"):
        return highlight_css
    if p.endswith(".html") or p.endswith(".htm"):
        return highlight_html
    return highlight_js  # 默认


# ---------- Diff 处理 ----------
def compute_inline_diff(old_line, new_line):
    """对两个差异行做简单 LCS，返回带 inline 高亮的 HTML 对。"""
    # 简单策略：找出最长公共前缀/后缀，中间部分作为变更
    max_pre = 0
    for i in range(min(len(old_line), len(new_line))):
        if old_line[i] == new_line[i]:
            max_pre = i + 1
        else:
            break
    max_suf = 0
    for i in range(1, min(len(old_line), len(new_line)) - max_pre + 1):
        if old_line[-i] == new_line[-i]:
            max_suf = i
        else:
            break
    # 如果后缀重叠了前缀，调整
    if max_pre + max_suf > min(len(old_line), len(new_line)):
        max_suf = min(len(old_line), len(new_line)) - max_pre
    old_mid = old_line[max_pre:len(old_line) - max_suf]
    new_mid = new_line[max_pre:len(new_line) - max_suf]
    return old_line[:max_pre] + (f'<span class="inline-del">{escape(old_mid)}</span>' if old_mid else "") + old_line[len(old_line) - max_suf:], \
           new_line[:max_pre] + (f'<span class="inline-add">{escape(new_mid)}</span>' if new_mid else "") + new_line[len(new_line) - max_suf:]


def render_code_block(lines, path):
    highlighter = pick_highlighter(path)
    rows = []
    ln = 0
    i = 0
    # 配对相邻的 - 和 + 行，给 inline 高亮
    paired = []
    while i < len(lines):
        raw = lines[i]
        if raw.startswith("- ") and i + 1 < len(lines) and lines[i + 1].startswith("+ "):
            paired.append(("pair", raw, lines[i + 1]))
            i += 2
            continue
        if raw.startswith("- "):
            paired.append(("del", raw))
            i += 1
            continue
        if raw.startswith("+ "):
            paired.append(("add", raw))
            i += 1
            continue
        paired.append(("ctx", raw))
        i += 1

    for item in paired:
        if item[0] == "pair":
            _, old_raw, new_raw = item
            ln += 1
            old_body = old_raw[2:]
            new_body = new_raw[2:]
            old_html, new_html = compute_inline_diff(old_body, new_body)
            rows.append(f'<tr><td class="ln">{ln}</td><td class="line-del"><span class="mark-del">-</span> {old_html}</td></tr>')
            ln += 1
            rows.append(f'<tr><td class="ln">{ln}</td><td class="line-add"><span class="mark-add">+</span> {new_html}</td></tr>')
        elif item[0] == "del":
            ln += 1
            body = item[1][2:]
            rows.append(f'<tr><td class="ln">{ln}</td><td class="line-del"><span class="mark-del">-</span> {highlighter(body)}</td></tr>')
        elif item[0] == "add":
            ln += 1
            body = item[1][2:]
            rows.append(f'<tr><td class="ln">{ln}</td><td class="line-add"><span class="mark-add">+</span> {highlighter(body)}</td></tr>')
        else:
            ln += 1
            body = item[1]
            rows.append(f'<tr><td class="ln">{ln}</td><td>{highlighter(body)}</td></tr>')
    return "\n".join(rows)


# ---------- 报告解析 ----------
def parse_report(md_text):
    sections = []
    current = None
    in_code = False
    code_lang = ""
    code_lines = []
    file_path = ""
    lines = md_text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            if not in_code:
                in_code = True
                code_lang = line.strip()[3:]
                code_lines = []
                # 回看上一条，取文件名
                file_path = ""
                if current and current["type"] == "file_header":
                    file_path = current["path"]
                    sections.append(current)
                    current = None
            else:
                in_code = False
                sections.append({"type": "code", "lang": code_lang, "path": file_path, "lines": code_lines})
            i += 1
            continue
        if in_code:
            code_lines.append(line)
            i += 1
            continue
        if line.startswith("### "):
            if current:
                sections.append(current)
            current = {"type": "file_header", "path": line[4:].strip(), "engine": ""}
            i += 1
            continue
        if line.startswith("- 处理引擎：") or line.startswith("- 反编译引擎："):
            if current and current["type"] == "file_header":
                current["engine"] = line.split("：", 1)[1].strip()
            i += 1
            continue
        if line.startswith("# ") or line.startswith("## "):
            if current:
                sections.append(current)
                current = None
            sections.append({"type": "heading", "level": line.count("#", 0, 6), "text": line.strip("# ")})
            i += 1
            continue
        if line.strip():
            if current and current["type"] not in ("text",):
                sections.append(current)
                current = {"type": "text", "lines": [line]}
            elif current:
                current["lines"].append(line)
            else:
                current = {"type": "text", "lines": [line]}
        i += 1
    if current:
        sections.append(current)
    return sections


# ---------- HTML 组装 ----------
def build_html(sections):
    parts = []
    title = "版本差异摘要报告"
    old_ver, new_ver = None, None
    stats = {"added": 0, "deleted": 0, "modified": 0, "unchanged": 0}
    file_blocks = []
    current_file = None

    for sec in sections:
        if sec["type"] == "heading" and sec["level"] == 1:
            title = escape(sec["text"])
            parts.append(f'<h1>{title}</h1>')
        elif sec["type"] == "heading" and sec["level"] == 2:
            text = sec["text"]
            if text.startswith("一、差异统计"):
                parts.append('<h2>一、差异统计</h2>')
            elif text.startswith("二、差异文件树"):
                parts.append('<h2>二、差异文件树</h2>')
            elif text.startswith("三、反编译源码级差异"):
                parts.append('<h2>三、反编译源码级差异</h2>')
            elif text.startswith("四、前端资源代码差异"):
                parts.append('<h2>四、前端资源代码差异</h2>')
            elif text.startswith("五、破坏性变更清单"):
                parts.append('<h2>五、破坏性变更清单</h2>')
            elif text.startswith("六、审计摘要"):
                parts.append('<h2>六、审计摘要</h2>')
            else:
                parts.append(f'<h2>{escape(text)}</h2>')
        elif sec["type"] == "text":
            for line in sec["lines"]:
                # 版本号提取
                m = re.search(r'老包：`([^`]+)`.*?版本\s+([\d\.\w-]+)', line)
                if m:
                    old_ver = m.group(2)
                    continue
                m = re.search(r'新包：`([^`]+)`.*?版本\s+([\d\.\w-]+)', line)
                if m:
                    new_ver = m.group(2)
                    continue
                # 统计
                m = re.search(r'新增 \*\*(\d+)\*\*.*?删除 \*\*(\d+)\*\*.*?修改 \*\*(\d+)\*\*.*?未变 (\d+)', line)
                if m:
                    stats["added"] = int(m.group(1))
                    stats["deleted"] = int(m.group(2))
                    stats["modified"] = int(m.group(3))
                    stats["unchanged"] = int(m.group(4))
                    continue
                # 普通列表
                if line.startswith("- "):
                    parts.append(f'<p class="section-note">{escape(line[2:])}</p>')
                else:
                    parts.append(f'<p class="section-note">{escape(line)}</p>')
        elif sec["type"] == "file_header":
            if current_file:
                file_blocks.append(current_file)
            current_file = {"path": sec["path"], "engine": sec["engine"], "code": None}
        elif sec["type"] == "code":
            if current_file:
                current_file["code"] = render_code_block(sec["lines"], current_file["path"])
            else:
                # 独立代码块
                current_file = {"path": sec["path"], "engine": "", "code": render_code_block(sec["lines"], sec["path"])}
    if current_file:
        file_blocks.append(current_file)

    # 覆盖版本号
    if OLD_VER:
        old_ver = OLD_VER
    if NEW_VER:
        new_ver = NEW_VER

    info_bar = f'''
    <div class="info-bar">
      <div class="info-item"><span class="info-label">旧包版本</span><span class="info-value">{escape(old_ver or 'N/A')}</span></div>
      <div class="info-item"><span class="info-label">新包版本</span><span class="info-value">{escape(new_ver or 'N/A')}</span></div>
      <div class="info-item"><span class="info-label">生成时间</span><span class="info-value">{escape(time_now())}</span></div>
    </div>
    '''
    stats_html = f'''
    <div class="stats">
      <div class="stat-card"><div class="stat-value add">+{stats["added"]}</div><div class="stat-label">新增文件</div></div>
      <div class="stat-card"><div class="stat-value del">-{stats["deleted"]}</div><div class="stat-label">删除文件</div></div>
      <div class="stat-card"><div class="stat-value mod">~{stats["modified"]}</div><div class="stat-label">修改文件</div></div>
      <div class="stat-card"><div class="stat-value">{stats["unchanged"]}</div><div class="stat-label">未变文件</div></div>
    </div>
    '''

    for fb in file_blocks:
        parts.append(f'''
        <h3>{escape(fb["path"])}</h3>
        <div class="file-block">
          <div class="file-header"><span>{escape(fb["path"])}</span><span class="file-engine">{escape(fb["engine"])}</span></div>
          <table class="code-table">{fb["code"]}</table>
        </div>
        ''')

    body = "\n".join(parts)
    html = f'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{escape(title)}</title>
<style>{CSS}</style>
</head>
<body>
<div class="container">
{info_bar}
{stats_html}
{body}
<div class="footer">由 BempDiff 生成 · 报告源文件：{escape(str(REPORT_MD))}</div>
</div>
</body>
</html>'''
    return html


def time_now():
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


if __name__ == "__main__":
    md = REPORT_MD.read_text(encoding="utf-8")
    html = build_html(parse_report(md))
    OUT_HTML.write_text(html, encoding="utf-8")
    print(f"HTML report written to: {OUT_HTML}")
