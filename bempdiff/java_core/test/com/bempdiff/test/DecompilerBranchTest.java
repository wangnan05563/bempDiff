package com.bempdiff.test;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Decompiler} 失败/回退分支补充单测：覆盖 decompile() 的 IOException 与通用 Exception 两条兜底、
 * decompileBytes() 的双侧缺失占位哈希与单侧哈希、反编译彻底失败时的 failWithHash 兜底，以及通过反射直接
 * 驱动 javap()/javapBin() 的成功与失败子进程分支（此前 DecompileTest 因 CFR 在 classpath 上恒走进程内路径，
 * javap/cfr 子进程与降级/失败分支未被触及）。
 */
public final class DecompilerBranchTest {

    private static String javaBin() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    // ---------------- decompile() 捕获 IOException → DecompiledUnit.fail ----------------

    public void testDecompile_missingLibEntry_returnsFailViaIOException() throws Exception {
        byte[] zip = TestFixtures.makeZip(singleEntry());
        Path p = TestFixtures.writePackage(zip, "db-missing");
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        // 嵌套源指向不存在的 lib 条目 → readEntryBytes 抛 IOException("missing lib")
        LogicalEntry entry = new LogicalEntry("k", Layer.L1, FileClass.CLASS, 10, "sha",
                new EntrySource("nope.jar", "inner.class"));
        m.put("k", entry);
        PackageSnapshot snap = new PackageSnapshot(p, PackageType.JAR, "1", m);

        DecompiledUnit u = new Decompiler(null, javaBin()).decompile(snap, snap, entry, entry, "k");
        Asserts.assertFalse("缺 lib 应反编译失败", u.isOk());
        Asserts.assertEquals("失败单元引擎标注 none", "none", u.getEngine());
        Asserts.assertContains("错误含 missing lib", String.valueOf(u.getError()), "missing lib");
    }

    // ---------------- decompile() 捕获非 IO 异常（NPE）→ DecompiledUnit.fail ----------------

    public void testDecompile_unexpectedException_returnsFail() throws Exception {
        // snap 传 null 而 entry 非空且非嵌套磁盘路径：readBytes 内 snap.getFile() 触发 NPE
        LogicalEntry entry = new LogicalEntry("k", Layer.L1, FileClass.CLASS, 10, "sha",
                new EntrySource("C:/bempdiff/nonexistent/file.jar", null));
        DecompiledUnit u = new Decompiler(null, javaBin()).decompile(null, null, entry, null, "k");
        Asserts.assertFalse("非 IO 异常也兜底为失败", u.isOk());
        Asserts.assertEquals("失败单元引擎标注 none", "none", u.getEngine());
    }

    // ---------------- decompileBytes() 双侧缺失：新增/删除标注 + 占位哈希 ----------------

    public void testDecompileBytes_bothNull_placeholdersAndMarkers() {
        DecompiledUnit u = new Decompiler(null, javaBin()).decompileBytes(null, null, "Ghost.class");
        Asserts.assertTrue("双侧 null 仍是 ok 单元（仅标注）", u.isOk());
        Asserts.assertContains("含新增类标注", u.getDiffText(), "[新增类]");
        Asserts.assertContains("含删除类标注", u.getDiffText(), "[删除类]");
        Asserts.assertEquals("老侧缺失哈希占位", "0000000", u.getOldHash());
        Asserts.assertEquals("新侧缺失哈希占位", "0000000", u.getNewHash());
    }

    // ---------------- decompileBytes() 单侧存在：真实哈希 + 缺失侧占位 ----------------

    public void testDecompileBytes_singleSideRealHash() throws Exception {
        byte[] cls = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        DecompiledUnit u = new Decompiler(null, javaBin()).decompileBytes(cls, null, "Only.class");
        Asserts.assertTrue("单侧真实类应反编译成功：err=" + u.getError(), u.isOk());
        Asserts.assertNotEquals("存在侧哈希非占位", "0000000", u.getOldHash());
        Asserts.assertEquals("缺失侧哈希占位", "0000000", u.getNewHash());
    }

    // ---------------- decompileBytes() 损坏类字节：反编译失败仍带字节指纹哈希 ----------------

    public void testDecompileBytes_corruptClass_failsWithHash() throws Exception {
        byte[] cls = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        byte[] corrupt = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // 合法魔数
                0, 0, 0, 52, 'x', 'y', 'z'}; // 但主体是垃圾 → CFR 与 javap 都失败 → failWithHash
        DecompiledUnit u = new Decompiler(null, javaBin()).decompileBytes(corrupt, cls, "Corrupt.class");
        Asserts.assertFalse("损坏字节应反编译失败", u.isOk());
        Asserts.assertEquals("失败单元引擎标注 cfr", "cfr", u.getEngine());
        Asserts.assertNotEquals("失败仍计算存在侧字节指纹哈希", "0000000", u.getOldHash());
    }

    // ---------------- javapBin() 推导分支（反射私有实例方法）----------------

    public void testJavapBin_derivationBranches() throws Exception {
        Method m = Decompiler.class.getDeclaredMethod("javapBin");
        m.setAccessible(true);
        Asserts.assertEquals("java.exe→javap.exe", "C:/jdk/bin/javap.exe",
                m.invoke(new Decompiler(null, "C:/jdk/bin/java.exe")));
        Asserts.assertEquals("unix java→javap", "/usr/bin/javap",
                m.invoke(new Decompiler(null, "/usr/bin/java")));
        Asserts.assertEquals("裸名兜底 PATH", "javap",
                m.invoke(new Decompiler(null, "java")));
        Asserts.assertEquals("null 兜底 PATH", "javap",
                m.invoke(new Decompiler(null, null)));
    }

    // ---------------- javap() 子进程成功/失败分支（反射私有实例方法，用真实 JDK javap）----------------

    public void testJavap_successAndFailure() throws Exception {
        Method javap = Decompiler.class.getDeclaredMethod("javap", File.class);
        javap.setAccessible(true);
        Decompiler dec = new Decompiler(null, javaBin());

        byte[] cls = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        Path good = Files.createTempFile("db-javap-good", ".class");
        Files.write(good, cls);
        try {
            String out = (String) javap.invoke(dec, good.toFile());
            Asserts.assertContains("javap 成功含降级标注前缀", out, "javap 签名级反编译");
            Asserts.assertContains("javap 输出含类名", out, "A");
        } finally {
            Files.deleteIfExists(good);
        }

        Path bad = Files.createTempFile("db-javap-bad", ".class");
        Files.write(bad, new byte[]{1, 2, 3, 4, 5}); // 非法 class → javap 退出码非 0
        boolean threw = false;
        try {
            javap.invoke(dec, bad.toFile());
        } catch (InvocationTargetException ite) {
            threw = ite.getCause() instanceof java.io.IOException;
        } finally {
            Files.deleteIfExists(bad);
        }
        Asserts.assertTrue("非法 class 的 javap 应抛 IOException", threw);
    }

    private static Map<String, byte[]> singleEntry() {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("x.txt", "x".getBytes());
        return e;
    }
}
