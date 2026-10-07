package com.bempdiff.test;

import com.bempdiff.diff.DiffRules;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link FrontendTextDiff} 未测分支补充：{@code beautify} 对 null / 超大输入（>2,000,000 字符）的提前返回、
 * 各 FileClass 的引擎标签（含 null 与 default），{@code beautifyJs} 非压缩回退 + 压缩态字符状态机
 * （单/双/反引号字符串、转义、块注释、行注释、花括号缩进），{@code beautifyCss} 压缩/非压缩两路，
 * {@code beautifyJsp} 标签与脚本边界断行，{@code diffBytes} 双侧 null 占位哈希，
 * 以及 {@code diff} 读条目抛 IOException 时的 catch→fail 兜底。
 * 与现有 FrontendTest 互补，不编辑该文件。
 */
public final class FrontendTextDiffBranchTest {

    private static final int OVER = 2_000_001; // > BEAUTIFY_MAX_CHARS(2_000_000)

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // ---------------- beautify：null 与超大输入提前返回 ----------------

    public void testBeautify_nullReturnsNull() {
        Asserts.assertNull("null 输入应返回 null", FrontendTextDiff.beautify(null, FileClass.JS));
    }

    public void testBeautify_oversizedFallback() {
        char[] big = new char[OVER];
        java.util.Arrays.fill(big, 'a');
        String input = new String(big);
        // 超过上限：直接原样返回，不做任何美化（同一引用）
        String out = FrontendTextDiff.beautify(input, FileClass.CSS);
        Asserts.assertEquals("超大输入应原样返回（同引用）", input, out);
        Asserts.assertEquals("长度保持不变", OVER, out.length());
    }

    // ---------------- 引擎标签：经 diffBytes 暴露各 FileClass 分支 ----------------

    public void testEngineLabels_allFileClasses() {
        byte[] b = "body{}".getBytes(StandardCharsets.UTF_8);
        Asserts.assertEquals("null FileClass → text-diff", "text-diff",
                new FrontendTextDiff().diffBytes(b, b, "k", null, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("CSS → css-beautify", "css-beautify",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.CSS, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("HTML → html-beautify", "html-beautify",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.HTML, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("JSP → jsp-normalize", "jsp-normalize",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.JSP, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("CONFIG → text-normalize", "text-normalize",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.CONFIG, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("JS → js-beautify", "js-beautify",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.JS, DiffRules.DEFAULT).getEngine());
        Asserts.assertEquals("未知前端(STATIC) → js-beautify(default)", "js-beautify",
                new FrontendTextDiff().diffBytes(b, b, "k", FileClass.STATIC, DiffRules.DEFAULT).getEngine());
    }

    // ---------------- beautifyJs：非压缩回退到换行归一 ----------------

    public void testBeautifyJs_nonMinified_normalizesNewlines() {
        // 短、多行 → 非压缩：走 normalizeNewlines。混合 \r\n（命中 CR 分支）与「尾随空白+\n」（命中 LF 去空白分支）。
        String src = "function f() {\r\n  return 1;   \n}\r\n";
        String out = FrontendTextDiff.beautify(src, FileClass.JS);
        Asserts.assertNotContains("CRLF/CR 应归一为 LF", out, "\r");
        Asserts.assertContains("LF 前行尾多余空白应被去除", out, "  return 1;\n");
        Asserts.assertNotContains("不应保留尾随空白再换行", out, "1;   ");
    }

    // ---------------- beautifyJs：压缩态字符状态机（字符串/转义/注释/花括号） ----------------

    public void testBeautifyJs_minifiedStateMachine_stringAndCommentSafety() {
        // head 无换行：整体单行 → 判为压缩；重复以超过 400 字符触发美化。
        String head = "var a='q1;r'; var b=\"d2;s\"; var c=`t3;u`; /* blk1;blk2 */ var e='esc\\'end'; function z(){var q=1;} ";
        String js = repeat(head, 6) + "// tailc;omment";
        Asserts.assertTrue("构造应为压缩单行", FrontendTextDiff.looksMinified(js));
        String out = FrontendTextDiff.beautify(js, FileClass.JS);
        Asserts.assertContains("反引号内分号不应被当断点", out, "`t3;u`");
        Asserts.assertContains("单引号内分号保留", out, "q1;r");
        Asserts.assertContains("双引号内分号保留", out, "d2;s");
        Asserts.assertContains("块注释内容保留", out, "blk1;blk2");
        Asserts.assertContains("转义引号被吞入字符串（未提前闭合）", out, "esc\\'end");
        Asserts.assertContains("行注释内容保留", out, "tailc;omment");
        Asserts.assertContains("函数体花括号应产生缩进换行", out, "\n");
    }

    // ---------------- beautifyCss：压缩 / 非压缩两路 ----------------

    public void testBeautifyCss_minified() {
        String head = "body{color:red;margin:0;} .x{b:'p;q';} .y{content:\"a;b\";} /* c1;c2 */ p{padding:1px;} ";
        String css = repeat(head, 6);
        Asserts.assertTrue("构造 CSS 应为压缩单行", FrontendTextDiff.looksMinified(css));
        String out = FrontendTextDiff.beautify(css, FileClass.CSS);
        Asserts.assertContains("声明分号后换行（保留 color:red;）", out, "color:red;");
        Asserts.assertContains("单引号字符串内分号保留", out, "'p;q'");
        Asserts.assertContains("双引号字符串内分号保留", out, "\"a;b\"");
        Asserts.assertContains("块注释内容保留", out, "c1;c2");
        Asserts.assertContains("美化后应为多行", out, "\n");
    }

    public void testBeautifyCss_nonMinified_normalizesNewlines() {
        String css = ".a { color: red; }  \r\n.b { margin: 0; }\r\n"; // 多行短样式，非压缩
        String out = FrontendTextDiff.beautify(css, FileClass.CSS);
        Asserts.assertNotContains("非压缩 CSS 仅归一换行，去 CRLF", out, "\r");
        Asserts.assertContains("保留声明内容", out, "color: red;");
    }

    // ---------------- beautifyJsp：标签与脚本片段边界断行 ----------------

    public void testBeautifyJsp_tagAndScriptBoundaries() {
        String jsp = "<div><% out.println(\"x\"); %>${user.name}<p>hi</p></div>";
        String out = FrontendTextDiff.beautify(jsp, FileClass.JSP);
        Asserts.assertContains("标签边界应断行", out, ">\n<");
        Asserts.assertContains("JSP 开始标签 <% 前应断行", out, "\n<%");
        Asserts.assertContains("JSP 结束标签 %> 后应断行", out, "%>\n");
    }

    // ---------------- diffBytes：双侧 null 的占位哈希 + 新增文件说明 ----------------

    public void testDiffBytes_bothNull_placeholderHashes() {
        DecompiledUnit u = new FrontendTextDiff().diffBytes(null, null, "gone.js", FileClass.JS, DiffRules.DEFAULT);
        Asserts.assertTrue("双侧缺失也应 ok（新增/删除语义）", u.isOk());
        Asserts.assertEquals("缺失侧 oldHash 占位", "0000000", u.getOldHash());
        Asserts.assertEquals("缺失侧 newHash 占位", "0000000", u.getNewHash());
        Asserts.assertContains("应标注新增文件", u.getDiffText(), "[新增文件]");
        Asserts.assertNull("oldSource 为 null", u.getOldSource());
    }

    // ---------------- diff()：读条目抛 IOException → catch → fail ----------------

    public void testDiff_ioExceptionFails() throws Exception {
        Path tmp = Files.createTempDirectory("ftd-fail");
        try {
            Path bogus = tmp.resolve("nope.war"); // 不存在：ZipFile 打开必抛
            Map<String, LogicalEntry> m = new LinkedHashMap<>();
            // innerEntry 非空 → 嵌套条目，跳过磁盘直读；底层文件不存在 → ZipFile 构造抛 IOException
            LogicalEntry le = new LogicalEntry("a.js", Layer.L0, FileClass.JS, 3, "sha",
                    new EntrySource("a.js", "a.js"));
            m.put("a.js", le);
            PackageSnapshot snap = new PackageSnapshot(bogus, PackageType.WAR, "1", m);
            DecompiledUnit u = new FrontendTextDiff().diff(snap, snap, le, le, "a.js", FileClass.JS);
            Asserts.assertFalse("读取异常应兜底为 fail", u.isOk());
            Asserts.assertNotNull("fail 应带错误信息", u.getError());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
