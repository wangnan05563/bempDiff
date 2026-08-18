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

    private static final String FENCE = "```";

    private MarkdownParser() {
    }

    public enum BlockType {
        HEADING, PARAGRAPH, CODE, LIST, TABLE, HR, QUOTE
    }

    /** 一个已识别的 Markdown 块。 */
    public static final class Block {
        private BlockType type;
        private int headingLevel;            // HEADING 专用
        private String headingText;          // HEADING / QUOTE 复用：标题文本 / 引用文本（可能含 \n）
        private String codeLang;             // CODE 专用：围栏后的语言标识
        private String codeText;             // CODE 专用：原始代码
        private boolean ordered;             // LIST 专用：是否整体有序（按首个条目判定）
        private final List<ListItem> items = new ArrayList<>();      // LIST 专用
        private final List<String> tableHeader = new ArrayList<>();  // TABLE 专用
        private final List<List<String>> tableRows = new ArrayList<>(); // TABLE 专用：数据行

        public Block(BlockType type) {
            this.type = type;
        }

        public BlockType getType() {
            return type;
        }

        public int getHeadingLevel() {
            return headingLevel;
        }

        public void setHeadingLevel(int headingLevel) {
            this.headingLevel = headingLevel;
        }

        public String getHeadingText() {
            return headingText;
        }

        public void setHeadingText(String headingText) {
            this.headingText = headingText;
        }

        public String getCodeLang() {
            return codeLang;
        }

        public void setCodeLang(String codeLang) {
            this.codeLang = codeLang;
        }

        public String getCodeText() {
            return codeText;
        }

        public void setCodeText(String codeText) {
            this.codeText = codeText;
        }

        public boolean isOrdered() {
            return ordered;
        }

        public void setOrdered(boolean ordered) {
            this.ordered = ordered;
        }

        public List<ListItem> getItems() {
            return items;
        }

        public List<String> getTableHeader() {
            return tableHeader;
        }

        public List<List<String>> getTableRows() {
            return tableRows;
        }
    }

    /** 列表项。indent 为前导空格数，用于渲染时呈现嵌套层级。 */
    public static final class ListItem {
        private final int indent;
        private final boolean ordered;
        private String content;

        public ListItem(int indent, boolean ordered, String content) {
            this.indent = indent;
            this.ordered = ordered;
            this.content = content;
        }

        public int getIndent() {
            return indent;
        }

        public boolean isOrdered() {
            return ordered;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
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
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                i++;
            } else if (trimmed.startsWith(FENCE)) {
                i = parseCodeBlock(lines, i, blocks);
            } else if (trimmed.startsWith("#") && isHeadingLine(trimmed)) {
                blocks.add(headingBlock(trimmed));
                i++;
            } else if (trimmed.contains("|") && i + 1 < n && isTableSeparator(lines.get(i + 1))) {
                i = parseTable(lines, i, blocks);
            } else if (isHr(trimmed)) {
                blocks.add(new Block(BlockType.HR));
                i++;
            } else if (isListItem(trimmed)) {
                i = parseList(lines, i, blocks);
            } else if (trimmed.startsWith(">")) {
                i = parseQuote(lines, i, blocks);
            } else {
                i = parseParagraph(lines, i, blocks);
            }
        }
        return blocks;
    }

    /** 围栏代码块：内部内容原样保留，不解析 --- / | 等。 */
    private static int parseCodeBlock(List<String> lines, int i, List<Block> blocks) {
        String lang = lines.get(i).trim().substring(3).trim();
        List<String> code = new ArrayList<>();
        i++;
        int n = lines.size();
        while (i < n) {
            if (lines.get(i).trim().startsWith(FENCE)) {
                i++;
                break;
            }
            code.add(lines.get(i));
            i++;
        }
        Block b = new Block(BlockType.CODE);
        b.setCodeLang(lang);
        b.setCodeText(String.join("\n", code));
        blocks.add(b);
        return i;
    }

    /** GFM 表格：当前行作为表头，其后连续的 | 行作为数据行。 */
    private static int parseTable(List<String> lines, int i, List<Block> blocks) {
        int n = lines.size();
        Block b = new Block(BlockType.TABLE);
        b.getTableHeader().addAll(splitRow(lines.get(i).trim()));
        i += 2; // 跳过表头行与分隔行
        while (i < n) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith(FENCE) || !t.contains("|")) {
                break;
            }
            b.getTableRows().add(splitRow(t));
            i++;
        }
        blocks.add(b);
        return i;
    }

    /** 列表：连续列表项聚合，首次项判定有序/无序，缩进的续行追加到上一项。 */
    private static int parseList(List<String> lines, int i, List<Block> blocks) {
        Block b = new Block(BlockType.LIST);
        int n = lines.size();
        while (i < n) {
            String raw = lines.get(i);
            String t = raw.trim();
            if (t.isEmpty() || (!isListItem(t) && !isContinuation(raw))) {
                break;
            }
            accumulateListItem(b, raw, t);
            i++;
        }
        blocks.add(b);
        return i;
    }

    /** 列表项聚合 (有序+无序判定+缩进续行追加)。 */
    private static void accumulateListItem(Block b, String raw, String t) {
        if (isListItem(t)) {
            int indent = leadingSpaces(raw);
            boolean ord = t.matches("^\\d+\\.\\s+.*");
            String content = ord
                    ? t.replaceFirst("^\\d+\\.\\s+", "")
                    : t.replaceFirst("^[-*]\\s+", "");
            ListItem item = new ListItem(indent, ord, content.trim());
            b.getItems().add(item);
            if (b.getItems().size() == 1) {
                b.setOrdered(ord);
            }
        } else {
            if (!b.getItems().isEmpty()) {
                ListItem last = b.getItems().get(b.getItems().size() - 1);
                last.setContent(last.getContent() + " " + t);
            }
        }
    }

    /** 引用块：以 > 开头的连续行聚合。 */
    private static int parseQuote(List<String> lines, int i, List<Block> blocks) {
        List<String> q = new ArrayList<>();
        int n = lines.size();
        while (i < n && lines.get(i).trim().startsWith(">")) {
            q.add(lines.get(i).trim().replaceFirst("^>\\s?", ""));
            i++;
        }
        Block b = new Block(BlockType.QUOTE);
        b.setHeadingText(String.join("\n", q));
        blocks.add(b);
        return i;
    }

    /** 段落：聚合相邻的普通非空行。 */
    private static int parseParagraph(List<String> lines, int i, List<Block> blocks) {
        List<String> para = new ArrayList<>();
        int n = lines.size();
        while (i < n) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || isParagraphBoundary(t, lines, i, n)) {
                break;
            }
            para.add(t);
            i++;
        }
        Block b = new Block(BlockType.PARAGRAPH);
        b.setHeadingText(String.join(" ", para));
        blocks.add(b);
        return i;
    }

    /** 判定某行是否属于其他元素结构，作为段落的停止条件。 */
    private static boolean isParagraphBoundary(String t, List<String> lines, int i, int n) {
        if (t.startsWith("#") || isHr(t) || t.startsWith(FENCE) || isListItem(t) || t.startsWith(">")) {
            return true;
        }
        return t.contains("|") && i + 1 < n && isTableSeparator(lines.get(i + 1));
    }

    private static Block headingBlock(String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        Block b = new Block(BlockType.HEADING);
        b.setHeadingLevel(level);
        b.setHeadingText(trimmed.substring(level).trim());
        return b;
    }

    /** 是否为合法标题行：# 数 1~6 且后随空格或行尾。 */
    private static boolean isHeadingLine(String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        return level <= 6 && (trimmed.length() == level || trimmed.charAt(level) == ' ');
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
            if (c == '`') {
                idx = parseInlineCode(s, idx, buf, toks);
            } else if (c == '*' && idx + 1 < len && s.charAt(idx + 1) == '*') {
                idx = parseInlineBold(s, idx, buf, toks);
            } else if (c == '*') {
                idx = parseInlineItalic(s, idx, buf, toks);
            } else {
                buf.append(c);
                idx++;
            }
        }
        flushToken(buf, toks);
        return toks;
    }

    /** 将积累的普通文本作为 TEXT token 提交。 */
    private static void flushToken(StringBuilder buf, List<InlineToken> toks) {
        if (buf.length() > 0) {
            toks.add(new InlineToken(InlineToken.Kind.TEXT, buf.toString()));
            buf.setLength(0);
        }
    }

    /** 行内代码：s[idx] 为 `。返回处理后的新下标。 */
    private static int parseInlineCode(String s, int idx, StringBuilder buf, List<InlineToken> toks) {
        flushToken(buf, toks);
        int end = s.indexOf('`', idx + 1);
        if (end < 0) {
            buf.append('`');
            return idx + 1;
        }
        toks.add(new InlineToken(InlineToken.Kind.CODE, s.substring(idx + 1, end)));
        return end + 1;
    }

    /** 粗体：s[idx..idx+1] 为 **。返回处理后的新下标。 */
    private static int parseInlineBold(String s, int idx, StringBuilder buf, List<InlineToken> toks) {
        flushToken(buf, toks);
        int end = s.indexOf("**", idx + 2);
        if (end < 0) {
            buf.append("**");
            return idx + 2;
        }
        toks.add(new InlineToken(InlineToken.Kind.BOLD, s.substring(idx + 2, end)));
        return end + 2;
    }

    /** 斜体：s[idx] 为 *。返回处理后的新下标。 */
    private static int parseInlineItalic(String s, int idx, StringBuilder buf, List<InlineToken> toks) {
        flushToken(buf, toks);
        int end = s.indexOf('*', idx + 1);
        if (end < 0) {
            buf.append('*');
            return idx + 1;
        }
        toks.add(new InlineToken(InlineToken.Kind.ITALIC, s.substring(idx + 1, end)));
        return end + 1;
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static List<String> readLines(String md) {
        List<String> lines = new ArrayList<>();
        md.lines().forEach(lines::add);
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