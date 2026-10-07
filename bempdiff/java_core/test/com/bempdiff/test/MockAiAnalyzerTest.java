package com.bempdiff.test;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.CategoryConclusion;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.FileRisk;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MockAiAnalyzer 全行为分支测试（离线回放，零触网）：
 *  - stageA/stageB 回放命中与兜底、聚焦维度（FocusKind）差异化文案、权威 category 注入
 *  - 回放目录为 null / prompt 落盘 / 响应读取失败降级
 *  - parseStageAStatic / parseFileAnalysisStatic 的 JSON、Markdown、纯文本、别名、
 *    截断未闭合等宽容解析路径（HttpAiAnalyzer 复用同一组静态解析）
 */
public final class MockAiAnalyzerTest {

    // ---------- 夹具 ----------

    private static DiffResult diff() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/classes/com/internal/A.class");
        r.put(DiffStatus.ADDED, "static/app.js");
        r.put(DiffStatus.DELETED, "WEB-INF/classes/com/internal/Old.class");
        return r;
    }

    private static Map<String, DecompiledUnit> dec() {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        m.put("WEB-INF/classes/com/internal/A.class",
                new DecompiledUnit("k", "o", "n", "L1\nL2\nL3", "cfr", "", true));
        return m;
    }

    private static ProjectContext ctx(String buildSystem) {
        return new ProjectContext("C:/proj", buildSystem,
                Arrays.asList("core", "web"),
                Arrays.asList("spring-boot"),
                Arrays.asList("com/x/App.java"),
                Arrays.asList("application.yml"),
                Arrays.asList("Maven", "Spring"),
                Arrays.asList("包根 com.x"),
                "多模块工程（Maven），模块含 core、web。");
    }

    private static AiConfig cfgDisabled() {
        AiConfig cfg = new AiConfig();
        cfg.setEnabled(false);
        return cfg;
    }

    private static AiConfig cfgEnabled() {
        AiConfig cfg = new AiConfig();
        cfg.setEnabled(true);
        return cfg;
    }

    private static AiAnalyzer.DecompileReq req(String key) {
        return new AiAnalyzer.DecompileReq(key,
                new DecompiledUnit("k", "o", "n", "-old\n+new", "cfr", "", true), FileClass.CLASS);
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- stageA：回放与兜底 ----------

    public void testStageA_replayHit_parsesResponseAndWritesPrompt() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            write(dir, "stageA.response.txt",
                    "{\"overallRisk\":\"HIGH\",\"impactScope\":\"回放影响范围\","
                            + "\"testThemes\":[\"主题1\",\"主题2\"]}");
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            StageASummary s = a.stageA(diff(), dec(), cfgEnabled());
            Asserts.assertEquals("风险来自回放响应", "HIGH", s.getOverallRisk());
            Asserts.assertEquals("影响范围来自回放响应", "回放影响范围", s.getImpactScope());
            Asserts.assertEquals("主题数", 2, s.getTestThemes().size());
            Asserts.assertTrue("prompt 文件应已落盘", Files.exists(dir.resolve("stageA.prompt.txt")));
            String prompt = new String(Files.readAllBytes(dir.resolve("stageA.prompt.txt")),
                    StandardCharsets.UTF_8);
            Asserts.assertContains("prompt 应含差异统计", prompt, "差异统计");
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageA_noResponse_fallbackDisabledVsEnabled() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            StageASummary off = a.stageA(diff(), dec(), cfgDisabled());
            Asserts.assertContains("关闭时应提示开关未开启", off.getImpactScope(), "AI 分析开关未开启");
            Asserts.assertEquals("兜底风险默认 MEDIUM", "MEDIUM", off.getOverallRisk());
            Asserts.assertEquals("兜底测试主题三条", 3, off.getTestThemes().size());
            Asserts.assertTrue("兜底 fileRisks 应为空", off.getFileRisks().isEmpty());

            StageASummary on = a.stageA(diff(), dec(), cfgEnabled());
            Asserts.assertContains("开启未配 Key 应提示未持久化", on.getImpactScope(), "但未配置 API Key 或未持久化");
            Asserts.assertContains("兜底应含差异文件计数", on.getImpactScope(), "差异文件数=3");
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageA_withContext_fallbackContainsCtxInfo() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            StageASummary s = a.stageA(diff(), dec(), cfgDisabled(), ctx("Maven"));
            Asserts.assertContains("应标注含项目上下文", s.getImpactScope(), "含项目上下文[Maven]");
            Asserts.assertContains("应产出血缘影响文案", s.getContextInfluence(), "构建系统=Maven");
            // rootPath 为空视为「未启用上下文增强」（isEmpty 口径）
            ProjectContext empty = new ProjectContext("", "Maven",
                    Arrays.asList("core"),
                    Arrays.asList("spring-boot"),
                    Arrays.asList("com/x/App.java"),
                    Arrays.asList("application.yml"),
                    Arrays.asList("Maven"),
                    Arrays.asList("包根 com.x"),
                    "空路径上下文。");
            StageASummary s2 = a.stageA(diff(), dec(), cfgDisabled(), empty);
            Asserts.assertNotContains("空上下文不应标注上下文", s2.getImpactScope(), "含项目上下文");
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageA_focusFallbackFiveKinds() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            StageASummary brk = a.stageA(diff(), dec(), cfgDisabled(), null, "关注破坏性变更与接口契约");
            Asserts.assertContains("breaking 兜底文案", brk.getImpactScope(), "破坏性变更专项");
            Asserts.assertContains("breaking 主题含调用方核对", brk.getTestThemes().toString(), "调用方与引用");

            StageASummary imp = a.stageA(diff(), dec(), cfgDisabled(), null, "评估影响范围与上下游依赖");
            Asserts.assertContains("impact 兜底文案", imp.getImpactScope(), "影响范围分析");
            Asserts.assertContains("impact 含新增/修改计数", imp.getImpactScope(), "新增=1");

            StageASummary tp = a.stageA(diff(), dec(), cfgDisabled(), null, "给出回归测试要点");
            Asserts.assertContains("testpoints 兜底文案", tp.getImpactScope(), "测试要点分析");

            StageASummary risk = a.stageA(diff(), dec(), cfgDisabled(), null, "整体风险与回滚降级");
            Asserts.assertContains("risk 兜底文案", risk.getImpactScope(), "整体风险分析");

            StageASummary def = a.stageA(diff(), dec(), cfgDisabled(), null, "随便看看");
            Asserts.assertContains("无法识别聚焦走默认回放文案", def.getImpactScope(), "离线回放模式");

            StageASummary blank = a.stageA(diff(), dec(), cfgDisabled(), null, "   ");
            Asserts.assertContains("空白聚焦按 RISK 处理", blank.getImpactScope(), "整体风险分析");

            // 带上下文时 focus 兜底也应写 contextInfluence
            StageASummary withCtx = a.stageA(diff(), dec(), cfgDisabled(), ctx("Gradle"), "关注破坏性变更");
            Asserts.assertContains("上下文影响应含构建系统", withCtx.getContextInfluence(), "构建系统=Gradle");
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageA_categoryAuthoritativeOverridesFocusInference() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            // category 为唯一事实源：focus 为空也按 category 出专项兜底
            StageASummary brk = a.stageA(diff(), dec(), cfgDisabled(), null, "", "breaking");
            Asserts.assertContains("category=breaking 兜底", brk.getImpactScope(), "破坏性变更专项");
            StageASummary imp = a.stageA(diff(), dec(), cfgDisabled(), null, "", "impact");
            Asserts.assertContains("category=impact 兜底", imp.getImpactScope(), "影响范围分析");
            StageASummary tps = a.stageA(diff(), dec(), cfgDisabled(), null, "", "testpoints");
            Asserts.assertContains("category=testpoints 兜底", tps.getImpactScope(), "测试要点分析");
            StageASummary risk = a.stageA(diff(), dec(), cfgDisabled(), null, "", "risk");
            Asserts.assertContains("category=risk 兜底", risk.getImpactScope(), "整体风险分析");
            // custom：kindOf 回落 FocusKind.of(null)=RISK；兜底路径不注入 category（仅回放路径注入）
            StageASummary cus = a.stageA(diff(), dec(), cfgDisabled(), null, "", "custom");
            Asserts.assertContains("custom 回落 RISK", cus.getImpactScope(), "整体风险分析");
            Asserts.assertNull("兜底摘要不写 category", cus.getCategory());

            // 回放命中时 category 仍由调用方覆盖
            write(dir, "stageA.response.txt", "{\"overallRisk\":\"LOW\",\"impactScope\":\"X\"}");
            StageASummary replayed = a.stageA(diff(), dec(), cfgDisabled(), null, "", "impact");
            Asserts.assertEquals("回放摘要风险", "LOW", replayed.getOverallRisk());
            Asserts.assertEquals("回放也应带 category", "impact", replayed.getCategory());
        } finally {
            deleteTree(dir);
        }
    }

    // ---------- stageB：回放与兜底 ----------

    public void testStageB_replayAndFallbackMixed() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            write(dir, "stageB.0.response.txt",
                    "{\"intent\":\"调整计费\",\"risk\":\"HIGH\",\"impact\":\"订单模块\","
                            + "\"testPoints\":[\"验证退款\",\"回归下单\"]}");
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            List<AiAnalyzer.DecompileReq> candidates = Arrays.asList(
                    req("WEB-INF/classes/com/internal/A.class"), req("static/app.js"));
            List<FileAnalysis> out = a.stageB(candidates, cfgDisabled());
            Asserts.assertEquals("两候选两个结果", 2, out.size());
            Asserts.assertEquals("回放风险", "HIGH", out.get(0).getRisk());
            Asserts.assertContains("回放意图", out.get(0).getIntent(), "调整计费");
            Asserts.assertEquals("回放测试要点数", 2, out.get(0).getTestPoints().size());
            Asserts.assertContains("未回放文件走兜底", out.get(1).getIntent(), "离线回放模式");
            Asserts.assertEquals("兜底默认风险", "MEDIUM", out.get(1).getRisk());
            Asserts.assertEquals("兜底默认要点", Arrays.asList("回归该类的调用方"), out.get(1).getTestPoints());
            Asserts.assertTrue("stageB.0 prompt 应落盘", Files.exists(dir.resolve("stageB.0.prompt.txt")));
            Asserts.assertTrue("stageB.1 prompt 应落盘", Files.exists(dir.resolve("stageB.1.prompt.txt")));
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageB_fallbackTestPointsVaryWithFocus() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            List<AiAnalyzer.DecompileReq> one = Arrays.asList(req("x/A.class"));
            List<FileAnalysis> brk = a.stageB(one, cfgDisabled(), null, "破坏性变更");
            Asserts.assertContains("breaking 兜底要点", brk.get(0).getTestPoints().toString(), "调用方兼容性");
            List<FileAnalysis> imp = a.stageB(one, cfgDisabled(), null, "影响范围扩散");
            Asserts.assertContains("impact 兜底要点", imp.get(0).getTestPoints().toString(), "依赖链路");
            List<FileAnalysis> tp = a.stageB(one, cfgDisabled(), null, "测试用例设计");
            Asserts.assertContains("testpoints 兜底要点", tp.get(0).getTestPoints().toString(), "边界/异常用例");
            List<FileAnalysis> risk = a.stageB(one, cfgDisabled(), null, "风险与回滚");
            Asserts.assertEquals("risk 走默认要点", Arrays.asList("回归该类的调用方"), risk.get(0).getTestPoints());
        } finally {
            deleteTree(dir);
        }
    }

    public void testStageB_perFileContextTakesPrecedence() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            AiAnalyzer.DecompileReq withOwn = new AiAnalyzer.DecompileReq(
                    "x/A.class", new DecompiledUnit("k", "o", "n", "-a\n+b", "cfr", "", true),
                    FileClass.CLASS, ctx("Ant"));
            AiAnalyzer.DecompileReq plain = req("x/B.class");
            List<FileAnalysis> out = a.stageB(Arrays.asList(withOwn, plain), cfgDisabled(), ctx("Maven"));
            Asserts.assertContains("逐文件上下文应优先", out.get(0).getContextInfluence(), "（Ant）");
            Asserts.assertContains("无逐文件上下文回退统一 ctx", out.get(1).getContextInfluence(), "（Maven）");

            // 上下文命中时回放响应应解析 contextInfluence（含中文别名）
            write(dir, "stageB.0.response.txt",
                    "{\"意图\":\"重构\",\"risk\":\"LOW\",\"影响\":\"仅内部\",\"上下文影响\":\"事务约束\"}");
            List<FileAnalysis> rep = a.stageB(Arrays.asList(withOwn), cfgDisabled(), null);
            Asserts.assertEquals("回放别名意图", "重构", rep.get(0).getIntent());
            Asserts.assertEquals("回放别名风险", "LOW", rep.get(0).getRisk());
            Asserts.assertContains("回放别名上下文", rep.get(0).getContextInfluence(), "事务约束");
        } finally {
            deleteTree(dir);
        }
    }

    // ---------- 边界：null 目录 / 读取失败 / testConnection ----------

    public void testReplayDirNull_fallbacksWithoutFiles() {
        MockAiAnalyzer a = new MockAiAnalyzer(null);
        StageASummary s = a.stageA(diff(), dec(), cfgDisabled());
        Asserts.assertEquals("null 目录仍出兜底风险", "MEDIUM", s.getOverallRisk());
        Asserts.assertContains("null 目录兜底文案", s.getImpactScope(), "离线回放模式");
        List<FileAnalysis> out = a.stageB(Arrays.asList(req("x/A.class")), cfgDisabled());
        Asserts.assertEquals("stageB 兜底", 1, out.size());
        Asserts.assertContains("stageB 兜底意图", out.get(0).getIntent(), "离线回放模式");
        StageASummary sf = a.stageA(diff(), dec(), cfgDisabled(), null, "影响范围");
        Asserts.assertContains("focus 兜底也应工作", sf.getImpactScope(), "影响范围分析");
    }

    public void testReadResponse_failureFallsBackToDefault() throws Exception {
        Path dir = Files.createTempDirectory("bempdiff-mockai-");
        try {
            // 以「目录」冒充响应文件：exists 为真但读取抛 IOException → readResponse 返回 null → 兜底
            Files.createDirectory(dir.resolve("stageA.response.txt"));
            MockAiAnalyzer a = new MockAiAnalyzer(dir);
            StageASummary s = a.stageA(diff(), dec(), cfgDisabled());
            Asserts.assertContains("读取失败应回落兜底", s.getImpactScope(), "离线回放模式");
        } finally {
            deleteTree(dir);
        }
    }

    public void testTestConnection_alwaysTrue() {
        MockAiAnalyzer a = new MockAiAnalyzer(null);
        Asserts.assertTrue("离线回放恒可连接", a.testConnection(cfgDisabled()));
    }

    // ---------- 静态解析：parseStageAStatic ----------

    public void testParseStageA_richJson_allSections() {
        String resp = "{"
                + "\"overallRisk\":\"HIGH\","
                + "\"impactScope\":[\"模块A\",\"模块B\"],"
                + "\"testThemes\":[\"转义\\n换行\",\"制表\\t符\",\"引号\\\"内\\\"\",\"路径\\\\分\",\"正斜\\/杠\",\"未知转义\\q\"],"
                + "\"fileRisks\":["
                + "  {\"key\":\"a.class\",\"risk\":\"HIGH\",\"oneLineReason\":\"签名变更\"},"
                + "  {\"fileName\":\"b.js\",\"riskLevel\":\"LOW\",\"reason\":\"文案调整\"},"
                + "  {\"risk\":\"MEDIUM\"}"
                + "],"
                + "\"conclusion\":{"
                + "  \"breakingChanges\":[{\"file\":\"a.class\",\"changeType\":\"删除方法\",\"change\":\"移除 doX()\","
                + "     \"compatImpact\":\"调用方编译失败\",\"severity\":\"HIGH\",\"migrationSuggestion\":\"改用 doY()\"}],"
                + "  \"compatibilityVerdict\":\"存在 1 处不兼容\","
                + "  \"affectedModules\":[\"billing-core\",\"web-api\"],"
                + "  \"internalCallers\":{\"billing\":\"PaymentService\",\"web\":\"IndexCtrl\"},"
                + "  \"diffusion\":\"上游 -> 下游\","
                + "  \"testPoints\":["
                + "    {\"item\":\"T1\",\"file\":\"a.class\",\"scenario\":\"S1\",\"caseIdea\":\"C1\",\"verifyFocus\":\"V1\"},"
                + "    {\"name\":\"T2\",\"files\":\"b.js\",\"scenario\":\"S2\",\"case_idea\":\"C2\",\"verify_focus\":\"V2\"}"
                + "  ],"
                + "  \"riskRationale\":\"整体可控\",\"rollbackPlan\":\"回滚脚本 A\",\"degradationPlan\":\"开关降级\","
                + "  \"suggestions\":\"- 建议一\\n- 建议二\""
                + "}"
                + "}";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("风险", "HIGH", s.getOverallRisk());
        Asserts.assertContains("字符串数组转多行", s.getImpactScope(), "模块A\n模块B");
        List<String> themes = s.getTestThemes();
        Asserts.assertEquals("主题数", 6, themes.size());
        Asserts.assertEquals("数组内 \\n 应还原为真实换行", "转义\n换行", themes.get(0));
        Asserts.assertContains("数组内 \\t 还原", themes.get(1), "\t");
        Asserts.assertEquals("数组内 \\\" 还原", "引号\"内\"", themes.get(2));
        Asserts.assertEquals("数组内 \\\\ 还原", "路径\\分", themes.get(3));
        Asserts.assertEquals("数组内 \\/ 还原", "正斜/杠", themes.get(4));
        Asserts.assertEquals("未知转义按原字符", "未知转义q", themes.get(5));

        List<FileRisk> risks = s.getFileRisks();
        Asserts.assertEquals("fileRisks 数", 3, risks.size());
        Asserts.assertEquals("主字段 key", "a.class", risks.get(0).getKey());
        Asserts.assertEquals("别名 fileName", "b.js", risks.get(1).getKey());
        Asserts.assertEquals("别名 riskLevel", "LOW", risks.get(1).getRisk());
        Asserts.assertEquals("缺 key 应填 ?", "?", risks.get(2).getKey());
        Asserts.assertEquals("缺 risk 应默认", "MEDIUM", risks.get(2).getRisk());

        CategoryConclusion c = s.getConclusion();
        Asserts.assertEquals("breakingChanges 数", 1, c.getBreakingChanges().size());
        CategoryConclusion.BreakingChange b = c.getBreakingChanges().get(0);
        Asserts.assertEquals("breaking 文件", "a.class", b.getFile());
        Asserts.assertEquals("breaking 变更类型", "删除方法", b.getChangeType());
        Asserts.assertEquals("breaking 建议", "改用 doY()", b.getMigrationSuggestion());
        Asserts.assertEquals("兼容性结论", "存在 1 处不兼容", c.getCompatibilityVerdict());
        Asserts.assertContains("受影响模块数组转文本", c.getAffectedModules(), "billing-core\nweb-api");
        Asserts.assertContains("对象形态拍平", c.getInternalCallers(), "- billing：PaymentService");
        Asserts.assertContains("对象形态拍平 2", c.getInternalCallers(), "- web：IndexCtrl");
        Asserts.assertEquals("testPoints 主字段", "T1", c.getTestPoints().get(0).getItem());
        Asserts.assertEquals("testPoints 别名 name", "T2", c.getTestPoints().get(1).getItem());
        Asserts.assertEquals("testPoints 别名 files", "b.js", c.getTestPoints().get(1).getFile());
        Asserts.assertEquals("testPoints 别名 case_idea", "C2", c.getTestPoints().get(1).getCaseIdea());
        Asserts.assertEquals("风险依据", "整体可控", c.getRiskRationale());
        Asserts.assertEquals("回滚预案", "回滚脚本 A", c.getRollbackPlan());
        Asserts.assertEquals("降级预案", "开关降级", c.getDegradationPlan());
        Asserts.assertContains("建议", c.getSuggestions(), "建议一");
        Asserts.assertTrue("hasSuggestions", c.hasSuggestions());
    }

    public void testParseStageA_aliasFieldsAndEmptySections() {
        // 别名：test_themes / 测试主题 / files / breaking_items / test_points
        String resp = "{"
                + "\"overallRisk\":\"LOW\",\"test_themes\":[\"别名主题\"],"
                + "\"files\":[{\"key\":\"c.css\",\"risk\":\"HIGH\"}],"
                + "\"breaking_items\":[{\"fileName\":\"d.class\",\"type\":\"接口破坏\",\"detail\":\"删接口\","
                + "  \"impact\":\"破坏对外\",\"risk\":\"HIGH\",\"suggestion\":\"补适配\"}],"
                + "\"test_points\":[{\"item\":\"T\"}]"
                + "}";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("test_themes 别名", "别名主题", s.getTestThemes().get(0));
        Asserts.assertEquals("files 别名", "c.css", s.getFileRisks().get(0).getKey());
        CategoryConclusion c = s.getConclusion();
        Asserts.assertEquals("breaking_items 别名 fileName", "d.class", c.getBreakingChanges().get(0).getFile());
        Asserts.assertEquals("breaking type 别名", "接口破坏", c.getBreakingChanges().get(0).getChangeType());
        Asserts.assertEquals("breaking detail 别名", "删接口", c.getBreakingChanges().get(0).getChange());
        Asserts.assertEquals("breaking impact 别名", "破坏对外", c.getBreakingChanges().get(0).getCompatImpact());
        Asserts.assertEquals("breaking risk 别名", "HIGH", c.getBreakingChanges().get(0).getSeverity());
        Asserts.assertEquals("breaking suggestion 别名", "补适配", c.getBreakingChanges().get(0).getMigrationSuggestion());
        Asserts.assertEquals("test_points 别名", "T", c.getTestPoints().get(0).getItem());

        // 空数组与缺失字段：不抛错，留空
        StageASummary e = MockAiAnalyzer.parseStageAStatic(
                "{\"testThemes\":[],\"fileRisks\":[]}");
        Asserts.assertEquals("空数组主题走 markdown 回退应为空", 0, e.getTestThemes().size());
        Asserts.assertTrue("空数组 fileRisks", e.getFileRisks().isEmpty());
        Asserts.assertEquals("缺失风险默认 MEDIUM", "MEDIUM", e.getOverallRisk());
        Asserts.assertTrue("未启用上下文时 contextInfluence 为空（null 或空串）",
                e.getContextInfluence() == null || e.getContextInfluence().isEmpty());
    }

    public void testParseStageA_markdownFallbackAndPlainTextPick() {
        String resp = "overallRisk: HIGH\n"
                + "impactScope: 影响范围X\n"
                + "- 测试回归登录\n"
                + "- 验证支付流程\n"
                + "- 与主题无关的行\n";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("纯文本 pick", "HIGH", s.getOverallRisk());
        Asserts.assertEquals("纯文本影响", "影响范围X", s.getImpactScope());
        Asserts.assertEquals("markdown 行扫描仅收主题相关", 2, s.getTestThemes().size());
        Asserts.assertEquals("去前导横线", "测试回归登录", s.getTestThemes().get(0));
    }

    public void testParseStageA_contextAliasAndIndependentKeyGuard() {
        // contextInfluence 的中文别名「上下文影响」；impact 独立键不应命中 compatImpact 复合键
        String resp = "{\"overallRisk\":\"LOW\",\"上下文影响\":\"中文别名上下文\","
                + "\"compatImpact\":\"复合键噪声\"}";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp, true);
        Asserts.assertContains("应命中中文别名", s.getContextInfluence(), "中文别名上下文");
        StageASummary s2 = MockAiAnalyzer.parseStageAStatic(resp, false);
        Asserts.assertTrue("未启用上下文不写入",
                s2.getContextInfluence() == null || s2.getContextInfluence().isEmpty());
    }

    public void testParseStageA_truncatedValueDoesNotHang() {
        // LLM 输出被腰斩：对象/数组未闭合，解析应终止并给出可用部分
        String resp = "{\"overallRisk\":\"HIGH\",\"suggestions\":{\"a\":\"b\",";
        StageASummary s = MockAiAnalyzer.parseStageAStatic(resp);
        Asserts.assertEquals("腰斩响应仍解析风险", "HIGH", s.getOverallRisk());
        String resp2 = "{\"affectedModules\":[\"x\",\"y\"";
        StageASummary s2 = MockAiAnalyzer.parseStageAStatic(resp2);
        Asserts.assertNotNull("未闭合数组不应抛错", s2.getConclusion());
    }

    public void testParseFileAnalysis_staticAllForms() {
        FileAnalysis json = MockAiAnalyzer.parseFileAnalysisStatic("k1",
                "{\"intent\":\"意图A\",\"risk\":\"LOW\",\"impact\":\"影响A\","
                        + "\"testPoints\":[\"P1\",\"P2\"],\"contextInfluence\":\"C1\"}", true);
        Asserts.assertEquals("JSON intent", "意图A", json.getIntent());
        Asserts.assertEquals("JSON impact", "影响A", json.getImpact());
        Asserts.assertEquals("JSON testPoints 数", 2, json.getTestPoints().size());
        Asserts.assertEquals("withContext 取 contextInfluence", "C1", json.getContextInfluence());

        FileAnalysis plain = MockAiAnalyzer.parseFileAnalysisStatic("k2",
                "intent: 纯文本意图\nrisk: \"HIGH\",\nimpact: 纯文本影响", false);
        Asserts.assertEquals("纯文本 intent", "纯文本意图", plain.getIntent());
        Asserts.assertEquals("纯文本 risk 去引号逗号", "HIGH", plain.getRisk());
        Asserts.assertEquals("withContext=false 忽略上下文", "", plain.getContextInfluence());

        FileAnalysis minimal = MockAiAnalyzer.parseFileAnalysisStatic("k3", "{}");
        Asserts.assertEquals("缺字段风险默认", "MEDIUM", minimal.getRisk());
        Asserts.assertTrue("缺字段要点为空", minimal.getTestPoints().isEmpty());
    }

    // ---------- 私有静态兜底逻辑（反射直测，补齐难达分支） ----------

    public void testOfflineReason_allBranchesViaReflection() throws Exception {
        Method m = MockAiAnalyzer.class.getDeclaredMethod("offlineReason", AiConfig.class);
        m.setAccessible(true);
        Asserts.assertContains("cfg null 分支", (String) m.invoke(null, (AiConfig) null), "未配置 AI 分析");
        Asserts.assertContains("未开启分支", (String) m.invoke(null, cfgDisabled()), "开关未开启");
        Asserts.assertContains("开启未配 Key 分支", (String) m.invoke(null, cfgEnabled()), "未持久化");
    }

    public void testFocusKindOf_viaReflection() throws Exception {
        Class<?> kind = Class.forName("com.bempdiff.ai.MockAiAnalyzer$FocusKind");
        Method of = kind.getDeclaredMethod("of", String.class);
        of.setAccessible(true);
        Asserts.assertEquals("null 聚焦按 RISK", "RISK", of.invoke(null, (String) null).toString());
        Asserts.assertEquals("兼容性关键词", "BREAKING", of.invoke(null, "接口契约与兼容性").toString());
        Asserts.assertEquals("依赖关键词", "IMPACT", of.invoke(null, "依赖方向").toString());
        Asserts.assertEquals("回归关键词", "TESTPOINTS", of.invoke(null, "回归场景").toString());
        Asserts.assertEquals("回滚关键词", "RISK", of.invoke(null, "回滚预案").toString());
        Asserts.assertEquals("无关键词", "DEFAULT", of.invoke(null, "你好").toString());
    }

    private static void deleteTree(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 临时目录清理失败交给操作系统兜底
                        }
                    });
        } catch (IOException ignored) {
            // 忽略
        }
    }
}
