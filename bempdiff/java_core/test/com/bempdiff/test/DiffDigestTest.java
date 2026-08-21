package com.bempdiff.test;

import com.bempdiff.diff.DiffDigest;

import java.util.List;

/**
 * unified diff 变更摘要（DiffDigest）解析与渲染测试。
 *
 * <p>守护需求：AI 报告中仅列变更行 + 行号区间 + 类型标注（修改/新增/删除），
 * 不再呈现完整文件。验证：hunk 头解析、相邻删增合并为「修改」、行号区间、
 * 无 hunk 兜底计数、渲染格式与有界截断。</p>
 */
public final class DiffDigestTest {

    /** 标准 unified diff：修改块（老 L2 → 新 L2）+ 删除+新增合并修改块（老 L10-11 → 新 L10）。 */
    public void testParse_modifiedBlocks() {
        String diff = "@@ -1,4 +1,4 @@\n"
                + " context1\n"
                + "-old line 2\n"
                + "+new line 2\n"
                + " context3\n"
                + "@@ -10,2 +10,1 @@\n"
                + "-del1\n"
                + "-del2\n"
                + "+add1\n";
        List<DiffDigest.Change> cs = DiffDigest.parse(diff);
        Asserts.assertEquals("应有 2 个变更块", 2, cs.size());

        DiffDigest.Change c0 = cs.get(0);
        Asserts.assertEquals("块0 类型=修改", "MODIFIED", c0.type);
        Asserts.assertEquals("块0 老起始行", 2, c0.oldStart);
        Asserts.assertEquals("块0 老结束行", 2, c0.oldEnd);
        Asserts.assertEquals("块0 新起始行", 2, c0.newStart);
        Asserts.assertEquals("块0 新结束行", 2, c0.newEnd);
        Asserts.assertEquals("块0 老行内容", 1, c0.oldLines.size());
        Asserts.assertEquals("块0 老行文本", "old line 2", c0.oldLines.get(0));
        Asserts.assertEquals("块0 新行内容", 1, c0.newLines.size());
        Asserts.assertEquals("块0 新行文本", "new line 2", c0.newLines.get(0));

        DiffDigest.Change c1 = cs.get(1);
        Asserts.assertEquals("块1 类型=修改（删2增1 合并）", "MODIFIED", c1.type);
        Asserts.assertEquals("块1 老起始行", 10, c1.oldStart);
        Asserts.assertEquals("块1 老结束行", 11, c1.oldEnd);
        Asserts.assertEquals("块1 新起始行", 10, c1.newStart);
        Asserts.assertEquals("块1 新结束行", 10, c1.newEnd);
    }

    /** 纯新增块与纯删除块。 */
    public void testParse_addedAndDeletedBlocks() {
        String diff = "@@ -5,0 +5,2 @@\n"
                + "+brand new line\n"
                + "+another new line\n"
                + "@@ -12,2 +12,0 @@\n"
                + "-gone a\n"
                + "-gone b\n";
        List<DiffDigest.Change> cs = DiffDigest.parse(diff);
        Asserts.assertEquals("应有 2 个变更块", 2, cs.size());

        DiffDigest.Change c0 = cs.get(0);
        Asserts.assertEquals("块0 类型=新增", "ADDED", c0.type);
        Asserts.assertEquals("块0 老侧无行（0）", 0, c0.oldStart);
        Asserts.assertEquals("块0 新起始行", 5, c0.newStart);
        Asserts.assertEquals("块0 新结束行", 6, c0.newEnd);
        Asserts.assertEquals("块0 新行数", 2, c0.newLines.size());

        DiffDigest.Change c1 = cs.get(1);
        Asserts.assertEquals("块1 类型=删除", "DELETED", c1.type);
        Asserts.assertEquals("块1 老起始行", 12, c1.oldStart);
        Asserts.assertEquals("块1 老结束行", 13, c1.oldEnd);
        Asserts.assertEquals("块1 新侧无行（0）", 0, c1.newStart);
        Asserts.assertEquals("块1 老行数", 2, c1.oldLines.size());
    }

    /** 无 hunk 头（手造假 diff）：按出现顺序从第 1 行连续计数，仍能给出类型与行号。 */
    public void testParse_noHunkFallback() {
        String diff = " ctx\n-removed\n+added\n keep\n";
        List<DiffDigest.Change> cs = DiffDigest.parse(diff);
        Asserts.assertEquals("无 hunk 也应解析出 1 块", 1, cs.size());
        DiffDigest.Change c = cs.get(0);
        Asserts.assertEquals("类型=修改", "MODIFIED", c.type);
        Asserts.assertEquals("老行号 2", 2, c.oldStart);
        Asserts.assertEquals("新行号 2", 2, c.newStart);
    }

    /** 渲染格式：类型中文标签 + 行号区间 + 变更行内容（保留 -/+ 前缀供 diff 高亮）。 */
    public void testRender_format() {
        String diff = "@@ -1,4 +1,4 @@\n"
                + " ctx\n"
                + "-old value\n"
                + "+new value\n"
                + " ctx2\n"
                + "@@ -10,1 +10,2 @@\n"
                + "+inserted\n"
                + " kept\n";
        String out = DiffDigest.render(diff);
        Asserts.assertContains("应标注 [修改]", out, "[修改]");
        Asserts.assertContains("应含老行号 L2", out, "老 L2");
        Asserts.assertContains("应含新行号 L2", out, "新 L2");
        Asserts.assertContains("应含旧行内容", out, "old value");
        Asserts.assertContains("应含新行内容", out, "new value");
        Asserts.assertContains("应含 [新增]", out, "[新增]");
        Asserts.assertContains("应含新增行号 L10", out, "新 L10");
        Asserts.assertContains("应含新增内容", out, "inserted");
        Asserts.assertNotContains("不应含上下文行 ctx", out, " ctx");
    }

    /** 渲染：纯删除块显示「老 Lx-y」。 */
    public void testRender_deletedLocation() {
        String out = DiffDigest.render("@@ -20,2 +20,0 @@\n-old1\n-old2\n");
        Asserts.assertContains("应标注 [删除]", out, "[删除]");
        Asserts.assertContains("应含老行号区间", out, "老 L20-21");
        Asserts.assertContains("应含被删内容", out, "old1");
    }

    /** 单行超长截断。 */
    public void testRender_longLineCapped() {
        StringBuilder longLine = new StringBuilder("+");
        for (int i = 0; i < 500; i++) longLine.append("x");
        String out = DiffDigest.render("@@ -1,1 +1,1 @@\n" + longLine + "\n");
        Asserts.assertTrue("输出长度应受限（不含整行 500 字符）",
                out.length() <= DiffDigest.LINE_CHAR_CAP + 32);
        Asserts.assertTrue("应带省略标记", out.contains("…"));
    }

    /** 空 diff / 仅上下文 → 无变更块。 */
    public void testParse_emptyOrContextOnly() {
        Asserts.assertEquals("null 输入 → 空列表", 0, DiffDigest.parse(null).size());
        Asserts.assertEquals("空串 → 空列表", 0, DiffDigest.parse("").size());
        List<DiffDigest.Change> cs = DiffDigest.parse("@@ -1,2 +1,2 @@\n a\n b\n");
        Asserts.assertEquals("仅上下文 → 空列表", 0, cs.size());
    }

    /** 超大 diff：块数护栏生效（最多 CHANGES_MAX 块），渲染结果有界。 */
    public void testRender_manyBlocksBounded() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("@@ -").append(i * 5 + 1).append(",2 +").append(i * 5 + 1).append(",2 @@\n");
            sb.append("-old").append(i).append("\n+new").append(i).append("\n");
        }
        String out = DiffDigest.render(sb.toString());
        Asserts.assertTrue("渲染应有省略标记", out.contains("已省略"));
        Asserts.assertTrue("渲染长度有界（远小于原 1200 行 diff）",
                out.length() < 60_000);
        Asserts.assertNotContains("不应渲染第 300 块", out, "old299");
    }
}
