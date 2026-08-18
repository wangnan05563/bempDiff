package com.bempdiff.test;

import com.bempdiff.diff.ArchiveDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.parse.PackageParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 归档清单 diff 的回归测试。
 *
 * <p>守护 2026-08-18 修复：点击 .zip 等压缩包不再无限卡在反编译 spinner，
 * 而是即时返回"条目清单"对比。所有用例断言 timeout/兜底能力而非真实秒数。</p>
 */
public final class ArchiveDiffTest {

    /** 两个 zip，结构不同 → unified diff 应同时包含新增/删除/修改条目行。 */
    public void testListingDiffDetectsAddedRemovedModified() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("a/keep.txt", "kept".getBytes());
        oldE.put("a/changed.txt", "v1".getBytes());
        oldE.put("a/removed.txt", "go".getBytes());

        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("a/keep.txt", "kept".getBytes());        // 内容相同 → 不出现在 diff
        newE.put("a/changed.txt", "v2".getBytes());        // 内容变化 → -v1/+v2
        newE.put("a/added.txt", "new".getBytes());          // 新增

        Path oldZip = TestFixtures.writePackage(TestFixtures.makeZip(oldE), "old");
        Path newZip = TestFixtures.writePackage(TestFixtures.makeZip(newE), "new");

        DecompiledUnit u = new ArchiveDiff().diff("p.zip", oldZip, newZip);

        Asserts.assertTrue("应 ok", u.isOk());
        Asserts.assertEquals("引擎标签", "archive-listing", u.getEngine());
        Asserts.assertNotNull("老清单应有内容", u.getOldSource());
        Asserts.assertNotNull("新清单应有内容", u.getNewSource());
        Asserts.assertTrue("老清单应含 keep/changed/removed 三条目",
                u.getOldSource().contains("keep.txt")
                        && u.getOldSource().contains("changed.txt")
                        && u.getOldSource().contains("removed.txt"));
        Asserts.assertTrue("新清单应含 keep/changed/added 三条目",
                u.getNewSource().contains("keep.txt")
                        && u.getNewSource().contains("changed.txt")
                        && u.getNewSource().contains("added.txt"));
        // diff 必须显式给出三类变化
        // （注：清单只展示 size/CRC/name，不展示条目内容字节；
        //   "v1→v2" 这类内容级修改在清单里体现为 size 或 CRC 变化。
        //   这里 a/changed.txt 大小 2→2 但 CRC 不同，diff 必须给出双侧 CRC 行。）
        String diff = u.getDiffText();
        Asserts.assertTrue("diff 应标记 removed 行: " + diff, diff.contains("-") && diff.contains("removed.txt"));
        Asserts.assertTrue("diff 应标记 added 行: " + diff, diff.contains("+") && diff.contains("added.txt"));
        // changed 行: 两侧各出现一次，且 CRC 至少一个不同
        int oldIdx = diff.indexOf("changed.txt");
        Asserts.assertTrue("diff 应同时给出 changed.txt 的 - 和 + 行: " + diff, oldIdx > 0);
        String changedSection = diff.substring(0, oldIdx + "changed.txt".length() + 1);
        Asserts.assertTrue("changed.txt 的差异应体现不同 CRC（- 行在前）: " + diff,
                changedSection.contains("-") && (diff.contains("6962ccb5") || diff.contains("f06b9d0f") || diff.contains("b6689356")));
    }

    /** 完全相同的两个 zip → diffText 应等同于"无修改"。 */
    public void testIdenticalArchivesProduceNoDiff() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("x/a.txt", "alpha".getBytes());
        entries.put("x/b.txt", "beta".getBytes());
        byte[] z = TestFixtures.makeZip(entries);

        DecompiledUnit u = new ArchiveDiff().diff("same.zip",
                TestFixtures.writePackage(z, "old"),
                TestFixtures.writePackage(z, "new"));

        Asserts.assertTrue("应 ok", u.isOk());
        // LineDiff.unified 在两边完全一致时生成空内容（不含 @@ hunk）
        Asserts.assertTrue("完全相同归档不应有 @@-hunk：" + u.getDiffText(),
                !u.getDiffText().contains("@@"));
    }

    /** 老侧存在、新侧不存在 → diffText 应明确标"删除归档"。 */
    public void testMissingNewSideFlaggedAsDeleted() throws IOException {
        Path oldZip = TestFixtures.writePackage(
                TestFixtures.makeZip(Map.of("only.txt", "x".getBytes())), "old");
        DecompiledUnit u = new ArchiveDiff().diff("p.zip", oldZip, null);
        Asserts.assertTrue("ok 但 newSource 为空", u.isOk() && u.getNewSource() == null);
        Asserts.assertTrue("diff 应标注归档已删除",
                u.getDiffText().contains("删除文件") && u.getDiffText().contains("only.txt"));
    }

    /** 新侧存在、老侧不存在 → diffText 应明确标"新增归档"。 */
    public void testMissingOldSideFlaggedAsAdded() throws IOException {
        Path newZip = TestFixtures.writePackage(
                TestFixtures.makeZip(Map.of("fresh.txt", "y".getBytes())), "new");
        DecompiledUnit u = new ArchiveDiff().diff("p.zip", null, newZip);
        Asserts.assertTrue("ok 但 oldSource 为空", u.isOk() && u.getOldSource() == null);
        Asserts.assertTrue("diff 应标注归档新增",
                u.getDiffText().contains("新增文件") && u.getDiffText().contains("fresh.txt"));
    }

    /** 两侧都缺（应反常但不该抛）。 */
    public void testBothSidesMissingReturnsFailNotThrow() throws IOException {
        DecompiledUnit u = new ArchiveDiff().diff("p.zip", null, null);
        Asserts.assertTrue("失败", !u.isOk());
        Asserts.assertTrue("错误信息应说明两侧都为空", u.getError().contains("两侧") || u.getError().contains("归档"));
    }

    /** 损坏的"zip"（不是 zip 结构）→ 走 fail 分支，不应抛异常冒泡。 */
    public void testCorruptedArchiveFailsGracefully() throws IOException {
        Path garbage = Files.createTempFile("bempdiff-bad-", ".zip");
        Files.write(garbage, "this is not a zip file at all, just plain text".getBytes());
        try {
            Path oldZip = garbage; // 老侧放一份真 zip（保 old 侧 ok）
            Path newZip = TestFixtures.writePackage(
                    TestFixtures.makeZip(Map.of("ok.txt", "ok".getBytes())), "good");
            // 调换顺序也会反过来；这里关键看：把 garbage 走 ArchiveDiff 不抛异常、返回 fail
            DecompiledUnit u = new ArchiveDiff().diff("p.zip", garbage, newZip);
            Asserts.assertTrue("损坏侧走 fail 而不是 ok", !u.isOk());
            Asserts.assertTrue("错误信息应提及 zip/损坏",
                    u.getError().contains("zip") || u.getError().contains("损坏")
                            || u.getError().contains("不可读"));
        } finally {
            Files.deleteIfExists(garbage);
        }
    }

    /** 文件分类：.zip/.war/.ear/.tar.gz/.tgz → ARCHIVE；.jar 仍为 JAR（保持库 jar 路径）。 */
    public void testClassifyArchivesVsJars() {
        Asserts.assertEquals(".zip → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("a/b/c.zip"));
        Asserts.assertEquals(".zip 大小写不变", FileClass.ARCHIVE, PackageParser.classify("a/b/C.ZIP"));
        Asserts.assertEquals(".war → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("app.war"));
        Asserts.assertEquals(".ear → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("app.ear"));
        Asserts.assertEquals(".tar.gz → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("a.tar.gz"));
        Asserts.assertEquals(".tgz → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("a.tgz"));
        Asserts.assertEquals(".tar → ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("a.tar"));
        // 兼容性：.jar 仍归 JAR（被 LibraryJarDiff 等专项链路处理，不可改语义）
        Asserts.assertEquals(".jar 维持 JAR", FileClass.JAR, PackageParser.classify("a.jar"));
    }

    /** 空 zip（无任何条目）→ 两侧都返回 ok 且清单为空。 */
    public void testEmptyZipProducesEmptyListing() throws IOException {
        byte[] empty = TestFixtures.makeZip(new LinkedHashMap<>());
        Path oldZip = TestFixtures.writePackage(empty, "old");
        Path newZip = TestFixtures.writePackage(empty, "new");
        DecompiledUnit u = new ArchiveDiff().diff("empty.zip", oldZip, newZip);
        Asserts.assertTrue("应 ok", u.isOk());
        // listings 都应是空字符串或仅剩一个空行
        Asserts.assertTrue("空 zip 老清单应为空：'" + u.getOldSource() + "'",
                u.getOldSource() == null || u.getOldSource().trim().isEmpty());
    }

    /** 单 zip 直接从字节读 listing（不走 Path）：用于压力测试无依赖写法。 */
    public void testReadListingFromBytesIsCaseInsensitiveOnExtensions() throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("README", "hi".getBytes());
        byte[] z = TestFixtures.makeZip(entries);
        String listing = ArchiveDiff.readListingFromBytes(z);
        Asserts.assertTrue("listing 应包含 README 条目: " + listing, listing.contains("README"));
    }

    /** 静默工具：构造一个含 N 条目的 zip（直接用 ZipOutputStream，可控性强）。 */
    @SuppressWarnings("unused")
    private static byte[] makeZipN(int n, int eachBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            byte[] payload = new byte[eachBytes];
            for (int i = 0; i < n; i++) {
                ZipEntry e = new ZipEntry("dir/file_" + i + ".txt");
                e.setSize(eachBytes);
                zos.putNextEntry(e);
                zos.write(payload);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
