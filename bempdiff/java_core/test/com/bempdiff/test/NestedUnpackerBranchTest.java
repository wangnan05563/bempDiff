package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.unpack.NestedUnpacker;
import com.bempdiff.unpack.UnpackOptions;
import com.bempdiff.unpack.UnpackReport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link NestedUnpacker} 分支补充单测：聚焦 UnpackTest/NestedZipDiffTest 未覆盖的降级与异常路径——
 * 深度护栏降级为原子、累计字节熔断（容器级 totalBytesCap）、大条目流式落盘（streamEntryToDisk）、
 * 损坏容器展开报错（expand catch）、穿越条目名被拒（sanitize 抛出→root.read）、内存/磁盘原子分岔，
 * 以及 flatten 无 root 早返回、memo 计数辅助方法（memoizedBytesOf/releaseMemoized/globalMemoBytesNow）。
 */
public final class NestedUnpackerBranchTest {

    private static byte[] jar(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static PackageSnapshot parseOuter(Map<String, byte[]> outerEntries) throws Exception {
        byte[] zip = TestFixtures.makeZip(outerEntries);
        Path p = TestFixtures.writePackage(zip, "nu-outer");
        return new PackageParser().parse(p, new ParseConfig(), false);
    }

    private static NestedUnpacker unpacker(UnpackOptions opts) throws Exception {
        return new NestedUnpacker(opts, Files.createTempDirectory("nu-tmp"));
    }

    // ---------------- flatten 无 root：原样返回同一快照 ----------------

    public void testFlattenWithNoArchiveRootsReturnsSameSnapshot() throws Exception {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("readme.txt", "hello".getBytes(StandardCharsets.UTF_8));
        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(new UnpackOptions()).flatten(snap, report);
        Asserts.assertTrue("无容器应返回同一引用", flat == snap);
        Asserts.assertFalse("无容器不应标记不完整", report.isIncomplete());
    }

    // ---------------- 深度护栏：超深容器降级为原子条目保留 ----------------

    public void testDepthGuardDowngradesToAtomicEntry() throws Exception {
        Map<String, byte[]> deep = new LinkedHashMap<>();
        deep.put("com/hundsun/X.class", "deep biz".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> mid = new LinkedHashMap<>();
        mid.put("c.jar", jar(deep)); // b.jar 内层仍是容器
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("b.jar", jar(mid));

        UnpackOptions opts = new UnpackOptions();
        opts.maxDepth = 0; // root 深度 0 可开，第 1 层子容器 depth(1)>0 触顶
        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(opts).flatten(snap, report);

        Asserts.assertNotNull("深度触顶：内层容器 b.jar/c.jar 应作为原子保留",
                flat.getEntries().get("b.jar/c.jar"));
        Asserts.assertNull("深度触顶：不应继续下钻出 b.jar/c.jar 内文件",
                flat.getEntries().get("b.jar/c.jar/com/hundsun/X.class"));
        Asserts.assertEquals("触顶容器归类仍为 JAR", FileClass.JAR,
                flat.getEntries().get("b.jar/c.jar").getFileClass());
    }

    // ---------------- 累计字节熔断（容器级 totalBytesCap）----------------

    public void testTotalBytesCapFuseKeepsContainerAndMarksIncomplete() throws Exception {
        Map<String, byte[]> inner = new LinkedHashMap<>();
        inner.put("com/hundsun/X.class", "biz".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("b.jar", jar(inner));

        UnpackOptions opts = new UnpackOptions();
        opts.totalBytesCap = 1; // 容器一进入即超总量上限
        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(opts).flatten(snap, report);

        Asserts.assertNotNull("总量熔断：容器降级保留为 b.jar 原子条目", flat.getEntries().get("b.jar"));
        Asserts.assertNull("总量熔断：未展开内部文件", flat.getEntries().get("b.jar/com/hundsun/X.class"));
        Asserts.assertTrue("总量熔断应标记解包不完整", report.isIncomplete());
    }

    // ---------------- 大条目流式落盘 + 小条目内存原子分岔 ----------------

    public void testLargeEntryStreamsToDiskSmallEntryMemoized() throws Exception {
        byte[] big = new byte[100_000];
        for (int i = 0; i < big.length; i++) big[i] = (byte) ('A' + (i % 26));
        Map<String, byte[]> inner = new LinkedHashMap<>();
        inner.put("data/big.txt", big); // >64KB → streamEntryToDisk（磁盘原子）
        inner.put("data/small.txt", "tiny".getBytes(StandardCharsets.UTF_8)); // 小 → 内存原子
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("b.jar", jar(inner));

        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(new UnpackOptions()).flatten(snap, report);

        LogicalEntry bigLe = flat.getEntries().get("b.jar/data/big.txt");
        LogicalEntry smallLe = flat.getEntries().get("b.jar/data/small.txt");
        Asserts.assertNotNull("大条目应被展开为原子", bigLe);
        Asserts.assertNotNull("小条目应被展开为原子", smallLe);
        Asserts.assertEquals("大条目字节数正确", 100_000L, bigLe.getSize());
        Asserts.assertFalse("大条目落盘（非内存态）", bigLe.getSrc().isMemoryBacked());
        Asserts.assertTrue("小条目内存态", smallLe.getSrc().isMemoryBacked());

        byte[] read = new PackageParser().readEntryBytes(flat, bigLe);
        Asserts.assertEquals("磁盘直读大条目内容长度一致", 100_000, read.length);
        Asserts.assertEquals("磁盘直读首字节正确", (byte) 'A', read[0]);
    }

    // ---------------- 损坏容器：展开 ZipFile 失败记 expand 阶段错误 ----------------

    public void testCorruptContainerReportsExpandError() throws Exception {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("broken.jar", "this is definitely not a zip archive".getBytes(StandardCharsets.UTF_8));
        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(new UnpackOptions()).flatten(snap, report);

        Asserts.assertNotNull("损坏容器仍保留在快照中", flat.getEntries().get("broken.jar"));
        Asserts.assertTrue("损坏容器导致不完整", report.isIncomplete());
        boolean hasBrokenError = false;
        for (com.bempdiff.unpack.UnpackError err : report.getErrors()) {
            if ("broken.jar".equals(err.key)) hasBrokenError = true;
        }
        Asserts.assertTrue("应记录损坏容器的错误", hasBrokenError);
    }

    // ---------------- 穿越条目名：sanitize 抛出 → 该 root 记 root.read ----------------

    public void testTraversalEntryNameRejectedAsRootReadError() throws Exception {
        // 在 b.jar 内放一个含 ".." 的条目名；flatten 时 sanitize 抛 IllegalStateException，
        // 被 runExpansion 的 root 任务 catch(Exception) 归为 "root.read" 错误。
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("a/../evil.txt")); // 穿越段
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("b.jar", bos.toByteArray());
        PackageSnapshot snap = parseOuter(outer);
        UnpackReport report = new UnpackReport("zip");
        PackageSnapshot flat = unpacker(new UnpackOptions()).flatten(snap, report);

        Asserts.assertNull("穿越条目不应被平面化写入", flat.getEntries().get("b.jar/a/../evil.txt"));
        boolean rootReadErr = false;
        for (com.bempdiff.unpack.UnpackError err : report.getErrors()) {
            if ("b.jar".equals(err.key) && "root.read".equals(err.stage)) rootReadErr = true;
        }
        Asserts.assertTrue("应把 sanitize 拒绝归为该 root 的 root.read 错误", rootReadErr);
    }

    // ---------------- memo 辅助静态方法 ----------------

    public void testMemoHelperStatics() {
        Asserts.assertEquals("null 快照 memo 为 0", 0L, NestedUnpacker.memoizedBytesOf(null));
        Asserts.assertFalse("null 数据不可入内存", NestedUnpacker.tryMemoize(null));

        long base = NestedUnpacker.globalMemoBytesNow();
        NestedUnpacker.releaseMemoized(0);   // <=0 no-op
        NestedUnpacker.releaseMemoized(-100); // <=0 no-op
        Asserts.assertEquals("非正数回扣为空操作", base, NestedUnpacker.globalMemoBytesNow());

        // 正数回扣：先入 1000 再回扣 1000，回到原值
        NestedUnpacker.tryMemoize(new byte[1000]);
        long increased = NestedUnpacker.globalMemoBytesNow();
        Asserts.assertEquals("入内存后计数增加 1000", base + 1000, increased);
        NestedUnpacker.releaseMemoized(1000);
        Asserts.assertEquals("回扣后计数还原", base, NestedUnpacker.globalMemoBytesNow());
    }

    // ---------------- 构造入参为 null 的容错默认 ----------------

    public void testConstructorAcceptsNullOptionsAndTempRoot() throws Exception {
        NestedUnpacker u = new NestedUnpacker(null, null); // 均回退默认
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("a.txt", "x".getBytes(StandardCharsets.UTF_8));
        PackageSnapshot snap = parseOuter(outer);
        PackageSnapshot flat = u.flatten(snap, new UnpackReport("zip"));
        Asserts.assertNotNull("默认构造可正常 flatten", flat);
    }
}
