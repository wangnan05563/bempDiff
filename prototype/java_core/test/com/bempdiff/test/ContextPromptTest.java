package com.bempdiff.test;

import com.bempdiff.ai.PromptBuilders;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 项目级上下文 Prompt 注入测试：验证上下文章节/指令注入，且 ctx=null 时行为不变。 */
public final class ContextPromptTest {

    private static DiffResult diff() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/classes/com/internal/A.class");
        return r;
    }

    private static Map<String, DecompiledUnit> dec() {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        m.put("WEB-INF/classes/com/internal/A.class",
                new DecompiledUnit("k", "o", "n", "L1\nL2", "cfr", "", true));
        return m;
    }

    private static ProjectContext ctx() {
        return new ProjectContext("C:/proj", "Maven",
                Arrays.asList("core", "web"),
                Arrays.asList("spring-boot", "mybatis"),
                Arrays.asList("com/x/App.java"),
                Arrays.asList("application.yml"),
                Arrays.asList("Maven", "Spring"),
                Arrays.asList("包根 com.x"),
                "多模块工程（Maven），模块含 core、web；约定：*Mapper/*Service。");
    }

    public void testStageA_withContext_injectsSectionAndInstruction() {
        AiConfig cfg = new AiConfig();
        String prompt = PromptBuilders.buildStageA(diff(), dec(), cfg, ctx());
        Asserts.assertContains("应注入项目级上下文章节", prompt, "项目级上下文（分析依据）");
        Asserts.assertContains("应注入构建系统", prompt, "Maven");
        Asserts.assertContains("应注入模块信息", prompt, "core、web");
        Asserts.assertContains("应要求产出 contextInfluence", prompt, "contextInfluence");
        Asserts.assertContains("仍保留差异统计", prompt, "差异统计");
    }

    public void testStageA_nullContext_equalsOldBehavior() {
        AiConfig cfg = new AiConfig();
        String withNull = PromptBuilders.buildStageA(diff(), dec(), cfg, null);
        String old = PromptBuilders.buildStageA(diff(), dec(), cfg);
        Asserts.assertEquals("ctx=null 应与原 3 参方法等价", old, withNull);
        Asserts.assertNotContains("ctx=null 不应含上下文章节", withNull, "项目级上下文（分析依据）");
    }

    public void testStageB_withContext_injectsSummaryAndInstruction() {
        AiConfig cfg = new AiConfig();
        DecompiledUnit u = new DecompiledUnit("k", "o", "n", "diff line", "cfr", "", true);
        String prompt = PromptBuilders.buildStageB("A.class", u, null, cfg, ctx());
        Asserts.assertContains("应注入上下文简要", prompt, "项目级上下文（简要）");
        Asserts.assertContains("应注入架构叙述", prompt, "多模块工程");
        Asserts.assertContains("应要求产出 contextInfluence", prompt, "contextInfluence");
    }

    public void testStageB_nullContext_equalsOldBehavior() {
        AiConfig cfg = new AiConfig();
        DecompiledUnit u = new DecompiledUnit("k", "o", "n", "diff line", "cfr", "", true);
        String withNull = PromptBuilders.buildStageB("A.class", u, null, cfg, null);
        String old = PromptBuilders.buildStageB("A.class", u, null, cfg);
        Asserts.assertEquals("ctx=null 应与原 3 参方法等价", old, withNull);
    }
}
