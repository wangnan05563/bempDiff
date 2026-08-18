package com.bempdiff.test;

import com.bempdiff.diff.FolderDiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

/**
 * FolderDiff 引擎单测：覆盖仅左/仅右、内容不同、仅属性不同、类型冲突、相同、文本行级 diff、
 * 错误隔离（非目录输入）与较大目录性能/计数。
 */
public final class FolderDiffTest {

    private static Path dir() throws IOException {
        return Files.createTempDirectory("bempdiff-folderdiff");
    }

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private static FolderDiff.FolderEntry byKey(FolderDiff.FolderDiffResult r, String key) {
        return r.flat.get(key);
    }

    public static void testLeftAndRightOnly() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("a.txt"), "alpha");
        write(right.resolve("b.txt"), "beta");
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        Asserts.assertEquals("仅左侧", 1, r.summary.getLeftOnly());
        Asserts.assertEquals("仅右侧", 1, r.summary.getRightOnly());
        Asserts.assertEquals("无两侧不同", 0, r.summary.getModified());
        Asserts.assertEquals("无相同", 0, r.summary.getSame());
        Asserts.assertEquals("a.txt 状态", FolderDiff.FolderDiffStatus.LEFT_ONLY, byKey(r, "a.txt").status);
        Asserts.assertEquals("b.txt 状态", FolderDiff.FolderDiffStatus.RIGHT_ONLY, byKey(r, "b.txt").status);
    }

    public static void testModifiedContentTextDiff() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("src/app.js"), "function add(a,b){\n  return a+b;\n}\n");
        write(right.resolve("src/app.js"), "function add(a,b){\n  return a*b;\n}\n");
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        FolderDiff.FolderEntry e = byKey(r, "src/app.js");
        Asserts.assertEquals("状态为 MODIFIED", FolderDiff.FolderDiffStatus.MODIFIED, e.status);
        Asserts.assertTrue("内容不同", e.isContentModified());
        Asserts.assertNotNull("行级 diff 非空", e.lineDiff);
        Asserts.assertContains("diff 含原内容行", e.lineDiff, "return a+b;");
        Asserts.assertContains("diff 含新内容行", e.lineDiff, "return a*b;");
    }

    public static void testModifiedAttrOnly() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("c.txt"), "same content");
        write(right.resolve("c.txt"), "same content");
        // 仅修改时间不同，内容/大小相同 -> 应判 MODIFIED(仅属性不同)，内容视为相同
        Files.setLastModifiedTime(right.resolve("c.txt"),
                FileTime.fromMillis(Files.getLastModifiedTime(left.resolve("c.txt")).toMillis() + 86_400_000L));
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        FolderDiff.FolderEntry e = byKey(r, "c.txt");
        Asserts.assertEquals("状态为 MODIFIED", FolderDiff.FolderDiffStatus.MODIFIED, e.status);
        Asserts.assertTrue("仅属性不同(无内容变化)", e.isAttrOnlyModified());
        Asserts.assertFalse("不应标内容变化", e.attrChanges.contains(FolderDiff.AttrChange.CONTENT));
        Asserts.assertTrue("应标修改时间变化", e.attrChanges.contains(FolderDiff.AttrChange.MTIME));
    }

    public static void testTypeMismatch() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("d"), "i am a file");          // 左侧：文件
        Files.createDirectory(right.resolve("d"));        // 右侧：同名目录
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        FolderDiff.FolderEntry e = byKey(r, "d");
        Asserts.assertEquals("类型冲突", FolderDiff.FolderDiffStatus.TYPE_MISMATCH, e.status);
        Asserts.assertTrue("含 TYPE 变更", e.attrChanges.contains(FolderDiff.AttrChange.TYPE));
    }

    public static void testSame() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("x/y.txt"), "identical");
        write(right.resolve("x/y.txt"), "identical");
        // 固定相同修改时间，避免写入时刻差异导致被判为"仅属性不同"
        FileTime t = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(left.resolve("x/y.txt"), t);
        Files.setLastModifiedTime(right.resolve("x/y.txt"), t);
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        // 目录不比较 mtime（避免随子项变化的冗余双标），故 x 目录与 x/y.txt 均为 SAME -> 共 2 项
        Asserts.assertEquals("相同计数(含目录)", 2, r.summary.getSame());
        Asserts.assertEquals("无两侧不同", 0, r.summary.getModified());
        Asserts.assertEquals("y.txt 相同", FolderDiff.FolderDiffStatus.SAME, byKey(r, "x/y.txt").status);
    }

    public static void testNestedStructureAndTree() throws IOException {
        Path left = dir(), right = dir();
        write(left.resolve("src/com/A.java"), "class A{}");
        write(left.resolve("src/com/B.java"), "class B{}");
        write(left.resolve("README.md"), "# hi");
        write(right.resolve("src/com/A.java"), "class A{ int x; }");
        write(right.resolve("src/com/B.java"), "class B{}");     // 未变
        write(right.resolve("README.md"), "# hi");              // 未变
        write(right.resolve("src/com/C.java"), "class C{}");     // 仅右侧
        // 固定相同修改时间，避免写入时刻差异导致 B.java/README.md 被判为"仅属性不同"
        FileTime t = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(left.resolve("src/com/B.java"), t);
        Files.setLastModifiedTime(right.resolve("src/com/B.java"), t);
        Files.setLastModifiedTime(left.resolve("README.md"), t);
        Files.setLastModifiedTime(right.resolve("README.md"), t);
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        // 树应含顶层 src 与 README.md
        boolean hasSrc = r.roots.stream().anyMatch(e -> e.relPath.equals("src"));
        boolean hasReadme = r.roots.stream().anyMatch(e -> e.relPath.equals("README.md"));
        Asserts.assertTrue("顶层含 src 目录", hasSrc);
        Asserts.assertTrue("顶层含 README.md", hasReadme);
        Asserts.assertEquals("A.java 修改", FolderDiff.FolderDiffStatus.MODIFIED, byKey(r, "src/com/A.java").status);
        Asserts.assertEquals("B.java 相同", FolderDiff.FolderDiffStatus.SAME, byKey(r, "src/com/B.java").status);
        Asserts.assertEquals("C.java 仅右侧", FolderDiff.FolderDiffStatus.RIGHT_ONLY, byKey(r, "src/com/C.java").status);
        // src 目录即使自身 SAME，因子树有差异，subtreeHasDiff 应为 true
        FolderDiff.FolderEntry src = byKey(r, "src");
        Asserts.assertEquals("src 自身状态 SAME", FolderDiff.FolderDiffStatus.SAME, src.status);
    }

    public static void testErrorOnNonDirectory() {
        Path f = null;
        try {
            f = Files.createTempFile("not-a-dir", ".tmp");
            try {
                FolderDiff.compare(f, f, FolderDiff.Options.defaults());
                Asserts.fail("应抛出 IOException(非目录)");
            } catch (IOException expected) {
                Asserts.assertContains("异常信息含目录提示", expected.getMessage(), "目录");
            }
        } catch (IOException e) {
            Asserts.fail("测试夹具创建失败: " + e.getMessage());
        } finally {
            if (f != null) {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            }
        }
    }

    public static void testLargeFolderPerformance() throws IOException {
        Path left = dir(), right = dir();
        int n = 400;
        for (int i = 0; i < n; i++) {
            String name = "f" + (i % 7) + "/file" + i + ".txt"; // 制造 7 个子目录
            write(left.resolve(name), "line1\nline2\nid=" + i + "\n");
            // 右侧：偶数文件内容改一个字（制造差异），奇数保持不变；并新增一个文件
            if (i % 2 == 0) {
                write(right.resolve(name), "line1\nline2-CHANGED\nid=" + i + "\n");
            } else {
                write(right.resolve(name), "line1\nline2\nid=" + i + "\n");
            }
        }
        write(right.resolve("only-right.txt"), "extra");
        long t0 = System.currentTimeMillis();
        FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
        long ms = System.currentTimeMillis() - t0;
        Asserts.assertEquals("扫描文件数=801(两侧累加: 400左 + 401右)", 801, r.summary.getScannedFiles());
        // 奇数文件因左右写入时刻 mtime 不同会被标为"仅属性不同"；此处用 contentChanged 精确断言内容改动数
        Asserts.assertEquals("内容不同=200(偶数行内容改)", 200, r.summary.getContentChanged());
        Asserts.assertEquals("仅右侧=1", 1, r.summary.getRightOnly());
        Asserts.assertTrue("性能: 400+ 文件应在 30s 内完成, 实际=" + ms + "ms", ms < 30_000);
    }
}
