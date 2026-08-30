package com.bempdiff.test;

import com.bempdiff.unpack.UnpackError;
import com.bempdiff.unpack.UnpackOptions;
import com.bempdiff.unpack.UnpackReport;

/** 解包数据模型（UnpackOptions/UnpackError/UnpackReport）结构测试（M-A Task1）。 */
public final class UnpackTest {

    public void testUnpackModels() {
        UnpackReport r = new UnpackReport("war");
        r.setThreadCount(4);
        r.addSuccess("WEB-INF/lib/a.jar");
        r.addError(new UnpackError("WEB-INF/lib/b.jar/inner/x.xml", "extract", "zip bomb guard"));
        r.finish();
        Asserts.assertEquals("错误应聚合 1 条", 1, r.getErrors().size());
        Asserts.assertEquals("threadCount 透出", 4, r.getThreadCount());
        Asserts.assertEquals("fileCount=成功+失败", 2, r.getFileCount());
        String json = r.toJson();
        Asserts.assertContains("JSON 应含线程数", json, "\"threadCount\":4");
        Asserts.assertContains("JSON 应含文件状态", json, "extract");
        Asserts.assertContains("JSON 应含错误消息", json, "zip bomb guard");
    }

    public void testUnpackOptionsDefaults() {
        UnpackOptions o = new UnpackOptions();
        Asserts.assertTrue("默认线程池为 4", o.threadPoolSize == 4);
        Asserts.assertTrue("存在深度护栏", o.maxDepth > 0);
    }

    /** M-A Task2：对嵌套 zip->jar->class 做物理平铺展开，断言平面 key 与磁盘直读。 */
    public void testNestedFlatten() {
        try {
            java.nio.file.Path outer = makeNestedZip();
            com.bempdiff.parse.PackageParser pp = new com.bempdiff.parse.PackageParser();
            com.bempdiff.model.PackageSnapshot snap = pp.parse(outer, new com.bempdiff.config.ParseConfig(), false);
            com.bempdiff.unpack.NestedUnpacker u = new com.bempdiff.unpack.NestedUnpacker(
                    new UnpackOptions(), java.nio.file.Files.createTempDirectory("bempdiff-ut"));
            com.bempdiff.model.PackageSnapshot flat = u.flatten(snap, new UnpackReport("zip"));
            com.bempdiff.model.LogicalEntry e = flat.getEntries().get("b.jar/com/hundsun/X.class");
            Asserts.assertNotNull("嵌套 class 应平面展开", e);
            Asserts.assertEquals("fileClass=CLASS", com.bempdiff.model.FileClass.CLASS, e.getFileClass());
            Asserts.assertEquals("内部业务码 layer=L1", com.bempdiff.model.Layer.L1, e.getLayer());
            byte[] disk = pp.readEntryBytes(flat, e);
            Asserts.assertContains("磁盘直读内容正确", new String(disk, java.nio.charset.StandardCharsets.UTF_8), "biz");
        } catch (Exception ex) {
            Asserts.fail("嵌套平铺失败: " + ex);
        }
    }

    /** A/B：解包进度回调——带 UnpackProgress 的 flatten 应对每个 root 容器上报 done/total，且 done 不越界、最终 done==total。 */
    public void testNestedFlattenProgress() {
        try {
            java.nio.file.Path outer = makeMultiJarZip(); // 两个顶层 jar → 2 个 root 容器
            com.bempdiff.parse.PackageParser pp = new com.bempdiff.parse.PackageParser();
            com.bempdiff.model.PackageSnapshot snap = pp.parse(outer, new com.bempdiff.config.ParseConfig(), false);
            final java.util.List<int[]> events = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            com.bempdiff.unpack.NestedUnpacker u = new com.bempdiff.unpack.NestedUnpacker(
                    new UnpackOptions(), java.nio.file.Files.createTempDirectory("bempdiff-utp"));
            // 进度回调在解包线程池线程上被并发调用：用同步容器收集，随后统一断言
            com.bempdiff.model.PackageSnapshot flat = u.flatten(snap, new UnpackReport("zip"),
                    new com.bempdiff.unpack.NestedUnpacker.UnpackProgress() {
                        @Override public void onProgress(int done, int total) {
                            events.add(new int[]{done, total});
                        }
                    });
            Asserts.assertEquals("root 总数=顶层 jar 数", 2, collectTotal(events));
            Asserts.assertTrue("应至少回调一次", !events.isEmpty());
            int[] last = events.get(events.size() - 1);
            for (int[] ev : events) {
                Asserts.assertTrue("done 不越界且为正", ev[0] >= 1 && ev[0] <= ev[1]);
                Asserts.assertEquals("每次回调 total 恒定", 2, ev[1]);
            }
            Asserts.assertEquals("解包全部完成后最终 done==total", last[0], last[1]);
            Asserts.assertNotNull("带进度回调解包仍应展开嵌套 class",
                    flat.getEntries().get("alpha.jar/com/hundsun/ALPHA.class"));
            Asserts.assertNotNull("另一侧 root 也应展开",
                    flat.getEntries().get("beta.jar/com/hundsun/BETA.class"));
        } catch (Exception ex) {
            Asserts.fail("解包进度回调测试失败: " + ex);
        }
    }

    /** 从回调事件中取最大 total（即应布满的 root 容器总数）。 */
    private static int collectTotal(java.util.List<int[]> events) {
        int total = 0;
        for (int[] ev : events) total = Math.max(total, ev[1]);
        return total;
    }

    /** 构造含两个顶层 jar 的 zip（2 个 root 容器），用于进度回调 done/total 断言。 */
    private static java.nio.file.Path makeMultiJarZip() throws Exception {
        java.nio.file.Path outer = java.nio.file.Files.createTempFile("bempdiff-roots", ".zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                java.nio.file.Files.newOutputStream(outer))) {
            for (String jar : new String[]{"alpha", "beta"}) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                try (java.util.zip.ZipOutputStream iz = new java.util.zip.ZipOutputStream(b)) {
                    iz.putNextEntry(new java.util.zip.ZipEntry("com/hundsun/" + jar.toUpperCase() + ".class"));
                    iz.write(("biz " + jar).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    iz.closeEntry();
                }
                zos.putNextEntry(new java.util.zip.ZipEntry(jar + ".jar"));
                zos.write(b.toByteArray());
                zos.closeEntry();
            }
        }
        return outer;
    }

    /** H1：扁平化解包也应应用比对级忽略扩展名——嵌套 jar 内的 MANIFEST.MF 不应进入最终快照。 */
    public void testNestedFlattenFiltersIgnoredExt() {
        try {
            java.nio.file.Path outer = makeNestedZipWithManifest();
            com.bempdiff.parse.PackageParser pp = new com.bempdiff.parse.PackageParser();
            com.bempdiff.model.PackageSnapshot snap = pp.parse(outer, new com.bempdiff.config.ParseConfig(), false);

            // 不忽略默认：flatten 后 MANIFEST.MF 应存在
            UnpackOptions o1 = new UnpackOptions();
            com.bempdiff.model.PackageSnapshot flat1 = new com.bempdiff.unpack.NestedUnpacker(
                    o1, java.nio.file.Files.createTempDirectory("bempdiff-ut1"))
                    .flatten(snap, new UnpackReport("zip"));
            Asserts.assertNotNull("默认应展开 MANIFEST.MF",
                    flat1.getEntries().get("b.jar/META-INF/MANIFEST.MF"));

            // 忽略 .mf：flatten 后 MANIFEST.MF 消失，class 保留
            UnpackOptions o2 = new UnpackOptions();
            java.util.List<String> ig = new java.util.ArrayList<>();
            ig.add(".mf");
            o2.ignoreExtensions = ig;
            com.bempdiff.model.PackageSnapshot flat2 = new com.bempdiff.unpack.NestedUnpacker(
                    o2, java.nio.file.Files.createTempDirectory("bempdiff-ut2"))
                    .flatten(snap, new UnpackReport("zip"));
            Asserts.assertNull("忽略 .mf 后扁平化不应含 MANIFEST.MF",
                    flat2.getEntries().get("b.jar/META-INF/MANIFEST.MF"));
            Asserts.assertNotNull("忽略 .mf 不应误伤嵌套 class",
                    flat2.getEntries().get("b.jar/com/hundsun/X.class"));
        } catch (Exception ex) {
            Asserts.fail("嵌套忽略过滤失败: " + ex);
        }
    }

    /** 构造 a.zip（内含 b.jar，其中 META-INF/MANIFEST.MF + com/hundsun/X.class）。 */
    private static java.nio.file.Path makeNestedZipWithManifest() throws Exception {
        java.io.ByteArrayOutputStream jarBytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(jarBytes)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("com/hundsun/X.class"));
            zos.write("class biz content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        java.nio.file.Path outer = java.nio.file.Files.createTempFile("bempdiff-b", ".zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                java.nio.file.Files.newOutputStream(outer))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("b.jar"));
            zos.write(jarBytes.toByteArray());
            zos.closeEntry();
        }
        return outer;
    }

    /** 构造 a.zip（内含 b.jar，b.jar 内含 com/hundsun/X.class 文本字节），返回 a.zip 路径。 */
    private static java.nio.file.Path makeNestedZip() throws Exception {
        java.io.ByteArrayOutputStream jarBytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(jarBytes)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("com/hundsun/X.class"));
            zos.write("class biz content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        java.nio.file.Path outer = java.nio.file.Files.createTempFile("bempdiff-a", ".zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                java.nio.file.Files.newOutputStream(outer))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("b.jar"));
            zos.write(jarBytes.toByteArray());
            zos.closeEntry();
        }
        return outer;
    }

    /** M-B：解包报告落盘（.json/.log）+ Job 绑定 + JSON 可解析断言。 */
    public void testUnpackOutputerAndJob() {
        try {
            UnpackReport r0 = new UnpackReport("zip");
            r0.setThreadCount(4);
            r0.addSuccess("a.jar/com/X.class");
            r0.addError(new UnpackError("a.jar/bad/x.xml", "expand", "zip corrupt"));
            r0.finish();
            UnpackReport r1 = new UnpackReport("zip");
            r1.setThreadCount(4);
            r1.addSuccess("b.jar/com/Y.class");
            r1.finish();
            com.bempdiff.server.Job job = new com.bempdiff.server.Job("J-01", "package",
                    new com.bempdiff.server.CompareOptions());
            job.setUnpackReports(new UnpackReport[]{r0, r1});
            Asserts.assertNotNull("Job 应持有报告", job.getUnpackReports());
            Asserts.assertEquals("旧侧报告透出", r0, job.getUnpackReports()[0]);
            java.nio.file.Path logs = java.nio.file.Files.createTempDirectory("bempdiff-logs");
            com.bempdiff.unpack.UnpackOutputer.write(job.id, job.getUnpackReports(), logs);
            java.nio.file.Path json = logs.resolve("unpack-J-01.json");
            java.nio.file.Path log = logs.resolve("unpack-J-01.log");
            Asserts.assertTrue("JSON 应落盘", java.nio.file.Files.exists(json));
            Asserts.assertTrue("错误日志应落盘", java.nio.file.Files.exists(log));
            String jtxt = new String(java.nio.file.Files.readAllBytes(json),
                    java.nio.charset.StandardCharsets.UTF_8);
            Asserts.assertContains("JSON 含 fileCount", jtxt, "\"fileCount\"");
            Asserts.assertContains("JSON 含错误 message", jtxt, "\"message\"");
            String ltxt = new String(java.nio.file.Files.readAllBytes(log),
                    java.nio.charset.StandardCharsets.US_ASCII);
            Asserts.assertTrue("日志含 [ERROR] 行", ltxt.contains("[ERROR]"));
        } catch (Exception ex) {
            Asserts.fail("解包报告/Job 测试失败: " + ex);
        }
    }

    /** 内存态原子边界：小文件可入内存，超阈值/空回退磁盘（tryMemoize）。 */
    public void testTryMemoizeBoundary() {
        Asserts.assertTrue("小文件应走内存", com.bempdiff.unpack.NestedUnpacker.tryMemoize(new byte[16]));
        Asserts.assertFalse("超过单文件上限应落盘",
                com.bempdiff.unpack.NestedUnpacker.tryMemoize(new byte[64 * 1024 + 1]));
        Asserts.assertFalse("空字节回退磁盘", com.bempdiff.unpack.NestedUnpacker.tryMemoize(null));
    }

    /** 作业级运行时目录回收：cleanupRuntime 递归删除由 runCompare 生成的解包临时目录。 */
    public void testJobCleanupRuntime() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("bempdiff-runtime-jt");
            java.nio.file.Files.createDirectories(dir.resolve("old"));
            java.nio.file.Files.write(dir.resolve("old/atom.bin"), new byte[]{1, 2, 3});
            com.bempdiff.server.Job job = new com.bempdiff.server.Job("J-CLEAN", "package",
                    new com.bempdiff.server.CompareOptions());
            job.setRuntimeDir(dir);
            job.cleanupRuntime();
            Asserts.assertFalse("运行时目录应被整体删除", java.nio.file.Files.exists(dir));
        } catch (Exception ex) {
            Asserts.fail("作业运行时回收测试失败: " + ex);
        }
    }
}