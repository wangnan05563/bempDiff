package com.bempdiff.test;

import com.bempdiff.fs.FileOps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 差异树右键菜单「磁盘文件操作」测试（FileOps 纯逻辑层）。
 * 覆盖：属性（info）、删除（文件/目录递归/不存在/越界）、重命名（正常/目标存在/非法名/不存在）、
 * 跨侧复制（l2r/r2l/目标已存在/源不存在/非法方向）、路径越界防护（.. / 绝对路径 / 根自身）。
 */
public class FileOpsTest {

    private Path mkDir() throws IOException {
        return Files.createTempDirectory("bd-fops-");
    }

    private void write(Path dir, String rel, String content) throws IOException {
        Path p = dir.resolve(rel.replace('/', java.io.File.separatorChar));
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private Path resolve(Path root, String key) throws IOException {
        return root.resolve(key.replace('/', java.io.File.separatorChar)).normalize();
    }

    // ---------- info ----------

    public void testInfo_file() throws IOException {
        Path root = mkDir();
        write(root, "a.txt", "hello");
        FileOps.OpResult r = FileOps.info(root, "a.txt");
        Asserts.assertTrue("文件属性应成功", r.ok());
        Asserts.assertNotNull("应携带属性数据", r.info());
        Asserts.assertFalse("应为文件（非目录）", r.info().dir());
        Asserts.assertEquals("大小应为 5", 5L, r.info().size());
        Asserts.assertNotNull("sha 不应为空", r.info().sha256());
        Asserts.assertTrue("路径应为绝对路径", r.info().path().startsWith(root.toAbsolutePath().toString()));
    }

    public void testInfo_dir() throws IOException {
        Path root = mkDir();
        write(root, "sub/b.txt", "x");
        FileOps.OpResult r = FileOps.info(root, "sub/");
        Asserts.assertTrue("目录属性应成功", r.ok());
        Asserts.assertTrue("应为目录", r.info().dir());
        Asserts.assertEquals("目录 sha 应为 null", null, r.info().sha256());
    }

    public void testInfo_notFound() throws IOException {
        Path root = mkDir();
        FileOps.OpResult r = FileOps.info(root, "nope.txt");
        Asserts.assertFalse("不存在应失败", r.ok());
        Asserts.assertEquals("错误码应为 NOT_FOUND", "NOT_FOUND", r.code());
    }

    // ---------- delete ----------

    public void testDelete_file() throws IOException {
        Path root = mkDir();
        write(root, "a.txt", "x");
        FileOps.OpResult r = FileOps.deleteEntry(root, "a.txt");
        Asserts.assertTrue("删除文件应成功: " + r.message(), r.ok());
        Asserts.assertFalse("文件应已消失", Files.exists(resolve(root, "a.txt")));
    }

    public void testDelete_dir_recursive() throws IOException {
        Path root = mkDir();
        write(root, "sub/deep/a.txt", "x");
        write(root, "sub/b.txt", "y");
        FileOps.OpResult r = FileOps.deleteEntry(root, "sub/");
        Asserts.assertTrue("递归删除目录应成功: " + r.message(), r.ok());
        Asserts.assertFalse("目录应已消失", Files.exists(resolve(root, "sub/")));
        Asserts.assertFalse("子文件应已消失", Files.exists(resolve(root, "sub/deep/a.txt")));
    }

    public void testDelete_emptyDir() throws IOException {
        Path root = mkDir();
        Files.createDirectories(resolve(root, "empty"));
        FileOps.OpResult r = FileOps.deleteEntry(root, "empty/");
        Asserts.assertTrue("删除空目录应成功", r.ok());
        Asserts.assertFalse("空目录应已消失", Files.exists(resolve(root, "empty")));
    }

    public void testDelete_notFound() throws IOException {
        Path root = mkDir();
        FileOps.OpResult r = FileOps.deleteEntry(root, "ghost.txt");
        Asserts.assertFalse("删除不存在应失败", r.ok());
        Asserts.assertEquals("错误码应为 NOT_FOUND", "NOT_FOUND", r.code());
    }

    public void testDelete_invalidPath_rejected() throws IOException {
        Path root = mkDir();
        // 越界（..）、绝对路径、根自身 —— 全部拒绝且不产生副作用
        FileOps.OpResult r1 = FileOps.deleteEntry(root, "../escape.txt");
        Asserts.assertFalse("越界路径应拒绝", r1.ok());
        Asserts.assertEquals("错误码应为 INVALID_PATH", "INVALID_PATH", r1.code());

        FileOps.OpResult r2 = FileOps.deleteEntry(root, root.getParent().resolve("outside-probe.txt").toString().replace('\\', '/'));
        Asserts.assertFalse("绝对路径应拒绝", r2.ok());
        Asserts.assertEquals("错误码应为 INVALID_PATH", "INVALID_PATH", r2.code());
    }

    // ---------- rename ----------

    public void testRename_ok() throws IOException {
        Path root = mkDir();
        write(root, "a.txt", "x");
        FileOps.OpResult r = FileOps.renameEntry(root, "a.txt", "b.txt");
        Asserts.assertTrue("重命名应成功: " + r.message(), r.ok());
        Asserts.assertTrue("新名应存在", Files.exists(resolve(root, "b.txt")));
        Asserts.assertFalse("旧名应消失", Files.exists(resolve(root, "a.txt")));
    }

    public void testRename_targetExists() throws IOException {
        Path root = mkDir();
        write(root, "a.txt", "x");
        write(root, "b.txt", "y");
        FileOps.OpResult r = FileOps.renameEntry(root, "a.txt", "b.txt");
        Asserts.assertFalse("目标存在应拒绝", r.ok());
        Asserts.assertEquals("错误码应为 EXISTS", "EXISTS", r.code());
    }

    public void testRename_invalidName() throws IOException {
        Path root = mkDir();
        write(root, "a.txt", "x");
        FileOps.OpResult r1 = FileOps.renameEntry(root, "a.txt", "x/evil.txt");
        Asserts.assertFalse("含分隔符名称应拒绝", r1.ok());
        Asserts.assertEquals("错误码应为 INVALID_PATH", "INVALID_PATH", r1.code());
        FileOps.OpResult r2 = FileOps.renameEntry(root, "a.txt", "..");
        Asserts.assertFalse("路径穿越名称应拒绝", r2.ok());
        FileOps.OpResult r3 = FileOps.renameEntry(root, "a.txt", "");
        Asserts.assertFalse("空名称应拒绝", r3.ok());
        Asserts.assertTrue("原文件不应被改动", Files.exists(resolve(root, "a.txt")));
    }

    public void testRename_notFound() throws IOException {
        Path root = mkDir();
        FileOps.OpResult r = FileOps.renameEntry(root, "ghost.txt", "new.txt");
        Asserts.assertFalse("重命名不存在应失败", r.ok());
        Asserts.assertEquals("错误码应为 NOT_FOUND", "NOT_FOUND", r.code());
    }

    // ---------- copy across ----------

    public void testCopy_l2r() throws IOException {
        Path oldRoot = mkDir();
        Path newRoot = mkDir();
        write(oldRoot, "conf/app.properties", "port=8080");
        FileOps.OpResult r = FileOps.copyAcross(oldRoot, newRoot, "conf/app.properties", "l2r");
        Asserts.assertTrue("左→右复制应成功: " + r.message(), r.ok());
        Asserts.assertTrue("目标侧应出现文件", Files.exists(resolve(newRoot, "conf/app.properties")));
        Asserts.assertEquals("内容应一致", "port=8080", Files.readString(resolve(newRoot, "conf/app.properties")));
    }

    public void testCopy_r2l() throws IOException {
        Path oldRoot = mkDir();
        Path newRoot = mkDir();
        write(newRoot, "only-new.txt", "new");
        FileOps.OpResult r = FileOps.copyAcross(newRoot, oldRoot, "only-new.txt", "r2l");
        Asserts.assertTrue("右→左复制应成功", r.ok());
        Asserts.assertTrue("目标侧应出现文件", Files.exists(resolve(oldRoot, "only-new.txt")));
    }

    public void testCopy_targetExists() throws IOException {
        Path oldRoot = mkDir();
        Path newRoot = mkDir();
        write(oldRoot, "a.txt", "x");
        write(newRoot, "a.txt", "y");
        FileOps.OpResult r = FileOps.copyAcross(oldRoot, newRoot, "a.txt", "l2r");
        Asserts.assertFalse("目标存在应拒绝（防误覆盖）", r.ok());
        Asserts.assertEquals("错误码应为 EXISTS", "EXISTS", r.code());
        Asserts.assertEquals("目标内容不应被覆盖", "y", Files.readString(resolve(newRoot, "a.txt")));
    }

    public void testCopy_sourceNotFound() throws IOException {
        Path oldRoot = mkDir();
        Path newRoot = mkDir();
        FileOps.OpResult r = FileOps.copyAcross(oldRoot, newRoot, "ghost.txt", "l2r");
        Asserts.assertFalse("源不存在应失败", r.ok());
        Asserts.assertEquals("错误码应为 NOT_FOUND", "NOT_FOUND", r.code());
    }

    public void testCopy_invalidDirection() throws IOException {
        Path oldRoot = mkDir();
        Path newRoot = mkDir();
        write(oldRoot, "a.txt", "x");
        FileOps.OpResult r = FileOps.copyAcross(oldRoot, newRoot, "a.txt", "sideways");
        Asserts.assertFalse("非法方向应拒绝", r.ok());
        Asserts.assertEquals("错误码应为 INVALID_PATH", "INVALID_PATH", r.code());
    }

    // ---------- resolveWithin 越界防护（直接验证解析器） ----------

    public void testResolveWithin_rejectsTraversal() throws IOException {
        Path root = mkDir();
        boolean threw = false;
        try {
            FileOps.resolveWithin(root, "../outside.txt");
        } catch (FileOps.InvalidPathException e) {
            threw = true;
        }
        Asserts.assertTrue(".. 穿越应抛异常", threw);

        threw = false;
        try {
            FileOps.resolveWithin(root, "sub/../../outside.txt");
        } catch (FileOps.InvalidPathException e) {
            threw = true;
        }
        Asserts.assertTrue("嵌套 .. 应抛异常", threw);

        threw = false;
        try {
            FileOps.resolveWithin(root, "C:/windows/evil.txt");
        } catch (FileOps.InvalidPathException e) {
            threw = true;
        }
        Asserts.assertTrue("绝对路径应抛异常", threw);

        threw = false;
        try {
            FileOps.resolveWithin(root, "");
        } catch (FileOps.InvalidPathException e) {
            threw = true;
        }
        Asserts.assertTrue("空 key 应抛异常", threw);
    }

    public void testResolveWithin_dirKey() throws IOException {
        Path root = mkDir();
        try {
            Path p = FileOps.resolveWithin(root, "sub/deep/");
            Asserts.assertTrue("目录 key 应解析到目录物理路径", p.endsWith("deep"));
            Asserts.assertTrue("路径应位于根内", p.startsWith(root.toAbsolutePath().normalize()));
        } catch (FileOps.InvalidPathException e) {
            Asserts.assertTrue("合法目录 key 不应抛异常: " + e.getMessage(), false);
        }
    }
}
