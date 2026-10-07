package com.bempdiff.test;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.LibJarDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 差异依赖 JAR 内部源码对比引擎（LibJarDiff）单测：识别过滤、逐 class 状态判定
 * （新增/删除/修改/未变）、jar 级状态（新增/删除/修改/未变）、失败隔离、Top-K 全局预算递减、
 * 懒加载读字节、结果模型访问器。纯逻辑，反编译路径用进程内 CFR（真实 class 字节，稳定）。
 */
public final class LibJarDiffTest {

    private static final String JAR_KEY = "WEB-INF/lib/deep.jar";

    private static PackageSnapshot snap(LogicalEntry... entries) {
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        for (LogicalEntry e : entries) m.put(e.getKey(), e);
        return new PackageSnapshot(Path.of("dummy.war"), null, "1.0", m);
    }

    /** 内存态 lib jar 条目：readEntryBytes 会直接返回这些字节（不落盘、不开归档）。 */
    private static LogicalEntry libEntry(String key, byte[] jarBytes, String sha) {
        return new LogicalEntry(key, Layer.L2, FileClass.JAR, jarBytes.length, sha,
                EntrySource.memoryBacked(jarBytes));
    }

    private static byte[] jar(Map<String, byte[]> inner) throws Exception {
        return TestFixtures.makeZip(inner);
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static DiffStatus statusOf(LibJarDiff.DiffJarInfo info, String inner) {
        for (LibJarDiff.LibClassUnit u : info.classes) {
            if (u.innerClass.equals(inner)) return u.status;
        }
        throw new AssertionError("未找到内部 class: " + inner);
    }

    private static Decompiler newDec() {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        return new Decompiler(null, javaBin);
    }

    // --------------------------- 识别过滤 ---------------------------

    public void testIdentifyDiffJars_filtersAndOrder() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/lib/mod.jar");
        r.put(DiffStatus.MODIFIED, "WEB-INF/classes/keep.class"); // 非 jar
        r.put(DiffStatus.MODIFIED, "WEB-INF/other/edge.jar");      // jar 但不在 /lib/
        r.put(DiffStatus.ADDED, "WEB-INF/lib/add.jar");
        r.put(DiffStatus.DELETED, "WEB-INF/lib/del.jar");
        r.put(DiffStatus.UNCHANGED, "WEB-INF/lib/same.jar");       // 未变不纳入

        List<String> out = LibJarDiff.identifyDiffJars(r);
        Asserts.assertEquals("仅 3 个 /lib/ 下差异 jar", 3, out.size());
        Asserts.assertEquals("MODIFIED 优先", "WEB-INF/lib/mod.jar", out.get(0));
        Asserts.assertEquals("ADDED 次之", "WEB-INF/lib/add.jar", out.get(1));
        Asserts.assertEquals("DELETED 最后", "WEB-INF/lib/del.jar", out.get(2));
        Asserts.assertFalse("不应含 class", out.toString().contains("keep.class"));
        Asserts.assertFalse("不应含非 lib 的 jar", out.toString().contains("edge.jar"));
        Asserts.assertFalse("不应含未变 jar", out.toString().contains("same.jar"));
    }

    // --------------------------- 单 jar 逐 class 状态矩阵 ---------------------------

    public void testAnalyzeJar_statusMatrix() throws Exception {
        Map<String, byte[]> oldJar = new LinkedHashMap<>();
        oldJar.put("com/x/A.class", b("A-old"));
        oldJar.put("com/x/B.class", b("B-same"));
        oldJar.put("com/x/D.class", b("D-only-old"));
        Map<String, byte[]> newJar = new LinkedHashMap<>();
        newJar.put("com/x/A.class", b("A-new"));   // MODIFIED
        newJar.put("com/x/B.class", b("B-same"));   // UNCHANGED
        newJar.put("com/x/C.class", b("C-only-new")); // ADDED
        byte[] oj = jar(oldJar);
        byte[] nj = jar(newJar);

        PackageSnapshot old = snap(libEntry(JAR_KEY, oj, "shaOld"));
        PackageSnapshot nu = snap(libEntry(JAR_KEY, nj, "shaNew"));

        // topK=0 → 不触发反编译，单元 unit 均为 null（覆盖非反编译主路径）
        LibJarDiff.DiffJarInfo info = LibJarDiff.analyzeJar(old, nu, JAR_KEY, newDec(), 0);
        Asserts.assertFalse("不应判失败", info.failed);
        Asserts.assertEquals("jar 自身状态=MODIFIED", DiffStatus.MODIFIED, info.jarStatus);
        Asserts.assertEquals("新增 1", 1, info.added);
        Asserts.assertEquals("删除 1", 1, info.removed);
        Asserts.assertEquals("修改 1", 1, info.modified);
        Asserts.assertEquals("未变 1", 1, info.unchanged);
        Asserts.assertEquals("单元总数 4", 4, info.classes.size());
        Asserts.assertEquals("逐 class 状态 A", DiffStatus.MODIFIED, statusOf(info, "com/x/A.class"));
        Asserts.assertEquals("逐 class 状态 B", DiffStatus.UNCHANGED, statusOf(info, "com/x/B.class"));
        Asserts.assertEquals("逐 class 状态 C", DiffStatus.ADDED, statusOf(info, "com/x/C.class"));
        Asserts.assertEquals("逐 class 状态 D", DiffStatus.DELETED, statusOf(info, "com/x/D.class"));
        Asserts.assertEquals("topK=0 无反编译", 0, info.getDecompiledCount());
        for (LibJarDiff.LibClassUnit u : info.classes) Asserts.assertNull("unit 应为 null", u.unit);
    }

    // --------------------------- jar 级：新增 / 删除 / 未变 ---------------------------

    public void testAnalyzeJar_addedJarAllClassesAdded() throws Exception {
        Map<String, byte[]> nj = new LinkedHashMap<>();
        nj.put("com/x/A.class", b("a"));
        nj.put("com/x/B.class", b("b"));
        PackageSnapshot old = snap(); // 老侧无此 jar
        PackageSnapshot nu = snap(libEntry(JAR_KEY, jar(nj), "shaNew"));
        LibJarDiff.DiffJarInfo info = LibJarDiff.analyzeJar(old, nu, JAR_KEY, newDec(), 0);
        Asserts.assertEquals("jar=ADDED", DiffStatus.ADDED, info.jarStatus);
        Asserts.assertEquals("全部新增", 2, info.added);
        Asserts.assertEquals("无删除", 0, info.removed);
        Asserts.assertEquals("无修改", 0, info.modified);
    }

    public void testAnalyzeJar_deletedJarAllClassesRemoved() throws Exception {
        Map<String, byte[]> oj = new LinkedHashMap<>();
        oj.put("com/x/A.class", b("a"));
        PackageSnapshot old = snap(libEntry(JAR_KEY, jar(oj), "shaOld"));
        PackageSnapshot nu = snap(); // 新侧无此 jar
        LibJarDiff.DiffJarInfo info = LibJarDiff.analyzeJar(old, nu, JAR_KEY, newDec(), 0);
        Asserts.assertEquals("jar=DELETED", DiffStatus.DELETED, info.jarStatus);
        Asserts.assertEquals("全部删除", 1, info.removed);
        Asserts.assertEquals("无新增", 0, info.added);
    }

    public void testAnalyzeJar_unchangedJar() throws Exception {
        Map<String, byte[]> j = new LinkedHashMap<>();
        j.put("com/x/A.class", b("a"));
        j.put("com/x/B.class", b("b"));
        byte[] bytes = jar(j);
        PackageSnapshot old = snap(libEntry(JAR_KEY, bytes, "same"));
        PackageSnapshot nu = snap(libEntry(JAR_KEY, bytes, "same"));
        LibJarDiff.DiffJarInfo info = LibJarDiff.analyzeJar(old, nu, JAR_KEY, newDec(), 0);
        Asserts.assertEquals("jar=UNCHANGED", DiffStatus.UNCHANGED, info.jarStatus);
        Asserts.assertEquals("全部未变", 2, info.unchanged);
        Asserts.assertEquals("无新增", 0, info.added);
        Asserts.assertEquals("无修改", 0, info.modified);
    }

    // --------------------------- 失败隔离 ---------------------------

    public void testAnalyzeJar_failureIsolation() {
        // 两侧条目存在（jarStatusOf 可算得 MODIFIED），但字节非 zip → 枚举 class 抛异常 → 隔离。
        byte[] garbage = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        PackageSnapshot old = snap(libEntry(JAR_KEY, garbage, "shaOld"));
        PackageSnapshot nu = snap(libEntry(JAR_KEY, garbage, "shaNew"));
        LibJarDiff.DiffJarInfo info = LibJarDiff.analyzeJar(old, nu, JAR_KEY, newDec(), 2);
        Asserts.assertTrue("应标记失败", info.failed);
        Asserts.assertNotNull("失败原因非空", info.error);
        Asserts.assertEquals("失败 jar 保留 jarStatus", DiffStatus.MODIFIED, info.jarStatus);
        Asserts.assertEquals("失败 jar 无单元", 0, info.classes.size());
        Asserts.assertEquals("失败计数不贡献 added", 0, info.added);
        Asserts.assertEquals("失败 jar 反编译数 0", 0, info.getDecompiledCount());
    }

    // --------------------------- Top-K 全局预算跨 jar 递减 ---------------------------

    public void testAnalyze_topKBudgetAcrossJars() throws Exception {
        byte[] v1 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        byte[] v2 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V2);

        Map<String, byte[]> oldA = new LinkedHashMap<>();
        oldA.put("com/x/One.class", v1);
        oldA.put("com/x/Two.class", v1);
        Map<String, byte[]> newA = new LinkedHashMap<>();
        newA.put("com/x/One.class", v2);
        newA.put("com/x/Two.class", v2);

        Map<String, byte[]> oldB = new LinkedHashMap<>();
        oldB.put("com/y/Three.class", v1);
        oldB.put("com/y/Four.class", v1);
        Map<String, byte[]> newB = new LinkedHashMap<>();
        newB.put("com/y/Three.class", v2);
        newB.put("com/y/Four.class", v2);

        String aKey = "WEB-INF/lib/a.jar";
        String bKey = "WEB-INF/lib/b.jar";
        PackageSnapshot old = snap(libEntry(aKey, jar(oldA), "oldA"), libEntry(bKey, jar(oldB), "oldB"));
        PackageSnapshot nu = snap(libEntry(aKey, jar(newA), "newA"), libEntry(bKey, jar(newB), "newB"));

        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, aKey);
        r.put(DiffStatus.MODIFIED, bKey);

        LibJarDiff.Result res = LibJarDiff.analyze(old, nu, r, newDec(), 2);
        Asserts.assertEquals("两个差异 jar", 2, res.totalJars);
        Asserts.assertFalse("结果非空", res.isEmpty());
        Asserts.assertEquals("修改合计 4", 4, res.totalModified);
        // 全局预算 2：排序靠前的 a.jar 反编译 2 个，b.jar 预算耗尽反编译 0 个。
        Asserts.assertEquals("a.jar 用满预算 2", 2, res.jars.get(0).getDecompiledCount());
        Asserts.assertEquals("b.jar 预算为 0", 0, res.jars.get(1).getDecompiledCount());
        // 计数始终全量准确（与是否反编译无关）
        Asserts.assertEquals("b.jar 仍统计 2 个修改 class", 2, res.jars.get(1).modified);
    }

    // --------------------------- 懒加载读单个 class 字节 ---------------------------

    public void testReadClassBytes_lazyLoad() throws Exception {
        Map<String, byte[]> inner = new LinkedHashMap<>();
        inner.put("com/x/A.class", b("payload-bytes"));
        PackageSnapshot s = snap(libEntry(JAR_KEY, jar(inner), "sha"));
        byte[] got = LibJarDiff.readClassBytes(s, JAR_KEY, "com/x/A.class");
        Asserts.assertNotNull("应取到 class 字节", got);
        Asserts.assertEquals("字节内容一致", "payload-bytes", new String(got, StandardCharsets.UTF_8));
        Asserts.assertNull("缺失内部 class 返回 null", LibJarDiff.readClassBytes(s, JAR_KEY, "com/x/None.class"));
        Asserts.assertNull("缺失 jar 条目返回 null", LibJarDiff.readClassBytes(s, "WEB-INF/lib/absent.jar", "com/x/A.class"));
    }

    // --------------------------- 结果模型访问器（7 参构造 / Result） ---------------------------

    public void testDiffJarInfoAndResult_accessors() {
        DecompiledUnit okUnit = new DecompiledUnit("com/x/A.class", "o", "n", "diff", "cfr", "", true);
        List<LibJarDiff.LibClassUnit> units = new ArrayList<>(Arrays.asList(
                new LibJarDiff.LibClassUnit("com/x/A.class", DiffStatus.MODIFIED, okUnit),
                new LibJarDiff.LibClassUnit("com/x/B.class", DiffStatus.ADDED, null)));
        LibJarDiff.DiffJarInfo info = new LibJarDiff.DiffJarInfo(
                JAR_KEY, DiffStatus.MODIFIED, 1, 0, 1, 0, units);
        Asserts.assertEquals("仅计 unit 非空的 class", 1, info.getDecompiledCount());
        Asserts.assertFalse("非失败", info.failed);
        Asserts.assertNull("无错误", info.error);

        LibJarDiff.Result empty = new LibJarDiff.Result(new ArrayList<>(), 0, 0, 0, 0, 0);
        Asserts.assertTrue("空结果 isEmpty", empty.isEmpty());
        Asserts.assertEquals("totalJars", 0, empty.totalJars);
        LibJarDiff.Result nonEmpty = new LibJarDiff.Result(new ArrayList<>(Arrays.asList(info)), 1, 1, 0, 1, 0);
        Asserts.assertFalse("非空结果", nonEmpty.isEmpty());
        Asserts.assertEquals("totalAdded", 1, nonEmpty.totalAdded);
    }
}
