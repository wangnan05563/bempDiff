package com.bempdiff.test;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffRules;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 反编译模块测试：行级 diff、统一格式 diff（增/删标记）、GBK 容错解码、真实 class 端到端反编译。 */
public final class DecompileTest {

    private static final Method UNIFIED_DIFF;
    private static final Method SIMPLE_DIFF;
    private static final Method DECODE;

    static {
        try {
            UNIFIED_DIFF = Decompiler.class.getDeclaredMethod("unifiedDiff", String.class, String.class, DiffRules.class);
            SIMPLE_DIFF = Decompiler.class.getDeclaredMethod("simpleDiff", List.class, List.class, DiffRules.class);
            DECODE = Decompiler.class.getDeclaredMethod("decode", byte[].class);
            for (Method m : new Method[]{UNIFIED_DIFF, SIMPLE_DIFF, DECODE}) m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public void testSimpleDiff_basic() throws Throwable {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) SIMPLE_DIFF.invoke(null,
                Arrays.asList("1", "2", "3"), Arrays.asList("2", "3", "4"), DiffRules.DEFAULT);
        Asserts.assertContains("应含删除 1", out.toString(), "- 1");
        Asserts.assertContains("应含新增 4", out.toString(), "+ 4");
        Asserts.assertContains("应含未变 2", out.toString(), "  2");
        Asserts.assertContains("应含未变 3", out.toString(), "  3");
    }

    public void testUnifiedDiff_modified() throws Throwable {
        String diff = (String) UNIFIED_DIFF.invoke(null, "a\nb\nc", "a\nx\nc", DiffRules.DEFAULT);
        Asserts.assertContains("应含删除 b", diff, "- b");
        Asserts.assertContains("应含新增 x", diff, "+ x");
        Asserts.assertContains("应含未变 a", diff, "  a");
    }

    public void testUnifiedDiff_added() throws Throwable {
        String diff = (String) UNIFIED_DIFF.invoke(null, null, "p\nq", DiffRules.DEFAULT);
        Asserts.assertContains("新增类应标记", diff, "[新增类]");
        Asserts.assertContains("应含 + p", diff, "+ p");
    }

    public void testUnifiedDiff_deleted() throws Throwable {
        String diff = (String) UNIFIED_DIFF.invoke(null, "m\nn", null, DiffRules.DEFAULT);
        Asserts.assertContains("删除类应标记", diff, "[删除类]");
        Asserts.assertContains("应含 - m", diff, "- m");
    }

    public void testDecode_utf8Chinese() throws Throwable {
        String original = "业务系统中文常量测试";
        byte[] bs = original.getBytes(StandardCharsets.UTF_8);
        String decoded = (String) DECODE.invoke(null, bs);
        Asserts.assertEquals("UTF-8 应原样解码", original, decoded);
    }

    public void testDecompile_realClass_javap() throws IOException {
        // 构造两个 war：A.class 内容不同（其余相同），端到端验证反编译 + 双栏 diff
        Map<String, byte[]> e1 = new LinkedHashMap<>();
        e1.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Path war1 = TestFixtures.writePackage(TestFixtures.makeWar(e1, "1"), "dec-v1");
        Map<String, byte[]> e2 = new LinkedHashMap<>();
        e2.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V2));
        Path war2 = TestFixtures.writePackage(TestFixtures.makeWar(e2, "2"), "dec-v2");

        PackageParser parser = new PackageParser();
        PackageSnapshot oldSnap = parser.parse(war1, ParseConfigNoExpand(), false);
        PackageSnapshot newSnap = parser.parse(war2, ParseConfigNoExpand(), false);
        DiffResult r = new DiffEngine().compute(oldSnap, newSnap);
        Asserts.assertContains("A 应为修改", r.get(DiffStatus.MODIFIED).toString(), "WEB-INF/classes/com/internal/A.class");

        String javaBin = ProcessHandle.current().info().command().orElse("java");
        Decompiler dec = new Decompiler(null, javaBin); // 无 CFR -> 走 javap 降级
        LogicalEntry oe = oldSnap.getEntries().get("WEB-INF/classes/com/internal/A.class");
        LogicalEntry ne = newSnap.getEntries().get("WEB-INF/classes/com/internal/A.class");
        com.bempdiff.model.DecompiledUnit u = dec.decompile(oldSnap, newSnap, oe, ne,
                "WEB-INF/classes/com/internal/A.class");
        Asserts.assertTrue("真实 class 应反编译成功(javap)", u.isOk());
        Asserts.assertNotNull("diffText 不应为空", u.getDiffText());
        Asserts.assertTrue("diffText 应含差异标记(+/-)", u.getDiffText().contains("+") || u.getDiffText().contains("-"));
    }

    /**
     * P0-1 回归（性能测试报告 §6）：当 cfr.jar 位于运行时 classpath 上时，
     * Decompiler 须走**进程内 CFR API（CfrDriver）**，而非逐类 `ProcessBuilder(java -jar cfr.jar)` 起子进程。
     * 本用例验证进程内路径产出正确源码，且引擎标记为 `cfr(in-process)`（证明未 spawn 子进程）。
     */
    public void testDecompile_inProcessCfr_perfFix() throws IOException {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        Path cfrJar = Paths.get("bempdiff/cfr.jar");
        Decompiler dec = new Decompiler(Files.exists(cfrJar) ? cfrJar : null, javaBin);

        Map<String, byte[]> e1 = new LinkedHashMap<>();
        e1.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Path war1 = TestFixtures.writePackage(TestFixtures.makeWar(e1, "1"), "decip-v1");
        Map<String, byte[]> e2 = new LinkedHashMap<>();
        e2.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V2));
        Path war2 = TestFixtures.writePackage(TestFixtures.makeWar(e2, "2"), "decip-v2");

        PackageParser parser = new PackageParser();
        PackageSnapshot oldSnap = parser.parse(war1, ParseConfigNoExpand(), false);
        PackageSnapshot newSnap = parser.parse(war2, ParseConfigNoExpand(), false);
        LogicalEntry oe = oldSnap.getEntries().get("WEB-INF/classes/com/internal/A.class");
        LogicalEntry ne = newSnap.getEntries().get("WEB-INF/classes/com/internal/A.class");
        DecompiledUnit u = dec.decompile(oldSnap, newSnap, oe, ne,
                "WEB-INF/classes/com/internal/A.class");

        Asserts.assertTrue("进程内 CFR 应反编译成功", u.isOk());
        Asserts.assertNotNull("新源码不应为空", u.getNewSource());
        Asserts.assertTrue("新源码应含 class 声明", u.getNewSource().contains("class A"));
        Asserts.assertTrue("diffText 应含差异标记(+/-)",
                u.getDiffText().contains("+") || u.getDiffText().contains("-"));

        // 若进程内 CFR API 可用（cfr.jar 在 classpath），引擎须为进程内实现（证明未 spawn 子进程）
        if (isCfrApiAvailable()) {
            Asserts.assertEquals("引擎应为进程内 CFR", "cfr(in-process)", u.getEngine());
        }
    }

    private static boolean isCfrApiAvailable() {
        try {
            Class.forName("org.benf.cfr.reader.api.CfrDriver");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * P1-1 回归（性能测试报告 §6）：相同 class 字节的反编译结果须被 **sha256 键 LRU 缓存**复用，
     * 第二次及以后直接返回缓存（同一对象引用，证明走缓存短路径、不重复跑 CFR/javap）。
     * 用 javap 降级路径即可验证缓存逻辑（与具体反编译引擎无关）。
     */
    public void testDecompile_cacheSameBytes_reusesResult() throws Throwable {
        Method decompileOne = Decompiler.class.getDeclaredMethod("decompileOne", byte[].class);
        decompileOne.setAccessible(true);

        byte[] classBytes = TestFixtures.compileClass("com.internal.A",
                TestFixtures.SRC_A_V1);
        Decompiler dec = new Decompiler(null, "java"); // 无 CFR -> javap 降级，缓存逻辑路径无关

        String first = (String) decompileOne.invoke(dec, (Object) classBytes);
        String second = (String) decompileOne.invoke(dec, (Object) classBytes);
        String third = (String) decompileOne.invoke(dec, (Object) classBytes);

        Asserts.assertNotNull("首次反编译结果不应为空", first);
        Asserts.assertEquals("第二次应命中缓存(内容相同)", first, second);
        Asserts.assertTrue("第三次应命中缓存(返回同一引用，证明短路径)",
                first == third);
    }

    /**
     * D5 回归（docs/BempDiff_AI优化与竞品分析报告.md P0）：跨会话的磁盘缓存。
     * 同一份 class 字节被 d1 反编译并异步落盘后，d2（模拟新进程冷启动）再次反编译须命中磁盘缓存，
     * 表现为 Decompiler.getDiskHits() 增长，且返回内容与此前一致（证明内容寻址磁盘层生效）。
     * P0-A 后内存缓存已升级为进程级(static)共享，d1 的命中会留在共享内存里，d2 能直接命中内存、
     * 完全绕开磁盘；故此处先清空进程级内存缓存，强制 d2 走磁盘层做验证（语义等同重启后的冷缓存）。
     * 用内联唯一源码确保磁盘键不被其它用例污染。
     */
    public void testDecompile_persistentDiskCache_crossInstance() throws Throwable {
        Method decompileOne = Decompiler.class.getDeclaredMethod("decompileOne", byte[].class);
        decompileOne.setAccessible(true);

        String src = "package com.internal;\npublic class PersistX {\n  public int ping(){ return 42; }\n}\n";
        byte[] classBytes = TestFixtures.compileClass("com.internal.PersistX", src);
        Decompiler d1 = new Decompiler(null, "java");
        long before = Decompiler.getDiskHits();
        String first = (String) decompileOne.invoke(d1, (Object) classBytes);
        Asserts.assertNotNull("首次反编译结果不应为空", first);

        // 等待异步落盘线程把结果写入磁盘
        Thread.sleep(600);

        // 清空进程级内存缓存，使 d2 的内存层必然 miss、仅剩磁盘缓存可命中（模拟新进程冷启动）
        java.lang.reflect.Field memCache = Decompiler.class.getDeclaredField("decompileCache");
        memCache.setAccessible(true);
        ((Map<?, ?>) memCache.get(null)).clear();

        Decompiler d2 = new Decompiler(null, "java");
        String again = (String) decompileOne.invoke(d2, (Object) classBytes);
        Asserts.assertEquals("跨实例应命中磁盘缓存(内容一致)", first, again);
        Asserts.assertTrue("应发生至少一次磁盘缓存命中",
                Decompiler.getDiskHits() > before);
    }

    // 仅用于本测试的极简 ParseConfig（避免引入对整个 config 包的耦合）
    private static com.bempdiff.config.ParseConfig ParseConfigNoExpand() {
        return new com.bempdiff.config.ParseConfig();
    }
}
