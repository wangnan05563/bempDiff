package com.bempdiff.test;

import com.bempdiff.diff.ApiCompat;
import com.bempdiff.model.DecompiledUnit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FR-007 API 兼容性语义分级测试（验收①：演示夹具四类变更识别准确率 100%）。
 * 四类 = 移除 API / 签名变更 / 新增 API / 无 API 变化（仅实现变化）；
 * 行为变更为 AI 辅助范畴（CategoryConclusion 链路），不在静态分级断言内。
 */
public final class ApiCompatTest {

    private static final String V1 = """
            package com.demo;
            public class Svc {
                public String hello(String name) { return "hi"; }
                public void run(int p) { }
                protected java.util.List<String> items() { return null; }
                private int secret = 1;
                public static final String VER = "1.0";
            }
            """;

    /** v2：移除 run(int)、hello 签名变化（参数增列）、新增 send、items 不变、VER 不变。 */
    private static final String V2 = """
            package com.demo;
            public class Svc {
                public String hello(String name, String title) { return "hi"; }
                protected java.util.List<String> items() { return null; }
                public void send(String msg) { }
                private int secret = 2;
                public static final String VER = "1.0";
            }
            """;

    /** 无 API 变化：仅私有实现与注释变化（应零分级条目）。 */
    private static final String V1_REIMPL = """
            package com.demo;
            // 实现重构
            public class Svc {
                public String hello(String name) { return "hi " + name; }
                public void run(int p) { System.out.println(p); }
                protected java.util.List<String> items() { return null; }
                private int secret = 999;
                public static final String VER = "1.0";
            }
            """;

    /** 整类移除：old 有、new 无（unit 只有 oldSource）。 */
    private static final String GONE_V1 = """
            package com.demo;
            public class Legacy {
                public void legacy() { }
            }
            """;

    private static DecompiledUnit unit(String key, String oldSrc, String newSrc) {
        return new DecompiledUnit(key, oldSrc, newSrc,
                (oldSrc != null && newSrc != null) ? "diff" : "", "cfr", null, true);
    }

    /** 验收①：移除/签名变更/新增三类识别 + 无 API 变化零条目。 */
    public void testFourKindsAccuracy() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/Svc.java", unit("com/demo/Svc.java", V1, V2));
        ApiCompat.Result r = ApiCompat.analyze(units);

        int removed = 0, sigChanged = 0, added = 0;
        boolean helloSig = false, runRemoved = false, sendAdded = false;
        for (ApiCompat.Item it : r.items) {
            switch (it.level) {
                case REMOVED_API -> {
                    removed++;
                    if (it.api.startsWith("run(")) runRemoved = true;
                }
                case SIGNATURE_CHANGED -> {
                    sigChanged++;
                    if (it.api.startsWith("hello(") && it.api.contains("→")) helloSig = true;
                }
                case ADDED_API -> {
                    added++;
                    if (it.api.startsWith("send(")) sendAdded = true;
                }
            }
        }
        Asserts.assertEquals("移除 API 恰 1 条（run）", 1, removed);
        Asserts.assertTrue("run(int) 应识别为移除", runRemoved);
        Asserts.assertEquals("签名变更恰 1 条（hello）", 1, sigChanged);
        Asserts.assertTrue("hello 签名变化应含旧→新对", helloSig);
        Asserts.assertEquals("新增 API 恰 1 条（send）", 1, added);
        Asserts.assertTrue("send 应识别为新增", sendAdded);
        // 私有成员不进分级（secret/VER 变化不算 API 变更）
        Asserts.assertTrue("分级不应包含私有成员", r.items.stream().noneMatch(i -> i.api.contains("secret")));
    }

    /** 仅私有实现变化 → 零分级条目（不误报）。 */
    public void testReimplYieldsEmpty() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/Svc.java", unit("com/demo/Svc.java", V1, V1_REIMPL));
        ApiCompat.Result r = ApiCompat.analyze(units);
        Asserts.assertTrue("仅实现变化应零分级条目", r.isEmpty());
    }

    /** 整类移除 → REMOVED_API；整类新增 → ADDED_API。 */
    public void testClassLevelRemovedAndAdded() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/Legacy.java", unit("com/demo/Legacy.java", GONE_V1, null));
        units.put("com/demo/New.java", unit("com/demo/New.java", null, GONE_V1.replace("Legacy", "New")));
        ApiCompat.Result r = ApiCompat.analyze(units);
        Asserts.assertEquals("整类移除+整类新增共 2 条", 2, r.items.size());
        Asserts.assertTrue("Legacy 应为移除", r.items.stream()
                .anyMatch(i -> i.level == ApiCompat.Level.REMOVED_API && i.cls.contains("Legacy")));
        Asserts.assertTrue("New 应为新增", r.items.stream()
                .anyMatch(i -> i.level == ApiCompat.Level.ADDED_API && i.cls.contains("New")));
    }

    /** 报告/提示词渲染：不兼容徽章与提示词段内容正确；空结果不渲染。 */
    public void testMarkdownAndPromptRendering() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/Svc.java", unit("com/demo/Svc.java", V1, V2));
        ApiCompat.Result r = ApiCompat.analyze(units);
        String md = r.toMarkdownSection();
        Asserts.assertContains("报告应含移除徽章", md, "sev-breaking");
        Asserts.assertContains("报告应含签名变更徽章", md, "sev-high");
        Asserts.assertContains("报告应含分级口径说明", md, "二进制不兼容");
        String prompt = r.toPromptSection();
        Asserts.assertContains("提示词应含 REMOVED_API", prompt, "REMOVED_API");
        Asserts.assertContains("提示词应含类名", prompt, "Svc");

        Map<String, DecompiledUnit> empty = new LinkedHashMap<>();
        Asserts.assertTrue("空结果 isEmpty", ApiCompat.analyze(empty).isEmpty());
        Asserts.assertEquals("空结果渲染为空串", "", ApiCompat.analyze(empty).toMarkdownSection());
    }
}
