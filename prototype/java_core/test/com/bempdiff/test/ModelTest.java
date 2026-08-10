package com.bempdiff.test;

import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;

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
    }

    public void testEntrySource_isNested() {
        EntrySource flat = new EntrySource("a.class", null);
        EntrySource nested = new EntrySource("WEB-INF/lib/x.jar", "com/internal/B.class");
        Asserts.assertFalse("扁平条目非嵌套", flat.isNested());
        Asserts.assertTrue("内嵌 jar 条目为嵌套", nested.isNested());
        Asserts.assertEquals("outerEntry", "WEB-INF/lib/x.jar", nested.getOuterEntry());
        Asserts.assertEquals("innerEntry", "com/internal/B.class", nested.getInnerEntry());
    }
}
