package com.bempdiff.test;

import com.bempdiff.diff.FolderDiff;
import com.bempdiff.report.FolderReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件夹对比 Markdown 报告渲染器（FolderReport）单测：汇总表格各维度计数、差异树标注
 * （目录斜杠 / 类型冲突 / 子树含差异 / 属性摘要）、行级内容差异章节、空结果提示，以及
 * writeToFile 落盘与 render(Path,Path) 便捷入口。数据由 FolderDiff.compare 真实生成。
 */
public final class FolderReportTest {

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private static void deleteRec(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    public void testRender_fullReportSectionsAndTree() throws Exception {
        Path left = Files.createTempDirectory("bdf-rep-l");
        Path right = Files.createTempDirectory("bdf-rep-r");
        try {
            write(left.resolve("doc/readme.md"), "hello\nworld\n");
            write(right.resolve("doc/readme.md"), "hello\nchange\n");   // 内容差异
            write(left.resolve("only-left.txt"), "L");                   // 仅左
            write(right.resolve("only-right.txt"), "R");                 // 仅右
            write(left.resolve("sub/a.txt"), "1");
            write(right.resolve("sub/a.txt"), "2");                      // sub 目录 SAME 但子树有差异
            write(left.resolve("same.txt"), "identical");
            write(right.resolve("same.txt"), "identical");               // 完全相同
            FileTime t = FileTime.fromMillis(1_000_000L);
            Files.setLastModifiedTime(left.resolve("same.txt"), t);
            Files.setLastModifiedTime(right.resolve("same.txt"), t);
            Files.createFile(left.resolve("tmc"));                       // 左：文件
            Files.createDirectory(right.resolve("tmc"));                 // 右：同名目录 → 类型冲突

            FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
            String md = FolderReport.render(r);

            // 标题与左右根
            Asserts.assertContains("报告标题", md, "# 文件夹对比报告");
            Asserts.assertContains("左根行", md, "- 左侧目录:");
            Asserts.assertContains("右根行", md, "- 右侧目录:");

            // 汇总表各维度计数
            Asserts.assertContains("汇总小节", md, "## 一、差异汇总");
            Asserts.assertContains("仅左侧=1", md, "| 仅左侧存在 (LEFT_ONLY) | 1 |");
            Asserts.assertContains("仅右侧=1", md, "| 仅右侧存在 (RIGHT_ONLY) | 1 |");
            Asserts.assertContains("两侧不同=2", md, "| 两侧不同 (MODIFIED) | 2 |");
            Asserts.assertContains("内容不同=2", md, "|   ├ 内容不同 | 2 |");
            Asserts.assertContains("类型冲突=1", md, "| 类型冲突 (TYPE_MISMATCH) | 1 |");
            Asserts.assertContains("完全相同行存在", md, "| 完全相同 (SAME) |");

            // 差异树：图例、目录斜杠、类型冲突标记、子树含差异、属性摘要
            Asserts.assertContains("差异树小节", md, "## 二、差异树");
            Asserts.assertContains("图例", md, "图例：");
            Asserts.assertContains("目录节点带斜杠", md, "sub/");
            Asserts.assertContains("类型冲突标记 [!]", md, "[!]");
            Asserts.assertContains("子树含差异标注", md, "(子树含差异)");
            Asserts.assertContains("修改项属性摘要含内容不同", md, "内容不同");

            // 行级差异章节：每个条目均列标题，修改文件附 ```diff 块
            Asserts.assertContains("行级差异小节", md, "## 三、修改文件内容差异");
            Asserts.assertContains("修改文件标题", md, "### doc/readme.md");
            Asserts.assertContains("diff 代码围栏", md, "```diff");
            Asserts.assertNotContains("空结果提示不应出现", md, "_无文本文件内容差异");
        } finally {
            deleteRec(left);
            deleteRec(right);
        }
    }

    public void testRender_emptyBothDirsNoDiff() throws Exception {
        Path left = Files.createTempDirectory("bdf-rep-empty-l");
        Path right = Files.createTempDirectory("bdf-rep-empty-r");
        try {
            FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());
            String md = FolderReport.render(r);
            Asserts.assertContains("汇总小节", md, "## 一、差异汇总");
            Asserts.assertContains("仅左侧=0", md, "| 仅左侧存在 (LEFT_ONLY) | 0 |");
            Asserts.assertContains("空文本差异提示", md, "_无文本文件内容差异");
            Asserts.assertNotContains("不应出现差异章节小节标题", md, "### ");
        } finally {
            deleteRec(left);
            deleteRec(right);
        }
    }

    public void testWriteToFileAndConvenienceEntry() throws Exception {
        Path left = Files.createTempDirectory("bdf-rep-wl");
        Path right = Files.createTempDirectory("bdf-rep-wr");
        Path base = Files.createTempDirectory("bdf-rep-out");
        try {
            write(left.resolve("app.txt"), "line1\nline2\n");
            write(right.resolve("app.txt"), "line1\nCHANGED\n");
            FolderDiff.FolderDiffResult r = FolderDiff.compare(left, right, FolderDiff.Options.defaults());

            Path out = base.resolve("report.md");
            FolderReport.writeToFile(r, out);
            Asserts.assertTrue("writeToFile 应创建文件", Files.exists(out));
            Asserts.assertTrue("文件非空", Files.size(out) > 0);
            String disk = Files.readString(out);
            Asserts.assertContains("落盘内容含标题", disk, "# 文件夹对比报告");

            // 便捷入口：给定两目录直接渲染
            String md = FolderReport.render(left, right);
            Asserts.assertContains("便捷入口含标题", md, "# 文件夹对比报告");
            Asserts.assertContains("便捷入口含修改文件", md, "### app.txt");
        } finally {
            deleteRec(left);
            deleteRec(right);
            deleteRec(base);
        }
    }
}
