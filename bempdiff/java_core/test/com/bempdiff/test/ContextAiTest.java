package com.bempdiff.test;

import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;

import java.util.Arrays;
import java.util.List;

/** 项目级上下文解析测试：验证 contextInfluence 字段捕获、向后兼容与降级。 */
public final class ContextAiTest {

    public void testParseStageA_plainText_withContext() {
        String resp = "overallRisk: HIGH\nimpactScope: 影响范围X\n"
                + "contextInfluence: 该改动位于 billing-core，受 spring 事务配置约束，影响被限定在计费域\n"
                + "- 测试回归登录\n- 验证支付流程";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp, true);
        Asserts.assertEquals("风险", "HIGH", s.getOverallRisk());
        Asserts.assertContains("应捕获上下文影响", s.getContextInfluence(), "billing-core");
        Asserts.assertTrue("仍应解析测试主题", s.getTestThemes().size() >= 2);
    }

    public void testParseStageA_jsonForm_withContext() {
        String resp = "{\"overallRisk\":\"MEDIUM\",\"impactScope\":\"Y影响\","
                + "\"contextInfluence\":\"模块依赖约束使风险可控\"}";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp, true);
        Asserts.assertEquals("风险 JSON", "MEDIUM", s.getOverallRisk());
        Asserts.assertContains("应捕获上下文影响(JSON)", s.getContextInfluence(), "模块依赖");
    }

    public void testParseStageA_withoutContextFlag_ignoresField() {
        // 向后兼容：未启用上下文增强时，即使响应含字段也不写入（避免旧流程误用）
        String resp = "overallRisk: LOW\ncontextInfluence: 不应被采用";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp, false);
        Asserts.assertEquals("风险", "LOW", s.getOverallRisk());
        Asserts.assertTrue("未启用时 contextInfluence 应为空",
                s.getContextInfluence() == null || s.getContextInfluence().isEmpty());
    }

    public void testParseStageA_missingField_emptyInfluence() {
        String resp = "overallRisk: LOW\nimpactScope: Z";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp, true);
        Asserts.assertTrue("缺字段时 contextInfluence 为空串",
                s.getContextInfluence() != null && s.getContextInfluence().isEmpty());
    }

    public void testParseFileAnalysis_withContext() {
        String resp = "intent: 调整了计费逻辑\nrisk: LOW\nimpact: 仅影响计费模块\n"
                + "contextInfluence: 该模块被 spring 声明式事务包裹，回滚策略已覆盖";
        FileAnalysis fa = MockAiAnalyzer.parseFileAnalysisStatic("k", resp, true);
        Asserts.assertEquals("risk", "LOW", fa.getRisk());
        Asserts.assertContains("intent", fa.getIntent(), "计费逻辑");
        Asserts.assertContains("应捕获文件级上下文影响", fa.getContextInfluence(), "事务");
    }

    public void testFileAnalysis_oldConstructor_backwardCompat() {
        List<String> tp = Arrays.asList("回归调用方");
        FileAnalysis fa = new FileAnalysis("k", "意图", "MEDIUM", "影响", tp);
        Asserts.assertEquals("key", "k", fa.getKey());
        Asserts.assertEquals("risk", "MEDIUM", fa.getRisk());
        Asserts.assertTrue("旧构造器 contextInfluence 应为空",
                fa.getContextInfluence() == null || fa.getContextInfluence().isEmpty());
    }
}
