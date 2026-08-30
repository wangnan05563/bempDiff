package com.bempdiff.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.util.ShortHash;

/** 模型层契约测试：LogicalEntry 等值/hash 一致性、DecompiledUnit 工厂、EntrySource 嵌套判定。 */
public final class ModelTest {

    public void testLogicalEntry_equalsAndHashCode() {
        LogicalEntry a = new LogicalEntry("k", Layer.L1, FileClass.CLASS, 10, "h", new EntrySource("k", null));
        LogicalEntry b = new LogicalEntry("k", Layer.L1, FileClass.CLASS, 10, "h", new EntrySource("k", null));
        LogicalEntry c = new LogicalEntry("k", Layer.L1, FileClass.CLASS, 10, "different", new EntrySource("k", null));
        Asserts.assertEquals("相同字段应相等", a, b);
        Asserts.assertEquals("hash 应一致", a.hashCode(), b.hashCode());
        Asserts.assertNotEquals("sha 不同不应相等", a, c);
    }

    public void testDecompiledUnit_fail() {
        DecompiledUnit u = DecompiledUnit.fail("k", "boom");
        Asserts.assertFalse("fail 工厂应为不成功", u.isOk());
        Asserts.assertEquals("engine 应为 none", "none", u.getEngine());
        Asserts.assertEquals("error 应保留", "boom", u.getError());
        Asserts.assertNull("oldSource 应为 null", u.getOldSource());
        // Task 1：失败工厂也要给哈希字段 0000000 占位
        Asserts.assertEquals("失败工厂 oldHash 占位", "0000000", u.getOldHash());
        Asserts.assertEquals("失败工厂 newHash 占位", "0000000", u.getNewHash());
    }

    public void testDecompiledUnit_hashFieldsBackwardCompat() {
        // 旧 7 参构造器：哈希字段回退 0000000
        DecompiledUnit u = new DecompiledUnit("k", "old", "new", "diff", "cfr", "", true);
        Asserts.assertEquals("7 参构造器 oldHash 兜底", "0000000", u.getOldHash());
        Asserts.assertEquals("7 参构造器 newHash 兜底", "0000000", u.getNewHash());
        // 9 参构造器：null 字符串统一回退
        DecompiledUnit u2 = new DecompiledUnit("k", "old", "new", "diff", "cfr", "", true, null, "");
        Asserts.assertEquals("null oldHash 回退", "0000000", u2.getOldHash());
        Asserts.assertEquals("空串 newHash 回退", "0000000", u2.getNewHash());
    }

    public void testShortHash_basic() {
        // 同一字节 → 同一短哈希
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        Asserts.assertEquals("相同字节应得相同哈希", ShortHash.ofBytes(a), ShortHash.ofBytes(a));
        Asserts.assertEquals("短哈希固定 7 位", 7, ShortHash.ofBytes(a).length());
        // 不同字节 → 不同哈希
        Asserts.assertNotEquals("不同字节应得不同哈希",
                ShortHash.ofBytes("hello".getBytes(StandardCharsets.UTF_8)),
                ShortHash.ofBytes("world".getBytes(StandardCharsets.UTF_8)));
        // null / 空 → 0000000
        Asserts.assertEquals("空字节回退", "0000000", ShortHash.ofBytes(new byte[0]));
        Asserts.assertEquals("null 字节回退", "0000000", ShortHash.ofBytes(null));
    }

    public void testShortHash_metaAndPath() {
        // 相同 meta → 相同哈希
        String h1 = ShortHash.ofMeta("a/b/c.properties", 1024, 1700000000000L);
        String h2 = ShortHash.ofMeta("a/b/c.properties", 1024, 1700000000000L);
        Asserts.assertEquals("相同 meta 应得相同哈希", h1, h2);
        // size 变化 → 哈希变化
        Asserts.assertNotEquals("size 变化应改哈希",
                ShortHash.ofMeta("a/b/c.properties", 1024, 1700000000000L),
                ShortHash.ofMeta("a/b/c.properties", 1025, 1700000000000L));
        // path 变化 → 哈希变化
        Asserts.assertNotEquals("path 变化应改哈希",
                ShortHash.ofPathAndSize("a.jar", 100),
                ShortHash.ofPathAndSize("b.jar", 100));
        // null path → 0000000
        Asserts.assertEquals("null path 回退", "0000000", ShortHash.ofMeta(null, 1, 1));
    }

    public void testEntrySource_isNested() {
        EntrySource flat = new EntrySource("a.class", null);
        EntrySource nested = new EntrySource("WEB-INF/lib/x.jar", "com/internal/B.class");
        Asserts.assertFalse("扁平条目非嵌套", flat.isNested());
        Asserts.assertTrue("内嵌 jar 条目为嵌套", nested.isNested());
        Asserts.assertEquals("outerEntry", "WEB-INF/lib/x.jar", nested.getOuterEntry());
        Asserts.assertEquals("innerEntry", "com/internal/B.class", nested.getInnerEntry());
    }

    /**
     * Task 2：Decompiler.decompileBytes 返回的 DecompiledUnit 须填入正确的 git 风格 7 位短哈希
     * （成功与失败路径都覆盖），间接保证 BempServer.handleEntryDecompile 透出的 oldHash/newHash
     * 字段值来自实际字节。任一側字节为 null 时该侧统一回退 "0000000"。
     *
     * 这里用"非法 class 字节"输入：javap/CFR 都会失败，但 Decompiler#failWithHash 仍按字节算
     * 哈希，确保两条路径都覆盖。
     */
    public void testDecompiler_decompileBytes_hashIntegration() {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        Decompiler dec = new Decompiler(null, javaBin); // 无 CFR → 走 javap 降级（输入非法时也走 failWithHash）
        byte[] a1 = new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE, 0, 1, 2, 3, 4, 5};
        byte[] a2 = new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE, 0, 1, 2, 3, 4, 6};
        // 两侧都有字节
        DecompiledUnit u = dec.decompileBytes(a1, a2, "k");
        Asserts.assertEquals("oldHash 应为 7 位", 7, u.getOldHash().length());
        Asserts.assertEquals("newHash 应为 7 位", 7, u.getNewHash().length());
        Asserts.assertEquals("oldHash = ShortHash.ofBytes(a1)", ShortHash.ofBytes(a1), u.getOldHash());
        Asserts.assertEquals("newHash = ShortHash.ofBytes(a2)", ShortHash.ofBytes(a2), u.getNewHash());
        // 缺左
        DecompiledUnit u2 = dec.decompileBytes(null, a2, "k2");
        Asserts.assertEquals("缺左 oldHash 占位", "0000000", u2.getOldHash());
        Asserts.assertEquals("缺左 newHash 真实", ShortHash.ofBytes(a2), u2.getNewHash());
        // 缺右
        DecompiledUnit u3 = dec.decompileBytes(a1, null, "k3");
        Asserts.assertEquals("缺右 oldHash 真实", ShortHash.ofBytes(a1), u3.getOldHash());
        Asserts.assertEquals("缺右 newHash 占位", "0000000", u3.getNewHash());
        // 两侧都缺
        DecompiledUnit u4 = dec.decompileBytes(null, null, "k4");
        Asserts.assertEquals("双缺 oldHash 占位", "0000000", u4.getOldHash());
        Asserts.assertEquals("双缺 newHash 占位", "0000000", u4.getNewHash());
    }

    /**
     * Task 2 配套：用真实编译产出的 class 字节跑 Decompiler.decompileBytes，验证 hash 与
     * ShortHash.ofBytes 完全一致（与 testDecompiler_decompileBytes_hashIntegration 的"非法字节"
     * 路径互为补充——一个走失败路径，一个走成功路径）。
     */
    public void testDecompiler_decompileBytes_realClassHash() throws IOException {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        Decompiler dec = new Decompiler(null, javaBin);
        byte[] c1 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1);
        byte[] c2 = TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V2);
        DecompiledUnit u = dec.decompileBytes(c1, c2, "WEB-INF/classes/com/internal/A.class");
        Asserts.assertEquals("真实 class oldHash 7 位", 7, u.getOldHash().length());
        Asserts.assertEquals("真实 class newHash 7 位", 7, u.getNewHash().length());
        Asserts.assertEquals("oldHash = ShortHash.ofBytes(c1)", ShortHash.ofBytes(c1), u.getOldHash());
        Asserts.assertEquals("newHash = ShortHash.ofBytes(c2)", ShortHash.ofBytes(c2), u.getNewHash());
        // 验证 javap/CFR 真实反编译成功（hash 字段值正确，且 ok=true）
        Asserts.assertTrue("javap 降级反编译应成功", u.isOk());
        Asserts.assertNotNull("newSource 不应为空", u.getNewSource());
    }
}
