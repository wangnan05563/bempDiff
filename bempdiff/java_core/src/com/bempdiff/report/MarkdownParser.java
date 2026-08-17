package com.bempdiff.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量级 Markdown 解析器（纯 Java，无 JavaFX 依赖，便于无头单测）。
 *
 * 支持常用元素：
 *  - 标题（# ~ ######）
 *  - 段落（相邻非空行合并，行内支持 **粗体** / *斜体* / `行内代码`）
 *  - 围栏代码块（``` lang ... ```）
 *  - 无序列表（- / *）与有序列表（1. 2. ...），按缩进呈现层级
 *  - GFM 表格（| 表头 | 与 |---|---| 分隔行）
 *  - 分隔线（--- / *** / ___）
 *  - 引用块（> ...）
 *
 * 设计目标：覆盖本工具自生成报告的常见排版，正确、可预测，而非 100% 兼容 CommonMark。
 * 渲染（JavaFX 节点构建）交由 com.bempdiff.ui.ReportViewer 完成。
 */
public final class MarkdownParser {

    private MarkdownParser() {
    }

    public enum BlockType {
        HEADING, PARAGRAPH, CODE, LIST, TABLE, HR, QUOTE
    }

    /** 一个已识别的 Markdown 块。 */
    public static final class Block {
        public BlockType type;
        public int headingLevel;            // HEADING 专用
        public String headingText;          // HEADING / QUOTE 复用：标题文本 / 引用文本（可能含 \n）
        public String codeLang;             // CODE 专用：围栏后的语言标识
        public String codeText;             // CODE 专用：原始代码
        public boolean ordered;             // LIST 专用：是否整体有序（按首个条目判定）
        public List<ListItem> items = new ArrayList<>();      // LIST 专用
        public List<String> tableHeader = new ArrayList<>();  // TABLE 专用
        public List<List<String>> tableRows = new ArrayList<>(); // TABLE 专用：数据行

        public Block(BlockType type) {
            this.type = type;
        }
    }

    /** 列表项。indent 为前导空格数，用于渲染时呈现嵌套层级。 */
    public static final class ListItem {
        public final int indent;
        public final boolean ordered;
        public String content;

        public ListItem(int indent, boolean ordered, String content) {
            this.indent = indent;
            this.ordered = ordered;
            this.content = content;
        }
    }

    /** 行内 token。 */
    public static final class InlineToken {
        public enum Kind { TEXT, BOLD, ITALIC, CODE }

        public final Kind kind;
        public final String value;

        public InlineToken(Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    // ------------------------------------------------------------------
    // 顶层解析
    // ------------------------------------------------------------------

    public static List<Block> parse(String md) {
        List<Block> blocks = new ArrayList<>();
        if (md == null || md.isEmpty()) {
            return blocks;
        }
        List<String> lines = readLines(md);
        int i = 0;
        int n = lines.size();
        while (i < n) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            // 1) 围栏代码块（最高优先级，内部内容原样保留，不解析 --- / | 等）
            if (trimmed.startsWith("```")) {
                String lang = trimmed.substring(3).trim();
                List<String> code = new ArrayList<>();
                i++;
                while (i < n) {
                    if (lines.get(i).trim().startsWith("```")) {
                        i++;
                        break;
                    }
                    code.add(lines.get(i));
                    i++;
                }
                Block b = new Block(BlockType.CODE);
                b.codeLang = lang;
                b.codeText = String.join("\n", code);
                blocks.add(b);
                continue;
            }

            // 2) 标题
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && (trimmed.length() == level || trimmed.charAt(level) == ' ')) {
                    Block b = new Block(BlockType.HEADING);
                    b.headingLevel = level;
                    b.headingText = trimmed.substring(level).trim();
                    blocks.add(b);
                    i++;
                    continue;
                }
            }

            // 3) 表格（当前行含 |，且下一行是分隔行）
            if (trimmed.contains("|") && i + 1 < n && isTableSeparator(lines.get(i + 1))) {
                Block b = new Block(BlockType.TABLE);
                b.tableHeader = splitRow(trimmed);
                i += 2; // 跳过表头行与分隔行
                while (i < n) {
                    String t = lines.get(i).trim();
                    if (t.isEmpty() || t.startsWith("```") || !t.contains("|")) {
                        break;
                    }
                    b.tableRows.add(splitRow(t));
                    i++;
                }
                blocks.add(b);
                continue;
            }

            // 4) 分隔线
            if (isHr(trimmed)) {
                blocks.add(new Block(BlockType.HR));
                i++;
                continue;
            }

            // 5) 列表
            if (isListItem(trimmed)) {
                Block b = new Block(BlockType.LIST);
                while (i < n) {
                    String raw = lines.get(i);
                    String t = raw.trim();
                    if (t.isEmpty()) {
                        break;
                    }
                    if (isListItem(t)) {
                        int indent = leadingSpaces(raw);
                        boolean ord = t.matches("^\\d+\\.\\s+.*");
                        String content = ord
                                ? t.replaceFirst("^\\d+\\.\\s+", "")
                                : t.replaceFirst("^[-*]\\s+", "");
                        b.items.add(new ListItem(indent, ord, content.trim()));
                        if (b.items.size() == 1) {
                            b.ordered = ord;
                        }
                    } else if (isContinuation(raw)) {
                        if (!b.items.isEmpty()) {
                            ListItem last = b.items.get(b.items.size() - 1);
                            last.content += " " + t;
                        }
                    } else {
                        break;
                    }
                    i++;
                }
                blocks.add(b);
                continue;
            }

            // 6) 引用块
            if (trimmed.startsWith(">")) {
                List<String> q = new ArrayList<>();
                while (i < n && lines.get(i).trim().startsWith(">")) {
                    q.add(lines.get(i).trim().replaceFirst("^>\\s?", ""));
                    i++;
                }
                Block b = new Block(BlockType.QUOTE);
                b.headingText = String.join("\n", q);
                blocks.add(b);
                continue;
            }

            // 7) 段落（聚合相邻普通行）
            List<String> para = new ArrayList<>();
            while (i < n) {
                String t = lines.get(i).trim();
                if (t.isEmpty()) {
                    break;
                }
                if (t.startsWith("#") || isHr(t) || t.startsWith("```") || isListItem(t)
                        || (t.contains("|") && i + 1 < n && isTableSeparator(lines.get(i + 1)))
                        || t.startsWith(">")) {
                    break;
                }
                para.add(t);
                i++;
            }
            Block b = new Block(BlockType.PARAGRAPH);
            b.headingText = String.join(" ", para);
            blocks.add(b);
        }
        return blocks;
    }

    // ------------------------------------------------------------------
    // 行内解析：**粗体** / *斜体* / `行内代码`
    // ------------------------------------------------------------------

    public static List<InlineToken> parseInline(String s) {
        List<InlineToken> toks = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return toks;
        }
        StringBuilder buf = new StringBuilder();
        int idx = 0;
        int len = s.length();
        while (idx < len) {
            char c = s.charAt(idx);
            // 行内代码
            if (c == '`') {
                if (buf.length() > 0) {
                    toks.add(new InlineToken(InlineToken.Kind.TEXT, buf.toString()));
                    buf.setLength(0);
                }
                int end = s.indexOf('`', idx + 1);
                if (end < 0) {
                    buf.append(c);
                    idx++;
                    continue;
                }
                toks.add(new InlineToken(InlineToken.Kind.CODE, s.substring(idx + 1, end)));
                idx = end + 1;
                continue;
            }
            // 粗体 **
            if (c == '*' && idx + 1 < len && s.charAt(idx + 1) == '*') {
                if (buf.length() > 0) {
                    toks.add(new InlineToken(InlineToken.Kind.TEXT, buf.toString()));
                    buf.setLength(0);
                }
                int end = s.indexOf("**", idx + 2);
                if (end < 0) {
                    buf.append("**");
                    idx += 2;
                    continue;
                }
                toks.add(new InlineToken(InlineToken.Kind.BOLD, s.substring(idx + 2, end)));
                idx = end + 2;
                continue;
            }
            // 斜体 *
            if (c == '*') {
                if (buf.length() > 0) {
                    toks.add(new InlineToken(InlineToken.Kind.TEXT, buf.toString()));
                    buf.setLength(0);
                }
                int end = s.indexOf('*', idx + 1);
                if (end < 0) {
                    buf.append(c);
                    idx++;
                    continue;
                }
                toks.add(new InlineToken(InlineToken.Kind.ITALIC, s.substring(idx + 1, end)));
                idx = end + 1;
                continue;
            }
            buf.append(c);
            idx++;
        }
        if (buf.length() > 0) {
            toks.add(new InlineToken(InlineToken.Kind.TEXT, buf.toString()));
        }
        return toks;
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static List<String> readLines(String md) {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.StringReader(md))) {
            String l;
            while ((l = br.readLine()) != null) {
                lines.add(l);
            }
        } catch (java.io.IOException e) {
            // StringReader 不会抛 IO 异常，这里仅为编译完备
            throw new RuntimeException(e);
        }
        return lines;
    }

    private static boolean isTableSeparator(String line) {
        String t = line.trim();
        if (!t.contains("-")) {
            return false;
        }
        String s = t.replace("|", "").replace(" ", "");
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '-' && c != ':') {
                return false;
            }
        }
        return s.contains("-");
    }

    private static boolean isHr(String t) {
        String s = t.replace(" ", "");
        if (s.length() < 3) {
            return false;
        }
        char first = s.charAt(0);
        if (first != '-' && first != '*' && first != '_') {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static boolean isListItem(String t) {
        return t.matches("^[-*]\\s+.*") || t.matches("^\\d+\\.\\s+.*");
    }

    /** 缩进的非列表、非空行，视为上一列表项的续行。 */
    private static boolean isContinuation(String raw) {
        String t = raw.trim();
        if (t.isEmpty() || isListItem(t)) {
            return false;
        }
        return leadingSpaces(raw) > 0;
    }

    private static int leadingSpaces(String line) {
        int c = 0;
        while (c < line.length() && line.charAt(c) == ' ') {
            c++;
        }
        return c;
    }

    private static List<String> splitRow(String t) {
        String s = t.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        String[] parts = s.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String p : parts) {
            cells.add(p.trim());
        }
        return cells;
    }
}
