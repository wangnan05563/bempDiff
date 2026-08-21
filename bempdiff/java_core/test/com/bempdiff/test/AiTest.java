package com.bempdiff.test;

import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.PromptBuilders;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.util.LinkedHashMap;
import java.util.Map;

/** AI 模块测试：阶段A prompt 构造/截断、脱敏（合规）、响应解析。 */
public final class AiTest {

    private static DiffResult buildDiff() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/classes/com/internal/A.class");
        r.put(DiffStatus.ADDED, "WEB-INF/classes/com/internal/Add.class");
        return r;
    }

    public void testBuildStageA_containsStatsAndFiles() {
        DiffResult r = buildDiff();
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("WEB-INF/classes/com/internal/A.class",
                new DecompiledUnit("k", "old", "new", "line1\nline2\nline3", "cfr", "", true));
        AiConfig cfg = new AiConfig();
        String prompt = PromptBuilders.buildStageA(r, decompiled, cfg);
        Asserts.assertContains("含差异统计", prompt, "差异统计");
        Asserts.assertContains("含新增数", prompt, "新增=");
        Asserts.assertContains("含改动文件 A", prompt, "WEB-INF/classes/com/internal/A.class");
        Asserts.assertContains("含改动文件 Add", prompt, "WEB-INF/classes/com/internal/Add.class");
    }

    public void testBuildStageA_topKAndTruncation() {
        DiffResult r = buildDiff();
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        // A 排在前（MODIFIED 优先），diff 5 行
        decompiled.put("WEB-INF/classes/com/internal/A.class",
                new DecompiledUnit("k", "o", "n", "L1\nL2\nL3\nL4\nL5", "cfr", "", true));
        decompiled.put("WEB-INF/classes/com/internal/Add.class",
                new DecompiledUnit("k2", null, "n2", "A1\nA2\nA3", "cfr", "", true));
        AiConfig cfg = new AiConfig();
        cfg.setStageATopK(1);          // 仅纳入 1 个文件
        cfg.setStageAFileSampleLines(2); // 每文件截断 2 行
        String prompt = PromptBuilders.buildStageA(r, decompiled, cfg);
        Asserts.assertContains("应包含优先的 A", prompt, "WEB-INF/classes/com/internal/A.class");
        Asserts.assertNotContains("Top-K=1 不应含 Add", prompt, "WEB-INF/classes/com/internal/Add.class");
        Asserts.assertContains("应出现截断提示", prompt, "截断，共 5 行");
        Asserts.assertNotContains("不应出现第 3 行", prompt, "L3");
    }

    /** 阶段B 单文件 diff 内容超长时必须截断（防整文件重写/超大文件撑爆请求与成本预估）。 */
    public void testBuildStageB_diffCapTruncatesHugeDiff() {
        AiConfig cfg = new AiConfig();
        MockAiAnalyzer a = new MockAiAnalyzer(java.nio.file.Paths.get("."));
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) huge.append("- line").append(i).append(" xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n");
        DecompiledUnit u = new DecompiledUnit("k", null, null, huge.toString(), "js-beautify", "", true);
        String prompt = a.buildStageBPrompt("static/app.js", u, FileClass.JS, cfg);
        Asserts.assertTrue("prompt 长度应受截断上限约束（原 30 万+ 字符）",
                prompt.length() <= PromptBuilders.STAGE_B_DIFF_CHAR_CAP + 512);
        Asserts.assertContains("应出现省略/截断标记", prompt, "省略");
        Asserts.assertNotContains("不应残留尾部行", prompt, "line4999");
        // 成本预估同步有界（estimateTokens = 字符数 / 2）
        double est = a.estimateTokens(prompt);
        Asserts.assertTrue("预估 token 应有界", est < 40_000);
    }

    /** 阶段B prompt 的 diff 呈现：仅变更行 + 行号 + 类型标注，上下文行不出现（AI 报告聚焦变更）。 */
    public void testBuildStageB_diffDigestOnlyChangedLines() {
        AiConfig cfg = new AiConfig();
        MockAiAnalyzer a = new MockAiAnalyzer(java.nio.file.Paths.get("."));
        String diff = "@@ -1,4 +1,4 @@\n"
                + " ctx1\n"
                + "-old value\n"
                + "+new value\n"
                + " ctx2\n"
                + "@@ -10,1 +10,2 @@\n"
                + "+inserted line\n"
                + " kept\n";
        DecompiledUnit u = new DecompiledUnit("k", "o", "n", diff, "js-beautify", "", true);
        String prompt = a.buildStageBPrompt("static/app.js", u, FileClass.JS, cfg);
        // 类型与行号标注
        Asserts.assertContains("应标注 [修改]", prompt, "[修改]");
        Asserts.assertContains("应含老侧行号 L2", prompt, "老 L2");
        Asserts.assertContains("应含新侧行号 L2", prompt, "新 L2");
        Asserts.assertContains("应标注 [新增]", prompt, "[新增]");
        Asserts.assertContains("应含新增行号 L10", prompt, "新 L10");
        // 变更行内容
        Asserts.assertContains("应含旧行内容", prompt, "old value");
        Asserts.assertContains("应含新行内容", prompt, "new value");
        Asserts.assertContains("应含插入行内容", prompt, "inserted line");
        // 上下文行不出现（仅变更行）
        Asserts.assertNotContains("不应出现上下文行 ctx1", prompt, "ctx1");
        Asserts.assertNotContains("不应出现上下文行 ctx2", prompt, "ctx2");
        Asserts.assertNotContains("不应出现上下文行 kept", prompt, "kept");
    }

    public void testSanitize_localModel_noRedaction() {
        AiConfig cfg = new AiConfig();
        cfg.setProvider("ollama");
        cfg.setBaseUrl("http://localhost:11434/v1");
        String text = "身份证11010119900307651X 密钥AK_SECRET_abc1234 手机13800138000";
        String out = PromptBuilders.sanitize(text, cfg);
        // 本地/私有化模型：代码不出机，原样保留
        Asserts.assertEquals("本地模型不应脱敏", text, out);
    }

    public void testSanitize_publicModel_redactsSensitive() {
        AiConfig cfg = new AiConfig();
        cfg.setProvider("openai");
        cfg.setBaseUrl("https://api.openai.com/v1");
        String text = "身份证11010119900307651X 密钥AK_SECRET_abc1234 手机13800138000";
        String out = PromptBuilders.sanitize(text, cfg);
        Asserts.assertContains("身份证应擦除", out, "***ID***");
        Asserts.assertContains("密钥应擦除", out, "***SECRET***");
        Asserts.assertContains("手机应擦除", out, "***PHONE***");
        Asserts.assertNotContains("不得残留 18 位身份证", out, "11010119900307651X");
        Asserts.assertNotContains("不得残留手机号", out, "13800138000");
    }

    public void testParseStageA_plainText() {
        String resp = "overallRisk: HIGH\nimpactScope: 影响范围X\n- 测试回归登录\n- 验证支付流程";
        com.bempdiff.ai.StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("风险应解析为 HIGH", "HIGH", s.getOverallRisk());
        Asserts.assertContains("影响范围", s.getImpactScope(), "影响范围X");
        Asserts.assertTrue("应包含测试主题", s.getTestThemes().size() >= 2);
        Asserts.assertContains("含回归登录主题", s.getTestThemes().toString(), "回归登录");
    }

    public void testParseStageA_jsonForm() {
        String resp = "{\"overallRisk\":\"MEDIUM\",\"impactScope\":\"Y影响\"}";
        com.bempdiff.ai.StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("JSON 形态风险应解析", "MEDIUM", s.getOverallRisk());
    }

    public void testParseFileAnalysis() {
        String resp = "intent: 调整了计费逻辑\nrisk: LOW\nimpact: 仅影响计费模块";
        com.bempdiff.ai.FileAnalysis fa = MockAiAnalyzer.parseFileAnalysisStatic("k", resp);
        Asserts.assertEquals("key", "k", fa.getKey());
        Asserts.assertEquals("risk", "LOW", fa.getRisk());
        Asserts.assertContains("intent", fa.getIntent(), "计费逻辑");
    }
}
