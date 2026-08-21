package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.ArchiveTree;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.server.CompareOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多层嵌套 ZIP 递归解包与对比的确定性回归测试（2026-08-19 修复）。
 *
 * <p>守护修复的两个核心问题：
 *  ① 包对比模式（直接比对两个 .zip/.war）下，顶层条目 outerEntry 是外层包内条目名，
 *     此前被当成磁盘路径返回 → ZipFile 打开失败（NoSuchFileException），嵌套归档无法解包；
 *  ② 目录结构（空目录/移动目录）未纳入比对，目录级新增/删除不可见。
 * </p>
 *
 * <p>覆盖：3 层嵌套（app.zip!/lib/bundle.zip!/deep/inner.zip!/data.txt）、
 * 目录结构差异、自动递归解包（recursiveUnpack）、深层内部条目内容 diff、
 * 单侧缺失（整侧 ADDED/DELETED）。</p>
 */
public final class NestedZipDiffTest {

    private static final CompareOptions OPTS = new CompareOptions();

    // ===================== 多层嵌套（3 层）=====================

    /** 文件夹模式：真实 app.zip 磁盘文件，递归展开 3 层嵌套 zip，逐层状态正确。 */
    public void testDeepNestingFolderMode() throws IOException {
        // 第 3 层（最内层）
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("data.txt", "v1".getBytes());
        innerOld.put("keep.txt", "same".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("data.txt", "v2".getBytes());       // MODIFIED
        innerNew.put("keep.txt", "same".getBytes());      // UNCHANGED
        innerNew.put("new.txt", "n".getBytes());          // ADDED

        // 第 2 层
        Map<String, byte[]> midOld = new LinkedHashMap<>();
        midOld.put("deep/inner.zip", TestFixtures.makeZip(innerOld));
        midOld.put("info.txt", "i-old".getBytes());
        Map<String, byte[]> midNew = new LinkedHashMap<>();
        midNew.put("deep/inner.zip", TestFixtures.makeZip(innerNew));
        midNew.put("info.txt", "i-old".getBytes());

        // 第 1 层（顶层归档）
        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.zip", TestFixtures.makeZip(midOld));
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.zip", TestFixtures.makeZip(midNew));

        PackageSnapshot oldSnap = folderSnap("app.zip", outerOld);
        PackageSnapshot newSnap = folderSnap("app.zip", outerNew);

        // L1：顶层展开出 bundle.zip（可继续展开）
        List<Map<String, Object>> l1 = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip");
        Map<String, Map<String, Object>> i1 = index(l1);
        Map<String, Object> bundle = i1.get("app.zip!/lib/bundle.zip");
        Asserts.assertNotNull("L1 应包含 lib/bundle.zip", bundle);
        Asserts.assertEquals("bundle.zip 应为 MODIFIED", "MODIFIED", bundle.get("status"));
        Asserts.assertEquals("bundle.zip 应可展开", Boolean.TRUE, bundle.get("expandable"));

        // L2：展开 bundle.zip
        List<Map<String, Object>> l2 = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip!/lib/bundle.zip");
        Map<String, Map<String, Object>> i2 = index(l2);
        Asserts.assertNotNull("L2 应含 deep/inner.zip", i2.get("app.zip!/lib/bundle.zip!/deep/inner.zip"));
        Asserts.assertEquals("inner.zip 应为 MODIFIED", "MODIFIED",
                i2.get("app.zip!/lib/bundle.zip!/deep/inner.zip").get("status"));
        Asserts.assertEquals("inner.zip 应可展开", Boolean.TRUE,
                i2.get("app.zip!/lib/bundle.zip!/deep/inner.zip").get("expandable"));
        Asserts.assertEquals("info.txt 应为 UNCHANGED", "UNCHANGED",
                i2.get("app.zip!/lib/bundle.zip!/info.txt").get("status"));

        // L3：展开 inner.zip，最内层文件状态正确
        List<Map<String, Object>> l3 = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip!/lib/bundle.zip!/deep/inner.zip");
        Map<String, Map<String, Object>> i3 = index(l3);
        Asserts.assertEquals("data.txt 应为 MODIFIED", "MODIFIED",
                i3.get("app.zip!/lib/bundle.zip!/deep/inner.zip!/data.txt").get("status"));
        Asserts.assertEquals("keep.txt 应为 UNCHANGED", "UNCHANGED",
                i3.get("app.zip!/lib/bundle.zip!/deep/inner.zip!/keep.txt").get("status"));
        Asserts.assertEquals("new.txt 应为 ADDED", "ADDED",
                i3.get("app.zip!/lib/bundle.zip!/deep/inner.zip!/new.txt").get("status"));
    }

    /** 包对比模式（直接比对两个 .zip，PackageParser 解析）：修复前 resolveTopArchive 把条目名当磁盘路径 → 解包失败。 */
    public void testDeepNestingPackageMode() throws IOException {
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("data.txt", "v1".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("data.txt", "v2".getBytes());

        Map<String, byte[]> midOld = new LinkedHashMap<>();
        midOld.put("deep/inner.zip", TestFixtures.makeZip(innerOld));
        Map<String, byte[]> midNew = new LinkedHashMap<>();
        midNew.put("deep/inner.zip", TestFixtures.makeZip(innerNew));

        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.zip", TestFixtures.makeZip(midOld));
        outerOld.put("README.txt", "readme".getBytes());
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.zip", TestFixtures.makeZip(midNew));
        outerNew.put("README.txt", "readme".getBytes());

        PackageParser pp = new PackageParser();
        PackageSnapshot oldSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerOld), "pkgOld"), new ParseConfig(), false);
        PackageSnapshot newSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerNew), "pkgNew"), new ParseConfig(), false);

        // 包模式下顶层节点 key 即包内条目名（无外层前缀）——修复前这里直接 IOException
        List<Map<String, Object>> l1 = ArchiveTree.computeChildren(oldSnap, newSnap, "lib/bundle.zip");
        Map<String, Map<String, Object>> i1 = index(l1);
        Map<String, Object> innerZip = i1.get("lib/bundle.zip!/deep/inner.zip");
        Asserts.assertNotNull("包模式 L1 应含 deep/inner.zip（修复点）", innerZip);
        Asserts.assertEquals("deep/inner.zip 应为 MODIFIED", "MODIFIED", innerZip.get("status"));
        Asserts.assertEquals("deep/inner.zip 应可展开", Boolean.TRUE, innerZip.get("expandable"));

        // 再下钻一层到最内层文件
        List<Map<String, Object>> l2 = ArchiveTree.computeChildren(oldSnap, newSnap, "lib/bundle.zip!/deep/inner.zip");
        Map<String, Map<String, Object>> i2 = index(l2);
        Asserts.assertEquals("最内层 data.txt 应为 MODIFIED", "MODIFIED",
                i2.get("lib/bundle.zip!/deep/inner.zip!/data.txt").get("status"));
    }

    // ===================== 自动递归解包 =====================

    /** recursiveUnpack：一次调用返回完整嵌套差异树（含 3 层嵌套），逐层状态正确。 */
    public void testRecursiveUnpackFullTree() throws IOException {
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("data.txt", "v1".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("data.txt", "v2".getBytes());

        Map<String, byte[]> midOld = new LinkedHashMap<>();
        midOld.put("deep/inner.zip", TestFixtures.makeZip(innerOld));
        Map<String, byte[]> midNew = new LinkedHashMap<>();
        midNew.put("deep/inner.zip", TestFixtures.makeZip(innerNew));

        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.zip", TestFixtures.makeZip(midOld));
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.zip", TestFixtures.makeZip(midNew));

        PackageParser pp = new PackageParser();
        PackageSnapshot oldSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerOld), "recOld"), new ParseConfig(), false);
        PackageSnapshot newSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerNew), "recNew"), new ParseConfig(), false);

        Map<String, Object> tree = ArchiveTree.recursiveUnpack(oldSnap, newSnap, "lib/bundle.zip");
        Asserts.assertEquals("根 key", "lib/bundle.zip", tree.get("key"));
        List<?> children = (List<?>) tree.get("children");
        Map<String, Object> deep = findNode(children, "lib/bundle.zip!/deep/inner.zip");
        Asserts.assertNotNull("递归树应含 deep/inner.zip", deep);
        Map<String, Object> data = findNode((List<?>) deep.get("children"), "lib/bundle.zip!/deep/inner.zip!/data.txt");
        Asserts.assertNotNull("递归树应穿透到最内层 data.txt", data);
        Asserts.assertEquals("最内层 data.txt 应为 MODIFIED", "MODIFIED", data.get("status"));
    }

    /** recursiveUnpack：节点数/深度护栏存在，深层不崩。 */
    public void testRecursiveUnpackGuards() throws IOException {
        Map<String, byte[]> leaf = new LinkedHashMap<>();
        leaf.put("x.txt", "x".getBytes());
        Map<String, byte[]> level = new LinkedHashMap<>();
        level.put("nested.zip", TestFixtures.makeZip(leaf));
        for (int i = 0; i < 20; i++) { // 构造 20 层嵌套，远超 MAX_RECURSION_DEPTH=12
            level = wrapNested(level);
        }
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("top.zip", TestFixtures.makeZip(level));

        PackageParser pp = new PackageParser();
        PackageSnapshot oldSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outer), "guardOld"), new ParseConfig(), false);
        PackageSnapshot newSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outer), "guardNew"), new ParseConfig(), false);

        // 深度触顶：不抛异常，返回树（可能为空 children，但根 key 正确）
        Map<String, Object> tree = ArchiveTree.recursiveUnpack(oldSnap, newSnap, "top.zip");
        Asserts.assertEquals("深度护栏下仍返回根 key", "top.zip", tree.get("key"));
    }

    // ===================== 目录结构对比 =====================

    /** 目录级差异：新增/删除目录（含空目录）应作为结构节点呈现并标记状态。 */
    public void testDirectoryStructureDiff() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("src/com/A.java", "a".getBytes());
        oldE.put("web/images/logo.png", new byte[]{1, 2, 3});
        oldE.put("empty/", new byte[0]); // 空目录（zip 显式目录条目）
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("src/com/A.java", "a".getBytes());
        newE.put("src/com/B.java", "b".getBytes()); // 新文件 → src/com/ 仍在，文件 ADDED
        newE.put("web/js/app.js", "j".getBytes());  // 新目录 web/js/
        // logo.png 删除 → web/images/ 目录删除；empty/ 目录删除

        PackageSnapshot oldSnap = folderSnap("app.zip", oldE);
        PackageSnapshot newSnap = folderSnap("app.zip", newE);

        List<Map<String, Object>> kids = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip");
        Map<String, Map<String, Object>> idx = index(kids);

        Asserts.assertEquals("目录 src/ 未变", "UNCHANGED", idx.get("app.zip!/src/").get("status"));
        Asserts.assertEquals("目录 web/images/ 删除", "DELETED", idx.get("app.zip!/web/images/").get("status"));
        Asserts.assertEquals("目录 web/js/ 新增", "ADDED", idx.get("app.zip!/web/js/").get("status"));
        Asserts.assertEquals("空目录 empty/ 删除", "DELETED", idx.get("app.zip!/empty/").get("status"));
        Asserts.assertEquals("目录节点不可展开", Boolean.FALSE, idx.get("app.zip!/src/").get("expandable"));
        Asserts.assertEquals("B.java 新增", "ADDED", idx.get("app.zip!/src/com/B.java").get("status"));
        Asserts.assertEquals("logo.png 删除", "DELETED", idx.get("app.zip!/web/images/logo.png").get("status"));
        Asserts.assertEquals("A.java 未变", "UNCHANGED", idx.get("app.zip!/src/com/A.java").get("status"));
    }

    // ===================== 深层内部条目内容 diff =====================

    /** 3 层嵌套后最内层文件的内容 diff（文本），应在包模式下可用。 */
    public void testDeepNestedInnerEntryContentDiff() throws IOException {
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("conf/app.properties", "port=8080\nname=old\n".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("conf/app.properties", "port=9090\nname=new\n".getBytes());

        Map<String, byte[]> midOld = new LinkedHashMap<>();
        midOld.put("deep/inner.zip", TestFixtures.makeZip(innerOld));
        Map<String, byte[]> midNew = new LinkedHashMap<>();
        midNew.put("deep/inner.zip", TestFixtures.makeZip(innerNew));

        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.zip", TestFixtures.makeZip(midOld));
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.zip", TestFixtures.makeZip(midNew));

        PackageParser pp = new PackageParser();
        PackageSnapshot oldSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerOld), "cntOld"), new ParseConfig(), false);
        PackageSnapshot newSnap = pp.parse(TestFixtures.writePackage(TestFixtures.makeZip(outerNew), "cntNew"), new ParseConfig(), false);

        DecompiledUnit u = ArchiveTree.computeInnerEntry(oldSnap, newSnap, OPTS,
                "lib/bundle.zip!/deep/inner.zip!/conf/app.properties", null, null);
        Asserts.assertTrue("深层文本内容 diff 应 ok：err=" + u.getError(), u.isOk());
        Asserts.assertTrue("diff 应含增删行", u.getDiffText().contains("-") && u.getDiffText().contains("+"));
    }

    // ===================== 单侧缺失 =====================

    /** 老侧缺失 → 全部 ADDED；新侧缺失 → 全部 DELETED。 */
    public void testSideMissingAllAddedOrDeleted() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("A.txt", "a".getBytes());
        PackageSnapshot only = folderSnap("app.zip", e);

        List<Map<String, Object>> added = ArchiveTree.computeChildren(null, only, "app.zip");
        Asserts.assertEquals("老侧缺失：A.txt 应为 ADDED", "ADDED", index(added).get("app.zip!/A.txt").get("status"));

        List<Map<String, Object>> deleted = ArchiveTree.computeChildren(only, null, "app.zip");
        Asserts.assertEquals("新侧缺失：A.txt 应为 DELETED", "DELETED", index(deleted).get("app.zip!/A.txt").get("status"));
    }

    // ===================== 夹具 =====================

    /** 文件夹模式快照：真实 zip 落盘，顶层条目 outerEntry=磁盘路径（EntrySource 非嵌套）。 */
    private static PackageSnapshot folderSnap(String zipName, Map<String, byte[]> entries) throws IOException {
        byte[] zip = TestFixtures.makeZip(entries);
        Path zipPath = TestFixtures.writePackage(zip, "snap");
        Map<String, LogicalEntry> map = new LinkedHashMap<>();
        map.put(zipName, new LogicalEntry(zipName, Layer.L0, FileClass.ARCHIVE,
                zip.length, "sha-" + zipName, new EntrySource(zipPath.toString(), null)));
        return new PackageSnapshot(zipPath, com.bempdiff.model.PackageType.WAR, "1.0", map);
    }

    /** 把某归档内容包一层外层 zip（用于构造超深嵌套）。 */
    private static Map<String, byte[]> wrapNested(Map<String, byte[]> inner) throws IOException {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("nested.zip", TestFixtures.makeZip(inner));
        return outer;
    }

    private static Map<String, Map<String, Object>> index(List<Map<String, Object>> kids) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> k : kids) m.put((String) k.get("key"), k);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findNode(List<?> nodes, String key) {
        if (nodes == null) return null;
        for (Object o : nodes) {
            Map<String, Object> n = (Map<String, Object>) o;
            if (key.equals(n.get("key"))) return n;
            Map<String, Object> sub = findNode((List<?>) n.get("children"), key);
            if (sub != null) return sub;
        }
        return null;
    }
}
