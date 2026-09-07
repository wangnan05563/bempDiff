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

    /**
     * 阶段A 必须同时有「行数」与「字符」两道护栏（事故根因回归）。
     *
     * <p>历史缺陷：阶段A 原先只有行数截断（stageAFileSampleLines），挡不住「单行超长」——
     * 压缩 JS / 整文件重写 / 反编译长行单行即可达数十万字符，叠加 stageATopK 个文件后
     * prompt 膨胀到 195504 tokens，触发供应商 HTTP 400 max_prompt_tokens(131072) 超限。
     * 阶段B 早有 STAGE_B_DIFF_CHAR_CAP，阶段A 缺失 → 护栏不对称。
     */
    public void testBuildStageA_singleFileCharCapGuardsLongLines() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "static/bundle.js");
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        // 单行 40 万字符（模拟压缩 JS 整行）：行数截断（默认 80 行）对此完全无效
        StringBuilder oneLine = new StringBuilder();
        while (oneLine.length() < 400_000) oneLine.append('x');
        decompiled.put("static/bundle.js",
                new DecompiledUnit("k", null, null, oneLine.toString(), "js-beautify", "", true));
        AiConfig cfg = new AiConfig();
        String prompt = PromptBuilders.buildStageA(r, decompiled, cfg);
        Asserts.assertContains("单文件超长应出现字符截断标记", prompt, "单文件摘要超长已截断");
        // 单文件 cap + 固定头部开销，远低于原始 40 万字符
        Asserts.assertTrue("阶段A prompt 应受单文件字符上限约束（原文 40 万字符）",
                prompt.length() <= PromptBuilders.STAGE_A_FILE_CHAR_CAP + 2048);
    }

    /**
     * 阶段A 总量护栏：多个大文件叠加时总字符数受 STAGE_A_TOTAL_CHAR_CAP 约束，
     * 并标注被省略的文件数，引导用户改用单文件「AI功能总结」深读。
     * 同时校验最坏情况的 token 估算远低于主流模型 128K 上下文窗口。
     */
    public void testBuildStageA_totalCharCapOmitsRemainingFiles() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        // 30 个文件 × 8000 字符 = 24 万字符原始输入，必然触发总量护栏
        StringBuilder big = new StringBuilder();
        while (big.length() < 8_000) big.append('y');
        for (int i = 0; i < 30; i++) {
            String key = "WEB-INF/classes/com/internal/C" + i + ".class";
            r.put(DiffStatus.MODIFIED, key);
            decompiled.put(key, new DecompiledUnit("k" + i, null, null, big.toString(), "cfr", "", true));
        }
        AiConfig cfg = new AiConfig();
        cfg.setStageATopK(30);
        String prompt = PromptBuilders.buildStageA(r, decompiled, cfg);
        Asserts.assertContains("总量超限时应有省略提示", prompt, "个变更文件未纳入");
        Asserts.assertTrue("阶段A 总字符应受总量上限约束（原 24 万字符）",
                prompt.length() <= PromptBuilders.STAGE_A_TOTAL_CHAR_CAP + 4096);
        // 最坏情况仍需远低于模型上下文窗口（estimateTokens = 字符数 / 2）
        Asserts.assertTrue("最坏情况 token 估算应远低于 131072 窗口",
                prompt.length() / 2.0 < 131_072);
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

    /**
     * 差异文件过多（超出 stageATopK）时：① 剥离逐文件初评（fileRisks）只出整体结论，
     * 防模型输出 JSON 超长被 max_tokens 腰斩；② 输入按目录分组聚合，各组统计覆盖全部文件。
     * 小场景（文件数 ≤ Top-K）保持旧行为：不分组、保留 fileRisks 指令（回归保护）。
     */
    public void testBuildStageA_tooManyFiles_groupAndDropFileRisks() {
        // 大场景：5 目录 × 9 个修改 + 3 个新增 = 48 个文件 > Top-K=10
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        Map<String, DecompiledUnit> dec = new LinkedHashMap<>();
        for (int i = 0; i < 45; i++) {
            String k = "WEB-INF/classes/com/mod" + (i % 5) + "/C" + i + ".class";
            r.put(DiffStatus.MODIFIED, k);
            dec.put(k, new DecompiledUnit("k" + i, null, null, "L1\nL2\nL3", "cfr", "", true));
        }
        for (int i = 0; i < 3; i++) {
            String k = "static/js/b" + i + ".js";
            r.put(DiffStatus.ADDED, k);
            dec.put(k, new DecompiledUnit("j" + i, null, null, "A1\nA2", "js", "", true));
        }
        AiConfig cfg = new AiConfig();
        cfg.setStageATopK(10);
        String p = PromptBuilders.buildStageA(r, dec, cfg);
        Asserts.assertContains("应触发分组路径", p, "按目录分组展示各组统计与代表文件");
        Asserts.assertContains("应含目录组统计", p, "### [目录] WEB-INF/classes/com/mod0");
        Asserts.assertContains("组统计覆盖全量文件", p, "共 9 个变更：新增0/修改9/删除0");
        Asserts.assertContains("新增目录组统计", p, "### [目录] static/js");
        Asserts.assertContains("应提示省略 fileRisks", p, "差异文件过多");
        Asserts.assertNotContains("不应再要求逐文件初评", p, "每个改动文件的初评数组");

        // 小场景：文件数 ≤ Top-K，保持原语义
        DiffResult r2 = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r2.put(DiffStatus.MODIFIED, "a/B.class");
        Map<String, DecompiledUnit> dec2 = new LinkedHashMap<>();
        dec2.put("a/B.class", new DecompiledUnit("k", null, null, "L1\nL2", "cfr", "", true));
        AiConfig cfg2 = new AiConfig();
        cfg2.setStageATopK(30);
        String p2 = PromptBuilders.buildStageA(r2, dec2, cfg2);
        Asserts.assertNotContains("小场景不应分组", p2, "按目录分组");
        Asserts.assertContains("小场景保留逐文件初评指令", p2, "每个改动文件的初评数组");
    }

    /** 输出上限自适应：max_tokens 按「模型窗口扣除输入后的剩余空间」放宽，且绝不超窗口真实剩余。 */
    public void testEffectiveOutputTokens_adaptiveCap() throws Exception {
        com.bempdiff.ai.HttpAiAnalyzer a = new com.bempdiff.ai.HttpAiAnalyzer(new AiConfig());
        java.lang.reflect.Method m = com.bempdiff.ai.HttpAiAnalyzer.class.getDeclaredMethod(
                "effectiveOutputTokens", String.class);
        m.setAccessible(true);
        AiConfig cfg = new AiConfig();
        cfg.setMaxPromptTokens(120_000);
        cfg.setMaxOutputTokens(8192);
        int wide = (Integer) m.invoke(a, new String(new char[30_000]).replace('\0', 'x'));
        Asserts.assertTrue("窗口允许时应放大输出上限（>8192）", wide > 8192);
        Asserts.assertTrue("放大后不得超出窗口真实剩余", wide <= 120_000 - 30_000 / 2 - 1);
        int tight = (Integer) m.invoke(a, new String(new char[200_000]).replace('\0', 'x'));
        Asserts.assertTrue("输入接近窗口时输出上限收紧且保持下限", tight >= 1024);
    }
}
