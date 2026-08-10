package com.bempdiff.test;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.report.MarkdownReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 报告导出测试：章节完整性、删除类→破坏性变更清单、Top-K 限制、writeToFile 自动建目录。 */
public final class ReportTest {

    private static PackageSnapshot snap(String version) {
        return new PackageSnapshot(Path.of("dummy-" + version + ".war"), null, version, new LinkedHashMap<>());
    }

    private static DiffResult buildDiff() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "a.class");
        r.put(DiffStatus.ADDED, "b.class");
        r.put(DiffStatus.DELETED, "c.class");
        return r;
    }

    private static DiffStats buildStats() {
        DiffStats s = new DiffStats();
        s.setAdded(1); s.setDeleted(1); s.setModified(1); s.setUnchanged(0);
        s.setBizChanged(3); s.setJarChanged(0);
        return s;
    }

    public void testRender_sectionsComplete() {
        MarkdownReport rep = new MarkdownReport(15);
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", "diff-a", "cfr", "", true));
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), decompiled);

        Asserts.assertContains("标题", md, "# 差异分析报告");
        Asserts.assertContains("一、差异统计", md, "## 一、差异统计");
        Asserts.assertContains("新增统计", md, "新增 **1**");
        Asserts.assertContains("二、差异文件树", md, "## 二、差异文件树");
        Asserts.assertContains("三、反编译差异", md, "## 三、反编译源码级差异");
        Asserts.assertContains("四、破坏性变更", md, "## 四、破坏性变更清单（删除类）");
        Asserts.assertContains("删除类应入破坏性清单", md, "c.class");
        Asserts.assertContains("五、审计摘要", md, "## 五、审计摘要");
        Asserts.assertContains("老包版本", md, "1.0");
        Asserts.assertContains("新包版本", md, "2.0");
    }

    public void testRender_topKLimit() {
        MarkdownReport rep = new MarkdownReport(2);
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", "da", "cfr", "", true));
        decompiled.put("b.class", new DecompiledUnit("b.class", "o", "n", "db", "cfr", "", true));
        decompiled.put("c.class", new DecompiledUnit("c.class", "o", "n", "dc", "cfr", "", true));
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), decompiled);
        int count = countOccurrences(md, "### ");
        Asserts.assertEquals("Top-K=2 应只展示 2 个文件 diff", 2, count);
    }

    public void testWriteToFile_createsParentDirs() throws IOException {
        MarkdownReport rep = new MarkdownReport(15);
        Path dir = Files.createTempDirectory("bdreport");
        Path out = dir.resolve("nested").resolve("sub").resolve("report.md"); // 父目录原本不存在
        rep.writeToFile(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), new LinkedHashMap<>(), out);
        Asserts.assertTrue("报告文件应被创建", Files.exists(out));
        Asserts.assertTrue("报告不应为空", Files.size(out) > 0);
    }

    private static int countOccurrences(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
        return c;
    }
}
