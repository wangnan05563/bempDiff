package com.bempdiff.test;

import com.bempdiff.diff.ApiCompat;
import com.bempdiff.diff.ImpactAnalyzer;
import com.bempdiff.model.DecompiledUnit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-012 变更影响面分析测试：
 *  ① 引用判定（import/同包/词边界）与证据链
 *  ② 风险分级：删除类被引用=CRITICAL、签名变更被引用=HIGH、实现变更被引用=MEDIUM、无引用=LOW
 *  ③ 自引用不算影响方；与 ApiCompat 联动
 *  ④ 可解释性：reason 与 referencedBy 可供人工复核（验收③证据链）
 */
public final class ImpactAnalyzerTest {

    private static final String CALLER_V = """
            package com.demo;
            import com.demo.svc.Svc;
            public class Caller {
                public String go() { return new Svc().hello("x"); }
            }
            """;

    private static final String SIBLING_V = """
            package com.demo.svc;
            public class Sibling {
                public String tag() { return "svc-neighbor"; }
            }
            """;

    private static final String SIBLING_CALLS_SVC = """
            package com.demo.svc;
            public class Sibling {
                public String tag(Svc s) { return s.hello("y"); }
            }
            """;

    private static final String SVC_V1 = """
            package com.demo.svc;
            public class Svc {
                public String hello(String name) { return "hi " + name; }
            }
            """;

    private static final String SVC_V2_SAME_SIG = """
            package com.demo.svc;
            public class Svc {
                public String hello(String name) { return "hello " + name + "!"; }
            }
            """;

    private static final String SVC_V2_SIG_CHANGE = """
            package com.demo.svc;
            public class Svc {
                public String hello(String name, String title) { return "hi " + title; }
            }
            """;

    private static DecompiledUnit unit(String key, String oldSrc, String newSrc) {
        return new DecompiledUnit(key, oldSrc, newSrc,
                (oldSrc != null && newSrc != null) ? "diff" : "", "cfr", null, true);
    }

    /** 实现变更（签名不变）被 import 引用 → MEDIUM + 引用方证据链。 */
    public void testImplChangeWithImportRefIsMedium() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, SVC_V2_SAME_SIG));
        units.put("com/demo/Caller.java", unit("com/demo/Caller.java", CALLER_V, CALLER_V));
        units.put("com/demo/svc/Sibling.java", unit("com/demo/svc/Sibling.java", SIBLING_V, SIBLING_V));
        ApiCompat.Result api = ApiCompat.analyze(units); // 签名不变 → 空
        ImpactAnalyzer.Result r = ImpactAnalyzer.analyze(units, api, List.of());
        Asserts.assertFalse("应有影响面条目", r.isEmpty());
        Asserts.assertEquals("maxRisk=MEDIUM", ImpactAnalyzer.Risk.MEDIUM, r.maxRisk());
        ImpactAnalyzer.ImpactItem it = r.items.get(0);
        Asserts.assertEquals("目标类 FQN", "com.demo.svc.Svc", it.changedClass);
        Asserts.assertTrue("证据链应含 import 引用方 Caller",
                it.referencedBy.contains("com/demo/Caller.java"));
        // Sibling 夹具（SIBLING_V）不含对 Svc 的引用（同包但无词出现）→ 不进证据链（词边界口径）
        Asserts.assertTrue("无引用词出现的同包类不应误入证据链",
                !it.referencedBy.contains("com/demo/svc/Sibling.java"));
        Asserts.assertTrue("reason 应可解释（含引用计数）", it.reason.contains("1 个类引用"));
    }

    /** 签名变更被引用 → HIGH（与 ApiCompat 联动）。 */
    public void testSigChangeWithRefIsHigh() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, SVC_V2_SIG_CHANGE));
        units.put("com/demo/svc/Sibling.java", unit("com/demo/svc/Sibling.java", SIBLING_CALLS_SVC, SIBLING_CALLS_SVC));
        ApiCompat.Result api = ApiCompat.analyze(units);
        Asserts.assertFalse("签名变更应产生 ApiCompat 条目", api.isEmpty());
        ImpactAnalyzer.Result r = ImpactAnalyzer.analyze(units, api, List.of());
        Asserts.assertEquals("maxRisk=HIGH", ImpactAnalyzer.Risk.HIGH, r.maxRisk());
        Asserts.assertTrue("reason 应含签名变更描述", r.items.get(0).reason.contains("签名变更"));
    }

    /** 整类删除且被引用 → CRITICAL；无引用删除 → LOW。 */
    public void testDeletedClassRisk() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/Caller.java", unit("com/demo/Caller.java", CALLER_V, CALLER_V));
        units.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, null));
        ApiCompat.Result api = ApiCompat.analyze(units);
        ImpactAnalyzer.Result r = ImpactAnalyzer.analyze(units, api,
                List.of("com/demo/svc/Svc.java"));
        Asserts.assertEquals("maxRisk=CRITICAL", ImpactAnalyzer.Risk.CRITICAL, r.maxRisk());
        Asserts.assertTrue("Caller 应在引用链中",
                r.items.get(0).referencedBy.contains("com/demo/Caller.java"));

        // 无引用方的删除 → LOW（api 需按本场景 units 重算，避免携带上一场景的签名变更升级）
        Map<String, DecompiledUnit> units2 = new LinkedHashMap<>();
        units2.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, null));
        ApiCompat.Result api2 = ApiCompat.analyze(units2);
        ImpactAnalyzer.Result r2 = ImpactAnalyzer.analyze(units2, api2, List.of("com/demo/svc/Svc.java"));
        Asserts.assertEquals("无引用删除应 LOW", ImpactAnalyzer.Risk.LOW, r2.maxRisk());
    }

    /** 自引用不算影响方；纯新增类（无 ApiCompat 条目且不在删除清单）不进影响面。 */
    public void testSelfRefExcludedAndAddedIgnored() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, SVC_V2_SAME_SIG));
        ApiCompat.Result api = ApiCompat.analyze(units);
        ImpactAnalyzer.Result r = ImpactAnalyzer.analyze(units, api, List.of());
        // 集内只有 Svc 自身（自引用排除）→ 无引用方 → LOW 且条目仍存在（实现变更）
        Asserts.assertEquals("集内仅目标类自身", 1, r.items.size());
        Asserts.assertTrue("自引用不应进入证据链", r.items.get(0).referencedBy.isEmpty());
        Asserts.assertEquals("孤岛变更应 LOW", ImpactAnalyzer.Risk.LOW, r.items.get(0).risk);
    }

    /** 渲染：报告表与提示词段含风险等级与证据链；空结果不渲染。 */
    public void testRendering() {
        Map<String, DecompiledUnit> units = new LinkedHashMap<>();
        units.put("com/demo/svc/Svc.java", unit("com/demo/svc/Svc.java", SVC_V1, SVC_V2_SIG_CHANGE));
        units.put("com/demo/svc/Sibling.java", unit("com/demo/svc/Sibling.java", SIBLING_CALLS_SVC, SIBLING_CALLS_SVC));
        ApiCompat.Result api = ApiCompat.analyze(units);
        ImpactAnalyzer.Result r = ImpactAnalyzer.analyze(units, api, List.of());
        String md = r.toMarkdownSection();
        Asserts.assertContains("报告应含影响面章节", md, "变更影响面");
        Asserts.assertContains("报告应含 HIGH 徽章", md, "sev-high");
        Asserts.assertContains("报告应含引用方证据", md, "Sibling");
        String prompt = r.toPromptSection();
        Asserts.assertContains("提示词应含风险等级", prompt, "[HIGH]");
        Asserts.assertContains("提示词应含被引用清单", prompt, "被引用：");

        Map<String, DecompiledUnit> empty = new LinkedHashMap<>();
        Asserts.assertTrue("空输入应得空结果", ImpactAnalyzer.analyze(empty, null, null).isEmpty());
    }
}
