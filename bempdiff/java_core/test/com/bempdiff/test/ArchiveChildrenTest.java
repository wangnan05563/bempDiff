package com.bempdiff.test;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归解包（归档展开 + 内部条目 diff）的确定性回归测试。
 *
 * <p>守护 2026-08-18 起的「点击 zip 自动解包对比差异」能力：
 * 差异树点击归档 → /api/entry/children 返回内部条目清单（跨旧/新算 ADDED/DELETED/MODIFIED/UNCHANGED）；
 * 点击内部条目 → /api/entry/decompile 按扩展名分派 class 反编译 / 文本 diff / 嵌套归档清单。</p>
 *
 * <p>直接构造真实 PackageSnapshot（顶层归档条目指向磁盘 zip，EntrySource 非嵌套），
 * 绕过 HTTP 层对 ArchiveTree 纯逻辑做断言。</p>
 */
public final class ArchiveChildrenTest {

    private static final CompareOptions OPTS = new CompareOptions();

    /** 顶层归档 children：旧/新两侧结构不同 → 应正确标出 MODIFIED/DELETED/ADDED/UNCHANGED。 */
    public void testComputeChildrenStatuses() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("A.class", "old-bytes-a".getBytes());
        oldE.put("B.txt", "same".getBytes());
        oldE.put("C.txt", "v1".getBytes());
        oldE.put("gone.txt", "x".getBytes());

        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("A.class", "new-bytes-a".getBytes()); // 修改（内容不同）
        newE.put("B.txt", "same".getBytes());            // 未变
        newE.put("C.txt", "v1".getBytes());              // 未变
        newE.put("added.txt", "y".getBytes());           // 新增

        PackageSnapshot oldSnap = snap("app.zip", oldE);
        PackageSnapshot newSnap = snap("app.zip", newE);

        List<Map<String, Object>> kids = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip");

        Map<String, Map<String, Object>> byKey = index(kids);
        Asserts.assertTrue("应包含 A.class", byKey.containsKey("app.zip!/A.class"));
        Asserts.assertTrue("应包含 B.txt", byKey.containsKey("app.zip!/B.txt"));
        Asserts.assertTrue("应包含 gone.txt(DELETED)", byKey.containsKey("app.zip!/gone.txt"));
        Asserts.assertTrue("应包含 added.txt(ADDED)", byKey.containsKey("app.zip!/added.txt"));

        Asserts.assertEquals("A.class 应为 MODIFIED", "MODIFIED", byKey.get("app.zip!/A.class").get("status"));
        Asserts.assertEquals("B.txt 应为 UNCHANGED", "UNCHANGED", byKey.get("app.zip!/B.txt").get("status"));
        Asserts.assertEquals("gone.txt 应为 DELETED", "DELETED", byKey.get("app.zip!/gone.txt").get("status"));
        Asserts.assertEquals("added.txt 应为 ADDED", "ADDED", byKey.get("app.zip!/added.txt").get("status"));

        // A.class 是 class → expandable=false（不可再展开）
        Asserts.assertEquals("A.class 不应可展开", Boolean.FALSE, byKey.get("app.zip!/A.class").get("expandable"));
    }

    /** 顶层归档内嵌一个 zip（嵌套归档）→ 该内部条目 expandable=true，且能递归展开。 */
    public void testNestedZipExpandableAndRecurse() throws IOException {
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("nested/A.class", "a1".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("nested/A.class", "a2".getBytes()); // 修改

        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("lib/bundle.zip", TestFixtures.makeZip(innerOld));
        Map<String, byte[]> outer2 = new LinkedHashMap<>();
        outer2.put("lib/bundle.zip", TestFixtures.makeZip(innerNew));

        PackageSnapshot oldSnap = snap("app.zip", outer);
        PackageSnapshot newSnap = snap("app.zip", outer2);

        List<Map<String, Object>> kids = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip");
        Map<String, Map<String, Object>> byKey = index(kids);
        Map<String, Object> bundle = byKey.get("app.zip!/lib/bundle.zip");
        Asserts.assertNotNull("应展开出 lib/bundle.zip", bundle);
        Asserts.assertEquals("bundle.zip 状态 MODIFIED", "MODIFIED", bundle.get("status"));
        Asserts.assertEquals("bundle.zip 可继续展开", Boolean.TRUE, bundle.get("expandable"));

        // 递归展开嵌套 zip
        List<Map<String, Object>> inner = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip!/lib/bundle.zip");
        Map<String, Map<String, Object>> innerIdx = index(inner);
        Asserts.assertNotNull("嵌套 zip 内应含 nested/A.class", innerIdx.get("app.zip!/lib/bundle.zip!/nested/A.class"));
        Asserts.assertEquals("嵌套 A.class 应为 MODIFIED",
                "MODIFIED", innerIdx.get("app.zip!/lib/bundle.zip!/nested/A.class").get("status"));
    }

    /** 点击内部 class 条目 → 走 decompileBytes，返回 ok 的 DecompiledUnit。 */
    public void testInnerClassDecompile() throws IOException {
        byte[] clsV1 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        byte[] clsV2 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V2);

        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("WEB-INF/classes/com/internal/A.class", clsV1);
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("WEB-INF/classes/com/internal/A.class", clsV2);

        PackageSnapshot oldSnap = snap("app.zip", oldE);
        PackageSnapshot newSnap = snap("app.zip", newE);

        DecompiledUnit u = ArchiveTree.computeInnerEntry(oldSnap, newSnap, OPTS,
                "app.zip!/WEB-INF/classes/com/internal/A.class", null, null);
        Asserts.assertTrue("内部 class 反编译应 ok：engine=" + u.getEngine() + " err=" + u.getError(), u.isOk());
        Asserts.assertNotNull("应给出 diff 文本", u.getDiffText());
    }

    /** 点击内部文本条目（.txt）→ 走 FrontendTextDiff.diffBytes，返回 ok。 */
    public void testInnerTextDiff() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("conf/app.properties", "name=old\nport=8080\n".getBytes());
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("conf/app.properties", "name=new\nport=9090\n".getBytes());

        PackageSnapshot oldSnap = snap("app.zip", oldE);
        PackageSnapshot newSnap = snap("app.zip", newE);

        DecompiledUnit u = ArchiveTree.computeInnerEntry(oldSnap, newSnap, OPTS,
                "app.zip!/conf/app.properties", null, null);
        Asserts.assertTrue("内部文本 diff 应 ok：err=" + u.getError(), u.isOk());
        Asserts.assertTrue("diff 应含新增/删除行", u.getDiffText().contains("-") || u.getDiffText().contains("+"));
    }

    /** 内部条目在两侧都不存在 → fail，且错误信息含"两侧"。 */
    public void testInnerEntryMissingBothSides() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("keep.txt", "x".getBytes());
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("keep.txt", "x".getBytes());

        PackageSnapshot oldSnap = snap("app.zip", oldE);
        PackageSnapshot newSnap = snap("app.zip", newE);

        DecompiledUnit u = ArchiveTree.computeInnerEntry(oldSnap, newSnap, OPTS,
                "app.zip!/ghost.txt", null, null);
        Asserts.assertTrue("两侧缺失应 fail", !u.isOk());
        Asserts.assertTrue("错误应说明两侧缺失", u.getError().contains("两侧"));
    }

    /** 非内部条目 key（无 !/）→ computeInnerEntry 直接 fail。 */
    public void testNonCompoundKeyRejected() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("keep.txt", "x".getBytes());
        PackageSnapshot s = snap("app.zip", e);
        DecompiledUnit u = ArchiveTree.computeInnerEntry(s, s, OPTS, "app.zip", null, null);
        Asserts.assertTrue("顶层 key 不应走内部 diff", !u.isOk());
    }

    /** 内部二进制条目（非 class/文本/归档）→ fail，提示不支持。 */
    public void testInnerBinaryUnsupported() throws IOException {
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("img.bin", new byte[]{0, 1, 2, 3}); // 未知扩展 → OTHER，非文本
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("img.bin", new byte[]{0, 1, 2, 9});
        PackageSnapshot oldSnap = snap("app.zip", oldE);
        PackageSnapshot newSnap = snap("app.zip", newE);
        DecompiledUnit u = ArchiveTree.computeInnerEntry(oldSnap, newSnap, OPTS,
                "app.zip!/img.bin", null, null);
        Asserts.assertTrue("二进制内部条目应 fail", !u.isOk());
        Asserts.assertTrue("错误应说明不支持", u.getError().contains("不支持") || u.getError().contains("二进制"));
    }

    /** 比对级忽略扩展名应作用于嵌套归档内部：jar 内的 META-INF/MANIFEST.MF 在展开列表中被跳过。
     *  守护用户反馈"zip 下嵌套 jar 包与 jar 包中的 .MF 未生效"。 */
    public void testIgnoreExtensionsFiltersNestedMf() throws IOException {
        // 内层：一个 jar，内含 MANIFEST.MF + 一个正常 class
        Map<String, byte[]> jar = new LinkedHashMap<>();
        jar.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        jar.put("com/internal/B.class", "b1".getBytes());
        Map<String, byte[]> jar2 = new LinkedHashMap<>();
        jar2.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nChanged\n".getBytes());
        jar2.put("com/internal/B.class", "b2".getBytes());

        // 外层：app.zip 只含 lib/bundle.jar
        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.jar", TestFixtures.makeZip(jar));
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.jar", TestFixtures.makeZip(jar2));
        PackageSnapshot oldSnap = snap("app.zip", outerOld);
        PackageSnapshot newSnap = snap("app.zip", outerNew);

        // 不忽略默认：jar 内 MANIFEST.MF 应展开出来
        List<Map<String, Object>> rawInner = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip!/lib/bundle.jar");
        Map<String, Map<String, Object>> rawIdx = index(rawInner);
        Asserts.assertNotNull("默认应含 jar 内 MANIFEST.MF", rawIdx.get("app.zip!/lib/bundle.jar!/META-INF/MANIFEST.MF"));

        // 忽略 .mf：MANIFEST.MF 从展开列表消失，class 保留
        java.util.List<String> ignores = new ArrayList<>();
        ignores.add(".mf");
        List<Map<String, Object>> filtered = ArchiveTree.computeChildren(oldSnap, newSnap, "app.zip!/lib/bundle.jar", ignores);
        Map<String, Map<String, Object>> filteredIdx = index(filtered);
        Asserts.assertNull("忽略 .mf 后 jar 内 MANIFEST.MF 不应展开", filteredIdx.get("app.zip!/lib/bundle.jar!/META-INF/MANIFEST.MF"));
        Asserts.assertNotNull("忽略 .mf 不应误伤 jar 内 class", filteredIdx.get("app.zip!/lib/bundle.jar!/com/internal/B.class"));
    }

    // ===================== 夹具 =====================

    /** 用 条目名->字节 构造一个归档：把 zip 落盘，并在快照里登记一个顶层归档条目
     *  （key=zipName，EntrySource 指向磁盘 zip），与真实比对产物结构一致。 */
    private static PackageSnapshot snap(String zipName, Map<String, byte[]> entries) throws IOException {
        byte[] zip = TestFixtures.makeZip(entries);
        Path zipPath = TestFixtures.writePackage(zip, "snap");
        Map<String, LogicalEntry> map = new LinkedHashMap<>();
        // 顶层归档条目本身（children/inner 解析时按 zipName 定位到磁盘 zip）
        map.put(zipName, new LogicalEntry(zipName, Layer.L0, FileClass.ARCHIVE,
                zip.length, "sha-" + zipName, new EntrySource(zipPath.toString(), null)));
        return new PackageSnapshot(zipPath, com.bempdiff.model.PackageType.WAR, "1.0", map);
    }

    private static Map<String, Map<String, Object>> index(List<Map<String, Object>> kids) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> k : kids) m.put((String) k.get("key"), k);
        return m;
    }
}
