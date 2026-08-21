package com.bempdiff.test;

import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.LineDiff;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 差异计算测试：增/删/改/未变判定、统计（含业务/ jar 级拆分）、L1 class 候选收集。 */
public final class DiffTest {

    private static LogicalEntry le(String key, Layer layer, FileClass fc, String sha) {
        return new LogicalEntry(key, layer, fc, sha.length(), sha, new EntrySource(key, null));
    }

    private static PackageSnapshot snap(Map<String, LogicalEntry> entries) {
        return new PackageSnapshot(Path.of("dummy"), null, null, entries);
    }

    public void testCompute_statuses() {
        Map<String, LogicalEntry> old = new LinkedHashMap<>();
        Map<String, LogicalEntry> now = new LinkedHashMap<>();
        old.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0"));
        old.put("m.class", le("m.class", Layer.L1, FileClass.CLASS, "h1"));
        old.put("d.class", le("d.class", Layer.L1, FileClass.CLASS, "h5"));
        now.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0")); // 未变
        now.put("m.class", le("m.class", Layer.L1, FileClass.CLASS, "h3")); // 修改
        now.put("a.class", le("a.class", Layer.L1, FileClass.CLASS, "h4")); // 新增

        DiffResult r = new DiffEngine().compute(snap(old), snap(now));
        Asserts.assertEquals("未变=1", 1, r.get(DiffStatus.UNCHANGED).size());
        Asserts.assertEquals("修改=1", 1, r.get(DiffStatus.MODIFIED).size());
        Asserts.assertEquals("新增=1", 1, r.get(DiffStatus.ADDED).size());
        Asserts.assertEquals("删除=1", 1, r.get(DiffStatus.DELETED).size());
        Asserts.assertContains("m 应为修改", r.get(DiffStatus.MODIFIED).toString(), "m.class");
        Asserts.assertContains("d 应为删除", r.get(DiffStatus.DELETED).toString(), "d.class");
    }

    public void testStats_bizVsJar() {
        Map<String, LogicalEntry> old = new LinkedHashMap<>();
        Map<String, LogicalEntry> now = new LinkedHashMap<>();
        old.put("b.class", le("b.class", Layer.L1, FileClass.CLASS, "h2"));
        old.put("WEB-INF/lib/x.jar", le("WEB-INF/lib/x.jar", Layer.L2, FileClass.JAR, "h6"));
        old.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0"));
        old.put("d.class", le("d.class", Layer.L1, FileClass.CLASS, "h5"));       // 删除
        now.put("b.class", le("b.class", Layer.L1, FileClass.CLASS, "h3"));       // 业务修改
        now.put("WEB-INF/lib/x.jar", le("WEB-INF/lib/x.jar", Layer.L2, FileClass.JAR, "h7")); // jar 修改
        now.put("c.class", le("c.class", Layer.L1, FileClass.CLASS, "h4"));       // 新增
        now.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0"));       // 未变

        DiffResult r = new DiffEngine().compute(snap(old), snap(now));
        DiffStats s = new DiffEngine().stats(r);
        Asserts.assertEquals("added", 1, s.getAdded());
        Asserts.assertEquals("deleted", 1, s.getDeleted());
        Asserts.assertEquals("modified", 2, s.getModified());
        Asserts.assertEquals("unchanged", 1, s.getUnchanged());
        // 业务变更 = b(改) + c(增) + d(删) = 3；jar 变更 = x.jar = 1
        Asserts.assertEquals("bizChanged", 3, s.getBizChanged());
        Asserts.assertEquals("jarChanged", 1, s.getJarChanged());
    }

    public void testCollectL1ClassCandidates() {
        Map<String, LogicalEntry> old = new LinkedHashMap<>();
        Map<String, LogicalEntry> now = new LinkedHashMap<>();
        old.put("b.class", le("b.class", Layer.L1, FileClass.CLASS, "h2"));
        old.put("x.jar", le("WEB-INF/lib/x.jar", Layer.L2, FileClass.JAR, "h6"));
        old.put("d.class", le("d.class", Layer.L1, FileClass.CLASS, "h5"));
        old.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0"));
        now.put("b.class", le("b.class", Layer.L1, FileClass.CLASS, "h3"));
        now.put("x.jar", le("WEB-INF/lib/x.jar", Layer.L2, FileClass.JAR, "h7"));
        now.put("c.class", le("c.class", Layer.L1, FileClass.CLASS, "h4"));
        now.put("u.class", le("u.class", Layer.L1, FileClass.CLASS, "h0"));

        DiffResult r = new DiffEngine().compute(snap(old), snap(now));
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, snap(old), snap(now));
        Asserts.assertEquals("候选应为 3 个 L1 class", 3, cands.size());
        Asserts.assertContains("含 b", cands.toString(), "b.class");
        Asserts.assertContains("含 c", cands.toString(), "c.class");
        Asserts.assertContains("含 d", cands.toString(), "d.class");
        Asserts.assertNotContains("不应含未变的 u", cands.toString(), "u.class");
        Asserts.assertNotContains("不应含 jar", cands.toString(), "x.jar");
    }

    /** 超大文件（LCS 单元格超限）→ 退化为线性 diff：不 OOM、输出有界、含增删行。 */
    public void testLineDiff_hugeInputFallsBackToLinear() {
        StringBuilder oldB = new StringBuilder(), newB = new StringBuilder();
        int lines = 20_000; // 20000×20000 = 4 亿单元格 ≈1.6GB，远超 MAX_LCS_CELLS
        for (int i = 0; i < lines; i++) {
            oldB.append("line").append(i).append(" common old\n");
            newB.append("LINE").append(i).append(" changed new\n"); // 每行都变（无公共行，除前缀/后缀边界）
        }
        String diff = LineDiff.unified(oldB.toString(), newB.toString());
        Asserts.assertTrue("diff 输出应完整覆盖双侧内容", diff.length() > lines * 10L);
        Asserts.assertContains("应含删除行", diff, "- line0");
        Asserts.assertContains("应含新增行", diff, "+ LINE0");
        Asserts.assertTrue("不应因 LCS 内存爆炸返回空/抛异常", diff.length() > 0);
    }
}
