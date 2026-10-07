package com.bempdiff.test;

import com.bempdiff.report.MarkdownParser;
import com.bempdiff.report.MarkdownParser.Block;
import com.bempdiff.report.MarkdownParser.BlockType;
import com.bempdiff.report.MarkdownParser.InlineToken;
import com.bempdiff.report.MarkdownParser.ListItem;

import java.util.List;

/**
 * MarkdownParser 纯逻辑单测：标题层级、段落合并、围栏代码块、有序/无序列表与嵌套续行、
 * GFM 表格、分隔线、引用块、行内粗体/斜体/行内码（含未闭合兜底）、空与畸形输入。
 *
 * <p>注意：parse 对「以 # 开头但非合法标题」的行会无限循环（生产代码缺陷，本任务禁止改 src），
 * 故本类不构造 7 个井号 / "#无空格" 之类输入，相关分支只在报告中说明未覆盖。</p>
 */
public final class MarkdownParserTest {

    private static List<Block> parse(String md) {
        return MarkdownParser.parse(md);
    }

    private static void assertType(String msg, List<Block> bs, int i, BlockType expected) {
        Asserts.assertEquals(msg, expected, bs.get(i).getType());
    }

    // --------------------------- 顶层：空/异常 ---------------------------

    public void testParse_nullAndEmpty() {
        Asserts.assertEquals("null 文档应返回空块表", 0, MarkdownParser.parse(null).size());
        Asserts.assertEquals("空串应返回空块表", 0, MarkdownParser.parse("").size());
    }

    // --------------------------- 标题 ---------------------------

    public void testParse_headingsAllLevels() {
        List<Block> bs = parse("# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6");
        Asserts.assertEquals("六级标题应产出 6 块", 6, bs.size());
        for (int i = 0; i < 6; i++) {
            assertType("第" + (i + 1) + "块应为 HEADING", bs, i, BlockType.HEADING);
            Asserts.assertEquals("层级应为 " + (i + 1), i + 1, bs.get(i).getHeadingLevel());
        }
        Asserts.assertEquals("一级标题文本", "H1", bs.get(0).getHeadingText());
        Asserts.assertEquals("六级标题文本", "H6", bs.get(5).getHeadingText());
    }

    public void testParse_headingOnlyMarker_emptyText() {
        // 仅井号、无正文：isHeadingLine 的 length==level 分支，标题文本为空。
        List<Block> bs = parse("#\n######");
        Asserts.assertEquals("两个空标题块", 2, bs.size());
        Asserts.assertEquals("首个井号→级别1", 1, bs.get(0).getHeadingLevel());
        Asserts.assertEquals("首个井号→空文本", "", bs.get(0).getHeadingText());
        Asserts.assertEquals("六个井号→级别6", 6, bs.get(1).getHeadingLevel());
    }

    // --------------------------- 段落 ---------------------------

    public void testParse_paragraphMergesAdjacentLines() {
        List<Block> bs = parse("line one\nline two\nline three");
        Asserts.assertEquals("相邻非空行合并为一段", 1, bs.size());
        assertType("段落类型", bs, 0, BlockType.PARAGRAPH);
        Asserts.assertEquals("以空格拼接", "line one line two line three", bs.get(0).getHeadingText());
    }

    public void testParse_standaloneSeparatorLineIsParagraph() {
        // 仅有分隔符样式一行、下一行为空：不构成表格（缺表头），落入段落。
        List<Block> bs = parse("|---|---|\n");
        Asserts.assertEquals("单行分隔符不成表→段落", 1, bs.size());
        assertType("段落", bs, 0, BlockType.PARAGRAPH);
    }

    // --------------------------- 代码块 ---------------------------

    public void testParse_codeBlock_withLang() {
        List<Block> bs = parse("```java\nint x = 1;\nint y = 2;\n```");
        Asserts.assertEquals("围栏代码块→单 CODE", 1, bs.size());
        assertType("代码块", bs, 0, BlockType.CODE);
        Asserts.assertEquals("语言标识", "java", bs.get(0).getCodeLang());
        Asserts.assertEquals("代码原文按行拼接", "int x = 1;\nint y = 2;", bs.get(0).getCodeText());
    }

    public void testParse_codeBlock_noLangAndUnclosed() {
        List<Block> bs = parse("```\nfoo\nbar");
        Asserts.assertEquals("未闭合围栏仍产出一块", 1, bs.size());
        assertType("代码块", bs, 0, BlockType.CODE);
        Asserts.assertEquals("无语言→空串", "", bs.get(0).getCodeLang());
        Asserts.assertEquals("未闭合→原样到结尾", "foo\nbar", bs.get(0).getCodeText());
    }

    public void testParse_codeBlock_keepsSpecialCharsRaw() {
        // 代码块内的破折号与竖线不应被识别为分隔线/表格（原样保留）。
        List<Block> bs = parse("```\n---\n| a | b |\n```");
        Asserts.assertEquals("应单块", 1, bs.size());
        assertType("代码块", bs, 0, BlockType.CODE);
        Asserts.assertEquals("内部特殊行原样保留", "---\n| a | b |", bs.get(0).getCodeText());
    }

    // --------------------------- 列表 ---------------------------

    public void testParse_listUnordered() {
        List<Block> bs = parse("- a\n- b\n* c");
        Asserts.assertEquals("应单块", 1, bs.size());
        assertType("列表", bs, 0, BlockType.LIST);
        Asserts.assertFalse("以短横起始判为无序", bs.get(0).isOrdered());
        List<ListItem> items = bs.get(0).getItems();
        Asserts.assertEquals("三项", 3, items.size());
        Asserts.assertEquals("内容 a", "a", items.get(0).getContent());
        Asserts.assertEquals("内容 c", "c", items.get(2).getContent());
        Asserts.assertEquals("首项 indent 0", 0, items.get(0).getIndent());
        Asserts.assertFalse("首项非有序", items.get(0).isOrdered());
    }

    public void testParse_listOrdered_firstItemDecidesOrder() {
        // 首项有序 → 整体 ordered=true；即便后续出现无序项也保持整体有序。
        List<Block> bs = parse("1. first\n- second");
        Asserts.assertEquals("应单块", 1, bs.size());
        Asserts.assertTrue("首项判定为有序", bs.get(0).isOrdered());
        List<ListItem> items = bs.get(0).getItems();
        Asserts.assertEquals("两项", 2, items.size());
        Asserts.assertEquals("去序号后的内容", "first", items.get(0).getContent());
        Asserts.assertTrue("首项 isOrdered", items.get(0).isOrdered());
        Asserts.assertEquals("无序项内容", "second", items.get(1).getContent());
        Asserts.assertFalse("第二项 isOrdered=false", items.get(1).isOrdered());
    }

    public void testParse_listIndentedContinuationAppends() {
        List<Block> bs = parse("- parent\n  child text");
        Asserts.assertEquals("应单块", 1, bs.size());
        List<ListItem> items = bs.get(0).getItems();
        Asserts.assertEquals("续行并入上一项→单项", 1, items.size());
        Asserts.assertEquals("续行以空格追加", "parent child text", items.get(0).getContent());
    }

    public void testParse_listNestedIndent() {
        List<Block> bs = parse("- a\n  - b");
        List<ListItem> items = bs.get(0).getItems();
        Asserts.assertEquals("嵌套项作为独立项", 2, items.size());
        Asserts.assertEquals("子项缩进 2", 2, items.get(1).getIndent());
        Asserts.assertEquals("内容 b", "b", items.get(1).getContent());
    }

    // --------------------------- 表格 ---------------------------

    public void testParse_table_withPipes() {
        List<Block> bs = parse("| 名称 | 值 |\n|---|---|\n| a | 1 |\n| b | 2 |");
        Asserts.assertEquals("应单块", 1, bs.size());
        assertType("表格", bs, 0, BlockType.TABLE);
        Asserts.assertEquals("表头两列", 2, bs.get(0).getTableHeader().size());
        Asserts.assertEquals("名称", "名称", bs.get(0).getTableHeader().get(0));
        Asserts.assertEquals("两数据行", 2, bs.get(0).getTableRows().size());
        Asserts.assertEquals("1", "1", bs.get(0).getTableRows().get(0).get(1));
    }

    public void testParse_table_withoutOuterPipes() {
        // 无首尾竖线的 GFM 表：splitRow 会去掉首尾竖线后按竖线切分并 trim。
        List<Block> bs = parse("h1 | h2\n--- | ---\nv1 | v2");
        Asserts.assertEquals("应单块", 1, bs.size());
        assertType("表格", bs, 0, BlockType.TABLE);
        Asserts.assertEquals("h1", "h1", bs.get(0).getTableHeader().get(0));
        Asserts.assertEquals("h2", "h2", bs.get(0).getTableHeader().get(1));
        Asserts.assertEquals("v1", "v1", bs.get(0).getTableRows().get(0).get(0));
    }

    public void testParse_tableAlignmentColonSeparator() {
        // 分隔行可含冒号（对齐标记）仍识别为表格。
        List<Block> bs = parse("| a | b |\n|:---|---:|\n| 1 | 2 |");
        assertType("表格", bs, 0, BlockType.TABLE);
        Asserts.assertEquals("一行数据", 1, bs.get(0).getTableRows().size());
    }

    public void testParse_tableStopsOnNonPipeLine() {
        List<Block> bs = parse("| a | b |\n|---|---|\n| 1 | 2 |\nafter paragraph");
        Asserts.assertEquals("表格后接普通段落", 2, bs.size());
        assertType("表格", bs, 0, BlockType.TABLE);
        assertType("段落", bs, 1, BlockType.PARAGRAPH);
    }

    // --------------------------- 分隔线 ---------------------------

    public void testParse_hrVariants() {
        List<Block> bs = parse("---\n***\n___\n- - -\n*  *  *");
        Asserts.assertEquals("五种写法均应判 HR", 5, bs.size());
        for (int i = 0; i < 5; i++) assertType("第" + i + "块 HR", bs, i, BlockType.HR);
    }

    // --------------------------- 引用块 ---------------------------

    public void testParse_quoteMergesAndStripsPrefix() {
        List<Block> bs = parse("> hello\n> world\nnot quote");
        Asserts.assertEquals("引用+后续段落两块", 2, bs.size());
        assertType("引用", bs, 0, BlockType.QUOTE);
        Asserts.assertEquals("去前缀后按换行聚合", "hello\nworld", bs.get(0).getHeadingText());
        assertType("段落", bs, 1, BlockType.PARAGRAPH);
    }

    public void testParse_quoteWithoutSpaceAfterGt() {
        List<Block> bs = parse(">tight");
        assertType("引用", bs, 0, BlockType.QUOTE);
        Asserts.assertEquals("紧接前导符无空格也去前缀", "tight", bs.get(0).getHeadingText());
    }

    // --------------------------- 混合文档 ---------------------------

    public void testParse_mixedDocumentSequence() {
        List<Block> bs = parse("# 标题\n\n一段正文\n\n- 列表项\n\n> 引用\n\n```js\ncode\n```\n\n| a |\n|---|\n| 1 |\n\n---");
        Asserts.assertEquals("混合文档应产出 7 块", 7, bs.size());
        assertType("标题", bs, 0, BlockType.HEADING);
        assertType("段落", bs, 1, BlockType.PARAGRAPH);
        assertType("列表", bs, 2, BlockType.LIST);
        assertType("引用", bs, 3, BlockType.QUOTE);
        assertType("代码", bs, 4, BlockType.CODE);
        assertType("表格", bs, 5, BlockType.TABLE);
        assertType("分隔线", bs, 6, BlockType.HR);
    }

    // --------------------------- 行内解析 ---------------------------

    public void testParseInline_nullEmpty() {
        Asserts.assertEquals("null→空", 0, MarkdownParser.parseInline(null).size());
        Asserts.assertEquals("空→空", 0, MarkdownParser.parseInline("").size());
    }

    public void testParseInline_boldItalicCode() {
        List<InlineToken> toks = MarkdownParser.parseInline("a **b** c *d* e `f` g");
        Asserts.assertEquals("应产出 7 个 token", 7, toks.size());
        Asserts.assertEquals("首 TEXT", InlineToken.Kind.TEXT, toks.get(0).kind);
        Asserts.assertEquals("a ", "a ", toks.get(0).value);
        Asserts.assertEquals("BOLD", InlineToken.Kind.BOLD, toks.get(1).kind);
        Asserts.assertEquals("b", "b", toks.get(1).value);
        Asserts.assertEquals("ITALIC", InlineToken.Kind.ITALIC, toks.get(3).kind);
        Asserts.assertEquals("d", "d", toks.get(3).value);
        Asserts.assertEquals("CODE", InlineToken.Kind.CODE, toks.get(5).kind);
        Asserts.assertEquals("f", "f", toks.get(5).value);
        Asserts.assertEquals("尾 TEXT", InlineToken.Kind.TEXT, toks.get(6).kind);
    }

    public void testParseInline_unclosedMarkersAreLiteral() {
        Asserts.assertEquals("未闭合粗体→字面", "**open", only(MarkdownParser.parseInline("**open")));
        Asserts.assertEquals("未闭合斜体→字面", "*open", only(MarkdownParser.parseInline("*open")));
        Asserts.assertEquals("未闭合行内码→字面", "`open", only(MarkdownParser.parseInline("`open")));
    }

    public void testParseInline_emptyCodeToken() {
        List<InlineToken> toks = MarkdownParser.parseInline("``");
        Asserts.assertEquals("双反引号→空 CODE token", 1, toks.size());
        Asserts.assertEquals("CODE", InlineToken.Kind.CODE, toks.get(0).kind);
        Asserts.assertEquals("空值", "", toks.get(0).value);
    }

    public void testParseInline_plainTextOnly() {
        List<InlineToken> toks = MarkdownParser.parseInline("no markers here");
        Asserts.assertEquals("单 token", 1, toks.size());
        Asserts.assertEquals("TEXT", InlineToken.Kind.TEXT, toks.get(0).kind);
        Asserts.assertEquals("no markers here", "no markers here", toks.get(0).value);
    }

    private static String only(List<InlineToken> toks) {
        Asserts.assertEquals("应仅一个 TEXT token", 1, toks.size());
        Asserts.assertEquals("TEXT", InlineToken.Kind.TEXT, toks.get(0).kind);
        return toks.get(0).value;
    }
}
