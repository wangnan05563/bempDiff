package com.bempdiff.test;

/** 轻量断言工具（与 JUnit 同形，零依赖，适配沙箱无网络场景）。 */
public final class Asserts {
    private Asserts() {}

    public static void assertTrue(String msg, boolean cond) {
        if (!cond) throw new AssertionError("断言失败[true 期望]: " + msg);
    }

    public static void assertFalse(String msg, boolean cond) {
        if (cond) throw new AssertionError("断言失败[false 期望]: " + msg);
    }

    public static void fail(String msg) {
        throw new AssertionError("断言失败[fail]: " + msg);
    }

    public static void assertEquals(String msg, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("断言失败[相等期望]\n  描述: " + msg
                    + "\n  期望: " + expected + "\n  实际: " + actual);
        }
    }

    public static void assertNotEquals(String msg, Object unexpected, Object actual) {
        if (java.util.Objects.equals(unexpected, actual)) {
            throw new AssertionError("断言失败[不等期望]: " + msg
                    + "\n  不应等于: " + unexpected + "\n  实际: " + actual);
        }
    }

    public static void assertNull(String msg, Object actual) {
        if (actual != null) throw new AssertionError("断言失败[null 期望]: " + msg + " 实际=" + actual);
    }

    public static void assertNotNull(String msg, Object actual) {
        if (actual == null) throw new AssertionError("断言失败[非 null 期望]: " + msg);
    }

    public static void assertContains(String msg, String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError("断言失败[包含期望]\n  描述: " + msg
                    + "\n  应在: " + truncate(haystack) + "\n  包含: " + needle);
        }
    }

    public static void assertNotContains(String msg, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            throw new AssertionError("断言失败[不包含期望]\n  描述: " + msg
                    + "\n  不应包含: " + needle + "\n  原文: " + truncate(haystack));
        }
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
