package com.bempdiff.test;

import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.diff.LineDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端源码（JS/HTML/CSS）对比与分析测试：
 *  - 分类路由（.js/.html/.css → 新枚举；回归 .class/.png 不变）
 *  - 压缩 JS 美化（单行 → 多行）
 *  - 前端文本 diff（修改/新增/删除语义）
 *  - 候选收集（collectFrontendTextCandidates）
 */
public final class FrontendTest {

    private final PackageParser parser = new PackageParser();

    public void testClassify_frontendRouting() {
        Asserts.assertEquals(".js → JS", FileClass.JS, PackageParser.classify("static/app.js"));
        Asserts.assertEquals(".html → HTML", FileClass.HTML, PackageParser.classify("WEB-INF/views/a.html"));
        Asserts.assertEquals(".htm → HTML", FileClass.HTML, PackageParser.classify("x.htm"));
        Asserts.assertEquals(".css → CSS", FileClass.CSS, PackageParser.classify("static/a.css"));
        Asserts.assertEquals("回归: .class 仍是 CLASS", FileClass.CLASS, PackageParser.classify("com/x/A.class"));
        Asserts.assertEquals("回归: .png 仍是 STATIC", FileClass.STATIC, PackageParser.classify("img/logo.png"));
        Asserts.assertEquals("回归: .jsp 仍是 STATIC", FileClass.STATIC, PackageParser.classify("index.jsp"));
        Asserts.assertTrue(".js 属于前端文本", FileClass.JS.isFrontendText());
        Asserts.assertFalse(".class 不属于前端文本", FileClass.CLASS.isFrontendText());
    }

    public void testLooksMinified() {
        Asserts.assertTrue("单行超长应判为压缩", FrontendTextDiff.looksMinified(repeat("function f(){return 1;}", 40)));
        Asserts.assertFalse("多行可读不应判为压缩",
                FrontendTextDiff.looksMinified("function f() {\n  return 1;\n}\n"));
    }

    public void testBeautifyJs_minified() {
        String min = repeat("function f(){var x=1;return x+1;}", 20); // 单行超长
        String pretty = FrontendTextDiff.beautify(min, FileClass.JS);
        Asserts.assertTrue("压缩 JS 美化后应为多行", pretty.contains("\n"));
        Asserts.assertTrue("美化后应保留函数定义", pretty.contains("function f()"));
        Asserts.assertTrue("美化后语句尾应有换行（含 return 后换行）", pretty.contains("return x+1;"));
    }

    public void testBeautifyHtml_minified() {
        String min = "<div><span> a </span><p> b </p></div><ul><li>c</li></ul>";
        String pretty = FrontendTextDiff.beautify(min, FileClass.HTML);
        Asserts.assertTrue("压缩 HTML 美化后应拆标签边界为换行", pretty.contains(">\n<"));
    }

    public void testFrontendDiff_modified() throws IOException {
        String oldJs = "function calc(){var a=1;return a+1;}function other(){return 2;}";
        String newJs = "function calc(){var a=1;return a+1;}function other(){return 99;}";
        PackageSnapshot oldSnap = parser.parse(warWith("static/app.js", oldJs), new com.bempdiff.config.ParseConfig(), false);
        PackageSnapshot newSnap = parser.parse(warWith("static/app.js", newJs), new com.bempdiff.config.ParseConfig(), false);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);
        Asserts.assertTrue("app.js 应为 MODIFIED", r.get(DiffStatus.MODIFIED).contains("static/app.js"));

        LogicalEntry oe = oldSnap.getEntries().get("static/app.js");
        LogicalEntry ne = newSnap.getEntries().get("static/app.js");
        DecompiledUnit u = new FrontendTextDiff().diff(oldSnap, newSnap, oe, ne, "static/app.js", FileClass.JS);
        Asserts.assertTrue("前端 diff 应成功", u.isOk());
        Asserts.assertNotNull("diffText 不应为空", u.getDiffText());
        Asserts.assertContains("diff 应体现旧值 return 2", u.getDiffText(), "return 2");
        Asserts.assertContains("diff 应体现新值 return 99", u.getDiffText(), "return 99");
        Asserts.assertContains("diff 应含删除行 -", u.getDiffText(), "- ");
        Asserts.assertContains("diff 应含新增行 +", u.getDiffText(), "+ ");
        Asserts.assertNotNull("newSource 应可用", u.getNewSource());
        Asserts.assertContains("newSource 应含新值", u.getNewSource(), "return 99");
    }

    public void testFrontendDiff_added() throws IOException {
        String newJs = "function brandNew(){return 'hi';}";
        PackageSnapshot oldSnap = parser.parse(warWith("static/old.js", "x"), new com.bempdiff.config.ParseConfig(), false);
        PackageSnapshot newSnap = parser.parse(warWithMulti(
                Map.of("static/old.js", "x", "static/new.js", newJs)),
                new com.bempdiff.config.ParseConfig(), false);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);
        Asserts.assertTrue("new.js 应为 ADDED", r.get(DiffStatus.ADDED).contains("static/new.js"));
        LogicalEntry ne = newSnap.getEntries().get("static/new.js");
        DecompiledUnit u = new FrontendTextDiff().diff(oldSnap, newSnap, null, ne, "static/new.js", FileClass.JS);
        Asserts.assertContains("新增文件应标注", u.getDiffText(), "[新增文件]");
        Asserts.assertNull("新增文件 oldSource 为 null", u.getOldSource());
        Asserts.assertNotNull("新增文件 newSource 可用", u.getNewSource());
    }

    public void testCollectFrontendCandidates() throws IOException {
        String oldJs = "function a(){return 1;}";
        String newJs = "function a(){return 2;}";
        PackageSnapshot oldSnap = parser.parse(warWith("static/app.js", oldJs), new com.bempdiff.config.ParseConfig(), false);
        PackageSnapshot newSnap = parser.parse(warWith("static/app.js", newJs), new com.bempdiff.config.ParseConfig(), false);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);
        List<String> cands = DiffEngine.collectFrontendTextCandidates(r, oldSnap, newSnap);
        Asserts.assertTrue("候选应包含 app.js", cands.contains("static/app.js"));
    }

    public void testLineDiff_basic() {
        List<String> a = LineDiff.lines("a\nb\nc");
        List<String> b = LineDiff.lines("a\nB\nc");
        String d = LineDiff.unified(a, b);
        Asserts.assertContains("应含删除 a 行?", d, "  a"); // a 未变 → 空格前缀
        Asserts.assertContains("应含删除 b", d, "- b");
        Asserts.assertContains("应含新增 B", d, "+ B");
    }

    // ---- 夹具 ----

    private java.nio.file.Path warWith(String name, String content) throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(name, content.getBytes(StandardCharsets.UTF_8));
        return TestFixtures.writePackage(TestFixtures.makeWar(e, "1.0"), "fe-" + name.hashCode());
    }

    private java.nio.file.Path warWithMulti(Map<String, String> entries) throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : entries.entrySet()) {
            e.put(en.getKey(), en.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return TestFixtures.writePackage(TestFixtures.makeWar(e, "1.0"), "fe-multi");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
