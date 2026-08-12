package com.bempdiff.test;

import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;
import com.bempdiff.parse.FolderParser;
import com.bempdiff.config.ParseConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文件夹比较功能测试（FR：新增的文件夹比较）。
 * 覆盖：FolderParser 解析（仅文件入表 / 相对路径 / 分类 / sha256 / 跳过符号链接 / 拒绝非目录）、
 * 以及复用 DiffEngine 的 新增/修改/删除/未变 差异检测。
 */
public class FolderTest {

    private final FolderParser parser = new FolderParser();
    private final ParseConfig cfg = new ParseConfig();

    private Path mkDir() throws IOException {
        return Files.createTempDirectory("bd-folder-");
    }

    private void write(Path dir, String rel, String content) throws IOException {
        Path p = dir.resolve(rel.replace('/', java.io.File.separatorChar));
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private void mkdir(Path dir, String rel) throws IOException {
        Files.createDirectories(dir.resolve(rel.replace('/', java.io.File.separatorChar)));
    }

    // ---------- 解析 ----------

    public void testParse_listsFilesWithRelativeKeys_noDirs() throws IOException {
        Path d = mkDir();
        write(d, "a.txt", "A");
        write(d, "sub/b.txt", "B");
        write(d, "sub/deep/c.md", "C");
        mkdir(d, "emptyDir"); // 空目录不应入表

        PackageSnapshot snap = parser.parse(d, cfg);
        Map<String, LogicalEntry> e = snap.getEntries();
        Asserts.assertEquals("应仅 3 个文件入表（空目录不入）", 3, e.size());
        Asserts.assertTrue("应含 a.txt", e.containsKey("a.txt"));
        Asserts.assertTrue("应含 sub/b.txt", e.containsKey("sub/b.txt"));
        Asserts.assertTrue("应含 sub/deep/c.md", e.containsKey("sub/deep/c.md"));
        Asserts.assertFalse("空目录不应入表", e.containsKey("emptyDir"));
        Asserts.assertEquals("类型应为 FOLDER", PackageType.FOLDER, snap.getType());
    }

    public void testParse_classifyAndSha() throws IOException {
        Path d = mkDir();
        write(d, "X.class", "compiled-bytes");   // 编译产物 → CLASS
        write(d, "Src.java", "public class Src {}"); // 源码 → OTHER（classify 仅对 .class 判 CLASS）
        write(d, "Y.js", "var a=1;");
        write(d, "Z.jar", "fakejar");
        write(d, "W.txt", "plain");
        PackageSnapshot snap = parser.parse(d, cfg);
        Map<String, LogicalEntry> e = snap.getEntries();
        Asserts.assertEquals("class 应为 CLASS", FileClass.CLASS, e.get("X.class").getFileClass());
        Asserts.assertEquals("java 源码应为 OTHER", FileClass.OTHER, e.get("Src.java").getFileClass());
        Asserts.assertEquals("js 应为 JS", FileClass.JS, e.get("Y.js").getFileClass());
        Asserts.assertEquals("jar 应为 JAR", FileClass.JAR, e.get("Z.jar").getFileClass());
        Asserts.assertEquals("txt 应为 OTHER", FileClass.OTHER, e.get("W.txt").getFileClass());

        // sha256：同内容不同路径 → 相同；不同内容 → 不同
        Path d2 = mkDir();
        write(d2, "p/a.txt", "SAME");
        write(d2, "q/a.txt", "SAME");
        write(d2, "r/a.txt", "DIFFERENT");
        PackageSnapshot snap2 = parser.parse(d2, cfg);
        Map<String, LogicalEntry> e2 = snap2.getEntries();
        String hSame1 = e2.get("p/a.txt").getSha256();
        String hSame2 = e2.get("q/a.txt").getSha256();
        String hDiff = e2.get("r/a.txt").getSha256();
        Asserts.assertNotNull("sha 不应为空", hSame1);
        Asserts.assertEquals("同内容 sha 应相等", hSame1, hSame2);
        Asserts.assertNotEquals("不同内容 sha 应不同", hSame1, hDiff);
    }

    public void testParse_rejectsNonDirectory() throws IOException {
        Path f = Files.createTempFile("bd-notdir-", ".txt");
        Files.writeString(f, "x");
        boolean threw = false;
        try {
            parser.parse(f, cfg);
        } catch (IOException ex) {
            threw = true;
        }
        Asserts.assertTrue("解析普通文件应抛 IOException", threw);
    }

    public void testParse_skipsSymlinks() throws IOException {
        Path d = mkDir();
        write(d, "real.txt", "real");
        Path link = d.resolve("link.txt");
        boolean supported = true;
        try {
            Files.createSymbolicLink(link, d.resolve("real.txt"));
        } catch (IOException | UnsupportedOperationException ex) {
            supported = false; // Windows 无特权时跳过
        }
        if (!supported) {
            Asserts.assertTrue("符号链接创建不被支持，跳过（视为通过）", true);
            return;
        }
        PackageSnapshot snap = parser.parse(d, cfg);
        Asserts.assertFalse("符号链接不应入表", snap.getEntries().containsKey("link.txt"));
        Asserts.assertTrue("真实文件仍应入表", snap.getEntries().containsKey("real.txt"));
    }

    // ---------- 差异（复用 DiffEngine） ----------

    public void testDiff_addedModifiedDeletedUnchanged() throws IOException {
        Path oldD = mkDir();
        write(oldD, "a.txt", "A");
        write(oldD, "b.txt", "B");
        write(oldD, "c.txt", "same");

        Path newD = mkDir();
        write(newD, "a.txt", "A2");      // 修改
        write(newD, "b.txt", "B");       // 未变
        // c.txt 删除
        write(newD, "d.txt", "D");       // 新增

        PackageSnapshot oldSnap = parser.parse(oldD, cfg);
        PackageSnapshot newSnap = parser.parse(newD, cfg);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);

        List<String> modified = r.get(DiffStatus.MODIFIED);
        List<String> unchanged = r.get(DiffStatus.UNCHANGED);
        List<String> deleted = r.get(DiffStatus.DELETED);
        List<String> added = r.get(DiffStatus.ADDED);

        Asserts.assertTrue("a.txt 应为 MODIFIED", modified.contains("a.txt"));
        Asserts.assertTrue("b.txt 应为 UNCHANGED", unchanged.contains("b.txt"));
        Asserts.assertTrue("c.txt 应为 DELETED", deleted.contains("c.txt"));
        Asserts.assertTrue("d.txt 应为 ADDED", added.contains("d.txt"));
        Asserts.assertEquals("MODIFIED 数量应为 1", 1, modified.size());
        Asserts.assertEquals("ADDED 数量应为 1", 1, added.size());
        Asserts.assertEquals("DELETED 数量应为 1", 1, deleted.size());
    }

    public void testDiff_renamedDirShowsAsDeletePlusAdd() throws IOException {
        // 子目录被整体改名：其下文件表现为 删除(旧路径)+新增(新路径)，结构变化可见
        Path oldD = mkDir();
        write(oldD, "v1/ServiceImpl.java", "old");
        write(oldD, "v1/Util.java", "old");

        Path newD = mkDir();
        write(newD, "v2/ServiceImpl.java", "old");  // 内容相同，但路径变了
        write(newD, "v2/Util.java", "old");

        PackageSnapshot oldSnap = parser.parse(oldD, cfg);
        PackageSnapshot newSnap = parser.parse(newD, cfg);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);

        Asserts.assertTrue("旧目录文件应标记 DELETED", r.get(DiffStatus.DELETED).contains("v1/ServiceImpl.java"));
        Asserts.assertTrue("新目录文件应标记 ADDED", r.get(DiffStatus.ADDED).contains("v2/ServiceImpl.java"));
        // 内容相同，不应有 MODIFIED
        Asserts.assertEquals("改名目录内容相同，不应有 MODIFIED", 0, r.get(DiffStatus.MODIFIED).size());
    }
}
